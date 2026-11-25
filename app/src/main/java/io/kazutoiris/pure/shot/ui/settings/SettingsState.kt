package io.kazutoiris.pure.shot.ui.settings

import io.kazutoiris.pure.shot.PrefKeys
import io.kazutoiris.pure.shot.hook.AppPolicy

/**
 * State of the local mirror relative to the framework's RemotePreferences.
 */
enum class SyncState {
    /** The libxposed service is not connected; edits are kept in local storage only. */
    LOCAL_ONLY,

    /** A sync pass is running. */
    SYNCING,

    /** Local values and the framework copy match. */
    SYNCED,

    /** Local edits exist but could not be pushed to the framework yet. */
    PENDING,

    /** The local mirror could not be written. */
    ERROR,
}

data class SettingsState(
    val moduleEnabled: Boolean = PrefKeys.DEFAULT_MODULE_ENABLED,
    val hideStatusBar: Boolean = PrefKeys.DEFAULT_HIDE_STATUS_BAR,
    val hideNavigationBar: Boolean = PrefKeys.DEFAULT_HIDE_NAVIGATION_BAR,
    val hideInputMethod: Boolean = PrefKeys.DEFAULT_HIDE_INPUT_METHOD,
    val hideToast: Boolean = PrefKeys.DEFAULT_HIDE_TOAST,
    val hideOverlays: Boolean = PrefKeys.DEFAULT_HIDE_OVERLAYS,
    val hideOtherSystemUi: Boolean = PrefKeys.DEFAULT_HIDE_OTHER_SYSTEM_UI,
    /** Explicit per-app overrides; apps that inherit everything are absent. */
    val perApp: Map<String, AppPolicy> = emptyMap(),
    val appListVisibility: AppListVisibility = AppListVisibility.Default,
    val syncState: SyncState = SyncState.LOCAL_ONLY,
) {
    companion object {
        val Default = SettingsState()
    }
}
