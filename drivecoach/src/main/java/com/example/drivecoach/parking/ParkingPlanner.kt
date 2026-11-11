package com.example.drivecoach.parking

import com.example.drivecoach.model.Gear
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

/** What a move is for; drives the wording of the instruction. */
enum class MoveRole { PRE_ANGLE, TURN_IN, BACK_OUT, STRAIGHT_IN, PARALLEL_FIRST, PARALLEL_STRAIGHT, PARALLEL_SECOND, CENTER }

/** Constant-steering move: [length] is the distance travelled (always ≥ 0), [kappa] left positive. */
data class Move(val gear: Gear, val kappa: Double, val length: Double, val role: MoveRole) {
    val signedLength: Double get() = if (gear == Gear.REVERSE) -length else length
    fun mirrored() = copy(kappa = -kappa)
}

data class PlanPose(val pose: Pose, val step: Int)

data class ManeuverStep(
    val gear: Gear,
    /** Fraction of full lock, left negative (same sign as the SDK steering angle). */
    val steer: Double,
    val lengthM: Double,
    val turnDeg: Double,
    val title: String,
    val detail: String,
    val firstPose: Int,
    val lastPose: Int,
)

data class ParkingPlan(
    val spec: ScenarioSpec,
    val geometry: VehicleGeometry,
    val layout: ScenarioLayout,
    val feasible: Boolean,
    /** Smallest distance to anything along the path, and what it was. */
    val clearanceM: Double,
    val closestLabel: String?,
    val closestPoint: Vec2?,
    val steps: List<ManeuverStep>,
    val poses: List<PlanPose>,
    /** Pose where the approach ends and the manoeuvre starts (the reference point). */
    val startPose: Pose,
    /** Driver's eye at [startPose]. */
    val eyeMarker: Vec2,
    /** Steering the first turning move needs: what the P-gear practice aims for. */
    val targetSteer: Double,
    val targetGear: Gear,
    /** Set when no plan fits: what would make it fit. */
    val requirement: String?,
)

/**
 * Plans textbook manoeuvres (full-lock arcs and straights) per scenario.
 *
 * Each template's 1–3 parameters are grid-searched. A candidate is built backwards from the goal pose
 * ("backward fold": the start pose follows exactly from the moves), checked for clearance along the
 * whole path including a straight approach, and the one with the best capped margin and the shortest,
 * simplest path wins. Plans are computed in the canonical right-side frame and mirrored for left stalls.
 */
object ParkingPlanner {

    private const val APPROACH_M = 6.0
    private const val COARSE_STEP_M = 0.05
    private const val COARSE_STEP_RAD = PI / 180
    private const val FINE_STEP_M = 0.02
    private const val FINE_STEP_RAD = PI / 360
    private const val COMFORT_MARGIN_M = 0.3

    fun plan(spec: ScenarioSpec, g: VehicleGeometry): ParkingPlan {
        val canonical = ScenarioBuilder.build(spec.copy(side = Side.RIGHT), g)
        val best = search(canonical, g)
        val requirement = if (best == null || best.eval.margin < 0) requirementFor(spec, g, best?.eval?.closestLabel) else null
        val layout = if (spec.side == Side.LEFT) canonical.mirrored().copy(spec = spec) else canonical.copy(spec = spec)
        if (best == null) {
            return ParkingPlan(
                spec, g, layout, feasible = false, clearanceM = 0.0, closestLabel = null, closestPoint = null,
                steps = emptyList(), poses = emptyList(), startPose = layout.goal, eyeMarker = layout.goal.position,
                targetSteer = 0.0, targetGear = Gear.REVERSE, requirement = requirement,
            )
        }
        val moves = if (spec.side == Side.LEFT) best.moves.map { it.mirrored() } else best.moves
        val start = if (spec.side == Side.LEFT) best.eval.start.mirrored() else best.eval.start
        return assemble(spec, g, layout, moves, start, requirement)
    }

    /** Rear-axle path for the practice overlay: [lengthM] metres with constant [steer] in [gear]. */
    fun preview(start: Pose, g: VehicleGeometry, steer: Double, gear: Gear, lengthM: Double = 6.0): List<Pose> {
        val kappa = g.curvatureForSteer(steer)
        val sign = if (gear == Gear.REVERSE) -1.0 else 1.0
        val n = 40
        return (0..n).map { i -> start.advance(kappa, sign * lengthM * i / n) }
    }

    // ---- search ----

    private class Candidate(val moves: List<Move>, val penalty: Double)

    private class Eval(
        val start: Pose,
        val margin: Double,
        val clearance: Double,
        val closestLabel: String?,
        val closestPoint: Vec2?,
        val length: Double,
    )

    private class Scored(val moves: List<Move>, val eval: Eval, val objective: Double)

    private fun search(layout: ScenarioLayout, g: VehicleGeometry): Scored? {
        var bestFeasible: Scored? = null
        var bestAny: Scored? = null
        for (c in candidates(layout, g)) {
            val e = evaluate(layout, g, c.moves, COARSE_STEP_M, COARSE_STEP_RAD, earlyExit = bestFeasible != null) ?: continue
            val objective = min(e.margin, COMFORT_MARGIN_M) - 0.01 * e.length - c.penalty
            val s = Scored(c.moves, e, objective)
            if (e.margin >= 0 && (bestFeasible == null || objective > bestFeasible.objective)) bestFeasible = s
            if (bestAny == null || e.margin > bestAny.eval.margin) bestAny = s
        }
        val chosen = bestFeasible ?: bestAny ?: return null
        // Re-check the winner at fine resolution (the coarse pass can miss a few centimetres).
        val fine = evaluate(layout, g, chosen.moves, FINE_STEP_M, FINE_STEP_RAD, earlyExit = false) ?: return chosen
        return Scored(chosen.moves, fine, chosen.objective)
    }

    private fun candidates(layout: ScenarioLayout, g: VehicleGeometry): Sequence<Candidate> {
        val k = g.maxCurvature
        return when (layout.spec.type) {
            ParkingType.REVERSE_PERPENDICULAR -> sequence {
                for (alphaDeg in 0..40 step 5) {
                    val alpha = alphaDeg * PI / 180
                    for (d in steps(0.3, 3.0, 0.1)) {
                        val moves = buildList {
                            if (alphaDeg > 0) add(Move(Gear.DRIVE, k, alpha / k, MoveRole.PRE_ANGLE))
                            add(Move(Gear.REVERSE, -k, (PI / 2 - alpha) / k, MoveRole.TURN_IN))
                            add(Move(Gear.REVERSE, 0.0, d, MoveRole.STRAIGHT_IN))
                        }
                        yield(Candidate(moves, penalty = 0.002 * alphaDeg))
                    }
                }
            }
            ParkingType.FORWARD_PERPENDICULAR -> sequence {
                for (radiusFactor in steps(1.0, 1.5, 0.1)) {
                    val kap = k / radiusFactor
                    // Going in nose first, the outer front corner sweeps wide, so the car often has to
                    // finish turning in the aisle and then drive a long way straight in.
                    for (d in steps(0.3, 5.0, 0.1)) {
                        yield(Candidate(listOf(Move(Gear.DRIVE, -kap, (PI / 2) / kap, MoveRole.TURN_IN), Move(Gear.DRIVE, 0.0, d, MoveRole.STRAIGHT_IN)), 0.0))
                    }
                }
                // One correction: turn in part-way, then reverse with the opposite lock - that keeps
                // rotating the car towards the stall while backing away from the neighbour - and turn in again.
                for (psi1 in 30..80 step 10) for (psi2 in 10..50 step 10) {
                    if (psi1 + psi2 > 85) continue
                    val a1 = psi1 * PI / 180
                    val a2 = psi2 * PI / 180
                    for (d in steps(0.3, 4.8, 0.3)) {
                        val moves = listOf(
                            Move(Gear.DRIVE, -k, a1 / k, MoveRole.TURN_IN),
                            Move(Gear.REVERSE, k, a2 / k, MoveRole.BACK_OUT),
                            Move(Gear.DRIVE, -k, (PI / 2 - a1 - a2) / k, MoveRole.TURN_IN),
                            Move(Gear.DRIVE, 0.0, d, MoveRole.STRAIGHT_IN),
                        )
                        yield(Candidate(moves, penalty = 0.15))
                    }
                }
            }
            ParkingType.PARALLEL -> sequence {
                val r = 1 / k
                for (gap in steps(0.5, 1.2, 0.1)) {
                    val y0 = layout.parkedCarsOuterY + gap + g.widthM / 2
                    val dy = y0 - layout.goal.y
                    for (d in steps(0.0, 1.5, 0.25)) {
                        val phi = solveParallelAngle(dy, d, r) ?: continue
                        if (phi <= 0 || phi > PI / 3) continue
                        for (center in steps(0.0, 1.0, 0.25)) {
                            val moves = buildList {
                                add(Move(Gear.REVERSE, -k, phi / k, MoveRole.PARALLEL_FIRST))
                                if (d > 0.01) add(Move(Gear.REVERSE, 0.0, d, MoveRole.PARALLEL_STRAIGHT))
                                add(Move(Gear.REVERSE, k, phi / k, MoveRole.PARALLEL_SECOND))
                                if (center > 0.01) add(Move(Gear.DRIVE, 0.0, center, MoveRole.CENTER))
                            }
                            yield(Candidate(moves, penalty = 0.02 * abs(gap - 0.8)))
                        }
                    }
                }
            }
            ParkingType.ANGLED -> sequence {
                val beta = layout.spec.angleDeg * PI / 180
                for (radiusFactor in steps(1.0, 1.5, 0.1)) {
                    val kap = k / radiusFactor
                    for (d in steps(0.3, 5.0, 0.1)) {
                        yield(Candidate(listOf(Move(Gear.DRIVE, -kap, beta / kap, MoveRole.TURN_IN), Move(Gear.DRIVE, 0.0, d, MoveRole.STRAIGHT_IN)), 0.0))
                    }
                }
            }
        }
    }

    /**
     * Parallel parking with two opposite full-lock arcs of angle φ and a straight d between them shifts
     * the car sideways by Δy = 2R(1 − cos φ) + d·sin φ. Solved for φ.
     */
    fun solveParallelAngle(dy: Double, d: Double, r: Double): Double? {
        val norm = hypot(2 * r, d)
        val arg = (dy - 2 * r) / norm
        if (arg < -1 || arg > 1) return null
        return atan2(2 * r, d) + asin(arg)
    }

    /** Start pose by folding the moves backwards from the goal. */
    fun startPose(goal: Pose, moves: List<Move>): Pose =
        moves.asReversed().fold(goal) { p, m -> p.advance(m.kappa, -m.signedLength) }

    private fun evaluate(layout: ScenarioLayout, g: VehicleGeometry, moves: List<Move>, stepM: Double, stepRad: Double, earlyExit: Boolean): Eval? {
        val start = startPose(layout.goal, moves)
        if (abs(normalizeAngle(start.theta)) > 1e-6) return null
        var margin = Double.MAX_VALUE
        var clearance = Double.MAX_VALUE
        var label: String? = null
        var point: Vec2? = null
        var length = APPROACH_M

        fun check(p: Pose): Boolean {
            val body = footprint(p, g)
            val bodyCenter = p.toWorld(Vec2((g.wheelbaseM + g.frontOverhangM - g.rearOverhangM) / 2, 0.0))
            for (o in layout.obstacles) {
                if (o is BoxObstacle && (o.corners[0] - bodyCenter).length() > g.lengthM + ScenarioBuilder.NEIGHBOR_LENGTH_M) continue
                val c = o.clearance(body)
                val m = c - o.minClearanceM
                if (m < margin) {
                    margin = m
                    clearance = c
                    label = o.label
                    point = o.closestPoint(body)
                }
            }
            return !(earlyExit && margin < -0.3)
        }

        // Straight approach along +x that ends at the start pose.
        val approachSteps = ceil(APPROACH_M / stepM).toInt()
        for (i in 0..approachSteps) {
            if (!check(start.advance(0.0, -APPROACH_M + APPROACH_M * i / approachSteps))) return null
        }
        var p = start
        for (m in moves) {
            val n = maxOf(1, ceil(maxOf(m.length / stepM, abs(m.kappa) * m.length / stepRad)).toInt())
            for (i in 1..n) {
                if (!check(p.advance(m.kappa, m.signedLength * i / n))) return null
            }
            p = p.advance(m.kappa, m.signedLength)
            length += m.length
        }
        return Eval(start, margin, clearance, label, point, length)
    }

    /**
     * Advice text: the smallest aisle (or, for parallel parking blocked by the parked cars, stall length)
     * that makes a plan fit. [binding] is the obstacle that stopped the best attempt.
     */
    private fun requirementFor(spec: ScenarioSpec, g: VehicleGeometry, binding: String?): String {
        val canonical = spec.copy(side = Side.RIGHT)
        val growStall = spec.type == ParkingType.PARALLEL && binding != null && binding != "차로 반대편"
        for (extra in steps(0.5, 4.0, 0.5)) {
            val tried = if (growStall) {
                canonical.copy(stallLengthM = spec.stallLengthM + extra)
            } else {
                canonical.copy(aisleWidthM = spec.aisleWidthM + extra)
            }
            val s = search(ScenarioBuilder.build(tried, g), g)
            if (s != null && s.eval.margin >= 0) {
                return when {
                    growStall -> "주차 칸 길이가 약 ${fmt1(tried.stallLengthM)} m 이상이면 한 번에 들어갈 수 있어요."
                    spec.type == ParkingType.PARALLEL -> "차로 폭이 약 ${fmt1(tried.aisleWidthM)} m 이상이면 한 번에 들어갈 수 있어요."
                    else -> "통로 폭이 약 ${fmt1(tried.aisleWidthM)} m 이상이면 한 번에 들어갈 수 있어요."
                }
            }
        }
        return "이 크기에서는 한 번에 들어가기 어려워요. 칸이나 통로가 더 넓은 곳을 이용하세요."
    }

    // ---- result ----

    private fun assemble(spec: ScenarioSpec, g: VehicleGeometry, layout: ScenarioLayout, moves: List<Move>, start: Pose, requirement: String?): ParkingPlan {
        val poses = mutableListOf<PlanPose>()
        val steps = mutableListOf<ManeuverStep>()
        var margin = Double.MAX_VALUE
        var clearance = Double.MAX_VALUE
        var label: String? = null
        var point: Vec2? = null

        fun add(p: Pose, step: Int) {
            poses += PlanPose(p, step)
            val body = footprint(p, g)
            for (o in layout.obstacles) {
                val c = o.clearance(body)
                if (c - o.minClearanceM < margin) {
                    margin = c - o.minClearanceM
                    clearance = c
                    label = o.label
                    point = o.closestPoint(body)
                }
            }
        }

        val eye = start.toWorld(Vec2(g.driverEyeAheadM, g.driverEyeLeftM))
        val texts = Wording(spec, g, layout, start, eye, moves)

        // Step 0: approach
        val approachSteps = ceil(APPROACH_M / FINE_STEP_M).toInt()
        for (i in 0..approachSteps) add(start.advance(0.0, -APPROACH_M + APPROACH_M * i / approachSteps), 0)
        steps += ManeuverStep(Gear.DRIVE, 0.0, APPROACH_M, 0.0, texts.approachTitle(), texts.approach(), 0, poses.lastIndex)

        var p = start
        moves.forEachIndexed { idx, m ->
            val stepIndex = idx + 1
            val first = poses.size
            val n = maxOf(1, ceil(maxOf(m.length / FINE_STEP_M, abs(m.kappa) * m.length / FINE_STEP_RAD)).toInt())
            for (i in 1..n) add(p.advance(m.kappa, m.signedLength * i / n), stepIndex)
            p = p.advance(m.kappa, m.signedLength)
            val steer = g.steerForCurvature(m.kappa)
            val turnDeg = abs(m.kappa * m.length) * 180 / PI
            steps += ManeuverStep(m.gear, steer, m.length, turnDeg, texts.title(m), texts.detail(m), first, poses.lastIndex)
        }
        steps += ManeuverStep(Gear.PARK, 0.0, 0.0, 0.0, "기어 P", "주차 완료! 기어를 P에 두고 주변을 확인하세요.", poses.lastIndex, poses.lastIndex)

        val firstTurn = moves.firstOrNull { abs(it.kappa) > 1e-9 }
        return ParkingPlan(
            spec = spec,
            geometry = g,
            layout = layout,
            feasible = margin >= 0,
            clearanceM = clearance,
            closestLabel = label,
            closestPoint = point,
            steps = steps,
            poses = poses,
            startPose = start,
            eyeMarker = eye,
            targetSteer = firstTurn?.let { g.steerForCurvature(it.kappa) } ?: 0.0,
            targetGear = firstTurn?.gear ?: Gear.REVERSE,
            requirement = if (margin >= 0) null else requirement ?: requirementFor(spec, g, label),
        )
    }

    private fun steps(from: Double, to: Double, by: Double): List<Double> {
        val n = ((to - from) / by + 1e-9).toInt()
        return (0..n).map { from + it * by }
    }

    private fun normalizeAngle(a: Double): Double {
        var x = a
        while (x > PI) x -= 2 * PI
        while (x < -PI) x += 2 * PI
        return x
    }

    fun fmt1(v: Double): String = ((v * 10).roundToInt() / 10.0).toString()
}

/** Korean instruction texts for a plan (after mirroring, so left/right are already final). */
private class Wording(
    private val spec: ScenarioSpec,
    private val g: VehicleGeometry,
    private val layout: ScenarioLayout,
    private val start: Pose,
    private val eye: Vec2,
    private val moves: List<Move>,
) {
    private val fmt = ParkingPlanner::fmt1

    fun approachTitle(): String = when (spec.type) {
        ParkingType.PARALLEL -> "앞차 옆에 나란히 · 전진"
        else -> "통로를 따라 접근 · 전진"
    }

    fun approach(): String = when (spec.type) {
        ParkingType.PARALLEL -> {
            val gap = abs(start.y - layout.parkedCarsOuterY) - g.widthM / 2
            val myRear = start.x - g.rearOverhangM
            val cue = if (spec.neighbors) {
                val diff = myRear - (layout.stallPitchM - ScenarioBuilder.NEIGHBOR_LENGTH_M / 2)
                when {
                    abs(diff) < 0.15 -> "내 차 뒷범퍼가 앞차 뒷범퍼와 나란해지면 정지하세요."
                    diff > 0 -> "내 차 뒷범퍼가 앞차 뒷범퍼보다 약 ${fmt(diff)} m 앞에 왔을 때 정지하세요."
                    else -> "내 차 뒷범퍼가 앞차 뒷범퍼보다 약 ${fmt(-diff)} m 뒤에 있을 때 정지하세요."
                }
            } else {
                val diff = myRear - layout.stallPitchM / 2
                if (diff >= 0) {
                    "내 차 뒷범퍼가 목표 칸 앞쪽 선을 약 ${fmt(diff)} m 지났을 때 정지하세요."
                } else {
                    "내 차 뒷범퍼가 목표 칸 앞쪽 선보다 약 ${fmt(-diff)} m 뒤에 있을 때 정지하세요."
                }
            }
            "${if (spec.neighbors) "앞차와" else "주차선과"} 약 ${fmt(gap)} m 간격을 두고 나란히 서세요. $cue"
        }
        else -> {
            val gapToLine = abs(start.y) - g.widthM / 2
            val units = (eye.x - layout.targetRefX) / layout.stallPitchM
            val dist = abs(eye.x - layout.targetRefX)
            val cue = when (spec.type) {
                ParkingType.REVERSE_PERPENDICULAR ->
                    "운전석이 목표 칸 가운데를 지나 약 ${halves(units)}칸(${fmt(dist)} m) 더 갔을 때 정지하세요."
                else -> if (units < 0) {
                    "운전석이 목표 칸 가운데보다 약 ${halves(-units)}칸(${fmt(dist)} m) 앞에 왔을 때 정지하세요."
                } else {
                    "운전석이 목표 칸 가운데를 약 ${fmt(dist)} m 지났을 때 정지하세요."
                }
            }
            "통로를 따라 천천히 전진하세요. 주차선과 약 ${fmt(gapToLine)} m 떨어져 갑니다. $cue"
        }
    }

    fun title(m: Move): String {
        val steer = g.steerForCurvature(m.kappa)
        val wheel = when {
            abs(steer) < 0.03 -> "핸들 정중앙"
            abs(steer) >= 0.97 -> "핸들 ${dir(steer)} 끝까지"
            else -> "핸들 ${dir(steer)} ${(abs(steer) * 100).roundToInt()}%"
        }
        return "$wheel · ${if (m.gear == Gear.REVERSE) "후진" else "전진"}"
    }

    fun detail(m: Move): String {
        val deg = (abs(m.kappa * m.length) * 180 / PI).roundToInt()
        val len = fmt(m.length)
        return when (m.role) {
            MoveRole.PRE_ANGLE -> "약 $len m 앞으로 가며 차를 약 $deg° 비스듬히 세운 뒤 정지하세요. 통로가 좁을 때 쓰는 방법이에요."
            MoveRole.TURN_IN -> if (m.gear == Gear.REVERSE) {
                "천천히 후진하다 차가 주차선과 나란해지면(약 $deg° 회전) 정지하세요. 양쪽 사이드미러로 옆 차와의 간격을 확인하세요."
            } else if (moves.count { it.role == MoveRole.TURN_IN } > 1 && moves.indexOf(m) == 0) {
                "천천히 전진하다 차가 약 $deg° 돌면 정지하세요."
            } else {
                "천천히 전진하다 차가 칸과 나란해지면(약 $deg° 회전) 정지하세요. 안쪽 뒷바퀴 쪽이 옆 차에 가까워지니 사이드미러를 확인하세요."
            }
            MoveRole.BACK_OUT -> "약 $len m 뒤로 빼서 각도를 다시 잡으세요."
            MoveRole.STRAIGHT_IN -> if (m.gear == Gear.REVERSE) {
                "핸들을 정중앙으로 돌려 약 $len m 곧게 후진하세요. 뒤 범퍼가 칸 끝에서 약 ${fmt(ScenarioBuilder.END_MARGIN_M)} m 남으면 정지합니다."
            } else {
                "핸들을 정중앙으로 돌려 약 $len m 곧게 들어가세요. 앞 범퍼가 칸 끝에서 약 ${fmt(ScenarioBuilder.END_MARGIN_M)} m 남으면 정지합니다."
            }
            MoveRole.PARALLEL_FIRST -> "천천히 후진하다 차가 약 $deg° 기울면 정지하세요. 차 뒷부분이 연석 쪽으로 들어갑니다."
            MoveRole.PARALLEL_STRAIGHT -> "핸들을 정중앙으로 돌려 약 $len m 곧게 후진하세요."
            MoveRole.PARALLEL_SECOND -> "천천히 후진하며 차가 도로와 나란해지면 정지하세요. 앞차와의 간격을 확인하세요."
            MoveRole.CENTER -> "핸들을 정중앙으로 돌려 약 $len m 앞으로 가서 앞뒤 간격을 맞추세요."
        }
    }

    private fun dir(steer: Double) = if (steer < 0) "왼쪽" else "오른쪽"

    private fun halves(v: Double): String {
        val h = (v * 2).roundToInt() / 2.0
        return if (h % 1.0 == 0.0) h.toInt().toString() else h.toString()
    }
}
