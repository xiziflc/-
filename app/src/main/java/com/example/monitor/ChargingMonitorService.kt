package com.example.monitor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.MainActivity
import com.example.R
import com.example.VoltPulseApp
import com.example.data.BatteryTelemetry
import com.example.data.CalibrationSettings
import com.example.data.db.ChargingSessionEntity
import com.example.data.db.TelemetrySampleEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

/**
 * Non-Root Foreground Telemetry Sentinel:
 * Records high-resolution complete charging curves (Power, Current, Voltage, Temp, SOC%)
 * and detected protocol (SuperVOOC / Mi Turbo / PPS / PD) into Room while connected to power.
 */
class ChargingMonitorService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var samplingJob: Job? = null
    private var settingsJob: Job? = null

    private var currentSettings = CalibrationSettings()
    private var autoStopOnUnplug = false

    private var activeSession: ChargingSessionEntity? = null
    private var powerSum = 0.0
    private var tempSum = 0.0
    private var accumulatedMah = 0.0
    private var lastSampleTimeMs = 0L
    private var lastPersistedSampleTimeMs = 0L
    private var lastPersistedLevel = -1
    private var lastPersistedPower = -1f
    private var unpluggedSinceMs: Long? = null
    private var highTempAlertFired = false
    private var targetChargeAlertFired = false

    private var lastBatteryIntent: Intent? = null

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return
            when (intent.action) {
                Intent.ACTION_BATTERY_CHANGED -> lastBatteryIntent = intent
                Intent.ACTION_POWER_DISCONNECTED -> unpluggedSinceMs = System.currentTimeMillis()
                Intent.ACTION_POWER_CONNECTED -> unpluggedSinceMs = null
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannels(this)
        _isRunning.value = true

        val app = application as VoltPulseApp
        settingsJob = serviceScope.launch {
            app.settingsRepository.settingsFlow.collect { settings ->
                currentSettings = settings
            }
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        registerReceiver(batteryReceiver, filter)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            finalizeAndStop()
            return START_NOT_STICKY
        }

        autoStopOnUnplug = intent?.getBooleanExtra(EXTRA_AUTO_STOP_ON_UNPLUG, false) ?: false

        val app = application as VoltPulseApp
        val initialSnapshot = app.batteryReader.readSnapshot(currentSettings, lastBatteryIntent)
        val notification = buildOngoingNotification(initialSnapshot)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID_ONGOING,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID_ONGOING, notification)
            }
        } catch (_: Exception) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (samplingJob == null || samplingJob?.isActive != true) {
            startSamplingLoop()
        }

        return START_STICKY
    }

    private fun startSamplingLoop() {
        val app = application as VoltPulseApp
        samplingJob = serviceScope.launch {
            currentSettings = app.settingsRepository.getSnapshot()

            while (isActive) {
                val now = System.currentTimeMillis()
                val telemetry = app.batteryReader.readSnapshot(currentSettings, lastBatteryIntent)
                _liveServiceTelemetry.value = telemetry

                updateNotification(telemetry)

                if (telemetry.isPlugged) {
                    unpluggedSinceMs = null
                    recordChargingSample(app, telemetry, now)
                    checkSafetyAlerts(telemetry)
                } else {
                    if (activeSession != null) {
                        completeActiveSession(app, now)
                    }
                    if (autoStopOnUnplug) {
                        val disconnectedAt = unpluggedSinceMs ?: now.also { unpluggedSinceMs = it }
                        if (now - disconnectedAt >= AUTO_STOP_DELAY_MS) {
                            if (currentSettings.autoStartOnCharge) {
                                ChargingWorkScheduler.scheduleChargingSentinel(applicationContext)
                            }
                            stopSelf()
                            break
                        }
                    }
                }

                delay(SAMPLE_INTERVAL_MS)
            }
        }
    }

    private suspend fun recordChargingSample(
        app: VoltPulseApp,
        telemetry: BatteryTelemetry,
        now: Long
    ) {
        val existing = activeSession
        if (existing == null) {
            powerSum = telemetry.powerWatts.toDouble()
            tempSum = telemetry.temperatureC.toDouble()
            accumulatedMah = 0.0
            lastSampleTimeMs = now
            lastPersistedSampleTimeMs = now
            lastPersistedLevel = telemetry.levelPct
            lastPersistedPower = telemetry.powerWatts
            highTempAlertFired = false
            targetChargeAlertFired = false

            val newSession = ChargingSessionEntity(
                startTimeMs = now,
                endTimeMs = now,
                startLevelPct = telemetry.levelPct,
                endLevelPct = telemetry.levelPct,
                plugTypeLabel = telemetry.plugLabel,
                protocolLabel = telemetry.detectedProtocol.label,
                peakPowerWatts = telemetry.powerWatts,
                avgPowerWatts = telemetry.powerWatts,
                peakTempC = telemetry.temperatureC,
                avgTempC = telemetry.temperatureC,
                mahAdded = 0f,
                sampleCount = 1,
                isCompleted = false
            )
            val id = app.chargingRepository.createSession(newSession)
            activeSession = newSession.copy(id = id)

            app.chargingRepository.addSample(
                TelemetrySampleEntity(
                    sessionId = id,
                    timestampMs = now,
                    levelPct = telemetry.levelPct,
                    powerWatts = telemetry.powerWatts,
                    currentMa = telemetry.calibratedCurrentMa,
                    voltageV = telemetry.calibratedVoltageV,
                    temperatureC = telemetry.temperatureC
                )
            )
            return
        }

        val dtHours = ((now - lastSampleTimeMs).coerceIn(0L, 30_000L)) / 3_600_000.0
        if (telemetry.calibratedCurrentMa > 0f) {
            accumulatedMah += telemetry.calibratedCurrentMa * dtHours
        }
        lastSampleTimeMs = now

        val newCount = existing.sampleCount + 1
        powerSum += telemetry.powerWatts.toDouble()
        tempSum += telemetry.temperatureC.toDouble()

        val bestProtocolLabel = if (telemetry.powerWatts >= existing.peakPowerWatts * 0.9f &&
            telemetry.detectedProtocol.kind != com.example.data.ProtocolKind.TRICKLE
        ) {
            telemetry.detectedProtocol.label
        } else {
            existing.protocolLabel.ifBlank { telemetry.detectedProtocol.label }
        }

        val updated = existing.copy(
            endTimeMs = now,
            endLevelPct = telemetry.levelPct,
            plugTypeLabel = telemetry.plugLabel,
            protocolLabel = bestProtocolLabel,
            peakPowerWatts = max(existing.peakPowerWatts, telemetry.powerWatts),
            avgPowerWatts = (powerSum / newCount).toFloat(),
            peakTempC = max(existing.peakTempC, telemetry.temperatureC),
            avgTempC = (tempSum / newCount).toFloat(),
            mahAdded = accumulatedMah.toFloat(),
            sampleCount = newCount
        )
        activeSession = updated

        val shouldPersistPoint = (now - lastPersistedSampleTimeMs >= PERSIST_POINT_INTERVAL_MS) ||
            (telemetry.levelPct != lastPersistedLevel) ||
            (abs(telemetry.powerWatts - lastPersistedPower) >= 2.0f)

        if (shouldPersistPoint) {
            lastPersistedSampleTimeMs = now
            lastPersistedLevel = telemetry.levelPct
            lastPersistedPower = telemetry.powerWatts
            app.chargingRepository.updateSession(updated)
            app.chargingRepository.addSample(
                TelemetrySampleEntity(
                    sessionId = updated.id,
                    timestampMs = now,
                    levelPct = telemetry.levelPct,
                    powerWatts = telemetry.powerWatts,
                    currentMa = telemetry.calibratedCurrentMa,
                    voltageV = telemetry.calibratedVoltageV,
                    temperatureC = telemetry.temperatureC
                )
            )
        }
    }

    private suspend fun completeActiveSession(app: VoltPulseApp, now: Long) {
        val current = activeSession ?: return
        app.chargingRepository.updateSession(
            current.copy(endTimeMs = now, isCompleted = true)
        )
        activeSession = null
        highTempAlertFired = false
        targetChargeAlertFired = false
    }

    private fun checkSafetyAlerts(telemetry: BatteryTelemetry) {
        if (!currentSettings.enableAlerts) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (!highTempAlertFired && telemetry.temperatureC >= currentSettings.tempAlertThresholdC) {
            highTempAlertFired = true
            val title = "⚠️ LUCY 热保护告警 ${String.format(Locale.US, "%.1f°C", telemetry.temperatureC)}"
            val body = "当前充电温度已超过设定阈值 ${String.format(Locale.US, "%.1f°C", currentSettings.tempAlertThresholdC)}，建议降低负载或改善散热。"
            nm.notify(NOTIFICATION_ID_TEMP_ALERT, buildAlertNotification(title, body))
        }

        if (!targetChargeAlertFired && telemetry.isCharging && telemetry.levelPct >= currentSettings.targetChargeAlertPct) {
            targetChargeAlertFired = true
            val title = "🌙 TO THE MOON · 已达 ${telemetry.levelPct}%"
            val body = "电池已充至设定的 ${currentSettings.targetChargeAlertPct}% 养护目标，可断开电源延长循环寿命。"
            nm.notify(NOTIFICATION_ID_TARGET_ALERT, buildAlertNotification(title, body))
        }
    }

    private fun updateNotification(telemetry: BatteryTelemetry) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID_ONGOING, buildOngoingNotification(telemetry))
    }

    private fun buildOngoingNotification(telemetry: BatteryTelemetry): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, ChargingMonitorService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val powerStr = String.format(Locale.US, "%.1fW", telemetry.powerWatts)
        val currentStr = String.format(Locale.US, "%.0fmA", telemetry.calibratedCurrentMa)
        val voltStr = String.format(Locale.US, "%.2fV", telemetry.calibratedVoltageV)
        val tempStr = String.format(Locale.US, "%.1f°C", telemetry.temperatureC)

        val title = if (telemetry.isPlugged) {
            "⚡ $powerStr · ${telemetry.levelPct}% · ${telemetry.detectedProtocol.kind.shortLabel}"
        } else {
            "🌙 LUCY 遥测 · ${telemetry.levelPct}% · $powerStr"
        }
        val content = "电流 $currentStr | 电压 $voltStr | 温度 $tempStr"

        return NotificationCompat.Builder(this, CHANNEL_MONITOR_ID)
            .setSmallIcon(R.drawable.ic_stat_volt)
            .setContentTitle(title)
            .setContentText(content)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openIntent)
            .addAction(0, getString(R.string.action_stop_monitor), stopIntent)
            .build()
    }

    private fun buildAlertNotification(title: String, body: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            2,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ALERT_ID)
            .setSmallIcon(R.drawable.ic_stat_volt)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openIntent)
            .build()
    }

    private fun finalizeAndStop() {
        val app = application as VoltPulseApp
        serviceScope.launch {
            completeActiveSession(app, System.currentTimeMillis())
            stopSelf()
        }
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(batteryReceiver) }
        val app = application as VoltPulseApp
        val sessionToClose = activeSession
        if (sessionToClose != null) {
            CoroutineScope(Dispatchers.IO).launch {
                app.chargingRepository.updateSession(
                    sessionToClose.copy(
                        endTimeMs = System.currentTimeMillis(),
                        isCompleted = true
                    )
                )
            }
        }
        _isRunning.value = false
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_MONITOR_ID = "voltpulse_monitor_channel"
        const val CHANNEL_ALERT_ID = "voltpulse_alert_channel"
        const val ACTION_START = "com.example.voltpulse.ACTION_START"
        const val ACTION_STOP = "com.example.voltpulse.ACTION_STOP"
        const val EXTRA_AUTO_STOP_ON_UNPLUG = "extra_auto_stop_on_unplug"

        private const val NOTIFICATION_ID_ONGOING = 4096
        private const val NOTIFICATION_ID_TEMP_ALERT = 4097
        private const val NOTIFICATION_ID_TARGET_ALERT = 4098
        private const val SAMPLE_INTERVAL_MS = 2000L
        private const val PERSIST_POINT_INTERVAL_MS = 5000L
        private const val AUTO_STOP_DELAY_MS = 6000L

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        private val _liveServiceTelemetry = MutableStateFlow<BatteryTelemetry?>(null)
        val liveServiceTelemetry: StateFlow<BatteryTelemetry?> = _liveServiceTelemetry.asStateFlow()

        fun ensureNotificationChannels(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

                val monitorChannel = NotificationChannel(
                    CHANNEL_MONITOR_ID,
                    context.getString(R.string.channel_monitor_name),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = context.getString(R.string.channel_monitor_desc)
                    setShowBadge(false)
                }

                val alertChannel = NotificationChannel(
                    CHANNEL_ALERT_ID,
                    context.getString(R.string.channel_alert_name),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = context.getString(R.string.channel_alert_desc)
                    enableVibration(true)
                }

                nm.createNotificationChannel(monitorChannel)
                nm.createNotificationChannel(alertChannel)
            }
        }

        fun start(context: Context, autoStopOnUnplug: Boolean): Boolean {
            ensureNotificationChannels(context)
            val intent = Intent(context, ChargingMonitorService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_AUTO_STOP_ON_UNPLUG, autoStopOnUnplug)
            }
            return runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                true
            }.getOrDefault(false)
        }

        fun stop(context: Context) {
            val intent = Intent(context, ChargingMonitorService::class.java).apply {
                action = ACTION_STOP
            }
            runCatching { context.startService(intent) }
        }
    }
}
