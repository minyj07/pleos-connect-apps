package com.example.drivecoach.ui.parking

import com.example.drivecoach.parking.CarPresets
import com.example.drivecoach.parking.ParkingPlanner
import com.example.drivecoach.parking.ParkingType
import com.example.drivecoach.parking.ScenarioSpec
import com.example.drivecoach.parking.Side
import com.example.drivecoach.parking.footprint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewBoxTest {

    private val scenarios = listOf(
        ScenarioSpec.defaults(ParkingType.REVERSE_PERPENDICULAR),
        ScenarioSpec.defaults(ParkingType.FORWARD_PERPENDICULAR),
        ScenarioSpec.defaults(ParkingType.PARALLEL),
        ScenarioSpec.defaults(ParkingType.ANGLED, angleDeg = 45),
        ScenarioSpec.defaults(ParkingType.ANGLED, angleDeg = 60),
    )

    /** The car must never be drawn half outside the card, wherever its approach starts. */
    @Test
    fun theDrawingHoldsTheLayoutAndTheWholeManoeuvre() {
        val g = CarPresets.IONIQ_5
        for (spec in scenarios) for (side in Side.entries) {
            val plan = ParkingPlanner.plan(spec.copy(side = side), g)
            val box = viewBox(plan)
            val name = "${spec.type} ${spec.angleDeg} $side"
            assertTrue(name, box.minX <= plan.layout.minX.toFloat() && box.maxX >= plan.layout.maxX.toFloat())
            assertTrue(name, box.minY <= plan.layout.minY.toFloat() && box.maxY >= plan.layout.maxY.toFloat())
            plan.poses.forEach { timed ->
                footprint(timed.pose, g).forEach { corner ->
                    assertTrue("$name x ${corner.x}", corner.x >= box.minX - 1e-3 && corner.x <= box.maxX + 1e-3)
                    assertTrue("$name y ${corner.y}", corner.y >= box.minY - 1e-3 && corner.y <= box.maxY + 1e-3)
                }
            }
        }
    }

    /** Where the car stays inside the drawn stalls, as in the design's own drawing, the framing is the layout's. */
    @Test
    fun theDesignsScenarioKeepsTheLayoutsOwnBox() {
        val plan = ParkingPlanner.plan(ScenarioSpec.defaults(ParkingType.REVERSE_PERPENDICULAR), CarPresets.IONIQ_5)
        val box = viewBox(plan)
        assertEquals(plan.layout.minX.toFloat(), box.minX, 1e-4f)
        assertEquals(plan.layout.maxX.toFloat(), box.maxX, 1e-4f)
        assertEquals(plan.layout.minY.toFloat(), box.minY, 1e-4f)
        assertEquals(plan.layout.maxY.toFloat(), box.maxY, 1e-4f)
    }
}
