package com.example.pleosev.advisor.domain

import com.example.pleosev.navi.NaviDestination
import com.example.pleosev.navi.NaviState
import com.example.pleosev.navi.NaviWaypoint
import com.example.pleosev.navi.RoutePoint
import com.example.pleosev.shared.api.GeoPoint
import com.example.pleosev.shared.api.PlannedStop

/** A station the driver added through the advisor (as a waypoint or the destination), by the name it was sent to the navi with. */
data class AddedStation(val location: GeoPoint, val statId: String)

/** What the route's charging stop is to the route. */
enum class StopRole {
    NAVI,         // a charging stop the navi planned by itself
    WAYPOINT,     // a waypoint the driver added
    DESTINATION,  // the route ends at a charging station
}

/**
 * Which charging stop the guided route has, and where it is.
 *
 * DrivingInfo lists every stop (manual waypoints and the navi's own charging stops) but is pushed only
 * when the route changes, so after this process (re)starts mid-route only the manual waypoints
 * (WaypointInfo) are known. A trip request to the destination ("probe") shows the stop the navi plans by
 * itself; it ignores the route's manual waypoints (E2E 2026-11-03), so it only stands in when no manual
 * waypoint is a charger. Without a charging stop on the way, a charging station as the destination is the
 * stop to check.
 *
 * None of the navi's stop reports carry coordinates. Stations added through the advisor are remembered,
 * a waypoint picked in the navi's own search is among its recent destinations, the navi's own stop is in
 * the probe and the destination has its own position. Names are matched exactly: a guessed position
 * would check the wrong station.
 */
object PlannedStopResolver {

    /**
     * The first charging stop's name, or null when the route has none as far as is known. A charger the driver
     * added through the advisor by its coordinates counts too: the navi does not flag it as a charger in
     * DrivingInfo (and may plan one of its own after it), yet it is where the driver means to charge.
     */
    fun stopName(navi: NaviState, added: Map<String, AddedStation>, probed: List<RoutePoint>?): String? {
        val onTheWay = if (navi.stopsKnown) navi.stops.firstOrNull { it.isChargingStation || it.name in added }?.name
            else chargerWaypoint(navi, added)?.name ?: probed?.firstOrNull()?.name
        return onTheWay ?: chargerDestination(navi, added)?.name
    }

    /** A probe is worth its request only while DrivingInfo is missing and no manual waypoint is a charger. */
    fun needsProbe(navi: NaviState, added: Map<String, AddedStation>): Boolean =
        !navi.stopsKnown && chargerWaypoint(navi, added) == null

    fun role(name: String, navi: NaviState): StopRole = when {
        navi.waypoints.any { it.name == name } -> StopRole.WAYPOINT
        navi.destination?.name == name -> StopRole.DESTINATION
        else -> StopRole.NAVI
    }

    /** Null: the position is unknown, so the stop's live status cannot be looked up. */
    fun locate(name: String, navi: NaviState, added: Map<String, AddedStation>, probed: List<RoutePoint>?): PlannedStop? {
        val maxKw = navi.chargingStops.firstOrNull { it.name == name }?.maxChargingKw
            ?: navi.destinationStop?.takeIf { it.name == name }?.maxChargingKw
        added[name]?.let { return PlannedStop(it.location, name, it.statId, maxKw) }
        val location = when (role(name, navi)) {
            StopRole.WAYPOINT -> {
                val waypoint = navi.waypoints.first { it.name == name }
                val byId = waypoint.poiId?.let { id -> navi.recentPlaces.firstOrNull { it.poiId == id } }
                (byId ?: navi.recentPlaces.firstOrNull { it.name == name })?.location
            }
            StopRole.DESTINATION -> navi.destination?.location
            StopRole.NAVI -> probed?.firstOrNull { it.name == name }?.location
        }
        return location?.let { PlannedStop(it, name, maxKw = maxKw) }
    }

    private fun chargerWaypoint(navi: NaviState, added: Map<String, AddedStation>): NaviWaypoint? =
        navi.waypoints.firstOrNull { it.name in added || CHARGER_WORD in it.name }

    private fun chargerDestination(navi: NaviState, added: Map<String, AddedStation>): NaviDestination? =
        navi.destination?.takeIf { d ->
            d.name in added || CHARGER_WORD in d.name || navi.destinationStop?.takeIf { it.name == d.name }?.isChargingStation == true
        }

    /** The navi's charger POIs are named "… 전기차충전소"; other places are not known to be chargers. */
    private const val CHARGER_WORD = "충전"
}
