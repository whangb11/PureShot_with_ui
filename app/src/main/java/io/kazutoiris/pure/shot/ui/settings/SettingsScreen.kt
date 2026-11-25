package io.kazutoiris.pure.shot.ui.settings

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.kazutoiris.pure.shot.BuildConfig
import io.kazutoiris.pure.shot.PrefKeys
import io.kazutoiris.pure.shot.R
import io.kazutoiris.pure.shot.hook.AppPolicy
import io.kazutoiris.pure.shot.hook.Feature
import io.kazutoiris.pure.shot.hook.Override
import io.kazutoiris.pure.shot.ui.theme.PureShotTheme

@Composable
fun SettingsRoute(
    viewModel: SettingsViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val tab by viewModel.tab.collectAsStateWithLifecycle()
    val destination by viewModel.destination.collectAsStateWithLifecycle()
    val appList by viewModel.appListState.collectAsStateWithLifecycle()
    val hotReload by viewModel.hotReloadState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val saveFailedMessage = stringResource(R.string.settings_save_failed)
    val resetDoneMessage = stringResource(R.string.settings_reset_done)
    val clearedMessage = stringResource(R.string.settings_app_overrides_cleared)

    val actions = remember(viewModel) {
        SettingsActions(
            toggle = viewModel::set,
            reset = viewModel::reset,
            selectTab = viewModel::selectTab,
            openFeatureApps = viewModel::openFeatureApps,
            openApp = viewModel::openApp,
            back = viewModel::back,
            setQuery = viewModel::setQuery,
            clearQuery = viewModel::clearQuery,
            setVisibility = viewModel::setVisibility,
            setAppMaster = viewModel::setAppMaster,
            setAppFeature = viewModel::setAppFeature,
            clearApp = viewModel::clearApp,
            refreshApps = { viewModel.refreshApps(force = true) },
            reloadSystemHooks = viewModel::reloadSystemHooks,
        )
    }
    val loadIcon: suspend (String) -> ImageBitmap? = remember(viewModel) {
        { packageName -> viewModel.loadIcon(packageName) }
    }

    LaunchedEffect(viewModel, saveFailedMessage, resetDoneMessage, clearedMessage) {
        viewModel.events.collect { event ->
            val message = when (event) {
                UiEvent.SaveFailed -> saveFailedMessage
                UiEvent.ResetDone -> resetDoneMessage
                UiEvent.AppOverridesCleared -> clearedMessage
            }
            snackbarHostState.showSnackbar(message)
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.onHostResumed()
    }

    BackHandler(enabled = destination != SettingsDestination.Tabs) {
        viewModel.back()
    }

    when (val screen = destination) {
        SettingsDestination.Tabs -> SettingsScreen(
            state = state,
            tab = tab,
            appList = appList,
            hotReload = hotReload,
            snackbarHostState = snackbarHostState,
            actions = actions,
            loadIcon = loadIcon,
        )

        is SettingsDestination.FeatureApps -> FeatureAppsScreen(
            feature = screen.feature,
            state = state,
            appList = appList,
            snackbarHostState = snackbarHostState,
            actions = actions,
            loadIcon = loadIcon,
        )

        is SettingsDestination.AppDetail -> AppDetailScreen(
            packageName = screen.packageName,
            state = state,
            appList = appList,
            snackbarHostState = snackbarHostState,
            actions = actions,
            loadIcon = loadIcon,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsState,
    tab: SettingsTab,
    appList: AppListUiState,
    snackbarHostState: SnackbarHostState,
    actions: SettingsActions,
    loadIcon: suspend (String) -> ImageBitmap?,
    hotReload: HotReloadUiState = HotReloadUiState(),
    modifier: Modifier = Modifier,
) {
    var showResetDialog by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.app_name)) },
                actions = {
                    TextButton(onClick = { showResetDialog = true }) {
                        Text(text = stringResource(R.string.settings_reset))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            PrimaryTabRow(
                selectedTabIndex = if (tab == SettingsTab.FEATURES) 0 else 1,
            ) {
                Tab(
                    selected = tab == SettingsTab.FEATURES,
                    onClick = { actions.selectTab(SettingsTab.FEATURES) },
                    text = { Text(text = stringResource(R.string.settings_tab_features)) },
                )
                Tab(
                    selected = tab == SettingsTab.APPS,
                    onClick = { actions.selectTab(SettingsTab.APPS) },
                    text = { Text(text = stringResource(R.string.settings_tab_apps)) },
                )
            }
            SyncStatusBanner(syncState = state.syncState)
            when (tab) {
                SettingsTab.FEATURES -> FeaturesTab(
                    state = state,
                    hotReload = hotReload,
                    actions = actions,
                    modifier = Modifier.weight(1f),
                )

                SettingsTab.APPS -> AppsTab(
                    state = state,
                    appList = appList,
                    actions = actions,
                    loadIcon = loadIcon,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(text = stringResource(R.string.settings_reset)) },
            text = { Text(text = stringResource(R.string.settings_reset_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetDialog = false
                        actions.reset()
                    },
                ) {
                    Text(text = stringResource(R.string.settings_reset))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text(text = stringResource(R.string.settings_cancel))
                }
            },
        )
    }
}

@Composable
private fun FeaturesTab(
    state: SettingsState,
    hotReload: HotReloadUiState,
    actions: SettingsActions,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 8.dp,
            bottom = 24.dp,
        ),
    ) {
        item { IntroCard() }

        item { SectionHeader(text = stringResource(R.string.settings_section_general)) }
        item {
            SettingsCard {
                ToggleRow(
                    title = stringResource(R.string.settings_master_title),
                    summary = stringResource(R.string.settings_master_summary),
                    checked = state.moduleEnabled,
                    onCheckedChange = { actions.toggle(PrefKeys.KEY_MODULE_ENABLED, it) },
                )
            }
        }

        item { SectionHeader(text = stringResource(R.string.settings_section_hide)) }
        item {
            SettingsCard {
                globalFeatureRows.forEachIndexed { index, row ->
                    if (index > 0) {
                        HorizontalDivider()
                    }
                    GlobalFeatureItem(
                        row = row,
                        state = state,
                        actions = actions,
                    )
                }
            }
        }

        item { SectionHeader(text = stringResource(R.string.settings_section_about)) }
        item {
            AboutCard(
                state = state,
                hotReload = hotReload,
                onReload = actions.reloadSystemHooks,
            )
        }
    }
}

@Composable
private fun GlobalFeatureItem(
    row: GlobalFeatureRow,
    state: SettingsState,
    actions: SettingsActions,
) {
    Column {
        ToggleRow(
            title = stringResource(row.titleRes),
            summary = row.summaryRes?.let { stringResource(it) },
            checked = row.isHidden(state),
            enabled = state.moduleEnabled,
            onCheckedChange = { actions.toggle(row.key, it) },
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val overrideCount = state.perApp.values.count {
                it.getFeatureOverride(row.feature) != null
            }
            if (overrideCount > 0) {
                Text(
                    text = stringResource(R.string.settings_feature_override_count, overrideCount),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
            TextButton(onClick = { actions.openFeatureApps(row.feature) }) {
                Text(text = stringResource(R.string.settings_per_app_entry))
            }
        }
    }
}

@Composable
private fun AppsTab(
    state: SettingsState,
    appList: AppListUiState,
    actions: SettingsActions,
    loadIcon: suspend (String) -> ImageBitmap?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        if (!state.moduleEnabled) {
            GlobalDisabledBanner(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        AppListPane(
            appList = appList,
            actions = actions,
            modifier = Modifier.weight(1f),
        ) { app ->
            AppMasterCard(
                app = app,
                policy = state.perApp[app.packageName] ?: AppPolicy.DEFAULT,
                actions = actions,
                loadIcon = loadIcon,
            )
        }
    }
}

@Composable
private fun IntroCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.settings_subtitle),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.settings_effect_note),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .alpha(if (enabled) 1f else 0.38f),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
            )
            if (summary != null) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
        )
    }
}

@Composable
private fun AboutCard(
    state: SettingsState,
    hotReload: HotReloadUiState,
    onReload: () -> Unit,
) {
    SettingsCard {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(R.string.settings_scope),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(
                    when (state.syncState) {
                        SyncState.LOCAL_ONLY -> R.string.settings_sync_local_only
                        SyncState.SYNCING -> R.string.settings_sync_syncing
                        SyncState.SYNCED -> R.string.settings_sync_synced
                        SyncState.PENDING -> R.string.settings_sync_pending
                        SyncState.ERROR -> R.string.settings_sync_error
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            HotReloadSection(
                state = hotReload,
                onReload = onReload,
            )
        }
    }
}

private val previewApps = listOf(
    AppEntry(
        packageName = "com.example.music",
        label = "Music",
        isSystem = false,
        isUpdatedSystem = false,
        hasLauncher = true,
        enabled = true,
    ),
    AppEntry(
        packageName = "com.example.notes",
        label = "Notes",
        isSystem = false,
        isUpdatedSystem = false,
        hasLauncher = true,
        enabled = true,
    ),
    AppEntry(
        packageName = "com.android.settings",
        label = "Settings",
        isSystem = true,
        isUpdatedSystem = false,
        hasLauncher = true,
        enabled = true,
    ),
)

private fun previewAppList() = AppListUiState(
    apps = previewApps,
    filtered = previewApps,
    visibility = AppListVisibility.LAUNCHER,
)

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SettingsScreenPreview() {
    PureShotTheme {
        SettingsScreen(
            state = SettingsState.Default.copy(
                perApp = mapOf(
                    "com.example.music" to AppPolicy(
                        Override.OFF,
                        mapOf(Feature.STATUS_BAR to false),
                    ),
                ),
            ),
            tab = SettingsTab.FEATURES,
            appList = previewAppList(),
            snackbarHostState = remember { SnackbarHostState() },
            actions = SettingsActions(),
            loadIcon = { null },
        )
    }
}

@Preview(name = "Apps tab", showBackground = true)
@Composable
private fun SettingsScreenAppsPreview() {
    PureShotTheme {
        SettingsScreen(
            state = SettingsState.Default,
            tab = SettingsTab.APPS,
            appList = previewAppList(),
            snackbarHostState = remember { SnackbarHostState() },
            actions = SettingsActions(),
            loadIcon = { null },
        )
    }
}

@Preview(name = "Master off", showBackground = true)
@Composable
private fun SettingsScreenMasterOffPreview() {
    PureShotTheme {
        SettingsScreen(
            state = SettingsState.Default.copy(moduleEnabled = false),
            tab = SettingsTab.FEATURES,
            appList = previewAppList(),
            snackbarHostState = remember { SnackbarHostState() },
            actions = SettingsActions(),
            loadIcon = { null },
        )
    }
}
