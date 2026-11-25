package io.kazutoiris.pure.shot

import android.app.Application
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import io.kazutoiris.pure.shot.ui.settings.RemoteSettingsSync
import io.kazutoiris.pure.shot.ui.settings.SettingsRepository

/**
 * Hosts the libxposed service connection used to mirror local settings into the framework.
 */
class PureShotApp : Application(), XposedServiceHelper.OnServiceListener {

    override fun onCreate() {
        super.onCreate()
        XposedServiceHelper.registerListener(this)
    }

    override fun onServiceBind(service: XposedService) {
        RemoteSettingsSync.bind(service, SettingsRepository.openPrefs(this))
    }

    override fun onServiceDied(service: XposedService) {
        RemoteSettingsSync.unbind(service)
    }
}
