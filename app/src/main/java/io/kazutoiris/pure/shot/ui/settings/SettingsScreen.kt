package io.kazutoiris.pure.shot.ui.settings

import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.kazutoiris.pure.shot.BuildConfig
import io.kazutoiris.pure.shot.PrefKeys
import io.kazutoiris.pure.shot.R
import io.kazutoiris.pure.shot.ui.theme.PureShotTheme

@Composable
fun SettingsRoute(
    viewModel: SettingsViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val saveFailedMessage = stringResource(R.string.settings_save_failed)
    val resetDoneMessage = stringResource(R.string.settings_reset_done)

    LaunchedEffect(viewModel, saveFailedMessage, resetDoneMessage) {
        viewModel.events.collect { event ->
            val message = when (event) {
                UiEvent.SaveFailed -> saveFailedMessage
                UiEvent.ResetDone -> resetDoneMessage
            }
            snackbarHostState.showSnackbar(message)
        }
    }

    SettingsScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onToggle = viewModel::set,
        onReset = viewModel::reset,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsState,
    snackbarHostState: SnackbarHostState,
    onToggle: (String, Boolean) -> Unit,
    onReset: () -> Unit,
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
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
                        onCheckedChange = { onToggle(PrefKeys.KEY_MODULE_ENABLED, it) },
                    )
                }
            }

            item { SectionHeader(text = stringResource(R.string.settings_section_hide)) }
            item {
                SettingsCard {
                    hideToggles.forEachIndexed { index, toggle ->
                        if (index > 0) {
                            HorizontalDivider()
                        }
                        ToggleRow(
                            title = stringResource(toggle.titleRes),
                            summary = toggle.summaryRes?.let { stringResource(it) },
                            checked = toggle.value(state),
                            enabled = state.moduleEnabled,
                            onCheckedChange = { onToggle(toggle.key, it) },
                        )
                    }
                }
            }

            item { SectionHeader(text = stringResource(R.string.settings_section_about)) }
            item { AboutCard() }
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
                        onReset()
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
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 24.dp, bottom = 8.dp),
    )
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(content = content)
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
private fun AboutCard() {
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
                text = stringResource(R.string.settings_local_only),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private class HideToggle(
    val key: String,
    @param:StringRes val titleRes: Int,
    @param:StringRes val summaryRes: Int? = null,
    val value: (SettingsState) -> Boolean,
)

private val hideToggles = listOf(
    HideToggle(
        key = PrefKeys.KEY_HIDE_STATUS_BAR,
        titleRes = R.string.settings_status_bar_title,
        summaryRes = R.string.settings_status_bar_summary,
        value = { it.hideStatusBar },
    ),
    HideToggle(
        key = PrefKeys.KEY_HIDE_NAVIGATION_BAR,
        titleRes = R.string.settings_nav_bar_title,
        summaryRes = R.string.settings_nav_bar_summary,
        value = { it.hideNavigationBar },
    ),
    HideToggle(
        key = PrefKeys.KEY_HIDE_INPUT_METHOD,
        titleRes = R.string.settings_ime_title,
        value = { it.hideInputMethod },
    ),
    HideToggle(
        key = PrefKeys.KEY_HIDE_TOAST,
        titleRes = R.string.settings_toast_title,
        value = { it.hideToast },
    ),
    HideToggle(
        key = PrefKeys.KEY_HIDE_OVERLAYS,
        titleRes = R.string.settings_overlays_title,
        value = { it.hideOverlays },
    ),
    HideToggle(
        key = PrefKeys.KEY_HIDE_OTHER_SYSTEM_UI,
        titleRes = R.string.settings_other_title,
        summaryRes = R.string.settings_other_summary,
        value = { it.hideOtherSystemUi },
    ),
)

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SettingsScreenPreview() {
    PureShotTheme {
        SettingsScreen(
            state = SettingsState.Default,
            snackbarHostState = remember { SnackbarHostState() },
            onToggle = { _, _ -> },
            onReset = {},
        )
    }
}

@Preview(name = "Master off", showBackground = true)
@Composable
private fun SettingsScreenMasterOffPreview() {
    PureShotTheme {
        SettingsScreen(
            state = SettingsState.Default.copy(moduleEnabled = false),
            snackbarHostState = remember { SnackbarHostState() },
            onToggle = { _, _ -> },
            onReset = {},
        )
    }
}
