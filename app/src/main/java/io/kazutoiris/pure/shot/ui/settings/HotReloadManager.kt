package io.kazutoiris.pure.shot.ui.settings

import io.github.libxposed.service.HookedTarget
import io.github.libxposed.service.HotReloadResult
import io.github.libxposed.service.XposedService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Requests a manual hot reload of the module code running inside `system_server`.
 *
 * All binder calls run on [Dispatchers.IO]. The framework reports the result asynchronously on a
 * Binder thread, so the caller is suspended until the callback arrives (or the timeout fires).
 */
class HotReloadManager(
    private val serviceProvider: () -> XposedService?,
) {

    suspend fun checkAvailability(): HotReloadAvailability = withContext(Dispatchers.IO) {
        val service = serviceProvider()
            ?: return@withContext HotReloadAvailability.ServiceUnavailable
        val api = runCatching { service.apiVersion }.getOrElse {
            return@withContext HotReloadAvailability.Error(it.message)
        }
        if (api < XposedService.API_102) {
            return@withContext HotReloadAvailability.ApiUnsupported
        }
        runCatching { pickSystemTarget(service.runningTargets) }.fold(
            onSuccess = { target ->
                if (target == null) {
                    HotReloadAvailability.TargetMissing
                } else {
                    HotReloadAvailability.Available
                }
            },
            onFailure = { HotReloadAvailability.Error(it.message) },
        )
    }

    suspend fun reload(): HotReloadOutcome = withContext(Dispatchers.IO) {
        val service = serviceProvider() ?: return@withContext HotReloadOutcome.ServiceUnavailable
        val api = runCatching { service.apiVersion }.getOrElse {
            return@withContext HotReloadOutcome.Failed(it.message)
        }
        if (api < XposedService.API_102) {
            return@withContext HotReloadOutcome.ApiUnsupported
        }
        val target = runCatching { pickSystemTarget(service.runningTargets) }.getOrElse {
            return@withContext HotReloadOutcome.Failed(it.message)
        } ?: return@withContext HotReloadOutcome.TargetMissing
        if (target.state == HookedTarget.State.RELOADING) {
            return@withContext HotReloadOutcome.InProgress
        }

        val result = try {
            withTimeoutOrNull(HOT_RELOAD_TIMEOUT_MS) { requestReload(service, target) }
        } catch (t: Throwable) {
            return@withContext HotReloadOutcome.Failed(t.message)
        }
        result?.toHotReloadOutcome() ?: HotReloadOutcome.TimedOut
    }

    private suspend fun requestReload(
        service: XposedService,
        target: HookedTarget,
    ): HotReloadResult = suspendCancellableCoroutine { continuation ->
        try {
            service.hotReloadModule(target, null) { _, result ->
                if (continuation.isActive) {
                    continuation.resume(result)
                }
            }
        } catch (t: Throwable) {
            if (continuation.isActive) {
                continuation.resumeWithException(t)
            }
        }
    }

    /**
     * LSPosed exposes `system_server` as `system`; other frameworks may use the long name.
     */
    private fun pickSystemTarget(targets: List<HookedTarget>): HookedTarget? =
        targets.firstOrNull { target ->
            target.processName == SYSTEM_PROCESS_NAME || target.processName == SYSTEM_SERVER_NAME
        }

    private companion object {
        const val SYSTEM_PROCESS_NAME = "system"
        const val SYSTEM_SERVER_NAME = "system_server"
        const val HOT_RELOAD_TIMEOUT_MS = 30_000L
    }
}

/** Maps the framework result to the UI-facing outcome; unit-tested for every status. */
internal fun HotReloadResult.toHotReloadOutcome(): HotReloadOutcome = when (status) {
    HotReloadResult.Status.SUCCEEDED -> HotReloadOutcome.Succeeded
    HotReloadResult.Status.FAILED -> HotReloadOutcome.Failed(message)
    HotReloadResult.Status.UNSUPPORTED -> HotReloadOutcome.Unsupported
    HotReloadResult.Status.IN_PROGRESS -> HotReloadOutcome.InProgress
    HotReloadResult.Status.PROCESS_DIED -> HotReloadOutcome.ProcessDied
}
