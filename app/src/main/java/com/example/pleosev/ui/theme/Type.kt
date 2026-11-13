package com.example.pleosev.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.pleosev.R

/**
 * The one typeface of the app: the design's "Pretendard Variable" (res/font/pretendard_variable.ttf, SIL OFL,
 * licence in assets/licenses). One entry per weight the design uses, each pinning the variable font's
 * weight axis; every text style goes through [evText], so this is the only place that names the font.
 */
val EvFontFamily: FontFamily = FontFamily(
    pretendard(400),
    pretendard(500),
    pretendard(600),
    pretendard(700),
    pretendard(800),
)

@OptIn(ExperimentalTextApi::class)
private fun pretendard(weight: Int): Font = Font(
    resId = R.font.pretendard_variable,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** CSS `line-height: normal` of Pretendard (ascender 0.952 + descender 0.241). */
const val NORMAL_LINE_HEIGHT = 1.193f

/**
 * A text style in the design's terms: size in design px (= sp inside the scaled design space), weight,
 * colour, CSS line-height as a multiple of the size, letter-spacing in em, tabular numerals.
 */
fun evText(
    size: Int,
    weight: FontWeight = FontWeight.Normal,
    color: Color = EvColors.Ink,
    lineHeight: Float = NORMAL_LINE_HEIGHT,
    letterSpacingEm: Float = 0f,
    tabular: Boolean = false,
): TextStyle = TextStyle(
    fontFamily = EvFontFamily,
    fontSize = size.sp,
    fontWeight = weight,
    color = color,
    lineHeight = (size * lineHeight).sp,
    letterSpacing = if (letterSpacingEm == 0f) TextUnit.Unspecified else letterSpacingEm.em,
    fontFeatureSettings = if (tabular) "tnum" else null,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

/** The design's weights by their CSS numbers. */
object EvWeight {
    val Regular = FontWeight.W400
    val Medium = FontWeight.W500
    val SemiBold = FontWeight.W600
    val Bold = FontWeight.W700
    val ExtraBold = FontWeight.W800
}
