package com.example.pleosev.ui.design

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlin.math.min

/** How many real dp one design px is in the current window. */
val LocalDesignScale = compositionLocalOf { 1f }

/** The design's page size ("EV Companion.dc.html": 1600×900, scaled by `min(w / 1600, h / 900)`). */
const val DESIGN_WIDTH = 1600f
const val DESIGN_HEIGHT = 900f

/** Narrow windows (the 841 dp wide Pleos split window) stack the columns in a page this wide; phones use the compact one. */
const val NARROW_DESIGN_WIDTH = 800f
const val COMPACT_DESIGN_WIDTH = 560f

private val WideFrom = 1100.dp
private val CompactBelow = 600.dp

/** Pleos basic button "Small": nothing tappable may be smaller than this in real size. */
private const val MIN_TOUCH_REAL_DP = 64f

/**
 * Lays a screen out in the design's own pixel space.
 *
 * The Pleos display is 160 dpi, so the design's px are dp. A wide window gets the 1600×900 page, scaled
 * uniformly to fit and centred, exactly like the hand-off page's `fit()`; inside it the code uses the
 * design's numbers as they are, because [LocalDensity] is scaled. The backdrop fills the whole window.
 * A narrow window gets [narrow]: the same parts stacked in one column that scrolls unless [narrowScrolls]
 * is off (while driving nothing may scroll).
 *
 * Controls are drawn at the design's size; their touch area is widened invisibly to 64 real dp through
 * [ViewConfiguration.minimumTouchTargetSize].
 */
@Composable
fun EvDesignScreen(
    backdrop: EvBackdrop,
    narrowScrolls: Boolean = true,
    wide: @Composable BoxScope.() -> Unit,
    narrow: @Composable ColumnScope.(compact: Boolean) -> Unit,
) {
    Box(Modifier.fillMaxSize().evBackdrop(backdrop)) {
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            val base = LocalDensity.current
            val isWide = maxWidth >= WideFrom
            val compact = maxWidth < CompactBelow
            val scale = when {
                isWide -> min(maxWidth.value / DESIGN_WIDTH, maxHeight.value / DESIGN_HEIGHT)
                compact -> maxWidth.value / COMPACT_DESIGN_WIDTH
                else -> maxWidth.value / NARROW_DESIGN_WIDTH
            }.coerceAtLeast(0.1f)
            val density = remember(base, scale) { Density(base.density * scale, base.fontScale) }
            val viewConfiguration = LocalViewConfiguration.current
            val touch = remember(viewConfiguration, scale) {
                MinTouchViewConfiguration(viewConfiguration, (MIN_TOUCH_REAL_DP / scale).dp)
            }
            CompositionLocalProvider(
                LocalDensity provides density,
                LocalDesignScale provides scale,
                LocalViewConfiguration provides touch,
            ) {
                if (isWide) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Box(Modifier.requiredSize(DESIGN_WIDTH.dp, DESIGN_HEIGHT.dp), content = wide)
                    }
                } else {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .then(if (narrowScrolls) Modifier.verticalScroll(rememberScrollState()) else Modifier),
                    ) { narrow(compact) }
                }
            }
        }
    }
}

/** [side] real-size touch targets for everything clickable below it, without changing any layout. */
private class MinTouchViewConfiguration(base: ViewConfiguration, private val side: Dp) : ViewConfiguration by base {
    override val minimumTouchTargetSize: DpSize get() = DpSize(side, side)
}

/** [realDp] of the real display, expressed in the scaled design space (e.g. the 100 dp driving button). */
@Composable
fun realDp(realDp: Float): Dp = (realDp / LocalDesignScale.current).dp
