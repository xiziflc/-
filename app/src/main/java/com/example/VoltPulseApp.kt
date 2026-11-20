package com.example

import android.app.Application
import com.example.data.BatteryReader
import com.example.data.ChargingRepository
import com.example.data.SettingsRepository
import com.example.data.db.VoltPulseDatabase
import com.example.monitor.ChargingMonitorService
import com.example.monitor.ChargingWorkScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class VoltPulseApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: VoltPulseDatabase by lazy { VoltPulseDatabase.getInstance(this) }
    val chargingRepository: ChargingRepository by lazy { ChargingRepository(database.chargingDao()) }
    val settingsRepository: SettingsRepository by lazy { SettingsRepository(this) }
    val batteryReader: BatteryReader by lazy { BatteryReader(this) }

    override fun onCreate() {
        super.onCreate()
        ChargingMonitorService.ensureNotificationChannels(this)

        appScope.launch {
            val settings = settingsRepository.getSnapshot()
            if (settings.autoStartOnCharge) {
                ChargingWorkScheduler.scheduleChargingSentinel(this@VoltPulseApp)
            }
        }
    }
}
