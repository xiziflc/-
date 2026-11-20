package com.example.data

import android.os.BatteryManager

enum class ChargeSpeedTier(val tag: String, val labelZh: String) {
    HYPER("MONOWIRE OVERDRIVE", "超级闪充"),
    FAST("NETRUN FAST", "高速快充"),
    STANDARD("SYNC CHARGE", "常规充电"),
    TRICKLE("LUNAR TRICKLE", "涓流养护"),
    FULL("TO THE MOON", "已满电"),
    DISCHARGING("OFFLINE DRAIN", "电池放电")
}

data class SysfsProbeItem(
    val vendorGroup: String,
    val nodeName: String,
    val path: String,
    val value: String?,
    val readable: Boolean
)

data class LiveWaveformPoint(
    val timestampMs: Long,
    val powerWatts: Float,
    val temperatureC: Float,
    val currentMa: Float
)

data class BatteryTelemetry(
    val timestampMs: Long = System.currentTimeMillis(),
    val levelPct: Int = 0,
    val status: Int = BatteryManager.BATTERY_STATUS_UNKNOWN,
    val plugged: Int = 0,
    val health: Int = BatteryManager.BATTERY_HEALTH_UNKNOWN,
    val temperatureC: Float = 0f,
    val rawVoltageMv: Int = 0,
    val rawCurrentNow: Long = 0L,
    val rawCurrentAvg: Long = 0L,
    val chargeCounterUah: Long = 0L,
    val technology: String = "Li-ion",
    val cycleCount: Int? = null,
    val detectedDesignCapacityMah: Int? = null,
    // Calibrated & Protocol values
    val activeProfile: DeviceHardwareProfile = DeviceProfileCatalog.genericAutoFallback,
    val detectedProtocol: DetectedProtocol = DetectedProtocol(
        kind = ProtocolKind.DISCHARGING,
        label = "OFFLINE // 电池放电",
        source = ProtocolDetectionSource.IDLE,
        detail = "未接入充电器"
    ),
    val calibratedVoltageV: Float = 0f,
    val calibratedCurrentMa: Float = 0f,
    val calibratedAvgCurrentMa: Float = 0f,
    val powerWatts: Float = 0f,
    val estimatedMinutesToFull: Int? = null
) {
    val isPlugged: Boolean
        get() = plugged != 0

    val isCharging: Boolean
        get() = status == BatteryManager.BATTERY_STATUS_CHARGING

    val isFull: Boolean
        get() = status == BatteryManager.BATTERY_STATUS_FULL || (isPlugged && levelPct >= 100)

    val plugLabel: String
        get() = when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC 电源适配器"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB 数据口"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "无线充电"
            BatteryManager.BATTERY_PLUGGED_DOCK -> "底座供电"
            else -> "未接入电源"
        }

    val plugShortTag: String
        get() = when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC // 适配器"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB // 端口"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "QI // 无线充"
            BatteryManager.BATTERY_PLUGGED_DOCK -> "DOCK // 底座"
            else -> "BAT // 离线放电"
        }

    val healthLabel: String
        get() = when (health) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "ICE CLEAR · 良好"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "THERMAL SPIKE · 过热"
            BatteryManager.BATTERY_HEALTH_DEAD -> "CELL DEGRADED · 老化"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "OVER VOLT · 过压"
            BatteryManager.BATTERY_HEALTH_COLD -> "CRYOGENIC · 低温"
            else -> "LINK ACTIVE · 正常"
        }

    val speedTier: ChargeSpeedTier
        get() = when {
            isFull -> ChargeSpeedTier.FULL
            !isPlugged -> ChargeSpeedTier.DISCHARGING
            powerWatts >= 40f -> ChargeSpeedTier.HYPER
            powerWatts >= 16f -> ChargeSpeedTier.FAST
            powerWatts >= 5f -> ChargeSpeedTier.STANDARD
            else -> ChargeSpeedTier.TRICKLE
        }

    val remainingMah: Int?
        get() = if (chargeCounterUah > 0L) (chargeCounterUah / 1000L).toInt() else null
}
