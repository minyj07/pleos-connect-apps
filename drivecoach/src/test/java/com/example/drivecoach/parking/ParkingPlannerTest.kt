package com.example.drivecoach.parking

import com.example.drivecoach.model.Gear
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class ParkingPlannerTest {

    private val scenarios = listOf(
        ScenarioSpec.defaults(ParkingType.REVERSE_PERPENDICULAR),
        ScenarioSpec.defaults(ParkingType.FORWARD_PERPENDICULAR),
        ScenarioSpec.defaults(ParkingType.PARALLEL),
        ScenarioSpec.defaults(ParkingType.ANGLED, angleDeg = 45),
        ScenarioSpec.defaults(ParkingType.ANGLED, angleDeg = 60),
    )

    @Test
    fun arcIsExact() {
        val start = Pose(1.0, 2.0, 0.3)
        val k = 0.2
        val oneShot = start.advance(k, 3.0)
        var stepped = start
        repeat(300) { stepped = stepped.advance(k, 0.01) }
        assertEquals(oneShot.x, stepped.x, 1e-9)
        assertEquals(oneShot.y, stepped.y, 1e-9)
        assertEquals(oneShot.theta, stepped.theta, 1e-9)
        val circle = start.advance(k, 2 * PI / k)
        assertEquals(start.x, circle.x, 1e-9)
        assertEquals(start.y, circle.y, 1e-9)
    }

    @Test
    fun ioniq5TurningGeometryMatchesTheSpecSheetDerivation() {
        val g = CarPresets.IONIQ_5
        assertEquals(4.37, g.rearAxleRadiusM, 0.05)
        assertEquals(34.5, g.maxSteerRad * 180 / PI, 0.6)
        assertEquals(1.0, g.steerForCurvature(-g.maxCurvature), 1e-9) // right turn = positive steer
        assertEquals(-g.maxCurvature, g.curvatureForSteer(1.0), 1e-9)
    }

    @Test
    fun aaosTurningValueIsClassifiedAsRadiusOrDiameter() {
        val radius = VehicleGeometry.fromAaosDimensions(intArrayOf(1605, 4655, 1890, 2100, 3000, 1638, 1647, 6110))!!
        assertEquals(5.99, radius.turningRadiusM, 0.01)
        val diameter = VehicleGeometry.fromAaosDimensions(intArrayOf(1605, 4655, 1890, 2100, 3000, 1638, 1647, 12220))!!
        assertEquals(5.99, diameter.turningRadiusM, 0.01)
        val garbage = VehicleGeometry.fromAaosDimensions(intArrayOf(1605, 4655, 1890, 2100, 3000, 1638, 1647, 0), fallbackTurningRadiusM = 5.5)!!
        assertEquals(5.5, garbage.turningRadiusM, 1e-9)
        assertNull(VehicleGeometry.fromAaosDimensions(intArrayOf(0, 0, 0, 0, 0, 0, 0, 0)))
    }

    @Test
    fun reversePerpendicularStartMatchesClosedForm() {
        val g = CarPresets.IONIQ_5
        val layout = ScenarioBuilder.build(ScenarioSpec.defaults(ParkingType.REVERSE_PERPENDICULAR), g)
        val r = g.rearAxleRadiusM
        val k = g.maxCurvature
        val alpha = 20 * PI / 180
        val d = 1.2
        val moves = listOf(
            Move(Gear.DRIVE, k, alpha / k, MoveRole.PRE_ANGLE),
            Move(Gear.REVERSE, -k, (PI / 2 - alpha) / k, MoveRole.TURN_IN),
            Move(Gear.REVERSE, 0.0, d, MoveRole.STRAIGHT_IN),
        )
        val p0 = ParkingPlanner.startPose(layout.goal, moves)
        val yf = layout.goal.y
        assertEquals(r * (1 - 2 * sin(alpha)), p0.x, 1e-9)
        assertEquals(yf + d + r * (2 * cos(alpha) - 1), p0.y, 1e-9)
        assertEquals(0.0, p0.theta, 1e-9)
    }

    @Test
    fun parallelAngleSolvesTheLateralShift() {
        val r = 4.4
        for (d in listOf(0.0, 0.5, 1.2)) {
            val dy = 2.0
            val phi = ParkingPlanner.solveParallelAngle(dy, d, r)!!
            assertEquals(dy, 2 * r * (1 - cos(phi)) + d * sin(phi), 1e-9)
        }
    }

    @Test
    fun everyPresetFitsEveryDefaultScenarioOnBothSides() {
        for (g in CarPresets.all) for (spec in scenarios) for (side in Side.entries) {
            // Nose-first between two parked cars needs more than the 6.0 m minimum aisle for long-wheelbase
            // cars (the outer front corner sweeps into the neighbour); the planner must then say so instead.
            if (spec.type == ParkingType.FORWARD_PERPENDICULAR) {
                val tight = ParkingPlanner.plan(spec.copy(side = side), g)
                if (!tight.feasible) assertTrue("${g.name}: ${tight.requirement}", tight.requirement!!.contains("통로 폭"))
            }
            val fitting = if (spec.type == ParkingType.FORWARD_PERPENDICULAR) spec.copy(aisleWidthM = 7.0) else spec
            val plan = ParkingPlanner.plan(fitting.copy(side = side), g)
            val name = "${g.name} ${spec.type} ${spec.angleDeg} $side"
            assertTrue("$name not feasible (clearance ${plan.clearanceM} to ${plan.closestLabel}, ${plan.requirement})", plan.feasible)
            assertNull(name, plan.requirement)

            val end = plan.poses.last().pose
            assertEquals(name, plan.layout.goal.x, end.x, 0.01)
            assertEquals(name, plan.layout.goal.y, end.y, 0.01)
            assertEquals(name, 0.0, angleDiff(plan.layout.goal.theta, end.theta), 0.01)
            assertInside(name, footprint(end, g), plan.layout.target, tolerance = if (spec.type == ParkingType.PARALLEL) 0.15 else 0.02)

            assertEquals(Gear.DRIVE, plan.steps.first().gear)
            assertEquals(Gear.PARK, plan.steps.last().gear)
            assertTrue(name, plan.steps.all { it.title.isNotBlank() && it.detail.isNotBlank() })
        }
    }

    @Test
    fun leftSidePlanIsTheMirrorImage() {
        val g = CarPresets.EV6
        for (spec in scenarios) {
            val right = ParkingPlanner.plan(spec.copy(side = Side.RIGHT), g)
            val left = ParkingPlanner.plan(spec.copy(side = Side.LEFT), g)
            assertEquals(right.poses.size, left.poses.size)
            right.poses.zip(left.poses).forEach { (r, l) ->
                assertEquals(r.pose.x, l.pose.x, 1e-6)
                assertEquals(-r.pose.y, l.pose.y, 1e-6)
                assertEquals(-r.pose.theta, l.pose.theta, 1e-6)
            }
            right.steps.zip(left.steps).forEach { (r, l) ->
                assertEquals(-r.steer, l.steer, 1e-9)
                if (r.title.contains("오른쪽")) assertTrue(l.title.contains("왼쪽"))
            }
        }
    }

    @Test
    fun narrowAisleExplainsWhatWouldFit() {
        val g = CarPresets.IONIQ_5
        val plan = ParkingPlanner.plan(ScenarioSpec.defaults(ParkingType.REVERSE_PERPENDICULAR).copy(aisleWidthM = 3.0), g)
        assertTrue(!plan.feasible)
        assertNotNull(plan.requirement)
        assertTrue(plan.requirement!!.contains("통로 폭"))
    }

    @Test
    fun previewFollowsTheSteeringInput() {
        val g = CarPresets.IONIQ_5
        val start = Pose(0.0, 0.0, 0.0)
        val straight = ParkingPlanner.preview(start, g, 0.0, Gear.DRIVE).last()
        assertEquals(6.0, straight.x, 1e-9)
        val reverseRight = ParkingPlanner.preview(start, g, 1.0, Gear.REVERSE).last()
        assertTrue(reverseRight.x < 0 && reverseRight.theta > 0) // reversing with right lock swings the nose left
    }

    private fun angleDiff(a: Double, b: Double): Double {
        var d = a - b
        while (d > PI) d -= 2 * PI
        while (d < -PI) d += 2 * PI
        return abs(d)
    }

    /** Every body corner lies inside the (convex) target polygon, up to [tolerance] metres. */
    private fun assertInside(name: String, body: Array<Vec2>, target: List<Vec2>, tolerance: Double) {
        val n = target.size
        // orientation-independent half-plane test
        val area = (0 until n).sumOf { i -> target[i].x * target[(i + 1) % n].y - target[(i + 1) % n].x * target[i].y }
        val sign = if (area > 0) 1.0 else -1.0
        for (c in body) {
            for (i in 0 until n) {
                val a = target[i]
                val b = target[(i + 1) % n]
                val edge = b - a
                val len = edge.length()
                val cross = (edge.x * (c.y - a.y) - edge.y * (c.x - a.x)) / len * sign
                assertTrue("$name: corner $c outside target by ${-cross} m", cross >= -tolerance)
            }
        }
    }
}
