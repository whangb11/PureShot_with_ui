package io.kazutoiris.pure.shot.ui.settings

import androidx.annotation.StringRes
import io.kazutoiris.pure.shot.PrefKeys
import io.kazutoiris.pure.shot.R
import io.kazutoiris.pure.shot.hook.Feature

/**
 * UI metadata for the six global feature switches. Keeping it in one place lets the global tab,
 * the per-feature app list and the app detail screen agree on labels and order.
 */
internal data class GlobalFeatureRow(
    val feature: Feature,
    val key: String,
    @param:StringRes val titleRes: Int,
    @param:StringRes val summaryRes: Int? = null,
    val isHidden: (SettingsState) -> Boolean,
)

internal val globalFeatureRows = listOf(
    GlobalFeatureRow(
        feature = Feature.STATUS_BAR,
        key = PrefKeys.KEY_HIDE_STATUS_BAR,
        titleRes = R.string.settings_status_bar_title,
        summaryRes = R.string.settings_status_bar_summary,
        isHidden = { it.hideStatusBar },
    ),
    GlobalFeatureRow(
        feature = Feature.NAVIGATION_BAR,
        key = PrefKeys.KEY_HIDE_NAVIGATION_BAR,
        titleRes = R.string.settings_nav_bar_title,
        summaryRes = R.string.settings_nav_bar_summary,
        isHidden = { it.hideNavigationBar },
    ),
    GlobalFeatureRow(
        feature = Feature.INPUT_METHOD,
        key = PrefKeys.KEY_HIDE_INPUT_METHOD,
        titleRes = R.string.settings_ime_title,
        isHidden = { it.hideInputMethod },
    ),
    GlobalFeatureRow(
        feature = Feature.TOAST,
        key = PrefKeys.KEY_HIDE_TOAST,
        titleRes = R.string.settings_toast_title,
        isHidden = { it.hideToast },
    ),
    GlobalFeatureRow(
        feature = Feature.OVERLAY,
        key = PrefKeys.KEY_HIDE_OVERLAYS,
        titleRes = R.string.settings_overlays_title,
        isHidden = { it.hideOverlays },
    ),
    GlobalFeatureRow(
        feature = Feature.OTHER,
        key = PrefKeys.KEY_HIDE_OTHER_SYSTEM_UI,
        titleRes = R.string.settings_other_title,
        summaryRes = R.string.settings_other_summary,
        isHidden = { it.hideOtherSystemUi },
    ),
)

@StringRes
internal fun featureTitleRes(feature: Feature): Int =
    globalFeatureRows.firstOrNull { it.feature == feature }?.titleRes
        ?: R.string.settings_other_title

internal fun SettingsState.isFeatureHidden(feature: Feature): Boolean =
    globalFeatureRows.firstOrNull { it.feature == feature }?.isHidden?.invoke(this) == true

@StringRes
internal fun AppListVisibility.labelRes(): Int = when (this) {
    AppListVisibility.LAUNCHER -> R.string.settings_visibility_launcher
    AppListVisibility.USER_INSTALLED -> R.string.settings_visibility_user_installed
    AppListVisibility.ALL -> R.string.settings_visibility_all
}
