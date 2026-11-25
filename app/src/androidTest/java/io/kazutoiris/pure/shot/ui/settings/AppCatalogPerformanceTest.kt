package io.kazutoiris.pure.shot.ui.settings

import android.content.Context
import android.os.Debug
import android.os.SystemClock
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Phase 6 performance probe: reports catalog size, filtering cost and the icon-cache memory
 * footprint on the current device. Run directly with:
 *
 * `adb shell am instrument -w -e class io.kazutoiris.pure.shot.ui.settings.AppCatalogPerformanceTest \
 *   io.kazutoiris.pure.shot.test/androidx.test.runner.AndroidJUnitRunner`
 */
@RunWith(AndroidJUnit4::class)
class AppCatalogPerformanceTest {

    @Test
    fun loadAllAppsAndIcons() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val catalog = AppCatalogRepository(context)

        val loadStart = SystemClock.elapsedRealtime()
        val apps = runBlocking { catalog.loadApps() }
        val loadMs = SystemClock.elapsedRealtime() - loadStart

        val all = AppListFilter.apply(apps, AppListVisibility.ALL, "")
        val launcher = AppListFilter.apply(apps, AppListVisibility.LAUNCHER, "")
        val userInstalled = AppListFilter.apply(apps, AppListVisibility.USER_INSTALLED, "")

        val iconLoader = AppIconLoader(context)
        collectGarbage()
        val memoryBefore = Debug.getPss()
        val iconStart = SystemClock.elapsedRealtime()
        val icons = runBlocking { all.take(MAX_ICONS).map { iconLoader.load(it.packageName) } }
        val iconMs = SystemClock.elapsedRealtime() - iconStart
        collectGarbage()
        val memoryAfter = Debug.getPss()
        iconLoader.clear()

        Log.i(
            TAG,
            "apps=${apps.size} all=${all.size} launcher=${launcher.size} " +
                "userInstalled=${userInstalled.size} loadMs=$loadMs icons=${icons.count { it != null }} " +
                "iconMs=$iconMs pssDeltaKb=${memoryAfter - memoryBefore}",
        )

        assertTrue("catalog should contain at least one app", apps.isNotEmpty())
        assertTrue("all-apps filter should include the catalog", all.size == apps.size)
    }

    private fun collectGarbage() {
        System.gc()
        System.runFinalization()
        Thread.sleep(200)
    }

    private companion object {
        const val TAG = "PureShotPerf"
        const val MAX_ICONS = 64
    }
}
