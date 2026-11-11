package com.example.drivecoach.parking

import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Car dimensions the parking simulation needs.
 *
 * [turningRadiusM] is the Korean spec "최소회전반경": measured on the centre line of the outer front
 * wheel. From it, the rear-axle-centre radius at full lock is `R = √(R_spec² − L²) − track/2` and the
 * bicycle-model full-lock angle (the angle PERF_STEERING_ANGLE reports) is `δmax = atan(L / R)`.
 */
@Serializable
data class VehicleGeometry(
    val id: String,
    val name: String,
    val lengthM: Double,
    val widthM: Double,
    val wheelbaseM: Double,
    val frontOverhangM: Double,
    val rearOverhangM: Double,
    val frontTrackM: Double,
    val turningRadiusM: Double,
    val source: Source,
    /** Steering-wheel turns from centre to full lock, display only ("약 1.3바퀴"). */
    val lockTurns: Double = DEFAULT_LOCK_TURNS,
) {
    @Serializable
    enum class Source(val label: String) { VEHICLE("차량 제공 제원"), PRESET("차종 프리셋"), CUSTOM("직접 입력") }

    val rearAxleRadiusM: Double
        get() {
            val r = sqrt((turningRadiusM * turningRadiusM - wheelbaseM * wheelbaseM).coerceAtLeast(0.0)) - frontTrackM / 2
            // Guard against inconsistent input: keep δmax within a plausible 28–42°.
            val rMin = wheelbaseM / tan(MAX_PLAUSIBLE_STEER)
            val rMax = wheelbaseM / tan(MIN_PLAUSIBLE_STEER)
            return r.coerceIn(rMin, rMax)
        }

    /** Bicycle-model front-wheel angle at full lock, radians. */
    val maxSteerRad: Double get() = atan(wheelbaseM / rearAxleRadiusM)

    val maxCurvature: Double get() = 1.0 / rearAxleRadiusM

    /** Curvature for a steering input (fraction of full lock, left negative like the SDK). */
    fun curvatureForSteer(steer: Double): Double = tan(-steer.coerceIn(-1.0, 1.0) * maxSteerRad) / wheelbaseM

    /** Steering input (left negative) that drives curvature [kappa] (left positive). */
    fun steerForCurvature(kappa: Double): Double = -(atan(kappa * wheelbaseM) / maxSteerRad).coerceIn(-1.0, 1.0)

    /** Driver's eye: about 1.2 m behind the front axle and 0.37 m left of centre (left-hand drive). */
    val driverEyeAheadM: Double get() = (wheelbaseM - 1.2).coerceAtLeast(0.8)
    val driverEyeLeftM: Double get() = 0.37

    companion object {
        const val DEFAULT_LOCK_TURNS = 1.3
        private const val MIN_PLAUSIBLE_STEER = 28.0 * PI / 180
        private const val MAX_PLAUSIBLE_STEER = 42.0 * PI / 180
        private const val TYRE_HALF_WIDTH_M = 0.12
        private const val FRONT_OVERHANG_SHARE = 0.52

        /**
         * From AAOS INFO_EXTERIOR_DIMENSIONS (int32[8], mm): height, length, width, width incl. mirrors,
         * wheelbase, front track, rear track, curb-to-curb turning radius. Some head units fill the last
         * field with the diameter, so it is classified by its ratio to the wheelbase. Returns null when the
         * basic dimensions are missing or implausible.
         */
        fun fromAaosDimensions(mm: IntArray, fallbackTurningRadiusM: Double? = null): VehicleGeometry? {
            if (mm.size < 5) return null
            val length = mm[1] / 1000.0
            val width = mm[2] / 1000.0
            val wheelbase = mm[4] / 1000.0
            if (length !in 2.5..6.5 || width !in 1.4..2.3 || wheelbase !in 1.8..3.8 || wheelbase >= length) return null
            val track = mm.getOrNull(5)?.takeIf { it in 1_100..2_100 }?.div(1000.0) ?: (width - 0.25)
            val raw = mm.getOrNull(7)?.div(1000.0) ?: 0.0
            val ratio = raw / wheelbase
            val turning = when {
                ratio in 1.5..2.6 -> raw - TYRE_HALF_WIDTH_M
                ratio in 3.0..5.2 -> raw / 2 - TYRE_HALF_WIDTH_M
                else -> fallbackTurningRadiusM ?: estimateTurningRadius(wheelbase, track)
            }
            val overhang = length - wheelbase
            return VehicleGeometry(
                id = "vehicle",
                name = "내 차량",
                lengthM = length,
                widthM = width,
                wheelbaseM = wheelbase,
                frontOverhangM = overhang * FRONT_OVERHANG_SHARE,
                rearOverhangM = overhang * (1 - FRONT_OVERHANG_SHARE),
                frontTrackM = track,
                turningRadiusM = turning,
                source = Source.VEHICLE,
            )
        }

        fun custom(lengthM: Double, widthM: Double, wheelbaseM: Double, turningRadiusM: Double): VehicleGeometry {
            val overhang = (lengthM - wheelbaseM).coerceAtLeast(0.4)
            return VehicleGeometry(
                id = "custom",
                name = "직접 입력",
                lengthM = lengthM,
                widthM = widthM,
                wheelbaseM = wheelbaseM,
                frontOverhangM = overhang * FRONT_OVERHANG_SHARE,
                rearOverhangM = overhang * (1 - FRONT_OVERHANG_SHARE),
                frontTrackM = widthM - 0.25,
                turningRadiusM = turningRadiusM,
                source = Source.CUSTOM,
            )
        }

        /** Spec-style radius for a typical 35° full lock. */
        fun estimateTurningRadius(wheelbaseM: Double, trackM: Double): Double {
            val r = wheelbaseM / tan(35.0 * PI / 180)
            val outer = r + trackM / 2
            return sqrt(outer * outer + wheelbaseM * wheelbaseM)
        }
    }
}

/**
 * Model presets for cars without INFO_EXTERIOR_DIMENSIONS (and the emulator). Length / width / wheelbase
 * are from the makers' published specs; the turning radius is the published turning circle / 2 where
 * available, otherwise estimated, so the UI labels these as reference values.
 */
object CarPresets {
    val IONIQ_5 = preset("ioniq5", "아이오닉 5", length = 4.655, width = 1.890, wheelbase = 3.000, track = 1.638, turning = 5.99)
    val EV6 = preset("ev6", "EV6", length = 4.695, width = 1.890, wheelbase = 2.900, track = 1.625, turning = 5.81)
    val EV3 = preset("ev3", "EV3", length = 4.300, width = 1.850, wheelbase = 2.680, track = 1.600, turning = null)

    val all = listOf(IONIQ_5, EV6, EV3)
    val default = IONIQ_5

    fun byId(id: String): VehicleGeometry? = all.firstOrNull { it.id == id }

    private fun preset(id: String, name: String, length: Double, width: Double, wheelbase: Double, track: Double, turning: Double?): VehicleGeometry {
        val overhang = length - wheelbase
        return VehicleGeometry(
            id = id,
            name = name,
            lengthM = length,
            widthM = width,
            wheelbaseM = wheelbase,
            frontOverhangM = overhang * 0.52,
            rearOverhangM = overhang * 0.48,
            frontTrackM = track,
            turningRadiusM = turning ?: VehicleGeometry.estimateTurningRadius(wheelbase, track),
            source = VehicleGeometry.Source.PRESET,
        )
    }
}
