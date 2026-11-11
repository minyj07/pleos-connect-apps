package com.example.drivecoach.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Pleos Connect shows an app either across the whole app area (landscape window, about 1550 dp of content
 * next to the tab rail) or in one of two windows side by side (portrait window, about 710 dp), and the user
 * can switch between them at any time. [LocalWideLayout] says which one the content area currently is;
 * the root provides it from the measured width.
 */
val LocalWideLayout = staticCompositionLocalOf { true }

/** Content width from which cards sit side by side and the share page puts its QR next to the text. */
val WideLayoutMinWidth: Dp = 1100.dp

class CardRowScope internal constructor() {
    internal val cards = mutableListOf<Pair<Float, @Composable (Modifier) -> Unit>>()

    /** One card: [weight] of the row when side by side, the full width when stacked. */
    fun card(weight: Float = 1f, content: @Composable (Modifier) -> Unit) {
        cards += weight to content
    }
}

/** Cards side by side in a wide (landscape) window, stacked in a narrow (portrait) one. */
@Composable
fun CardRow(modifier: Modifier = Modifier, spacing: Dp = 20.dp, content: CardRowScope.() -> Unit) {
    val cards = CardRowScope().apply(content).cards
    if (LocalWideLayout.current) {
        Row(modifier, horizontalArrangement = Arrangement.spacedBy(spacing)) {
            cards.forEach { (weight, card) -> card(Modifier.weight(weight)) }
        }
    } else {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(spacing)) {
            cards.forEach { (_, card) -> card(Modifier.fillMaxWidth()) }
        }
    }
}
