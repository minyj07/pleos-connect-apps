package com.example.pleosev.shared.api

import kotlinx.serialization.Serializable

/** Driver preferences; the app stores them and sends them with every request. */
@Serializable
data class ChargePrefs(
    val minKw: Int = 50,
    val connectors: Set<Connector> = setOf(Connector.DC_COMBO),
    val reserveSocPct: Int = 10,          // the app sends max(user setting, navi DrivingState.targetSoc)
    val preferredOperators: Set<String> = emptySet(), // KECO busiId
    val avoidedOperators: Set<String> = emptySet(),
    val includeRestricted: Boolean = false,
    val maxDetourMin: Int = 15,
)

@Serializable
enum class RecommendMode {
    ON_ROUTE,            // guidance active, the route needs (another) charging stop
    NEARBY,              // no guidance, battery low
    AROUND_PLANNED_STOP, // the navi planned a charging stop; find alternatives around it
    NEAR_DESTINATION,    // charge at / near the destination
}

/** A charging stop already in the route (e.g. the navi's AUTO stop taken from TripInfo.routePoints). */
@Serializable
data class PlannedStop(val location: GeoPoint, val name: String, val statId: String? = null, val maxKw: Int? = null)

/**
 * `POST /v1/recommendations`. Coordinates should be rounded to 3 decimals by the app; the server
 * never logs or stores them.
 */
@Serializable
data class RecommendationRequest(
    val mode: RecommendMode,
    val origin: GeoPoint,
    val headingDeg: Double? = null,
    val destination: GeoPoint? = null,
    val waypoints: List<GeoPoint> = emptyList(),
    val remainDistanceM: Int? = null,      // navi DestinationInfo.remainDistance
    val remainTimeS: Int? = null,          // navi DestinationInfo.remainTime
    val naviArrivalSocPct: Double? = null, // navi remain*Charging (Wh) / battery capacity * 100
    val plannedStop: PlannedStop? = null,
    val socPct: Double? = null,
    val rangeKm: Double? = null,
    val urgent: Boolean = false,           // decided by the app (reach < 25 km, SOC <= 8 %, CAUSE_LOW_BATTERY)
    val prefs: ChargePrefs = ChargePrefs(),
    val excludeStatIds: List<String> = emptyList(),
    val limit: Int = 8,
)

/** Where along the route charging makes sense, from the energy estimate. */
@Serializable
data class ReachWindow(
    val reachKm: Double,         // road km until the reserve is hit
    val preferredFromKm: Double,
    val preferredToKm: Double,
    val sinuosity: Double,       // road km per straight km used for the estimates
)

@Serializable
data class Candidate(
    val station: StationSummary,
    val straightKm: Double,        // straight line from the origin
    val estRoadKm: Double,         // estimated road distance from the origin
    val estDetourMin: Double?,     // extra minutes compared with the current route (NEARBY: minutes to get there)
    val estArrivalSocPct: Double?,
    val score: Double,
    val reasons: List<String>,     // e.g. AVAIL_2/4, KW_350, DETOUR_4MIN
    val warnings: List<String>,    // e.g. STATUS_STALE, NO_AVAILABLE_NOW
)

@Serializable
data class RecommendationResponse(
    val generatedAtMs: Long,
    val dataAsOfMs: Long?,             // last successful status poll
    val coverageOk: Boolean,
    val window: ReachWindow?,
    val plannedStop: StationSummary? = null, // live status of the planned stop, when it could be identified
    val candidates: List<Candidate>,
    val notices: List<String>,         // e.g. WIDENED_SEARCH, FALLBACK_NEARBY, PLANNED_STOP_UNMATCHED
)
