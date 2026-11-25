package io.kazutoiris.pure.shot.ui.settings

import androidx.compose.runtime.Stable
import io.kazutoiris.pure.shot.hook.Feature
import io.kazutoiris.pure.shot.hook.Override

/**
 * Callbacks for the settings screens.
 *
 * Keeping them in one stable object avoids threading a dozen lambdas through every composable and
 * makes Compose previews trivial (`SettingsActions()` provides no-ops).
 */
@Stable
data class SettingsActions(
    val toggle: (String, Boolean) -> Unit = { _, _ -> },
    val reset: () -> Unit = {},
    val selectTab: (SettingsTab) -> Unit = {},
    val openFeatureApps: (Feature) -> Unit = {},
    val openApp: (String) -> Unit = {},
    val back: () -> Unit = {},
    val setQuery: (String) -> Unit = {},
    val clearQuery: () -> Unit = {},
    val setVisibility: (AppListVisibility) -> Unit = {},
    val setAppMaster: (String, Override) -> Unit = { _, _ -> },
    val setAppFeature: (String, Feature, Boolean?) -> Unit = { _, _, _ -> },
    val clearApp: (String) -> Unit = {},
    val refreshApps: () -> Unit = {},
    val reloadSystemHooks: () -> Unit = {},
)
