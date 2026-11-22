package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
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

    Box(
        modifier = modifier
            .widthIn(max = 264.dp)
            .aspectRatio(1f),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val minDim = size.minDimension
            val center = Offset(size.width / 2f, size.height / 2f)
            val outerRadius = minDim * 0.47f
            val arcRadius = minDim * 0.40f
            val monowireRadius = minDim * 0.33f
            val moonRadius = minDim * 0.24f

            // Ambient Lucy iridescent halo
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        cyber.lucyLavender.copy(alpha = if (telemetry.isCharging) glowAlpha else 0.10f),
                        cyber.neonCyan.copy(alpha = 0.05f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = outerRadius * 1.06f
                ),
                radius = outerRadius * 1.06f,
                center = center
            )

            // Subtle Lunar disc & crescent highlight behind digital readout
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        cyber.lucyPearl.copy(alpha = 0.11f),
                        cyber.lucyLavender.copy(alpha = 0.05f),
                        Color.Transparent
                    ),
                    center = Offset(center.x - moonRadius * 0.2f, center.y - moonRadius * 0.25f),
                    radius = moonRadius * 1.15f
                ),
                radius = moonRadius,
                center = center
            )
            drawCircle(
                color = cyber.lucyPearl.copy(alpha = 0.16f),
                radius = moonRadius,
                center = center,
                style = Stroke(width = 1.dp.toPx())
            )

            // Outer segmented telemetry ticks (48 ticks spanning 270 degrees)
            val totalTicks = 48
            val startDeg = 135.0
            val sweepTotal = 270.0
            for (i in 0..totalTicks) {
                val frac = i.toFloat() / totalTicks
                val deg = startDeg + frac * sweepTotal
                val rad = deg * PI / 180.0
                val isMajor = i % 6 == 0
                val tickLen = if (isMajor) 9.dp.toPx() else 4.5.dp.toPx()
                val isLit = (frac * 100f) <= telemetry.levelPct

                val tickColor = when {
                    !isLit -> cyber.gridLine
                    frac > 0.75f -> cyber.lucyRose
                    frac > 0.40f -> cyber.lucyLavender
                    else -> cyber.neonCyan
                }

                val startPt = Offset(
                    x = center.x + (outerRadius - tickLen) * cos(rad).toFloat(),
                    y = center.y + (outerRadius - tickLen) * sin(rad).toFloat()
                )
                val endPt = Offset(
                    x = center.x + outerRadius * cos(rad).toFloat(),
                    y = center.y + outerRadius * sin(rad).toFloat()
                )
                drawLine(
                    color = tickColor.copy(alpha = if (isLit) 0.92f else 0.7f),
                    start = startPt,
                    end = endPt,
                    strokeWidth = if (isMajor) 2.2.dp.toPx() else 1.2.dp.toPx(),
                    cap = StrokeCap.Butt
                )
            }

            // Track background arc
            val arcTopLeft = Offset(center.x - arcRadius, center.y - arcRadius)
            val arcSize = Size(arcRadius * 2f, arcRadius * 2f)
            drawArc(
                color = cyber.gridLine.copy(alpha = 0.55f),
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Butt)
            )

            // Lucy Iridescent Hair Sweep Charge Arc
            if (animatedLevelSweep > 0.5f) {
                val sweepColors = if (isOverheating) {
                    listOf(cyber.signalAmber, cyber.monowireCrimson, cyber.alertCrimson)
                } else {
                    listOf(cyber.neonCyan, cyber.lucyPearl, cyber.lucyLavender, cyber.lucyRose, cyber.neonCyan)
                }
                drawArc(
                    brush = Brush.sweepGradient(colors = sweepColors, center = center),
                    startAngle = 135f,
                    sweepAngle = animatedLevelSweep,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Butt)
                )
            }

            // Orbiting Glowing Monowire Filaments (Crimson + Neon Cyan)
            rotate(degrees = monowireAngle, pivot = center) {
                val monoTopLeft = Offset(center.x - monowireRadius, center.y - monowireRadius)
                val monoSize = Size(monowireRadius * 2f, monowireRadius * 2f)
                drawArc(
                    color = cyber.monowireCrimson.copy(alpha = if (telemetry.isCharging) 0.85f else 0.28f),
                    startAngle = 0f,
                    sweepAngle = 62f,
                    useCenter = false,
                    topLeft = monoTopLeft,
                    size = monoSize,
                    style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round)
                )
                drawArc(
                    color = cyber.neonCyan.copy(alpha = if (telemetry.isCharging) 0.72f else 0.22f),
                    startAngle = 180f,
                    sweepAngle = 46f,
                    useCenter = false,
                    topLeft = monoTopLeft,
                    size = monoSize,
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }

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
