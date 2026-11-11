package com.example.pleosev.navi

import kotlinx.coroutines.flow.StateFlow

/**
 * The stock navigation as the app sees it: route state in, route changes out. Commands return false
 * when they could not be sent (navi not connected); their effect shows up later in [state].
 */
interface NaviRepository {
    val state: StateFlow<NaviState>

    fun start()

    fun stop()

    /** Asks the navi to re-send destination, waypoints, route state and position. */
    fun refresh()

    /** The navi's own estimate from here to [target] (distance, ETA, arrival SOC), or null. Calls are serialized. */
    suspend fun estimateTrip(target: NaviTarget): TripEstimate?

    fun routeTo(target: NaviTarget): Boolean

    fun addWaypoint(target: NaviTarget, slot: WaypointSlot): Boolean

    fun removeWaypoint(slot: WaypointSlot): Boolean

    /** Opens the navi's place search for [keyword], where the driver can add the result with its POI id. */
    fun searchInNavi(keyword: String): Boolean

    /** Opens the navi's own nearby charging station list (fallback when the advisor server is unreachable). */
    fun showNearbyChargers(): Boolean
}
