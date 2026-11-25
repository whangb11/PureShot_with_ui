package io.kazutoiris.pure.shot.ui.settings

/**
 * Whether the manual "reload system hooks" action can be used right now.
 *
 * The availability check needs the framework service and a running `system` target, so it is
 * performed off the main thread and refreshed when the service reconnects or the screen resumes.
 */
sealed interface HotReloadAvailability {
    data object Checking : HotReloadAvailability
    data object Available : HotReloadAvailability
    data object ServiceUnavailable : HotReloadAvailability
    data object ApiUnsupported : HotReloadAvailability
    data object TargetMissing : HotReloadAvailability
    data class Error(val message: String?) : HotReloadAvailability
}

/** Result of the most recent manual hot-reload request. */
sealed interface HotReloadOutcome {
    data object Succeeded : HotReloadOutcome
    data class Failed(val message: String?) : HotReloadOutcome
    data object Unsupported : HotReloadOutcome
    data object InProgress : HotReloadOutcome
    data object ProcessDied : HotReloadOutcome
    data object TimedOut : HotReloadOutcome
    data object ServiceUnavailable : HotReloadOutcome
    data object ApiUnsupported : HotReloadOutcome
    data object TargetMissing : HotReloadOutcome
}

data class HotReloadUiState(
    val availability: HotReloadAvailability = HotReloadAvailability.Checking,
    val running: Boolean = false,
    val outcome: HotReloadOutcome? = null,
) {
    val canReload: Boolean
        get() = !running && availability == HotReloadAvailability.Available
}
