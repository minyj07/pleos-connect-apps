package com.example.pleosev.data

import android.app.Application
import android.util.Log
import com.example.pleos.core.car.UxRestrictionsMonitor
import com.example.pleosev.BuildConfig
import com.example.pleosev.model.ConnectionState
import com.example.pleosev.model.DataSource
import com.example.pleosev.model.EvDashboardState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Process-wide owner of the vehicle data source, shared by the dashboard and the charging advisor.
 *
 * - Starts with the real Pleos Vehicle SDK.
 * - In DEBUG builds, if the Vehicle Service cannot be reached (e.g. running on a plain
 *   Android emulator instead of the Pleos Connect Emulator) it falls back to demo data
 *   so the UI can still be developed. Release builds show the error instead.
 */
class VehicleStateHolder(
    private val app: Application,
    private val scope: CoroutineScope,
    private val uxMonitor: UxRestrictionsMonitor,
) {
    private var repository: VehicleRepository? = null
    private var collectJob: Job? = null
    private var demoFallbackUsed = false
    private var started = false

    private val _state = MutableStateFlow(EvDashboardState())
    val state: StateFlow<EvDashboardState> = _state.asStateFlow()

    fun start() {
        if (started) return
        started = true
        scope.launch {
            uxMonitor.isDriving.collect { driving -> _state.update { it.copy(isDriving = driving) } }
        }
        connect(PleosVehicleRepository(app))
    }

    /** The runtime CAR_ENERGY permission was just granted: the AAOS properties can be read now. */
    fun onCarEnergyGranted() {
        repository?.restartCarProperties()
    }

    /** Re-connect to the Vehicle Service (e.g. after the emulator finished booting). */
    fun retry() {
        demoFallbackUsed = false
        connect(PleosVehicleRepository(app))
    }

    private fun connect(repo: VehicleRepository) {
        collectJob?.cancel()
        repository?.stop()
        repository = repo
        repo.start()
        collectJob = scope.launch {
            repo.state.collect { s ->
                if (s.connection == ConnectionState.FAILED &&
                    s.source == DataSource.PLEOS_SDK &&
                    BuildConfig.DEBUG && !demoFallbackUsed
                ) {
                    demoFallbackUsed = true
                    Log.w(TAG, "Vehicle Service unavailable (${s.errorMessage}); switching to demo data")
                    connect(FakeVehicleRepository())
                    return@collect
                }
                _state.update { prev -> s.copy(isDriving = prev.isDriving) }
            }
        }
    }

    private companion object {
        const val TAG = "VehicleStateHolder"
    }
}
