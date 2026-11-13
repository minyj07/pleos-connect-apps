package com.example.pleosev.ui.design

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.pleosev.ui.theme.EvColors

/**
 * The design's icons, built from the SVG path data of "EV Companion.dc.html" with the same view boxes,
 * stroke widths, caps and joins. Single-colour icons are white and take their colour from [EvIcon]'s tint;
 * the charger and battery icons carry the design's two colours.
 */
object EvIcons {
    // Header (home)
    val Bluetooth = icon { stroke("m7 7 10 10-5 5V2l5 5L7 17", 2f) }
    val Wifi = icon {
        stroke("M2 8.8a15 15 0 0 1 20 0", 2.2f)
        stroke("M5 12.6a10 10 0 0 1 14 0", 2.2f)
        stroke("M8.5 16.4a5 5 0 0 1 7 0", 2.2f)
        fillAndStroke(circle(12f, 20f, 1f), 2.2f)
    }

    /** Connection pills; view box 26×22. */
    val Car = icon(26f, 22f) {
        fill(
            "M5 2.5C5.4 1.3 6.5.5 7.8.5h10.4c1.3 0 2.4.8 2.8 2L23 8h1a1 1 0 0 1 1 1v1.5l-1 .6V19a1.5 1.5 0 0 1-1.5 1.5h-1.5" +
                "A1.5 1.5 0 0 1 19.5 19v-1.5h-13V19A1.5 1.5 0 0 1 5 20.5H3.5A1.5 1.5 0 0 1 2 19v-7.9l-1-.6V9a1 1 0 0 1 1-1h1z" +
                "M7.2 3.2 5.6 8h14.8l-1.6-4.8a1 1 0 0 0-1-.7H8.2a1 1 0 0 0-1 .7z" +
                "M6 14.5a1.6 1.6 0 1 0 0-3.2 1.6 1.6 0 0 0 0 3.2zm14 0a1.6 1.6 0 1 0 0-3.2 1.6 1.6 0 0 0 0 3.2z",
        )
    }

    // Home
    /** "충전 도우미" button; view box 32, dark body with a green bolt. */
    val Charger = icon(32f, 32f) {
        fill(roundRect(3f, 3f, 15f, 26f, 2.5f), color = EvColors.OnGreen)
        fill("M12 8l-4.5 8H11l-1.5 7L15 14h-3.5z", color = EvColors.GreenGlyph)
        stroke("M18 11h3.5a2 2 0 0 1 2 2v9a2 2 0 0 0 4 0V12l-3-4", 2.4f, color = EvColors.OnGreen)
    }

    /** The same charger for dark tiles (station thumbnail): green body, dark bolt. */
    val ChargerGlyph = icon(32f, 32f) {
        fill(roundRect(3f, 3f, 15f, 26f, 2.5f), color = EvColors.GreenGlyph)
        fill("M12 8l-4.5 8H11l-1.5 7L15 14h-3.5z", color = EvColors.IllustrationEnd)
        stroke("M18 11h3.5a2 2 0 0 1 2 2v9a2 2 0 0 0 4 0V12l-3-4", 2.4f, color = EvColors.GreenGlyph)
    }
    val ArrowRight = icon { stroke("M4 12h16M14 6l6 6-6 6", 2.2f) }
    val ArrowLeft = icon { stroke("M20 12H4M10 6l-6 6 6 6", 2.4f) }

    /** "배터리" tile: green battery with a dark bolt. */
    val BatteryFilled = icon {
        fill(roundRect(6f, 3.5f, 12f, 19f, 2.5f), color = EvColors.Green)
        fill(roundRect(9.5f, 1.5f, 5f, 3f, 1f), color = EvColors.Green)
        fill("M13 8l-3.5 5.5H12L11 18l3.5-5.5H12z", color = EvColors.OnGreen)
    }
    val BoltFilled = icon { fill("M14 1.5 4.5 14H11l-1.5 8.5L19.5 10H13z") }
    val Leaf = icon {
        stroke("M11 20A7 7 0 0 1 9.8 6.1C15.5 5 17 4.5 19 2c1 2 2 4.2 2 8 0 5.5-4.8 10-10 10ZM2 21c0-3 1.9-5.4 5.2-6.1 2.4-.5 4.9-2 5.8-3.9", 2f)
    }
    val Route = icon { stroke("M6 19V7a3 3 0 0 1 6 0v10a3 3 0 0 0 6 0V5M6 19h.01M18 5h.01", 2f) }
    val Timer = icon { stroke("M10 2h4M12 14l3-3M12 22a8 8 0 1 0 0-16 8 8 0 0 0 0 16z", 2f) }
    val Gauge = icon { stroke("M12 14l4-4M3.3 19a10 10 0 1 1 17.4 0z", 2f) }
    val CarOutline = icon {
        stroke("M5 17h14M5 17v2M19 17v2M3 13l2-6a2 2 0 0 1 2-1.5h10A2 2 0 0 1 19 7l2 6v4H3zM7 13.5h.01M17 13.5h.01", 2f)
    }

    // Low-battery alert
    val BatteryAlert = icon {
        stroke("M9 2.5h6M8 4.5h8a1.5 1.5 0 0 1 1.5 1.5v14a1.5 1.5 0 0 1-1.5 1.5H8A1.5 1.5 0 0 1 6.5 20V6A1.5 1.5 0 0 1 8 4.5zM9.5 17.5h5", 2f)
    }
    val Close = icon { stroke("M6 6l12 12M18 6 6 18", 2.4f) }

    // 충전 도우미
    val Refresh = icon { stroke("M20 12a8 8 0 1 1-2.3-5.7L20 8.5M20 3v5.5h-5.5", 2.4f) }
    val Gear = icon {
        fill(
            "M10.3 1.5h3.4l.6 2.8a8 8 0 0 1 2 1.2l2.7-.9 1.7 3-2.1 1.9a8 8 0 0 1 0 2.4l2.1 1.9-1.7 3-2.7-.9a8 8 0 0 1-2 1.2" +
                "l-.6 2.8h-3.4l-.6-2.8a8 8 0 0 1-2-1.2l-2.7.9-1.7-3 2.1-1.9a8 8 0 0 1 0-2.4L3.3 7.6l1.7-3 2.7.9a8 8 0 0 1 2-1.2z" +
                "M12 8.3a3.7 3.7 0 1 0 0 7.4 3.7 3.7 0 0 0 0-7.4z",
            evenOdd = true,
        )
    }
    val BatteryReserve = icon {
        stroke("M9 2.5h6M8 4.5h8a1.5 1.5 0 0 1 1.5 1.5v14a1.5 1.5 0 0 1-1.5 1.5H8A1.5 1.5 0 0 1 6.5 20V6A1.5 1.5 0 0 1 8 4.5zM9.5 16.5h5M9.5 13.5h5", 2f)
    }
    val BoltOutline = icon { stroke("M13.5 2 5 13.5h6.5L10.5 22 19 10.5h-6.5z", 2f) }
    val Monitor = icon { stroke("M3 4.5h18v12H3zM8 20.5h8M12 16.5v4", 2f) }

    /** Map pin; view box 24×28. */
    val Pin = icon(24f, 28f) {
        fill("M12 0a9 9 0 0 1 9 9c0 6.5-9 17-9 17S3 15.5 3 9a9 9 0 0 1 9-9zm0 5.5a3.5 3.5 0 1 0 0 7 3.5 3.5 0 0 0 0-7z")
    }
    val ChevronDown = icon { stroke("m6 9 6 6 6-6", 2.4f) }
    /** The only stroke icon of the design without `stroke-linejoin="round"`: its hands meet in a mitre. */
    val Clock = icon {
        stroke(circle(12f, 12f, 9f), 2f)
        stroke("M12 7v5l3 2", 2f, join = StrokeJoin.Miter)
    }
    val BatteryOutline = icon {
        stroke("M9 2.5h6M8 4.5h8a1.5 1.5 0 0 1 1.5 1.5v14a1.5 1.5 0 0 1-1.5 1.5H8A1.5 1.5 0 0 1 6.5 20V6A1.5 1.5 0 0 1 8 4.5z", 2f)
    }
    private const val STAR = "m12 2.8 2.8 5.8 6.3.9-4.6 4.4 1.1 6.3L12 17.2l-5.6 3 1.1-6.3-4.6-4.4 6.3-.9z"
    val StarOutline = icon { stroke(STAR, 1.8f) }
    val StarFilled = icon { fillAndStroke(STAR, 1.8f) }
    val Navigate = icon { fill("M21.5 2.5 2.5 10l7.5 3 3 7.5z") }

    /** Not in the design: marks the chosen order in the sort menu (the design's line weight and caps). */
    val Check = icon { stroke("M4 12l5 5L20 6", 2.4f) }

    /** Every icon by name, for checks that go through all of them. */
    val all: Map<String, ImageVector>
        get() = mapOf(
            "Bluetooth" to Bluetooth, "Wifi" to Wifi, "Car" to Car, "Charger" to Charger, "ChargerGlyph" to ChargerGlyph,
            "ArrowRight" to ArrowRight, "ArrowLeft" to ArrowLeft, "BatteryFilled" to BatteryFilled, "BoltFilled" to BoltFilled,
            "Leaf" to Leaf, "Route" to Route, "Timer" to Timer, "Gauge" to Gauge, "CarOutline" to CarOutline,
            "BatteryAlert" to BatteryAlert, "Close" to Close, "Refresh" to Refresh, "Gear" to Gear,
            "BatteryReserve" to BatteryReserve, "BoltOutline" to BoltOutline, "Monitor" to Monitor, "Pin" to Pin,
            "ChevronDown" to ChevronDown, "Clock" to Clock, "BatteryOutline" to BatteryOutline, "StarOutline" to StarOutline,
            "StarFilled" to StarFilled, "Navigate" to Navigate, "Check" to Check,
        )
}

/** Draws [vector] at the design's size, in [tint] for the single-colour icons. */
@Composable
fun EvIcon(
    vector: ImageVector,
    width: Dp,
    modifier: Modifier = Modifier,
    height: Dp = width,
    tint: Color? = null,
    contentDescription: String? = null,
) {
    Image(
        imageVector = vector,
        contentDescription = contentDescription,
        modifier = modifier.size(width, height),
        colorFilter = tint?.let { ColorFilter.tint(it) },
    )
}

private fun icon(viewWidth: Float = 24f, viewHeight: Float = 24f, build: ImageVector.Builder.() -> Unit): ImageVector =
    ImageVector.Builder(
        name = "EvIcon",
        defaultWidth = viewWidth.dp,
        defaultHeight = viewHeight.dp,
        viewportWidth = viewWidth,
        viewportHeight = viewHeight,
    ).apply(build).build()

private fun ImageVector.Builder.stroke(d: String, width: Float, color: Color = Color.White, join: StrokeJoin = StrokeJoin.Round) {
    addPath(
        pathData = addPathNodes(d),
        stroke = SolidColor(color),
        strokeLineWidth = width,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = join,
    )
}

private fun ImageVector.Builder.fill(d: String, color: Color = Color.White, evenOdd: Boolean = false) {
    addPath(
        pathData = addPathNodes(d),
        fill = SolidColor(color),
        pathFillType = if (evenOdd) PathFillType.EvenOdd else PathFillType.NonZero,
    )
}

private fun ImageVector.Builder.fillAndStroke(d: String, width: Float, color: Color = Color.White) {
    addPath(
        pathData = addPathNodes(d),
        fill = SolidColor(color),
        stroke = SolidColor(color),
        strokeLineWidth = width,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    )
}

/** `<circle>` as path data. */
private fun circle(cx: Float, cy: Float, r: Float): String =
    "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0z"

/** `<rect rx>` as path data. */
private fun roundRect(x: Float, y: Float, w: Float, h: Float, r: Float): String =
    "M${x + r} ${y}h${w - 2 * r}a$r $r 0 0 1 $r ${r}v${h - 2 * r}a$r $r 0 0 1 ${-r} ${r}h${-(w - 2 * r)}" +
        "a$r $r 0 0 1 ${-r} ${-r}v${-(h - 2 * r)}a$r $r 0 0 1 $r ${-r}z"
