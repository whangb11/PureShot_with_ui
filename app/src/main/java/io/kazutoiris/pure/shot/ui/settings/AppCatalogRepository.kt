package io.kazutoiris.pure.shot.ui.settings

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.os.Process
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Loads the installed application catalog for the current user.
 *
 * The repository only reports facts (system/updated-system, launcher presence, enabled state);
 * visibility levels, search and ordering live in [AppListFilter] so they stay unit-testable.
 */
class AppCatalogRepository(context: Context) {

    private val appContext = context.applicationContext ?: context
    private val packageManager = appContext.packageManager
    private val launcherApps = appContext.getSystemService(LauncherApps::class.java)

    suspend fun loadApps(): List<AppEntry> = withContext(Dispatchers.IO) {
        val launcherPackages = loadLauncherPackages()
        packageManager.getInstalledApplications(INSTALLED_FLAGS)
            .asSequence()
            // Never offer PureShot itself.
            .filter { it.packageName != appContext.packageName }
            .map { it.toEntry(launcherPackages) }
            .toList()
    }

    private fun ApplicationInfo.toEntry(launcherPackages: Set<String>): AppEntry {
        val label = runCatching { packageManager.getApplicationLabel(this).toString() }
            .getOrDefault(packageName)
        return AppEntry(
            packageName = packageName,
            label = label.ifBlank { packageName },
            isSystem = (flags and ApplicationInfo.FLAG_SYSTEM) != 0,
            isUpdatedSystem = (flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0,
            hasLauncher = packageName in launcherPackages,
            enabled = enabled,
        )
    }

    /**
     * LauncherApps is the source of truth for "has a launcher icon"; PackageManager is only a
     * fallback for frameworks that return an empty list (e.g. restricted profiles).
     */
    private fun loadLauncherPackages(): Set<String> {
        val packages = mutableSetOf<String>()
        try {
            launcherApps?.getActivityList(null, Process.myUserHandle())?.forEach { activity ->
                packages.add(activity.applicationInfo.packageName)
            }
        } catch (_: Throwable) {
            // Fall through to PackageManager.
        }
        if (packages.isEmpty()) {
            try {
                val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
                    .forEach { packages.add(it.activityInfo.packageName) }
            } catch (_: Throwable) {
                // Keep the empty set; the launcher level will simply have no entries.
            }
        }
        return packages
    }

    private companion object {
        const val INSTALLED_FLAGS = PackageManager.MATCH_DISABLED_COMPONENTS or
            PackageManager.MATCH_DIRECT_BOOT_AWARE or
            PackageManager.MATCH_DIRECT_BOOT_UNAWARE
    }
}
