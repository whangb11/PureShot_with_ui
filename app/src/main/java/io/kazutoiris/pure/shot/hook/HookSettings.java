package io.kazutoiris.pure.shot.hook;

import android.content.SharedPreferences;
import android.os.SystemClock;
import android.util.Log;

import io.github.libxposed.api.XposedInterface;
import io.kazutoiris.pure.shot.PrefKeys;

/**
 * Reads PureShot settings inside system_server from the libxposed remote preference group.
 *
 * <p>Results are cached in an immutable snapshot for a short TTL and invalidated immediately when
 * the framework reports a preference change. Any failure falls back to "everything enabled",
 * matching the legacy behaviour and never letting an exception escape into system_server.
 */
public final class HookSettings {

	private static final long TTL_MS = 1000L;

	private final XposedInterface xposed;
	private final SharedPreferences.OnSharedPreferenceChangeListener changeListener =
			(sharedPreferences, key) -> snapshot = null;

	private volatile Snapshot snapshot;
	private SharedPreferences prefs;
	private boolean loadFailureLogged;

	public HookSettings(XposedInterface xposed) {
		this.xposed = xposed;
	}

	public Snapshot get() {
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

	private Snapshot load(long now) {
		try {
			SharedPreferences current = prefs;
			if (current == null) {
				current = xposed.getRemotePreferences(PrefKeys.REMOTE_PREFS_GROUP);
				current.registerOnSharedPreferenceChangeListener(changeListener);
				prefs = current;
			}
			return new Snapshot(
					readBoolean(current, PrefKeys.KEY_MODULE_ENABLED, PrefKeys.DEFAULT_MODULE_ENABLED),
					readBoolean(current, PrefKeys.KEY_HIDE_STATUS_BAR, PrefKeys.DEFAULT_HIDE_STATUS_BAR),
					readBoolean(current, PrefKeys.KEY_HIDE_NAVIGATION_BAR, PrefKeys.DEFAULT_HIDE_NAVIGATION_BAR),
					readBoolean(current, PrefKeys.KEY_HIDE_INPUT_METHOD, PrefKeys.DEFAULT_HIDE_INPUT_METHOD),
					readBoolean(current, PrefKeys.KEY_HIDE_TOAST, PrefKeys.DEFAULT_HIDE_TOAST),
					readBoolean(current, PrefKeys.KEY_HIDE_OVERLAYS, PrefKeys.DEFAULT_HIDE_OVERLAYS),
					readBoolean(current, PrefKeys.KEY_HIDE_OTHER_SYSTEM_UI, PrefKeys.DEFAULT_HIDE_OTHER_SYSTEM_UI),
					now
			);
		} catch (Throwable t) {
			logFailureOnce(t);
			return Snapshot.defaults(now);
		}
	}

	private static boolean readBoolean(SharedPreferences prefs, String key, boolean defaultValue) {
		try {
			return prefs.getBoolean(key, defaultValue);
		} catch (ClassCastException e) {
			return defaultValue;
		}
	}

	private void logFailureOnce(Throwable t) {
		synchronized (this) {
			if (loadFailureLogged) {
				return;
			}
			loadFailureLogged = true;
		}
		xposed.log(Log.ERROR, "PureShot", "failed to read remote settings, using defaults", t);
	}

	public static final class Snapshot {
		private final boolean moduleEnabled;
		private final boolean hideStatusBar;
		private final boolean hideNavigationBar;
		private final boolean hideInputMethod;
		private final boolean hideToast;
		private final boolean hideOverlays;
		private final boolean hideOtherSystemUi;
		private final long loadedAt;

		private Snapshot(boolean moduleEnabled, boolean hideStatusBar, boolean hideNavigationBar,
				boolean hideInputMethod, boolean hideToast, boolean hideOverlays,
				boolean hideOtherSystemUi, long loadedAt) {
			this.moduleEnabled = moduleEnabled;
			this.hideStatusBar = hideStatusBar;
			this.hideNavigationBar = hideNavigationBar;
			this.hideInputMethod = hideInputMethod;
			this.hideToast = hideToast;
			this.hideOverlays = hideOverlays;
			this.hideOtherSystemUi = hideOtherSystemUi;
			this.loadedAt = loadedAt;
		}

		private static Snapshot defaults(long loadedAt) {
			return new Snapshot(
					PrefKeys.DEFAULT_MODULE_ENABLED,
					PrefKeys.DEFAULT_HIDE_STATUS_BAR,
					PrefKeys.DEFAULT_HIDE_NAVIGATION_BAR,
					PrefKeys.DEFAULT_HIDE_INPUT_METHOD,
					PrefKeys.DEFAULT_HIDE_TOAST,
					PrefKeys.DEFAULT_HIDE_OVERLAYS,
					PrefKeys.DEFAULT_HIDE_OTHER_SYSTEM_UI,
					loadedAt
			);
		}

		public boolean isEnabled(Feature feature) {
			if (!moduleEnabled || feature == null || feature == Feature.NONE) {
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
	}
}
