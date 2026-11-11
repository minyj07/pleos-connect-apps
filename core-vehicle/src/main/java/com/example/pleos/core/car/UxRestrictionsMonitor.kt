package com.example.pleos.core.car

import android.car.Car
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Observes AAOS CarUxRestrictions so the UI can simplify itself while driving
 * (Pleos "주행 규제 개발 가이드": apps must honour CarUxRestrictions even when declared
 * distractionOptimized).
 *
 * [available] stays false when the restriction state is unknown (not an automotive device, car
 * service missing). Callers that must fail safe should then fall back to other signals such as the
 * gear or the speed instead of assuming "not driving".
 */
class UxRestrictionsMonitor(private val context: Context) {

    private val _isDriving = MutableStateFlow(false)
    val isDriving: StateFlow<Boolean> = _isDriving.asStateFlow()

    private val _available = MutableStateFlow(false)
    val available: StateFlow<Boolean> = _available.asStateFlow()

    private var bridge: CarBridge? = null

    fun start() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
            Log.i(TAG, "Not an automotive device; CarUxRestrictions disabled")
            return
        }
        try {
            bridge = AaosUxRestrictionsBridge(context) { driving ->
                _isDriving.value = driving
                _available.value = true
            }.also { it.start() }
        } catch (t: Throwable) {
            Log.w(TAG, "CarUxRestrictions unavailable: ${t.javaClass.simpleName}: ${t.message}")
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
            _available.value = false
        }
    }

    private companion object {
        const val TAG = "UxRestrictionsMonitor"
    }
}

/** All android.car usage for UX restrictions. Only loaded on automotive devices. */
internal class AaosUxRestrictionsBridge(
    private val context: Context,
    private val onDrivingChanged: (Boolean) -> Unit,
) : CarBridge {

    private var car: Car? = null
    private var manager: CarUxRestrictionsManager? = null

    override fun start() {
        val c = Car.createCar(context) ?: run {
            Log.w(TAG, "Car service not available")
            return
        }
        car = c
        val m = c.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager ?: return
        manager = m
        m.currentCarUxRestrictions?.let { onDrivingChanged(it.isRequiresDistractionOptimization) }
        m.registerListener { restrictions -> onDrivingChanged(restrictions.isRequiresDistractionOptimization) }
    }

    override fun stop() {
        manager?.unregisterListener()
        car?.disconnect()
        manager = null
        car = null
    }

    private companion object {
        const val TAG = "AaosUxRestrictions"
    }
}
