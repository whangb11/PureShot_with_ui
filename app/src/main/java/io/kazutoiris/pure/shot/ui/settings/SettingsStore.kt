package io.kazutoiris.pure.shot.ui.settings

import android.content.Context
import android.content.SharedPreferences
import io.github.libxposed.service.XposedService
import io.kazutoiris.pure.shot.PrefKeys
import io.kazutoiris.pure.shot.hook.AppPolicy
import io.kazutoiris.pure.shot.hook.Feature
import io.kazutoiris.pure.shot.hook.Override
import io.kazutoiris.pure.shot.hook.PerAppConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single source of truth for PureShot settings.
 *
 * The local [SharedPreferences] file is always the editable mirror. When the libxposed service is
 * bound, the same values are mirrored into RemotePreferences, which system_server observes in real
 * time. If the service is unavailable, edits stay local and are marked as pending so the next
 * successful connection pushes the local snapshot instead of overwriting it.
 *
 * Global switches are stored as booleans, `per_app_config` as a versioned JSON string and
 * `app_list_visibility` as an integer. Per-app edits are always expressed through [AppPolicy] /
 * [PerAppConfig] so nothing outside this store has to know the wire format.
 */
class SettingsStore(context: Context) {

    private val appContext = context.applicationContext ?: context
    private val prefs = openPrefs(appContext)
    private val lock = Any()

    private val _state = MutableStateFlow(readState(SyncState.LOCAL_ONLY))
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    /** The currently bound framework service, or null when the framework is unavailable. */
    private val _service = MutableStateFlow<XposedService?>(null)
    val service: StateFlow<XposedService?> = _service.asStateFlow()

    private val localListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        publish()
    }

    private val remoteListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        onRemoteChanged()
    }

    private var boundService: XposedService? = null
    private var remotePrefs: SharedPreferences? = null
    private var remoteWriteInProgress = false
    private var syncing = false
    private var lastSyncFailed = false

    init {
        prefs.registerOnSharedPreferenceChangeListener(localListener)
    }

    /**
     * Writes any missing defaults, including the migration keys introduced for per-app settings.
     * Returns true when the local file was changed.
     */
    fun initialize(): Boolean {
        return synchronized(lock) {
            val ensured = ensureDefaults()
            val repaired = repairBlankPerAppConfigLocked()
            publish()
            ensured || repaired
        }
    }

    fun setBoolean(key: String, value: Boolean): Boolean {
        return synchronized(lock) {
            writeValueLocked(key, value)
        }
    }

    /** Adds or replaces the app-level master override. */
    fun setAppMaster(packageName: String, master: Override): Boolean =
        updateAppPolicy(packageName) { it.withMaster(master) }

    /** Pass `value = null` to clear the override and inherit the global switch again. */
    fun setAppFeature(packageName: String, feature: Feature, value: Boolean?): Boolean {
        if (feature == Feature.NONE) {
            return false
        }
        return updateAppPolicy(packageName) { it.withFeature(feature, value) }
    }

    /** Removes the master switch and every feature override for one package. */
    fun clearApp(packageName: String): Boolean =
        updatePerAppConfig { it.without(packageName) }

    fun setAppListVisibility(visibility: AppListVisibility): Boolean {
        return synchronized(lock) {
            writeValueLocked(PrefKeys.KEY_APP_LIST_VISIBILITY, visibility.level)
        }
    }

    /**
     * Raw escape hatch used by automation/instrumentation seeding. Normal UI code must use the
     * structured per-app methods above.
     */
    fun setPerAppConfigJson(json: String): Boolean {
        return synchronized(lock) {
            writeValueLocked(PrefKeys.KEY_PER_APP_CONFIG, json)
        }
    }

    /** Resets global switches, clears every per-app override and restores visibility level 1. */
    fun resetAll(): Boolean {
        return synchronized(lock) {
            writeValuesLocked(defaultValues.toMap())
        }
    }

    fun resetToDefaults(): Boolean = resetAll()

    fun bindService(service: XposedService) {
        synchronized(lock) {
            detachRemoteLocked()
            this.boundService = service
            _service.value = service
            val remote = runCatching {
                service.getRemotePreferences(PrefKeys.REMOTE_PREFS_GROUP)
            }.getOrNull()
            if (remote == null) {
                lastSyncFailed = true
                publish()
                return
            }
            remotePrefs = remote
            runCatching {
                remote.registerOnSharedPreferenceChangeListener(remoteListener)
            }
            syncOnConnectLocked(remote)
        }
    }

    fun unbindService(service: XposedService) {
        synchronized(lock) {
            if (this.boundService !== service) {
                return
            }
            detachRemoteLocked()
            this.boundService = null
            _service.value = null
            lastSyncFailed = false
            publish()
        }
    }

    /**
     * Retries a pending push when the app returns to the foreground. No-op when there is nothing
     * pending or the framework service is not connected.
     */
    fun retryPendingSync() {
        synchronized(lock) {
            val remote = remotePrefs ?: return
            if (!pendingSync()) {
                return
            }
            val pushed = pushAllLocked(remote)
            writePendingSyncLocked(!pushed)
            lastSyncFailed = !pushed
            publish()
        }
    }

    fun close() {
        synchronized(lock) {
            prefs.unregisterOnSharedPreferenceChangeListener(localListener)
            detachRemoteLocked()
            boundService = null
            _service.value = null
        }
    }

    private fun updateAppPolicy(packageName: String, transform: (AppPolicy) -> AppPolicy): Boolean {
        if (packageName.isBlank()) {
            return false
        }
        return updatePerAppConfig { config ->
            val policy = config.get(packageName) ?: AppPolicy.DEFAULT
            config.withPolicy(packageName, transform(policy))
        }
    }

    private fun updatePerAppConfig(transform: (PerAppConfig) -> PerAppConfig): Boolean {
        return synchronized(lock) {
            val config = PerAppConfig.decode(
                readString(PrefKeys.KEY_PER_APP_CONFIG, PrefKeys.DEFAULT_PER_APP_CONFIG),
            )
            writeValueLocked(PrefKeys.KEY_PER_APP_CONFIG, transform(config).encode())
        }
    }

    private fun syncOnConnectLocked(remote: SharedPreferences) {
        syncing = true
        publish()

        val pending = pendingSync()
        val remoteHasData = defaultValues.keys.any { key ->
            runCatching { remote.contains(key) }.getOrDefault(false)
        }

        val synced = if (pending || !remoteHasData) {
            val pushed = pushAllLocked(remote)
            writePendingSyncLocked(!pushed)
            pushed
        } else {
            val pulled = pullFromRemoteLocked(remote)
            writePendingSyncLocked(!pulled)
            pulled
        }

        lastSyncFailed = !synced
        syncing = false
        publish()
    }

    private fun onRemoteChanged() {
        synchronized(lock) {
            val remote = remotePrefs
            if (remote == null || remoteWriteInProgress) {
                return
            }
            if (pendingSync()) {
                // Local edits win until they have been pushed.
                return
            }
            if (pullFromRemoteLocked(remote)) {
                lastSyncFailed = false
                publish()
            }
        }
    }

    private fun writeValuesLocked(values: Map<String, Any>): Boolean {
        val saved = runCatching { writeLocalLocked(values) }.getOrDefault(false)
        if (!saved) {
            publish(SyncState.ERROR)
            return false
        }
        val synced = pushKeysLocked(values)
        writePendingSyncLocked(!synced)
        lastSyncFailed = !synced
        publish()
        return true
    }

    private fun writeValueLocked(key: String, value: Any): Boolean =
        writeValuesLocked(mapOf(key to value))

    private fun writeLocalLocked(values: Map<String, Any>): Boolean {
        val editor = prefs.edit()
        for ((key, value) in values) {
            if (!editor.putValue(key, value)) {
                return false
            }
        }
        return editor.commit()
    }

    private fun pushAllLocked(remote: SharedPreferences): Boolean {
        val values = defaultValues.mapValues { (key, defaultValue) ->
            readValueLocked(key, defaultValue)
        }
        return writeRemoteLocked(remote, values)
    }

    private fun pushKeysLocked(values: Map<String, Any>): Boolean {
        val remote = remotePrefs ?: return false
        return writeRemoteLocked(remote, values)
    }

    private fun writeRemoteLocked(
        remote: SharedPreferences,
        values: Map<String, Any>,
    ): Boolean {
        remoteWriteInProgress = true
        return try {
            val editor = remote.edit()
            for ((key, value) in values) {
                if (!editor.putValue(key, value)) {
                    return false
                }
            }
            editor.commit()
        } catch (t: Throwable) {
            false
        } finally {
            remoteWriteInProgress = false
        }
    }

    private fun pullFromRemoteLocked(remote: SharedPreferences): Boolean {
        return runCatching {
            val editor = prefs.edit()
            defaultValues.forEach { (key, defaultValue) ->
                editor.putValue(key, readRemoteValue(remote, key, defaultValue))
            }
            editor.commit()
        }.getOrDefault(false)
    }

    private fun writePendingSyncLocked(value: Boolean) {
        if (pendingSync() == value) {
            return
        }
        runCatching {
            prefs.edit().putBoolean(PrefKeys.KEY_PENDING_SYNC, value).commit()
        }
    }

    private fun detachRemoteLocked() {
        val remote = remotePrefs ?: return
        runCatching {
            remote.unregisterOnSharedPreferenceChangeListener(remoteListener)
        }
        remotePrefs = null
    }

    private fun publish(forced: SyncState? = null) {
        _state.value = readState(forced ?: currentSyncState())
    }

    private fun currentSyncState(): SyncState = when {
        boundService == null -> SyncState.LOCAL_ONLY
        syncing -> SyncState.SYNCING
        pendingSync() -> SyncState.PENDING
        lastSyncFailed -> SyncState.ERROR
        else -> SyncState.SYNCED
    }

    private fun pendingSync(): Boolean =
        runCatching { prefs.getBoolean(PrefKeys.KEY_PENDING_SYNC, false) }.getOrDefault(false)

    private fun ensureDefaults(): Boolean = runCatching {
        val editor = prefs.edit()
        var changed = false
        defaultValues.forEach { (key, value) ->
            if (!prefs.contains(key)) {
                editor.putValue(key, value)
                changed = true
            }
        }
        if (changed) {
            editor.commit()
        } else {
            true
        }
    }.getOrDefault(false)

    /** Older builds wrote only the seven global switches; a blank JSON value is unusable. */
    private fun repairBlankPerAppConfigLocked(): Boolean {
        val current = runCatching {
            prefs.getString(PrefKeys.KEY_PER_APP_CONFIG, null)
        }.getOrNull()
        if (current == null || current.isNotBlank()) {
            return false
        }
        return runCatching {
            prefs.edit().putString(
                PrefKeys.KEY_PER_APP_CONFIG,
                PrefKeys.DEFAULT_PER_APP_CONFIG,
            ).commit()
        }.getOrDefault(false)
    }

    private fun readState(syncState: SyncState): SettingsState {
        val perApp = PerAppConfig.decode(
            readString(PrefKeys.KEY_PER_APP_CONFIG, PrefKeys.DEFAULT_PER_APP_CONFIG),
        ).apps
        return SettingsState(
            moduleEnabled = readBoolean(PrefKeys.KEY_MODULE_ENABLED, PrefKeys.DEFAULT_MODULE_ENABLED),
            hideStatusBar = readBoolean(PrefKeys.KEY_HIDE_STATUS_BAR, PrefKeys.DEFAULT_HIDE_STATUS_BAR),
            hideNavigationBar = readBoolean(
                PrefKeys.KEY_HIDE_NAVIGATION_BAR,
                PrefKeys.DEFAULT_HIDE_NAVIGATION_BAR,
            ),
            hideInputMethod = readBoolean(
                PrefKeys.KEY_HIDE_INPUT_METHOD,
                PrefKeys.DEFAULT_HIDE_INPUT_METHOD,
            ),
            hideToast = readBoolean(PrefKeys.KEY_HIDE_TOAST, PrefKeys.DEFAULT_HIDE_TOAST),
            hideOverlays = readBoolean(PrefKeys.KEY_HIDE_OVERLAYS, PrefKeys.DEFAULT_HIDE_OVERLAYS),
            hideOtherSystemUi = readBoolean(
                PrefKeys.KEY_HIDE_OTHER_SYSTEM_UI,
                PrefKeys.DEFAULT_HIDE_OTHER_SYSTEM_UI,
            ),
            perApp = perApp,
            appListVisibility = AppListVisibility.fromLevel(
                readInt(
                    PrefKeys.KEY_APP_LIST_VISIBILITY,
                    PrefKeys.DEFAULT_APP_LIST_VISIBILITY,
                ),
            ),
            syncState = syncState,
        )
    }

    private fun readBoolean(key: String, default: Boolean): Boolean =
        runCatching { prefs.getBoolean(key, default) }.getOrDefault(default)

    private fun readInt(key: String, default: Int): Int =
        runCatching { prefs.getInt(key, default) }.getOrDefault(default)

    private fun readString(key: String, default: String): String =
        runCatching { prefs.getString(key, default) }.getOrNull() ?: default

    private fun readValueLocked(key: String, defaultValue: Any): Any = runCatching {
        when (defaultValue) {
            is Boolean -> prefs.getBoolean(key, defaultValue)
            is Int -> prefs.getInt(key, defaultValue)
            is String -> prefs.getString(key, defaultValue) ?: defaultValue
            else -> defaultValue
        }
    }.getOrDefault(defaultValue)

    private fun readRemoteValue(
        remote: SharedPreferences,
        key: String,
        defaultValue: Any,
    ): Any = runCatching {
        when (defaultValue) {
            is Boolean -> remote.getBoolean(key, defaultValue)
            is Int -> remote.getInt(key, defaultValue)
            is String -> remote.getString(key, defaultValue) ?: defaultValue
            else -> defaultValue
        }
    }.getOrDefault(defaultValue)

    private fun SharedPreferences.Editor.putValue(key: String, value: Any): Boolean {
        when (value) {
            is Boolean -> putBoolean(key, value)
            is Int -> putInt(key, value)
            is String -> putString(key, value)
            else -> return false
        }
        return true
    }

    companion object {
        internal val defaultValues: Map<String, Any> = linkedMapOf(
            PrefKeys.KEY_MODULE_ENABLED to PrefKeys.DEFAULT_MODULE_ENABLED,
            PrefKeys.KEY_HIDE_STATUS_BAR to PrefKeys.DEFAULT_HIDE_STATUS_BAR,
            PrefKeys.KEY_HIDE_NAVIGATION_BAR to PrefKeys.DEFAULT_HIDE_NAVIGATION_BAR,
            PrefKeys.KEY_HIDE_INPUT_METHOD to PrefKeys.DEFAULT_HIDE_INPUT_METHOD,
            PrefKeys.KEY_HIDE_TOAST to PrefKeys.DEFAULT_HIDE_TOAST,
            PrefKeys.KEY_HIDE_OVERLAYS to PrefKeys.DEFAULT_HIDE_OVERLAYS,
            PrefKeys.KEY_HIDE_OTHER_SYSTEM_UI to PrefKeys.DEFAULT_HIDE_OTHER_SYSTEM_UI,
            PrefKeys.KEY_PER_APP_CONFIG to PrefKeys.DEFAULT_PER_APP_CONFIG,
            PrefKeys.KEY_APP_LIST_VISIBILITY to PrefKeys.DEFAULT_APP_LIST_VISIBILITY,
        )

        internal fun openPrefs(context: Context): SharedPreferences =
            context.getSharedPreferences(PrefKeys.PREFS_FILE, Context.MODE_PRIVATE)
    }
}
