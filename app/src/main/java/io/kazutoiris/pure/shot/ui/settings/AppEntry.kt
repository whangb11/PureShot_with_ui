package io.kazutoiris.pure.shot.ui.settings

/**
 * One installed application in the picker. Contains no Android types so filtering stays testable
 * on the JVM.
 */
data class AppEntry(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
    val isUpdatedSystem: Boolean,
    val hasLauncher: Boolean,
    val enabled: Boolean,
) {
    /** Matches {@code (flags & (FLAG_SYSTEM | FLAG_UPDATED_SYSTEM_APP)) == 0}. */
    val isUserInstalled: Boolean
        get() = !isSystem && !isUpdatedSystem
}
