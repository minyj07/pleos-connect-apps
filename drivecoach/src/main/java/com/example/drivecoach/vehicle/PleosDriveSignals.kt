package com.example.drivecoach.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.api.model.capability.CapabilityConfig
import ai.pleos.playground.vehicle.constant.control.CruiseControl
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import ai.pleos.playground.vehicle.listener.CruiseControlStateListener
import ai.pleos.playground.vehicle.listener.DistanceDrivenListener
import ai.pleos.playground.vehicle.listener.SteeringWheelAngleListener
import android.content.Context
import android.os.Handler
import android.util.Log
import com.example.pleos.core.PleosErrors

/**
 * Pleos Vehicle SDK signals: brake pedal (on/off), steering angle (front wheels, left negative),
 * cruise control state and the odometer. All need `pleos.car.permission.CAR_DRIVE`.
 *
 * The SDK has no connection callback, so the first capability call doubles as a probe. A
 * NOT_SUPPORTED or permission answer still means the Vehicle Service is up - only that signal is
 * missing. Every SDK callback is hopped onto the hub thread before touching the sink.
 */
internal class PleosDriveSignals(
    private val context: Context,
    private val handler: Handler,
    private val sink: SignalSink,
) {
    private var vehicle: Vehicle? = null
    private var stopped = true
    private var connected = false
    private var probeAttempt = 0

    private val brakeListener = object : BrakePedalStatusListener {
        override fun onBrakePedalStatusUpdated(status: Boolean?) = hub { sink.onBrake(status) }
        override fun onFailed(e: Exception) = listenerFailed(Signal.BRAKE, e)
    }
    private val steeringListener = object : SteeringWheelAngleListener {
        override fun onSteeringWheelAngleUpdated(angle: Float?) = hub { sink.onSteering(angle) }
        override fun onFailed(e: Exception) = listenerFailed(Signal.STEERING, e)
    }
    private val cruiseListener = object : CruiseControlStateListener {
        override fun onCruiseControlStateUpdated(state: CruiseControl?) = hub { sink.onCruise(state == CruiseControl.ACTIVATED) }
        override fun onFailed(e: Exception) = listenerFailed(Signal.CRUISE, e)
    }
    private val odometerListener = object : DistanceDrivenListener {
        override fun onDistanceDrivenUpdated(value: Float?) = hub { sink.onOdometer(value) }
        override fun onFailed(e: Exception) = listenerFailed(Signal.ODOMETER, e)
    }

    private val watchdog = object : Runnable {
        override fun run() {
            val v = vehicle ?: return
            if (stopped) return
            safe("watchdog") {
                v.getBrake().getCurrentBrakePedalStatus({ pressed -> hub { sink.onBrake(pressed) } }) { e ->
                    if (PleosErrors.isServiceConnectionFailure(e)) hub { reconnect() }
                }
            }
            handler.postDelayed(this, WATCHDOG_MS)
        }
    }

    fun start() {
        if (!stopped) return
        stopped = false
        probeAttempt = 0
        val v = Vehicle(context)
        vehicle = v
        try {
            v.initialize()
        } catch (e: Exception) {
            Log.w(TAG, "Vehicle SDK initialize() failed: ${e.message}")
            markAll(SignalAvailability.DISCONNECTED)
            return
        }
        probe(v)
    }

    fun stop() {
        if (stopped) return
        stopped = true
        handler.removeCallbacks(watchdog)
        val v = vehicle ?: return
        vehicle = null
        if (connected) {
            runCatching { v.getBrake().unregisterBrakePedalStatus(brakeListener) }
            runCatching { v.getSteeringWheel().unregisterSteeringWheelAngle(steeringListener) }
            runCatching { v.getDrivingMode().unregisterCruiseControlState(cruiseListener) }
            runCatching { v.getOdometer().unregisterDistanceDriven(odometerListener) }
        }
        connected = false
        // The SDK must not be released while calls are in flight.
        handler.postDelayed({ runCatching { v.release() }.onFailure { Log.w(TAG, "release() failed: ${it.message}") } }, RELEASE_DELAY_MS)
    }

    private fun reconnect() {
        Log.w(TAG, "Vehicle Service connection lost; reconnecting")
        stop()
        markAll(SignalAvailability.DISCONNECTED)
        handler.postDelayed({ start() }, RELEASE_DELAY_MS + 500)
    }

    private fun probe(v: Vehicle) {
        if (stopped) return
        probeAttempt++
        safe("checkBrakePedalStatusCapabilities") {
            v.getBrake().checkBrakePedalStatusCapabilities(
                onComplete = { config -> hub { onConnected(v, brakeReadable = config.readable) } },
                onFailed = { e -> hub { onProbeFailed(v, e) } },
            )
        }
    }

    private fun onProbeFailed(v: Vehicle, e: Exception) {
        if (stopped) return
        when {
            PleosErrors.isPermissionDenied(e) -> {
                sink.onAvailability(Signal.BRAKE, SignalAvailability.NO_PERMISSION)
                onConnected(v, brakeReadable = false)
            }
            PleosErrors.isNotSupported(e) -> {
                sink.onAvailability(Signal.BRAKE, SignalAvailability.NOT_SUPPORTED)
                onConnected(v, brakeReadable = false)
            }
            probeAttempt < MAX_PROBE_ATTEMPTS -> {
                Log.i(TAG, "probe #$probeAttempt failed (${PleosErrors.describe(e)}); retrying")
                handler.postDelayed({ probe(v) }, PROBE_INTERVAL_MS)
            }
            else -> {
                Log.w(TAG, "Vehicle Service unavailable: ${PleosErrors.describe(e)}")
                markAll(SignalAvailability.DISCONNECTED)
            }
        }
    }

    private fun onConnected(v: Vehicle, brakeReadable: Boolean) {
        if (stopped || connected) return
        connected = true

        if (brakeReadable) {
            sink.onAvailability(Signal.BRAKE, SignalAvailability.AVAILABLE)
            safe("brake") {
                v.getBrake().getCurrentBrakePedalStatus({ p -> hub { sink.onBrake(p) } }, ::logFailure)
                v.getBrake().registerBrakePedalStatus(brakeListener)
            }
        }

        safe("checkSteeringWheelAngleCapabilities") {
            v.getSteeringWheel().checkSteeringWheelAngleCapabilities(
                onComplete = { config, bounds ->
                    hub {
                        if (stopped) return@hub
                        sink.onSteeringBounds(bounds.minValue, bounds.maxValue)
                        if (config.readable) {
                            sink.onAvailability(Signal.STEERING, SignalAvailability.AVAILABLE)
                            safe("steering") {
                                v.getSteeringWheel().getCurrentSteeringWheelAngle({ a -> hub { sink.onSteering(a) } }, ::logFailure)
                                v.getSteeringWheel().registerSteeringWheelAngle(steeringListener)
                            }
                        } else {
                            sink.onAvailability(Signal.STEERING, SignalAvailability.NOT_SUPPORTED)
                        }
                    }
                },
                onFailed = { e -> hub { sink.onAvailability(Signal.STEERING, availabilityOf(e)) } },
            )
        }

        safe("cruise") {
            v.getDrivingMode().getCruiseControlState(
                { state ->
                    hub {
                        sink.onAvailability(Signal.CRUISE, SignalAvailability.AVAILABLE)
                        sink.onCruise(state == CruiseControl.ACTIVATED)
                    }
                },
                { e -> hub { sink.onAvailability(Signal.CRUISE, availabilityOf(e)) } },
            )
            v.getDrivingMode().registerCruiseControlState(cruiseListener)
        }

        safe("odometer") {
            v.getOdometer().getCurrentDistanceDriven(
                { km ->
                    hub {
                        sink.onAvailability(Signal.ODOMETER, SignalAvailability.AVAILABLE)
                        sink.onOdometer(km)
                    }
                },
                { e -> hub { sink.onAvailability(Signal.ODOMETER, availabilityOf(e)) } },
            )
            v.getOdometer().registerDistanceDriven(odometerListener)
        }

        handler.postDelayed(watchdog, WATCHDOG_MS)
    }

    private val CapabilityConfig.readable: Boolean
        get() = accessType == AccessType.READ || accessType == AccessType.READ_WRITE

    private fun availabilityOf(e: Exception): SignalAvailability = when {
        PleosErrors.isPermissionDenied(e) -> SignalAvailability.NO_PERMISSION
        PleosErrors.isServiceConnectionFailure(e) -> SignalAvailability.DISCONNECTED
        else -> SignalAvailability.NOT_SUPPORTED
    }

    private fun markAll(a: SignalAvailability) {
        listOf(Signal.BRAKE, Signal.STEERING, Signal.CRUISE, Signal.ODOMETER).forEach { sink.onAvailability(it, a) }
    }

    private fun listenerFailed(signal: Signal, e: Exception) {
        Log.w(TAG, "$signal listener failed: ${PleosErrors.describe(e)}")
        hub { sink.onAvailability(signal, availabilityOf(e)) }
    }

    private fun logFailure(e: Exception) {
        Log.w(TAG, "Vehicle SDK call failed: ${PleosErrors.describe(e)}")
    }

    private inline fun safe(name: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            // e.g. SecurityException when the manifest permission is missing on this head unit
            Log.w(TAG, "$name threw: ${e.message}")
        }
    }

    private fun hub(block: () -> Unit) {
        handler.post(block)
    }

    private companion object {
        const val TAG = "PleosDriveSignals"
        const val MAX_PROBE_ATTEMPTS = 8
        const val PROBE_INTERVAL_MS = 1_000L
        const val WATCHDOG_MS = 60_000L
        const val RELEASE_DELAY_MS = 3_000L
    }
}
