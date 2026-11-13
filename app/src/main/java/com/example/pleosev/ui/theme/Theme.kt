package com.example.pleosev.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Material roles mapped onto the design's palette ([EvColors]), so the few Material parts the app still
 * uses (dialogs, menus, progress indicators, ripples) match the screens, which are drawn from the design's
 * own tokens. Every role M3 would otherwise fill with its purple-tinted baseline is set here.
 */
private val EvDarkColors = darkColorScheme(
    primary = EvColors.Green,
    onPrimary = EvColors.OnGreen,
    primaryContainer = EvColors.IllustrationStart,
    onPrimaryContainer = EvColors.GradientStart,
    inversePrimary = EvColors.GlowGreenDeep,
    secondary = EvColors.GradientEnd,
    onSecondary = EvColors.OnGreen,
    secondaryContainer = EvColors.IllustrationEnd,
    onSecondaryContainer = EvColors.GradientStart,
    tertiary = EvColors.Amber,
    onTertiary = EvColors.OnAmber,
    tertiaryContainer = Color(0xFF282210),
    onTertiaryContainer = EvColors.AmberSub,
    error = EvColors.Red,
    onError = EvColors.OnRed,
    errorContainer = Color(0xFF2A1212),
    onErrorContainer = EvColors.RedSub,
    background = EvColors.BackgroundOuter,
    onBackground = EvColors.Ink,
    surface = EvColors.PanelSolid,
    onSurface = EvColors.Ink,
    surfaceVariant = EvColors.StationCard,
    onSurfaceVariant = EvColors.TextSub,
    surfaceTint = EvColors.Green,
    inverseSurface = EvColors.TextLabel,
    inverseOnSurface = EvColors.OnGreen,
    surfaceDim = EvColors.Outside,
    surfaceBright = EvColors.ArcTrack,
    surfaceContainerLowest = EvColors.Outside,
    surfaceContainerLow = EvColors.BackgroundOuter,
    surfaceContainer = EvColors.PanelSolid,
    surfaceContainerHigh = EvColors.StationCard,
    surfaceContainerHighest = EvColors.RoundButton,
    outline = Color(0xFF39424C),         // white 20 % over the page
    outlineVariant = Color(0xFF232B34),  // white 10 % over the page
    scrim = Color.Black,
    // The "fixed" accents too, so no role keeps M3's baseline purple.
    primaryFixed = EvColors.GradientStart,
    primaryFixedDim = EvColors.Green,
    onPrimaryFixed = EvColors.OnGreen,
    onPrimaryFixedVariant = EvColors.IllustrationEnd,
    secondaryFixed = EvColors.GradientStartCard,
    secondaryFixedDim = EvColors.GradientEnd,
    onSecondaryFixed = EvColors.OnGreen,
    onSecondaryFixedVariant = EvColors.IllustrationEnd,
    tertiaryFixed = EvColors.ArcLowEnd,
    tertiaryFixedDim = EvColors.Amber,
    onTertiaryFixed = EvColors.OnAmber,
    onTertiaryFixedVariant = Color(0xFF282210),
)

private val EvTypography = Typography(
    displayLarge = evText(100, EvWeight.ExtraBold, lineHeight = 1f, letterSpacingEm = -0.02f),
    headlineLarge = evText(46, EvWeight.Bold, letterSpacingEm = -0.01f),
    headlineMedium = evText(36, EvWeight.Bold, letterSpacingEm = -0.01f),
    headlineSmall = evText(31, EvWeight.Bold),
    titleLarge = evText(27, EvWeight.Bold),
    titleMedium = evText(23, EvWeight.Bold),
    titleSmall = evText(21, EvWeight.SemiBold),
    bodyLarge = evText(20),
    bodyMedium = evText(19),
    bodySmall = evText(18),
    labelLarge = evText(19, EvWeight.SemiBold),
    labelMedium = evText(17),
    labelSmall = evText(17),
)

@Composable
fun PleosEvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = EvDarkColors,
        typography = EvTypography,
        content = content,
    )
}
