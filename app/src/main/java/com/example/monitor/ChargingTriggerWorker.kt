package com.example.monitor

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.VoltPulseApp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Non-Root Auto-Wakeup Sentinel using Android WorkManager + JobScheduler's requiresCharging constraint.
 * Even when the app process is killed by the OS while unplugged, connecting a charger triggers
 * this worker to wake the process and launch ChargingMonitorService for the duration of charging.
 */
class ChargingTriggerWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? VoltPulseApp ?: return Result.success()
        val settings = app.settingsRepository.getSnapshot()
        if (!settings.autoStartOnCharge) {
            return Result.success()
        }

        val snapshot = app.batteryReader.readSnapshot(settings)
        if (snapshot.isPlugged && !ChargingMonitorService.isRunning.value) {
            ChargingMonitorService.start(
                context = applicationContext,
                autoStopOnUnplug = true
            )
        }
        return Result.success()
    }
}

object ChargingWorkScheduler {
    private const val UNIQUE_SENTINEL_WORK = "voltpulse_charging_trigger_sentinel"

    fun scheduleChargingSentinel(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiresCharging(true)
            .build()

        val request = OneTimeWorkRequestBuilder<ChargingTriggerWorker>()
            .setConstraints(constraints)
            .addTag(UNIQUE_SENTINEL_WORK)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_SENTINEL_WORK,
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    fun cancelChargingSentinel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_SENTINEL_WORK)
    }

    fun observeSentinelArmed(context: Context): Flow<Boolean> {
        return WorkManager.getInstance(context)
            .getWorkInfosForUniqueWorkFlow(UNIQUE_SENTINEL_WORK)
            .map { infos ->
                infos.any {
                    it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING
                }
            }
    }
}
