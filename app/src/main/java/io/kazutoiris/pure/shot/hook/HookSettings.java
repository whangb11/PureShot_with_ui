package io.kazutoiris.pure.shot.hook;

import android.os.SystemClock;

import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import io.kazutoiris.pure.shot.BuildConfig;
import io.kazutoiris.pure.shot.PrefKeys;

/**
 * Reads PureShot settings from the module's shared preferences inside system_server.
 *
 * <p>{@link XSharedPreferences} performs file IO, so results are cached in an immutable snapshot
 * for a short TTL. Any failure falls back to "everything enabled", matching the legacy behaviour
 * of the module and never letting an exception escape into system_server.
 */
public final class HookSettings {

	private static final long TTL_MS = 1000L;

	private static volatile Snapshot snapshot;
	private static boolean loadFailureLogged;

	private HookSettings() {
	}

	public static Snapshot get() {
		Snapshot current = snapshot;
		long now = SystemClock.elapsedRealtime();
		if (current != null && now - current.loadedAt < TTL_MS) {
			return current;
		}
		synchronized (HookSettings.class) {
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

	private static Snapshot load(long now) {
		try {
			XSharedPreferences prefs = new XSharedPreferences(BuildConfig.APPLICATION_ID, PrefKeys.PREFS_FILE);
			prefs.reload();
			return new Snapshot(
					readBoolean(prefs, PrefKeys.KEY_MODULE_ENABLED, PrefKeys.DEFAULT_MODULE_ENABLED),
					readBoolean(prefs, PrefKeys.KEY_HIDE_STATUS_BAR, PrefKeys.DEFAULT_HIDE_STATUS_BAR),
					readBoolean(prefs, PrefKeys.KEY_HIDE_NAVIGATION_BAR, PrefKeys.DEFAULT_HIDE_NAVIGATION_BAR),
					readBoolean(prefs, PrefKeys.KEY_HIDE_INPUT_METHOD, PrefKeys.DEFAULT_HIDE_INPUT_METHOD),
					readBoolean(prefs, PrefKeys.KEY_HIDE_TOAST, PrefKeys.DEFAULT_HIDE_TOAST),
					readBoolean(prefs, PrefKeys.KEY_HIDE_OVERLAYS, PrefKeys.DEFAULT_HIDE_OVERLAYS),
					readBoolean(prefs, PrefKeys.KEY_HIDE_OTHER_SYSTEM_UI, PrefKeys.DEFAULT_HIDE_OTHER_SYSTEM_UI),
					now
			);
		} catch (Throwable t) {
			logFailureOnce(t);
			return Snapshot.defaults(now);
		}
	}

	private static boolean readBoolean(XSharedPreferences prefs, String key, boolean defaultValue) {
		try {
			return prefs.getBoolean(key, defaultValue);
		} catch (ClassCastException e) {
			return defaultValue;
		}
	}

	private static void logFailureOnce(Throwable t) {
		synchronized (HookSettings.class) {
			if (loadFailureLogged) {
				return;
			}
			loadFailureLogged = true;
		}
		XposedBridge.log("PureShot: failed to read settings, using defaults");
		XposedBridge.log(t);
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
