package io.kazutoiris.pure.shot.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.kazutoiris.pure.shot.R
import io.kazutoiris.pure.shot.hook.AppPolicy
import io.kazutoiris.pure.shot.hook.Feature

/**
 * Per-feature app list. It shares the Apps tab's search query and visibility directly, so both
 * entry points always agree.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeatureAppsScreen(
    feature: Feature,
    state: SettingsState,
    appList: AppListUiState,
    snackbarHostState: SnackbarHostState,
    actions: SettingsActions,
    loadIcon: suspend (String) -> ImageBitmap?,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(featureTitleRes(feature)),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    TextButton(onClick = actions.back) {
                        Text(text = stringResource(R.string.settings_back))
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
            if (!state.moduleEnabled) {
                GlobalDisabledBanner(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            GlobalFeatureSummaryBar(
                feature = feature,
                state = state,
                actions = actions,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
            )
            AppListPane(
                appList = appList,
                actions = actions,
                modifier = Modifier.weight(1f),
                listState = listState,
            ) { app ->
                val policy = state.perApp[app.packageName] ?: AppPolicy.DEFAULT
                FeatureAppCard(
                    feature = feature,
                    app = app,
                    policy = policy,
                    actions = actions,
                    loadIcon = loadIcon,
                )
            }
        }
    }
}
