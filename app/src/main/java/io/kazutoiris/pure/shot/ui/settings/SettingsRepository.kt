package io.kazutoiris.pure.shot.ui.settings

import android.content.Context
import android.content.SharedPreferences
import io.kazutoiris.pure.shot.PrefKeys
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences = openPrefs(context.applicationContext)

    private val _state = MutableStateFlow(readState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        _state.value = readState()
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun initialize(): Boolean {
        val written = ensureDefaults()
        _state.value = readState()
        RemoteSettingsSync.pushAll(prefs)
        return written
    }

    fun setBoolean(key: String, value: Boolean): Boolean {
        val saved = runCatching {
            prefs.edit().putBoolean(key, value).commit()
        }.getOrDefault(false)
        if (saved) {
            _state.value = readState()
            RemoteSettingsSync.pushAll(prefs)
        }
        return saved
    }

    fun resetToDefaults(): Boolean {
        val saved = runCatching {
            val editor = prefs.edit()
            defaultValues.forEach { (key, value) ->
                editor.putBoolean(key, value)
            }
            editor.commit()
        }.getOrDefault(false)
        if (saved) {
            _state.value = readState()
            RemoteSettingsSync.pushAll(prefs)
        }
        return saved
    }

    fun close() {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    private fun ensureDefaults(): Boolean = runCatching {
        val editor = prefs.edit()
        var changed = false
        defaultValues.forEach { (key, value) ->
            if (!prefs.contains(key)) {
                editor.putBoolean(key, value)
                changed = true
            }
        }
        if (changed) {
            editor.commit()
        } else {
            true
        }
    }.getOrDefault(false)

    private fun readState(): SettingsState = SettingsState(
        moduleEnabled = prefs.readBoolean(PrefKeys.KEY_MODULE_ENABLED, PrefKeys.DEFAULT_MODULE_ENABLED),
        hideStatusBar = prefs.readBoolean(PrefKeys.KEY_HIDE_STATUS_BAR, PrefKeys.DEFAULT_HIDE_STATUS_BAR),
        hideNavigationBar = prefs.readBoolean(PrefKeys.KEY_HIDE_NAVIGATION_BAR, PrefKeys.DEFAULT_HIDE_NAVIGATION_BAR),
        hideInputMethod = prefs.readBoolean(PrefKeys.KEY_HIDE_INPUT_METHOD, PrefKeys.DEFAULT_HIDE_INPUT_METHOD),
        hideToast = prefs.readBoolean(PrefKeys.KEY_HIDE_TOAST, PrefKeys.DEFAULT_HIDE_TOAST),
        hideOverlays = prefs.readBoolean(PrefKeys.KEY_HIDE_OVERLAYS, PrefKeys.DEFAULT_HIDE_OVERLAYS),
        hideOtherSystemUi = prefs.readBoolean(PrefKeys.KEY_HIDE_OTHER_SYSTEM_UI, PrefKeys.DEFAULT_HIDE_OTHER_SYSTEM_UI),
    )

    private fun SharedPreferences.readBoolean(key: String, default: Boolean): Boolean =
        runCatching { getBoolean(key, default) }.getOrDefault(default)

    companion object {
        internal val defaultValues = linkedMapOf(
            PrefKeys.KEY_MODULE_ENABLED to PrefKeys.DEFAULT_MODULE_ENABLED,
            PrefKeys.KEY_HIDE_STATUS_BAR to PrefKeys.DEFAULT_HIDE_STATUS_BAR,
            PrefKeys.KEY_HIDE_NAVIGATION_BAR to PrefKeys.DEFAULT_HIDE_NAVIGATION_BAR,
            PrefKeys.KEY_HIDE_INPUT_METHOD to PrefKeys.DEFAULT_HIDE_INPUT_METHOD,
            PrefKeys.KEY_HIDE_TOAST to PrefKeys.DEFAULT_HIDE_TOAST,
            PrefKeys.KEY_HIDE_OVERLAYS to PrefKeys.DEFAULT_HIDE_OVERLAYS,
            PrefKeys.KEY_HIDE_OTHER_SYSTEM_UI to PrefKeys.DEFAULT_HIDE_OTHER_SYSTEM_UI,
        )

        internal fun openPrefs(context: Context): SharedPreferences =
            context.getSharedPreferences(PrefKeys.PREFS_FILE, Context.MODE_PRIVATE)
    }
}
