package com.example.ui.theme

import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Immutable
data class CyberExtendedColors(
    val neonCyan: Color = NeonCyan,
    val acidYellow: Color = AcidYellow,
    val hyperMagenta: Color = HyperMagenta,
    val lucyPearl: Color = LucyPearlSilver,
    val lucyLavender: Color = LucyLavender,
    val lucyRose: Color = LucyRoseNeon,
    val monowireCrimson: Color = MonowireCrimson,
    val monowireFlare: Color = MonowireFlare,
    val signalEmerald: Color = SignalEmerald,
    val signalAmber: Color = SignalAmber,
    val alertCrimson: Color = AlertCrimson,
    val gridLine: Color = CyberGridLine,
    val panelElevated: Color = CyberPanelElevated,
    val textMuted: Color = CyberTextMuted
) {
    val lucyIridescentBrush: Brush
        get() = Brush.linearGradient(
            colors = listOf(neonCyan, lucyPearl, lucyLavender, lucyRose)
        )

    val monowireBrush: Brush
        get() = Brush.linearGradient(
            colors = listOf(monowireCrimson, monowireFlare, lucyRose)
        )
}

val LocalCyberColors = staticCompositionLocalOf { CyberExtendedColors() }

private val LucyCyberColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = CyberVoid,
    primaryContainer = Color(0xFF0A323D),
    onPrimaryContainer = NeonCyan,
    secondary = LucyLavender,
    onSecondary = CyberVoid,
    secondaryContainer = Color(0xFF2B1B4A),
    onSecondaryContainer = LucyLavender,
    tertiary = LucyRoseNeon,
    onTertiary = CyberVoid,
    tertiaryContainer = Color(0xFF47102C),
    onTertiaryContainer = LucyRoseNeon,
    background = CyberVoid,
    onBackground = CyberTextPrimary,
    surface = CyberDeepSurface,
    onSurface = CyberTextPrimary,
    surfaceVariant = CyberPanelSurface,
    onSurfaceVariant = CyberTextSecondary,
    surfaceContainerLowest = CyberVoid,
    surfaceContainerLow = CyberDeepSurface,
    surfaceContainer = CyberPanelSurface,
    surfaceContainerHigh = CyberPanelElevated,
    surfaceContainerHighest = Color(0xFF272147),
    outline = CyberGridLine,
    outlineVariant = Color(0xFF1C1836),
    error = AlertCrimson,
    onError = CyberTextPrimary,
    errorContainer = Color(0xFF4A0C1D),
    onErrorContainer = AlertCrimson
)

val CyberShapes = Shapes(
    extraSmall = CutCornerShape(4.dp),
    small = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
    medium = CutCornerShape(topStart = 12.dp, bottomEnd = 12.dp),
    large = CutCornerShape(topStart = 16.dp, bottomEnd = 16.dp),
    extraLarge = CutCornerShape(topStart = 20.dp, bottomEnd = 20.dp)
)

@Composable
fun VoltPulseTheme(
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalCyberColors provides CyberExtendedColors()
    ) {
        MaterialTheme(
            colorScheme = LucyCyberColorScheme,
            typography = Typography,
            shapes = CyberShapes,
            content = content
        )
    }
}
