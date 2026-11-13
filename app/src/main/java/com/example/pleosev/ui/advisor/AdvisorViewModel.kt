package com.example.pleosev.ui.advisor

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.pleosev.advisor.AdvisorState
import com.example.pleosev.advisor.domain.NaviAction
import com.example.pleosev.appGraph
import com.example.pleosev.data.settings.AdvisorPrefs
import com.example.pleosev.model.EvDashboardState
import com.example.pleosev.navi.NaviState
import com.example.pleosev.shared.api.StationSummary
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Screen access to the process-wide ChargingAdvisor and the driver's settings. */
class AdvisorViewModel(application: Application) : AndroidViewModel(application) {

    private val graph = application.appGraph

    val advisor: StateFlow<AdvisorState> = graph.advisor.state
    val vehicle: StateFlow<EvDashboardState> = graph.vehicle.state
    val navi: StateFlow<NaviState> = graph.navi.state
    val prefs: StateFlow<AdvisorPrefs> = graph.settings.prefs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AdvisorPrefs())

    /** Stations the driver starred (statId); only the star on their cards depends on it. */
    val favorites: StateFlow<Set<String>> =
        graph.settings.favoriteStations.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    fun toggleFavorite(statId: String) {
        viewModelScope.launch { graph.settings.toggleFavoriteStation(statId) }
    }

    fun setConsent(given: Boolean) {
        viewModelScope.launch { graph.settings.setLocationConsent(given) }
    }

    fun setReserveSocPct(pct: Int) {
        viewModelScope.launch { graph.settings.setReserveSocPct(pct) }
    }

    fun setMinKw(kw: Int) {
        viewModelScope.launch { graph.settings.setMinKw(kw) }
    }

    fun setBackgroundWatch(on: Boolean) {
        viewModelScope.launch { graph.settings.setBackgroundWatch(on) }
    }

    fun refresh() = graph.advisor.refresh()

    fun apply(action: NaviAction, station: StationSummary) = graph.advisor.apply(action, station)

    fun showNaviChargers() {
        graph.advisor.showNaviChargers()
    }
}
