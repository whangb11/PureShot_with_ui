package io.kazutoiris.pure.shot.ui.settings

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.kazutoiris.pure.shot.PrefKeys
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsRepositoryTest {

    private lateinit var context: Context
    private lateinit var repository: SettingsRepository

    @Before
    fun setUp() {
        context = IsolatedContext(ApplicationProvider.getApplicationContext())
        prefs().edit().clear().commit()
        repository = SettingsRepository(context)
    }

    @After
    fun tearDown() {
        repository.close()
        prefs().edit().clear().commit()
    }

    @Test
    fun initializeCreatesAllDefaults() {
        assertTrue(repository.initialize())

        assertEquals(7, prefs().all.size)
        assertEquals(SettingsState.Default, repository.state.value)
    }

    @Test
    fun writeAndResetUpdateStateAndPersist() {
        repository.initialize()

        assertTrue(repository.setBoolean(PrefKeys.KEY_HIDE_TOAST, false))
        assertFalse(repository.state.value.hideToast)

        assertTrue(repository.resetToDefaults())
        assertTrue(repository.state.value.hideToast)

        repository.close()
        repository = SettingsRepository(context)
        assertTrue(repository.state.value.hideToast)
    }

    private fun prefs() =
        context.getSharedPreferences(PrefKeys.PREFS_FILE, Context.MODE_PRIVATE)

    private class IsolatedContext(base: Context) : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this

        override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences =
            super.getSharedPreferences("test_$name", mode)
    }
}
