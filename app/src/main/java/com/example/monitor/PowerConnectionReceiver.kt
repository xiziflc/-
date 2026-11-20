package com.example.monitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.VoltPulseApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Re-arms the WorkManager charging sentinel automatically after device reboot or app update.
 */
class PowerConnectionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val pending = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val app = context.applicationContext as? VoltPulseApp
                    val enabled = app?.settingsRepository?.getSnapshot()?.autoStartOnCharge ?: true
                    if (enabled) {
                        ChargingWorkScheduler.scheduleChargingSentinel(context.applicationContext)
                    }
                } finally {
                    pending.finish()
                }
            }
        }
    }
}
