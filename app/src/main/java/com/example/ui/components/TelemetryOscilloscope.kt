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
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.draw.drawWithCache
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

    data class CurveBounds(
        val maxPower: Float,
        val maxCurrent: Float,
        val minVolt: Float,
        val maxVolt: Float,
        val minTemp: Float,
        val maxTemp: Float
    )

    // Compute normalized ranges for each metric (memoized per samples list)
    val (maxPower, maxCurrent, minVolt, maxVolt, minTemp, maxTemp) = remember(samples) {
        val pMax = max(10f, (samples.maxOfOrNull { it.powerWatts } ?: 10f) * 1.15f)
        val cMax = max(1000f, (samples.maxOfOrNull { abs(it.currentMa) } ?: 1000f) * 1.15f)
        val vMin = ((samples.minOfOrNull { it.voltageV } ?: 3.5f) - 0.3f).coerceAtLeast(2.5f)
        val vMax = max(vMin + 1.0f, (samples.maxOfOrNull { it.voltageV } ?: 5.0f) + 0.3f)
        val tMin = ((samples.minOfOrNull { it.temperatureC } ?: 25f) - 3f).coerceAtLeast(15f)
        val tMax = max(tMin + 10f, (samples.maxOfOrNull { it.temperatureC } ?: 45f) + 3f)
        CurveBounds(pMax, cMax, vMin, vMax, tMin, tMax)
    }

    val gridDashEffect = remember { PathEffect.dashPathEffect(floatArrayOf(6f, 6f)) }
    val gapDashEffect = remember { PathEffect.dashPathEffect(floatArrayOf(4f, 4f)) }
    val curveDashEffect = remember { PathEffect.dashPathEffect(floatArrayOf(10f, 6f)) }
    val crosshairDashEffect = remember { PathEffect.dashPathEffect(floatArrayOf(6f, 4f)) }

    val minTs = samples.firstOrNull()?.timestampMs ?: 0L
    val maxTs = samples.lastOrNull()?.timestampMs ?: minTs
    val totalSpanMs = (maxTs - minTs).coerceAtLeast(0L)
    val useRealTimeline = samples.size >= 2 && totalSpanMs >= 2000L
    val gapThresholdMs = 45_000L
    val gapCount = remember(samples) {
        if (samples.size < 2) 0
        else (1 until samples.size).count { samples[it].timestampMs - samples[it - 1].timestampMs > gapThresholdMs }
    }

    fun nearestIndexForFraction(frac: Float): Int {
        if (samples.isEmpty()) return 0
        if (!useRealTimeline) {
            return (frac * (samples.size - 1)).roundToInt().coerceIn(0, samples.lastIndex)
        }
        val targetTs = minTs + (frac * totalSpanMs).toLong()
        var bestIdx = 0
        var bestDiff = Long.MAX_VALUE
        for (i in samples.indices) {
            val diff = abs(samples[i].timestampMs - targetTs)
            if (diff < bestDiff) {
                bestDiff = diff
                bestIdx = i
            }
        }
        return bestIdx
    }

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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(chartHeight)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f))
                    .pointerInput(samples) {
                        detectTapGestures { offset ->
                            val frac = (offset.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                            inspectedIndex = nearestIndexForFraction(frac)
                        }
                    }
                    .pointerInput(samples) {
                        detectHorizontalDragGestures(
                            onHorizontalDrag = { change, _ ->
                                change.consume()
                                val frac = (change.position.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                                inspectedIndex = nearestIndexForFraction(frac)
                            }
                        )
                    }
                    .padding(vertical = 8.dp, horizontal = 4.dp)
            ) {
                fun normFor(channel: CurveChannel, pt: CurveDataPoint): Float = when (channel) {
                    CurveChannel.POWER -> (pt.powerWatts / maxPower).coerceIn(0f, 1f)
                    CurveChannel.CURRENT -> (abs(pt.currentMa) / maxCurrent).coerceIn(0f, 1f)
                    CurveChannel.VOLTAGE -> ((pt.voltageV - minVolt) / (maxVolt - minVolt)).coerceIn(0f, 1f)
                    CurveChannel.TEMP -> ((pt.temperatureC - minTemp) / (maxTemp - minTemp)).coerceIn(0f, 1f)
                    CurveChannel.SOC -> (pt.levelPct / 100f).coerceIn(0f, 1f)
                }

                // Cached curves & gridlines (rebuilt only when size, samples, or activeChannels change)
                Spacer(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawWithCache {
                            val w = size.width
                            val h = size.height
                            val gridColor = cyber.gridLine.copy(alpha = 0.45f)
                            val stepX = w / (samples.size - 1).coerceAtLeast(1)

                            fun xFor(idx: Int): Float = if (useRealTimeline) {
                                ((samples[idx].timestampMs - minTs).toFloat() / totalSpanMs.toFloat()).coerceIn(0f, 1f) * w
                            } else {
                                idx * stepX
                            }

                            data class CachedCurve(
                                val linePath: Path,
                                val fillPath: Path?,
                                val fillBrush: Brush?,
                                val color: Color,
                                val stroke: Stroke
                            )

                            fun buildCurve(channel: CurveChannel, fillArea: Boolean, dashed: Boolean): CachedCurve {
                                val color = channelColor(channel)
                                val linePath = Path()
                                val fillPath = if (fillArea) Path() else null
                                var hasOpenSegment = false

                                samples.forEachIndexed { idx, pt ->
                                    val x = xFor(idx)
                                    val y = h - normFor(channel, pt) * h
                                    val isGapAfterPrev = idx > 0 &&
                                        (samples[idx].timestampMs - samples[idx - 1].timestampMs > gapThresholdMs)

                                    if (idx == 0 || isGapAfterPrev) {
                                        if (fillPath != null && hasOpenSegment) {
                                            fillPath.lineTo(xFor(idx - 1), h)
                                            fillPath.close()
                                        }
                                        linePath.moveTo(x, y)
                                        if (fillPath != null) {
                                            fillPath.moveTo(x, h)
                                            fillPath.lineTo(x, y)
                                        }
                                        hasOpenSegment = true
                                    } else {
                                        linePath.lineTo(x, y)
                                        fillPath?.lineTo(x, y)
                                    }
                                    if (fillPath != null && idx == samples.lastIndex && hasOpenSegment) {
                                        fillPath.lineTo(x, h)
                                        fillPath.close()
                                    }
                                }

                                val fillBrush = if (fillPath != null) {
                                    Brush.verticalGradient(
                                        colors = listOf(color.copy(alpha = 0.26f), color.copy(alpha = 0.01f)),
                                        startY = 0f,
                                        endY = h
                                    )
                                } else null

                                val stroke = Stroke(
                                    width = if (channel == CurveChannel.POWER) 2.5.dp.toPx() else 1.8.dp.toPx(),
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round,
                                    pathEffect = if (dashed) curveDashEffect else null
                                )
                                return CachedCurve(linePath, fillPath, fillBrush, color, stroke)
                            }

                            val cachedCurves = buildList {
                                if (CurveChannel.SOC in activeChannels) add(buildCurve(CurveChannel.SOC, fillArea = false, dashed = false))
                                if (CurveChannel.VOLTAGE in activeChannels) add(buildCurve(CurveChannel.VOLTAGE, fillArea = false, dashed = true))
                                if (CurveChannel.CURRENT in activeChannels) add(buildCurve(CurveChannel.CURRENT, fillArea = false, dashed = false))
                                if (CurveChannel.TEMP in activeChannels) add(buildCurve(CurveChannel.TEMP, fillArea = false, dashed = true))
                                if (CurveChannel.POWER in activeChannels) add(buildCurve(CurveChannel.POWER, fillArea = true, dashed = false))
                            }

                            onDrawBehind {
                                for (r in 0..4) {
                                    val y = h * r / 4f
                                    drawLine(
                                        color = gridColor,
                                        start = Offset(0f, y),
                                        end = Offset(w, y),
                                        strokeWidth = 1f,
                                        pathEffect = if (r in 1..3) gridDashEffect else null
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
                                for (i in 1 until samples.size) {
                                    if (samples[i].timestampMs - samples[i - 1].timestampMs > gapThresholdMs) {
                                        val gx1 = xFor(i - 1)
                                        val gx2 = xFor(i)
                                        drawRect(
                                            color = cyber.lucyRose.copy(alpha = 0.08f),
                                            topLeft = Offset(gx1, 0f),
                                            size = androidx.compose.ui.geometry.Size((gx2 - gx1).coerceAtLeast(2f), h)
                                        )
                                        drawLine(
                                            color = cyber.lucyRose.copy(alpha = 0.45f),
                                            start = Offset(gx1, 0f),
                                            end = Offset(gx1, h),
                                            strokeWidth = 1f,
                                            pathEffect = gapDashEffect
                                        )
                                    }
                                }
                                for (curve in cachedCurves) {
                                    if (curve.fillPath != null && curve.fillBrush != null) {
                                        drawPath(path = curve.fillPath, brush = curve.fillBrush)
                                    }
                                    drawPath(path = curve.linePath, color = curve.color, style = curve.stroke)
                                }
                            }
                        }
                )

                // Lightweight overlay Canvas for crosshair & highlight dots only
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val stepX = w / (samples.size - 1).coerceAtLeast(1)
                    val highlightIdx = safeInspectedIdx ?: samples.lastIndex
                    val hx = if (useRealTimeline) {
                        ((samples[highlightIdx].timestampMs - minTs).toFloat() / totalSpanMs.toFloat()).coerceIn(0f, 1f) * w
                    } else {
                        highlightIdx * stepX
                    }
                    if (safeInspectedIdx != null) {
                        drawLine(
                            color = cyber.lucyPearl.copy(alpha = 0.78f),
                            start = Offset(hx, 0f),
                            end = Offset(hx, h),
                            strokeWidth = 1.4.dp.toPx(),
                            pathEffect = crosshairDashEffect
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
            }

            data class CurveSummary(
                val firstTime: String,
                val lastTime: String,
                val peakW: Float,
                val avgW: Float,
                val peakT: Float
            )

            // X-axis Timeline & Peak/Avg Footer
            val (firstTimeStr, lastTimeStr, peakWFloat, avgWFloat, peakTFloat) = remember(samples) {
                val ft = timeFormat.format(Date(samples.first().timestampMs))
                val lt = timeFormat.format(Date(samples.last().timestampMs))
                val pw = samples.maxOf { it.powerWatts }
                val aw = samples.map { it.powerWatts }.average().toFloat()
                val pt = samples.maxOf { it.temperatureC }
                CurveSummary(ft, lt, pw, aw, pt)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = firstTimeStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (gapCount > 0) {
                        "峰值 ${String.format(Locale.US, "%.1fW", peakWFloat)} / 均值 ${String.format(Locale.US, "%.1fW", avgWFloat)} / 断采 ${gapCount}处"
                    } else {
                        "可滑动曲线查点 · 峰值 ${String.format(Locale.US, "%.1fW", peakWFloat)} / 均值 ${String.format(Locale.US, "%.1fW", avgWFloat)} / 最高 ${String.format(Locale.US, "%.1f°C", peakTFloat)}"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (gapCount > 0) cyber.lucyRose else cyber.lucyLavender,
                    maxLines = 1
                )
                Text(
                    text = lastTimeStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
