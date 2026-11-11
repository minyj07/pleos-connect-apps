package com.example.drivecoach.analysis

import com.example.drivecoach.analysis.SyntheticDrive.Companion.analyze
import com.example.drivecoach.model.EventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EventDetectorTest {

    private fun events(build: SyntheticDrive.() -> Unit): Map<EventType, Int> {
        val d = SyntheticDrive(quantizeKmh = true).apply(build)
        return analyze(d.samples)!!.summary.events
    }

    @Test
    fun gentleCityDrivingHasNoEvents() {
        val e = events {
            hold(2.0); ramp(40.0, 12.0); hold(30.0); ramp(0.0, 9.0); hold(3.0)
            ramp(50.0, 14.0); hold(60.0); ramp(20.0, 8.0); hold(10.0); ramp(0.0, 5.0); hold(3.0)
        }
        assertTrue("unexpected events: $e", e.isEmpty())
    }

    @Test
    fun hardLaunchFromStandstillIsAHarshStart() {
        val e = events { hold(2.0); ramp(50.0, 4.0); hold(30.0); ramp(0.0, 15.0); hold(2.0) }
        assertEquals(1, e[EventType.HARSH_START])
        assertEquals(null, e[EventType.HARSH_ACCEL])
    }

    @Test
    fun strongOvertakeIsAHarshAcceleration() {
        val e = events { hold(1.0); ramp(30.0, 12.0); hold(10.0); ramp(80.0, 4.0); hold(20.0); ramp(0.0, 25.0); hold(2.0) }
        assertEquals(1, e[EventType.HARSH_ACCEL])
        assertEquals(null, e[EventType.HARSH_START])
    }

    @Test
    fun brakingToAStandstillIsAHardStop() {
        val e = events { hold(1.0); ramp(60.0, 20.0); hold(10.0); ramp(0.0, 4.0); hold(3.0) }
        assertEquals(1, e[EventType.HARD_STOP])
        assertEquals(null, e[EventType.HARSH_BRAKE])
    }

    @Test
    fun hardSlowdownThatKeepsRollingIsHarshBraking() {
        val e = events { hold(1.0); ramp(80.0, 25.0); hold(10.0); ramp(40.0, 3.0); hold(20.0); ramp(0.0, 12.0); hold(2.0) }
        assertEquals(1, e[EventType.HARSH_BRAKE])
        assertEquals(null, e[EventType.HARD_STOP])
    }

    @Test
    fun cruiseControlSegmentsAreIgnored() {
        val e = events {
            hold(1.0); ramp(80.0, 25.0)
            cruise = true; hold(5.0); ramp(40.0, 3.0); hold(5.0); cruise = false
            hold(5.0); ramp(0.0, 12.0); hold(2.0)
        }
        assertEquals(null, e[EventType.HARSH_BRAKE])
    }

    @Test
    fun acceleratingThenBrakingShortlyAfterIsFlagged() {
        val e = events {
            hold(1.0); ramp(50.0, 7.0) // ~2 m/s²: not harsh
            hold(1.0)
            brake = true; ramp(25.0, 3.0); brake = false // -2.3 m/s² with the pedal
            hold(40.0); ramp(0.0, 10.0); hold(2.0) // long enough (> 300 m) to count as a drive
        }
        assertEquals(1, e[EventType.ACCEL_THEN_BRAKE])
        assertEquals(null, e[EventType.HARSH_BRAKE])
    }

    @Test
    fun shortManoeuvreHasNoEventsAndNoScore() {
        val d = SyntheticDrive().apply { hold(1.0); ramp(8.0, 3.0); hold(5.0); ramp(0.0, 2.0); hold(1.0) }
        val r = analyze(d.samples)!!
        assertEquals(com.example.drivecoach.model.TripKind.MANEUVER, r.summary.kind)
        assertEquals(null, r.summary.score)
    }
}
