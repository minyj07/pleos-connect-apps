package com.example.drivecoach.analysis

import com.example.drivecoach.model.Gear
import com.example.drivecoach.model.HabitStats
import com.example.drivecoach.model.SPEED_BAND_LIMITS_KMH
import com.example.drivecoach.model.Sample
import kotlin.math.max

/**
 * Accumulates the per-trip habit statistics.
 *
 * Distance, time and brake presses come from the raw samples; the acceleration distribution and the
 * brake-vs-regen energy split come from [AccelPoint]s (the estimate already carries the brake state of
 * that moment).
 */
class HabitAccumulator(private val th: Thresholds) {

    private var last: Sample? = null
    private var distanceM = 0.0
    private var durationS = 0.0
    private var movingS = 0.0
    private var idleS = 0.0
    private var maxMps = 0f
    private val bandS = DoubleArray(SPEED_BAND_LIMITS_KMH.size + 1)
    private var gentleS = 0.0
    private var moderateS = 0.0
    private var hardS = 0.0
    private var brakeKnown = false
    private var presses = 0
    private var pressS = 0.0
    private var pressStartMs: Long? = null
    private var decelBrakeJ = 0.0
    private var decelCoastJ = 0.0
    private var cruiseS = 0.0

    val distanceMeters: Double get() = distanceM

    fun addSample(s: Sample) {
        if (s.brake != null) brakeKnown = true
        maxMps = max(maxMps, s.speedMps)
        val prev = last
        last = s
        if (prev == null) return
        val dtMs = s.tMs - prev.tMs
        if (dtMs <= 0) return
        if (dtMs > th.maxSampleGapMs) {
            pressStartMs = null // don't stretch a press across a gap
            return
        }
        val dt = dtMs / 1000.0
        val v = (prev.speedMps + s.speedMps) / 2.0
        distanceM += v * dt
        durationS += dt
        if (prev.speedMps > MOVING_MPS || s.speedMps > MOVING_MPS) {
            movingS += dt
            bandS[bandOf(v * 3.6)] += dt
        } else if (prev.gear != Gear.PARK) {
            idleS += dt
        }
        if (prev.cruise) cruiseS += dt

        if (prev.brake == false && s.brake == true && s.speedMps >= th.habitMinSpeedMps) {
            presses++
            pressStartMs = s.tMs
        }
        if (prev.brake == true && s.brake == false) {
            pressStartMs?.let { pressS += (s.tMs - it) / 1000.0 }
            pressStartMs = null
        }
    }

    /** [dtS] is the time this estimate stands for (spacing between estimates). */
    fun addAccel(p: AccelPoint, dtS: Double) {
        if (p.sample.cruise) return
        val a = p.accelMps2
        if (a > th.accelNoiseMps2 && p.sample.brake != true) {
            when {
                a >= th.harshAccelMps2 -> hardS += dtS
                a >= th.gentleAccelMps2 -> moderateS += dtS
                else -> gentleS += dtS
            }
        }
        if (a < -th.accelNoiseMps2 && p.speedMps > th.habitMinSpeedMps) {
            val energy = -a * p.speedMps * dtS
            when (p.sample.brake) {
                true -> decelBrakeJ += energy
                false -> decelCoastJ += energy
                null -> Unit
            }
        }
    }

    fun stats(): HabitStats = HabitStats(
        distanceKm = (distanceM / 1000.0).toFloat(),
        durationS = durationS.toFloat(),
        movingS = movingS.toFloat(),
        idleS = idleS.toFloat(),
        maxKmh = maxMps * 3.6f,
        speedBandS = bandS.map { it.toFloat() },
        accelGentleS = gentleS.toFloat(),
        accelModerateS = moderateS.toFloat(),
        accelHardS = hardS.toFloat(),
        brakeKnown = brakeKnown,
        brakePresses = presses,
        brakePressS = pressS.toFloat(),
        decelBrakeJ = decelBrakeJ.toFloat(),
        decelCoastJ = decelCoastJ.toFloat(),
        cruiseS = cruiseS.toFloat(),
    )

    private fun bandOf(kmh: Double): Int {
        val i = SPEED_BAND_LIMITS_KMH.indexOfFirst { kmh < it }
        return if (i < 0) SPEED_BAND_LIMITS_KMH.size else i
    }

    private companion object {
        const val MOVING_MPS = 0.5f
    }
}
