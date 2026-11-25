package io.kazutoiris.pure.shot.ui.settings

import io.github.libxposed.service.HotReloadResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Covers the degraded paths of the manual hot reload action. The success paths require a real
 * framework service and are verified on the API 37 AVD (see `plan/phase5-report.md`).
 */
class HotReloadManagerTest {

    @Test
    fun `availability without a service is unavailable`() = runBlocking {
        val manager = HotReloadManager { null }

        assertEquals(HotReloadAvailability.ServiceUnavailable, manager.checkAvailability())
    }

    @Test
    fun `reload without a service returns service unavailable`() = runBlocking {
        val manager = HotReloadManager { null }

        assertEquals(HotReloadOutcome.ServiceUnavailable, manager.reload())
    }

    @Test
    fun `every framework status maps to an outcome`() {
        assertEquals(
            HotReloadOutcome.Succeeded,
            HotReloadResult(HotReloadResult.Status.SUCCEEDED, null).toHotReloadOutcome(),
        )
        assertEquals(
            HotReloadOutcome.Failed(null),
            HotReloadResult(HotReloadResult.Status.FAILED, null).toHotReloadOutcome(),
        )
        assertEquals(
            HotReloadOutcome.Failed("boom"),
            HotReloadResult(HotReloadResult.Status.FAILED, "boom").toHotReloadOutcome(),
        )
        assertEquals(
            HotReloadOutcome.Unsupported,
            HotReloadResult(HotReloadResult.Status.UNSUPPORTED, null).toHotReloadOutcome(),
        )
        assertEquals(
            HotReloadOutcome.InProgress,
            HotReloadResult(HotReloadResult.Status.IN_PROGRESS, null).toHotReloadOutcome(),
        )
        assertEquals(
            HotReloadOutcome.ProcessDied,
            HotReloadResult(HotReloadResult.Status.PROCESS_DIED, null).toHotReloadOutcome(),
        )
    }
}
