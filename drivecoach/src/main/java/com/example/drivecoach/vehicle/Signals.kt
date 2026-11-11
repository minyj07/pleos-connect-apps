package com.example.drivecoach.vehicle

import android.os.Handler
import com.example.drivecoach.model.Gear
import com.example.drivecoach.model.Sample
import kotlinx.coroutines.flow.StateFlow

enum class Signal(val label: String) {
    SPEED("속도"),
    GEAR("기어"),
    BRAKE("브레이크"),
    STEERING("조향"),
    PARKING_BRAKE("주차 브레이크"),
    IGNITION("시동"),
    CRUISE("크루즈"),
    ODOMETER("누적 거리"),
    DIMENSIONS("차량 제원"),
}

enum class SignalAvailability { UNKNOWN, AVAILABLE, NO_PERMISSION, NOT_SUPPORTED, DISCONNECTED }

enum class Ignition { UNKNOWN, OFF, ACC, ON }

/** Latest value of every signal. [tMs] is monotonic (elapsedRealtime based). */
data class LiveVehicleState(
    val tMs: Long = 0L,
    val speedMps: Float? = null,
    val gear: Gear = Gear.UNKNOWN,
    val brake: Boolean? = null,
    /** Steering angle as the SDK reports it (degrees, left negative). */
    val steerRaw: Float? = null,
    /** Largest steering value from the capability bounds; tells front-wheel from steering-wheel degrees. */
    val steerMaxRaw: Float? = null,
    val parkingBrake: Boolean? = null,
    val ignition: Ignition = Ignition.UNKNOWN,
    val cruiseActive: Boolean = false,
    val odometerKm: Float? = null,
    /** AAOS INFO_EXTERIOR_DIMENSIONS, mm. */
    val dimensionsMm: List<Int>? = null,
    val regenLevel: Int? = null,
    val stoppingMode: Int? = null,
    val availability: Map<Signal, SignalAvailability> = emptyMap(),
    /** Debug builds: values come from a scripted demo drive. */
    val simulated: Boolean = false,
) {
    fun availability(s: Signal): SignalAvailability = availability[s] ?: SignalAvailability.UNKNOWN

    /** Steering as a fraction of full lock (-1..1, left negative). */
    val steer: Float?
        get() = steerRaw?.let { raw -> (raw / fullLockRaw(steerMaxRaw, raw)).coerceIn(-1f, 1f) }

    val isParked: Boolean get() = gear == Gear.PARK || parkingBrake == true

    fun toSample(): Sample? = speedMps?.let {
        Sample(
            tMs = tMs,
            speedMps = it,
            gear = gear,
            brake = brake,
            steer = steer,
            cruise = cruiseActive,
            ignitionOff = ignition == Ignition.OFF || ignition == Ignition.ACC,
        )
    }

    companion object {
        /** Typical bicycle-model full-lock angle when the head unit gives no bounds. */
        const val DEFAULT_FRONT_WHEEL_LOCK_DEG = 35f

        /** The emulator's Virtual Console reports steering-wheel degrees (±540). */
        const val DEFAULT_STEERING_WHEEL_LOCK_DEG = 540f

        fun fullLockRaw(boundsMax: Float?, raw: Float): Float = when {
            boundsMax != null && boundsMax > 1f -> boundsMax
            kotlin.math.abs(raw) > 60f -> DEFAULT_STEERING_WHEEL_LOCK_DEG
            else -> DEFAULT_FRONT_WHEEL_LOCK_DEG
        }
    }
}

fun interface SampleListener {
    /** Called on the source's thread ([VehicleSource.handler]) for every update. */
    fun onSample(state: LiveVehicleState)
}

/** Vehicle signals for the whole app: one connection, shared by the UI and the recorder. */
interface VehicleSource {
    /** Latest values, published at most every 100 ms for the UI. */
    val state: StateFlow<LiveVehicleState>

    /** Thread that delivers [SampleListener] callbacks. */
    val handler: Handler

    /** Reference-counted: the connection stays up while anyone holds it (+ a short grace period). */
    fun acquire()
    fun release()

    fun addSampleListener(listener: SampleListener)
    fun removeSampleListener(listener: SampleListener)

    /** Re-subscribe after a runtime permission such as CAR_SPEED was granted. */
    fun onPermissionsChanged()
}

/** Debug builds only: scripted demo drives played through the same pipeline. */
interface DemoControls {
    val scripts: List<String>
    val playing: StateFlow<String?>
    fun play(script: String, speedup: Int)
    fun stop()
}

class VehicleEnvironment(val source: VehicleSource, val demo: DemoControls?)
