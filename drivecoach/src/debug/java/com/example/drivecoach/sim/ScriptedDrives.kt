package com.example.drivecoach.sim

import com.example.drivecoach.model.Gear
import kotlin.math.roundToInt

/** One 100 ms step of a scripted drive. */
data class SimFrame(
    val kmh: Float,
    val gear: Gear,
    val brake: Boolean,
    /** Fraction of full lock, left negative. */
    val steer: Float,
    val cruise: Boolean,
    val ignitionOff: Boolean = false,
)

/**
 * Demo drives for emulators that cannot change the speed (the Pleos Virtual Console has no speed
 * card). Deterministic, so tests can replay them too. Debug builds only.
 */
object ScriptedDrives {

    const val CITY = "도심 주행 (급출발·급정지 포함) + 후진 주차"
    const val HIGHWAY = "고속도로 (크루즈·급감속)"
    const val PARKING_ONLY = "주차만 (전·후진 3회)"

    val names = listOf(CITY, HIGHWAY, PARKING_ONLY)

    fun frames(name: String): List<SimFrame> = when (name) {
        CITY -> city()
        HIGHWAY -> highway()
        else -> parkingOnly()
    }.frames

    private fun city() = Script().apply {
        park(3.0)
        gear = Gear.DRIVE
        ramp(45.0, 8.0) // gentle pull-away
        hold(35.0)
        brake = true; ramp(0.0, 9.0); hold(4.0); brake = false // normal stop at a light
        ramp(50.0, 4.0) // harsh start
        hold(30.0)
        ramp(30.0, 10.0) // lift-off: regen
        hold(20.0)
        ramp(55.0, 8.0)
        hold(25.0)
        brake = true; ramp(0.0, 4.5); brake = false // hard stop
        hold(5.0)
        ramp(60.0, 12.0)
        hold(60.0)
        ramp(95.0, 3.0) // harsh acceleration (overtake)
        hold(30.0)
        brake = true; ramp(55.0, 3.5); brake = false // harsh braking, keeps rolling
        hold(30.0)
        ramp(10.0, 12.0) // regen into the car park
        hold(15.0)
        reverseParking()
        park(3.0)
        ignitionOff()
    }

    private fun highway() = Script().apply {
        park(2.0)
        gear = Gear.DRIVE
        ramp(60.0, 12.0)
        hold(20.0)
        ramp(100.0, 15.0)
        hold(120.0)
        cruise = true
        hold(120.0)
        ramp(60.0, 3.0) // cruise brakes hard for traffic: the car's decision, not counted
        hold(60.0)
        cruise = false
        hold(10.0)
        brake = true; ramp(25.0, 3.0); brake = false // harsh braking by the driver
        hold(20.0)
        ramp(90.0, 12.0)
        hold(120.0)
        ramp(30.0, 25.0) // long regen exit
        hold(30.0)
        brake = true; ramp(0.0, 8.0); brake = false
        park(3.0)
        ignitionOff()
    }

    private fun parkingOnly() = Script().apply {
        park(2.0)
        gear = Gear.DRIVE
        ramp(8.0, 3.0)
        hold(6.0)
        reverseParking()
        park(3.0)
        ignitionOff()
    }

    /** Stop, reverse in with full right lock, one forward correction, straight back, stop. */
    private fun Script.reverseParking() {
        brake = true; ramp(0.0, 3.0); hold(1.0); brake = false
        gear = Gear.REVERSE
        steerTo(1f)
        ramp(4.0, 1.5); hold(5.0)
        brake = true; ramp(0.0, 1.0); hold(1.0); brake = false
        gear = Gear.DRIVE
        steerTo(-0.4f)
        ramp(3.0, 1.0); hold(1.5)
        brake = true; ramp(0.0, 1.0); hold(1.0); brake = false
        gear = Gear.REVERSE
        steerTo(0f)
        ramp(3.0, 1.0); hold(3.0)
        brake = true; ramp(0.0, 1.0); hold(1.0)
    }

    private class Script {
        val frames = mutableListOf<SimFrame>()
        var gear = Gear.PARK
        var brake = false
        var steer = 0f
        var cruise = false
        private var kmh = 0.0

        fun ramp(toKmh: Double, seconds: Double) {
            val n = (seconds * 10).roundToInt().coerceAtLeast(1)
            val from = kmh
            repeat(n) { i ->
                kmh = from + (toKmh - from) * (i + 1) / n
                emit()
            }
        }

        fun hold(seconds: Double) = ramp(kmh, seconds)

        fun park(seconds: Double) {
            gear = Gear.PARK
            brake = false
            kmh = 0.0
            hold(seconds)
        }

        /** Turning the wheel while standing still, ~1 s from lock to centre. */
        fun steerTo(target: Float) {
            val from = steer
            repeat(10) { i ->
                steer = from + (target - from) * (i + 1) / 10
                emit()
            }
        }

        fun ignitionOff() {
            frames += SimFrame(0f, Gear.PARK, brake = false, steer = steer, cruise = false, ignitionOff = true)
        }

        private fun emit() {
            // Report whole km/h like many cars do.
            frames += SimFrame(kmh.roundToInt().toFloat(), gear, brake, steer, cruise)
        }
    }
}
