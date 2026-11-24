package io.kazutoiris.pure.shot.ui.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.kazutoiris.pure.shot.PrefKeys
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test-only helper used by the runtime regression matrix to seed a settings state from
 * instrumentation arguments, e.g.
 *
 * `-Pandroid.testInstrumentationRunnerArguments.module_enabled=false`
 */
@RunWith(AndroidJUnit4::class)
class SettingsSeedTest {

    @Test
    fun seedFromInstrumentationArguments() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = SettingsRepository(context)
        try {
            repository.initialize()
            val arguments = InstrumentationRegistry.getArguments()
            argumentKeys.forEach { (argument, key) ->
                arguments.getString(argument)?.toBooleanStrictOrNull()?.let { value ->
                    repository.setBoolean(key, value)
                }
            }
        } finally {
            repository.close()
        }
    }

    private companion object {
        val argumentKeys = linkedMapOf(
            "module_enabled" to PrefKeys.KEY_MODULE_ENABLED,
            "hide_status_bar" to PrefKeys.KEY_HIDE_STATUS_BAR,
            "hide_navigation_bar" to PrefKeys.KEY_HIDE_NAVIGATION_BAR,
            "hide_input_method" to PrefKeys.KEY_HIDE_INPUT_METHOD,
            "hide_toast" to PrefKeys.KEY_HIDE_TOAST,
            "hide_overlays" to PrefKeys.KEY_HIDE_OVERLAYS,
            "hide_other_system_ui" to PrefKeys.KEY_HIDE_OTHER_SYSTEM_UI,
        )
    }
}
