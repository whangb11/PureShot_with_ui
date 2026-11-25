package io.kazutoiris.pure.shot.hook;

import android.content.SharedPreferences;
import android.os.SystemClock;
import android.util.Log;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import io.github.libxposed.api.XposedInterface;
import io.kazutoiris.pure.shot.PrefKeys;

/**
 * Reads PureShot settings inside system_server from the libxposed remote preference group.
 *
 * <p>The store keeps an immutable {@link Snapshot} and registers a change listener so settings
 * changes can be applied to already-existing system windows without restarting System UI. A short
 * TTL re-read is kept as a safety net for frameworks that drop listener callbacks. Any failure
 * falls back to "everything enabled", matching the legacy behaviour, and never escapes into
 * system_server.
 *
 * <p>The snapshot also carries the decoded per-app configuration; {@link #isEnabled} delegates to
 * {@link PolicyResolver} so global switches, the per-app master switch and feature overrides all
 * follow one unit-tested truth table.
 */
public final class PolicyStore {

	private static final String TAG = "PureShot";
	private static final long TTL_MS = 5000L;
	private static final int MAX_WARNING_LOGS = 32;

	private final XposedInterface xposed;
	private final Runnable onChanged;
	private final SharedPreferences.OnSharedPreferenceChangeListener changeListener;
	private final Set<String> loggedWarnings = ConcurrentHashMap.newKeySet();

	private volatile Snapshot snapshot;
	private SharedPreferences prefs;
	private boolean loadFailureLogged;
	private volatile boolean stopped;

	public PolicyStore(XposedInterface xposed, Runnable onChanged) {
		this.xposed = xposed;
		this.onChanged = onChanged;
		this.changeListener = (sharedPreferences, key) -> {
			snapshot = null;
			xposed.log(Log.INFO, TAG, "remote preference changed: " + key);
			Runnable callback = this.onChanged;
			if (callback != null) {
				try {
					callback.run();
				} catch (Throwable t) {
					logFailureOnce(t);
				}
			}
		};
	}

	/**
	 * Resolves the remote preference group, starts listening for changes and loads the first
	 * snapshot. Safe to call outside of hook callbacks.
	 */
	public void start() {
		get();
	}

	public boolean isEnabled(Feature feature, String packageName) {
		return get().isEnabled(feature, packageName);
	}

	public Snapshot get() {
		if (stopped) {
			return Snapshot.defaults(SystemClock.elapsedRealtime());
		}
		Snapshot current = snapshot;
		long now = SystemClock.elapsedRealtime();
		if (current != null && now - current.loadedAt < TTL_MS) {
			return current;
		}
		synchronized (this) {
			current = snapshot;
			now = SystemClock.elapsedRealtime();
			if (current != null && now - current.loadedAt < TTL_MS) {
				return current;
			}
			Snapshot loaded = load(now);
			snapshot = loaded;
			return loaded;
		}
	}

	/**
	 * Releases the remote preference listener. Used by the hot reload lifecycle in later phases.
	 */
	public void shutdown() {
		synchronized (this) {
			stopped = true;
			SharedPreferences current = prefs;
			prefs = null;
			snapshot = null;
			if (current != null) {
				try {
					current.unregisterOnSharedPreferenceChangeListener(changeListener);
				} catch (Throwable t) {
					logFailureOnce(t);
				}
			}
		}
	}

	private Snapshot load(long now) {
		if (stopped) {
			return Snapshot.defaults(now);
		}
		try {
			SharedPreferences current = prefs;
			if (current == null) {
				current = xposed.getRemotePreferences(PrefKeys.REMOTE_PREFS_GROUP);
				current.registerOnSharedPreferenceChangeListener(changeListener);
				prefs = current;
			}
			return Snapshot.from(current, now, this::logWarningOnce);
		} catch (Throwable t) {
			logFailureOnce(t);
			return Snapshot.defaults(now);
		}
	}

	private void logFailureOnce(Throwable t) {
		synchronized (this) {
			if (loadFailureLogged) {
				return;
			}
			loadFailureLogged = true;
		}
		xposed.log(Log.ERROR, TAG, "failed to read remote settings, using defaults", t);
	}

	private void logWarningOnce(String message) {
		if (loggedWarnings.size() >= MAX_WARNING_LOGS || !loggedWarnings.add(message)) {
			return;
		}
		xposed.log(Log.WARN, TAG, message);
	}

	/**
	 * Immutable global setting values plus the decoded per-app configuration.
	 */
	public static final class Snapshot implements PolicyResolver.Globals {
		private final boolean moduleEnabled;
		private final boolean hideStatusBar;
		private final boolean hideNavigationBar;
		private final boolean hideInputMethod;
		private final boolean hideToast;
		private final boolean hideOverlays;
		private final boolean hideOtherSystemUi;
		private final PerAppConfig perApp;
		private final long loadedAt;

		private Snapshot(boolean moduleEnabled, boolean hideStatusBar, boolean hideNavigationBar,
				boolean hideInputMethod, boolean hideToast, boolean hideOverlays,
				boolean hideOtherSystemUi, PerAppConfig perApp, long loadedAt) {
			this.moduleEnabled = moduleEnabled;
			this.hideStatusBar = hideStatusBar;
			this.hideNavigationBar = hideNavigationBar;
			this.hideInputMethod = hideInputMethod;
			this.hideToast = hideToast;
			this.hideOverlays = hideOverlays;
			this.hideOtherSystemUi = hideOtherSystemUi;
			this.perApp = perApp != null ? perApp : PerAppConfig.EMPTY;
			this.loadedAt = loadedAt;
		}

		static Snapshot from(SharedPreferences prefs, long loadedAt, Consumer<String> warning) {
			return new Snapshot(
					readBoolean(prefs, PrefKeys.KEY_MODULE_ENABLED, PrefKeys.DEFAULT_MODULE_ENABLED),
					readBoolean(prefs, PrefKeys.KEY_HIDE_STATUS_BAR, PrefKeys.DEFAULT_HIDE_STATUS_BAR),
					readBoolean(prefs, PrefKeys.KEY_HIDE_NAVIGATION_BAR, PrefKeys.DEFAULT_HIDE_NAVIGATION_BAR),
					readBoolean(prefs, PrefKeys.KEY_HIDE_INPUT_METHOD, PrefKeys.DEFAULT_HIDE_INPUT_METHOD),
					readBoolean(prefs, PrefKeys.KEY_HIDE_TOAST, PrefKeys.DEFAULT_HIDE_TOAST),
					readBoolean(prefs, PrefKeys.KEY_HIDE_OVERLAYS, PrefKeys.DEFAULT_HIDE_OVERLAYS),
					readBoolean(prefs, PrefKeys.KEY_HIDE_OTHER_SYSTEM_UI, PrefKeys.DEFAULT_HIDE_OTHER_SYSTEM_UI),
					PerAppConfig.decode(
							readString(prefs, PrefKeys.KEY_PER_APP_CONFIG,
									PrefKeys.DEFAULT_PER_APP_CONFIG),
							warning),
					loadedAt
			);
		}

		static Snapshot defaults(long loadedAt) {
			return new Snapshot(
					PrefKeys.DEFAULT_MODULE_ENABLED,
					PrefKeys.DEFAULT_HIDE_STATUS_BAR,
					PrefKeys.DEFAULT_HIDE_NAVIGATION_BAR,
					PrefKeys.DEFAULT_HIDE_INPUT_METHOD,
					PrefKeys.DEFAULT_HIDE_TOAST,
					PrefKeys.DEFAULT_HIDE_OVERLAYS,
					PrefKeys.DEFAULT_HIDE_OTHER_SYSTEM_UI,
					PerAppConfig.EMPTY,
					loadedAt
			);
		}

		private static boolean readBoolean(SharedPreferences prefs, String key, boolean defaultValue) {
			try {
				return prefs.getBoolean(key, defaultValue);
			} catch (Throwable ignored) {
				return defaultValue;
			}
		}

		private static String readString(SharedPreferences prefs, String key, String defaultValue) {
			try {
				String value = prefs.getString(key, defaultValue);
				return value != null ? value : defaultValue;
			} catch (Throwable ignored) {
				return defaultValue;
			}
		}

		public PerAppConfig getPerApp() {
			return perApp;
		}

		@java.lang.Override
		public boolean isModuleEnabled() {
			return moduleEnabled;
		}

		@java.lang.Override
		public boolean isFeatureEnabled(Feature feature) {
			if (feature == null) {
				return false;
			}
			switch (feature) {
				case STATUS_BAR:
					return hideStatusBar;
				case NAVIGATION_BAR:
					return hideNavigationBar;
				case INPUT_METHOD:
					return hideInputMethod;
				case TOAST:
					return hideToast;
				case OVERLAY:
					return hideOverlays;
				case OTHER:
					return hideOtherSystemUi;
				default:
					return false;
			}
		}

		public boolean isEnabled(Feature feature, String packageName) {
			return PolicyResolver.isEnabled(this, perApp.getApps(), feature, packageName);
		}
	}
}
