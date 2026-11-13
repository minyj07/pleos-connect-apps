package com.example.drivecoach.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.drivecoach.R

/*
 * Design tokens of "Drive Coach Composite" (Claude Design handoff). The Pleos display is 160 dpi, so the
 * design's CSS px are dp and its font px are sp. Both `[data-theme]` sets are copied value by value.
 */

private fun rgba(r: Int, g: Int, b: Int, a: Float) = Color(r / 255f, g / 255f, b / 255f, a)

/** One `[data-theme]` block of the design. */
@Immutable
data class CoachPalette(
    val dark: Boolean,
    val bg: Color,
    val card: Color,
    val cardBd: Color,
    val tile: Color,
    val surface: Color,
    val ink: Color,
    val ink1: Color,
    val ink2: Color,
    val ink3: Color,
    val ink4: Color,
    val line: Color,
    val track: Color,
    val hair: Color,
    val hair2: Color,
    val tint: Color,
    val off: Color,
    val car2: Color,
    val acc: Color,
    val accSoft: Color,
    val accArea: Color,
    /** Opacity of the background glow layer. */
    val glow: Float,
)

val LightPalette = CoachPalette(
    dark = false,
    bg = Color(0xFFF6F6F6),
    card = rgba(255, 255, 255, 0.6f),
    cardBd = rgba(255, 255, 255, 0.9f),
    tile = Color(0xFFF6F6F6),
    surface = Color(0xFFFFFFFF),
    ink = Color(0xFF111111),
    ink1 = Color(0xFF3A3A3A),
    ink2 = Color(0xFF555555),
    ink3 = Color(0xFF666666),
    ink4 = Color(0xFF8A8A8A),
    line = Color(0xFFE3E3E3),
    track = Color(0xFFECECEC),
    hair = rgba(0, 0, 0, 0.07f),
    hair2 = rgba(0, 0, 0, 0.14f),
    tint = Color(0xFFE4ECFB),
    off = Color(0xFFCFCFCF),
    car2 = Color(0xFFC9D0DB),
    acc = Color(0xFF1F5FD1),
    accSoft = rgba(31, 95, 209, 0.14f),
    accArea = rgba(31, 95, 209, 0.08f),
    glow = 1f,
)

val DarkPalette = CoachPalette(
    dark = true,
    bg = Color(0xFF0E1013),
    card = Color(0xFF181B20),
    cardBd = rgba(255, 255, 255, 0.05f),
    tile = Color(0xFF20242A),
    surface = Color(0xFF2A2F36),
    ink = Color(0xFFEEF0F3),
    ink1 = Color(0xFFC9CDD4),
    ink2 = Color(0xFF9DA3AD),
    ink3 = Color(0xFF868C97),
    ink4 = Color(0xFF646A74),
    line = Color(0xFF30353D),
    track = Color(0xFF2A2E35),
    hair = rgba(255, 255, 255, 0.06f),
    hair2 = rgba(255, 255, 255, 0.12f),
    tint = rgba(80, 130, 245, 0.16f),
    off = Color(0xFF444A53),
    car2 = Color(0xFF343A44),
    acc = Color(0xFF3F78F0),
    accSoft = rgba(80, 130, 245, 0.16f),
    accArea = rgba(80, 130, 245, 0.12f),
    glow = 0.45f,
)

/** Colours the design keeps the same in both themes. */
object CoachColors {
    /** 급가속·급출발, the planned path, caution. */
    val Amber = Color(0xFFE0A21B)

    /** 급감속·급정지, braking, delete and other destructive actions. */
    val Red = Color(0xFFD9483B)

    /** 가속 직후 제동. */
    val Purple = Color(0xFF8B6BD9)

    val ScoreGood = Color(0xFF2E9E5B)
    val ScoreMid = Color(0xFFD99A0B)
    val ScoreBad = Color(0xFFD9483B)

    val Chevron = Color(0xFF999999)
    val InfoBadge = Color(0xFF777777)
    val White = Color(0xFFFFFFFF)

    val DangerBorder = rgba(217, 72, 59, 0.45f)
    val DangerSoft = rgba(217, 72, 59, 0.10f)
    val BrakeBand = rgba(217, 72, 59, 0.10f)
    val BrakeSwatch = rgba(217, 72, 59, 0.35f)

    val Wall = Color(0xFFC3C9D3)
    val SlotLine = Color(0xFF9AA3B2)
    val TargetSlot = rgba(31, 95, 209, 0.10f)
    val CarWindow = rgba(255, 255, 255, 0.35f)

    val KnobShadow = rgba(0, 0, 0, 0.25f)
    val ThumbShadow = rgba(0, 0, 0, 0.20f)

    /** The three radial gradients of the glow layer (start colours; each fades to transparent). */
    val GlowA = rgba(31, 95, 209, 0.11f)
    val GlowB = rgba(90, 150, 240, 0.09f)
    val GlowC = rgba(160, 195, 250, 0.06f)
}

val LocalCoachPalette = staticCompositionLocalOf { LightPalette }

object CoachTheme {
    val colors: CoachPalette
        @Composable
        @ReadOnlyComposable
        get() = LocalCoachPalette.current
}

// ---- type ----

private fun notoSansKr(weight: Int) = Font(
    resId = R.font.notosanskr_vf,
    weight = FontWeight(weight),
    // The file's default instance is Thin (wght 100), so every weight must name its axis value.
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** Noto Sans KR (SIL OFL 1.1, bundled variable font) at the three weights the design uses. */
val NotoSansKr = FontFamily(notoSansKr(400), notoSansKr(500), notoSansKr(700))

/**
 * CSS `line-height: normal` of the web font the design was drawn with (Google Fonts Noto Sans KR:
 * ascent 1.16 em + descent 0.288 em). Texts without an explicit line-height in the design use it.
 */
const val CssNormalLineHeight = 1.448f

private const val WEB_ASCENT = 1.16f
private const val WEB_DESCENT = 0.288f

// hhea metrics of the bundled file (res/font/notosanskr_vf.ttf), which Android uses for line metrics.
private const val FONT_ASCENT = 1.0f
private const val FONT_DESCENT = 0.2f

/**
 * Where the baseline goes inside a line of [lineHeight] em so it sits where a browser puts it: CSS centres
 * the web font's content area in the line box; Android would centre this file's (smaller) one instead.
 */
private fun cssAlignment(lineHeight: Float): LineHeightStyle.Alignment {
    val targetDescent = (lineHeight - (WEB_ASCENT + WEB_DESCENT)) / 2f + WEB_DESCENT
    val extra = lineHeight - (FONT_ASCENT + FONT_DESCENT)
    val topRatio = when {
        extra > 0.001f -> 1f - (targetDescent - FONT_DESCENT) / extra
        extra < -0.001f -> (targetDescent - FONT_DESCENT) / extra
        else -> 0.5f
    }
    return LineHeightStyle.Alignment(topRatio.coerceIn(0f, 1f))
}

/**
 * Text style from the design's numbers: font size in px (= sp), weight, CSS line-height as a multiple of the
 * font size and letter-spacing in px.
 *
 * Lines are filled greedily ([LineBreak.Simple]), like a browser. The design's `text-wrap: pretty` only
 * repairs a lone last word; Android's paragraph strategy would rebalance every line and move more breaks
 * away from the design than it fixes.
 */
fun coachTextStyle(
    size: Float,
    weight: FontWeight = FontWeight.W400,
    lineHeight: Float = CssNormalLineHeight,
    letterSpacing: Float = 0f,
    tabular: Boolean = false,
    family: FontFamily = NotoSansKr,
): TextStyle = TextStyle(
    fontFamily = family,
    fontSize = size.sp,
    fontWeight = weight,
    lineHeight = (size * lineHeight).sp,
    letterSpacing = letterSpacing.sp,
    fontFeatureSettings = if (tabular) "tnum" else null,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(alignment = cssAlignment(lineHeight), trim = LineHeightStyle.Trim.None),
    lineBreak = LineBreak.Simple,
)

// ---- theme ----

/** Pleos "Small" control height: no tappable element gets a smaller hit area. */
val MinTouchTarget = 64.dp

/**
 * Controls are drawn at the design's size; this makes Compose accept touches up to [MinTouchTarget] around
 * the smaller ones (the same mechanism as the 48 dp phone default), without changing what is drawn.
 */
private class CarViewConfiguration(base: ViewConfiguration) : ViewConfiguration by base {
    override val minimumTouchTargetSize: DpSize get() = DpSize(MinTouchTarget, MinTouchTarget)
}

/** Re-applies the car touch target inside windows of their own (dialogs), which reset the view configuration. */
@Composable
fun ProvideCarTouchTargets(content: @Composable () -> Unit) {
    val base = LocalViewConfiguration.current
    val car = remember(base) { base as? CarViewConfiguration ?: CarViewConfiguration(base) }
    CompositionLocalProvider(LocalViewConfiguration provides car, content = content)
}

// Material is only used for what the design does not draw itself (ripples, progress rings, the dialog scrim).
private fun materialScheme(p: CoachPalette) = if (p.dark) {
    darkColorScheme(
        primary = p.acc,
        onPrimary = CoachColors.White,
        secondary = p.acc,
        onSecondary = CoachColors.White,
        background = p.bg,
        onBackground = p.ink,
        surface = p.card,
        onSurface = p.ink,
        surfaceVariant = p.tile,
        onSurfaceVariant = p.ink2,
        surfaceContainer = p.card,
        surfaceContainerHigh = p.surface,
        surfaceContainerHighest = p.surface,
        outline = p.line,
        outlineVariant = p.line,
        error = CoachColors.Red,
        onError = CoachColors.White,
    )
} else {
    lightColorScheme(
        primary = p.acc,
        onPrimary = CoachColors.White,
        secondary = p.acc,
        onSecondary = CoachColors.White,
        background = p.bg,
        onBackground = p.ink,
        surface = p.surface,
        onSurface = p.ink,
        surfaceVariant = p.tile,
        onSurfaceVariant = p.ink2,
        surfaceContainer = p.surface,
        surfaceContainerHigh = p.surface,
        surfaceContainerHighest = p.tile,
        outline = p.line,
        outlineVariant = p.line,
        error = CoachColors.Red,
        onError = CoachColors.White,
    )
}

/** The whole app follows [dark] (설정 > 화면 > 다크 모드) at once. */
@Composable
fun DriveCoachTheme(dark: Boolean, content: @Composable () -> Unit) {
    val palette = if (dark) DarkPalette else LightPalette
    MaterialTheme(colorScheme = remember(palette) { materialScheme(palette) }) {
        CompositionLocalProvider(
            LocalCoachPalette provides palette,
            LocalContentColor provides palette.ink,
            LocalTextStyle provides coachTextStyle(20f),
            LocalMinimumInteractiveComponentSize provides MinTouchTarget,
        ) {
            ProvideCarTouchTargets(content)
        }
    }
}
