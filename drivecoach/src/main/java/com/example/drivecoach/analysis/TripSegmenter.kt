package com.example.drivecoach.analysis

import com.example.drivecoach.model.EndReason
import com.example.drivecoach.model.Gear
import com.example.drivecoach.model.Sample

/**
 * Splits the continuous sample stream into trips.
 *
 * - Start: the gear leaves P, or (gear unknown) the car moves faster than 1 km/h. Speed-triggered
 *   starts include a short pre-roll so the acceleration window sees the car standing still.
 * - End: ignition OFF/ACC; P held for [parkEndMs] (the trip ends when P was engaged, so drop-offs and
 *   drive-throughs stay one trip); standstill for [longStopEndMs] when the gear is unknown; or no
 *   samples for [gapEndMs] (the head unit went to sleep) - then the trip ends at the last sample.
 *
 * Times are the samples' monotonic timestamps, so replaying recorded samples gives the same trips.
 */
class TripSegmenter(
    private val listener: Listener,
    private val parkEndMs: Long = 3 * 60_000L,
    private val longStopEndMs: Long = 15 * 60_000L,
    private val gapEndMs: Long = 120_000L,
    private val preRollMs: Long = 3_000L,
) {
    interface Listener {
        fun onTripStart(first: Sample, preRoll: List<Sample>)
        fun onTripSample(s: Sample)
        fun onTripEnd(endTMs: Long, reason: EndReason)
    }

    var active: Boolean = false
        private set

    private val preRoll = ArrayDeque<Sample>()
    private var last: Sample? = null
    private var parkedSinceMs: Long? = null
    private var stoppedSinceMs: Long? = null

    fun add(s: Sample) {
        val prev = last
        if (prev != null && s.tMs <= prev.tMs) return
        if (active && prev != null && s.tMs - prev.tMs > gapEndMs) end(prev.tMs, EndReason.SIGNAL_GAP)
        last = s
        if (active) onActive(s) else onIdle(s)
    }

    /** Called periodically with the current monotonic time; closes a trip whose signals stopped. */
    fun tick(nowMs: Long) {
        val prev = last ?: return
        if (active && nowMs - prev.tMs > gapEndMs) end(prev.tMs, EndReason.SIGNAL_GAP)
    }

    /** The time base changed (e.g. a demo drive ended): close any trip and start over. */
    fun reset() {
        val prev = last
        if (active && prev != null) end(prev.tMs, EndReason.SIGNAL_GAP)
        last = null
        preRoll.clear()
    }

    private fun onIdle(s: Sample) {
        val startByGear = s.gear != Gear.UNKNOWN && s.gear != Gear.PARK && !s.ignitionOff
        val startBySpeed = s.gear == Gear.UNKNOWN && s.speedMps > START_SPEED_MPS
        if (startByGear || startBySpeed) {
            val roll = if (startBySpeed) preRoll.filter { it.tMs >= s.tMs - preRollMs } else emptyList()
            preRoll.clear()
            active = true
            parkedSinceMs = null
            stoppedSinceMs = null
            listener.onTripStart(s, roll)
            return
        }
        preRoll.addLast(s)
        while (preRoll.isNotEmpty() && preRoll.first().tMs < s.tMs - preRollMs) preRoll.removeFirst()
    }

    private fun onActive(s: Sample) {
        if (s.ignitionOff) {
            listener.onTripSample(s)
            end(s.tMs, EndReason.IGNITION_OFF)
            return
        }
        listener.onTripSample(s)

        if (s.gear == Gear.PARK) {
            val since = parkedSinceMs ?: s.tMs.also { parkedSinceMs = it }
            if (s.tMs - since >= parkEndMs) end(since, EndReason.PARKED)
            return
        }
        parkedSinceMs = null

        if (s.gear == Gear.UNKNOWN && s.speedMps < STOPPED_MPS) {
            val since = stoppedSinceMs ?: s.tMs.also { stoppedSinceMs = it }
            if (s.tMs - since >= longStopEndMs) end(since, EndReason.LONG_STOP)
        } else {
            stoppedSinceMs = null
        }
    }

    private fun end(atMs: Long, reason: EndReason) {
        active = false
        parkedSinceMs = null
        stoppedSinceMs = null
        preRoll.clear()
        listener.onTripEnd(atMs, reason)
    }

    private companion object {
        const val START_SPEED_MPS = 1f / 3.6f
        const val STOPPED_MPS = 0.3f
    }
}
