package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.LocalCyberColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

data class CurveDataPoint(
    val timestampMs: Long,
    val powerWatts: Float,
    val currentMa: Float,
    val voltageV: Float,
    val temperatureC: Float,
    val levelPct: Int
)

enum class CurveChannel(val label: String, val unit: String) {
    POWER("功率", "W"),
    CURRENT("电流", "mA"),
    VOLTAGE("电压", "V"),
    TEMP("温度", "°C"),
    SOC("电量", "%")
}

/**
 * Full Multi-Channel Interactive Telemetry Curve Chart:
 * Plots Power (W), Current (mA), Voltage (V), Temperature (°C), and Battery SOC (%)
 * with toggleable channel overlays, min/avg/peak strip, X-axis timestamps, and
 * touch-to-scrub Lucy Monowire crosshair inspection.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FullTelemetryCurveChart(
    samples: List<CurveDataPoint>,
    modifier: Modifier = Modifier,
    chartHeight: Dp = 196.dp,
    initialChannels: Set<CurveChannel> = setOf(CurveChannel.POWER, CurveChannel.TEMP, CurveChannel.SOC),
    emptyHint: String = "正在采集完整多维充电曲线..."
) {
    val cyber = LocalCyberColors.current
    var activeChannels by remember { mutableStateOf(initialChannels) }
    var inspectedIndex by remember(samples.size) { mutableStateOf<Int?>(null) }

    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    fun channelColor(ch: CurveChannel): Color = when (ch) {
        CurveChannel.POWER -> cyber.neonCyan
        CurveChannel.CURRENT -> cyber.acidYellow
        CurveChannel.VOLTAGE -> cyber.lucyLavender
        CurveChannel.TEMP -> cyber.monowireCrimson
        CurveChannel.SOC -> cyber.signalEmerald
    }

    val safeInspectedIdx = inspectedIndex?.coerceIn(0, (samples.size - 1).coerceAtLeast(0))
    val inspectedPoint = safeInspectedIdx?.let { samples.getOrNull(it) } ?: samples.lastOrNull()

    // Compute normalized ranges for each metric
    val maxPower = max(10f, (samples.maxOfOrNull { it.powerWatts } ?: 10f) * 1.15f)
    val maxCurrent = max(1000f, (samples.maxOfOrNull { abs(it.currentMa) } ?: 1000f) * 1.15f)
    val minVolt = ((samples.minOfOrNull { it.voltageV } ?: 3.5f) - 0.3f).coerceAtLeast(2.5f)
    val maxVolt = max(minVolt + 1.0f, (samples.maxOfOrNull { it.voltageV } ?: 5.0f) + 0.3f)
    val minTemp = ((samples.minOfOrNull { it.temperatureC } ?: 25f) - 3f).coerceAtLeast(15f)
    val maxTemp = max(minTemp + 10f, (samples.maxOfOrNull { it.temperatureC } ?: 45f) + 3f)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Channel Toggle Chips
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            CurveChannel.entries.forEach { channel ->
                val enabled = channel in activeChannels
                val color = channelColor(channel)
                CyberChipToggle(
                    label = "${channel.label} ${channel.unit}",
                    selected = enabled,
                    onClick = {
                        activeChannels = if (enabled && activeChannels.size > 1) {
                            activeChannels - channel
                        } else {
                            activeChannels + channel
                        }
                    },
                    accentColor = color,
                    modifier = Modifier.testTag("curve_channel_${channel.name}")
                )
            }
        }

        // Crosshair Inspection Readout Strip
        if (inspectedPoint != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.78f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (safeInspectedIdx != null) {
                        "光标 ${timeFormat.format(Date(inspectedPoint.timestampMs))}"
                    } else {
                        "最新 ${timeFormat.format(Date(inspectedPoint.timestampMs))}"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = cyber.lucyPearl
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (CurveChannel.POWER in activeChannels) {
                        Text(
                            text = String.format(Locale.US, "%.1fW", inspectedPoint.powerWatts),
                            style = MaterialTheme.typography.labelSmall,
                            color = cyber.neonCyan,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (CurveChannel.CURRENT in activeChannels) {
                        Text(
                            text = String.format(Locale.US, "%.0fmA", inspectedPoint.currentMa),
                            style = MaterialTheme.typography.labelSmall,
                            color = cyber.acidYellow,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (CurveChannel.VOLTAGE in activeChannels) {
                        Text(
                            text = String.format(Locale.US, "%.2fV", inspectedPoint.voltageV),
                            style = MaterialTheme.typography.labelSmall,
                            color = cyber.lucyLavender,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (CurveChannel.TEMP in activeChannels) {
                        Text(
                            text = String.format(Locale.US, "%.1f°C", inspectedPoint.temperatureC),
                            style = MaterialTheme.typography.labelSmall,
                            color = cyber.monowireCrimson,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (CurveChannel.SOC in activeChannels) {
                        Text(
                            text = "${inspectedPoint.levelPct}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = cyber.signalEmerald,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Main Interactive Canvas
        if (samples.size < 2) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(chartHeight)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.65f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = emptyHint,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(chartHeight)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f))
                    .pointerInput(samples.size) {
                        detectTapGestures { offset ->
                            val frac = (offset.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                            inspectedIndex = (frac * (samples.size - 1)).roundToInt()
                        }
                    }
                    .pointerInput(samples.size) {
                        detectHorizontalDragGestures(
                            onHorizontalDrag = { change, _ ->
                                change.consume()
                                val frac = (change.position.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                                inspectedIndex = (frac * (samples.size - 1)).roundToInt()
                            }
                        )
                    }
                    .padding(vertical = 8.dp, horizontal = 4.dp)
            ) {
                val w = size.width
                val h = size.height
                val gridColor = cyber.gridLine.copy(alpha = 0.45f)

                // Horizontal & vertical gridlines
                for (r in 0..4) {
                    val y = h * r / 4f
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y),
                        end = Offset(w, y),
                        strokeWidth = 1f,
                        pathEffect = if (r in 1..3) PathEffect.dashPathEffect(floatArrayOf(6f, 6f)) else null
                    )
                }
                for (c in 1..3) {
                    val x = w * c / 4f
                    drawLine(
                        color = gridColor.copy(alpha = 0.25f),
                        start = Offset(x, 0f),
                        end = Offset(x, h),
                        strokeWidth = 1f
                    )
                }

                val stepX = w / (samples.size - 1).coerceAtLeast(1)

                fun normFor(channel: CurveChannel, pt: CurveDataPoint): Float = when (channel) {
                    CurveChannel.POWER -> (pt.powerWatts / maxPower).coerceIn(0f, 1f)
                    CurveChannel.CURRENT -> (abs(pt.currentMa) / maxCurrent).coerceIn(0f, 1f)
                    CurveChannel.VOLTAGE -> ((pt.voltageV - minVolt) / (maxVolt - minVolt)).coerceIn(0f, 1f)
                    CurveChannel.TEMP -> ((pt.temperatureC - minTemp) / (maxTemp - minTemp)).coerceIn(0f, 1f)
                    CurveChannel.SOC -> (pt.levelPct / 100f).coerceIn(0f, 1f)
                }

                fun drawChannelCurve(channel: CurveChannel, fillArea: Boolean, dashed: Boolean) {
                    val color = channelColor(channel)
                    val linePath = Path()
                    val fillPath = Path()

                    samples.forEachIndexed { idx, pt ->
                        val x = idx * stepX
                        val y = h - normFor(channel, pt) * h
                        if (idx == 0) {
                            linePath.moveTo(x, y)
                            if (fillArea) {
                                fillPath.moveTo(x, h)
                                fillPath.lineTo(x, y)
                            }
                        } else {
                            linePath.lineTo(x, y)
                            if (fillArea) fillPath.lineTo(x, y)
                        }
                        if (fillArea && idx == samples.lastIndex) {
                            fillPath.lineTo(x, h)
                            fillPath.close()
                        }
                    }

                    if (fillArea) {
                        drawPath(
                            path = fillPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(color.copy(alpha = 0.26f), color.copy(alpha = 0.01f)),
                                startY = 0f,
                                endY = h
                            )
                        )
                    }

                    drawPath(
                        path = linePath,
                        color = color,
                        style = Stroke(
                            width = if (channel == CurveChannel.POWER) 2.5.dp.toPx() else 1.8.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round,
                            pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(10f, 6f)) else null
                        )
                    )
                }

                // Draw enabled layers in crisp visual z-order
                if (CurveChannel.SOC in activeChannels) {
                    drawChannelCurve(CurveChannel.SOC, fillArea = false, dashed = false)
                }
                if (CurveChannel.VOLTAGE in activeChannels) {
                    drawChannelCurve(CurveChannel.VOLTAGE, fillArea = false, dashed = true)
                }
                if (CurveChannel.CURRENT in activeChannels) {
                    drawChannelCurve(CurveChannel.CURRENT, fillArea = false, dashed = false)
                }
                if (CurveChannel.TEMP in activeChannels) {
                    drawChannelCurve(CurveChannel.TEMP, fillArea = false, dashed = true)
                }
                if (CurveChannel.POWER in activeChannels) {
                    drawChannelCurve(CurveChannel.POWER, fillArea = true, dashed = false)
                }

                // Lucy Monowire Crosshair & Active Highlight Dots
                val highlightIdx = safeInspectedIdx ?: samples.lastIndex
                val hx = highlightIdx * stepX
                if (safeInspectedIdx != null) {
                    drawLine(
                        color = cyber.lucyPearl.copy(alpha = 0.78f),
                        start = Offset(hx, 0f),
                        end = Offset(hx, h),
                        strokeWidth = 1.4.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))
                    )
                }

                val pt = samples[highlightIdx]
                activeChannels.forEach { ch ->
                    val y = h - normFor(ch, pt) * h
                    drawCircle(
                        color = channelColor(ch),
                        radius = 4.dp.toPx(),
                        center = Offset(hx, y)
                    )
                }
            }

            // X-axis Timeline & Peak/Avg Footer
            val firstTime = timeFormat.format(Date(samples.first().timestampMs))
            val lastTime = timeFormat.format(Date(samples.last().timestampMs))
            val peakW = samples.maxOf { it.powerWatts }
            val avgW = samples.map { it.powerWatts }.average().toFloat()
            val peakT = samples.maxOf { it.temperatureC }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = firstTime,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "可滑动曲线查点 · 峰值 ${String.format(Locale.US, "%.1fW", peakW)} / 均值 ${String.format(Locale.US, "%.1fW", avgW)} / 最高 ${String.format(Locale.US, "%.1f°C", peakT)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = cyber.lucyLavender,
                    maxLines = 1
                )
                Text(
                    text = lastTime,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
