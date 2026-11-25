package io.kazutoiris.pure.shot.ui.settings

/**
 * Everything the app picker needs in one frame. [filtered] is precomputed by the view model so the
 * two entry points (Apps tab and per-feature list) always show the same result.
 */
data class AppListUiState(
    val apps: List<AppEntry> = emptyList(),
    val filtered: List<AppEntry> = emptyList(),
    val query: String = "",
    val loading: Boolean = false,
    val failed: Boolean = false,
    val visibility: AppListVisibility = AppListVisibility.Default,
)
