package com.example.drivecoach.ui.parking

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.example.drivecoach.model.Gear
import com.example.drivecoach.parking.ParkingPlan
import com.example.drivecoach.parking.Pose
import com.example.drivecoach.parking.Vec2
import com.example.drivecoach.parking.footprint
import com.example.drivecoach.ui.theme.CoachColors
import java.util.Locale
import kotlin.math.min

/** What to draw on top of the plan for the P-gear steering practice. */
class PracticeOverlay(val forward: List<Pose>, val reverse: List<Pose>)

/**
 * Top-down view of the scenario: stall lines, parked cars, the planned rear-axle path (blue forward,
 * amber reverse), the car at [carPose], the reference point and the closest approach.
 */
@Composable
fun ParkingCanvas(
    plan: ParkingPlan,
    carPose: Pose,
    modifier: Modifier = Modifier,
    practice: PracticeOverlay? = null,
) {
    val measurer = rememberTextMeasurer()
    val g = plan.geometry
    val layout = plan.layout
    Canvas(modifier) {
        val pad = 24f
        val scale = min((size.width - 2 * pad) / (layout.maxX - layout.minX), (size.height - 2 * pad) / (layout.maxY - layout.minY)).toFloat()
        val cx = ((layout.minX + layout.maxX) / 2).toFloat()
        val cy = ((layout.minY + layout.maxY) / 2).toFloat()
        fun p(v: Vec2) = Offset(size.width / 2 + (v.x.toFloat() - cx) * scale, size.height / 2 - (v.y.toFloat() - cy) * scale)
        fun poly(points: List<Vec2>) = Path().apply {
            points.forEachIndexed { i, v -> if (i == 0) moveTo(p(v).x, p(v).y) else lineTo(p(v).x, p(v).y) }
            close()
        }

        drawRect(CoachColors.Asphalt)
        drawPath(poly(layout.target), CoachColors.Gentle.copy(alpha = 0.18f))
        layout.lines.forEach { drawLine(CoachColors.Lines, p(it.a), p(it.b), strokeWidth = 3f) }
        layout.walls.forEach { drawLine(Color(0xFF8A96A6), p(it.a), p(it.b), strokeWidth = 8f, cap = StrokeCap.Round) }
        layout.cars.forEach { car ->
            drawPath(poly(car), CoachColors.ParkedCar)
            drawPath(poly(car), Color(0xFF2E3640), style = Stroke(3f))
        }

        // Planned path of the rear-axle centre, coloured by gear.
        val steps = plan.steps
        for (i in 1 until plan.poses.size) {
            val a = plan.poses[i - 1]
            val b = plan.poses[i]
            val color = if (steps.getOrNull(b.step)?.gear == Gear.REVERSE) CoachColors.Reverse else CoachColors.Drive
            drawLine(color.copy(alpha = 0.75f), p(a.pose.position), p(b.pose.position), strokeWidth = 4f)
        }

        practice?.let { o ->
            val dash = PathEffect.dashPathEffect(floatArrayOf(18f, 12f))
            drawPolyline(o.forward.map { p(it.position) }, CoachColors.Drive, dash)
            drawPolyline(o.reverse.map { p(it.position) }, CoachColors.Reverse, dash)
            val endR = o.reverse.lastOrNull()
            if (endR != null) drawPath(poly(footprint(endR, g).toList()), CoachColors.Reverse.copy(alpha = 0.35f), style = Stroke(3f))
        }

        // Car body with its front edge highlighted.
        val body = footprint(carPose, g)
        drawPath(poly(body.toList()), Color(0xFF5BC0FF).copy(alpha = 0.85f))
        drawLine(Color.White, p(body[2]), p(body[3]), strokeWidth = 6f, cap = StrokeCap.Round)
        drawCircle(Color.White, 5f, p(carPose.position))

        // Reference point: the driver's eye where the manoeuvre starts.
        drawCircle(Color.White, 9f, p(plan.eyeMarker))
        drawCircle(Color(0xFF0B0F14), 4f, p(plan.eyeMarker))
        label(measurer, "기준점", p(plan.eyeMarker) + Offset(12f, -34f), Color.White)

        plan.closestPoint?.let { c ->
            drawCircle(CoachColors.Brake, 10f, p(c), style = Stroke(4f))
            val text = "가장 가까운 곳 ${String.format(Locale.KOREA, "%.2f", plan.clearanceM)} m"
            label(measurer, text, p(c) + Offset(14f, 6f), CoachColors.Brake)
        }
    }
}

private fun DrawScope.drawPolyline(points: List<Offset>, color: Color, effect: PathEffect) {
    if (points.size < 2) return
    val path = Path().apply {
        moveTo(points[0].x, points[0].y)
        points.drop(1).forEach { lineTo(it.x, it.y) }
    }
    drawPath(path, color, style = Stroke(width = 5f, pathEffect = effect))
}

private fun DrawScope.label(measurer: androidx.compose.ui.text.TextMeasurer, text: String, at: Offset, color: Color) {
    drawText(measurer, text, at, TextStyle(color = color, fontSize = 15.sp))
}
