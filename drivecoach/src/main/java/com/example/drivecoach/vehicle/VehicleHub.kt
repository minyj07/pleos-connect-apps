package com.example.drivecoach.vehicle

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import com.example.drivecoach.model.Gear
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.abs
import kotlin.math.max

/** Receives raw signal updates from the Pleos and AAOS sources. Always called on the hub thread. */
internal interface SignalSink {
    fun onSpeed(mps: Float, tMs: Long)
    fun onGear(gear: Gear, tMs: Long)
    fun onParkingBrake(on: Boolean, tMs: Long)
    fun onIgnition(ignition: Ignition, tMs: Long)
    fun onBrake(pressed: Boolean?)
    fun onSteering(raw: Float?)
    fun onSteeringBounds(minValue: Float, maxValue: Float)
    fun onCruise(active: Boolean)
    fun onOdometer(km: Float?)
    fun onDimensions(mm: List<Int>)
    fun onRegen(level: Int?, stoppingMode: Int?)
    fun onAvailability(signal: Signal, availability: SignalAvailability)
}

/**
 * The real [VehicleSource]: one Pleos Vehicle SDK instance plus one AAOS Car connection for the whole
 * process, on a dedicated thread so the recorder never competes with the UI thread.
 *
 * Debug builds can switch it into simulation, where scripted states replace the real ones until
 * [endSimulation]; the real values keep being tracked underneath.
 */
class VehicleHub(context: Context) : VehicleSource {

    private val appContext = context.applicationContext
    private val thread = HandlerThread("vehicle-hub").apply { start() }
    override val handler: Handler = Handler(thread.looper)

    private val listeners = CopyOnWriteArrayList<SampleListener>()
    private var real = LiveVehicleState()
    private var current = real
    private var simulating = false
    private val _state = MutableStateFlow(current)
    override val state: StateFlow<LiveVehicleState> = _state.asStateFlow()

    private var refs = 0
    private var running = false
    private var lastPublishMs = 0L
    private var publishPending = false
    private var lastSteerMs = 0L

    private val sink = object : SignalSink {
        override fun onSpeed(mps: Float, tMs: Long) = change(tMs) { copy(speedMps = abs(mps)) }
        override fun onGear(gear: Gear, tMs: Long) = change(tMs) { copy(gear = gear) }
        override fun onParkingBrake(on: Boolean, tMs: Long) = change(tMs) { copy(parkingBrake = on) }
        override fun onIgnition(ignition: Ignition, tMs: Long) = change(tMs) { copy(ignition = ignition) }
        override fun onBrake(pressed: Boolean?) = change { copy(brake = pressed) }
        override fun onSteering(raw: Float?) {
            // The SDK has no rate control and decodes every event: drop near-duplicates (< 50 ms and < 0.5°).
            val now = SystemClock.elapsedRealtime()
            val last = real.steerRaw
            if (raw != null && last != null && now - lastSteerMs < STEER_MIN_INTERVAL_MS && abs(raw - last) < STEER_MIN_DELTA) return
            lastSteerMs = now
            change { copy(steerRaw = raw) }
        }
        override fun onSteeringBounds(minValue: Float, maxValue: Float) =
            change(publishOnly = true) { copy(steerMaxRaw = max(abs(minValue), abs(maxValue))) }
        override fun onCruise(active: Boolean) = change { copy(cruiseActive = active) }
        override fun onOdometer(km: Float?) = change(publishOnly = true) { copy(odometerKm = km) }
        override fun onDimensions(mm: List<Int>) = change(publishOnly = true) { copy(dimensionsMm = mm) }
        override fun onRegen(level: Int?, stoppingMode: Int?) =
            change(publishOnly = true) { copy(regenLevel = level ?: regenLevel, stoppingMode = stoppingMode ?: this.stoppingMode) }
        override fun onAvailability(signal: Signal, availability: SignalAvailability) =
            change(publishOnly = true) { copy(availability = this.availability + (signal to availability)) }
    }

    private val pleos = PleosDriveSignals(appContext, handler, sink)
    private val aaos = AaosDriveSignals(appContext, handler, sink)

    private val stopRunnable = Runnable {
        if (refs == 0 && running) {
            running = false
            pleos.stop()
            aaos.stop()
            real = LiveVehicleState()
            if (!simulating) emit(real, publishOnly = true)
        }
    }

    override fun acquire() {
        onHub {
            refs++
            handler.removeCallbacks(stopRunnable)
            if (!running) {
                running = true
                pleos.start()
                aaos.start()
            }
        }
    }

    override fun release() {
        onHub {
            refs = (refs - 1).coerceAtLeast(0)
            if (refs == 0) handler.postDelayed(stopRunnable, STOP_GRACE_MS)
        }
    }

    override fun addSampleListener(listener: SampleListener) {
        listeners += listener
    }

    override fun removeSampleListener(listener: SampleListener) {
        listeners -= listener
    }

    override fun onPermissionsChanged() {
        onHub { if (running) aaos.resubscribe() }
    }

    // ---- simulation (debug builds) ----

    fun beginSimulation() = onHub {
        simulating = true
        emit(LiveVehicleState(simulated = true, availability = Signal.entries.associateWith { SignalAvailability.AVAILABLE }), publishOnly = true)
    }

    fun simulate(state: LiveVehicleState) = onHub {
        if (simulating) emit(state.copy(simulated = true), publishOnly = false)
    }

    fun endSimulation() = onHub {
        simulating = false
        emit(real.copy(tMs = SystemClock.elapsedRealtime()), publishOnly = true)
    }

    // ---- internals (hub thread) ----

    /** Applies a real-signal change; samples get a monotonic timestamp. */
    private fun change(tMs: Long = SystemClock.elapsedRealtime(), publishOnly: Boolean = false, transform: LiveVehicleState.() -> LiveVehicleState) {
        val t = max(tMs, real.tMs + 1)
        real = real.transform().copy(tMs = t)
        if (!simulating) emit(real, publishOnly)
    }

    private fun emit(s: LiveVehicleState, publishOnly: Boolean) {
        current = s
        if (!publishOnly) listeners.forEach { it.onSample(s) }
        publishThrottled()
    }

    private fun publishThrottled() {
        val now = SystemClock.elapsedRealtime()
        val wait = PUBLISH_INTERVAL_MS - (now - lastPublishMs)
        if (wait <= 0) {
            lastPublishMs = now
            _state.value = current
        } else if (!publishPending) {
            publishPending = true
            handler.postDelayed({
                publishPending = false
                lastPublishMs = SystemClock.elapsedRealtime()
                _state.value = current
            }, wait)
        }
    }

    private inline fun onHub(crossinline block: () -> Unit) {
        if (Looper.myLooper() == handler.looper) block() else handler.post { block() }
    }

    private companion object {
        const val STOP_GRACE_MS = 3_000L
        const val PUBLISH_INTERVAL_MS = 100L
        const val STEER_MIN_INTERVAL_MS = 50L
        const val STEER_MIN_DELTA = 0.5f
    }
}
