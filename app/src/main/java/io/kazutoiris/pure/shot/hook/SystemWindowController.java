package io.kazutoiris.pure.shot.hook;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.SurfaceControl;
import android.view.WindowManager;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

import io.github.libxposed.api.XposedInterface;

/**
 * Owns the registry of system window surfaces and applies {@code SKIP_SCREENSHOT} dynamically.
 *
 * <p>Window creation hooks only register the surface; every cross-process SurfaceFlinger call is
 * posted to the system_server main looper and coalesced. Unlike the legacy constructor flags, a
 * transaction can be applied and reverted at any time, which is what makes per-app and live
 * toggling possible.
 */
public final class SystemWindowController {

	private static final String TAG = "PureShot";

	private final XposedInterface xposed;
	private final PolicyStore policyStore;
	private final ForegroundAppTracker foregroundTracker;
	private final Handler handler = new Handler(Looper.getMainLooper());
	private final Map<SurfaceControl, Entry> registry = new WeakHashMap<>();
	private final Set<String> loggedFailures = ConcurrentHashMap.newKeySet();
	private final Method setSkipScreenshot;

	private volatile boolean stopped;
	private boolean applyScheduled;

	public SystemWindowController(XposedInterface xposed, ClassLoader classLoader,
			PolicyStore policyStore, ForegroundAppTracker foregroundTracker) {
		this.xposed = xposed;
		this.policyStore = policyStore;
		this.foregroundTracker = foregroundTracker;
		Class<?> transactionClass = Reflect.findClass(
				"android.view.SurfaceControl$Transaction", classLoader);
		this.setSkipScreenshot = Reflect.findMethod(transactionClass, "setSkipScreenshot",
				SurfaceControl.class, boolean.class);
		if (setSkipScreenshot == null) {
			logOnce(Log.WARN, "SurfaceControl.Transaction#setSkipScreenshot unavailable", null);
		}
	}

	/**
	 * Registers a system window surface. Safe to call from WMS/ATMS threads; applying the policy
	 * itself always hops to the main looper.
	 */
	public void register(SurfaceControl surfaceControl, int windowType) {
		if (stopped || surfaceControl == null || setSkipScreenshot == null) {
			return;
		}
		Feature feature = WindowTypeFeatureMapper.map(windowType);
		if (feature == Feature.NONE) {
			return;
		}
		synchronized (registry) {
			Entry entry = registry.get(surfaceControl);
			if (entry == null) {
				registry.put(surfaceControl, new Entry(feature));
			} else {
				entry.feature = feature;
			}
		}
		requestApply();
	}

	/**
	 * Coalesces policy/foreground changes into a single main-thread pass.
	 */
	public void requestApply() {
		if (stopped || setSkipScreenshot == null) {
			return;
		}
		synchronized (this) {
			if (applyScheduled) {
				return;
			}
			applyScheduled = true;
		}
		handler.post(this::applyNow);
	}

	/**
	 * Re-registers windows that already existed when the hooks were installed (boot-time windows,
	 * and all windows after a hot reload).
	 */
	public void rehydrateExistingWindows() {
		if (stopped) {
			return;
		}
		handler.post(this::rehydrateNow);
	}

	public void shutdown() {
		stopped = true;
		handler.removeCallbacksAndMessages(null);
		synchronized (this) {
			applyScheduled = false;
		}
		synchronized (registry) {
			registry.clear();
		}
		// Intentionally do not clear SKIP_SCREENSHOT here: during hot reload the old generation
		// stays in place until the new one re-applies, avoiding a visible flash of system bars.
	}

	private void applyNow() {
		if (stopped) {
			return;
		}
		synchronized (this) {
			applyScheduled = false;
		}
		String packageName = foregroundTracker != null
				? foregroundTracker.packageForPolicy()
				: ForegroundAppTracker.FALLBACK_PACKAGE;

		List<SurfaceControl> targets = new ArrayList<>();
		List<Boolean> desiredValues = new ArrayList<>();
		synchronized (registry) {
			Iterator<Map.Entry<SurfaceControl, Entry>> iterator = registry.entrySet().iterator();
			while (iterator.hasNext()) {
				Map.Entry<SurfaceControl, Entry> item = iterator.next();
				SurfaceControl surface = item.getKey();
				Entry entry = item.getValue();
				if (surface == null || entry == null) {
					iterator.remove();
					continue;
				}
				boolean desired = policyStore != null
						&& policyStore.isEnabled(entry.feature, packageName);
				if (entry.appliedSkip != null && entry.appliedSkip == desired) {
					continue;
				}
				targets.add(surface);
				desiredValues.add(desired);
			}
		}

		int applied = 0;
		for (int i = 0; i < targets.size(); i++) {
			SurfaceControl surface = targets.get(i);
			boolean desired = desiredValues.get(i);
			if (!isValid(surface)) {
				remove(surface);
				continue;
			}
			if (applySkip(surface, desired)) {
				markApplied(surface, desired);
				applied++;
			} else {
				remove(surface);
			}
		}
		if (applied > 0) {
			xposed.log(Log.INFO, TAG, "applied SKIP_SCREENSHOT to " + applied
					+ " window(s), foreground=" + packageName);
		}
	}

	private boolean applySkip(SurfaceControl surfaceControl, boolean skip) {
		SurfaceControl.Transaction transaction = null;
		try {
			transaction = new SurfaceControl.Transaction();
			setSkipScreenshot.invoke(transaction, surfaceControl, skip);
			transaction.apply();
			return true;
		} catch (Throwable t) {
			logOnce(Log.WARN, "failed to apply SKIP_SCREENSHOT=" + skip, t);
			return false;
		} finally {
			if (transaction != null) {
				try {
					transaction.close();
				} catch (Throwable ignored) {
					// Nothing useful to do here.
				}
			}
		}
	}

	private boolean isValid(SurfaceControl surfaceControl) {
		try {
			return surfaceControl.isValid();
		} catch (Throwable t) {
			return false;
		}
	}

	private void markApplied(SurfaceControl surfaceControl, boolean desired) {
		synchronized (registry) {
			Entry entry = registry.get(surfaceControl);
			if (entry != null) {
				entry.appliedSkip = desired;
			}
		}
	}

	private void remove(SurfaceControl surfaceControl) {
		synchronized (registry) {
			registry.remove(surfaceControl);
		}
	}

	private void rehydrateNow() {
		if (stopped) {
			return;
		}
		try {
			Object wms = lookupService("window");
			if (wms == null) {
				logOnce(Log.WARN, "WindowManagerService unavailable for rehydrate", null);
				return;
			}
			Class<?> wmsClass = wms.getClass();
			Field windowMapField = Reflect.findField(wmsClass, "mWindowMap");
			Object windowMap = Reflect.get(windowMapField, wms);
			if (!(windowMap instanceof Map)) {
				logOnce(Log.WARN, "WindowManagerService#mWindowMap unavailable", null);
				return;
			}

			Field globalLockField = Reflect.findField(wmsClass, "mGlobalLock");
			Object globalLock = Reflect.get(globalLockField, wms);
			Object guard = globalLock != null ? globalLock : wms;

			List<WindowSurface> windows = new ArrayList<>();
			Field attrsField = null;
			Field surfaceField = null;
			// Copy field values under the WMS lock, but never call into SurfaceFlinger there.
			synchronized (guard) {
				for (Object windowState : ((Map<?, ?>) windowMap).values()) {
					if (windowState == null) {
						continue;
					}
					if (attrsField == null) {
						attrsField = Reflect.findField(windowState.getClass(), "mAttrs");
						surfaceField = Reflect.findField(windowState.getClass(), "mSurfaceControl");
					}
					Object attrs = Reflect.get(attrsField, windowState);
					Object surface = Reflect.get(surfaceField, windowState);
					if (attrs instanceof WindowManager.LayoutParams
							&& surface instanceof SurfaceControl) {
						windows.add(new WindowSurface(
								(SurfaceControl) surface,
								((WindowManager.LayoutParams) attrs).type));
					}
				}
			}

			for (WindowSurface window : windows) {
				register(window.surfaceControl, window.windowType);
			}
			xposed.log(Log.INFO, TAG, "rehydrated " + windows.size() + " existing window(s)");
		} catch (Throwable t) {
			logOnce(Log.WARN, "failed to rehydrate existing windows", t);
		}
	}

	private static Object lookupService(String name) {
		Class<?> serviceManager = Reflect.findClass(
				"android.os.ServiceManager", SystemWindowController.class.getClassLoader());
		Method getService = Reflect.findMethod(serviceManager, "getService", String.class);
		return Reflect.call(getService, null, name);
	}

	private void logOnce(int priority, String message, Throwable t) {
		if (!loggedFailures.add(message)) {
			return;
		}
		if (t == null) {
			xposed.log(priority, TAG, message);
		} else {
			xposed.log(priority, TAG, message, t);
		}
	}

	private static final class Entry {
		Feature feature;
		Boolean appliedSkip;

		Entry(Feature feature) {
			this.feature = feature;
		}
	}

	private static final class WindowSurface {
		final SurfaceControl surfaceControl;
		final int windowType;

		WindowSurface(SurfaceControl surfaceControl, int windowType) {
			this.surfaceControl = surfaceControl;
			this.windowType = windowType;
		}
	}
}
