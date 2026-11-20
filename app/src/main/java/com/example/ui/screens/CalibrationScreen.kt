package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.BatteryTelemetry
import com.example.data.CalibrationSettings
import com.example.data.CurrentUnitMode
import com.example.data.DeviceProfileCatalog
import com.example.data.DeviceProfileId
import com.example.ui.components.CyberBadge
import com.example.ui.components.CyberChipToggle
import com.example.ui.components.CyberOutlinedButton
import com.example.ui.components.CyberPanel
import com.example.ui.components.CyberSectionHeader
import com.example.ui.theme.LocalCyberColors
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CalibrationScreen(
    telemetry: BatteryTelemetry,
    settings: CalibrationSettings,
    onSelectDeviceProfile: (DeviceProfileId) -> Unit,
    onVoltageMultiplierChange: (Float) -> Unit,
    onCurrentMultiplierChange: (Float) -> Unit,
    onInvertSignChange: (Boolean) -> Unit,
    onUnitModeChange: (CurrentUnitMode) -> Unit,
    onRatedCapacityChange: (Int) -> Unit,
    onEnableAlertsChange: (Boolean) -> Unit,
    onTempThresholdChange: (Float) -> Unit,
    onTargetPctChange: (Int) -> Unit,
    onResetCalibration: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cyber = LocalCyberColors.current
    val activeProfile = telemetry.activeProfile

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "calib_header") {
                Column {
                    Text(
                        text = "RIG CALIBRATION // 机型校准",
                        style = MaterialTheme.typography.headlineLarge,
                        color = cyber.lucyPearl
                    )
                    Text(
                        text = "专属内置 OPPO Find X8 Ultra、小米 13、坚果 Pro 3 协议与电芯参数",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Section 0: Dedicated 3-Phone Hardware Presets
            item(key = "device_profiles_section") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CyberSectionHeader(
                        indexTag = "// 00",
                        title = "专属机型与协议预设",
                        accentColor = cyber.lucyLavender
                    )

                    CyberPanel(
                        modifier = Modifier.fillMaxWidth(),
                        accentColor = cyber.lucyLavender
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(
                                    DeviceProfileId.AUTO to "自动识别",
                                    DeviceProfileId.OPPO_FIND_X8_ULTRA to "Find X8U",
                                    DeviceProfileId.XIAOMI_13 to "小米 13",
                                    DeviceProfileId.NUT_PRO_3 to "坚果 Pro 3"
                                ).forEach { (id, label) ->
                                    val selected = settings.deviceProfileId == id
                                    CyberChipToggle(
                                        label = label,
                                        selected = selected,
                                        onClick = { onSelectDeviceProfile(id) },
                                        accentColor = when (id) {
                                            DeviceProfileId.OPPO_FIND_X8_ULTRA -> cyber.signalEmerald
                                            DeviceProfileId.XIAOMI_13 -> cyber.acidYellow
                                            DeviceProfileId.NUT_PRO_3 -> cyber.lucyRose
                                            DeviceProfileId.AUTO -> cyber.neonCyan
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("calib_profile_${id.name}")
                                    )
                                }
                            }

                            Text(
                                text = "${activeProfile.displayName} · ${activeProfile.cellArchitecture}",
                                style = MaterialTheme.typography.titleSmall,
                                color = cyber.neonCyan
                            )

                            Text(
                                text = activeProfile.calibrationTip,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                activeProfile.protocolSpecs.forEach { spec ->
                                    val badgeColor = if (spec.kind.isProprietary) cyber.acidYellow else cyber.neonCyan
                                    CyberBadge(
                                        text = "${spec.kind.shortLabel} ${spec.maxWatts}W",
                                        color = badgeColor
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Live Calibration Comparison Card
            item(key = "live_preview") {
                CyberPanel(
                    modifier = Modifier.fillMaxWidth(),
                    accentColor = cyber.acidYellow
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "实时校准公式预览 (P = V × I)",
                            style = MaterialTheme.typography.titleSmall,
                            color = cyber.acidYellow
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "原始读数",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${telemetry.rawVoltageMv}mV · RAW ${telemetry.rawCurrentNow}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "校准输出",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${String.format(Locale.US, "%.2fV", telemetry.calibratedVoltageV)} × ${String.format(Locale.US, "%.0fmA", telemetry.calibratedCurrentMa)} = ${String.format(Locale.US, "%.1fW", telemetry.powerWatts)}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = cyber.neonCyan,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // Section 1: Dual-Cell & Current Calibration
            item(key = "cell_calib_section") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CyberSectionHeader(
                        indexTag = "// 01",
                        title = "电芯倍率与电流方向"
                    )

                    CyberPanel(
                        modifier = Modifier.fillMaxWidth(),
                        accentColor = cyber.neonCyan
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "电压倍率（Find X8 Ultra 双电芯选 ×2.0；小米 13 / 坚果 Pro 3 选 ×1.0）",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CyberChipToggle(
                                        label = "单电芯 ×1.0",
                                        selected = settings.voltageMultiplier < 1.5f,
                                        onClick = { onVoltageMultiplierChange(1.0f) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("calib_volt_1x")
                                    )
                                    CyberChipToggle(
                                        label = "双电芯 ×2.0",
                                        selected = settings.voltageMultiplier >= 1.5f,
                                        onClick = { onVoltageMultiplierChange(2.0f) },
                                        accentColor = cyber.acidYellow,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("calib_volt_2x")
                                    )
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "电流倍率",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf(0.5f to "×0.5", 1.0f to "×1.0", 2.0f to "×2.0").forEach { (mult, label) ->
                                        CyberChipToggle(
                                            label = label,
                                            selected = abs(settings.currentMultiplier - mult) < 0.05f,
                                            onClick = { onCurrentMultiplierChange(mult) },
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("calib_curr_${label}")
                                        )
                                    }
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "电流单位解析",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CurrentUnitMode.entries.forEach { mode ->
                                        CyberChipToggle(
                                            label = mode.label,
                                            selected = settings.currentUnitMode == mode,
                                            onClick = { onUnitModeChange(mode) },
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("calib_unit_${mode.name}")
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "强制反转电流正负号",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                    Text(
                                        text = "默认自动将充电修正为正、放电修正为负；异常机型可手动反转。",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = settings.invertCurrentSign,
                                    onCheckedChange = onInvertSignChange,
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                        checkedTrackColor = cyber.lucyRose
                                    ),
                                    modifier = Modifier.testTag("calib_invert_sign_switch")
                                )
                            }

                            CyberOutlinedButton(
                                text = "恢复自动识别与默认参数",
                                onClick = onResetCalibration,
                                accentColor = cyber.neonCyan,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("calib_reset_button")
                            )
                        }
                    }
                }
            }

            // Section 2: Rated Battery Capacity (quick presets tailored to 4000 / 4500 / 5000 / 6100 mAh)
            item(key = "capacity_section") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CyberSectionHeader(
                        indexTag = "// 02",
                        title = "电池设计容量 (${settings.ratedCapacityMah} mAh)",
                        accentColor = cyber.signalEmerald
                    )

                    CyberPanel(
                        modifier = Modifier.fillMaxWidth(),
                        accentColor = cyber.signalEmerald
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            val detectedHint = telemetry.detectedDesignCapacityMah?.let {
                                "系统 PowerProfile 识别参考值：$it mAh（坚果 4000 / 小米13 4500 / X8U 6100）"
                            } ?: "快捷预设覆盖坚果 Pro 3 (4000) / 小米 13 (4500) / Find X8 Ultra (6100)"
                            Text(
                                text = detectedHint,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Slider(
                                value = settings.ratedCapacityMah.toFloat(),
                                onValueChange = { raw ->
                                    val snapped = ((raw / 100f).roundToInt() * 100).coerceIn(2500, 8000)
                                    onRatedCapacityChange(snapped)
                                },
                                valueRange = 2500f..8000f,
                                colors = SliderDefaults.colors(
                                    thumbColor = cyber.signalEmerald,
                                    activeTrackColor = cyber.signalEmerald
                                ),
                                modifier = Modifier.testTag("calib_capacity_slider")
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(4000, 4500, 5000, 6100).forEach { preset ->
                                    CyberChipToggle(
                                        label = "$preset",
                                        selected = settings.ratedCapacityMah == preset,
                                        onClick = { onRatedCapacityChange(preset) },
                                        accentColor = cyber.signalEmerald,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Section 3: Thermal & Target Charge Alerts
            item(key = "alerts_section") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CyberSectionHeader(
                        indexTag = "// 03",
                        title = "电池安全告警",
                        accentColor = cyber.monowireCrimson
                    )

                    CyberPanel(
                        modifier = Modifier.fillMaxWidth(),
                        accentColor = cyber.monowireCrimson
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "开启充电高温与目标电量提醒",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                    Text(
                                        text = "监控服务运行期间自动触发高优先级通知提醒",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = settings.enableAlerts,
                                    onCheckedChange = onEnableAlertsChange,
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                        checkedTrackColor = cyber.monowireCrimson
                                    ),
                                    modifier = Modifier.testTag("calib_alerts_switch")
                                )
                            }

                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "高温告警阈值",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                    Text(
                                        text = String.format(Locale.US, "%.1f °C", settings.tempAlertThresholdC),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = cyber.monowireCrimson
                                    )
                                }
                                Slider(
                                    value = settings.tempAlertThresholdC,
                                    onValueChange = { onTempThresholdChange((it * 2).roundToInt() / 2f) },
                                    valueRange = 36f..48f,
                                    enabled = settings.enableAlerts,
                                    colors = SliderDefaults.colors(
                                        thumbColor = cyber.monowireCrimson,
                                        activeTrackColor = cyber.monowireCrimson
                                    ),
                                    modifier = Modifier.testTag("calib_temp_slider")
                                )
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "目标养护电量提醒",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                    Text(
                                        text = "${settings.targetChargeAlertPct}%",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = cyber.acidYellow
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf(80, 85, 90, 95, 100).forEach { pct ->
                                        CyberChipToggle(
                                            label = "$pct%",
                                            selected = settings.targetChargeAlertPct == pct,
                                            onClick = { onTargetPctChange(pct) },
                                            accentColor = cyber.acidYellow,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }
        }
    }
}
