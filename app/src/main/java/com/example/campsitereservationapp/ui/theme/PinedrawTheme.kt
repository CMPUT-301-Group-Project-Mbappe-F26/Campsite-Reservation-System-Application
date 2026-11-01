package com.example.campsitereservationapp.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Colours from 1-DESIGNSYSTEM.svg
object PinedrawColors {
    val Background = Color(0xFF06110B)
    val Surface = Color(0xFF11251A)
    val Text = Color(0xFFF1F5EE)
    val TextSecondary = Color(0xFFA7B6AA)
    val TextMuted = Color(0xFF6E8273)
    val Lime = Color(0xFFA3E05C)
    val Amber = Color(0xFFF5B74E)
    val Coral = Color(0xFFFF6F61)
    val Sky = Color(0xFF7CC4FF)
    val Teal = Color(0xFF43D3C3)
    val Ink = Color(0xFF08160D)
    val Glass = Color.White.copy(alpha = 0.06f)

    // Gradient used for the primary action buttons
    val PrimaryGradient = Brush.horizontalGradient(listOf(Lime, Color(0xFF7ACF54)))
}

object PinedrawSpacing {
    val Small = 8.dp
    val Medium = 12.dp
    val Large = 16.dp
    val Screen = 24.dp
    val Section = 32.dp
}

private val pinedrawScheme = darkColorScheme(
    primary = PinedrawColors.Lime,
    onPrimary = PinedrawColors.Ink,
    primaryContainer = PinedrawColors.Lime,
    onPrimaryContainer = PinedrawColors.Ink,
    secondary = PinedrawColors.Teal,
    onSecondary = PinedrawColors.Ink,
    secondaryContainer = PinedrawColors.Lime,
    onSecondaryContainer = PinedrawColors.Ink,
    tertiary = PinedrawColors.Amber,
    onTertiary = PinedrawColors.Ink,
    tertiaryContainer = PinedrawColors.Surface,
    onTertiaryContainer = PinedrawColors.Amber,
    background = PinedrawColors.Background,
    onBackground = PinedrawColors.Text,
    surface = PinedrawColors.Surface,
    onSurface = PinedrawColors.Text,
    surfaceTint = Color.Transparent,
    surfaceDim = PinedrawColors.Background,
    surfaceBright = Color(0xFF1E3A26),
    surfaceContainerLowest = PinedrawColors.Background,
    surfaceContainerLow = PinedrawColors.Surface,
    surfaceContainer = PinedrawColors.Surface,
    surfaceContainerHigh = Color(0xFF193223),
    surfaceContainerHighest = Color(0xFF203E2C),
    inverseSurface = PinedrawColors.Text,
    inverseOnSurface = PinedrawColors.Ink,
    inversePrimary = Color(0xFF2E6B20),
    surfaceVariant = PinedrawColors.Glass,
    onSurfaceVariant = PinedrawColors.TextSecondary,
    error = PinedrawColors.Coral,
    onError = PinedrawColors.Ink,
    errorContainer = PinedrawColors.Coral.copy(alpha = 0.15f),
    onErrorContainer = PinedrawColors.Coral,
    outline = PinedrawColors.TextMuted,
    outlineVariant = PinedrawColors.TextMuted.copy(alpha = 0.3f),
    scrim = Color.Black
)

private val pinedrawTypography = Typography(
    displayLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 34.sp, fontWeight = FontWeight.Bold),
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 26.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 19.sp, fontWeight = FontWeight.Bold),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 15.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 15.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 13.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
)

private val pinedrawShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

// Applies the Pinedraw colours, fonts, and rounded shapes.
@Composable
fun PinedrawTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = pinedrawScheme,
        typography = pinedrawTypography,
        shapes = pinedrawShapes,
        content = content
    )
}
