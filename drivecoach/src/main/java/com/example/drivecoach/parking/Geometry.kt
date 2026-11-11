package com.example.drivecoach.parking

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

data class Vec2(val x: Double, val y: Double) {
    operator fun plus(o: Vec2) = Vec2(x + o.x, y + o.y)
    operator fun minus(o: Vec2) = Vec2(x - o.x, y - o.y)
    operator fun times(k: Double) = Vec2(x * k, y * k)
    fun dot(o: Vec2) = x * o.x + y * o.y
    fun length() = hypot(x, y)
    fun mirrored() = Vec2(x, -y)
}

/**
 * Pose of the rear-axle centre: position in metres and heading [theta] in radians (0 = +x, CCW positive).
 */
data class Pose(val x: Double, val y: Double, val theta: Double) {

    val position: Vec2 get() = Vec2(x, y)

    /**
     * Moves along an arc of curvature [kappa] (1/m, left turn positive) by the signed arc length [s]
     * (negative = reversing). Exact for the kinematic bicycle model at low speed.
     */
    fun advance(kappa: Double, s: Double): Pose {
        if (abs(kappa) < 1e-9) return Pose(x + s * cos(theta), y + s * sin(theta), theta)
        val t2 = theta + kappa * s
        return Pose(x + (sin(t2) - sin(theta)) / kappa, y - (cos(t2) - cos(theta)) / kappa, t2)
    }

    /** Point given in the car frame (x forward from the rear axle, y to the left). */
    fun toWorld(local: Vec2): Vec2 {
        val c = cos(theta)
        val s = sin(theta)
        return Vec2(x + local.x * c - local.y * s, y + local.x * s + local.y * c)
    }

    fun mirrored() = Pose(x, -y, -theta)
}

/** Body outline (rear-left, rear-right, front-right, front-left) at [pose]. */
fun footprint(pose: Pose, g: VehicleGeometry, inflate: Double = 0.0): Array<Vec2> {
    val rear = -g.rearOverhangM - inflate
    val front = g.wheelbaseM + g.frontOverhangM + inflate
    val half = g.widthM / 2 + inflate
    return arrayOf(
        pose.toWorld(Vec2(rear, half)),
        pose.toWorld(Vec2(rear, -half)),
        pose.toWorld(Vec2(front, -half)),
        pose.toWorld(Vec2(front, half)),
    )
}

/** Axis-aligned-in-its-own-frame rectangle: centre, heading of its length axis, size. */
fun rectangle(center: Vec2, heading: Double, length: Double, width: Double): Array<Vec2> {
    val p = Pose(center.x, center.y, heading)
    val hl = length / 2
    val hw = width / 2
    return arrayOf(
        p.toWorld(Vec2(-hl, hw)),
        p.toWorld(Vec2(-hl, -hw)),
        p.toWorld(Vec2(hl, -hw)),
        p.toWorld(Vec2(hl, hw)),
    )
}

sealed interface Obstacle {
    /** Required free distance; a plan is feasible only if every obstacle keeps at least this. */
    val minClearanceM: Double
    val label: String

    /** Signed distance from the convex polygon [body] (negative = overlapping). */
    fun clearance(body: Array<Vec2>): Double

    /** Closest point on the obstacle to [body], for the "가장 가까운 지점" marker. */
    fun closestPoint(body: Array<Vec2>): Vec2
}

/** A parked car or any other convex block. */
data class BoxObstacle(
    val corners: Array<Vec2>,
    override val minClearanceM: Double,
    override val label: String,
) : Obstacle {
    override fun clearance(body: Array<Vec2>): Double = polygonDistance(body, corners)
    override fun closestPoint(body: Array<Vec2>): Vec2 = closestPointOnPolygon(corners, body)
}

/** Wall, curb or aisle edge: free side is `normal · p ≥ offset`. */
data class LineObstacle(
    val normal: Vec2,
    val offset: Double,
    override val minClearanceM: Double,
    override val label: String,
) : Obstacle {
    override fun clearance(body: Array<Vec2>): Double = body.minOf { normal.dot(it) - offset }
    override fun closestPoint(body: Array<Vec2>): Vec2 {
        val p = body.minBy { normal.dot(it) - offset }
        val d = normal.dot(p) - offset
        return p - normal * d
    }
}

/**
 * Distance between two convex polygons: separating-axis overlap (negative) when they intersect,
 * otherwise the smallest vertex-to-edge distance in both directions (exact for convex shapes).
 */
fun polygonDistance(a: Array<Vec2>, b: Array<Vec2>): Double {
    var minOverlap = Double.MAX_VALUE
    for (poly in arrayOf(a, b)) {
        for (i in poly.indices) {
            val p = poly[i]
            val q = poly[(i + 1) % poly.size]
            val n = Vec2(-(q.y - p.y), q.x - p.x)
            val len = n.length()
            if (len < 1e-12) continue
            val axis = n * (1 / len)
            var aMin = Double.MAX_VALUE
            var aMax = -Double.MAX_VALUE
            for (v in a) {
                val d = axis.dot(v)
                aMin = min(aMin, d)
                aMax = max(aMax, d)
            }
            var bMin = Double.MAX_VALUE
            var bMax = -Double.MAX_VALUE
            for (v in b) {
                val d = axis.dot(v)
                bMin = min(bMin, d)
                bMax = max(bMax, d)
            }
            if (aMax < bMin || bMax < aMin) return separatedDistance(a, b)
            minOverlap = min(minOverlap, min(aMax - bMin, bMax - aMin))
        }
    }
    return -minOverlap
}

private fun separatedDistance(a: Array<Vec2>, b: Array<Vec2>): Double {
    var best = Double.MAX_VALUE
    for (v in a) best = min(best, pointToPolygonEdges(v, b))
    for (v in b) best = min(best, pointToPolygonEdges(v, a))
    return best
}

private fun pointToPolygonEdges(p: Vec2, poly: Array<Vec2>): Double {
    var best = Double.MAX_VALUE
    for (i in poly.indices) best = min(best, (p - closestOnSegment(p, poly[i], poly[(i + 1) % poly.size])).length())
    return best
}

fun closestOnSegment(p: Vec2, a: Vec2, b: Vec2): Vec2 {
    val ab = b - a
    val len2 = ab.dot(ab)
    if (len2 < 1e-12) return a
    val t = ((p - a).dot(ab) / len2).coerceIn(0.0, 1.0)
    return a + ab * t
}

/** Point of [poly] closest to [other] (vertex-edge search, fine for drawing). */
private fun closestPointOnPolygon(poly: Array<Vec2>, other: Array<Vec2>): Vec2 {
    var best = poly[0]
    var bestD = Double.MAX_VALUE
    for (v in other) {
        for (i in poly.indices) {
            val c = closestOnSegment(v, poly[i], poly[(i + 1) % poly.size])
            val d = (v - c).length()
            if (d < bestD) {
                bestD = d
                best = c
            }
        }
    }
    for (v in poly) {
        for (i in other.indices) {
            val c = closestOnSegment(v, other[i], other[(i + 1) % other.size])
            val d = (v - c).length()
            if (d < bestD) {
                bestD = d
                best = v
            }
        }
    }
    return best
}
