package com.example.drivecoach.sim

import com.example.drivecoach.analysis.TripAnalyzer
import com.example.drivecoach.analysis.TripSegmenter
import com.example.drivecoach.model.EndReason
import com.example.drivecoach.model.EventType
import com.example.drivecoach.model.Sample
import com.example.drivecoach.model.TripKind
import com.example.drivecoach.model.TripRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The demo drives must produce what they claim, through the same segmenter + analyzer as real drives. */
class ScriptedDriveReplayTest {

    private fun replay(name: String): List<TripRecord> {
        val records = mutableListOf<TripRecord>()
        var analyzer: TripAnalyzer? = null
        val segmenter = TripSegmenter(object : TripSegmenter.Listener {
            override fun onTripStart(first: Sample, preRoll: List<Sample>) {
                val a = TripAnalyzer(name, 0L, (preRoll.firstOrNull() ?: first).tMs)
                (preRoll + first).forEach(a::add)
                analyzer = a
            }

            override fun onTripSample(s: Sample) {
                analyzer?.add(s)
            }

            override fun onTripEnd(endTMs: Long, reason: EndReason) {
                analyzer?.finish(endTMs, reason)?.let(records::add)
                analyzer = null
            }
        })
        ScriptedDrives.frames(name).forEachIndexed { i, f ->
            segmenter.add(Sample(1_000L + i * 100L, f.kmh / 3.6f, f.gear, f.brake, f.steer, f.cruise, f.ignitionOff))
        }
        return records
    }

    @Test
    fun cityDriveHasEveryHarshEventAndAReverseParking() {
        val r = replay(ScriptedDrives.CITY).single()
        val s = r.summary
        assertEquals(TripKind.DRIVE, s.kind)
        assertEquals(EndReason.IGNITION_OFF, s.endReason)
        assertEquals(1, s.count(EventType.HARSH_START))
        assertEquals(1, s.count(EventType.HARSH_ACCEL))
        assertEquals(1, s.count(EventType.HARSH_BRAKE))
        assertEquals(1, s.count(EventType.HARD_STOP))
        val parking = s.parking!!
        assertEquals(3, parking.directionChanges)
        assertTrue(parking.endedInReverse)
        assertEquals(100, parking.maxSteerPct)
        assertNotNull(s.stats.coastShare)
    }

    @Test
    fun cruiseControlBrakingIsNotCountedAgainstTheDriver() {
        val s = replay(ScriptedDrives.HIGHWAY).single().summary
        assertEquals(1, s.count(EventType.HARSH_BRAKE))
        assertEquals(0, s.count(EventType.HARSH_ACCEL))
        assertTrue(s.stats.cruiseS > 170f)
    }

    @Test
    fun parkingOnlyIsAManoeuvre() {
        val s = replay(ScriptedDrives.PARKING_ONLY).single().summary
        assertEquals(TripKind.MANEUVER, s.kind)
        assertEquals(3, s.parking!!.directionChanges)
    }
}
