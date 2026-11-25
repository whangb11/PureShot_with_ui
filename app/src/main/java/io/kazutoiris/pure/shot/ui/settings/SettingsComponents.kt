package io.kazutoiris.pure.shot.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.kazutoiris.pure.shot.R

@Composable
internal fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 24.dp, bottom = 8.dp),
    )
}

@Composable
internal fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(content = content)
    }
}

/** Shown while the global master switch is off; per-app values are still editable. */
@Composable
internal fun GlobalDisabledBanner(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Text(
            text = stringResource(R.string.settings_global_disabled_banner),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}

/** Compact sync reminder under the tabs; the About card keeps the detailed message. */
@Composable
internal fun SyncStatusBanner(
    syncState: SyncState,
    modifier: Modifier = Modifier,
) {
    if (syncState == SyncState.SYNCED) {
        return
    }
    val messageRes = when (syncState) {
        SyncState.LOCAL_ONLY -> R.string.settings_sync_local_only
        SyncState.SYNCING -> R.string.settings_sync_syncing
        SyncState.PENDING -> R.string.settings_sync_pending
        SyncState.ERROR -> R.string.settings_sync_error
        SyncState.SYNCED -> return
    }
    val containerColor = when (syncState) {
        SyncState.ERROR -> MaterialTheme.colorScheme.errorContainer
        SyncState.PENDING -> MaterialTheme.colorScheme.tertiaryContainer
        SyncState.LOCAL_ONLY -> MaterialTheme.colorScheme.secondaryContainer
        SyncState.SYNCING -> MaterialTheme.colorScheme.surfaceVariant
        SyncState.SYNCED -> return
    }
    val contentColor = when (syncState) {
        SyncState.ERROR -> MaterialTheme.colorScheme.onErrorContainer
        SyncState.PENDING -> MaterialTheme.colorScheme.onTertiaryContainer
        SyncState.LOCAL_ONLY -> MaterialTheme.colorScheme.onSecondaryContainer
        SyncState.SYNCING -> MaterialTheme.colorScheme.onSurfaceVariant
        SyncState.SYNCED -> return
    }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(8.dp),
        color = containerColor,
        contentColor = contentColor,
    ) {
        Text(
            text = stringResource(messageRes),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}
