package com.example.drivecoach.analysis

import com.example.drivecoach.model.EndReason
import com.example.drivecoach.model.EventType
import com.example.drivecoach.model.Sample
import com.example.drivecoach.model.TimelinePoint
import com.example.drivecoach.model.TripKind
import com.example.drivecoach.model.TripRecord
import com.example.drivecoach.model.TripSummary

/**
 * Runs every per-trip analysis over the samples of one trip. Pure and deterministic, so a trip
 * interrupted by a process death can be rebuilt by replaying its saved samples.
 */
class TripAnalyzer(
    private val id: String,
    private val startEpochMs: Long,
    private val startTMs: Long,
    private val odoStartKm: Float? = null,
    private val th: Thresholds = Thresholds.DEFAULT,
) {
    private val estimator = AccelEstimator(th.estimatorHalfWindowMs, th.maxSampleGapMs)
    private val detector = EventDetector(th, startTMs)
    private val habits = HabitAccumulator(th)
    private val parking = ParkingSessionDetector()
    private val timeline = TimelineBuilder(startTMs)
    private var lastAccelMs: Long? = null
    var lastSampleMs: Long = startTMs
        private set

    val distanceMeters: Double get() = habits.distanceMeters

    fun add(s: Sample) {
        habits.addSample(s)
        parking.add(s)
        timeline.addSample(s)
        lastSampleMs = s.tMs
        val p = estimator.add(s) ?: return
        val dt = lastAccelMs?.let { ((p.tMs - it) / 1000.0).coerceIn(0.0, MAX_ESTIMATE_SPACING_S) } ?: NOMINAL_SPACING_S
        lastAccelMs = p.tMs
        detector.add(p)
        habits.addAccel(p, dt)
        timeline.addAccel(p)
    }

    /** Returns null for a "trip" that never moved (gear shuffled in place). */
    fun finish(endTMs: Long, reason: EndReason, odoEndKm: Float? = null): TripRecord? {
        val stats = habits.stats().copy(durationS = ((endTMs - startTMs).coerceAtLeast(0) / 1000f))
        val distanceM = habits.distanceMeters
        if (distanceM < MIN_MOVED_M) return null
        val kind = if (distanceM >= MIN_DRIVE_M) TripKind.DRIVE else TripKind.MANEUVER
        val events = if (kind == TripKind.DRIVE) detector.finish() else emptyList()
        val counts = events.groupingBy { it.type }.eachCount()
        val summary = TripSummary(
            id = id,
            startEpochMs = startEpochMs,
            endEpochMs = startEpochMs + (endTMs - startTMs),
            kind = kind,
            stats = stats,
            events = counts,
            score = if (kind == TripKind.DRIVE) Scoring.smoothness(counts, stats.distanceKm) else null,
            parking = parking.finish(endTMs),
            odoStartKm = odoStartKm,
            odoEndKm = odoEndKm,
            endReason = reason,
        )
        return TripRecord(summary, events, timeline.build())
    }

    private companion object {
        const val MIN_MOVED_M = 1.0
        const val MIN_DRIVE_M = 300.0
        const val NOMINAL_SPACING_S = 0.1
        const val MAX_ESTIMATE_SPACING_S = 0.5
    }
}

/** One averaged point per second for the report chart. */
class TimelineBuilder(private val startTMs: Long) {

    private val points = mutableListOf<TimelinePoint>()
    private var second = -1
    private var n = 0
    private var speedSum = 0.0
    private var accelSum = 0.0
    private var accelN = 0
    private var brakeKnownN = 0
    private var brakeOnN = 0
    private var steerSum = 0.0
    private var steerN = 0

    fun addSample(s: Sample) {
        roll(s.tMs)
        n++
        speedSum += s.speedMps
        s.brake?.let {
            brakeKnownN++
            if (it) brakeOnN++
        }
        s.steer?.let {
            steerSum += it
            steerN++
        }
    }

    fun addAccel(p: AccelPoint) {
        // Estimates lag the samples; attribute them to their own second when it is still open.
        if (((p.tMs - startTMs) / 1000).toInt() == second) {
            accelSum += p.accelMps2
            accelN++
        }
    }

    fun build(): List<TimelinePoint> {
        flush()
        return points.toList()
    }

    private fun roll(tMs: Long) {
        val sec = ((tMs - startTMs).coerceAtLeast(0) / 1000).toInt()
        if (sec != second) {
            flush()
            second = sec
        }
    }

    private fun flush() {
        if (n > 0) {
            points += TimelinePoint(
                tS = second,
                kmh = (speedSum / n * 3.6).toFloat(),
                accel = if (accelN > 0) (accelSum / accelN).toFloat() else 0f,
                brake = if (brakeKnownN > 0) brakeOnN.toFloat() / brakeKnownN else null,
                steer = if (steerN > 0) (steerSum / steerN).toFloat() else null,
            )
        }
        n = 0
        speedSum = 0.0
        accelSum = 0.0
        accelN = 0
        brakeKnownN = 0
        brakeOnN = 0
        steerSum = 0.0
        steerN = 0
    }
}

object Scoring {
    /**
     * "부드러움 점수": 100 minus the weighted harsh-event rate per 10 km. Short trips are normalised as
     * 3 km so a single event on a 500 m hop doesn't zero the score.
     */
    fun smoothness(events: Map<EventType, Int>, distanceKm: Float): Int {
        val weighted = events.entries.sumOf { (type, n) -> type.scoreWeight * n }
        val per10Km = weighted * 10.0 / maxOf(distanceKm.toDouble(), MIN_NORMALISE_KM)
        return (100 - per10Km).toInt().coerceIn(0, 100)
    }

    private const val MIN_NORMALISE_KM = 3.0
}
