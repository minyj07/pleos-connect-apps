package com.example.pleosev.data

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.constant.control.DrivingModeType
import ai.pleos.playground.vehicle.listener.DistanceDrivenListener
import ai.pleos.playground.vehicle.listener.DrivingModeStateListener
import ai.pleos.playground.vehicle.listener.DrivingTimeListener
import ai.pleos.playground.vehicle.listener.EvBatteryEfficiencyListener
import ai.pleos.playground.vehicle.listener.EvBatteryStateOfChargeListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.pleos.core.PleosErrors
import com.example.pleosev.car.CarPropertySource
import com.example.pleosev.model.ConnectionState
import com.example.pleosev.model.DataSource
import com.example.pleosev.model.EvDashboardState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Real data source backed by the Pleos Vehicle SDK (plus AAOS [CarPropertySource] for the
 * standard properties the Pleos SDK deprecated in 2.3.2: range, battery Wh, charge port ...).
 *
 * Lifecycle (per Pleos docs):
 *   Vehicle(context) -> initialize() -> getXxx() domain objects -> capability / get / register
 *   ... -> unregister listeners -> release()
 *
 * All SDK calls are asynchronous (IPC); results arrive in onComplete / onFailed callbacks on an
 * SDK thread, so state is only touched through the atomic [MutableStateFlow.update].
 */
class PleosVehicleRepository(context: Context) : VehicleRepository {

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _state = MutableStateFlow(EvDashboardState(connection = ConnectionState.CONNECTING, source = DataSource.PLEOS_SDK))
    override val state: StateFlow<EvDashboardState> = _state.asStateFlow()

    private var vehicle: Vehicle? = null
    private val carProperties = CarPropertySource(appContext)

    @Volatile private var stopped = false
    @Volatile private var connected = false
    private var probeAttempt = 0

    // ---- Listeners (keep the same instances for register / unregister) ----

    private val socListener = object : EvBatteryStateOfChargeListener {
        override fun onEvBatteryStateOfChargeUpdated(value: Int?) = update { copy(socPercent = value) }
        override fun onFailed(e: Exception) = listenerFailed("EvBatteryStateOfCharge", e)
    }
    private val currentEfficiencyListener = object : EvBatteryEfficiencyListener {
        override fun onEvBatteryEfficiencyUpdated(efficiency: Float?) = update { copy(currentEfficiency = efficiency) }
        override fun onFailed(e: Exception) = listenerFailed("CurrentEvEfficiency", e)
    }
    private val sinceChargeEfficiencyListener = object : EvBatteryEfficiencyListener {
        override fun onEvBatteryEfficiencyUpdated(efficiency: Float?) = update { copy(sinceChargeEfficiency = efficiency) }
        override fun onFailed(e: Exception) = listenerFailed("SinceChargeEvEfficiency", e)
    }
    private val odometerListener = object : DistanceDrivenListener {
        override fun onDistanceDrivenUpdated(value: Float?) = update { copy(odometerKm = value) }
        override fun onFailed(e: Exception) = listenerFailed("DistanceDriven", e)
    }
    private val tripDistanceListener = object : DistanceDrivenListener {
        override fun onDistanceDrivenUpdated(value: Float?) = update { copy(tripDistanceKm = value?.metersToKm()) }
        override fun onFailed(e: Exception) = listenerFailed("TripCurrentDrivingDistance", e)
    }
    private val tripTimeListener = object : DrivingTimeListener {
        override fun onDrivingTimeUpdated(value: Int?) = update { copy(tripDrivingTimeMin = value) }
        override fun onFailed(e: Exception) = listenerFailed("TripCurrentDrivingTime", e)
    }
    private val drivingModeListener = object : DrivingModeStateListener {
        override fun onDrivingModeStateUpdated(state: DrivingModeType?) = update { copy(drivingMode = state?.name) }
        override fun onFailed(e: Exception) = listenerFailed("DrivingModeState", e)
    }

    // ---- VehicleRepository ----

    override fun start() {
        check(vehicle == null) { "start() may only be called once" }
        val v = Vehicle(appContext)
        vehicle = v
        try {
            v.initialize()
        } catch (e: Exception) {
            fail("Vehicle SDK initialize() failed", e)
            return
        }
        probe(v)
    }

    override fun stop() {
        stopped = true
        mainHandler.removeCallbacksAndMessages(null)
        carProperties.stop()
        val v = vehicle ?: return
        vehicle = null
        if (connected) {
            runCatching { v.getEvBattery().unregisterEvBatteryStateOfCharge(socListener) }
            runCatching { v.getEvBattery().unregisterCurrentEvEfficiency(currentEfficiencyListener) }
            runCatching { v.getEvBattery().unregisterSinceChargeEvEfficiency(sinceChargeEfficiencyListener) }
            runCatching { v.getOdometer().unregisterDistanceDriven(odometerListener) }
            runCatching { v.getOdometer().unregisterTripCurrentDrivingDistance(tripDistanceListener) }
            runCatching { v.getOdometer().unregisterTripCurrentDrivingTime(tripTimeListener) }
            runCatching { v.getDrivingMode().unregisterDrivingModeState(drivingModeListener) }
        }
        runCatching { v.release() }.onFailure { Log.w(TAG, "release() failed: ${it.message}") }
    }

    // ---- Connection probe ----

    /**
     * The SDK has no connection callback, so we probe with a cheap capability call and retry a few
     * times while the Vehicle Service is still binding.
     */
    private fun probe(v: Vehicle) {
        if (stopped) return
        probeAttempt++
        v.getEvBattery().checkEvBatteryStateOfChargeCapabilities(
            onComplete = { config ->
                if (stopped) return@checkEvBatteryStateOfChargeCapabilities
                Log.i(TAG, "Vehicle Service connected; SOC accessType=${config.accessType}")
                onConnected(v, socReadable = config.accessType == AccessType.READ || config.accessType == AccessType.READ_WRITE)
            },
            onFailed = { e ->
                if (stopped) return@checkEvBatteryStateOfChargeCapabilities
                // SecurityException = permission missing (manifest) or not defined on this device -> no point retrying
                val retryable = e !is SecurityException && (
                    PleosErrors.isServiceConnectionFailure(e) ||
                        PleosErrors.codeOf(e) == PleosErrors.ERROR_INTERNAL_SERVICE ||
                        PleosErrors.codeOf(e) == null
                    )
                if (retryable && probeAttempt < MAX_PROBE_ATTEMPTS) {
                    Log.w(TAG, "probe #$probeAttempt failed (${PleosErrors.describe(e)}); retrying")
                    mainHandler.postDelayed({ probe(v) }, PROBE_INTERVAL_MS)
                } else {
                    fail("Vehicle Service 연결 실패 (${PleosErrors.describe(e)})", e)
                }
            },
        )
    }

    private fun onConnected(v: Vehicle, socReadable: Boolean) {
        connected = true
        update { copy(connection = ConnectionState.CONNECTED, errorMessage = null) }

        val ev = v.getEvBattery()
        val odo = v.getOdometer()
        val mode = v.getDrivingMode()

        // Initial values (each call is independent; failures just leave the field null)
        if (socReadable) safe("getEvBatteryStateOfCharge") {
            ev.getEvBatteryStateOfCharge({ soc -> update { copy(socPercent = soc) } }, ::logFailure)
        }
        safe("getEvChargeSwitchState") { ev.getEvChargeSwitchState({ on -> update { copy(chargeSwitchOn = on) } }, ::logFailure) }
        safe("getCurrentEvEfficiency") { ev.getCurrentEvEfficiency({ e -> update { copy(currentEfficiency = e) } }, ::logFailure) }
        safe("getSinceChargeEvEfficiency") { ev.getSinceChargeEvEfficiency({ e -> update { copy(sinceChargeEfficiency = e) } }, ::logFailure) }
        safe("getCurrentDistanceDriven") { odo.getCurrentDistanceDriven({ km -> update { copy(odometerKm = km) } }, ::logFailure) }
        safe("getTripCurrentDrivingDistance") { odo.getTripCurrentDrivingDistance({ m -> update { copy(tripDistanceKm = m?.metersToKm()) } }, ::logFailure) }
        safe("getTripCurrentDrivingTime") { odo.getTripCurrentDrivingTime({ min -> update { copy(tripDrivingTimeMin = min) } }, ::logFailure) }
        safe("getCurrentDrivingModeState") { mode.getCurrentDrivingModeState({ m -> update { copy(drivingMode = m?.name) } }, ::logFailure) }

        // Live updates
        if (socReadable) safe("registerEvBatteryStateOfCharge") { ev.registerEvBatteryStateOfCharge(socListener) }
        safe("registerCurrentEvEfficiency") { ev.registerCurrentEvEfficiency(currentEfficiencyListener) }
        safe("registerSinceChargeEvEfficiency") { ev.registerSinceChargeEvEfficiency(sinceChargeEfficiencyListener) }
        safe("registerDistanceDriven") { odo.registerDistanceDriven(odometerListener) }
        safe("registerTripCurrentDrivingDistance") { odo.registerTripCurrentDrivingDistance(tripDistanceListener) }
        safe("registerTripCurrentDrivingTime") { odo.registerTripCurrentDrivingTime(tripTimeListener) }
        safe("registerDrivingModeState") { mode.registerDrivingModeState(drivingModeListener) }

        // Standard AAOS properties (range, battery Wh, charge port / state ...)
        carProperties.start(::onCarProperties)
    }

    /**
     * The runtime CAR_ENERGY permission is usually granted after the first subscription failed (the dialog comes
     * up after the Vehicle Service connected), and AAOS checks it when subscribing: subscribe again.
     */
    override fun restartCarProperties() {
        if (stopped || !connected) return
        carProperties.stop()
        carProperties.start(::onCarProperties)
    }

    private fun onCarProperties(snap: CarPropertySource.Snapshot) = update {
        copy(
            carPropertiesAvailable = true,
            rangeRemainingKm = snap.rangeRemainingKm ?: rangeRemainingKm,
            batteryLevelKwh = snap.batteryLevelKwh ?: batteryLevelKwh,
            batteryCapacityKwh = snap.batteryCapacityKwh ?: batteryCapacityKwh,
            chargePortOpen = snap.chargePortOpen ?: chargePortOpen,
            chargePortConnected = snap.chargePortConnected ?: chargePortConnected,
            chargeRateKw = snap.chargeRateKw ?: chargeRateKw,
            chargeState = snap.chargeState ?: chargeState,
            chargeSwitchOn = snap.chargeSwitchOn ?: chargeSwitchOn, // the SDK value is read once only
        )
    }

    // ---- helpers ----

    private inline fun update(transform: EvDashboardState.() -> EvDashboardState) {
        // Stamped only on a real change: PERF_ODOMETER repeats the same value every few seconds, and an unchanged
        // state must not wake the advisor, the monitor and the screens each time.
        _state.update { old -> old.transform().let { if (it == old) old else it.copy(lastUpdatedMillis = System.currentTimeMillis()) } }
    }

    private inline fun safe(name: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            // e.g. SecurityException when a permission is missing from the manifest
            Log.w(TAG, "$name threw: ${e.message}")
        }
    }

    private fun logFailure(e: Exception) {
        Log.w(TAG, "Vehicle SDK call failed: ${PleosErrors.describe(e)}")
        if (PleosErrors.codeOf(e) == PleosErrors.ERROR_SECURITY_PERMISSION) {
            update { copy(errorMessage = "퍼미션 누락: ${e.message}") }
        }
    }

    private fun listenerFailed(name: String, e: Exception) {
        Log.w(TAG, "$name listener failed: ${PleosErrors.describe(e)}")
    }

    private fun fail(message: String, e: Exception) {
        Log.e(TAG, message, e)
        update { copy(connection = ConnectionState.FAILED, errorMessage = message) }
    }

    private fun Float.metersToKm(): Float = this / 1000f

    private companion object {
        const val TAG = "PleosVehicleRepo"
        const val MAX_PROBE_ATTEMPTS = 8
        const val PROBE_INTERVAL_MS = 1_000L
    }
}
