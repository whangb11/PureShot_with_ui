package io.kazutoiris.pure.shot.hook;

import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Small reflection helper for system_server internals.
 *
 * <p>Lookups are tolerant by design: a missing class/member is logged once and returns
 * {@code null} so a single Android version change cannot take down system_server.
 */
public final class Reflect {

	private static final String TAG = "PureShot";
	private static final Set<String> LOGGED_FAILURES = ConcurrentHashMap.newKeySet();

	private Reflect() {
	}

	public static Class<?> findClass(String name, ClassLoader classLoader) {
		try {
			return Class.forName(name, false, classLoader);
		} catch (Throwable t) {
			logOnce("class " + name, t);
			return null;
		}
	}

	public static Method findMethod(Class<?> clazz, String name, Class<?>... parameterTypes) {
		if (clazz == null) {
			return null;
		}
		for (Class<?> current = clazz; current != null; current = current.getSuperclass()) {
			try {
				Method method = current.getDeclaredMethod(name, parameterTypes);
				method.setAccessible(true);
				return method;
			} catch (NoSuchMethodException ignored) {
				// Try the superclass.
			} catch (Throwable t) {
				logOnce("method " + clazz.getName() + "#" + name, t);
				return null;
			}
		}
		logOnce("method " + clazz.getName() + "#" + name, null);
		return null;
	}

	public static Field findField(Class<?> clazz, String name) {
		if (clazz == null) {
			return null;
		}
		for (Class<?> current = clazz; current != null; current = current.getSuperclass()) {
			try {
				Field field = current.getDeclaredField(name);
				field.setAccessible(true);
				return field;
			} catch (NoSuchFieldException ignored) {
				// Try the superclass.
			} catch (Throwable t) {
				logOnce("field " + clazz.getName() + "#" + name, t);
				return null;
			}
		}
		logOnce("field " + clazz.getName() + "#" + name, null);
		return null;
	}

	public static Object get(Field field, Object target) {
		if (field == null || target == null) {
			return null;
		}
		try {
			return field.get(target);
		} catch (Throwable t) {
			logOnce("get " + field, t);
			return null;
		}
	}

	public static Object call(Method method, Object target, Object... args) {
		if (method == null) {
			return null;
		}
		try {
			return method.invoke(target, args);
		} catch (Throwable t) {
			logOnce("call " + method, t);
			return null;
		}
	}

	private static void logOnce(String operation, Throwable t) {
		if (!LOGGED_FAILURES.add(operation)) {
			return;
		}
		if (t == null) {
			Log.w(TAG, "reflection unavailable: " + operation);
		} else {
			Log.w(TAG, "reflection failed: " + operation, t);
		}
	}
}
