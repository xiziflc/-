package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class CurrentUnitMode(val label: String) {
    AUTO("自动识别"),
    MICRO_AMPS("微安 (µA)"),
    MILLI_AMPS("毫安 (mA)")
}

data class CalibrationSettings(
    val deviceProfileId: DeviceProfileId = DeviceProfileId.AUTO,
    val voltageMultiplier: Float = 1.0f,
    val currentMultiplier: Float = 1.0f,
    val invertCurrentSign: Boolean = false,
    val currentUnitMode: CurrentUnitMode = CurrentUnitMode.AUTO,
    val ratedCapacityMah: Int = 5000,
    val autoStartOnCharge: Boolean = true,
    val enableAlerts: Boolean = true,
    val tempAlertThresholdC: Float = 42.0f,
    val targetChargeAlertPct: Int = 90
)

private val Context.voltPulseDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "voltpulse_settings"
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val DEVICE_PROFILE_ID = stringPreferencesKey("device_profile_id")
        val VOLTAGE_MULTIPLIER = floatPreferencesKey("voltage_multiplier")
        val CURRENT_MULTIPLIER = floatPreferencesKey("current_multiplier")
        val INVERT_CURRENT_SIGN = booleanPreferencesKey("invert_current_sign")
        val CURRENT_UNIT_MODE = stringPreferencesKey("current_unit_mode")
        val RATED_CAPACITY_MAH = intPreferencesKey("rated_capacity_mah")
        val AUTO_START_ON_CHARGE = booleanPreferencesKey("auto_start_on_charge")
        val ENABLE_ALERTS = booleanPreferencesKey("enable_alerts")
        val TEMP_ALERT_THRESHOLD = floatPreferencesKey("temp_alert_threshold")
        val TARGET_CHARGE_ALERT_PCT = intPreferencesKey("target_charge_alert_pct")
    }

    val settingsFlow: Flow<CalibrationSettings> = context.voltPulseDataStore.data.map { prefs ->
        val profileId = prefs[Keys.DEVICE_PROFILE_ID]?.let { name ->
            runCatching { DeviceProfileId.valueOf(name) }.getOrDefault(DeviceProfileId.AUTO)
        } ?: DeviceProfileId.AUTO

        val resolvedAuto = DeviceProfileCatalog.resolveProfile(profileId)
        val unitMode = prefs[Keys.CURRENT_UNIT_MODE]?.let { name ->
            runCatching { CurrentUnitMode.valueOf(name) }.getOrDefault(CurrentUnitMode.AUTO)
        } ?: CurrentUnitMode.AUTO

        CalibrationSettings(
            deviceProfileId = profileId,
            voltageMultiplier = prefs[Keys.VOLTAGE_MULTIPLIER] ?: resolvedAuto.defaultVoltageMultiplier,
            currentMultiplier = prefs[Keys.CURRENT_MULTIPLIER] ?: resolvedAuto.defaultCurrentMultiplier,
            invertCurrentSign = prefs[Keys.INVERT_CURRENT_SIGN] ?: false,
            currentUnitMode = unitMode,
            ratedCapacityMah = prefs[Keys.RATED_CAPACITY_MAH] ?: resolvedAuto.defaultCapacityMah,
            autoStartOnCharge = prefs[Keys.AUTO_START_ON_CHARGE] ?: true,
            enableAlerts = prefs[Keys.ENABLE_ALERTS] ?: true,
            tempAlertThresholdC = prefs[Keys.TEMP_ALERT_THRESHOLD] ?: 42.0f,
            targetChargeAlertPct = prefs[Keys.TARGET_CHARGE_ALERT_PCT] ?: 90
        )
    }

    suspend fun getSnapshot(): CalibrationSettings = settingsFlow.first()

    /**
     * Applies a device hardware profile (OPPO Find X8 Ultra, Xiaomi 13, Nut Pro 3, or AUTO)
     * and sets its tuned dual-cell/single-cell multipliers and battery capacity immediately.
     */
    suspend fun applyDeviceProfile(profileId: DeviceProfileId) {
        val profile = DeviceProfileCatalog.resolveProfile(profileId)
        context.voltPulseDataStore.edit { prefs ->
            prefs[Keys.DEVICE_PROFILE_ID] = profileId.name
            prefs[Keys.VOLTAGE_MULTIPLIER] = profile.defaultVoltageMultiplier
            prefs[Keys.CURRENT_MULTIPLIER] = profile.defaultCurrentMultiplier
            prefs[Keys.RATED_CAPACITY_MAH] = profile.defaultCapacityMah
            prefs[Keys.INVERT_CURRENT_SIGN] = false
            prefs[Keys.CURRENT_UNIT_MODE] = CurrentUnitMode.AUTO.name
        }
    }

    suspend fun setVoltageMultiplier(value: Float) {
        context.voltPulseDataStore.edit { it[Keys.VOLTAGE_MULTIPLIER] = value }
    }

    suspend fun setCurrentMultiplier(value: Float) {
        context.voltPulseDataStore.edit { it[Keys.CURRENT_MULTIPLIER] = value }
    }

    suspend fun setInvertCurrentSign(invert: Boolean) {
        context.voltPulseDataStore.edit { it[Keys.INVERT_CURRENT_SIGN] = invert }
    }

    suspend fun setCurrentUnitMode(mode: CurrentUnitMode) {
        context.voltPulseDataStore.edit { it[Keys.CURRENT_UNIT_MODE] = mode.name }
    }

    suspend fun setRatedCapacityMah(capacityMah: Int) {
        context.voltPulseDataStore.edit { it[Keys.RATED_CAPACITY_MAH] = capacityMah.coerceIn(1000, 15000) }
    }

    suspend fun setAutoStartOnCharge(enabled: Boolean) {
        context.voltPulseDataStore.edit { it[Keys.AUTO_START_ON_CHARGE] = enabled }
    }

    suspend fun setEnableAlerts(enabled: Boolean) {
        context.voltPulseDataStore.edit { it[Keys.ENABLE_ALERTS] = enabled }
    }

    suspend fun setTempAlertThresholdC(thresholdC: Float) {
        context.voltPulseDataStore.edit { it[Keys.TEMP_ALERT_THRESHOLD] = thresholdC.coerceIn(35f, 50f) }
    }

    suspend fun setTargetChargeAlertPct(pct: Int) {
        context.voltPulseDataStore.edit { it[Keys.TARGET_CHARGE_ALERT_PCT] = pct.coerceIn(50, 100) }
    }

    suspend fun resetCalibration() {
        applyDeviceProfile(DeviceProfileId.AUTO)
    }
}
