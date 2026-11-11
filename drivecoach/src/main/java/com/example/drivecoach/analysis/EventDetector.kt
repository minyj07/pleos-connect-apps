package com.example.drivecoach.analysis

import com.example.drivecoach.model.DrivingEvent
import com.example.drivecoach.model.EventType
import kotlin.math.max
import kotlin.math.min

/**
 * Turns acceleration estimates into driving events.
 *
 * Harsh episodes open when |a| crosses the limit and close when it falls below `limit × releaseFactor`;
 * they count only if they last [Thresholds.minEventMs]. Segments with cruise control active are skipped
 * because the car, not the driver, chose that acceleration.
 */
class EventDetector(
    private val th: Thresholds,
    private val tripStartMs: Long,
) {
    private val events = mutableListOf<DrivingEvent>()

    private var accel: Episode? = null
    private var decel: Episode? = null
    private var pendingStop: Episode? = null

    // "가속 직후 제동": end of the last acceleration phase, then a watch window after the brake goes down
    private var inAccelPhase = false
    private var accelPhaseEndMs: Long? = null
    private var brakeWatchStartMs: Long? = null
    private var lastBrake: Boolean? = null

    private class Episode(val start: AccelPoint) {
        var last: AccelPoint = start
        var peak: Float = start.accelMps2
    }

    fun add(p: AccelPoint) {
        val a = p.accelMps2
        val cruise = p.sample.cruise

        // Harsh acceleration
        val acc = accel
        when {
            !cruise && a >= th.harshAccelMps2 ->
                if (acc == null) accel = Episode(p) else acc.extend(p, max(acc.peak, a))
            acc != null && (cruise || a < th.harshAccelMps2 * th.releaseFactor) -> {
                closeAccel(acc)
                accel = null
            }
            acc != null -> acc.last = p
        }

        // Harsh deceleration
        val dec = decel
        when {
            !cruise && a <= -th.harshDecelMps2 ->
                if (dec == null) decel = Episode(p) else dec.extend(p, min(dec.peak, a))
            dec != null && (cruise || a > -th.harshDecelMps2 * th.releaseFactor) -> {
                closeDecel(dec)
                decel = null
            }
            dec != null -> dec.last = p
        }

        // A closed braking episode becomes a hard stop if the car stops shortly after.
        pendingStop?.let { pending ->
            if (p.speedMps < th.lowSpeedMps) {
                emitDecel(pending, stopped = true)
                pendingStop = null
            } else if (p.tMs - pending.last.tMs > th.hardStopWindowMs) {
                emitDecel(pending, stopped = false)
                pendingStop = null
            }
        }

        trackAccelThenBrake(p)
    }

    /** Closes open episodes and returns all events in time order. */
    fun finish(): List<DrivingEvent> {
        accel?.let { closeAccel(it) }
        accel = null
        decel?.let { closeDecel(it) }
        decel = null
        pendingStop?.let { emitDecel(it, stopped = it.last.speedMps < th.lowSpeedMps * 2) }
        pendingStop = null
        return events.sortedBy { it.atS }
    }

    private fun Episode.extend(p: AccelPoint, newPeak: Float) {
        last = p
        peak = newPeak
    }

    private fun closeAccel(e: Episode) {
        if (e.last.tMs - e.start.tMs < th.minEventMs) return
        val from = e.start.speedMps
        val to = e.last.speedMps
        val type = if (from < th.lowSpeedMps) {
            if (to - from < th.harshStartMinGainMps) return
            EventType.HARSH_START
        } else {
            EventType.HARSH_ACCEL
        }
        record(type, e)
    }

    private fun closeDecel(e: Episode) {
        if (e.last.tMs - e.start.tMs < th.minEventMs) return
        if (e.last.speedMps < th.lowSpeedMps) emitDecel(e, stopped = true) else {
            pendingStop?.let { emitDecel(it, stopped = false) }
            pendingStop = e
        }
    }

    private fun emitDecel(e: Episode, stopped: Boolean) {
        record(if (stopped) EventType.HARD_STOP else EventType.HARSH_BRAKE, e, toMps = if (stopped) 0f else null)
    }

    private fun record(type: EventType, e: Episode, toMps: Float? = null) {
        val atS = (e.start.tMs - tripStartMs) / 1000f
        val endS = (e.last.tMs - tripStartMs) / 1000f
        val fromKmh = e.start.speedMps * 3.6f
        val toKmh = (toMps ?: e.last.speedMps) * 3.6f
        val prev = events.lastOrNull { it.type == type }
        if (prev != null && (atS - (prev.atS + prev.durationS)) * 1000 < th.mergeGapMs) {
            events.remove(prev)
            events += prev.copy(
                durationS = endS - prev.atS,
                peakMps2 = if (type == EventType.HARSH_ACCEL || type == EventType.HARSH_START) {
                    max(prev.peakMps2, e.peak)
                } else {
                    min(prev.peakMps2, e.peak)
                },
                toKmh = toKmh,
            )
            return
        }
        events += DrivingEvent(type, atS, endS - atS, e.peak, fromKmh, toKmh)
    }

    private fun trackAccelThenBrake(p: AccelPoint) {
        val a = p.accelMps2
        val brake = p.sample.brake
        if (brake == null || p.sample.cruise) {
            lastBrake = brake
            return
        }
        if (a >= th.gentleAccelMps2) {
            inAccelPhase = true
        } else if (inAccelPhase && a < th.gentleAccelMps2 / 2) {
            inAccelPhase = false
            accelPhaseEndMs = p.tMs
        }

        if (lastBrake == false && brake) {
            val end = accelPhaseEndMs
            if (end != null && p.tMs - end <= th.accelThenBrakeWindowMs) brakeWatchStartMs = p.tMs
        }
        lastBrake = brake

        val watchStart = brakeWatchStartMs ?: return
        if (p.tMs - watchStart > th.accelThenBrakeWindowMs) {
            brakeWatchStartMs = null
        } else if (a <= -th.accelThenBrakeDecelMps2) {
            events += DrivingEvent(
                type = EventType.ACCEL_THEN_BRAKE,
                atS = (watchStart - tripStartMs) / 1000f,
                durationS = (p.tMs - watchStart) / 1000f,
                peakMps2 = a,
                fromKmh = p.speedMps * 3.6f,
                toKmh = p.speedMps * 3.6f,
            )
            brakeWatchStartMs = null
            accelPhaseEndMs = null
        }
    }
}
