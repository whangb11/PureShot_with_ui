package io.kazutoiris.pure.shot.hook;

import android.content.ComponentName;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import io.github.libxposed.api.XposedInterface;

/**
 * Tracks the package name of the top resumed activity inside system_server.
 *
 * <p>The tracker returns {@code com.android.systemui} whenever no package has been observed or the
 * keyguard is showing, which is the fallback used for lock screen / AOD policy.
 *
 * <p>All callbacks are moved to the system_server main looper so no module-owned thread is created.
 */
public final class ForegroundAppTracker {

	public static final String FALLBACK_PACKAGE = "com.android.systemui";

	private static final String TAG = "PureShot";

	private final XposedInterface xposed;
	private final Runnable onForegroundChanged;
	private final Handler handler = new Handler(Looper.getMainLooper());

	private volatile String currentPackage;
	private volatile boolean stopped;
	private Field packageNameField;
	private Field activityComponentField;
	private Field lastResumedField;
	private Object atmService;
	private Object keyguardController;
	private Method keyguardMethod;
	private boolean keyguardUsesDisplayId;
	private boolean keyguardResolved;
	private boolean failureLogged;

	public ForegroundAppTracker(XposedInterface xposed, Runnable onForegroundChanged) {
		this.xposed = xposed;
		this.onForegroundChanged = onForegroundChanged;
	}

	public void start(ClassLoader classLoader) {
		if (stopped) {
			return;
		}
		try {
			Class<?> atmClass = Reflect.findClass(
					"com.android.server.wm.ActivityTaskManagerService", classLoader);
			Class<?> recordClass = Reflect.findClass(
					"com.android.server.wm.ActivityRecord", classLoader);
			if (atmClass == null || recordClass == null) {
				logOnce(Log.WARN, "ActivityTaskManagerService/ActivityRecord unavailable", null);
				return;
			}

			packageNameField = Reflect.findField(recordClass, "packageName");
			activityComponentField = Reflect.findField(recordClass, "mActivityComponent");
			lastResumedField = Reflect.findField(atmClass, "mLastResumedActivity");

			Method target = findResumeMethod(atmClass, recordClass);
			if (target == null) {
				logOnce(Log.WARN, "setLastResumedActivityUncheckLocked not found", null);
				return;
			}

			xposed.hook(target)
					.setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
					.intercept(chain -> {
						Object result = chain.proceed();
						try {
							Object thisObject = chain.getThisObject();
							if (thisObject != null) {
								atmService = thisObject;
							}
							List<Object> args = chain.getArgs();
							Object record = args.isEmpty() ? null : args.get(0);
							if (updateFromRecord(record) == null) {
								readLastResumed(thisObject);
							}
						} catch (Throwable t) {
							logOnce(Log.WARN, "failed to read resumed activity", t);
						}
						return result;
					});

			handler.post(this::readInitialState);
			xposed.log(Log.INFO, TAG, "foreground app tracker hooked");
		} catch (Throwable t) {
			logOnce(Log.WARN, "failed to install foreground app tracker", t);
		}
	}

	public String packageForPolicy() {
		if (isKeyguardShowing()) {
			return FALLBACK_PACKAGE;
		}
		String packageName = currentPackage;
		return packageName != null ? packageName : FALLBACK_PACKAGE;
	}

	public void shutdown() {
		stopped = true;
		handler.removeCallbacksAndMessages(null);
	}

	/**
	 * Finds the resume hook without hard-coding the method signature. Android 14-17 use
	 * {@code (ActivityRecord, String)} but the parameter list has changed before.
	 */
	private static Method findResumeMethod(Class<?> atmClass, Class<?> recordClass) {
		for (Method method : atmClass.getDeclaredMethods()) {
			if (!"setLastResumedActivityUncheckLocked".equals(method.getName())) {
				continue;
			}
			Class<?>[] parameters = method.getParameterTypes();
			if (parameters.length >= 1 && parameters[0].isAssignableFrom(recordClass)) {
				try {
					method.setAccessible(true);
				} catch (Throwable ignored) {
					// Try to hook anyway; the framework may already allow access.
				}
				return method;
			}
		}
		return null;
	}

	private void readInitialState() {
		Object service = atmService;
		if (service == null) {
			service = lookupActivityTaskManager();
			atmService = service;
		}
		readLastResumed(service);
	}

	private void readLastResumed(Object service) {
		if (service == null || lastResumedField == null) {
			return;
		}
		try {
			Object record = Reflect.get(lastResumedField, service);
			updateFromRecord(record);
		} catch (Throwable t) {
			logOnce(Log.WARN, "failed to read mLastResumedActivity", t);
		}
	}

	/**
	 * Lock screen and AOD show no resumed activity of the user's app; use the System UI policy so
	 * per-app overrides never leak into the lock screen.
	 */
	private boolean isKeyguardShowing() {
		Method method = keyguardMethod;
		if (method == null && !keyguardResolved) {
			resolveKeyguardController();
			method = keyguardMethod;
		}
		Object controller = keyguardController;
		if (method == null || controller == null) {
			return false;
		}
		try {
			Object result = keyguardUsesDisplayId
					? method.invoke(controller, 0)
					: method.invoke(controller);
			return result instanceof Boolean && (Boolean) result;
		} catch (Throwable t) {
			logOnce(Log.WARN, "failed to read keyguard state", t);
			return false;
		}
	}

	private void resolveKeyguardController() {
		if (keyguardResolved) {
			return;
		}
		try {
			// KeyguardController is owned by ActivityTaskManagerService on modern Android
			// releases; WindowManagerService used to expose it on some versions.
			Object controller = keyguardControllerOf(
					atmService != null ? atmService : lookupActivityTaskManager());
			if (controller == null) {
				controller = keyguardControllerOf(lookupService("window"));
			}
			if (controller == null) {
				return;
			}

			Method method = Reflect.findMethod(
					controller.getClass(), "isKeyguardOrAodShowing", int.class);
			boolean usesDisplayId = method != null;
			if (method == null) {
				method = Reflect.findMethod(controller.getClass(), "isKeyguardOrAodShowing");
			}
			if (method == null) {
				method = Reflect.findMethod(controller.getClass(), "isKeyguardShowing");
			}
			if (method == null) {
				return;
			}

			keyguardController = controller;
			keyguardMethod = method;
			keyguardUsesDisplayId = usesDisplayId;
			keyguardResolved = true;
			xposed.log(Log.INFO, TAG, "keyguard state available for policy fallback");
		} catch (Throwable t) {
			logOnce(Log.WARN, "failed to resolve keyguard controller", t);
		}
	}

	private static Object keyguardControllerOf(Object service) {
		if (service == null) {
			return null;
		}
		Field field = Reflect.findField(service.getClass(), "mKeyguardController");
		return Reflect.get(field, service);
	}

	private String updateFromRecord(Object record) {
		if (record == null || stopped) {
			return null;
		}
		String packageName = packageOf(record);
		if (packageName == null || packageName.isEmpty()) {
			return null;
		}
		if (packageName.equals(currentPackage)) {
			return packageName;
		}
		currentPackage = packageName;
		xposed.log(Log.INFO, TAG, "foreground app -> " + packageName);
		Runnable callback = onForegroundChanged;
		if (callback != null) {
			try {
				callback.run();
			} catch (Throwable t) {
				logOnce(Log.WARN, "foreground callback failed", t);
			}
		}
		return packageName;
	}

	private String packageOf(Object record) {
		Object packageName = Reflect.get(packageNameField, record);
		if (packageName instanceof String && !((String) packageName).isEmpty()) {
			return (String) packageName;
		}
		Object component = Reflect.get(activityComponentField, record);
		if (component instanceof ComponentName) {
			return ((ComponentName) component).getPackageName();
		}
		// Last resort: ActivityRecord#toString() contains "package/activity".
		String value = String.valueOf(record);
		int slash = value.indexOf('/');
		if (slash > 0) {
			int start = value.lastIndexOf(' ', slash);
			if (start >= 0 && start < slash - 1) {
				return value.substring(start + 1, slash);
			}
		}
		return null;
	}

	private Object lookupActivityTaskManager() {
		try {
			Object service = lookupService("activity_task");
			if (service != null && lastResumedField != null
					&& lastResumedField.getDeclaringClass().isInstance(service)) {
				return service;
			}
			Object wms = lookupService("window");
			if (wms != null) {
				Field atmField = Reflect.findField(wms.getClass(), "mAtmService");
				Object atm = Reflect.get(atmField, wms);
				if (atm != null) {
					return atm;
				}
			}
		} catch (Throwable t) {
			logOnce(Log.WARN, "failed to resolve ActivityTaskManagerService", t);
		}
		return null;
	}

	private static Object lookupService(String name) {
		Class<?> serviceManager = Reflect.findClass(
				"android.os.ServiceManager", ForegroundAppTracker.class.getClassLoader());
		Method getService = Reflect.findMethod(serviceManager, "getService", String.class);
		return Reflect.call(getService, null, name);
	}

	private void logOnce(int priority, String message, Throwable t) {
		synchronized (this) {
			if (failureLogged) {
				return;
			}
			failureLogged = true;
		}
		if (t == null) {
			xposed.log(priority, TAG, message);
		} else {
			xposed.log(priority, TAG, message, t);
		}
	}
}
