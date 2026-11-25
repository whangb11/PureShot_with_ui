package io.kazutoiris.pure.shot.ui.settings

import android.content.SharedPreferences
import io.github.libxposed.service.XposedService
import io.kazutoiris.pure.shot.PrefKeys

/**
 * Phase 1 bridge: mirrors the seven global booleans into libxposed RemotePreferences so
 * system_server can read them without the legacy shared-preference bridge.
 */
internal object RemoteSettingsSync {

    @Volatile
    private var service: XposedService? = null

    fun bind(service: XposedService, prefs: SharedPreferences) {
        this.service = service
        pushAll(prefs)
    }

    fun unbind(service: XposedService) {
        if (this.service === service) {
            this.service = null
        }
    }

    fun pushAll(prefs: SharedPreferences) {
        val current = service ?: return
        runCatching {
            val editor = current.getRemotePreferences(PrefKeys.REMOTE_PREFS_GROUP).edit()
            SettingsRepository.defaultValues.forEach { (key, defaultValue) ->
                editor.putBoolean(key, prefs.getBoolean(key, defaultValue))
            }
            editor.commit()
        }
    }
}
