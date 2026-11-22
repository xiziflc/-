package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.LocalCyberColors
import com.example.ui.theme.VoltPulseTheme

@Composable
fun CyberGridBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val bg = MaterialTheme.colorScheme.background
    val cyber = LocalCyberColors.current

    Box(
        modifier = modifier.drawBehind {
            drawRect(color = bg)

            // Lucy Iridescent Ambient Glows (Cyan + Lavender + Rose Monowire)
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(cyber.neonCyan.copy(alpha = 0.08f), Color.Transparent),
                    center = Offset(size.width * 0.85f, size.height * 0.08f),
                    radius = size.width * 0.85f
                )
            )
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(cyber.lucyLavender.copy(alpha = 0.07f), Color.Transparent),
                    center = Offset(size.width * 0.15f, size.height * 0.45f),
                    radius = size.width * 0.72f
                )
            )
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(cyber.monowireCrimson.copy(alpha = 0.06f), Color.Transparent),
                    center = Offset(size.width * 0.18f, size.height * 0.90f),
                    radius = size.width * 0.75f
                )
            )

            // Netrunner ICE precision grid
            val step = 36.dp.toPx()
            val gridColor = cyber.gridLine.copy(alpha = 0.25f)
            var x = 0f
            while (x < size.width) {
                drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                x += step
            }
            var y = 0f
            while (y < size.height) {
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                y += step
            }
        },
        content = content
    )
}

@Composable
fun LucyMoonHeroBanner(
    deviceTitle: String,
    protocolBadge: String,
    levelPct: Int,
    statusBadge: String,
    statusColor: Color,
    modifier: Modifier = Modifier
) {
    val cyber = LocalCyberColors.current
    val shape = CutCornerShape(topStart = 16.dp, bottomEnd = 16.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(148.dp)
            .clip(shape)
            .border(
                width = 1.2.dp,
                brush = cyber.lucyIridescentBrush,
                shape = shape
            )
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_lucy_moon_banner),
            contentDescription = "Lucy 月球赛博朋克主题背景",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Left-to-right & bottom gradient scrim for ultra-clean HUD typography
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0.0f to MaterialTheme.colorScheme.background.copy(alpha = 0.92f),
                        0.52f to MaterialTheme.colorScheme.background.copy(alpha = 0.68f),
                        1.0f to Color.Transparent
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Transparent,
                        0.68f to Color.Transparent,
                        1.0f to MaterialTheme.colorScheme.background.copy(alpha = 0.88f)
                    )
                )
        )

        // Top monowire laser accent bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .align(Alignment.TopCenter)
                .background(cyber.monowireBrush)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "LUCY // NETRUNNER OS",
                        style = MaterialTheme.typography.labelSmall,
                        color = cyber.lucyLavender,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "VOLTPULSE",
                        style = MaterialTheme.typography.headlineLarge,
                        color = cyber.lucyPearl
                    )
                    Text(
                        text = deviceTitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = cyber.neonCyan
                    )
                }

                CyberBadge(
                    text = statusBadge,
                    color = statusColor
                )
            }

            // Lunar Charge Progress Strip ("TO THE MOON")
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "TO THE MOON · $levelPct%",
                        style = MaterialTheme.typography.labelSmall,
                        color = cyber.lucyPearl
                    )
                    Text(
                        text = protocolBadge,
                        style = MaterialTheme.typography.labelSmall,
                        color = cyber.lucyRose,
                        fontWeight = FontWeight.Bold
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(cyber.gridLine.copy(alpha = 0.7f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction = (levelPct / 100f).coerceIn(0.02f, 1f))
                            .height(4.dp)
                            .background(cyber.lucyIridescentBrush)
                    )
                }
            }
        }
    }
}

@Composable
fun CyberPanel(
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    cutSize: Dp = 14.dp,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.90f),
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val shape: Shape = CutCornerShape(topStart = cutSize, bottomEnd = cutSize)
    val baseModifier = modifier
        .clip(shape)
        .background(
            brush = Brush.linearGradient(
                colors = listOf(
                    containerColor,
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
                )
            )
        )
        .border(
            width = 1.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    accentColor.copy(alpha = 0.65f),
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                    accentColor.copy(alpha = 0.4f)
                )
            ),
            shape = shape
        )
        .drawWithContent {
            drawContent()
            val bLen = 10.dp.toPx()
            val stroke = 2.dp.toPx()
            val c = accentColor.copy(alpha = 0.85f)
            drawLine(c, Offset(size.width - bLen, 1f), Offset(size.width - 1f, 1f), stroke)
            drawLine(c, Offset(size.width - 1f, 1f), Offset(size.width - 1f, bLen), stroke)
            drawLine(c, Offset(1f, size.height - bLen), Offset(1f, size.height - 1f), stroke)
            drawLine(c, Offset(1f, size.height - 1f), Offset(bLen, size.height - 1f), stroke)
        }

    val clickableModifier = if (onClick != null) {
        baseModifier
            .minimumInteractiveComponentSize()
            .clickable(onClick = onClick)
    } else {
        baseModifier
    }

    Box(modifier = clickableModifier, content = content)
}

@Composable
fun CyberSectionHeader(
    indexTag: String,
    title: String,
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(16.dp)
                    .background(accentColor)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = indexTag,
                style = MaterialTheme.typography.labelMedium,
                color = accentColor
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold
            )
        }
        if (trailing != null) {
            trailing()
        }
    }
}

@Composable
fun CyberBadge(
    text: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp),
        color = color.copy(alpha = 0.16f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.72f))
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun CyberPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.minimumInteractiveComponentSize(),
        shape = CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = accentColor,
            contentColor = contentColor,
            disabledContainerColor = accentColor.copy(alpha = 0.25f),
            disabledContentColor = contentColor.copy(alpha = 0.5f)
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun CyberOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.minimumInteractiveComponentSize(),
        shape = CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.75f)),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = accentColor.copy(alpha = 0.08f),
            contentColor = accentColor
        ),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun CyberChipToggle(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary
) {
    val shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp)
    Surface(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(shape)
            .clickable(onClick = onClick),
        shape = shape,
        color = if (selected) accentColor.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) accentColor else MaterialTheme.colorScheme.outline
        )
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0814)
@Composable
private fun LucyMoonHeroBannerPreview() {
    VoltPulseTheme {
        LucyMoonHeroBanner(
            deviceTitle = "OPPO Find X8 Ultra · 串联双电芯 · 6100mAh 硅碳",
            protocolBadge = "SuperVOOC",
            levelPct = 68,
            statusBadge = "ICE 监控中",
            statusColor = LocalCyberColors.current.signalEmerald,
            modifier = Modifier.padding(16.dp)
        )
    }
}
