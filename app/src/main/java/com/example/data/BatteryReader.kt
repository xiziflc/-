package com.example.data

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

class BatteryReader(private val context: Context) {

    private val batteryManager: BatteryManager? =
        context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager

    private val cachedDesignCapacityMah: Int? by lazy { readDesignCapacityViaPowerProfile() }

    fun readSnapshot(
        settings: CalibrationSettings,
        batteryIntent: Intent? = null
    ): BatteryTelemetry {
        val intent = batteryIntent ?: context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )

        val levelRaw = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scaleRaw = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val levelPct = if (levelRaw >= 0 && scaleRaw > 0) {
            ((levelRaw * 100f) / scaleRaw).roundToInt().coerceIn(0, 100)
        } else {
            batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.coerceIn(0, 100) ?: 0
        }

        val status = intent?.getIntExtra(
            BatteryManager.EXTRA_STATUS,
            BatteryManager.BATTERY_STATUS_UNKNOWN
        ) ?: BatteryManager.BATTERY_STATUS_UNKNOWN

        val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val health = intent?.getIntExtra(
            BatteryManager.EXTRA_HEALTH,
            BatteryManager.BATTERY_HEALTH_UNKNOWN
        ) ?: BatteryManager.BATTERY_HEALTH_UNKNOWN

        val tempTenths = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val tempC = tempTenths / 10f

        val rawVoltage = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        val rawVoltageMv = when {
            rawVoltage in 1..20 -> rawVoltage * 1000
            rawVoltage > 100_000 -> rawVoltage / 1000
            else -> rawVoltage
        }

        val technology = intent?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY)
            ?.takeIf { it.isNotBlank() } ?: "Li-ion"

        val cycleCount = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val cycles = intent?.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1) ?: -1
            cycles.takeIf { it >= 0 }
        } else {
            null
        }

        val rawCurrentNow = safeLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        val rawCurrentAvg = safeLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE)
        val chargeCounter = safeLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)

        val isPlugged = plugged != 0
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING
        val isFull = status == BatteryManager.BATTERY_STATUS_FULL || (isPlugged && levelPct >= 100)

        val currentMaBase = normalizeCurrentToMa(rawCurrentNow, settings.currentUnitMode)
        val avgCurrentMaBase = normalizeCurrentToMa(rawCurrentAvg, settings.currentUnitMode)

        val signAdjustedNow = adjustSign(currentMaBase, isCharging, isPlugged, settings.invertCurrentSign)
        val signAdjustedAvg = adjustSign(avgCurrentMaBase, isCharging, isPlugged, settings.invertCurrentSign)

        val calibratedCurrentMa = signAdjustedNow * settings.currentMultiplier
        val calibratedAvgCurrentMa = signAdjustedAvg * settings.currentMultiplier
        val calibratedVoltageV = (rawVoltageMv / 1000f) * settings.voltageMultiplier
        val powerWatts = abs(calibratedVoltageV * calibratedCurrentMa / 1000f)

        val activeProfile = DeviceProfileCatalog.resolveProfile(settings.deviceProfileId)
        val quickSysfsHints = if (isPlugged) readQuickProtocolSysfsHints() else emptyMap()
        val detectedProtocol = ChargingProtocolAnalyzer.analyze(
            profile = activeProfile,
            isPlugged = isPlugged,
            isFull = isFull,
            pluggedType = plugged,
            powerWatts = powerWatts,
            sysfsHints = quickSysfsHints
        )

        val etaMinutes = estimateTimeToFullMinutes(
            isCharging = isCharging,
            levelPct = levelPct,
            currentMa = calibratedCurrentMa,
            ratedCapacityMah = settings.ratedCapacityMah
        )

        return BatteryTelemetry(
            timestampMs = System.currentTimeMillis(),
            levelPct = levelPct,
            status = status,
            plugged = plugged,
            health = health,
            temperatureC = tempC,
            rawVoltageMv = rawVoltageMv,
            rawCurrentNow = rawCurrentNow,
            rawCurrentAvg = rawCurrentAvg,
            chargeCounterUah = chargeCounter,
            technology = technology,
            cycleCount = cycleCount,
            detectedDesignCapacityMah = cachedDesignCapacityMah,
            activeProfile = activeProfile,
            detectedProtocol = detectedProtocol,
            calibratedVoltageV = calibratedVoltageV,
            calibratedCurrentMa = calibratedCurrentMa,
            calibratedAvgCurrentMa = calibratedAvgCurrentMa,
            powerWatts = powerWatts,
            estimatedMinutesToFull = etaMinutes
        )
    }

    private fun readQuickProtocolSysfsHints(): Map<String, String> {
        val probeMap = mapOf(
            "voocchg_ing" to "/sys/class/power_supply/battery/voocchg_ing",
            "oplus_fastchg" to "/sys/class/oplus_chg/battery/fastchg_status",
            "mi_quick_charge" to "/sys/class/power_supply/usb/quick_charge_type",
            "usb_real_type" to "/sys/class/power_supply/usb/real_type",
            "usb_pd_active" to "/sys/class/power_supply/usb/pd_active"
        )
        val result = mutableMapOf<String, String>()
        for ((key, path) in probeMap) {
            readFileSafe(path)?.let { result[key] = it }
        }
        return result
    }

    private fun readFileSafe(path: String): String? {
        return runCatching {
            val file = File(path)
            if (file.exists() && file.canRead()) {
                file.readText().trim().takeIf { it.isNotEmpty() }
            } else {
                null
            }
        }.getOrNull()
    }

    private fun safeLongProperty(propertyId: Int): Long {
        val value = runCatching {
            batteryManager?.getLongProperty(propertyId) ?: 0L
        }.getOrDefault(0L)
        return if (value == Long.MIN_VALUE || value == Int.MIN_VALUE.toLong()) 0L else value
    }

    private fun normalizeCurrentToMa(raw: Long, unitMode: CurrentUnitMode): Float {
        if (raw == 0L) return 0f
        return when (unitMode) {
            CurrentUnitMode.MICRO_AMPS -> raw / 1000f
            CurrentUnitMode.MILLI_AMPS -> raw.toFloat()
            CurrentUnitMode.AUTO -> {
                if (abs(raw) > 20_000L) raw / 1000f else raw.toFloat()
            }
        }
    }

    private fun adjustSign(
        currentMa: Float,
        isCharging: Boolean,
        isPlugged: Boolean,
        invertManual: Boolean
    ): Float {
        if (currentMa == 0f) return 0f
        var adjusted = when {
            isCharging && currentMa < 0f -> -currentMa
            !isPlugged && currentMa > 0f -> -currentMa
            else -> currentMa
        }
        if (invertManual) {
            adjusted = -adjusted
        }
        return adjusted
    }

    private fun estimateTimeToFullMinutes(
        isCharging: Boolean,
        levelPct: Int,
        currentMa: Float,
        ratedCapacityMah: Int
    ): Int? {
        if (!isCharging || levelPct >= 100 || currentMa <= 80f) return null
        val remainingPct = (100 - levelPct) / 100f
        val remainingMah = ratedCapacityMah * remainingPct
        val hours = (remainingMah / currentMa) * 1.18f
        val minutes = (hours * 60f).roundToInt()
        return minutes.takeIf { it in 1..1440 }
    }

    /**
     * Probes standard + OEM-specific kernel sysfs nodes tailored to OPPO Find X8 Ultra (OPLUS/VOOC),
     * Xiaomi 13 (Mi Turbo/BMS), and Nut Pro 3 (Qualcomm PD/PPS) safely without Root.
     */
    fun probeSysfsNodes(): List<SysfsProbeItem> {
        val candidates = listOf(
            // OPPO Find X8 Ultra / OPLUS SuperVOOC nodes
            Triple("OPPO / SuperVOOC", "oplus_fastchg_status", "/sys/class/oplus_chg/battery/fastchg_status"),
            Triple("OPPO / SuperVOOC", "voocchg_ing", "/sys/class/power_supply/battery/voocchg_ing"),
            Triple("OPPO / SuperVOOC", "oplus_adapter_type", "/sys/class/oplus_chg/usb/otg_switch"),
            // Xiaomi 13 / HyperOS Mi Turbo nodes
            Triple("Xiaomi 13 / Mi Turbo", "quick_charge_type", "/sys/class/power_supply/usb/quick_charge_type"),
            Triple("Xiaomi 13 / Mi Turbo", "bms/cycle_count", "/sys/class/power_supply/bms/cycle_count"),
            Triple("Xiaomi 13 / Mi Turbo", "bms/charge_full", "/sys/class/power_supply/bms/charge_full"),
            // Qualcomm / Nut Pro 3 / USB PD & PPS nodes
            Triple("USB PD / PPS / 坚果", "usb/real_type", "/sys/class/power_supply/usb/real_type"),
            Triple("USB PD / PPS / 坚果", "usb/pd_active", "/sys/class/power_supply/usb/pd_active"),
            Triple("USB PD / PPS / 坚果", "usb/voltage_max", "/sys/class/power_supply/usb/voltage_max"),
            Triple("USB PD / PPS / 坚果", "usb/current_max", "/sys/class/power_supply/usb/current_max"),
            // Generic Android Battery nodes
            Triple("通用内核节点", "battery/charge_type", "/sys/class/power_supply/battery/charge_type"),
            Triple("通用内核节点", "battery/status", "/sys/class/power_supply/battery/status")
        )
        return candidates.map { (group, name, path) ->
            val value = readFileSafe(path)
            SysfsProbeItem(
                vendorGroup = group,
                nodeName = name,
                path = path,
                value = value,
                readable = value != null
            )
        }
    }

    @SuppressLint("PrivateApi")
    private fun readDesignCapacityViaPowerProfile(): Int? {
        return runCatching {
            val clazz = Class.forName("com.android.internal.os.PowerProfile")
            val instance = clazz.getConstructor(Context::class.java).newInstance(context)
            val method = clazz.getMethod("getBatteryCapacity")
            val capacity = (method.invoke(instance) as? Double)?.roundToInt()
            capacity?.takeIf { it in 1000..15000 }
        }.getOrNull()
    }
}
