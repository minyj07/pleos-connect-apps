package com.example.pleosev.navi

import android.util.Log
import com.example.pleosev.shared.api.GeoPoint
import com.example.pleosev.shared.geo.Geo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.roundToInt

/**
 * Stand-in navi for DEBUG builds without a Navi Service (e.g. a plain Android emulator). Parked at the
 * Pleos emulator's default position; routes and waypoints are kept as requested, trip estimates use
 * straight lines with a fixed battery. Never used in release builds.
 */
class FakeNaviRepository : NaviRepository {

    private val _state = MutableStateFlow(
        NaviState(connection = NaviConnection.CONNECTED, demo = true, location = PANGYO, headingDeg = 145.0, targetSocPct = 10),
    )
    override val state: StateFlow<NaviState> = _state.asStateFlow()

    override fun start() = Unit

    override fun stop() = Unit

    override fun refresh() = Unit

    override suspend fun estimateTrip(target: NaviTarget): TripEstimate {
        val km = Geo.distanceKm(PANGYO, target.location) * SINUOSITY
        return TripEstimate(
            distanceM = (km * 1000).roundToInt(),
            etaS = (km / SPEED_KMH * 3600).roundToInt(),
            arrivalSocPct = (DEMO_SOC - DEMO_RATE * km).roundToInt(),
            energyWh = km * 1000 / KM_PER_KWH,
            routePoints = listOf(RoutePoint(target.name, target.location, target.poiId.ifBlank { null }, 100.0, false, false, true)),
        )
    }

    override fun routeTo(target: NaviTarget): Boolean {
        val km = Geo.distanceKm(PANGYO, target.location) * SINUOSITY
        _state.update {
            it.copy(
                guiding = true,
                routeId = "demo-${System.currentTimeMillis()}",
                destination = NaviDestination(target.name, target.location, (km * 1000).roundToInt(), (km / SPEED_KMH * 3600).roundToInt(), target.poiId.ifBlank { null }),
                destinationRemainWh = ((DEMO_SOC - DEMO_RATE * km) / 100 * 77_400).roundToInt(),
                stops = emptyList(),
                stopsKnown = true,
                waypoints = emptyList(),
                updatedAtMs = System.currentTimeMillis(),
            )
        }
        return true
    }

    override fun addWaypoint(target: NaviTarget, slot: WaypointSlot): Boolean {
        val km = Geo.distanceKm(PANGYO, target.location) * SINUOSITY
        _state.update {
            val waypoint = NaviWaypoint(target.name, (km * 1000).roundToInt(), target.poiId.ifBlank { null })
            val stop = NaviStop(target.name, (km * 1000).roundToInt(), null, target.poiId.isNotBlank(), null, null)
            val index = if (slot == WaypointSlot.FIRST) 0 else it.waypoints.size.coerceAtMost(1)
            it.copy(
                waypoints = it.waypoints.toMutableList().apply { add(index.coerceAtMost(size), waypoint) }.take(2),
                stops = it.stops.toMutableList().apply { add(index.coerceAtMost(size), stop) },
                updatedAtMs = System.currentTimeMillis(),
            )
        }
        return true
    }

    override fun removeWaypoint(slot: WaypointSlot): Boolean {
        _state.update {
            val index = if (slot == WaypointSlot.FIRST) 0 else 1
            it.copy(
                waypoints = it.waypoints.filterIndexed { i, _ -> i != index },
                stops = it.stops.filterIndexed { i, _ -> i != index },
                updatedAtMs = System.currentTimeMillis(),
            )
        }
        return true
    }

    override fun searchInNavi(keyword: String): Boolean {
        Log.i(TAG, "demo navi: search '$keyword'")
        return true
    }

    override fun showNearbyChargers(): Boolean {
        Log.i(TAG, "demo navi: nearby chargers")
        return true
    }

    private companion object {
        const val TAG = "FakeNaviRepo"
        val PANGYO = GeoPoint(37.4127, 127.0947)
        const val SINUOSITY = 1.2
        const val SPEED_KMH = 85.0
        const val DEMO_SOC = 42.0
        const val DEMO_RATE = 0.2 // % per km
        const val KM_PER_KWH = 6.8
    }
}
