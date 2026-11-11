package com.example.drivecoach.analysis

/**
 * Every tuning constant of the habit analysis in one place.
 *
 * The event limits sit in the usual ~0.3 g telematics range. EVs reach 2.8 m/s² easily when pulling
 * away, so these are a starting point to be tuned on real drive logs, not a safety judgement.
 */
data class Thresholds(
    /** Harsh acceleration / harsh start: a ≥ this (≈ 10 km/h per second). */
    val harshAccelMps2: Float = 2.8f,
    /** Harsh braking / hard stop: a ≤ -this (≈ 11 km/h per second). */
    val harshDecelMps2: Float = 3.0f,
    /** An episode must last at least this long to count. */
    val minEventMs: Long = 500,
    /** Hysteresis: an episode ends once |a| falls below limit × this. */
    val releaseFactor: Float = 0.85f,
    /** "Standing still" for start / stop classification (5 km/h). */
    val lowSpeedMps: Float = 5f / 3.6f,
    /** A harsh start must really gain speed; wheel speed sensors jump from 0 to ~3 km/h. */
    val harshStartMinGainMps: Float = 2.5f,
    /** A harsh-braking episode followed by a stop within this window is a hard stop. */
    val hardStopWindowMs: Long = 2_000,
    /** Same-type events closer than this are merged. */
    val mergeGapMs: Long = 2_000,
    /** Acceleration intensity bands for the habit distribution. */
    val gentleAccelMps2: Float = 1.5f,
    val accelNoiseMps2: Float = 0.3f,
    /** Accelerate, then brake hard within this window: "가속 직후 제동". */
    val accelThenBrakeWindowMs: Long = 3_000,
    val accelThenBrakeDecelMps2: Float = 2.0f,
    /** Brake presses and regen/brake energy only count above walking speed. */
    val habitMinSpeedMps: Float = 5f / 3.6f,
    /** Never interpolate across a signal gap longer than this. */
    val maxSampleGapMs: Long = 5_000,
    /** Half width of the least-squares window used for acceleration. */
    val estimatorHalfWindowMs: Long = 500,
) {
    companion object {
        val DEFAULT = Thresholds()
    }
}
