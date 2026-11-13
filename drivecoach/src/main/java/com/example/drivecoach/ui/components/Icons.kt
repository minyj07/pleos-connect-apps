package com.example.drivecoach.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import com.example.drivecoach.ui.theme.CoachColors

/**
 * One `<path>` of a design icon, kept as the SVG path data. [stroke] is the SVG stroke-width in viewBox
 * units (null = filled); [cap] and [join] are the SVG's stroke-linecap / stroke-linejoin.
 */
class IconPart(
    val d: String,
    val stroke: Float? = null,
    val cap: StrokeCap = StrokeCap.Round,
    val join: StrokeJoin = StrokeJoin.Round,
) {
    val path: Path by lazy(LazyThreadSafetyMode.NONE) { PathParser().parsePathString(d).toPath() }
}

/** An icon of the design: its SVG paths in a square [viewBox] (24 unless noted). */
class CoachIcon(val parts: List<IconPart>, val viewBox: Float = 24f) {
    constructor(vararg parts: IconPart) : this(parts.toList())
}

private fun fill(d: String) = IconPart(d)

private fun line(d: String, width: Float, join: StrokeJoin = StrokeJoin.Round) = IconPart(d, width, StrokeCap.Round, join)

/** Path data copied from "Drive Coach Composite.dc.html"; do not replace with Material icons. */
object CoachIcons {
    // Navigation rail (36 dp)
    val Home = CoachIcon(fill("M12 3 2 12h3v8h5v-6h4v6h5v-8h3z"))
    val Trips = CoachIcon(line("M3 17l5-6 4 4 4-6 5 4", 2.4f))
    val Habits = CoachIcon(line("M3 18l5-6 4 3 5-7 4 3", 2.4f), line("M17 3v3M15.5 4.5h3M5 6v2M4 7h2", 2.4f))
    val Settings = CoachIcon(
        fill(
            "M19.4 13a7.5 7.5 0 0 0 0-2l2.1-1.6-2-3.5-2.5 1a7 7 0 0 0-1.7-1L15 3h-4l-.4 2.7a7 7 0 0 0-1.7 1l-2.5-1-2 3.5L6.6 11" +
                "a7.5 7.5 0 0 0 0 2l-2.1 1.6 2 3.5 2.5-1a7 7 0 0 0 1.7 1L11 21h4l.4-2.7a7 7 0 0 0 1.7-1l2.5 1 2-3.5z" +
                "M13 15.5a3.5 3.5 0 1 1 0-7 3.5 3.5 0 0 1 0 7z",
        ),
    )

    // Shared
    val Check = CoachIcon(line("M4 12l5 5L20 6", 3f))
    val Qr = CoachIcon(fill("M3 3h7v7H3zM14 3h7v7h-7zM3 14h7v7H3zM14 14h3v3h-3zM18 18h3v3h-3zM14 19h2v2h-2zM19 14h2v2h-2z"))
    val Trash = CoachIcon(line("M4 7h16M10 11v6M14 11v6M6 7l1 13h10l1-13M9 7V4h6v3", 2.4f))
    val Back = CoachIcon(line("M15 5l-7 7 7 7", 2.6f))
    val Chevron = CoachIcon(line("M9 5l7 7-7 7", 2.6f))
    val Minus = CoachIcon(line("M5 12h14", 2.6f, StrokeJoin.Miter))
    val Plus = CoachIcon(line("M5 12h14M12 5v14", 2.6f, StrokeJoin.Miter))

    // Parking transport
    val Prev = CoachIcon(fill("M6 5h2.5v14H6zM19 5v14l-9-7z"))
    val Next = CoachIcon(fill("M15.5 5H18v14h-2.5zM5 5v14l9-7z"))
    val Play = CoachIcon(fill("M7 4l13 8-13 8z"))
    val Pause = CoachIcon(fill("M6 5h4v14H6zM14 5h4v14h-4z"))
    val Reset = CoachIcon(line("M4 12a8 8 0 1 0 2.3-5.7", 2.6f), line("M4 4v5h5", 2.6f))

    // Coaching tiles
    val Bolt = CoachIcon(fill("M13 2 4 14h7l-1 8 9-12h-7z"))
    val Start = CoachIcon(fill("M6 4l14 8-14 8z"))
    val Bars = CoachIcon(fill("M5 12h3v8H5zM10.5 6h3v14h-3zM16 9h3v11h-3z"))
    val Refresh = CoachIcon(line("M20 12a8 8 0 1 1-2.3-5.7", 2.6f), line("M20 4v5h-5", 2.6f))

    /** The mark inside the design's info badge, and its "!" counterpart for the error state the design has no screen for. */
    val InfoMark = CoachIcon(line("M12 10.5v6M12 7v.5", 2.4f, StrokeJoin.Miter))
    val AlertMark = CoachIcon(line("M12 7v6.5M12 16.5v.5", 2.4f, StrokeJoin.Miter))
}

/** Draws [icon] scaled to [sidePx] with its top-left corner at [topLeft]. */
fun DrawScope.drawCoachIcon(icon: CoachIcon, tint: Color, sidePx: Float, topLeft: Offset = Offset.Zero) {
    val k = sidePx / icon.viewBox
    withTransform({
        translate(topLeft.x, topLeft.y)
        scale(k, k, pivot = Offset.Zero)
    }) {
        icon.parts.forEach { part ->
            val width = part.stroke
            if (width == null) {
                drawPath(part.path, tint)
            } else {
                drawPath(part.path, tint, style = Stroke(width, cap = part.cap, join = part.join))
            }
        }
    }
}

/** A design icon at [size] in one colour (the SVG's `currentColor` / `fill` / `stroke`). */
@Composable
fun CoachIconView(icon: CoachIcon, size: Dp, tint: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(size).drawBehind { drawCoachIcon(icon, tint, this.size.minDimension) })
}

/** The grey "i" badge of the home screen's notes: `circle r=10 fill #777` with a white mark. */
@Composable
fun InfoBadge(size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).drawBehind {
            val k = this.size.minDimension / 24f
            drawCircle(CoachColors.InfoBadge, radius = 10f * k, center = Offset(12f * k, 12f * k))
            drawCoachIcon(CoachIcons.InfoMark, CoachColors.White, this.size.minDimension)
        },
    )
}
