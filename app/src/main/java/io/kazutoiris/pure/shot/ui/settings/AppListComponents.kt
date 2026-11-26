package io.kazutoiris.pure.shot.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kazutoiris.pure.shot.R
import io.kazutoiris.pure.shot.hook.AppPolicy
import io.kazutoiris.pure.shot.hook.Feature
import io.kazutoiris.pure.shot.hook.Override
import java.util.Locale

/**
 * Shared picker used by the Apps tab and by [FeatureAppsScreen]. The search/filter bar stays outside
 * the list so search and visibility remain reachable while scrolling.
 *
 * [listState] is owned by the route so entering and leaving detail screens keeps the scroll
 * position; the route resets it when the query or visibility actually changes.
 */
@Composable
internal fun AppListPane(
    appList: AppListUiState,
    actions: SettingsActions,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    itemContent: @Composable (AppEntry) -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .imePadding(),
    ) {
        Spacer(modifier = Modifier.height(12.dp))
        AppSearchFilterBar(
            query = appList.query,
            onQueryChange = actions.setQuery,
            visibility = appList.visibility,
            onVisibilityChange = actions.setVisibility,
        )
        if (appList.filtered.isNotEmpty()) {
            Text(
                text = pluralStringResource(
                    R.plurals.settings_app_count,
                    appList.filtered.size,
                    appList.filtered.size,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, end = 4.dp),
            )
        }
        when {
            appList.loading && appList.apps.isEmpty() -> LoadingState(Modifier.weight(1f))
            appList.failed && appList.apps.isEmpty() -> LoadFailedState(
                onRetry = actions.refreshApps,
                modifier = Modifier.weight(1f),
            )

            appList.filtered.isEmpty() -> EmptyState(
                hasQuery = appList.query.isNotEmpty(),
                onClearQuery = actions.clearQuery,
                modifier = Modifier.weight(1f),
            )

            else -> LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items = appList.filtered, key = { it.packageName }) { app ->
                    itemContent(app)
                }
            }
        }
    }
}

/** Single-line search field with the visibility filter tucked into the trailing slot. */
@Composable
private fun AppSearchFilterBar(
    query: String,
    onQueryChange: (String) -> Unit,
    visibility: AppListVisibility,
    onVisibilityChange: (AppListVisibility) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val clearLabel = stringResource(R.string.settings_clear_search)
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        placeholder = { Text(text = stringResource(R.string.settings_app_search_placeholder)) },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(
            onSearch = { focusManager.clearFocus() },
        ),
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (query.isNotEmpty()) {
                    TextButton(
                        onClick = { onQueryChange("") },
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier.semantics { contentDescription = clearLabel },
                    ) {
                        Text(
                            text = "✕",
                            modifier = Modifier.clearAndSetSemantics { },
                        )
                    }
                }
                VerticalDivider(modifier = Modifier.height(24.dp))
                FilterMenuButton(
                    visibility = visibility,
                    onVisibilityChange = onVisibilityChange,
                )
            }
        },
    )
}

@Composable
private fun FilterMenuButton(
    visibility: AppListVisibility,
    onVisibilityChange: (AppListVisibility) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val filterDescription = stringResource(
        R.string.settings_filter_content_description,
        stringResource(visibility.labelRes()),
    )
    Box {
        TextButton(
            onClick = { expanded = true },
            contentPadding = PaddingValues(horizontal = 8.dp),
            modifier = Modifier.semantics { contentDescription = filterDescription },
        ) {
            Text(
                text = stringResource(visibility.labelRes()),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = " ▾",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.clearAndSetSemantics { },
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.widthIn(min = 180.dp),
        ) {
            AppListVisibility.entries.forEach { level ->
                val selected = level == visibility
                DropdownMenuItem(
                    text = {
                        Text(
                            text = if (selected) {
                                "✓ " + stringResource(level.labelRes())
                            } else {
                                stringResource(level.labelRes())
                            },
                        )
                    },
                    onClick = {
                        expanded = false
                        if (!selected) {
                            onVisibilityChange(level)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun LoadingState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.settings_apps_loading),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LoadFailedState(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.settings_apps_load_failed),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        TextButton(onClick = onRetry) {
            Text(text = stringResource(R.string.settings_retry))
        }
    }
}

@Composable
private fun EmptyState(
    hasQuery: Boolean,
    onClearQuery: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.settings_apps_empty),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        if (hasQuery) {
            TextButton(onClick = onClearQuery) {
                Text(text = stringResource(R.string.settings_clear_search))
            }
        }
    }
}

@Composable
private fun AppIcon(
    app: AppEntry,
    loadIcon: suspend (String) -> ImageBitmap?,
    size: Dp,
) {
    val icon by produceState<ImageBitmap?>(initialValue = null, app.packageName) {
        value = loadIcon(app.packageName)
    }
    val shape = RoundedCornerShape(10.dp)
    val bitmap = icon
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = Modifier
                .size(size)
                .clip(shape),
        )
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = app.label.trim().take(1)
                    .uppercase(Locale.getDefault())
                    .ifBlank { "?" },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AppBadges(app: AppEntry, modifier: Modifier = Modifier) {
    val disabled = stringResource(R.string.settings_app_disabled)
    val system = stringResource(R.string.settings_app_system)
    val updatedSystem = stringResource(R.string.settings_app_updated_system)
    val badges = buildList {
        if (!app.enabled) {
            add(disabled to (MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer))
        }
        when {
            app.isUpdatedSystem -> add(
                updatedSystem to (
                    MaterialTheme.colorScheme.tertiaryContainer to
                        MaterialTheme.colorScheme.onTertiaryContainer
                    ),
            )

            app.isSystem -> add(
                system to (
                    MaterialTheme.colorScheme.secondaryContainer to
                        MaterialTheme.colorScheme.onSecondaryContainer
                    ),
            )
        }
    }
    if (badges.isEmpty()) {
        return
    }
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        badges.forEach { (label, colors) ->
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = colors.first,
                contentColor = colors.second,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
    }
}

@Composable
internal fun AppHeader(
    app: AppEntry,
    loadIcon: suspend (String) -> ImageBitmap?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    iconSize: Dp = 40.dp,
) {
    val clickModifier = if (onClick != null) {
        Modifier.clickable(onClickLabel = onClickLabel, onClick = onClick)
    } else {
        Modifier
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(clickModifier)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(app = app, loadIcon = loadIcon, size = iconSize)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                AppBadges(app = app, modifier = Modifier.padding(start = 8.dp))
            }
            Text(
                text = app.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (trailing != null) {
            Spacer(modifier = Modifier.width(8.dp))
            trailing()
        }
    }
}

/** Apps tab row: app facts, the app-level master selector and a shortcut to details. */
@Composable
internal fun AppMasterCard(
    app: AppEntry,
    policy: AppPolicy,
    actions: SettingsActions,
    loadIcon: suspend (String) -> ImageBitmap?,
) {
    val overrideCount = policy.features.size
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            AppHeader(
                app = app,
                loadIcon = loadIcon,
                onClick = { actions.openApp(app.packageName) },
                onClickLabel = stringResource(R.string.settings_open_app_details),
                trailing = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (overrideCount > 0) {
                            val description = pluralStringResource(
                                R.plurals.settings_app_override_count,
                                overrideCount,
                                overrideCount,
                            )
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.clearAndSetSemantics {
                                    contentDescription = description
                                },
                            ) {
                                Text(
                                    text = overrideCount.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                        Text(
                            text = "›",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.clearAndSetSemantics { },
                        )
                    }
                },
            )
            Spacer(modifier = Modifier.height(8.dp))
            MasterOverrideSelector(
                value = policy.master,
                onValueChange = { actions.setAppMaster(app.packageName, it) },
            )
        }
    }
}

/** Feature-apps row: the same app facts plus a selector for one feature. */
@Composable
internal fun FeatureAppCard(
    feature: Feature,
    app: AppEntry,
    policy: AppPolicy,
    actions: SettingsActions,
    loadIcon: suspend (String) -> ImageBitmap?,
) {
    val masterOff = policy.master == Override.OFF
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            AppHeader(
                app = app,
                loadIcon = loadIcon,
            )
            Spacer(modifier = Modifier.height(8.dp))
            FeatureOverrideSelector(
                value = policy.getFeatureOverride(feature),
                onValueChange = { actions.setAppFeature(app.packageName, feature, it) },
                enabled = !masterOff,
            )
            if (masterOff) {
                Text(
                    text = stringResource(R.string.settings_app_master_disabled_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}
