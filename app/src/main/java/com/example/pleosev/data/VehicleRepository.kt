package com.example.pleosev.data

import com.example.pleosev.model.EvDashboardState
import kotlinx.coroutines.flow.StateFlow

/**
 * Abstraction over the vehicle data source so the UI can run against the real
 * Pleos Vehicle SDK ([PleosVehicleRepository]) or simulated data ([FakeVehicleRepository]).
 */
interface VehicleRepository {
    val state: StateFlow<EvDashboardState>

    /** Connect / start producing data. Safe to call once per instance. */
    fun start()

    /** Unregister listeners and release SDK resources. The instance must not be reused afterwards. */
    fun stop()

    /** Subscribe to the AAOS properties again, e.g. after the runtime CAR_ENERGY permission was granted. */
    fun restartCarProperties() = Unit
}
