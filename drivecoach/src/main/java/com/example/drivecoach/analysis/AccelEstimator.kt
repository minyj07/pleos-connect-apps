package com.example.drivecoach.analysis

import com.example.drivecoach.model.Sample
import kotlin.math.abs

/** Acceleration estimate at the centre of the fitting window. */
data class AccelPoint(
    val tMs: Long,
    val accelMps2: Float,
    /** Fitted speed at [tMs] (less noisy than the raw reading). */
    val speedMps: Float,
    /** The raw sample closest to [tMs]; carries brake / gear / steering for that moment. */
    val sample: Sample,
)

/**
 * Longitudinal acceleration from speed only (the accelerator pedal is not readable by third-party apps).
 *
 * Fits a straight line through the samples of the last `2 × halfWindowMs` (Savitzky–Golay, order 1)
 * using the raw timestamps and reports its slope at the window centre, so estimates lag by
 * `halfWindowMs`. With 1 km/h speed steps at 10 Hz the noise is ≈ 0.08 m/s², versus ≈ 0.23 m/s² for a
 * plain 0.5 s difference.
 */
class AccelEstimator(
    private val halfWindowMs: Long = Thresholds.DEFAULT.estimatorHalfWindowMs,
    private val maxGapMs: Long = Thresholds.DEFAULT.maxSampleGapMs,
) {
    private val window = ArrayDeque<Sample>()
    private var lastEmittedMs = Long.MIN_VALUE

    /** Adds a sample; returns a new estimate when the window is full enough. */
    fun add(s: Sample): AccelPoint? {
        val last = window.lastOrNull()
        if (last != null) {
            if (s.tMs <= last.tMs) return null
            if (s.tMs - last.tMs > maxGapMs) window.clear()
        }
        window.addLast(s)
        val span = 2 * halfWindowMs
        // Keep a little history so sparse signals (≤ 2 Hz) still get three points.
        while (window.size > 3 && window.first().tMs < s.tMs - SPARSE_WINDOW_MS) window.removeFirst()

        val inWindow = window.filter { it.tMs >= s.tMs - span }
        val fit = if (inWindow.size >= 3) inWindow else window.toList().takeLast(3)
        if (fit.size < 3 || s.tMs - fit.first().tMs < halfWindowMs) return null

        val tc = s.tMs - halfWindowMs
        val tMean = fit.sumOf { it.tMs - tc } / fit.size.toDouble() / 1000.0
        val vMean = fit.sumOf { it.speedMps.toDouble() } / fit.size
        var num = 0.0
        var den = 0.0
        for (p in fit) {
            val dt = (p.tMs - tc) / 1000.0 - tMean
            num += dt * (p.speedMps - vMean)
            den += dt * dt
        }
        if (den <= 1e-9) return null
        val slope = num / den

        val center = fit.minBy { abs(it.tMs - tc) }
        if (center.tMs <= lastEmittedMs) return null
        lastEmittedMs = center.tMs
        val vFit = vMean + slope * ((center.tMs - tc) / 1000.0 - tMean)
        return AccelPoint(center.tMs, slope.toFloat(), vFit.coerceAtLeast(0.0).toFloat(), center)
    }

    private companion object {
        const val SPARSE_WINDOW_MS = 3_000L
    }
}
