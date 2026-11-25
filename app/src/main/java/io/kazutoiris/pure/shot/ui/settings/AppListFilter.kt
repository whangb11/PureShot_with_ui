package io.kazutoiris.pure.shot.ui.settings

import java.text.Collator
import java.util.Locale

/**
 * Pure visibility/search/sort pipeline for the app picker.
 *
 * The visibility level is applied before the search query, so searching can never reveal apps that
 * the current level hides. Labels are compared with the platform collator; the package name is the
 * stable tie-breaker.
 */
object AppListFilter {

    fun apply(
        apps: List<AppEntry>,
        level: AppListVisibility,
        query: String,
        excludedPackages: Set<String> = emptySet(),
    ): List<AppEntry> {
        val needle = query.trim().lowercase(Locale.ROOT)
        val collator = Collator.getInstance()
        val comparator = Comparator<AppEntry> { left, right ->
            val byLabel = collator.compare(left.label, right.label)
            if (byLabel != 0) byLabel else left.packageName.compareTo(right.packageName)
        }

        return apps.asSequence()
            .filter { it.packageName !in excludedPackages }
            .filter { isVisible(it, level) }
            .filter { needle.isEmpty() || matches(it, needle) }
            .sortedWith(comparator)
            .toList()
    }

    private fun isVisible(app: AppEntry, level: AppListVisibility): Boolean = when (level) {
        AppListVisibility.LAUNCHER -> app.enabled && app.hasLauncher
        AppListVisibility.USER_INSTALLED -> app.enabled && app.isUserInstalled
        // Disabled apps stay listed here (with a marker in the UI), so settings can be prepared
        // before the app is enabled again.
        AppListVisibility.ALL -> true
    }

    private fun matches(app: AppEntry, lowercaseNeedle: String): Boolean =
        app.label.lowercase(Locale.ROOT).contains(lowercaseNeedle) ||
            app.packageName.lowercase(Locale.ROOT).contains(lowercaseNeedle)
}
