package io.kazutoiris.pure.shot;

import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.SurfaceControl;
import android.view.WindowManager;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedInterface.HookHandle;
import io.kazutoiris.pure.shot.hook.ForegroundAppTracker;
import io.kazutoiris.pure.shot.hook.PolicyStore;
import io.kazutoiris.pure.shot.hook.Reflect;
import io.kazutoiris.pure.shot.hook.SystemWindowController;

/**
 * libxposed API 102 entry point.
 *
 * <p>The module is scoped to {@code system} and only initializes inside system_server. Hooks are
 * installed from {@link #onSystemServerStarting} and never throw into the system process.
 *
 * <p>Window creation hooks only register surfaces with {@link SystemWindowController}; the policy
 * is applied and reverted through SurfaceControl transactions, so settings changes no longer
 * require restarting System UI.
 */
public class XposedModule extends io.github.libxposed.api.XposedModule {

	private static final String TAG = "PureShot";

	private final AtomicBoolean windowFailureLogged = new AtomicBoolean();
	private volatile PolicyStore policyStore;
	private volatile ForegroundAppTracker foregroundTracker;
	private volatile SystemWindowController windowController;
	private volatile boolean initialized;

	@Override
	public void onModuleLoaded(ModuleLoadedParam param) {
		log(Log.INFO, TAG, "loaded in " + param.getProcessName()
				+ " (systemServer=" + param.isSystemServer() + ")"
				+ ", API " + getApiVersion()
				+ ", framework " + getFrameworkName() + " " + getFrameworkVersion()
				+ ", properties=0x" + Long.toHexString(getFrameworkProperties()));
	}

	@Override
	public void onSystemServerStarting(SystemServerStartingParam param) {
		initializeSystemServer(param.getClassLoader());
	}

	/**
	 * Runs in the old generation. The framework keeps the old hooks installed until
	 * {@link #onHotReloaded}, so this only releases module-owned resources: the main-thread
	 * Handler queue and the RemotePreferences listener. SKIP_SCREENSHOT flags are deliberately
	 * left untouched so system bars do not flash during the swap.
	 */
	@Override
	public boolean onHotReloading(HotReloadingParam param) {
		try {
			log(Log.INFO, TAG, "hot reload requested, retiring generation " + generationId());
			shutdown();
			return true;
		} catch (Throwable t) {
			log(Log.ERROR, TAG, "failed to retire generation " + generationId(), t);
			// The half-released generation must not stay active.
			return false;
		}
	}

	/**
	 * Runs in the new generation. Resolves the system_server classloader from the old hooks before
	 * removing them, then installs a fresh hook set and re-applies the current policy to windows
	 * that already exist.
	 */
	@Override
	public void onHotReloaded(HotReloadedParam param) {
		List<HookHandle> oldHandles = param.getOldHookHandles();
		ClassLoader serverClassLoader = resolveSystemServerClassLoader(oldHandles);
		log(Log.INFO, TAG, "hot reloaded into generation " + generationId()
				+ " (process=" + param.getProcessName()
				+ ", systemServer=" + param.isSystemServer()
				+ ", oldHooks=" + oldHandles.size()
				+ ", classLoader=" + serverClassLoader + ")");

		int unhooked = 0;
		for (HookHandle handle : oldHandles) {
			try {
				handle.unhook();
				unhooked++;
			} catch (Throwable t) {
				log(Log.WARN, TAG, "failed to unhook " + describe(handle), t);
			}
		}
		log(Log.INFO, TAG, "unhooked " + unhooked + "/" + oldHandles.size() + " old hook(s)");

		if (!param.isSystemServer()) {
			log(Log.WARN, TAG, "hot reloaded outside system_server, skipping initialization");
			return;
		}

		// This instance is new, but reset explicitly in case a framework reuses the entry object.
		initialized = false;
		initializeSystemServer(serverClassLoader);

		SystemWindowController controller = windowController;
		if (controller != null) {
			// New registry is empty; re-register the windows that survived the swap.
			controller.rehydrateExistingWindows();
		}
	}

	private void shutdown() {
		ForegroundAppTracker tracker = foregroundTracker;
		foregroundTracker = null;
		if (tracker != null) {
			try {
				tracker.shutdown();
			} catch (Throwable t) {
				log(Log.WARN, TAG, "failed to stop foreground tracker", t);
			}
		}

		SystemWindowController controller = windowController;
		windowController = null;
		if (controller != null) {
			try {
				controller.shutdown();
			} catch (Throwable t) {
				log(Log.WARN, TAG, "failed to stop window controller", t);
			}
		}

		PolicyStore store = policyStore;
		policyStore = null;
		if (store != null) {
			try {
				store.shutdown();
			} catch (Throwable t) {
				log(Log.WARN, TAG, "failed to stop policy store", t);
			}
		}

		initialized = false;
	}

	/**
	 * {@link HotReloadedParam} does not expose a classloader. System hooks (WindowState,
	 * WindowManagerService, ActivityTaskManagerService) were declared by classes loaded by the
	 * system_server loader, so the first non-boot old handle gives it to us. When every handle is
	 * from the boot classpath, the module classloader's parent is the system_server loader.
	 */
	private ClassLoader resolveSystemServerClassLoader(List<HookHandle> oldHandles) {
		for (HookHandle handle : oldHandles) {
			try {
				Class<?> declaring = handle.getExecutable().getDeclaringClass();
				ClassLoader candidate = declaring.getClassLoader();
				if (candidate != null && canLoadSystemServer(candidate)) {
					log(Log.INFO, TAG, "resolved system_server classloader from "
							+ declaring.getName());
					return candidate;
				}
			} catch (Throwable ignored) {
				// Try the next handle.
			}
		}

		ClassLoader own = getClass().getClassLoader();
		ClassLoader parent = own != null ? own.getParent() : null;
		if (canLoadSystemServer(parent)) {
			log(Log.INFO, TAG, "resolved system_server classloader from module classloader parent");
			return parent;
		}

		ClassLoader system = ClassLoader.getSystemClassLoader();
		if (canLoadSystemServer(system)) {
			log(Log.INFO, TAG, "resolved system_server classloader from system classloader");
			return system;
		}

		ClassLoader fallback = parent != null ? parent : system;
		log(Log.WARN, TAG, "could not validate a system_server classloader, using " + fallback);
		return fallback;
	}

	private static boolean canLoadSystemServer(ClassLoader classLoader) {
		if (classLoader == null) {
			return false;
		}
		try {
			Class.forName("com.android.server.wm.WindowManagerService", false, classLoader);
			return true;
		} catch (Throwable t) {
			return false;
		}
	}

	private String generationId() {
		ClassLoader loader = getClass().getClassLoader();
		return "0x" + Integer.toHexString(System.identityHashCode(loader));
	}

	/**
	 * Identifies the installed module APK the current generation was loaded from. The install
	 * directory changes on every update, so a hot reload shows the newly installed APK here.
	 */
	private String moduleStamp() {
		try {
			ApplicationInfo info = getModuleApplicationInfo();
			return info.sourceDir;
		} catch (Throwable t) {
			return "unknown";
		}
	}

	private static String describe(HookHandle handle) {
		try {
			return handle.getExecutable().toString();
		} catch (Throwable t) {
			return "<unknown hook>";
		}
	}

	private synchronized void initializeSystemServer(ClassLoader classLoader) {
		if (initialized) {
			log(Log.WARN, TAG, "system_server hooks already initialized");
			return;
		}
		initialized = true;

		log(Log.INFO, TAG, "initializing system_server hooks, generation=" + generationId()
				+ ", SDK=" + Build.VERSION.SDK_INT + ", module=" + moduleStamp());
		try {
			policyStore = new PolicyStore(this, this::requestWindowApply);
			foregroundTracker = new ForegroundAppTracker(this, this::requestWindowApply);
			windowController = new SystemWindowController(
					this, classLoader, policyStore, foregroundTracker);

			policyStore.start();
			foregroundTracker.start(classLoader);

			if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.BAKLAVA) {
				boolean hooked = hookSurfaceControlConstructor(classLoader);
				if (!hooked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
					// API 35-36 fallback when the private constructor path disappears.
					hookWindowState(classLoader);
				}
			} else {
				// API 37+ (use_client_surface): the client owns its buffer surface, so mark the
				// server-owned WindowState container surface instead.
				hookWindowState(classLoader);
			}
			hookWindowManagerReady(classLoader);
		} catch (Throwable t) {
			log(Log.ERROR, TAG, "failed to initialize system_server hooks", t);
		}
	}

	private void requestWindowApply() {
		SystemWindowController controller = windowController;
		if (controller != null) {
			controller.requestApply();
		}
	}

	private boolean hookSurfaceControlConstructor(ClassLoader classLoader) {
		Class<?> surfaceControlClass = Reflect.findClass("android.view.SurfaceControl", classLoader);
		if (surfaceControlClass == null) {
			return false;
		}
		for (Constructor<?> constructor : surfaceControlClass.getDeclaredConstructors()) {
			if (constructor.getParameterCount() != 10) {
				continue;
			}
			hook(constructor)
					.setExceptionMode(ExceptionMode.PROTECTIVE)
					.intercept(chain -> {
						Object result = chain.proceed();
						try {
							Object[] args = chain.getArgs().toArray();
							String callsite = args[9] instanceof String ? (String) args[9] : null;
							if (Objects.equals(callsite, "WindowSurfaceController")) {
								SparseIntArray metadata = args[7] instanceof SparseIntArray
										? (SparseIntArray) args[7] : null;
								int windowType = metadata != null ? metadata.get(2, -1) : -1;
								Object thisObject = chain.getThisObject();
								if (thisObject instanceof SurfaceControl) {
									SystemWindowController controller = windowController;
									if (controller != null) {
										controller.register((SurfaceControl) thisObject, windowType);
									}
								}
							}
						} catch (Throwable t) {
							logWindowFailure(t);
						}
						return result;
					});
			log(Log.INFO, TAG, "hooked SurfaceControl 10-arg constructor");
			return true;
		}
		log(Log.WARN, TAG, "SurfaceControl 10-arg constructor not found");
		return false;
	}

	private void hookWindowState(ClassLoader classLoader) {
		Class<?> windowStateClass = Reflect.findClass("com.android.server.wm.WindowState", classLoader);
		if (windowStateClass == null) {
			return;
		}
		Method target = Reflect.findMethod(windowStateClass, "setInitialSurfaceControlProperties",
				SurfaceControl.Builder.class);
		Field attrsField = Reflect.findField(windowStateClass, "mAttrs");
		Field surfaceField = Reflect.findField(windowStateClass, "mSurfaceControl");
		if (target == null || attrsField == null || surfaceField == null) {
			log(Log.WARN, TAG, "WindowState hook prerequisites missing");
			return;
		}

		hook(target)
				.setExceptionMode(ExceptionMode.PROTECTIVE)
				.intercept(chain -> {
					Object result = chain.proceed();
					try {
						Object thisObject = chain.getThisObject();
						Object attrsObject = Reflect.get(attrsField, thisObject);
						Object surfaceObject = Reflect.get(surfaceField, thisObject);
						if (attrsObject instanceof WindowManager.LayoutParams
								&& surfaceObject instanceof SurfaceControl) {
							SystemWindowController controller = windowController;
							if (controller != null) {
								controller.register((SurfaceControl) surfaceObject,
										((WindowManager.LayoutParams) attrsObject).type);
							}
						}
					} catch (Throwable t) {
						logWindowFailure(t);
					}
					return result;
				});
		log(Log.INFO, TAG, "hooked WindowState#setInitialSurfaceControlProperties");
	}

	/**
	 * Boot-time windows may be created before the module finishes registering its window hook.
	 * Rehydrating from {@code WindowManagerService.mWindowMap} once WMS is ready closes that gap.
	 */
	private void hookWindowManagerReady(ClassLoader classLoader) {
		Class<?> wmsClass = Reflect.findClass("com.android.server.wm.WindowManagerService", classLoader);
		Method systemReady = Reflect.findMethod(wmsClass, "systemReady");
		if (systemReady == null) {
			log(Log.WARN, TAG, "WindowManagerService#systemReady not found");
			return;
		}
		hook(systemReady)
				.setExceptionMode(ExceptionMode.PROTECTIVE)
				.intercept(chain -> {
					Object result = chain.proceed();
					try {
						SystemWindowController controller = windowController;
						if (controller != null) {
							controller.rehydrateExistingWindows();
						}
					} catch (Throwable t) {
						logWindowFailure(t);
					}
					return result;
				});
		log(Log.INFO, TAG, "hooked WindowManagerService#systemReady");
	}

	private void logWindowFailure(Throwable t) {
		if (windowFailureLogged.compareAndSet(false, true)) {
			log(Log.ERROR, TAG, "window registration failed", t);
		}
	}
}
