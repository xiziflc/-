package com.example.ui

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.VoltPulseApp
import com.example.data.BatteryTelemetry
import com.example.data.CalibrationSettings
import com.example.data.CurrentUnitMode
import com.example.data.DeviceProfileId
import com.example.data.SysfsProbeItem
import com.example.data.db.ChargingSessionEntity
import com.example.data.db.TelemetrySampleEntity
import com.example.monitor.ChargingMonitorService
import com.example.monitor.ChargingWorkScheduler
import com.example.monitor.KeepAliveHelper
import com.example.monitor.KeepAliveStatus
import com.example.monitor.ShizukuHelper
import com.example.monitor.ShizukuStatus
import com.example.ui.components.CurveDataPoint
import rikka.shizuku.Shizuku
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class VoltPulseViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as VoltPulseApp
    private val batteryReader = app.batteryReader
    private val settingsRepo = app.settingsRepository
    private val chargingRepo = app.chargingRepository

    val settings: StateFlow<CalibrationSettings> = settingsRepo.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CalibrationSettings()
    )

    val sessions: StateFlow<List<ChargingSessionEntity>> = chargingRepo.allSessions.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val isServiceRunning: StateFlow<Boolean> = ChargingMonitorService.isRunning

    val isSentinelArmed: StateFlow<Boolean> =
        ChargingWorkScheduler.observeSentinelArmed(application).stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    private val _telemetry = MutableStateFlow(BatteryTelemetry())
    val telemetry: StateFlow<BatteryTelemetry> = _telemetry.asStateFlow()

    // Short-window 60s high-resolution live waveform (1s tick)
    private val _liveWaveform = MutableStateFlow<List<CurveDataPoint>>(emptyList())
    val liveWaveform: StateFlow<List<CurveDataPoint>> = _liveWaveform.asStateFlow()

    // Continuous foreground full-timeline buffer (fallback when service isn't active)
    private val _foregroundFullCurve = MutableStateFlow<List<CurveDataPoint>>(emptyList())

    // Active persisted Room charging session's complete samples (plug-in to present)
    private val activeRoomCurve: StateFlow<List<CurveDataPoint>> = chargingRepo.activeSession
        .flatMapLatest { session ->
            if (session == null) {
                flowOf(emptyList())
            } else {
                chargingRepo.observeSamples(session.id).map { list ->
                    list.map { s ->
                        CurveDataPoint(
                            timestampMs = s.timestampMs,
                            powerWatts = s.powerWatts,
                            currentMa = s.currentMa,
                            voltageV = s.voltageV,
                            temperatureC = s.temperatureC,
                            levelPct = s.levelPct
                        )
                    }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Complete session curve: prefers the persistent Room charging session curve when available
     * (survives background/screen-off from plug-in to 100%), otherwise uses the continuous
     * foreground timeline buffer so users always see a complete unbroken curve.
     */
    val fullSessionCurve: StateFlow<List<CurveDataPoint>> = combine(
        activeRoomCurve,
        _foregroundFullCurve
    ) { roomPoints, fgPoints ->
        if (roomPoints.size >= 2) roomPoints else fgPoints
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _sysfsNodes = MutableStateFlow<List<SysfsProbeItem>>(emptyList())
    val sysfsNodes: StateFlow<List<SysfsProbeItem>> = _sysfsNodes.asStateFlow()

    private val _selectedSessionId = MutableStateFlow<Long?>(null)
    val selectedSessionId: StateFlow<Long?> = _selectedSessionId.asStateFlow()

    val selectedSession: StateFlow<ChargingSessionEntity?> = _selectedSessionId
        .flatMapLatest { id ->
            if (id == null) flowOf(null) else chargingRepo.observeSession(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val selectedSessionSamples: StateFlow<List<TelemetrySampleEntity>> = _selectedSessionId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else chargingRepo.observeSamples(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _statusRefreshTick = MutableStateFlow(0)

    private val _shizukuStatus = MutableStateFlow(ShizukuStatus())
    val shizukuStatus: StateFlow<ShizukuStatus> = _shizukuStatus.asStateFlow()

    private val shizukuBinderListener = Shizuku.OnBinderReceivedListener {
        refreshShizukuStatus()
    }
    private val shizukuBinderDeadListener = Shizuku.OnBinderDeadListener {
        refreshShizukuStatus()
    }
    private val shizukuPermListener = Shizuku.OnRequestPermissionResultListener { requestCode, _ ->
        if (requestCode == ShizukuHelper.REQUEST_CODE_SHIZUKU) {
            refreshShizukuStatus()
        }
    }

    val keepAliveStatus: StateFlow<KeepAliveStatus> = combine(
        isSentinelArmed,
        isServiceRunning,
        _statusRefreshTick
    ) { armed, running, _ ->
        KeepAliveHelper.checkStatus(app, armed, running)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = KeepAliveHelper.checkStatus(app, sentinelArmed = false, serviceRunning = false)
    )

    private var latestBatteryIntent: Intent? = null
    private var lastFullBufferAppendMs = 0L

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                latestBatteryIntent = intent
                refreshTelemetryOnce(appendWaveform = false)
            }
        }
    }

    init {
        app.registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        runCatching {
            Shizuku.addBinderReceivedListenerSticky(shizukuBinderListener)
            Shizuku.addBinderDeadListener(shizukuBinderDeadListener)
            Shizuku.addRequestPermissionResultListener(shizukuPermListener)
        }
        refreshShizukuStatus()
        rescanSysfs()

        viewModelScope.launch {
            settings.collect {
                refreshTelemetryOnce(appendWaveform = false)
            }
        }

        viewModelScope.launch(Dispatchers.Default) {
            while (isActive) {
                refreshTelemetryOnce(appendWaveform = true)
                delay(1000L)
            }
        }
    }

    private fun refreshTelemetryOnce(appendWaveform: Boolean) {
        val currentSettings = settings.value
        val snapshot = batteryReader.readSnapshot(currentSettings, latestBatteryIntent)
        _telemetry.value = snapshot

        if (appendWaveform) {
            val point = CurveDataPoint(
                timestampMs = snapshot.timestampMs,
                powerWatts = snapshot.powerWatts,
                currentMa = snapshot.calibratedCurrentMa,
                voltageV = snapshot.calibratedVoltageV,
                temperatureC = snapshot.temperatureC,
                levelPct = snapshot.levelPct
            )
            _liveWaveform.value = (_liveWaveform.value + point).takeLast(60)

            // Record into continuous full timeline every 3 seconds (up to 1200 points = 1 hour in-RAM)
            val now = snapshot.timestampMs
            if (lastFullBufferAppendMs == 0L || now - lastFullBufferAppendMs >= 3000L) {
                lastFullBufferAppendMs = now
                val currentFull = _foregroundFullCurve.value
                _foregroundFullCurve.value = if (currentFull.size >= 1200) {
                    // Downsample older half cleanly to retain complete history without unbounded growth
                    currentFull.filterIndexed { idx, _ -> idx % 2 == 0 } + point
                } else {
                    currentFull + point
                }
            }
        }
    }

    fun rescanSysfs() {
        viewModelScope.launch(Dispatchers.IO) {
            _sysfsNodes.value = batteryReader.probeSysfsNodes()
        }
    }

    fun refreshKeepAliveStatus() {
        _statusRefreshTick.value += 1
        refreshShizukuStatus()
    }

    fun refreshShizukuStatus(message: String = _shizukuStatus.value.lastActionMessage) {
        viewModelScope.launch(Dispatchers.IO) {
            _shizukuStatus.value = ShizukuHelper.checkStatus(app, message)
        }
    }

    fun requestShizukuPermission() {
        ShizukuHelper.requestPermission()
    }

    fun applyShizukuHardening() {
        viewModelScope.launch(Dispatchers.IO) {
            _shizukuStatus.value = ShizukuHelper.applyKeepAliveHardening(app)
            _statusRefreshTick.value += 1
        }
    }

    fun toggleMonitorService() {
        if (isServiceRunning.value) {
            ChargingMonitorService.stop(app)
        } else {
            ChargingMonitorService.start(app, autoStopOnUnplug = false)
        }
        refreshKeepAliveStatus()
    }

    fun startMonitorServiceIfIdle() {
        if (!isServiceRunning.value) {
            ChargingMonitorService.start(app, autoStopOnUnplug = false)
        }
        refreshKeepAliveStatus()
    }

    fun applyDeviceProfile(profileId: DeviceProfileId) {
        viewModelScope.launch { settingsRepo.applyDeviceProfile(profileId) }
    }

    fun setVoltageMultiplier(value: Float) {
        viewModelScope.launch { settingsRepo.setVoltageMultiplier(value) }
    }

    fun setCurrentMultiplier(value: Float) {
        viewModelScope.launch { settingsRepo.setCurrentMultiplier(value) }
    }

    fun setInvertCurrentSign(invert: Boolean) {
        viewModelScope.launch { settingsRepo.setInvertCurrentSign(invert) }
    }

    fun setCurrentUnitMode(mode: CurrentUnitMode) {
        viewModelScope.launch { settingsRepo.setCurrentUnitMode(mode) }
    }

    fun setRatedCapacityMah(capacityMah: Int) {
        viewModelScope.launch { settingsRepo.setRatedCapacityMah(capacityMah) }
    }

    fun setAutoStartOnCharge(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepo.setAutoStartOnCharge(enabled)
            if (enabled) {
                ChargingWorkScheduler.scheduleChargingSentinel(app)
            } else {
                ChargingWorkScheduler.cancelChargingSentinel(app)
            }
            refreshKeepAliveStatus()
        }
    }

    fun setEnableAlerts(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.setEnableAlerts(enabled) }
    }

    fun setTempAlertThreshold(tempC: Float) {
        viewModelScope.launch { settingsRepo.setTempAlertThresholdC(tempC) }
    }

    fun setTargetChargeAlertPct(pct: Int) {
        viewModelScope.launch { settingsRepo.setTargetChargeAlertPct(pct) }
    }

    fun resetCalibration() {
        viewModelScope.launch { settingsRepo.resetCalibration() }
    }

    fun selectSession(sessionId: Long?) {
        _selectedSessionId.value = sessionId
    }

    fun deleteSession(sessionId: Long) {
        viewModelScope.launch {
            if (_selectedSessionId.value == sessionId) {
                _selectedSessionId.value = null
            }
            chargingRepo.deleteSession(sessionId)
        }
    }

    fun clearAllSessions() {
        viewModelScope.launch {
            _selectedSessionId.value = null
            chargingRepo.clearAllSessions()
        }
    }

    override fun onCleared() {
        runCatching { app.unregisterReceiver(batteryReceiver) }
        runCatching {
            Shizuku.removeBinderReceivedListener(shizukuBinderListener)
            Shizuku.removeBinderDeadListener(shizukuBinderDeadListener)
            Shizuku.removeRequestPermissionResultListener(shizukuPermListener)
        }
        super.onCleared()
    }
}
