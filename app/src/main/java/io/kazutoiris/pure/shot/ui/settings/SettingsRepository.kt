package io.kazutoiris.pure.shot.ui.settings

import android.content.Context
import android.content.SharedPreferences
import io.github.libxposed.service.XposedService
import io.kazutoiris.pure.shot.PureShotApp
import io.kazutoiris.pure.shot.hook.Feature
import io.kazutoiris.pure.shot.hook.Override
import kotlinx.coroutines.flow.StateFlow

/**
 * UI-facing facade over the process-wide [SettingsStore].
 *
 * The real application shares one store so the service connection owned by [PureShotApp] reaches
 * every screen. Isolated contexts (instrumentation tests) get their own store.
 */
class SettingsRepository(context: Context) {

    private val appContext = context.applicationContext ?: context
    private val sharedStore = (appContext as? PureShotApp)?.settingsStore
    private val store = sharedStore ?: SettingsStore(appContext)
    private val ownsStore = sharedStore == null

    val state: StateFlow<SettingsState> = store.state

    /** The bound framework service; null means the framework is unavailable. */
    val service: StateFlow<XposedService?> = store.service

    fun initialize(): Boolean {
        val written = store.initialize()
        store.retryPendingSync()
        return written
    }

    fun setBoolean(key: String, value: Boolean): Boolean = store.setBoolean(key, value)

    fun retryPendingSync() = store.retryPendingSync()

    fun setAppMaster(packageName: String, master: Override): Boolean =
        store.setAppMaster(packageName, master)

    /** Pass `value = null` to clear the override and inherit the global switch. */
    fun setAppFeature(packageName: String, feature: Feature, value: Boolean?): Boolean =
        store.setAppFeature(packageName, feature, value)

    fun clearApp(packageName: String): Boolean = store.clearApp(packageName)

    fun setAppListVisibility(visibility: AppListVisibility): Boolean =
        store.setAppListVisibility(visibility)

    fun setPerAppConfigJson(json: String): Boolean = store.setPerAppConfigJson(json)

    /** Resets global switches, every per-app override and the app-list visibility. */
    fun resetAll(): Boolean = store.resetAll()

    fun resetToDefaults(): Boolean = store.resetToDefaults()

    fun close() {
        if (ownsStore) {
            store.close()
        }
    }

    companion object {
        internal val defaultValues = SettingsStore.defaultValues

        internal fun openPrefs(context: Context): SharedPreferences =
            SettingsStore.openPrefs(context)
    }
}
