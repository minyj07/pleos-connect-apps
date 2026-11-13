package com.example.drivecoach.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Pleos Connect shows an app either across the whole app area (landscape window, about 1530 dp of content
 * next to the tab rail) or in one of two windows side by side (portrait window, about 690 dp), and the user
 * can switch between them at any time. [LocalWideLayout] says which one the content area currently is;
 * the root provides it from the measured width. The design is drawn for the wide window.
 */
val LocalWideLayout = staticCompositionLocalOf { true }

/** Content width from which cards sit side by side and the share page puts its QR next to the text. */
val WideLayoutMinWidth: Dp = 1100.dp

class CardRowScope internal constructor() {
    internal val cards = mutableListOf<Pair<Float, @Composable (Modifier) -> Unit>>()

    /** One card: [weight] is its `fr` share of the row when side by side; it takes the full width when stacked. */
    fun card(weight: Float = 1f, content: @Composable (Modifier) -> Unit) {
        cards += weight to content
    }
}

/**
 * One row of the design's CSS grids: cards side by side with equal heights in a wide (landscape) window,
 * stacked in a narrow (portrait) one. Card content must support intrinsic measurement (no
 * BoxWithConstraints or lazy lists inside).
 */
@Composable
fun CardRow(modifier: Modifier = Modifier, spacing: Dp = 24.dp, content: CardRowScope.() -> Unit) {
    val cards = CardRowScope().apply(content).cards
    if (LocalWideLayout.current) {
        Row(modifier.height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(spacing)) {
            cards.forEach { (weight, card) -> card(Modifier.weight(weight).fillMaxHeight()) }
        }
    } else {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(spacing)) {
            cards.forEach { (_, card) -> card(Modifier.fillMaxWidth()) }
        }
    }
}
