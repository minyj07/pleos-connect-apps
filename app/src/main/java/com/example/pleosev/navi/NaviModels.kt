package com.example.pleosev.navi

import com.example.pleosev.shared.api.GeoPoint

enum class NaviConnection { CONNECTING, CONNECTED, UNAVAILABLE }

/**
 * A place to send to the stock navi. [poiId] is the navi's own POI id; it is known only for places the
 * navi itself proposed (TripInfo.routePoints). Without it a charging station is treated as a plain
 * waypoint and the navi may add its own charging stop as well (Phase 0 spike).
 */
data class NaviTarget(val name: String, val location: GeoPoint, val poiId: String = "", val address: String = "")

enum class WaypointSlot { FIRST, SECOND }

data class NaviDestination(
    val name: String,
    val location: GeoPoint?,
    val remainDistanceM: Int?,
    val remainTimeS: Int?,
    val poiId: String?,
)

/** A stop of the guided route as DrivingInfo reports it: manual waypoints and the navi's own charging stops. No coordinates. */
data class NaviStop(
    val name: String,
    val distanceFromStartM: Int,
    val remainEnergyWh: Int?,
    val isChargingStation: Boolean,
    val maxChargingKw: Int?,
    val chargerCount: Int?,
)

/** A manual waypoint (WaypointInfo). The navi reports 0.0 coordinates for these, so none are kept. */
data class NaviWaypoint(val name: String, val remainDistanceM: Int?, val poiId: String?)

/**
 * One of the navi's recent destinations (RecentDestinationInfo). These do carry coordinates, and a place
 * picked in the navi's own search as a waypoint is listed here too: the only way to locate such a waypoint.
 */
data class NaviPlace(val name: String, val location: GeoPoint, val poiId: String?)

data class NaviState(
    val connection: NaviConnection = NaviConnection.CONNECTING,
    val demo: Boolean = false,
    val guiding: Boolean = false,
    /**
     * The navi cancelled the route to plan it again, as it does on every waypoint change (8–12 s, spike): the
     * trip goes on, so the last route is kept meanwhile. A cancel that is not followed by a new route ends it.
     */
    val replanning: Boolean = false,
    val routeId: String? = null,
    val location: GeoPoint? = null,
    val headingDeg: Double? = null,
    val destination: NaviDestination? = null,
    val destinationRemainWh: Int? = null,    // battery energy left at the destination (after the navi's planned charging)
    val targetSocPct: Int? = null,           // DrivingState.targetSoc: the navi's minimum arrival SOC
    val stops: List<NaviStop> = emptyList(),
    val stopsKnown: Boolean = false,         // a DrivingInfo arrived for this route (it is pushed on route changes only)
    val destinationStop: NaviStop? = null,   // DrivingInfo.destination: tells whether the navi knows the destination as a charger
    val waypoints: List<NaviWaypoint> = emptyList(),
    val recentPlaces: List<NaviPlace> = emptyList(),
    val lowBatteryWarningAtMs: Long? = null, // NaviErrorCode.CAUSE_LOW_BATTERY
    val lastError: String? = null,
    val updatedAtMs: Long = 0L,
) {
    val chargingStops: List<NaviStop> get() = stops.filter { it.isChargingStation }

    /** Charging stops the navi planned itself: in DrivingInfo but not among the manual waypoints. */
    val plannedChargingStops: List<NaviStop> get() = chargingStops.filter { stop -> waypoints.none { it.name == stop.name } }
}

/** One point of TripInfo.summary.routePoints. */
data class RoutePoint(
    val name: String,
    val location: GeoPoint,
    val poiId: String?,
    val ratioOnRoute: Double,
    val isChargingStation: Boolean,
    val isAuto: Boolean,
    val isDestination: Boolean,
)

/** TripInfo: the navi engine's own estimate from the current position to one point. */
data class TripEstimate(
    val distanceM: Int,
    val etaS: Int,
    val arrivalSocPct: Int,
    val energyWh: Double,
    val routePoints: List<RoutePoint>,
) {
    /** Charging stops the navi would need before reaching the point: it is not reachable on the current charge. */
    val autoChargingStops: List<RoutePoint> get() = routePoints.filter { it.isAuto && it.isChargingStation && !it.isDestination }
}
