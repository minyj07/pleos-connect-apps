package com.example.drivecoach.analysis

import com.example.drivecoach.analysis.SyntheticDrive.Companion.analyze
import com.example.drivecoach.model.EventType
import com.example.drivecoach.model.Gear
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HabitAndParkingTest {

    @Test
    fun distanceAndSpeedBandsAreIntegrated() {
        val d = SyntheticDrive().apply { ramp(36.0, 0.1); hold(60.0) }
        val stats = analyze(d.samples)!!.summary.stats
        assertEquals(0.6, stats.distanceKm.toDouble(), 0.01)
        assertEquals(60.0, stats.movingS.toDouble(), 0.5)
        assertEquals(60.0, stats.speedBandS[1].toDouble(), 0.5) // 30–60 km/h band
    }

    @Test
    fun onlyBrakePressesAboveWalkingSpeedCount() {
        val d = SyntheticDrive().apply {
            hold(1.0); ramp(30.0, 10.0); hold(5.0)
            brake = true; ramp(10.0, 6.0); brake = false; hold(5.0) // counts
            ramp(0.0, 4.0)
            brake = true; hold(20.0); brake = false // red-light hold: starts at 0 km/h, ignored
            ramp(30.0, 10.0); hold(30.0)
        }
        val stats = analyze(d.samples)!!.summary.stats
        assertEquals(1, stats.brakePresses)
        assertEquals(6.0, stats.avgBrakePressS!!.toDouble(), 0.3)
    }

    @Test
    fun coastShareSplitsDecelerationEnergyByBrakeUse() {
        val d = SyntheticDrive().apply {
            hold(1.0); ramp(60.0, 20.0); hold(10.0)
            ramp(30.0, 15.0) // lift-off: regen / coasting
            hold(10.0)
            brake = true; ramp(0.0, 15.0); brake = false
            hold(2.0)
        }
        val share = analyze(d.samples)!!.summary.stats.coastShare
        assertNotNull(share)
        // 60→30 removes (60²−30²)/2 and 30→0 removes 30²/2 (in (m/s)²): 75 % without the brake
        assertEquals(0.75, share!!.toDouble(), 0.08)
    }

    @Test
    fun parkingSessionCountsDirectionChangesAndSteering() {
        val d = SyntheticDrive().apply {
            hold(1.0); ramp(30.0, 8.0); hold(20.0); ramp(5.0, 6.0) // arriving
            ramp(0.0, 2.0); hold(1.0)
            gear = Gear.REVERSE; steer = 0.9f; ramp(4.0, 2.0); hold(4.0); ramp(0.0, 1.0)
            gear = Gear.DRIVE; steer = -0.4f; ramp(3.0, 1.0); hold(2.0); ramp(0.0, 1.0)
            gear = Gear.REVERSE; steer = 0f; ramp(3.0, 1.0); hold(3.0); ramp(0.0, 1.0)
            gear = Gear.PARK; hold(2.0)
        }
        val p = analyze(d.samples)!!.summary.parking!!
        assertEquals(3, p.directionChanges) // D→R, R→D, D→R
        assertEquals(90, p.maxSteerPct)
        assertTrue(p.endedInReverse)
        assertTrue(p.stops >= 2)
    }

    @Test
    fun scoreDropsWithWeightedEventRate() {
        assertEquals(100, Scoring.smoothness(emptyMap(), 25f))
        assertEquals(88, Scoring.smoothness(mapOf(EventType.HARSH_BRAKE to 2), 10f))
        // a single event on a 1 km hop is normalised as 3 km
        assertEquals(80, Scoring.smoothness(mapOf(EventType.HARD_STOP to 1), 1f))
        assertEquals(100, Scoring.smoothness(mapOf(EventType.ACCEL_THEN_BRAKE to 5), 10f))
    }

    @Test
    fun tipsPraiseSmoothDrivingAndFlagHarshBraking() {
        val smooth = SyntheticDrive().apply { hold(1.0); ramp(50.0, 14.0); hold(120.0); ramp(0.0, 12.0) }
        val tips = CoachingTips.forTrip(analyze(smooth.samples)!!.summary)
        assertTrue(tips.first().praise)

        val stats = analyze(smooth.samples)!!.summary.stats
        val harsh = CoachingTips.forStats(stats, mapOf(EventType.HARD_STOP to 3))
        assertTrue(harsh.any { it.title.contains("급제동") })
    }
}
