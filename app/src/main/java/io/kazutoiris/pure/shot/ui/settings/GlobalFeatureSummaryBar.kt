package io.kazutoiris.pure.shot.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.kazutoiris.pure.shot.R
import io.kazutoiris.pure.shot.hook.Feature

/**
 * Fixed summary above a per-feature app list. Every app card used to repeat the global value; this
 * bar is the single place that shows it, and it toggles the global switch directly.
 */
@Composable
internal fun GlobalFeatureSummaryBar(
    feature: Feature,
    state: SettingsState,
    actions: SettingsActions,
    modifier: Modifier = Modifier,
) {
    val row = globalFeatureRow(feature) ?: return
    val hidden = state.isFeatureHidden(feature)
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (state.moduleEnabled) 1f else 0.38f)
                .toggleable(
                    value = hidden,
                    enabled = state.moduleEnabled,
                    role = Role.Switch,
                    onValueChange = { actions.toggle(row.key, it) },
                )
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(
                        if (hidden) {
                            R.string.settings_global_value_hidden
                        } else {
                            R.string.settings_global_value_shown
                        },
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = stringResource(R.string.settings_global_feature_summary),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(
                checked = hidden,
                onCheckedChange = null,
                enabled = state.moduleEnabled,
            )
        }
    }
}
