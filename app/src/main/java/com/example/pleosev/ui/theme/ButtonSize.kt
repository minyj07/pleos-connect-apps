package com.example.pleosev.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Button heights from the Pleos Connect UI Design Guide (basic button Large / Medium / Small = 100 / 80 / 64 px).
 * The Pleos display is 160 dpi, so the guide's px are dp. Nothing tappable is smaller than [Small].
 */
object ButtonSize {
    /** The one main action of a card while driving. */
    val Large = 100.dp
    /** Regular actions, and every button that stays on screen while driving. */
    val Medium = 80.dp
    /** Secondary actions and choices that only appear while parked. */
    val Small = 64.dp
    /** Icon inside Large / Medium buttons (the guide's 36 px). */
    val Icon = 36.dp
}
