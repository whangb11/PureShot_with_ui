package io.kazutoiris.pure.shot.ui.settings

import io.kazutoiris.pure.shot.PrefKeys

data class SettingsState(
    val moduleEnabled: Boolean = PrefKeys.DEFAULT_MODULE_ENABLED,
    val hideStatusBar: Boolean = PrefKeys.DEFAULT_HIDE_STATUS_BAR,
    val hideNavigationBar: Boolean = PrefKeys.DEFAULT_HIDE_NAVIGATION_BAR,
    val hideInputMethod: Boolean = PrefKeys.DEFAULT_HIDE_INPUT_METHOD,
    val hideToast: Boolean = PrefKeys.DEFAULT_HIDE_TOAST,
    val hideOverlays: Boolean = PrefKeys.DEFAULT_HIDE_OVERLAYS,
    val hideOtherSystemUi: Boolean = PrefKeys.DEFAULT_HIDE_OTHER_SYSTEM_UI,
) {
    companion object {
        val Default = SettingsState()
    }
}
