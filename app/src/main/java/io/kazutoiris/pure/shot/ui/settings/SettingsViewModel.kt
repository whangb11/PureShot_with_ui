package io.kazutoiris.pure.shot.ui.settings

import android.app.Application
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.kazutoiris.pure.shot.hook.Feature
import io.kazutoiris.pure.shot.hook.Override
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SettingsTab {
    FEATURES,
    APPS,
}

sealed interface SettingsDestination {
    data object Tabs : SettingsDestination
    data class FeatureApps(val feature: Feature) : SettingsDestination
    data class AppDetail(val packageName: String) : SettingsDestination
}

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SettingsRepository(application)
    private val catalog = AppCatalogRepository(application)
    private val iconLoader = AppIconLoader(application)
    private val hotReloadManager = HotReloadManager { repository.service.value }

    val state: StateFlow<SettingsState> = repository.state

    private val _hotReloadState = MutableStateFlow(HotReloadUiState())
    val hotReloadState: StateFlow<HotReloadUiState> = _hotReloadState.asStateFlow()

    private val _tab = MutableStateFlow(SettingsTab.FEATURES)
    val tab: StateFlow<SettingsTab> = _tab.asStateFlow()

    private val _destination = MutableStateFlow<SettingsDestination>(SettingsDestination.Tabs)
    val destination: StateFlow<SettingsDestination> = _destination.asStateFlow()

    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())
    private val _query = MutableStateFlow("")
    private val _appsLoading = MutableStateFlow(false)
    private val _appsFailed = MutableStateFlow(false)

    val appListState: StateFlow<AppListUiState> = combine(
        _apps,
        _query,
        _appsLoading,
        _appsFailed,
        repository.state.map { it.appListVisibility }.distinctUntilChanged(),
    ) { apps, query, loading, failed, visibility ->
        AppListUiState(
            apps = apps,
            filtered = AppListFilter.apply(apps, visibility, query),
            query = query,
            loading = loading,
            failed = failed,
            visibility = visibility,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppListUiState(),
    )

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    private var loadJob: Job? = null
    private var lastLoadedAt = 0L

    init {
        viewModelScope.launch(Dispatchers.IO) {
            repository.initialize()
        }
        viewModelScope.launch {
            repository.service.collect { service ->
                if (service == null) {
                    _hotReloadState.update {
                        it.copy(
                            availability = HotReloadAvailability.ServiceUnavailable,
                            running = false,
                        )
                    }
                } else {
                    refreshHotReloadAvailability()
                }
            }
        }
    }

    fun set(key: String, value: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            if (!repository.setBoolean(key, value)) {
                _events.emit(UiEvent.SaveFailed)
            }
        }
    }

    fun reset() {
        viewModelScope.launch(Dispatchers.IO) {
            if (repository.resetAll()) {
                _events.emit(UiEvent.ResetDone)
            } else {
                _events.emit(UiEvent.SaveFailed)
            }
        }
    }

    fun selectTab(tab: SettingsTab) {
        if (_tab.value == tab) {
            return
        }
        _tab.value = tab
        _query.value = ""
        _destination.value = SettingsDestination.Tabs
        if (tab == SettingsTab.APPS) {
            refreshApps()
        }
    }

    fun openFeatureApps(feature: Feature) {
        _destination.value = SettingsDestination.FeatureApps(feature)
        refreshApps()
    }

    fun openApp(packageName: String) {
        _destination.value = SettingsDestination.AppDetail(packageName)
    }

    fun back() {
        if (_destination.value != SettingsDestination.Tabs) {
            _destination.value = SettingsDestination.Tabs
        }
    }

    fun setQuery(query: String) {
        _query.value = query
    }

    fun clearQuery() {
        _query.value = ""
    }

    fun setVisibility(visibility: AppListVisibility) {
        viewModelScope.launch(Dispatchers.IO) {
            if (!repository.setAppListVisibility(visibility)) {
                _events.emit(UiEvent.SaveFailed)
            }
        }
    }

    fun setAppMaster(packageName: String, master: Override) {
        viewModelScope.launch(Dispatchers.IO) {
            if (!repository.setAppMaster(packageName, master)) {
                _events.emit(UiEvent.SaveFailed)
            }
        }
    }

    fun setAppFeature(packageName: String, feature: Feature, value: Boolean?) {
        viewModelScope.launch(Dispatchers.IO) {
            if (!repository.setAppFeature(packageName, feature, value)) {
                _events.emit(UiEvent.SaveFailed)
            }
        }
    }

    fun clearApp(packageName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (repository.clearApp(packageName)) {
                _events.emit(UiEvent.AppOverridesCleared)
            } else {
                _events.emit(UiEvent.SaveFailed)
            }
        }
    }

    suspend fun loadIcon(packageName: String): ImageBitmap? = iconLoader.load(packageName)

    /** Re-queries the framework for a `system` target; used on resume and after reconnect. */
    fun refreshHotReloadAvailability() {
        viewModelScope.launch {
            val availability = hotReloadManager.checkAvailability()
            _hotReloadState.update { it.copy(availability = availability) }
        }
    }

    /** Manual "reload system hooks": swaps the module generation inside system_server. */
    fun reloadSystemHooks() {
        if (_hotReloadState.value.running) {
            return
        }
        _hotReloadState.update { it.copy(running = true, outcome = null) }
        viewModelScope.launch {
            val outcome = hotReloadManager.reload()
            _hotReloadState.update { it.copy(running = false, outcome = outcome) }
            val availability = hotReloadManager.checkAvailability()
            _hotReloadState.update { it.copy(availability = availability) }
        }
    }

    /**
     * Loads the catalog on first entry and refreshes it when a page is revisited after
     * [REFRESH_INTERVAL_MS]; [force] is used by the retry action.
     */
    fun refreshApps(force: Boolean = false) {
        if (loadJob?.isActive == true) {
            return
        }
        val now = System.currentTimeMillis()
        if (!force && _apps.value.isNotEmpty() && now - lastLoadedAt < REFRESH_INTERVAL_MS) {
            return
        }
        _appsLoading.value = true
        _appsFailed.value = false
        loadJob = viewModelScope.launch {
            try {
                val apps = runCatching { catalog.loadApps() }
                apps.onSuccess {
                    _apps.value = it
                    lastLoadedAt = System.currentTimeMillis()
                }.onFailure {
                    _appsFailed.value = true
                }
            } finally {
                _appsLoading.value = false
            }
        }
    }

    /** Called when the host Activity resumes; keeps the app catalog fresh after installs. */
    fun onHostResumed() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.retryPendingSync()
        }
        refreshHotReloadAvailability()
        if (_tab.value == SettingsTab.APPS || _destination.value != SettingsDestination.Tabs) {
            refreshApps()
        }
    }

    override fun onCleared() {
        repository.close()
        iconLoader.clear()
        super.onCleared()
    }

    private companion object {
        const val REFRESH_INTERVAL_MS = 15_000L
    }
}

sealed interface UiEvent {
    data object SaveFailed : UiEvent
    data object ResetDone : UiEvent
    data object AppOverridesCleared : UiEvent
}
