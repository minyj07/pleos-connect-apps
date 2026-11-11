package com.example.pleosev.car

import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.example.pleos.core.car.CarBridge
import com.example.pleosev.BuildConfig
import com.example.pleosev.model.ChargeState

/**
 * Standard AAOS vehicle properties read through CarPropertyManager.
 *
 * Pleos' SDK policy ("AAOS Alignment") is to use the standard Car API for data that AAOS already
 * exposes; the Pleos Vehicle SDK 2.3.2 accordingly marks range / battery Wh / charge-port APIs as
 * deprecated. Everything here is best-effort: unsupported properties or missing permissions
 * simply leave the corresponding field null.
 *
 * Manifest permissions:
 *   android.car.permission.CAR_ENERGY        (dangerous -> requested at runtime) : RANGE_REMAINING, EV_BATTERY_LEVEL, charge rate/state/switch
 *   android.car.permission.CAR_ENERGY_PORTS  (normal)                           : EV_CHARGE_PORT_OPEN / _CONNECTED
 *   android.car.permission.CAR_INFO          (normal)                           : INFO_EV_BATTERY_CAPACITY
 *
 * Every property is read once and then followed. The charge switch is followed here because the Pleos SDK
 * only reads it once. The battery capacity is a static property without change events, yet the Virtual
 * Console changes it, which the app showed only after a restart (2026-11-04): it is read again whenever the
 * battery energy changes, which the console's E2E steps change together with it.
 *
 * All `android.car` references live in [AaosCarPropertyBridge] so this facade can be constructed
 * safely on non-automotive devices (see [CarBridge] in :core-vehicle).
 */
class CarPropertySource(private val context: Context) {

    data class Snapshot(
        val rangeRemainingKm: Float? = null,
        val batteryLevelKwh: Float? = null,
        val batteryCapacityKwh: Float? = null,
        val chargePortOpen: Boolean? = null,
        val chargePortConnected: Boolean? = null,
        val chargeRateKw: Float? = null,
        val chargeState: ChargeState? = null,
        val chargeSwitchOn: Boolean? = null,
    )

    private var bridge: CarBridge? = null

    fun start(onSnapshot: (Snapshot) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
            Log.i(TAG, "Not an automotive device; CarPropertyManager disabled")
            return
        }
        try {
            bridge = AaosCarPropertyBridge(context, onSnapshot).also { it.start() }
        } catch (t: Throwable) {
            Log.w(TAG, "CarPropertyManager unavailable: ${t.javaClass.simpleName}: ${t.message}")
            bridge = null
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

    private companion object {
        const val TAG = "CarPropertySource"
    }
}

/** All android.car usage for vehicle properties. Only loaded on automotive devices. */
internal class AaosCarPropertyBridge(
    private val context: Context,
    private val onSnapshot: (CarPropertySource.Snapshot) -> Unit,
) : CarBridge {

    private var car: Car? = null
    private var manager: CarPropertyManager? = null
    @Volatile private var snapshot = CarPropertySource.Snapshot()
    @Volatile private var capacityReadAtMs = 0L

    private val callback = object : CarPropertyManager.CarPropertyEventCallback {
        @Suppress("DEPRECATION") // getStatus(): getPropertyStatus() does not exist on API 34
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            // An unavailable or failed property carries a placeholder value (e.g. "not connected" while plugged in).
            if (value.status != CarPropertyValue.STATUS_AVAILABLE) return
            val changed = apply(value.propertyId, value.value)
            // The capacity is static (no events of its own), but the console changes it with the battery group. This
            // getProperty is a binder call on the main thread: only when the energy changed, or every 10 s.
            val now = SystemClock.elapsedRealtime()
            if (value.propertyId == VehiclePropertyIds.EV_BATTERY_LEVEL && (changed || now - capacityReadAtMs >= CAPACITY_REREAD_MS)) {
                capacityReadAtMs = now
                manager?.let { readInitial(it, VehiclePropertyIds.INFO_EV_BATTERY_CAPACITY) }
            }
        }

        override fun onErrorEvent(propId: Int, zone: Int) {
            Log.w(TAG, "property error: 0x${Integer.toHexString(propId)} zone=$zone")
        }
    }

    override fun start() {
        val c = Car.createCar(context) ?: run { Log.w(TAG, "Car service unavailable"); return }
        car = c
        val m = c.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager ?: return
        manager = m
        for (prop in OBSERVED) {
            readInitial(m, prop)
            runCatching { m.registerCallback(callback, prop, CarPropertyManager.SENSOR_RATE_ONCHANGE) }
                .onFailure { Log.d(TAG, "registerCallback(0x${Integer.toHexString(prop)}) failed: ${it.message}") }
        }
        readInitial(m, VehiclePropertyIds.INFO_EV_BATTERY_CAPACITY) // static; read again with each battery energy change
    }

    override fun stop() {
        manager?.unregisterCallback(callback)
        car?.disconnect()
        manager = null
        car = null
    }

    @Suppress("DEPRECATION") // getStatus(), as above
    private fun readInitial(m: CarPropertyManager, prop: Int) {
        try {
            val v: CarPropertyValue<Any> = m.getProperty<Any>(prop, GLOBAL_AREA) ?: return
            if (v.status != CarPropertyValue.STATUS_AVAILABLE) return
            apply(prop, v.value)
        } catch (e: Exception) {
            // SecurityException (permission not granted) / IllegalArgumentException (unsupported) / ServiceSpecificException
            Log.d(TAG, "getProperty(0x${Integer.toHexString(prop)}) failed: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    /** True when the value changed the snapshot. */
    private fun apply(prop: Int, raw: Any?): Boolean {
        val s = snapshot
        val next = when (prop) {
            VehiclePropertyIds.RANGE_REMAINING -> s.copy(rangeRemainingKm = (raw as? Float)?.let { it / 1000f })
            VehiclePropertyIds.EV_BATTERY_LEVEL -> s.copy(batteryLevelKwh = (raw as? Float)?.let { it / 1000f })
            VehiclePropertyIds.INFO_EV_BATTERY_CAPACITY -> s.copy(batteryCapacityKwh = (raw as? Float)?.let { it / 1000f })
            VehiclePropertyIds.EV_CHARGE_PORT_OPEN -> s.copy(chargePortOpen = raw as? Boolean)
            VehiclePropertyIds.EV_CHARGE_PORT_CONNECTED -> s.copy(chargePortConnected = raw as? Boolean)
            VehiclePropertyIds.EV_BATTERY_INSTANTANEOUS_CHARGE_RATE -> s.copy(chargeRateKw = (raw as? Float)?.let { it / 1_000_000f })
            VehiclePropertyIds.EV_CHARGE_STATE -> s.copy(chargeState = (raw as? Int)?.toChargeState())
            VehiclePropertyIds.EV_CHARGE_SWITCH -> s.copy(chargeSwitchOn = raw as? Boolean)
            else -> return false
        }
        if (next == s) return false // e.g. the capacity read again unchanged
        if (BuildConfig.DEBUG && prop in SELDOM_CHANGING) Log.d(TAG, "0x${Integer.toHexString(prop)} -> $raw")
        snapshot = next
        onSnapshot(next)
        return true
    }

    /** android.car.hardware.property.EvChargeState constants (API 33). */
    private fun Int.toChargeState(): ChargeState = when (this) {
        1 -> ChargeState.CHARGING
        2 -> ChargeState.FULLY_CHARGED
        3 -> ChargeState.NOT_CHARGING
        4 -> ChargeState.ERROR
        else -> ChargeState.UNKNOWN
    }

    private companion object {
        const val TAG = "AaosCarProperty"
        const val GLOBAL_AREA = 0
        const val CAPACITY_REREAD_MS = 10_000L
        val OBSERVED = intArrayOf(
            VehiclePropertyIds.RANGE_REMAINING,
            VehiclePropertyIds.EV_BATTERY_LEVEL,
            VehiclePropertyIds.EV_CHARGE_PORT_OPEN,
            VehiclePropertyIds.EV_CHARGE_PORT_CONNECTED,
            VehiclePropertyIds.EV_BATTERY_INSTANTANEOUS_CHARGE_RATE,
            VehiclePropertyIds.EV_CHARGE_STATE,
            VehiclePropertyIds.EV_CHARGE_SWITCH,
        )

        /** Logged in debug builds when they change (E2E checks); never the position, which is not read here. */
        val SELDOM_CHANGING = setOf(
            VehiclePropertyIds.INFO_EV_BATTERY_CAPACITY,
            VehiclePropertyIds.EV_CHARGE_PORT_OPEN,
            VehiclePropertyIds.EV_CHARGE_PORT_CONNECTED,
            VehiclePropertyIds.EV_CHARGE_STATE,
            VehiclePropertyIds.EV_CHARGE_SWITCH,
        )
    }
}
