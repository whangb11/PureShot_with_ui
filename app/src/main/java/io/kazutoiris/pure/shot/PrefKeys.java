package io.kazutoiris.pure.shot;

public final class PrefKeys {
	public static final String PREFS_FILE = "pure_shot_settings";
	public static final String REMOTE_PREFS_GROUP = "pure_shot";

	public static final String KEY_MODULE_ENABLED = "module_enabled";
	public static final String KEY_HIDE_STATUS_BAR = "hide_status_bar";
	public static final String KEY_HIDE_NAVIGATION_BAR = "hide_navigation_bar";
	public static final String KEY_HIDE_INPUT_METHOD = "hide_input_method";
	public static final String KEY_HIDE_TOAST = "hide_toast";
	public static final String KEY_HIDE_OVERLAYS = "hide_overlays";
	public static final String KEY_HIDE_OTHER_SYSTEM_UI = "hide_other_system_ui";
	public static final String KEY_PER_APP_CONFIG = "per_app_config";
	public static final String KEY_APP_LIST_VISIBILITY = "app_list_visibility";

	/** Local-only bookkeeping: the mirror still has changes that were not pushed remotely. */
	public static final String KEY_PENDING_SYNC = "pending_sync";

	/** Empty per-app configuration; kept in sync with {@code PerAppConfig.VERSION}. */
	public static final String DEFAULT_PER_APP_CONFIG = "{\"version\":1,\"apps\":{}}";
	public static final int DEFAULT_APP_LIST_VISIBILITY = 1;

	public static final boolean DEFAULT_MODULE_ENABLED = true;
	public static final boolean DEFAULT_HIDE_STATUS_BAR = true;
	public static final boolean DEFAULT_HIDE_NAVIGATION_BAR = true;
	public static final boolean DEFAULT_HIDE_INPUT_METHOD = true;
	public static final boolean DEFAULT_HIDE_TOAST = true;
	public static final boolean DEFAULT_HIDE_OVERLAYS = true;
	public static final boolean DEFAULT_HIDE_OTHER_SYSTEM_UI = true;

	private PrefKeys() {
	}
}
