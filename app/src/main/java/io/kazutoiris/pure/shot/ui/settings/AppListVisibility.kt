package io.kazutoiris.pure.shot.ui.settings

/**
 * Which applications are visible in the app picker.
 *
 * Levels are persisted as integers in `app_list_visibility`.
 */
enum class AppListVisibility(val level: Int) {
    /** Apps with a launcher icon (default). */
    LAUNCHER(1),

    /** User-installed, non-system apps. */
    USER_INSTALLED(2),

    /** Every app installed for the current user, including system and disabled apps. */
    ALL(3),
    ;

    companion object {
        val Default = LAUNCHER

        fun fromLevel(level: Int): AppListVisibility =
            entries.firstOrNull { it.level == level } ?: Default
    }
}
