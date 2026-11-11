package com.example.drivecoach.parking

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class ParkingType(val label: String) {
    REVERSE_PERPENDICULAR("후진 직각주차"),
    PARALLEL("평행주차"),
    FORWARD_PERPENDICULAR("전진 직각주차"),
    ANGLED("사선주차"),
}

enum class Side(val label: String) { RIGHT("오른쪽 칸"), LEFT("왼쪽 칸") }

/**
 * A parking situation. Stall and aisle defaults follow 주차장법 시행규칙 (일반형 주차구획 2.5 × 5.0 m,
 * 평행주차 2.0 × 6.0 m). Aisles use the two-entrance widths, except parallel parking: with a hard edge at
 * 3.3 m a long car's front cannot swing out, so it uses the single-entrance width (5.0 m), which is
 * closer to kerbside parking where the next lane is free.
 */
data class ScenarioSpec(
    val type: ParkingType,
    val side: Side = Side.RIGHT,
    val stallWidthM: Double,
    val stallLengthM: Double,
    val aisleWidthM: Double,
    /** Stall angle to the aisle for [ParkingType.ANGLED] (45 or 60). */
    val angleDeg: Int = 90,
    val neighbors: Boolean = true,
) {
    companion object {
        fun defaults(type: ParkingType, angleDeg: Int = 45): ScenarioSpec = when (type) {
            ParkingType.REVERSE_PERPENDICULAR, ParkingType.FORWARD_PERPENDICULAR ->
                ScenarioSpec(type, stallWidthM = 2.5, stallLengthM = 5.0, aisleWidthM = 6.0)
            ParkingType.PARALLEL ->
                ScenarioSpec(type, stallWidthM = 2.0, stallLengthM = 6.0, aisleWidthM = 5.0)
            ParkingType.ANGLED ->
                ScenarioSpec(type, stallWidthM = 2.5, stallLengthM = 5.0, aisleWidthM = if (angleDeg >= 60) 4.5 else 3.5, angleDeg = angleDeg)
        }
    }
}

data class Segment(val a: Vec2, val b: Vec2) {
    fun mirrored() = Segment(a.mirrored(), b.mirrored())
}

/**
 * World layout in metres. Canonical frame: the car drives along +x, the stalls are on its right
 * (y < 0) with the entrance line at y = 0, the target stall is centred near x = 0, and the far side of
 * the aisle is at y = aisle width. Left-side scenarios are the mirror image (y → −y).
 */
data class ScenarioLayout(
    val spec: ScenarioSpec,
    /** Final rear-axle pose inside the target stall. */
    val goal: Pose,
    val target: List<Vec2>,
    val lines: List<Segment>,
    val cars: List<List<Vec2>>,
    val walls: List<Segment>,
    val obstacles: List<Obstacle>,
    /** For text cues: stall pitch along the aisle and the target stall's reference x. */
    val stallPitchM: Double,
    val targetRefX: Double,
    /** Parallel parking: the outer (lane-side) edge of the parked cars. */
    val parkedCarsOuterY: Double,
    val minX: Double,
    val maxX: Double,
    val minY: Double,
    val maxY: Double,
) {
    fun mirrored(): ScenarioLayout = copy(
        goal = goal.mirrored(),
        target = target.map { it.mirrored() },
        lines = lines.map { it.mirrored() },
        cars = cars.map { c -> c.map { it.mirrored() } },
        walls = walls.map { it.mirrored() },
        obstacles = obstacles.map { o ->
            when (o) {
                is BoxObstacle -> o.copy(corners = o.corners.map { it.mirrored() }.toTypedArray())
                is LineObstacle -> o.copy(normal = o.normal.mirrored())
            }
        },
        parkedCarsOuterY = -parkedCarsOuterY,
        minY = -maxY,
        maxY = -minY,
    )
}

object ScenarioBuilder {

    /** Typical parked neighbour (mid-size). */
    const val NEIGHBOR_LENGTH_M = 4.7
    const val NEIGHBOR_WIDTH_M = 1.88

    /** Gap left between the bumper and the end of the stall. */
    const val END_MARGIN_M = 0.2

    /** Parallel parking: distance kept from the curb. */
    const val CURB_GAP_M = 0.2

    const val CAR_CLEARANCE_M = 0.25
    const val WALL_CLEARANCE_M = 0.15
    const val CURB_CLEARANCE_M = 0.10

    /** Canonical (right-side) layout; mirror with [ScenarioLayout.mirrored] for left-side stalls. */
    fun build(spec: ScenarioSpec, g: VehicleGeometry): ScenarioLayout = when (spec.type) {
        ParkingType.REVERSE_PERPENDICULAR, ParkingType.FORWARD_PERPENDICULAR -> perpendicular(spec, g)
        ParkingType.PARALLEL -> parallel(spec, g)
        ParkingType.ANGLED -> angled(spec, g)
    }

    private fun perpendicular(spec: ScenarioSpec, g: VehicleGeometry): ScenarioLayout {
        val s = spec.stallWidthM
        val d = spec.stallLengthM
        val a = spec.aisleWidthM
        val span = 4.5 * s
        val lines = (-4..5).map { k -> Segment(Vec2(-s / 2 + k * s, 0.0), Vec2(-s / 2 + k * s, -d)) } +
            Segment(Vec2(-span, -d), Vec2(span, -d))
        val cars = if (spec.neighbors) {
            listOf(-3, -2, -1, 1, 2, 3).map { k -> rectangle(Vec2(k * s, -d / 2), PI / 2, NEIGHBOR_LENGTH_M, NEIGHBOR_WIDTH_M) }
        } else {
            emptyList()
        }
        val goal = if (spec.type == ParkingType.REVERSE_PERPENDICULAR) {
            Pose(0.0, -d + END_MARGIN_M + g.rearOverhangM, PI / 2)
        } else {
            Pose(0.0, -d + END_MARGIN_M + g.wheelbaseM + g.frontOverhangM, -PI / 2)
        }
        return ScenarioLayout(
            spec = spec,
            goal = goal,
            target = listOf(Vec2(-s / 2, 0.0), Vec2(s / 2, 0.0), Vec2(s / 2, -d), Vec2(-s / 2, -d)),
            lines = lines,
            cars = cars.map { it.toList() },
            walls = listOf(Segment(Vec2(-span, a), Vec2(span, a))),
            obstacles = cars.map { BoxObstacle(it, CAR_CLEARANCE_M, "옆 차") } +
                LineObstacle(Vec2(0.0, 1.0), -d, WALL_CLEARANCE_M, "칸 끝") +
                LineObstacle(Vec2(0.0, -1.0), -a, WALL_CLEARANCE_M, "통로 반대편"),
            stallPitchM = s,
            targetRefX = 0.0,
            parkedCarsOuterY = 0.0,
            minX = -span,
            maxX = span,
            minY = -d - 0.6,
            maxY = a + 0.6,
        )
    }

    private fun parallel(spec: ScenarioSpec, g: VehicleGeometry): ScenarioLayout {
        val w = spec.stallWidthM
        val l = spec.stallLengthM
        val a = spec.aisleWidthM
        val span = 2.5 * l
        val lines = (-2..3).map { k -> Segment(Vec2(-l / 2 + k * l, 0.0), Vec2(-l / 2 + k * l, -w)) } +
            Segment(Vec2(-span, 0.0), Vec2(span, 0.0))
        val cars = if (spec.neighbors) {
            listOf(-2, -1, 1, 2).map { k -> rectangle(Vec2(k * l, -w / 2), 0.0, NEIGHBOR_LENGTH_M, NEIGHBOR_WIDTH_M) }
        } else {
            emptyList()
        }
        // Car centred in its stall, CURB_GAP_M from the curb.
        val goal = Pose(-(g.wheelbaseM + g.frontOverhangM - g.rearOverhangM) / 2, -w + CURB_GAP_M + g.widthM / 2, 0.0)
        return ScenarioLayout(
            spec = spec,
            goal = goal,
            target = listOf(Vec2(-l / 2, 0.0), Vec2(l / 2, 0.0), Vec2(l / 2, -w), Vec2(-l / 2, -w)),
            lines = lines,
            cars = cars.map { it.toList() },
            walls = listOf(Segment(Vec2(-span, -w), Vec2(span, -w)), Segment(Vec2(-span, a), Vec2(span, a))),
            obstacles = cars.map { BoxObstacle(it, CAR_CLEARANCE_M, "앞뒤 차") } +
                LineObstacle(Vec2(0.0, 1.0), -w, CURB_CLEARANCE_M, "연석") +
                LineObstacle(Vec2(0.0, -1.0), -a, WALL_CLEARANCE_M, "차로 반대편"),
            stallPitchM = l,
            targetRefX = 0.0,
            parkedCarsOuterY = if (spec.neighbors) -w / 2 + NEIGHBOR_WIDTH_M / 2 else 0.0,
            minX = -2 * l,
            maxX = 2 * l,
            minY = -w - 0.6,
            maxY = a + 0.6,
        )
    }

    /**
     * Angled stalls are 2.5 × 5.0 rectangles rotated by the stall angle β, spaced S / sin β along the
     * aisle so neighbours share their long sides; each stall's upper entrance corner touches y = 0.
     */
    private fun angled(spec: ScenarioSpec, g: VehicleGeometry): ScenarioLayout {
        val s = spec.stallWidthM
        val d = spec.stallLengthM
        val a = spec.aisleWidthM
        val beta = spec.angleDeg * PI / 180
        val u = Vec2(cos(beta), -sin(beta)) // into the stall
        val n = Vec2(sin(beta), cos(beta)) // left of u
        val pitch = s / sin(beta)
        fun entrance(k: Int) = Vec2(k * pitch, -(s / 2) * cos(beta))
        fun stall(k: Int): List<Vec2> {
            val c = entrance(k)
            val e1 = c + n * (s / 2)
            val e2 = c - n * (s / 2)
            return listOf(e1, e2, e2 + u * d, e1 + u * d)
        }
        val range = -4..4
        val lines = range.flatMap { k ->
            val st = stall(k)
            listOf(Segment(st[0], st[3]), Segment(st[2], st[3])) + if (k == range.first) listOf(Segment(st[1], st[2])) else emptyList()
        }
        val cars = if (spec.neighbors) {
            listOf(-3, -2, -1, 1, 2, 3).map { k -> rectangle(entrance(k) + u * (d / 2), -beta, NEIGHBOR_LENGTH_M, NEIGHBOR_WIDTH_M) }
        } else {
            emptyList()
        }
        val rearAxle = entrance(0) + u * (d - END_MARGIN_M - g.wheelbaseM - g.frontOverhangM)
        val lowest = stall(0).minOf { it.y }
        val span = 3.4 * pitch
        return ScenarioLayout(
            spec = spec,
            goal = Pose(rearAxle.x, rearAxle.y, -beta),
            target = stall(0),
            lines = lines,
            cars = cars.map { it.toList() },
            walls = listOf(Segment(Vec2(-span, a), Vec2(span, a))),
            obstacles = cars.map { BoxObstacle(it, CAR_CLEARANCE_M, "옆 차") } +
                LineObstacle(Vec2(0.0, -1.0), -a, WALL_CLEARANCE_M, "통로 반대편"),
            stallPitchM = pitch,
            targetRefX = 0.0,
            parkedCarsOuterY = 0.0,
            minX = -span,
            maxX = span,
            minY = lowest - 0.6,
            maxY = a + 0.6,
        )
    }
}
