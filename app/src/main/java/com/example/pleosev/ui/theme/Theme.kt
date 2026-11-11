package com.example.pleosev.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * In-vehicle UI is always dark, high-contrast and large (driver-distraction guideline:
 * large touch targets, high contrast, minimal text).
 */
private val EvDarkColors = darkColorScheme(
    primary = Color(0xFF4CD964),          // charge green
    onPrimary = Color(0xFF00210A),
    primaryContainer = Color(0xFF245035), // selected choice (tonal green)
    onPrimaryContainer = Color(0xFFB7F0C1),
    secondary = Color(0xFF7DB7FF),        // info blue
    onSecondary = Color(0xFF00213F),
    tertiary = Color(0xFFFFC857),         // warning amber
    error = Color(0xFFFF5C5C),
    background = Color(0xFF0B0F14),
    onBackground = Color(0xFFF2F4F7),
    surface = Color(0xFF151B23),
    onSurface = Color(0xFFF2F4F7),
    surfaceVariant = Color(0xFF1F2730),
    onSurfaceVariant = Color(0xFFB8C0CC),
    // Container roles on the same blue-grey ramp; left unset, M3 parts such as dialogs use its purple-tinted greys.
    surfaceContainerLowest = Color(0xFF0B0F14),
    surfaceContainerLow = Color(0xFF10151B),
    surfaceContainer = Color(0xFF181F28),
    surfaceContainerHigh = Color(0xFF1F2730), // dialogs
    surfaceContainerHighest = Color(0xFF29323D),
    outline = Color(0xFF3A4654),
)

private val EvTypography = Typography(
    displayLarge = TextStyle(fontSize = 64.sp, fontWeight = FontWeight.Bold, lineHeight = 72.sp),
    headlineMedium = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.SemiBold, lineHeight = 40.sp),
    titleLarge = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold, lineHeight = 32.sp),
    titleMedium = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Medium, lineHeight = 28.sp),
    bodyLarge = TextStyle(fontSize = 20.sp, lineHeight = 28.sp),
    bodyMedium = TextStyle(fontSize = 18.sp, lineHeight = 26.sp),
    labelLarge = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Medium, lineHeight = 24.sp),
)

@Composable
fun PleosEvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = EvDarkColors,
        typography = EvTypography,
        content = content,
    )
}
