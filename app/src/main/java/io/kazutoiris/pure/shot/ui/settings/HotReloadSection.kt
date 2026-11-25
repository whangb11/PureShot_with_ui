package io.kazutoiris.pure.shot.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.kazutoiris.pure.shot.R

/**
 * "Reload system hooks" control for the About card.
 *
 * The button is disabled whenever hot reload cannot run, and the reason is always spelled out in
 * text instead of relying on the disabled state alone.
 */
@Composable
internal fun HotReloadSection(
    state: HotReloadUiState,
    onReload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_hot_reload_title),
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = stringResource(R.string.settings_hot_reload_summary),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = availabilityText(state.availability),
            style = MaterialTheme.typography.bodySmall,
            color = if (state.availability == HotReloadAvailability.Available) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.error
            },
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FilledTonalButton(
                onClick = onReload,
                enabled = state.canReload,
            ) {
                if (state.running) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(R.string.settings_hot_reload_running))
                } else {
                    Text(text = stringResource(R.string.settings_hot_reload_action))
                }
            }
        }
        state.outcome?.let { outcome ->
            Text(
                text = outcomeText(outcome),
                style = MaterialTheme.typography.bodySmall,
                color = when (outcome) {
                    HotReloadOutcome.Succeeded -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.error
                },
            )
        }
    }
}

@Composable
private fun availabilityText(availability: HotReloadAvailability): String =
    when (availability) {
        HotReloadAvailability.Checking -> stringResource(R.string.settings_hot_reload_checking)
        HotReloadAvailability.Available -> stringResource(R.string.settings_hot_reload_available)
        HotReloadAvailability.ServiceUnavailable ->
            stringResource(R.string.settings_hot_reload_service_unavailable)
        HotReloadAvailability.ApiUnsupported ->
            stringResource(R.string.settings_hot_reload_api_unsupported)
        HotReloadAvailability.TargetMissing ->
            stringResource(R.string.settings_hot_reload_target_missing)
        is HotReloadAvailability.Error ->
            availability.message?.let { stringResource(R.string.settings_hot_reload_error_detail, it) }
                ?: stringResource(R.string.settings_hot_reload_error)
    }

@Composable
private fun outcomeText(outcome: HotReloadOutcome): String =
    when (outcome) {
        HotReloadOutcome.Succeeded -> stringResource(R.string.settings_hot_reload_success)
        is HotReloadOutcome.Failed ->
            outcome.message?.let { stringResource(R.string.settings_hot_reload_failed_detail, it) }
                ?: stringResource(R.string.settings_hot_reload_failed)
        HotReloadOutcome.Unsupported -> stringResource(R.string.settings_hot_reload_unsupported)
        HotReloadOutcome.InProgress -> stringResource(R.string.settings_hot_reload_in_progress)
        HotReloadOutcome.ProcessDied -> stringResource(R.string.settings_hot_reload_process_died)
        HotReloadOutcome.TimedOut -> stringResource(R.string.settings_hot_reload_timeout)
        HotReloadOutcome.ServiceUnavailable ->
            stringResource(R.string.settings_hot_reload_service_unavailable)
        HotReloadOutcome.ApiUnsupported ->
            stringResource(R.string.settings_hot_reload_api_unsupported)
        HotReloadOutcome.TargetMissing ->
            stringResource(R.string.settings_hot_reload_target_missing)
    }
