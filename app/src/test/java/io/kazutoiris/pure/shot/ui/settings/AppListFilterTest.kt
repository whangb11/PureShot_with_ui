package io.kazutoiris.pure.shot.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppListFilterTest {

    @Test
    fun launcherLevelShowsOnlyEnabledAppsWithLauncherIcons() {
        val own = "io.kazutoiris.pure.shot"
        val apps = listOf(
            app("com.example.launcher", "Launcher"),
            app("com.android.settings", "Settings", isSystem = true),
            app("com.example.headless", "Headless", hasLauncher = false),
            app("com.example.disabled", "Disabled", enabled = false),
            app(own, "PureShot"),
        )

        val result = AppListFilter.apply(
            apps = apps,
            level = AppListVisibility.LAUNCHER,
            query = "",
            excludedPackages = setOf(own),
        )

        assertEquals(
            listOf("com.android.settings", "com.example.launcher"),
            result.map { it.packageName }.sorted(),
        )
    }

    @Test
    fun userInstalledLevelExcludesSystemAndUpdatedSystem() {
        val apps = listOf(
            app("com.example.user", "User"),
            app("com.android.settings", "Settings", isSystem = true),
            app("com.google.android.gms", "Play services", isUpdatedSystem = true),
            app("com.example.disabled", "Disabled", enabled = false),
        )

        val result = AppListFilter.apply(apps, AppListVisibility.USER_INSTALLED, "")

        assertEquals(listOf("com.example.user"), result.map { it.packageName })
    }

    @Test
    fun allLevelIncludesSystemUpdatedSystemAndDisabledApps() {
        val apps = listOf(
            app("com.example.user", "User"),
            app("com.android.settings", "Settings", isSystem = true),
            app("com.google.android.gms", "Play services", isUpdatedSystem = true),
            app("com.example.headless", "Headless", hasLauncher = false),
            app("com.example.disabled", "Disabled", enabled = false),
        )

        val result = AppListFilter.apply(apps, AppListVisibility.ALL, "")

        assertEquals(apps.size, result.size)
    }

    @Test
    fun excludedPackagesAreRemovedAtEveryLevel() {
        val own = "io.kazutoiris.pure.shot"
        val apps = listOf(
            app(own, "PureShot", isSystem = false),
            app("com.example.other", "Other"),
        )

        val result = AppListFilter.apply(
            apps = apps,
            level = AppListVisibility.ALL,
            query = "",
            excludedPackages = setOf(own),
        )

        assertEquals(listOf("com.example.other"), result.map { it.packageName })
    }

    @Test
    fun searchMatchesLabelAndPackageIgnoringCase() {
        val apps = listOf(
            app("com.example.camera", "Camera"),
            app("com.example.gallery", "Gallery"),
            app("org.cam.tools", "Tools"),
        )

        assertEquals(
            listOf("com.example.camera"),
            AppListFilter.apply(apps, AppListVisibility.ALL, "CAMERA").map { it.packageName },
        )
        assertEquals(
            listOf("com.example.camera", "org.cam.tools"),
            AppListFilter.apply(apps, AppListVisibility.ALL, "cam").map { it.packageName },
        )
    }

    @Test
    fun searchDoesNotBypassVisibilityLevel() {
        val apps = listOf(
            app("com.android.hidden", "Hidden settings", isSystem = true, hasLauncher = false),
        )

        assertTrue(AppListFilter.apply(apps, AppListVisibility.LAUNCHER, "hidden").isEmpty())
        assertEquals(
            listOf("com.android.hidden"),
            AppListFilter.apply(apps, AppListVisibility.ALL, "hidden").map { it.packageName },
        )
    }

    @Test
    fun searchIgnoresSurroundingWhitespace() {
        val apps = listOf(app("com.example.camera", "Camera"))

        assertEquals(
            listOf("com.example.camera"),
            AppListFilter.apply(apps, AppListVisibility.ALL, "  cam  ").map { it.packageName },
        )
    }

    @Test
    fun sortsByLabelWithPackageNameTieBreaker() {
        val apps = listOf(
            app("com.example.beta", "Beta"),
            app("com.example.zeta", "Alpha"),
            app("com.example.alpha", "Alpha"),
        )

        assertEquals(
            listOf("com.example.alpha", "com.example.zeta", "com.example.beta"),
            AppListFilter.apply(apps, AppListVisibility.ALL, "").map { it.packageName },
        )
    }

    @Test
    fun emptyQueryKeepsEveryVisibleApp() {
        val apps = listOf(
            app("com.example.one", "One"),
            app("com.example.two", "Two", enabled = false),
        )

        assertEquals(1, AppListFilter.apply(apps, AppListVisibility.LAUNCHER, "").size)
        assertEquals(2, AppListFilter.apply(apps, AppListVisibility.ALL, "").size)
    }

    private fun app(
        packageName: String,
        label: String,
        isSystem: Boolean = false,
        isUpdatedSystem: Boolean = false,
        hasLauncher: Boolean = true,
        enabled: Boolean = true,
    ) = AppEntry(
        packageName = packageName,
        label = label,
        isSystem = isSystem,
        isUpdatedSystem = isUpdatedSystem,
        hasLauncher = hasLauncher,
        enabled = enabled,
    )
}
