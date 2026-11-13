package com.example.drivecoach.ui.parking

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import com.example.drivecoach.parking.ParkingPlan
import com.example.drivecoach.parking.Pose
import com.example.drivecoach.parking.Side
import com.example.drivecoach.parking.Vec2
import com.example.drivecoach.parking.footprint
import com.example.drivecoach.ui.theme.CoachColors
import com.example.drivecoach.ui.theme.CoachTheme
import com.example.drivecoach.ui.theme.coachTextStyle
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** What to draw on top of the plan for the P-gear steering practice. */
class PracticeOverlay(val forward: List<Pose>, val reverse: List<Pose>)

/**
 * Top-down view of the scenario in the design's style (its SVG has `viewBox 0 0 700 560`; every width,
 * radius and font size below is in those units and scales with the canvas like the SVG does): the wall
 * (#c3c9d3, 8 wide, round caps), the tinted target stall, stall lines (#9aa3b2, 3 wide), parked cars as
 * rounded rectangles in `car2`, the planned rear-axle path as a dashed amber line (4 wide, dash 10 8), the
 * reference point (white dot, ink ring) with "기준점" above it, the closest approach as a red ring with its
 * distance, and the car in the accent colour with a window, a white front edge and the rear-axle dot.
 *
 * The geometry is the real plan in metres. As in the design the stalls are at the bottom: a left-side
 * scenario is the mirror image of the right-side one (the design's `scale(-1 1)`).
 */
@Composable
fun ParkingCanvas(
    plan: ParkingPlan,
    carPose: Pose,
    modifier: Modifier = Modifier,
    practice: PracticeOverlay? = null,
) {
    val c = CoachTheme.colors
    val measurer = rememberTextMeasurer()
    val g = plan.geometry
    val layout = plan.layout
    val view = remember(plan) { viewBox(plan) }
    val clearance = String.format(Locale.KOREA, "%.2f", plan.clearanceM)
    Canvas(modifier.semantics { contentDescription = "${plan.spec.type.label} 시뮬레이션" }) {
        // viewBox 700×560 with preserveAspectRatio xMidYMid meet: k is one viewBox unit in pixels.
        val k = min(size.width / 700f, size.height / 560f)
        val margin = 30f * k
        val worldW = view.maxX - view.minX
        val worldH = view.maxY - view.minY
        val scale = min((size.width - 2 * margin) / worldW, (size.height - 2 * margin) / worldH)
        val cx = (view.minX + view.maxX) / 2
        val cy = (view.minY + view.maxY) / 2
        // Right-side stalls have y < 0 and go to the bottom; left-side ones (y > 0) are turned half a circle.
        val sign = if (plan.spec.side == Side.LEFT) -1f else 1f
        fun p(v: Vec2) = Offset(size.width / 2 + sign * (v.x.toFloat() - cx) * scale, size.height / 2 - sign * (v.y.toFloat() - cy) * scale)
        fun poly(points: List<Vec2>) = Path().apply {
            points.forEachIndexed { i, v -> if (i == 0) moveTo(p(v).x, p(v).y) else lineTo(p(v).x, p(v).y) }
            close()
        }
        fun polyline(points: List<Vec2>) = Path().apply {
            points.forEachIndexed { i, v -> if (i == 0) moveTo(p(v).x, p(v).y) else lineTo(p(v).x, p(v).y) }
        }
        val dash = PathEffect.dashPathEffect(floatArrayOf(10f * k, 8f * k))
        val pathStroke = Stroke(4f * k, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = dash)

        layout.walls.forEach { drawLine(CoachColors.Wall, p(it.a), p(it.b), strokeWidth = 8f * k, cap = StrokeCap.Round) }
        drawPath(poly(layout.target), CoachColors.TargetSlot)
        layout.lines.forEach { drawLine(CoachColors.SlotLine, p(it.a), p(it.b), strokeWidth = 3f * k) }
        layout.cars.forEach { car -> body(car.map { p(it) }, 10f * k, c.car2) }

        // Planned path of the rear-axle centre. While practising it steps back so the live preview stands out.
        val planned = polyline(plan.poses.map { it.pose.position })
        if (practice == null) {
            drawPath(planned, CoachColors.Amber, style = pathStroke)
        } else {
            drawPath(planned, CoachColors.Amber.copy(alpha = 0.35f), style = Stroke(4f * k, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }

        practice?.let { o ->
            if (o.forward.size > 1) drawPath(polyline(o.forward.map { it.position }), c.acc, style = pathStroke)
            if (o.reverse.size > 1) drawPath(polyline(o.reverse.map { it.position }), CoachColors.Amber, style = pathStroke)
            o.reverse.lastOrNull()?.let { end ->
                body(footprint(end, g).map { p(it) }, 12f * k, CoachColors.Amber.copy(alpha = 0.35f), Stroke(3f * k))
            }
        }

        // Reference point: the driver's eye where the manoeuvre starts.
        val eye = p(plan.eyeMarker)
        drawCircle(CoachColors.White, 9f * k, eye)
        drawCircle(c.ink, 9f * k, eye, style = Stroke(4f * k))
        // The design writes "기준점" above the point. Across a narrow aisle (angled stalls) the far wall runs
        // right there, so the label goes beside the point instead of onto the wall.
        val wallAbove = layout.walls.any { wall ->
            val a = p(wall.a)
            val b = p(wall.b)
            val y = (a.y + b.y) / 2f
            y > eye.y - 44f * k && y < eye.y - 13f * k && eye.x >= min(a.x, b.x) && eye.x <= max(a.x, b.x)
        }
        if (wallAbove) {
            label(measurer, "기준점", 18f * k, FontWeight.W400, c.ink, eye.x + sign * 20f * k, eye.y + 6f * k, sign.toInt())
        } else {
            label(measurer, "기준점", 18f * k, FontWeight.W400, c.ink, eye.x, eye.y - 22f * k, 0)
        }

        plan.closestPoint?.let { closest ->
            val ring = p(closest)
            drawCircle(CoachColors.Red, 11f * k, ring, style = Stroke(4f * k))
            // The design writes the distance under the stall row, starting 15 units beside the ring. When the
            // closest thing is on the far side of the aisle it goes above that wall instead.
            val stallBottom = (layout.lines.flatMap { listOf(it.a, it.b) } + layout.target).maxOfOrNull { p(it).y } ?: ring.y
            val baseline = if (ring.y > size.height / 2 - 2f * scale) stallBottom + 30f * k else ring.y - 20f * k
            label(measurer, "가장 가까운 곳 $clearance m", 18f * k, FontWeight.W700, CoachColors.Red, ring.x + sign * 15f * k, baseline, sign.toInt())
        }

        // The car: rear-left, rear-right, front-right, front-left.
        val corners = footprint(carPose, g).map { p(it) }
        val mid = Offset(corners.sumOf { it.x.toDouble() }.toFloat() / 4f, corners.sumOf { it.y.toDouble() }.toFloat() / 4f)
        val length = distance(corners[0], corners[3])
        val width = distance(corners[0], corners[1])
        val heading = Math.toDegrees(atan2((corners[3].y - corners[0].y).toDouble(), (corners[3].x - corners[0].x).toDouble())).toFloat()
        withTransform({
            translate(mid.x, mid.y)
            rotate(heading, pivot = Offset.Zero)
        }) {
            // The design's car is 124×54: body rx 12, a window from x = 10 to 32 and y = ±21, the front edge 5 wide.
            drawRoundRect(c.acc, Offset(-length / 2, -width / 2), Size(length, width), CornerRadius(12f * k))
            val windowX = length / 2 * (10f / 62f)
            val windowW = length / 2 * (22f / 62f)
            val windowH = width * (42f / 54f)
            drawRoundRect(CoachColors.CarWindow, Offset(windowX, -windowH / 2), Size(windowW, windowH), CornerRadius(5f * k))
            drawLine(CoachColors.White, Offset(length / 2, -width / 2), Offset(length / 2, width / 2), strokeWidth = 5f * k)
        }
        drawCircle(CoachColors.White, 6f * k, p(carPose.position))
    }
}

private fun distance(a: Offset, b: Offset) = hypot((b.x - a.x).toDouble(), (b.y - a.y).toDouble()).toFloat()

/** The part of the world (metres) the drawing shows. */
class ViewBox(val minX: Float, val maxX: Float, val minY: Float, val maxY: Float)

/**
 * The layout's own box, widened where the car leaves it: the approach to an angled stall starts beyond
 * the last drawn stall, and the car would otherwise begin half outside the card.
 */
fun viewBox(plan: ParkingPlan): ViewBox {
    val layout = plan.layout
    var minX = layout.minX
    var maxX = layout.maxX
    var minY = layout.minY
    var maxY = layout.maxY
    plan.poses.forEach { timed ->
        footprint(timed.pose, plan.geometry).forEach { corner ->
            minX = min(minX, corner.x)
            maxX = max(maxX, corner.x)
            minY = min(minY, corner.y)
            maxY = max(maxY, corner.y)
        }
    }
    return ViewBox(minX.toFloat(), maxX.toFloat(), minY.toFloat(), maxY.toFloat())
}

/** A car outline given as its four corners on screen (rear-left, rear-right, front-right, front-left), drawn with rounded corners. */
private fun DrawScope.body(corners: List<Offset>, radius: Float, color: Color, style: DrawStyle = Fill) {
    val mid = Offset(corners.sumOf { it.x.toDouble() }.toFloat() / 4f, corners.sumOf { it.y.toDouble() }.toFloat() / 4f)
    val length = distance(corners[0], corners[3])
    val width = distance(corners[0], corners[1])
    val heading = Math.toDegrees(atan2((corners[3].y - corners[0].y).toDouble(), (corners[3].x - corners[0].x).toDouble())).toFloat()
    withTransform({
        translate(mid.x, mid.y)
        rotate(heading, pivot = Offset.Zero)
    }) {
        drawRoundRect(color, Offset(-length / 2, -width / 2), Size(length, width), CornerRadius(radius), style = style)
    }
}

/**
 * SVG `<text>`: [x] is the anchor ([anchor] 0 = middle, 1 = start, −1 = end) and [baseline] the y of the
 * text's baseline. The text is kept inside the canvas.
 */
private fun DrawScope.label(measurer: TextMeasurer, text: String, sizePx: Float, weight: FontWeight, color: Color, x: Float, baseline: Float, anchor: Int) {
    val style = coachTextStyle(sizePx / (density * fontScale), weight)
    val layout = measurer.measure(text, style, softWrap = false, maxLines = 1)
    val left = when {
        anchor > 0 -> x
        anchor < 0 -> x - layout.size.width
        else -> x - layout.size.width / 2f
    }
    val inside = max(4f, min(left, size.width - layout.size.width - 4f))
    drawText(layout, color = color, topLeft = Offset(inside, baseline - layout.firstBaseline))
}
