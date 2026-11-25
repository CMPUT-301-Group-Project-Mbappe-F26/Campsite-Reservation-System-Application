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

/** Colours from the Pinedraw design system. */
object PinedrawColors {
    val Background = Color(0xFF06110B)
    val Surface = Color(0xFF11251A)
    val SurfaceRaised = Color(0xFF183223)
    val Text = Color(0xFFF1F5EE)
    val TextSecondary = Color(0xFFA7B6AA)
    val TextMuted = Color(0xFF6E8273)
    val Lime = Color(0xFFA3E05C)
    val LimeLight = Color(0xFFBDF07A)
    val LimeDark = Color(0xFF5FBF3D)
    val Amber = Color(0xFFF5B74E)
    val Coral = Color(0xFFFF6F61)
    val Sky = Color(0xFF7CC4FF)
    val Teal = Color(0xFF43D3C3)
    val Ink = Color(0xFF08160D)
    val Glass = Color.White.copy(alpha = 0.06f)
    val Border = Color.White.copy(alpha = 0.08f)

    /** Lime gradient used by primary actions. */
    val PrimaryGradient = Brush.horizontalGradient(listOf(LimeLight, LimeDark))

    /** Amber gradient used by host actions. */
    val AmberGradient = Brush.horizontalGradient(listOf(Color(0xFFFFCB6B), Color(0xFFF0A63A)))

    /** Soft forest glow behind every screen. */
    val ScreenGradient = Brush.verticalGradient(
        0f to Color(0xFF1B3A25),
        0.4f to Background,
        1f to Background
    )
}

/** Spacing scale shared by every screen. */
object PinedrawSpacing {
    val XSmall = 4.dp
    val Small = 8.dp
    val Medium = 12.dp
    val Large = 16.dp
    val Gutter = 20.dp
    val Screen = 24.dp
    val Section = 32.dp
}

/** Fixed component sizes so controls look the same everywhere. */
object PinedrawDimens {
    val ButtonHeight = 56.dp
    val CompactButtonHeight = 44.dp
    val IconButtonSize = 48.dp
    val IconSize = 22.dp
    val SmallIconSize = 18.dp
    val IconBadgeSize = 44.dp
    val CardPadding = 18.dp
    val BorderWidth = 1.dp
    val NavBarHeight = 68.dp
}

/** Corner radius scale: 12 · 16 · 24 · 32 · pill. */
object PinedrawRadius {
    val Small = RoundedCornerShape(12.dp)
    val Medium = RoundedCornerShape(16.dp)
    val Large = RoundedCornerShape(24.dp)
    val XLarge = RoundedCornerShape(32.dp)
    val Pill = RoundedCornerShape(percent = 50)
}

private val pinedrawScheme = darkColorScheme(
    primary = PinedrawColors.Lime,
    onPrimary = PinedrawColors.Ink,
    primaryContainer = PinedrawColors.Lime.copy(alpha = 0.16f),
    onPrimaryContainer = PinedrawColors.Lime,
    secondary = PinedrawColors.Teal,
    onSecondary = PinedrawColors.Ink,
    secondaryContainer = PinedrawColors.Lime.copy(alpha = 0.16f),
    onSecondaryContainer = PinedrawColors.Lime,
    tertiary = PinedrawColors.Amber,
    onTertiary = PinedrawColors.Ink,
    tertiaryContainer = PinedrawColors.Amber.copy(alpha = 0.16f),
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
    surfaceContainerHigh = PinedrawColors.SurfaceRaised,
    surfaceContainerHighest = Color(0xFF203E2C),
    inverseSurface = PinedrawColors.Text,
    inverseOnSurface = PinedrawColors.Ink,
    inversePrimary = Color(0xFF2E6B20),
    surfaceVariant = PinedrawColors.SurfaceRaised,
    onSurfaceVariant = PinedrawColors.TextSecondary,
    error = PinedrawColors.Coral,
    onError = PinedrawColors.Ink,
    errorContainer = PinedrawColors.Coral.copy(alpha = 0.15f),
    onErrorContainer = PinedrawColors.Coral,
    outline = PinedrawColors.TextMuted,
    outlineVariant = PinedrawColors.Border,
    scrim = Color.Black.copy(alpha = 0.7f)
)

private val font = FontFamily.SansSerif

private val pinedrawTypography = Typography(
    displayLarge = TextStyle(fontFamily = font, fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
    displayMedium = TextStyle(fontFamily = font, fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
    headlineLarge = TextStyle(fontFamily = font, fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
    headlineMedium = TextStyle(fontFamily = font, fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
    headlineSmall = TextStyle(fontFamily = font, fontSize = 21.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontFamily = font, fontSize = 19.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontFamily = font, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontFamily = font, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontFamily = font, fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = font, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = font, fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = font, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold),
    labelMedium = TextStyle(fontFamily = font, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold),
    labelSmall = TextStyle(fontFamily = font, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
)

private val pinedrawShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

/** Applies the Pinedraw colours, type scale and rounded shapes. */
@Composable
fun PinedrawTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = pinedrawScheme,
        typography = pinedrawTypography,
        shapes = pinedrawShapes,
        content = content
    )
}
