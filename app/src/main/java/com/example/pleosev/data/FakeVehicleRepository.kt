package com.example.pleosev.data

import com.example.pleosev.model.ChargeState
import com.example.pleosev.model.ConnectionState
import com.example.pleosev.model.DataSource
import com.example.pleosev.model.EvDashboardState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Simulated EV data for running the UI without the Pleos Connect Emulator
 * (e.g. on a plain Android emulator). Never used in release builds.
 */
class FakeVehicleRepository : VehicleRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow(
        EvDashboardState(
            connection = ConnectionState.CONNECTED,
            source = DataSource.DEMO,
            socPercent = 68,
            batteryLevelKwh = 52.4f,
            batteryCapacityKwh = 77.4f,
            chargeSwitchOn = false,
            chargePortOpen = false,
            chargePortConnected = false,
            chargeRateKw = 0f,
            chargeState = ChargeState.NOT_CHARGING,
            carPropertiesAvailable = true,
            currentEfficiency = 5.6f,
            sinceChargeEfficiency = 5.9f,
            rangeRemainingKm = 318f,
            odometerKm = 12_480f,
            tripDistanceKm = 23.4f,
            tripDrivingTimeMin = 31,
            drivingMode = "ECO",
            lastUpdatedMillis = System.currentTimeMillis(),
        )
    )
    override val state: StateFlow<EvDashboardState> = _state.asStateFlow()

    override fun start() {
        scope.launch {
            var charging = false
            var tick = 0
            while (isActive) {
                delay(1_000)
                tick++
                if (tick % 45 == 0) charging = !charging   // flip charging every 45 s
                _state.update { s ->
                    val soc = (s.socPercent ?: 50).let { if (charging) minOf(100, it + 1) else maxOf(5, it - (if (tick % 6 == 0) 1 else 0)) }
                    val capacity = s.batteryCapacityKwh ?: 77.4f
                    val eff = (s.currentEfficiency ?: 5.5f) + Random.nextFloat() * 0.4f - 0.2f
                    s.copy(
                        socPercent = soc,
                        batteryLevelKwh = capacity * soc / 100f,
                        rangeRemainingKm = capacity * soc / 100f * eff,
                        chargeSwitchOn = charging,
                        chargePortOpen = charging,
                        chargePortConnected = charging,
                        chargeRateKw = if (charging) 48f + Random.nextFloat() * 4f else 0f,
                        chargeState = if (charging) ChargeState.CHARGING else ChargeState.NOT_CHARGING,
                        currentEfficiency = eff.coerceIn(3f, 8f),
                        tripDistanceKm = if (charging) s.tripDistanceKm else (s.tripDistanceKm ?: 0f) + 0.02f,
                        tripDrivingTimeMin = if (charging) s.tripDrivingTimeMin else (s.tripDrivingTimeMin ?: 0) + (if (tick % 60 == 0) 1 else 0),
                        odometerKm = if (charging) s.odometerKm else (s.odometerKm ?: 0f) + 0.02f,
                        lastUpdatedMillis = System.currentTimeMillis(),
                    )
                }
            }
        }
    }

    override fun stop() {
        scope.cancel()
    }
}
