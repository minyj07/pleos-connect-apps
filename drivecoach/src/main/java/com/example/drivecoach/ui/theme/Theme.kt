package com.example.drivecoach.ui.theme

import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * In-vehicle UI is always dark, high-contrast and large (driver-distraction guideline:
 * large touch targets, high contrast, minimal text). Same base as EV Companion.
 */
// Every role is set: Material fills the unset ones from its purple baseline, which showed up in
// component defaults (slider inactive track = secondaryContainer, switch track, dialog background).
private val CoachDarkColors = darkColorScheme(
    primary = Color(0xFF5BC0FF), // coach blue
    onPrimary = Color(0xFF002235),
    primaryContainer = Color(0xFF0E3A55),
    onPrimaryContainer = Color(0xFFCFEAFF),
    inversePrimary = Color(0xFF006590),
    secondary = Color(0xFF4CD964), // smooth / good
    onSecondary = Color(0xFF00210A),
    secondaryContainer = Color(0xFF2A3542), // neutral blue-grey: Material uses it for tonal fills
    onSecondaryContainer = Color(0xFFDCE4EE),
    tertiary = Color(0xFFFFC857), // caution amber (reverse gear, braking)
    onTertiary = Color(0xFF2B1D00),
    tertiaryContainer = Color(0xFF4A3A12),
    onTertiaryContainer = Color(0xFFFFE2A6),
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF3B0A0C),
    errorContainer = Color(0xFF5A1F22),
    onErrorContainer = Color(0xFFFFDAD7),
    background = Color(0xFF0B0F14),
    onBackground = Color(0xFFF2F4F7),
    surface = Color(0xFF151B23),
    onSurface = Color(0xFFF2F4F7),
    surfaceVariant = Color(0xFF1F2730),
    onSurfaceVariant = Color(0xFFB8C0CC),
    surfaceDim = Color(0xFF0B0F14),
    surfaceBright = Color(0xFF2A333E),
    surfaceContainerLowest = Color(0xFF080B10),
    surfaceContainerLow = Color(0xFF11161D),
    surfaceContainer = Color(0xFF151B23),
    surfaceContainerHigh = Color(0xFF1C242E),
    surfaceContainerHighest = Color(0xFF26303B),
    inverseSurface = Color(0xFFE6E9EE),
    inverseOnSurface = Color(0xFF1B222B),
    outline = Color(0xFF3A4654),
    outlineVariant = Color(0xFF2A333E),
)

private val CoachTypography = Typography(
    displayLarge = TextStyle(fontSize = 64.sp, fontWeight = FontWeight.Bold, lineHeight = 72.sp),
    displaySmall = TextStyle(fontSize = 44.sp, fontWeight = FontWeight.Bold, lineHeight = 52.sp),
    headlineMedium = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.SemiBold, lineHeight = 40.sp),
    titleLarge = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold, lineHeight = 32.sp),
    titleMedium = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Medium, lineHeight = 28.sp),
    bodyLarge = TextStyle(fontSize = 20.sp, lineHeight = 28.sp),
    bodyMedium = TextStyle(fontSize = 18.sp, lineHeight = 26.sp),
    bodySmall = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    labelLarge = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Medium, lineHeight = 24.sp),
    labelMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp),
)

/** Colours with a meaning across screens (charts, events, gears). */
object CoachColors {
    val Drive = Color(0xFF5BC0FF)
    val Reverse = Color(0xFFFFC857)
    val Brake = Color(0xFFFF6B6B)
    val Coast = Color(0xFF4CD964)
    val Gentle = Color(0xFF4CD964)
    val Moderate = Color(0xFFFFC857)
    val Hard = Color(0xFFFF6B6B)
    val Asphalt = Color(0xFF1B222B)
    val Lines = Color(0xFFE6E9EE)
    val ParkedCar = Color(0xFF55606E)
}

/**
 * Touch sizes from the Pleos Connect basic button spec (heights Large 100 / Medium 80 / Small 64 at
 * 160 dpi, corner radius 32 / 24 / 16): nothing tappable is smaller than [Small], and neighbouring
 * targets stay at least [Gap] apart.
 */
object CoachSizes {
    val Large = 100.dp
    val Medium = 80.dp
    val Small = 64.dp
    val Gap = 16.dp
    val Icon = 40.dp
}

@Composable
fun DriveCoachTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = CoachDarkColors, typography = CoachTypography) {
        // Material's 48 dp touch floor is a phone value; in the car every control gets at least Pleos "Small".
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides CoachSizes.Small, content = content)
    }
}
