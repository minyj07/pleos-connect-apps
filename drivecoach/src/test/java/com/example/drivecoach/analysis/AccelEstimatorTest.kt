package com.example.drivecoach.analysis

import com.example.drivecoach.model.Sample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.roundToInt

class AccelEstimatorTest {

    @Test
    fun constantAccelerationIsRecoveredFromQuantizedSpeed() {
        val est = AccelEstimator()
        val points = (0..100).mapNotNull { i ->
            val t = i * 100L
            val kmh = (2.0 * t / 1000.0 * 3.6).roundToInt() // 2 m/s², reported in 1 km/h steps
            est.add(Sample(t, (kmh / 3.6).toFloat()))
        }
        assertTrue(points.size > 80)
        points.drop(5).forEach { assertEquals(2.0, it.accelMps2.toDouble(), 0.25) }
    }

    @Test
    fun steadySpeedWithQuantizationJitterStaysNearZero() {
        val est = AccelEstimator()
        val jitter = listOf(0, 1, 0, -1, 0, 0, 1, 0, -1, 0)
        val points = (0..200).mapNotNull { i -> est.add(Sample(i * 100L, ((50 + jitter[i % jitter.size]) / 3.6).toFloat())) }
        assertTrue(points.all { abs(it.accelMps2) < 0.6f })
    }

    @Test
    fun signalGapResetsTheWindow() {
        val est = AccelEstimator()
        (0..20).forEach { est.add(Sample(it * 100L, 10f)) }
        // 10 s later the car is much faster; no estimate may bridge the gap
        assertEquals(null, est.add(Sample(12_000L, 20f)))
        assertEquals(null, est.add(Sample(12_100L, 20f)))
    }
}
