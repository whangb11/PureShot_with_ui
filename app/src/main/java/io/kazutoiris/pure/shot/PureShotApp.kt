package io.kazutoiris.pure.shot

import android.app.Application
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import io.kazutoiris.pure.shot.ui.settings.SettingsStore
import java.util.concurrent.Executors

/**
 * Owns the libxposed service connection and the process-wide [SettingsStore].
 *
 * Service callbacks are dispatched to a serial background executor because RemotePreferences
 * access is a binder round-trip and must not block the main thread.
 */
class PureShotApp : Application(), XposedServiceHelper.OnServiceListener {

    lateinit var settingsStore: SettingsStore
        private set

    // Serializes bind/died callbacks and keeps RemotePreferences binder calls off the main thread.
    private val serviceExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "pureshot-service")
    }

    override fun onCreate() {
        super.onCreate()
        settingsStore = SettingsStore(this)
        XposedServiceHelper.registerListener(this)
    }

    override fun onServiceBind(service: XposedService) {
        serviceExecutor.execute {
            settingsStore.bindService(service)
        }
    }

    override fun onServiceDied(service: XposedService) {
        serviceExecutor.execute {
            settingsStore.unbindService(service)
        }
    }
}
