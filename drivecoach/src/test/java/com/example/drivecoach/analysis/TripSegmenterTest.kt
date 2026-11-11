package com.example.drivecoach.analysis

import com.example.drivecoach.model.EndReason
import com.example.drivecoach.model.Gear
import com.example.drivecoach.model.Sample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TripSegmenterTest {

    private class Recorder : TripSegmenter.Listener {
        val starts = mutableListOf<Long>()
        val ends = mutableListOf<Pair<Long, EndReason>>()
        var preRollSize = 0
        var samples = 0
        override fun onTripStart(first: Sample, preRoll: List<Sample>) {
            starts += first.tMs
            preRollSize = preRoll.size
        }
        override fun onTripSample(s: Sample) {
            samples++
        }
        override fun onTripEnd(endTMs: Long, reason: EndReason) {
            ends += endTMs to reason
        }
    }

    private fun stream(vararg parts: Pair<Gear, Int>, speed: Float = 5f, startMs: Long = 0L): List<Sample> {
        var t = startMs
        return parts.flatMap { (gear, seconds) ->
            (0 until seconds * 10).map {
                t += 100
                Sample(t, if (gear == Gear.PARK) 0f else speed, gear)
            }
        }
    }

    @Test
    fun shortStopInParkKeepsOneTripAndLongParkEndsItAtParkEntry() {
        val rec = Recorder()
        val seg = TripSegmenter(rec)
        val s = stream(Gear.PARK to 10, Gear.DRIVE to 60, Gear.PARK to 30, Gear.DRIVE to 30, Gear.PARK to 240)
        s.forEach(seg::add)
        assertEquals(1, rec.starts.size)
        assertEquals(1, rec.ends.size)
        val secondParkEntry = s.first { it.tMs > 130_000 && it.gear == Gear.PARK }.tMs
        assertEquals(secondParkEntry to EndReason.PARKED, rec.ends.single())
    }

    @Test
    fun reverseDriveShufflesStayOneTrip() {
        val rec = Recorder()
        val seg = TripSegmenter(rec)
        stream(Gear.DRIVE to 20, Gear.REVERSE to 5, Gear.DRIVE to 3, Gear.REVERSE to 4, Gear.PARK to 200).forEach(seg::add)
        assertEquals(1, rec.starts.size)
        assertEquals(EndReason.PARKED, rec.ends.single().second)
    }

    @Test
    fun signalGapEndsTheTripAtTheLastSample() {
        val rec = Recorder()
        val seg = TripSegmenter(rec)
        val before = stream(Gear.DRIVE to 30)
        before.forEach(seg::add)
        stream(Gear.DRIVE to 10, startMs = before.last().tMs + 130_000).forEach(seg::add)
        assertEquals(before.last().tMs to EndReason.SIGNAL_GAP, rec.ends.first())
        assertEquals(2, rec.starts.size)
    }

    @Test
    fun tickClosesATripWhenSamplesStop() {
        val rec = Recorder()
        val seg = TripSegmenter(rec)
        val s = stream(Gear.DRIVE to 10)
        s.forEach(seg::add)
        seg.tick(s.last().tMs + 60_000)
        assertTrue(rec.ends.isEmpty())
        seg.tick(s.last().tMs + 121_000)
        assertEquals(s.last().tMs to EndReason.SIGNAL_GAP, rec.ends.single())
    }

    @Test
    fun ignitionOffEndsImmediately() {
        val rec = Recorder()
        val seg = TripSegmenter(rec)
        val s = stream(Gear.DRIVE to 10)
        s.forEach(seg::add)
        seg.add(Sample(s.last().tMs + 100, 0f, Gear.PARK, ignitionOff = true))
        assertEquals(EndReason.IGNITION_OFF, rec.ends.single().second)
    }

    @Test
    fun unknownGearStartsOnSpeedWithPreRollAndEndsAfterLongStop() {
        val rec = Recorder()
        val seg = TripSegmenter(rec)
        var t = 0L
        repeat(100) { t += 100; seg.add(Sample(t, 0f)) } // standing, gear unknown
        repeat(50) { t += 100; seg.add(Sample(t, 3f)) } // moving
        assertEquals(1, rec.starts.size)
        assertTrue(rec.preRollSize in 25..30)
        repeat(16 * 600) { t += 100; seg.add(Sample(t, 0f)) } // 16 min standstill
        assertEquals(EndReason.LONG_STOP, rec.ends.single().second)
    }

    @Test
    fun longIdleInDriveDoesNotEndTheTrip() {
        val rec = Recorder()
        val seg = TripSegmenter(rec)
        var t = 0L
        repeat(100) { t += 100; seg.add(Sample(t, 10f, Gear.DRIVE)) }
        repeat(20 * 600) { t += 100; seg.add(Sample(t, 0f, Gear.DRIVE)) } // 20 min in a jam / auto hold
        assertTrue(rec.ends.isEmpty())
    }
}
