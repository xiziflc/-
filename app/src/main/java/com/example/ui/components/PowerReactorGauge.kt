package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.os.BatteryManager
import com.example.data.BatteryTelemetry
import com.example.data.ChargeSpeedTier
import com.example.data.DetectedProtocol
import com.example.data.DeviceProfileCatalog
import com.example.data.ProtocolDetectionSource
import com.example.data.ProtocolKind
import com.example.ui.theme.LocalCyberColors
import com.example.ui.theme.VoltPulseTheme
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Lucy Lunar Monowire Reactor Gauge:
 * Combines Lucy's pearl-silver Moon silhouette, iridescent pastel cyan-lavender-rose charge arc,
 * and glowing crimson Monowire laser orbit filaments.
 */
@Composable
fun PowerReactorGauge(
    telemetry: BatteryTelemetry,
    tempAlertThresholdC: Float,
    modifier: Modifier = Modifier
) {
    val cyber = LocalCyberColors.current
    val isOverheating = telemetry.temperatureC >= tempAlertThresholdC

    val primaryAccent = when {
        isOverheating -> cyber.alertCrimson
        telemetry.speedTier == ChargeSpeedTier.HYPER -> cyber.monowireCrimson
        telemetry.speedTier == ChargeSpeedTier.FAST -> cyber.neonCyan
        telemetry.speedTier == ChargeSpeedTier.FULL -> cyber.lucyPearl
        telemetry.isPlugged -> cyber.lucyLavender
        else -> cyber.lucyRose
    }

    val animatedLevelSweep by animateFloatAsState(
        targetValue = (telemetry.levelPct / 100f) * 270f,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "batterySweep"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "lucyReactorPulse")
    val monowireAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when {
                    telemetry.speedTier == ChargeSpeedTier.HYPER -> 2200
                    telemetry.isCharging -> 4000
                    else -> 13000
                },
                easing = LinearEasing
            )
        ),
        label = "monowireAngle"
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.14f,
        targetValue = 0.32f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    val sweepColors = remember(isOverheating, cyber) {
        if (isOverheating) {
            listOf(cyber.signalAmber, cyber.monowireCrimson, cyber.alertCrimson)
        } else {
            listOf(cyber.neonCyan, cyber.lucyPearl, cyber.lucyLavender, cyber.lucyRose, cyber.neonCyan)
        }
    }

    Box(
        modifier = modifier
            .widthIn(max = 264.dp)
            .aspectRatio(1f),
        contentAlignment = Alignment.Center
    ) {
        // Layer 1: GPU-composited ambient halo (alpha animated without Canvas redraw)
        Spacer(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = if (telemetry.isCharging) (glowAlpha / 0.32f) else (0.10f / 0.32f)
                }
                .drawWithCache {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val haloRadius = size.minDimension * 0.47f * 1.06f
                    val haloBrush = Brush.radialGradient(
                        colors = listOf(
                            cyber.lucyLavender.copy(alpha = 0.32f),
                            cyber.neonCyan.copy(alpha = 0.16f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = haloRadius
                    )
                    onDrawBehind {
                        drawCircle(brush = haloBrush, radius = haloRadius, center = center)
                    }
                }
        )

        // Layer 2: Static lunar disc, ticks, and charge sweep arc (cached per size/level)
        Spacer(
            modifier = Modifier
                .fillMaxSize()
                .drawWithCache {
                    val minDim = size.minDimension
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val outerRadius = minDim * 0.47f
                    val arcRadius = minDim * 0.40f
                    val moonRadius = minDim * 0.24f

                    val lunarBrush = Brush.radialGradient(
                        colors = listOf(
                            cyber.lucyPearl.copy(alpha = 0.11f),
                            cyber.lucyLavender.copy(alpha = 0.05f),
                            Color.Transparent
                        ),
                        center = Offset(center.x - moonRadius * 0.2f, center.y - moonRadius * 0.25f),
                        radius = moonRadius * 1.15f
                    )
                    val moonStroke = Stroke(width = 1.dp.toPx())
                    val trackStroke = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Butt)
                    val arcTopLeft = Offset(center.x - arcRadius, center.y - arcRadius)
                    val arcSize = Size(arcRadius * 2f, arcRadius * 2f)
                    val sweepBrush = Brush.sweepGradient(colors = sweepColors, center = center)

                    val totalTicks = 48
                    val startDeg = 135.0
                    val sweepTotal = 270.0
                    val tickMajorWidth = 2.2.dp.toPx()
                    val tickMinorWidth = 1.2.dp.toPx()
                    val tickMajorLen = 9.dp.toPx()
                    val tickMinorLen = 4.5.dp.toPx()

                    data class TickGeometry(
                        val start: Offset,
                        val end: Offset,
                        val strokeWidth: Float,
                        val baseColor: Color,
                        val thresholdPct: Float
                    )

                    val ticks = List(totalTicks + 1) { i ->
                        val frac = i.toFloat() / totalTicks
                        val rad = (startDeg + frac * sweepTotal) * PI / 180.0
                        val cosR = cos(rad).toFloat()
                        val sinR = sin(rad).toFloat()
                        val isMajor = i % 6 == 0
                        val tickLen = if (isMajor) tickMajorLen else tickMinorLen
                        val litColor = when {
                            frac > 0.75f -> cyber.lucyRose
                            frac > 0.40f -> cyber.lucyLavender
                            else -> cyber.neonCyan
                        }
                        TickGeometry(
                            start = Offset(center.x + (outerRadius - tickLen) * cosR, center.y + (outerRadius - tickLen) * sinR),
                            end = Offset(center.x + outerRadius * cosR, center.y + outerRadius * sinR),
                            strokeWidth = if (isMajor) tickMajorWidth else tickMinorWidth,
                            baseColor = litColor,
                            thresholdPct = frac * 100f
                        )
                    }

                    onDrawBehind {
                        drawCircle(brush = lunarBrush, radius = moonRadius, center = center)
                        drawCircle(
                            color = cyber.lucyPearl.copy(alpha = 0.16f),
                            radius = moonRadius,
                            center = center,
                            style = moonStroke
                        )

                        for (tick in ticks) {
                            val isLit = tick.thresholdPct <= telemetry.levelPct
                            drawLine(
                                color = if (isLit) tick.baseColor.copy(alpha = 0.92f) else cyber.gridLine.copy(alpha = 0.7f),
                                start = tick.start,
                                end = tick.end,
                                strokeWidth = tick.strokeWidth,
                                cap = StrokeCap.Butt
                            )
                        }

                        drawArc(
                            color = cyber.gridLine.copy(alpha = 0.55f),
                            startAngle = 135f,
                            sweepAngle = 270f,
                            useCenter = false,
                            topLeft = arcTopLeft,
                            size = arcSize,
                            style = trackStroke
                        )

                        if (animatedLevelSweep > 0.5f) {
                            drawArc(
                                brush = sweepBrush,
                                startAngle = 135f,
                                sweepAngle = animatedLevelSweep,
                                useCenter = false,
                                topLeft = arcTopLeft,
                                size = arcSize,
                                style = trackStroke
                            )
                        }
                    }
                }
        )

        // Layer 3: GPU-rotated monowire filaments (rotationZ animated without Canvas redraw)
        Spacer(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { rotationZ = monowireAngle }
                .drawWithCache {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val monowireRadius = size.minDimension * 0.33f
                    val monoTopLeft = Offset(center.x - monowireRadius, center.y - monowireRadius)
                    val monoSize = Size(monowireRadius * 2f, monowireRadius * 2f)
                    val crimsonStroke = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round)
                    val cyanStroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)

                    onDrawBehind {
                        drawArc(
                            color = cyber.monowireCrimson.copy(alpha = if (telemetry.isCharging) 0.85f else 0.28f),
                            startAngle = 0f,
                            sweepAngle = 62f,
                            useCenter = false,
                            topLeft = monoTopLeft,
                            size = monoSize,
                            style = crimsonStroke
                        )
                        drawArc(
                            color = cyber.neonCyan.copy(alpha = if (telemetry.isCharging) 0.72f else 0.22f),
                            startAngle = 180f,
                            sweepAngle = 46f,
                            useCenter = false,
                            topLeft = monoTopLeft,
                            size = monoSize,
                            style = cyanStroke
                        )
                    }
                }
        )

        // Center HUD numeric readout
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(top = 6.dp)
        ) {
            CyberBadge(
                text = if (isOverheating) "THERMAL SPIKE" else telemetry.speedTier.tag,
                color = primaryAccent
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = String.format(Locale.US, "%.1f", telemetry.powerWatts),
                    style = MaterialTheme.typography.displayLarge.copy(fontSize = 40.sp),
                    color = cyber.lucyPearl,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "W",
                    style = MaterialTheme.typography.titleLarge,
                    color = cyber.neonCyan,
                    modifier = Modifier.padding(start = 3.dp, bottom = 6.dp)
                )
            }
            Text(
                text = "${telemetry.detectedProtocol.kind.shortLabel} · ${telemetry.levelPct}%",
                style = MaterialTheme.typography.labelMedium,
                color = cyber.lucyLavender,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(3.dp))
            val etaLabel = telemetry.estimatedMinutesToFull?.let { mins ->
                val h = mins / 60
                val m = mins % 60
                if (h > 0) "预计充满 ${h}h ${m}m" else "预计充满 ${m}m"
            } ?: telemetry.plugShortTag
            Text(
                text = etaLabel,
                style = MaterialTheme.typography.labelSmall,
                color = cyber.neonCyan.copy(alpha = 0.9f)
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0814)
@Composable
private fun PowerReactorGaugePreview() {
    VoltPulseTheme {
        PowerReactorGauge(
            telemetry = BatteryTelemetry(
                levelPct = 68,
                status = BatteryManager.BATTERY_STATUS_CHARGING,
                plugged = BatteryManager.BATTERY_PLUGGED_AC,
                health = BatteryManager.BATTERY_HEALTH_GOOD,
                temperatureC = 34.2f,
                activeProfile = DeviceProfileCatalog.oppoFindX8Ultra,
                detectedProtocol = DetectedProtocol(
                    kind = ProtocolKind.SUPERVOOC,
                    label = "SuperVOOC 100W",
                    source = ProtocolDetectionSource.SYSFS_KERNEL,
                    detail = "10V / 8.8A"
                ),
                calibratedVoltageV = 8.95f,
                calibratedCurrentMa = 9820f,
                powerWatts = 87.9f,
                estimatedMinutesToFull = 14
            ),
            tempAlertThresholdC = 43f,
            modifier = Modifier.padding(16.dp)
        )
    }
}
