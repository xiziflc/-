package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.db.ChargingSessionEntity
import com.example.data.db.TelemetrySampleEntity
import com.example.ui.components.CurveChannel
import com.example.ui.components.CurveDataPoint
import com.example.ui.components.CyberBadge
import com.example.ui.components.CyberOutlinedButton
import com.example.ui.components.CyberPanel
import com.example.ui.components.CyberPrimaryButton
import com.example.ui.components.CyberPrimaryButton
import com.example.ui.components.CyberSectionHeader
import com.example.ui.components.FullTelemetryCurveChart
import com.example.ui.theme.LocalCyberColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    sessions: List<ChargingSessionEntity>,
    ratedCapacityMah: Int,
    selectedSession: ChargingSessionEntity?,
    selectedSamples: List<TelemetrySampleEntity>,
    onSelectSession: (Long?) -> Unit,
    onDeleteSession: (Long) -> Unit,
    onExportSession: (ChargingSessionEntity, List<TelemetrySampleEntity>) -> Unit,
    onCopySessionReport: (ChargingSessionEntity, List<TelemetrySampleEntity>) -> Unit,
    onStartMonitorNow: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (selectedSession != null) {
        BackHandler { onSelectSession(null) }
        SessionDetailView(
            session = selectedSession,
            samples = selectedSamples,
            ratedCapacityMah = ratedCapacityMah,
            onBack = { onSelectSession(null) },
            onDelete = { onDeleteSession(selectedSession.id) },
            onExport = { onExportSession(selectedSession, selectedSamples) },
            onCopy = { onCopySessionReport(selectedSession, selectedSamples) },
            modifier = modifier
        )
        return
    }

    val cyber = LocalCyberColors.current

    val peakRecordW = remember(sessions) {
        sessions.maxOfOrNull { it.peakPowerWatts } ?: 0f
    }
    val estimatedHealthPct = remember(sessions, ratedCapacityMah) {
        val estimates = sessions.mapNotNull { it.estimatedFullCapacityMah }
        if (estimates.isEmpty() || ratedCapacityMah <= 0) {
            null
        } else {
            val avgEstimated = estimates.average().toFloat()
            ((avgEstimated / ratedCapacityMah) * 100f).coerceIn(40f, 105f)
        }
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "history_header") {
                Column {
                    Text(
                        text = "NETDIVE // 充能日志",
                        style = MaterialTheme.typography.headlineLarge,
                        color = cyber.lucyPearl
                    )
                    Text(
                        text = "本地 Room 数据库归档每次充电全流程 · 点开查看完整五维曲线与协议握手",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Aggregate Stats Ribbon
            item(key = "history_summary") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SummaryStatCard(
                        title = "已记录次数",
                        value = "${sessions.size}",
                        unit = "次",
                        accent = cyber.neonCyan,
                        modifier = Modifier.weight(1f)
                    )
                    SummaryStatCard(
                        title = "历史峰值功率",
                        value = String.format(Locale.US, "%.1f", peakRecordW),
                        unit = "W",
                        accent = cyber.lucyRose,
                        modifier = Modifier.weight(1f)
                    )
                    SummaryStatCard(
                        title = "估算健康度",
                        value = estimatedHealthPct?.let { String.format(Locale.US, "%.0f", it) } ?: "--",
                        unit = if (estimatedHealthPct != null) "%" else "待采样",
                        accent = cyber.signalEmerald,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (sessions.isEmpty()) {
                item(key = "empty_history") {
                    CyberPanel(
                        modifier = Modifier.fillMaxWidth(),
                        accentColor = cyber.lucyLavender
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(132.dp)
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.img_lucy_netdive_card),
                                    contentDescription = "Lucy 赛博空间月球视觉图",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(
                                                    Color.Transparent,
                                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
                                                )
                                            )
                                        )
                                )
                                Text(
                                    text = "ARCHIVE EMPTY // 尚无充能档案",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = cyber.lucyPearl,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(horizontal = 16.dp, vertical = 10.dp)
                                )
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "接入充电器并启动常驻监控后，系统每 5 秒（及功率跃变时）记录功率、电流、电压、温度与电量点位，支持完整回看 SuperVOOC / Mi Turbo / PPS / PD 充电降档曲线。",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                CyberPrimaryButton(
                                    text = "立即启动充能监控并记录曲线",
                                    onClick = onStartMonitorNow,
                                    accentColor = cyber.neonCyan,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("empty_start_monitor_button")
                                )
                            }
                        }
                    }
                }
            } else {
                item(key = "list_header") {
                    CyberSectionHeader(
                        indexTag = "// ARCHIVE",
                        title = "历史充电会话 (${sessions.size})",
                        accentColor = cyber.lucyLavender
                    )
                }

                items(sessions, key = { it.id }) { session ->
                    SessionCard(
                        session = session,
                        onClick = { onSelectSession(session.id) },
                        modifier = Modifier.testTag("session_card_${session.id}")
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun SessionCard(
    session: ChargingSessionEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cyber = LocalCyberColors.current
    val dateFormat = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }

    CyberPanel(
        modifier = modifier.fillMaxWidth(),
        accentColor = if (session.isCompleted) cyber.lucyLavender else cyber.signalEmerald,
        onClick = onClick
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Bolt,
                        contentDescription = null,
                        tint = cyber.acidYellow,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "${session.startLevelPct}% → ${session.endLevelPct}% (+${session.levelDeltaPct}%)",
                        style = MaterialTheme.typography.titleMedium,
                        color = cyber.lucyPearl,
                        fontWeight = FontWeight.Bold
                    )
                }
                CyberBadge(
                    text = if (session.isCompleted) dateFormat.format(Date(session.startTimeMs)) else "LIVE 采集中",
                    color = if (session.isCompleted) cyber.neonCyan else cyber.signalEmerald
                )
            }

            if (session.protocolLabel.isNotBlank()) {
                CyberBadge(
                    text = session.protocolLabel,
                    color = cyber.lucyRose
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "峰值 ${String.format(Locale.US, "%.1fW", session.peakPowerWatts)} · 均值 ${String.format(Locale.US, "%.1fW", session.avgPowerWatts)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = cyber.neonCyan
                )
                Text(
                    text = "最高温 ${String.format(Locale.US, "%.1f°C", session.peakTempC)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = cyber.monowireCrimson
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${session.plugTypeLabel} · 耗时 ${formatDuration(session.durationMs)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "充入 ≈ ${String.format(Locale.US, "%.0f mAh", session.mahAdded)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = cyber.signalEmerald
                )
            }
        }
    }
}

@Composable
private fun SessionDetailView(
    session: ChargingSessionEntity,
    samples: List<TelemetrySampleEntity>,
    ratedCapacityMah: Int,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cyber = LocalCyberColors.current
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }
    val chartSamples = remember(samples) {
        samples.map {
            CurveDataPoint(
                timestampMs = it.timestampMs,
                powerWatts = it.powerWatts,
                currentMa = it.currentMa,
                voltageV = it.voltageV,
                temperatureC = it.temperatureC,
                levelPct = it.levelPct
            )
        }
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
            item(key = "detail_topbar") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("session_detail_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回充电日志",
                                tint = cyber.neonCyan
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Column {
                            Text(
                                text = "ARCHIVE #${session.id} · 完整充电曲线",
                                style = MaterialTheme.typography.titleLarge,
                                color = cyber.lucyPearl
                            )
                            Text(
                                text = dateFormat.format(Date(session.startTimeMs)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.testTag("session_detail_delete_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = "删除此记录",
                            tint = cyber.alertCrimson
                        )
                    }
                }
            }

            item(key = "detail_chart") {
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
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "全程五维遥测曲线 (${samples.size} 点)",
                                style = MaterialTheme.typography.titleSmall,
                                color = cyber.neonCyan
                            )
                            if (session.protocolLabel.isNotBlank()) {
                                CyberBadge(text = session.protocolLabel, color = cyber.lucyRose)
                            }
                        }

                        FullTelemetryCurveChart(
                            samples = chartSamples,
                            chartHeight = 220.dp,
                            initialChannels = setOf(
                                CurveChannel.POWER,
                                CurveChannel.CURRENT,
                                CurveChannel.TEMP,
                                CurveChannel.SOC
                            ),
                            emptyHint = "采样点不足 2 个，无法绘制完整曲线"
                        )
                    }
                }
            }

            item(key = "detail_metrics") {
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
                        DetailRow("电量区间", "${session.startLevelPct}% → ${session.endLevelPct}% (+${session.levelDeltaPct}%)")
                        if (session.protocolLabel.isNotBlank()) {
                            DetailRow("握手协议", session.protocolLabel)
                        }
                        DetailRow("供电类型", session.plugTypeLabel)
                        DetailRow("总时长", formatDuration(session.durationMs))
                        DetailRow(
                            "峰值 / 平均功率",
                            "${String.format(Locale.US, "%.1fW", session.peakPowerWatts)} / ${String.format(Locale.US, "%.1fW", session.avgPowerWatts)}"
                        )
                        DetailRow(
                            "最高 / 平均温度",
                            "${String.format(Locale.US, "%.1f°C", session.peakTempC)} / ${String.format(Locale.US, "%.1f°C", session.avgTempC)}"
                        )
                        DetailRow("累计充入电量", "${String.format(Locale.US, "%.0f", session.mahAdded)} mAh")
                        session.estimatedFullCapacityMah?.let { estCap ->
                            val health = ((estCap / ratedCapacityMah) * 100f).coerceIn(40f, 105f)
                            DetailRow(
                                "本次推算满电容量",
                                "${String.format(Locale.US, "%.0f", estCap)} mAh (≈ ${String.format(Locale.US, "%.0f%%", health)})"
                            )
                        }
                    }
                }
            }

            item(key = "detail_export_actions") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CyberPrimaryButton(
                        text = "导出诊断日志 (.txt)",
                        onClick = onExport,
                        accentColor = cyber.neonCyan,
                        modifier = Modifier.weight(1f)
                    )
                    CyberOutlinedButton(
                        text = "复制报告",
                        onClick = onCopy,
                        accentColor = cyber.lucyLavender,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item(key = "detail_back_btn") {
                CyberOutlinedButton(
                    text = "返回充能日志列表",
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun SummaryStatCard(
    title: String,
    value: String,
    unit: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    CyberPanel(
        modifier = modifier,
        accentColor = accent,
        cutSize = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = unit,
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
                    modifier = Modifier.padding(bottom = 3.dp)
                )
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSec = (durationMs / 1000L).coerceAtLeast(0L)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return when {
        h > 0 -> "${h}h ${m}m"
        m > 0 -> "${m}m ${s}s"
        else -> "${s}s"
    }
}
