package com.example.pleosev.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.pleosev.advisor.AdvisorState
import com.example.pleosev.appGraph
import com.example.pleosev.model.EvDashboardState
import kotlinx.coroutines.flow.StateFlow

/** Dashboard view of the process-wide vehicle state (see VehicleStateHolder) and the advisor's banner. */
class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val vehicle = application.appGraph.vehicle

    val uiState: StateFlow<EvDashboardState> = vehicle.state

    val advisor: StateFlow<AdvisorState> = application.appGraph.advisor.state

    /** Re-connect to the Vehicle Service (e.g. after the emulator finished booting). */
    fun retry() = vehicle.retry()
}
