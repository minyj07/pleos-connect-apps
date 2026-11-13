package com.example.pleosev.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Colours of the EV Companion design (hand-off "EV Companion.dc.html"). Names say where the design uses
 * them; CSS `rgba()` values keep their alpha. [Red] and its companions are the only additions: the design
 * has no urgent state, so it reuses the amber alert's structure in a red tone.
 */
object EvColors {
    // Page
    val Outside = Color(0xFF05080C)
    val BackgroundInner = Color(0xFF111A23)
    val BackgroundOuter = Color(0xFF0B1118)
    val Ink = Color(0xFFFFFFFF)
    val OnGreen = Color(0xFF0B1118)

    // Greens
    val Green = Color(0xFF4ADE80)             // outline pills, accents, availability
    val GreenGlyph = Color(0xFF5BE36C)        // bolt in the charger icon, illustration glyph
    val GradientStart = Color(0xFF86EF7A)     // primary buttons, selected segment
    val GradientStartCard = Color(0xFF6EEA6E) // "안내" button on a station card
    val GradientEnd = Color(0xFF3EE05F)
    val GlowGreen = Color(0xFF22C55E)
    val GlowGreenDeep = Color(0xFF16A34A)
    val ArcStart = Color(0xFF22D34F)
    val ArcEnd = Color(0xFF8AF07A)
    val ArcLowStart = Color(0xFFF5B70A)
    val ArcLowEnd = Color(0xFFFDE68A)
    val ArcTrack = Color(0xFF262F38)
    val IllustrationStart = Color(0xFF1F5A32)
    val IllustrationEnd = Color(0xFF123B22)

    // Text
    val TextSub = Color(0xFFB7BEC7)           // subtitle, descriptions
    val TextSoft = Color(0xFFD2D7DD)          // range line, sort label
    val TextLabel = Color(0xFFD7DCE2)         // tile labels, chips, outline pills
    val TextHint = Color(0xFFC8CED5)          // tile sub lines
    val IconStroke = Color(0xFFE6E9ED)

    // Surfaces
    val Tile = Color(0xFF1C242E).copy(alpha = 0.72f)
    val TileBorder = Color.White.copy(alpha = 0.06f)
    val Panel = Color(0xFF161D25).copy(alpha = 0.82f)
    val PanelBorder = Color.White.copy(alpha = 0.05f)
    val PanelSolid = Color(0xFF161D25)
    val StationCard = Color(0xFF1A222B)
    val RoundButton = Color(0xFF1B232C)
    val RoundButtonPressed = Color(0xFF232D38)
    val Divider = Color.White.copy(alpha = 0.10f)
    val SegmentBorder = Color.White.copy(alpha = 0.14f)
    val SegmentDivider = Color.White.copy(alpha = 0.20f)
    val OutlineBorder = Color.White.copy(alpha = 0.20f)
    val Chip = Color.White.copy(alpha = 0.09f)
    val ToggleOn = Color(0xFF3EE05F)
    val ToggleOff = Color(0xFF3A444F)

    // Low-battery alert
    val Amber = Color(0xFFFCD34D)
    val AmberSub = Color(0xFFE6DCC0)
    val AmberBackground = Color(0xFF282210).copy(alpha = 0.96f)
    val AmberBorder = Color(0xFFFCD34D).copy(alpha = 0.55f)
    val OnAmber = Color(0xFF1A1505)

    // Urgent variant (not in the design)
    val Red = Color(0xFFF87171)
    val RedSub = Color(0xFFEBCFCF)
    val RedBackground = Color(0xFF2A1212).copy(alpha = 0.96f)
    val RedBorder = Color(0xFFF87171).copy(alpha = 0.55f)
    val OnRed = Color(0xFF1A0606)
}
