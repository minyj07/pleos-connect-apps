package com.example.drivecoach.vehicle

import android.car.Car
import android.car.VehicleGear
import android.car.VehicleIgnitionState
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.util.Log
import com.example.drivecoach.model.Gear
import com.example.pleos.core.car.CarBridge

/**
 * Standard AAOS properties: speed, gear, parking brake, ignition, EV regen context and the exterior
 * dimensions. The Pleos SDK removed its speed API and deprecated gear in favour of these.
 *
 * Permissions: CAR_SPEED (dangerous, granted at runtime after consent), CAR_POWERTRAIN and CAR_INFO
 * (normal). All `android.car` references live in [AaosDriveBridge].
 */
internal class AaosDriveSignals(
    private val context: Context,
    private val handler: Handler,
    private val sink: SignalSink,
) {
    private var bridge: AaosDriveBridge? = null

    fun start() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            !context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)
        ) {
            Log.i(TAG, "Not an automotive device; AAOS properties disabled")
            OWN_SIGNALS.forEach { sink.onAvailability(it, SignalAvailability.NOT_SUPPORTED) }
            return
        }
        try {
            bridge = AaosDriveBridge(context, handler, sink).also { it.start() }
        } catch (t: Throwable) {
            Log.w(TAG, "CarPropertyManager unavailable: ${t.javaClass.simpleName}: ${t.message}")
            OWN_SIGNALS.forEach { sink.onAvailability(it, SignalAvailability.NOT_SUPPORTED) }
            bridge = null
        }
    }

    fun resubscribe() {
        try {
            bridge?.resubscribe()
        } catch (t: Throwable) {
            Log.w(TAG, "resubscribe failed: ${t.message}")
        }
    }

    fun stop() {
        try {
            bridge?.stop()
        } catch (t: Throwable) {
            Log.w(TAG, "stop failed: ${t.message}")
        } finally {
            bridge = null
        }
    }

    companion object {
        const val TAG = "AaosDriveSignals"
        val OWN_SIGNALS = listOf(Signal.SPEED, Signal.GEAR, Signal.PARKING_BRAKE, Signal.IGNITION, Signal.DIMENSIONS)
    }
}

/** All android.car usage for driving signals. Only loaded on automotive devices. */
internal class AaosDriveBridge(
    private val context: Context,
    private val handler: Handler,
    private val sink: SignalSink,
) : CarBridge {

    private var car: Car? = null
    private var manager: CarPropertyManager? = null

    private val callback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) = dispatch(value)
        override fun onErrorEvent(propId: Int, zone: Int) {
            Log.w(TAG, "property error: 0x${Integer.toHexString(propId)} zone=$zone")
        }
    }

    override fun start() {
        car = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Non-blocking; the listener fires with ready=true now or once the car service is up, and
            // again after a car service restart.
            Car.createCar(context, handler, Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) { c, ready ->
                if (ready) subscribe(c) else onDisconnected()
            }
        } else {
            Car.createCar(context, handler)?.also { subscribe(it) }
        }
    }

    fun resubscribe() {
        manager?.unregisterCallback(callback)
        car?.let { subscribe(it) }
    }

    override fun stop() {
        manager?.unregisterCallback(callback)
        car?.disconnect()
        manager = null
        car = null
    }

    private fun onDisconnected() {
        manager = null
        AaosDriveSignals.OWN_SIGNALS.forEach { sink.onAvailability(it, SignalAvailability.DISCONNECTED) }
    }

    private fun subscribe(c: Car) {
        val m = c.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager ?: return
        manager = m
        register(m, VehiclePropertyIds.PERF_VEHICLE_SPEED, Signal.SPEED, PERMISSION_SPEED, SPEED_RATE_HZ)
        register(m, VehiclePropertyIds.GEAR_SELECTION, Signal.GEAR, PERMISSION_POWERTRAIN)
        register(m, VehiclePropertyIds.PARKING_BRAKE_ON, Signal.PARKING_BRAKE, PERMISSION_POWERTRAIN)
        register(m, VehiclePropertyIds.IGNITION_STATE, Signal.IGNITION, PERMISSION_POWERTRAIN)
        register(m, VehiclePropertyIds.EV_STOPPING_MODE, null, PERMISSION_POWERTRAIN)
        register(m, VehiclePropertyIds.EV_BRAKE_REGENERATION_LEVEL, null, PERMISSION_POWERTRAIN)
        readDimensions(m)
    }

    private fun register(m: CarPropertyManager, prop: Int, signal: Signal?, permission: String, rateHz: Float = CarPropertyManager.SENSOR_RATE_ONCHANGE) {
        // A null config means unsupported here, or (for dangerous permissions) not granted yet.
        val config = runCatching { m.getCarPropertyConfig(prop) }.getOrNull()
        if (config == null) {
            signal?.let { sink.onAvailability(it, if (granted(permission)) SignalAvailability.NOT_SUPPORTED else SignalAvailability.NO_PERMISSION) }
            return
        }
        val rate = if (rateHz > 0f && config.maxSampleRate > 0f) {
            rateHz.coerceIn(config.minSampleRate, config.maxSampleRate)
        } else {
            CarPropertyManager.SENSOR_RATE_ONCHANGE
        }
        if (rate == CarPropertyManager.SENSOR_RATE_ONCHANGE) readInitial(m, prop)
        val ok = runCatching { m.registerCallback(callback, prop, rate) }
            .onFailure { Log.d(TAG, "registerCallback(0x${Integer.toHexString(prop)}) failed: ${it.message}") }
            .getOrDefault(false)
        signal?.let { sink.onAvailability(it, if (ok) SignalAvailability.AVAILABLE else SignalAvailability.NOT_SUPPORTED) }
    }

    private fun readInitial(m: CarPropertyManager, prop: Int) {
        try {
            val v: CarPropertyValue<Any> = m.getProperty<Any>(prop, GLOBAL_AREA) ?: return
            dispatch(v)
        } catch (e: Exception) {
            Log.d(TAG, "getProperty(0x${Integer.toHexString(prop)}) failed: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun readDimensions(m: CarPropertyManager) {
        try {
            val v: CarPropertyValue<Any> = m.getProperty<Any>(VehiclePropertyIds.INFO_EXTERIOR_DIMENSIONS, GLOBAL_AREA) ?: return
            val mm = (v.value as? Array<*>)?.mapNotNull { (it as? Number)?.toInt() }
            if (mm != null && mm.size >= 5) {
                sink.onDimensions(mm)
                sink.onAvailability(Signal.DIMENSIONS, SignalAvailability.AVAILABLE)
            }
        } catch (e: Exception) {
            Log.d(TAG, "INFO_EXTERIOR_DIMENSIONS unavailable: ${e.javaClass.simpleName}")
            sink.onAvailability(Signal.DIMENSIONS, SignalAvailability.NOT_SUPPORTED)
        }
    }

    @Suppress("DEPRECATION") // getStatus(): getPropertyStatus() does not exist on API 34
    private fun dispatch(v: CarPropertyValue<*>) {
        if (v.status != CarPropertyValue.STATUS_AVAILABLE) return
        val tMs = v.timestamp / 1_000_000 // elapsedRealtimeNanos -> ms
        val raw = v.value
        when (v.propertyId) {
            VehiclePropertyIds.PERF_VEHICLE_SPEED -> (raw as? Float)?.let { sink.onSpeed(it, tMs) }
            VehiclePropertyIds.GEAR_SELECTION -> (raw as? Int)?.let { sink.onGear(it.toGear(), tMs) }
            VehiclePropertyIds.PARKING_BRAKE_ON -> (raw as? Boolean)?.let { sink.onParkingBrake(it, tMs) }
            VehiclePropertyIds.IGNITION_STATE -> (raw as? Int)?.let { sink.onIgnition(it.toIgnition(), tMs) }
            VehiclePropertyIds.EV_STOPPING_MODE -> sink.onRegen(level = null, stoppingMode = raw as? Int)
            VehiclePropertyIds.EV_BRAKE_REGENERATION_LEVEL -> sink.onRegen(level = raw as? Int, stoppingMode = null)
        }
    }

    private fun granted(permission: String) = context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun Int.toGear(): Gear = when {
        this == VehicleGear.GEAR_PARK -> Gear.PARK
        this == VehicleGear.GEAR_REVERSE -> Gear.REVERSE
        this == VehicleGear.GEAR_NEUTRAL -> Gear.NEUTRAL
        this == VehicleGear.GEAR_DRIVE || this >= VehicleGear.GEAR_FIRST -> Gear.DRIVE
        else -> Gear.UNKNOWN
    }

    private fun Int.toIgnition(): Ignition = when (this) {
        VehicleIgnitionState.LOCK, VehicleIgnitionState.OFF -> Ignition.OFF
        VehicleIgnitionState.ACC -> Ignition.ACC
        VehicleIgnitionState.ON, VehicleIgnitionState.START -> Ignition.ON
        else -> Ignition.UNKNOWN
    }

    private companion object {
        const val TAG = "AaosDriveBridge"
        const val GLOBAL_AREA = 0
        const val SPEED_RATE_HZ = 10f
        const val PERMISSION_SPEED = "android.car.permission.CAR_SPEED"
        const val PERMISSION_POWERTRAIN = "android.car.permission.CAR_POWERTRAIN"
    }
}
