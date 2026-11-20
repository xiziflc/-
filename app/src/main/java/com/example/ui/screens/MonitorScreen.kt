package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DeviceThermostat
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.BatteryTelemetry
import com.example.data.CalibrationSettings
import com.example.data.DeviceProfileId
import com.example.data.ProtocolKind
import com.example.ui.components.CurveDataPoint
import com.example.ui.components.CyberBadge
import com.example.ui.components.CyberChipToggle
import com.example.ui.components.CyberPanel
import com.example.ui.components.CyberPrimaryButton
import com.example.ui.components.CyberSectionHeader
import com.example.ui.components.FullTelemetryCurveChart
import com.example.ui.components.LucyMoonHeroBanner
import com.example.ui.components.PowerReactorGauge
import com.example.ui.theme.LocalCyberColors
import java.util.Locale

private enum class CurveWindowMode(val label: String) {
    FULL_SESSION("完整全程曲线"),
    LIVE_60S("实时 60s 波形")
}

@Composable
fun MonitorScreen(
    telemetry: BatteryTelemetry,
    settings: CalibrationSettings,
    liveWaveform: List<CurveDataPoint>,
    fullSessionCurve: List<CurveDataPoint>,
    isServiceRunning: Boolean,
    isSentinelArmed: Boolean,
    onToggleService: () -> Unit,
    onSelectDeviceProfile: (DeviceProfileId) -> Unit,
    onQuickVoltageMultiplier: (Float) -> Unit,
    onQuickInvertSign: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val cyber = LocalCyberColors.current
    var curveMode by rememberSaveable { mutableStateOf(CurveWindowMode.FULL_SESSION) }

    val displayedCurve = when (curveMode) {
        CurveWindowMode.FULL_SESSION -> if (fullSessionCurve.size >= 2) fullSessionCurve else liveWaveform
        CurveWindowMode.LIVE_60S -> liveWaveform
    }

    val tempColor = when {
        telemetry.temperatureC >= settings.tempAlertThresholdC -> cyber.alertCrimson
        telemetry.temperatureC >= 38f -> cyber.signalAmber
        else -> cyber.signalEmerald
    }

    val serviceBadgeText = when {
        isServiceRunning -> "ICE 监控中"
        isSentinelArmed -> "哨兵待命"
        else -> "前台读数"
    }
    val serviceBadgeColor = when {
        isServiceRunning -> cyber.signalEmerald
        isSentinelArmed -> cyber.acidYellow
        else -> cyber.textMuted
    }

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
            // 1. Lucy Moonlit Netrunner Hero Banner
            item(key = "lucy_hero_banner") {
                LucyMoonHeroBanner(
                    deviceTitle = "${telemetry.activeProfile.displayName} · ${telemetry.activeProfile.cellArchitecture}",
                    protocolBadge = telemetry.detectedProtocol.kind.shortLabel,
                    levelPct = telemetry.levelPct,
                    statusBadge = serviceBadgeText,
                    statusColor = serviceBadgeColor
                )
            }

            // 2. Hero Reactor Power Gauge Panel
            item(key = "reactor_hero") {
                CyberPanel(
                    modifier = Modifier.fillMaxWidth(),
                    accentColor = if (telemetry.isPlugged) cyber.neonCyan else cyber.lucyRose
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        PowerReactorGauge(
                            telemetry = telemetry,
                            tempAlertThresholdC = settings.tempAlertThresholdC
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        CyberPrimaryButton(
                            text = if (isServiceRunning) "停止通知栏监控并归档曲线" else "启动通知栏常驻监控（记录完整曲线）",
                            onClick = onToggleService,
                            accentColor = if (isServiceRunning) cyber.monowireCrimson else cyber.neonCyan,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("toggle_monitor_service_button")
                        )
                    }
                }
            }

            // 3. Protocol Handshake Matrix (SuperVOOC / Mi Turbo / PPS / PD) + Quick 3-Phone Switcher
            item(key = "protocol_matrix") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CyberSectionHeader(
                        indexTag = "// 01",
                        title = "快充协议握手矩阵",
                        accentColor = cyber.lucyLavender,
                        trailing = {
                            CyberBadge(
                                text = telemetry.detectedProtocol.source.label,
                                color = cyber.lucyLavender
                            )
                        }
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
                            // Active Protocol Banner
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Usb,
                                        contentDescription = "当前协议",
                                        tint = cyber.neonCyan,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Column {
                                        Text(
                                            text = telemetry.detectedProtocol.label,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = cyber.lucyPearl,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = telemetry.detectedProtocol.detail,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            // 4 Core Protocol Channels: SuperVOOC, Mi Turbo, USB PPS, USB PD
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val activeKind = telemetry.detectedProtocol.kind
                                val lanes = listOf(
                                    Triple(ProtocolKind.SUPERVOOC, "SuperVOOC", "100W 私有"),
                                    Triple(ProtocolKind.MI_TURBO, "Mi Turbo", "67W 私有"),
                                    Triple(ProtocolKind.PPS, "USB PPS", "调压快充"),
                                    Triple(ProtocolKind.PD, "USB PD", "通用快充")
                                )
                                lanes.forEach { (kind, title, sub) ->
                                    val isActive = activeKind == kind
                                    val isSupportedByProfile = telemetry.activeProfile.protocolSpecs.any { it.kind == kind }
                                    val accent = when (kind) {
                                        ProtocolKind.SUPERVOOC -> cyber.signalEmerald
                                        ProtocolKind.MI_TURBO -> cyber.acidYellow
                                        ProtocolKind.PPS -> cyber.neonCyan
                                        else -> cyber.lucyLavender
                                    }
                                    ProtocolLanePill(
                                        title = title,
                                        subtitle = sub,
                                        isActive = isActive,
                                        isSupported = isSupportedByProfile,
                                        accent = accent,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            // Quick 3-Phone Profile Switcher (Find X8 Ultra / Xiaomi 13 / Nut Pro 3)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(
                                    DeviceProfileId.OPPO_FIND_X8_ULTRA to "Find X8U",
                                    DeviceProfileId.XIAOMI_13 to "小米 13",
                                    DeviceProfileId.NUT_PRO_3 to "坚果 Pro 3"
                                ).forEach { (id, label) ->
                                    val isSelected = telemetry.activeProfile.id == id
                                    CyberChipToggle(
                                        label = label,
                                        selected = isSelected,
                                        onClick = { onSelectDeviceProfile(id) },
                                        accentColor = when (id) {
                                            DeviceProfileId.OPPO_FIND_X8_ULTRA -> cyber.signalEmerald
                                            DeviceProfileId.XIAOMI_13 -> cyber.acidYellow
                                            else -> cyber.lucyRose
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("quick_profile_${id.name}")
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 4. Full Multi-Channel Telemetry Curves (Complete Session vs Live 60s)
            item(key = "full_curve_panel") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CyberSectionHeader(
                        indexTag = "// 02",
                        title = "完整充电曲线（五维叠加）",
                        accentColor = cyber.neonCyan,
                        trailing = {
                            Text(
                                text = "${displayedCurve.size} 采样点",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )

                    CyberPanel(
                        modifier = Modifier.fillMaxWidth(),
                        accentColor = cyber.neonCyan
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CurveWindowMode.entries.forEach { mode ->
                                    CyberChipToggle(
                                        label = mode.label,
                                        selected = curveMode == mode,
                                        onClick = { curveMode = mode },
                                        accentColor = if (mode == CurveWindowMode.FULL_SESSION) cyber.lucyRose else cyber.neonCyan,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("curve_window_${mode.name}")
                                    )
                                }
                            }

                            FullTelemetryCurveChart(
                                samples = displayedCurve,
                                chartHeight = 200.dp,
                                emptyHint = "采样中（2秒后生成连续曲线；开启前台监控可在息屏下记录完整 0%→100% 曲线）"
                            )
                        }
                    }
                }
            }

            // 5. 2x2 Real-Time Telemetry Metric Grid
            item(key = "telemetry_grid") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CyberSectionHeader(
                        indexTag = "// 03",
                        title = "实时传感器读数",
                        trailing = {
                            Text(
                                text = telemetry.healthLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = cyber.signalEmerald
                            )
                        }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        TelemetryMetricCard(
                            label = "瞬时电流",
                            value = String.format(Locale.US, "%+.0f", telemetry.calibratedCurrentMa),
                            unit = "mA",
                            subtext = "均值 ${String.format(Locale.US, "%.0f mA", telemetry.calibratedAvgCurrentMa)}",
                            icon = Icons.Filled.ElectricBolt,
                            accent = if (telemetry.calibratedCurrentMa >= 0f) cyber.neonCyan else cyber.lucyRose,
                            modifier = Modifier.weight(1f)
                        )
                        TelemetryMetricCard(
                            label = "电池电压",
                            value = String.format(Locale.US, "%.3f", telemetry.calibratedVoltageV),
                            unit = "V",
                            subtext = "原始 ${telemetry.rawVoltageMv} mV (×${String.format(Locale.US, "%.0f", settings.voltageMultiplier)})",
                            icon = Icons.Filled.Bolt,
                            accent = cyber.lucyLavender,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        TelemetryMetricCard(
                            label = "电池温度",
                            value = String.format(Locale.US, "%.1f", telemetry.temperatureC),
                            unit = "°C",
                            subtext = "告警阈值 ${String.format(Locale.US, "%.1f°C", settings.tempAlertThresholdC)}",
                            icon = Icons.Filled.DeviceThermostat,
                            accent = tempColor,
                            modifier = Modifier.weight(1f)
                        )
                        val capText = telemetry.remainingMah?.let { "$it" } ?: "--"
                        val cycleText = telemetry.cycleCount?.let { "循环 $it 次" }
                            ?: "设计 ${settings.ratedCapacityMah} mAh"
                        TelemetryMetricCard(
                            label = "库仑计余量",
                            value = capText,
                            unit = "mAh",
                            subtext = cycleText,
                            icon = Icons.Filled.BatteryChargingFull,
                            accent = cyber.signalEmerald,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // 6. Quick Dual-Cell Calibration Strip
            item(key = "quick_calibration") {
                CyberPanel(
                    modifier = Modifier.fillMaxWidth(),
                    accentColor = cyber.acidYellow,
                    cutSize = 10.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "双电芯 / 电流极性微调",
                                style = MaterialTheme.typography.titleSmall,
                                color = cyber.acidYellow
                            )
                            Text(
                                text = "RAW ${telemetry.rawCurrentNow}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CyberChipToggle(
                                label = "单电芯 ×1",
                                selected = settings.voltageMultiplier < 1.5f,
                                onClick = { onQuickVoltageMultiplier(1.0f) },
                                accentColor = cyber.neonCyan,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("quick_single_cell_button")
                            )
                            CyberChipToggle(
                                label = "双电芯 ×2",
                                selected = settings.voltageMultiplier >= 1.5f,
                                onClick = { onQuickVoltageMultiplier(2.0f) },
                                accentColor = cyber.acidYellow,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("quick_dual_cell_button")
                            )
                            CyberChipToggle(
                                label = if (settings.invertCurrentSign) "已反转 ±" else "反转 ±",
                                selected = settings.invertCurrentSign,
                                onClick = { onQuickInvertSign(!settings.invertCurrentSign) },
                                accentColor = cyber.lucyRose,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("quick_invert_sign_button")
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun ProtocolLanePill(
    title: String,
    subtitle: String,
    isActive: Boolean,
    isSupported: Boolean,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp)
    val bgColor = when {
        isActive -> accent.copy(alpha = 0.24f)
        isSupported -> MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
        else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.35f)
    }
    val borderColor = when {
        isActive -> accent
        isSupported -> accent.copy(alpha = 0.45f)
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    }

    Surface(
        modifier = modifier,
        shape = shape,
        color = bgColor,
        border = BorderStroke(if (isActive) 1.5.dp else 1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = when {
                    isActive -> accent
                    isSupported -> MaterialTheme.colorScheme.onBackground
                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                },
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1
            )
            Text(
                text = if (isActive) "LOCKED" else subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = if (isActive) accent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun TelemetryMetricCard(
    label: String,
    value: String,
    unit: String,
    subtext: String,
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier
) {
    CyberPanel(
        modifier = modifier,
        accentColor = accent,
        cutSize = 10.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = accent,
                    modifier = Modifier.size(16.dp)
                )
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = unit,
                    style = MaterialTheme.typography.labelMedium,
                    color = accent,
                    modifier = Modifier.padding(bottom = 3.dp)
                )
            }
            Text(
                text = subtext,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}
