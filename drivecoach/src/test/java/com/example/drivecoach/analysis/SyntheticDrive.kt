package com.example.drivecoach.analysis

import com.example.drivecoach.model.Gear
import com.example.drivecoach.model.Sample
import kotlin.math.roundToInt

/**
 * Builds 10 Hz sample streams from simple speed profiles for tests.
 * [quantizeKmh] rounds speeds the way many cars report them (1 km/h steps).
 */
class SyntheticDrive(
    private val quantizeKmh: Boolean = false,
    startMs: Long = 1_000_000L,
    private val periodMs: Long = 100,
) {
    val samples = mutableListOf<Sample>()
    var tMs = startMs
        private set
    private var kmh = 0.0
    var gear = Gear.DRIVE
    var brake: Boolean? = false
    var steer: Float? = null
    var cruise = false

    fun hold(seconds: Double) = ramp(kmh, seconds)

    /** Linear speed change to [toKmh] over [seconds]. */
    fun ramp(toKmh: Double, seconds: Double): SyntheticDrive {
        val n = (seconds * 1000 / periodMs).roundToInt()
        val from = kmh
        for (i in 1..n) {
            kmh = from + (toKmh - from) * i / n
            emit()
        }
        return this
    }

    fun gap(seconds: Double): SyntheticDrive {
        tMs += (seconds * 1000).toLong()
        return this
    }

    fun ignitionOff(): SyntheticDrive {
        tMs += periodMs
        samples += Sample(tMs, 0f, gear, brake, steer, cruise, ignitionOff = true)
        return this
    }

    private fun emit() {
        tMs += periodMs
        val v = if (quantizeKmh) kmh.roundToInt().toDouble() else kmh
        samples += Sample(tMs, (v / 3.6).toFloat(), gear, brake, steer, cruise)
    }

    companion object {
        fun analyze(samples: List<Sample>, th: Thresholds = Thresholds.DEFAULT) =
            TripAnalyzer("test", startEpochMs = 0L, startTMs = samples.first().tMs, th = th).run {
                samples.forEach { add(it) }
                finish(samples.last().tMs, com.example.drivecoach.model.EndReason.PARKED)
            }
    }
}
