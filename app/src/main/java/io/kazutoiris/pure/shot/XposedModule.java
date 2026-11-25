package io.kazutoiris.pure.shot;

import android.os.Build;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.WindowManager;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModuleInterface;
import io.kazutoiris.pure.shot.hook.Feature;
import io.kazutoiris.pure.shot.hook.HookSettings;
import io.kazutoiris.pure.shot.hook.Reflect;
import io.kazutoiris.pure.shot.hook.WindowTypeFeatureMapper;

/**
 * libxposed API 102 entry point.
 *
 * <p>The module is scoped to {@code system} and only initializes inside system_server. Hooks are
 * installed from {@link #onSystemServerStarting} and never throw into the system process.
 */
public class XposedModule extends io.github.libxposed.api.XposedModule {

	private static final String TAG = "PureShot";

	private final AtomicBoolean windowFailureLogged = new AtomicBoolean();
	private volatile HookSettings settings;
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

	private synchronized void initializeSystemServer(ClassLoader classLoader) {
		if (initialized) {
			log(Log.WARN, TAG, "system_server hooks already initialized");
			return;
		}
		initialized = true;

		settings = new HookSettings(this);
		log(Log.INFO, TAG, "initializing system_server hooks, SDK=" + Build.VERSION.SDK_INT);
		try {
			if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.BAKLAVA) {
				// API 31-34 and 35-36 keep the existing constructor-based behaviour.
				hookSurfaceControlConstructor(classLoader);
			} else {
				// API 37+ (use_client_surface): the client owns its buffer surface, so mark the
				// server-owned WindowState container surface instead.
				hookWindowState(classLoader);
			}
		} catch (Throwable t) {
			log(Log.ERROR, TAG, "failed to install system_server hooks", t);
		}
	}

	private boolean shouldProcessWindow(int windowType) {
		try {
			HookSettings current = settings;
			Feature feature = WindowTypeFeatureMapper.map(windowType);
			return current == null || current.get().isEnabled(feature);
		} catch (Throwable t) {
			// Never let settings failures break system_server; fall back to "hide".
			return true;
		}
	}

	private void hookSurfaceControlConstructor(ClassLoader classLoader) {
		Class<?> surfaceControlClass = Reflect.findClass("android.view.SurfaceControl", classLoader);
		if (surfaceControlClass == null) {
			return;
		}
		for (Constructor<?> constructor : surfaceControlClass.getDeclaredConstructors()) {
			if (constructor.getParameterCount() != 10) {
				continue;
			}
			hook(constructor)
					.setExceptionMode(ExceptionMode.PROTECTIVE)
					.intercept(chain -> {
						Object[] args = chain.getArgs().toArray();
						String callsite = args[9] instanceof String ? (String) args[9] : null;
						if (Objects.equals(callsite, "WindowSurfaceController")) {
							SparseIntArray metadata = args[7] instanceof SparseIntArray
									? (SparseIntArray) args[7] : null;
							if (metadata != null) {
								int windowType = metadata.get(2, -1);
								if (windowType >= WindowManager.LayoutParams.FIRST_SYSTEM_WINDOW
										&& windowType != WindowManager.LayoutParams.TYPE_WALLPAPER
										&& shouldProcessWindow(windowType)) {
									if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S) {
										metadata.put(2, 441731);
									} else {
										int flags = args[5] instanceof Integer ? (Integer) args[5] : 0;
										args[5] = flags | (1 << 6);
									}
								}
							}
						}
						return chain.proceed(args);
					});
			log(Log.INFO, TAG, "hooked SurfaceControl 10-arg constructor");
			return;
		}
		log(Log.WARN, TAG, "SurfaceControl 10-arg constructor not found");
	}

	private void hookWindowState(ClassLoader classLoader) {
		Class<?> windowStateClass = Reflect.findClass("com.android.server.wm.WindowState", classLoader);
		if (windowStateClass == null) {
			return;
		}
		Method target = Reflect.findMethod(windowStateClass, "setInitialSurfaceControlProperties",
				android.view.SurfaceControl.Builder.class);
		Field attrsField = Reflect.findField(windowStateClass, "mAttrs");
		Field surfaceField = Reflect.findField(windowStateClass, "mSurfaceControl");
		Method getPendingTransaction = Reflect.findMethod(windowStateClass, "getPendingTransaction");
		Class<?> transactionClass = Reflect.findClass("android.view.SurfaceControl$Transaction", classLoader);
		Method setSkipScreenshot = Reflect.findMethod(transactionClass, "setSkipScreenshot",
				android.view.SurfaceControl.class, boolean.class);
		if (target == null || attrsField == null || surfaceField == null
				|| getPendingTransaction == null || setSkipScreenshot == null) {
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
						if (attrsObject instanceof WindowManager.LayoutParams) {
							int windowType = ((WindowManager.LayoutParams) attrsObject).type;
							if (windowType >= WindowManager.LayoutParams.FIRST_SYSTEM_WINDOW
									&& windowType != WindowManager.LayoutParams.TYPE_WALLPAPER
									&& shouldProcessWindow(windowType)) {
								Object surfaceControl = Reflect.get(surfaceField, thisObject);
								if (surfaceControl != null) {
									Object transaction = Reflect.call(getPendingTransaction, thisObject);
									if (transaction != null) {
										Reflect.call(setSkipScreenshot, transaction, surfaceControl, true);
									}
								}
							}
						}
					} catch (Throwable t) {
						if (windowFailureLogged.compareAndSet(false, true)) {
							log(Log.ERROR, TAG, "failed to apply SKIP_SCREENSHOT", t);
						}
					}
					return result;
				});
		log(Log.INFO, TAG, "hooked WindowState#setInitialSurfaceControlProperties");
	}
}
