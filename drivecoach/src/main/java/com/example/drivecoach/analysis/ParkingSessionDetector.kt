package com.example.drivecoach.analysis

import com.example.drivecoach.model.Gear
import com.example.drivecoach.model.ParkingSession
import com.example.drivecoach.model.Sample
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Finds the parking manoeuvre at the end of a trip: the stretch after the car last went faster than
 * 10 km/h. Counts D <-> R switches, stops and the largest steering input.
 */
class ParkingSessionDetector {

    private var start: Sample? = null
    private var prev: Sample? = null
    private var distanceM = 0.0
    private var directionChanges = 0
    private var stops = 0
    private var maxSteer: Float? = null
    private var lastDirection: Gear? = null
    private var wasMoving = false

    fun add(s: Sample) {
        if (s.speedMps > MANEUVER_MAX_MPS) {
            reset()
            return
        }
        if (start == null) start = s
        prev?.let { p ->
            val dtMs = s.tMs - p.tMs
            if (dtMs in 1..MAX_GAP_MS) distanceM += (p.speedMps + s.speedMps) / 2.0 * dtMs / 1000.0
        }
        prev = s

        if (s.gear.isDirection) {
            val lastDir = lastDirection
            if (lastDir != null && lastDir != s.gear) directionChanges++
            lastDirection = s.gear
        }
        val moving = s.speedMps > STOPPED_MPS
        if (wasMoving && !moving) stops++
        wasMoving = moving
        s.steer?.let { maxSteer = max(maxSteer ?: 0f, abs(it)) }
    }

    fun finish(endTMs: Long): ParkingSession? {
        val first = start ?: return null
        if (distanceM < MIN_DISTANCE_M) return null
        return ParkingSession(
            durationS = (endTMs - first.tMs).coerceAtLeast(0) / 1000f,
            distanceM = distanceM.toFloat(),
            directionChanges = directionChanges,
            // the final stop before P is not a correction
            stops = (stops - 1).coerceAtLeast(0),
            maxSteerPct = maxSteer?.let { (it.coerceAtMost(1f) * 100).roundToInt() },
            endedInReverse = lastDirection == Gear.REVERSE,
        )
    }

    private fun reset() {
        start = null
        prev = null
        distanceM = 0.0
        directionChanges = 0
        stops = 0
        maxSteer = null
        lastDirection = null
        wasMoving = false
    }

    private companion object {
        const val MANEUVER_MAX_MPS = 10f / 3.6f
        const val STOPPED_MPS = 0.3f
        const val MAX_GAP_MS = 5_000L
        const val MIN_DISTANCE_M = 1.0
    }
}
