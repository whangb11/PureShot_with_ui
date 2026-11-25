package io.kazutoiris.pure.shot.ui.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import io.kazutoiris.pure.shot.R
import io.kazutoiris.pure.shot.hook.Override

private data class TriStateOption<T>(val value: T, val label: String)

/**
 * Three equal-width segmented buttons used for every override control.
 *
 * Material3's segmented button row is the primary implementation; the plan's FilterChip fallback is
 * not needed with the Compose BOM in this project.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> TriStateSelector(
    options: List<TriStateOption<T>>,
    selected: T,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option.value == selected,
                onClick = {
                    if (option.value != selected) {
                        onSelected(option.value)
                    }
                },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                modifier = Modifier.weight(1f),
                enabled = enabled,
                icon = {},
                label = {
                    Text(
                        text = option.label,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        }
    }
}

/**
 * Per-feature override: follow the global switch, force hiding, or force showing.
 * `null` means inherit.
 */
@Composable
internal fun FeatureOverrideSelector(
    value: Boolean?,
    onValueChange: (Boolean?) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    TriStateSelector(
        options = listOf(
            TriStateOption(null, stringResource(R.string.settings_tristate_inherit)),
            TriStateOption(true, stringResource(R.string.settings_tristate_hide)),
            TriStateOption(false, stringResource(R.string.settings_tristate_show)),
        ),
        selected = value,
        onSelected = onValueChange,
        modifier = modifier,
        enabled = enabled,
    )
}

/** App-level override: follow the global switch, force this app on, or force it off. */
@Composable
internal fun MasterOverrideSelector(
    value: Override,
    onValueChange: (Override) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    TriStateSelector(
        options = listOf(
            TriStateOption(Override.INHERIT, stringResource(R.string.settings_tristate_inherit)),
            TriStateOption(Override.ON, stringResource(R.string.settings_tristate_master_on)),
            TriStateOption(Override.OFF, stringResource(R.string.settings_tristate_master_off)),
        ),
        selected = value,
        onSelected = onValueChange,
        modifier = modifier,
        enabled = enabled,
    )
}
