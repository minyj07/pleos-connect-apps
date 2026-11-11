package com.example.pleosev.shared.api

import kotlinx.serialization.Serializable

/** Plug types derived from the KECO `chgerType` code. Hyundai / Kia EVs fast-charge with [DC_COMBO]. */
@Serializable
enum class Connector { DC_COMBO, DC_CHADEMO, AC3, AC_SLOW, NACS }

/** App-level charger state derived from the KECO `stat` code (see ChargerStates). */
@Serializable
enum class ChargerState { AVAILABLE, CHARGING, CHARGING_STALE, UNKNOWN, OUT_OF_SERVICE }

/** How current the server's view of a station is (based on its own polling, not on `statUpdDt`). */
@Serializable
enum class FreshnessLevel { LIVE, DELAYED, STALE }

@Serializable
data class Freshness(
    val serverCheckedAtMs: Long?, // last successful status poll that covered this station (epoch ms)
    val sourceUpdatedAtMs: Long?, // newest statUpdDt among the compatible chargers (epoch ms)
    val level: FreshnessLevel,
)

/** Counts over the chargers that match the request's connectors and minimum power. */
@Serializable
data class ChargerCounts(
    val total: Int,
    val available: Int,
    val charging: Int,       // includes nothing stale: CHARGING_STALE is counted as unknown
    val unknown: Int,        // communication error / unconfirmed / charging for suspiciously long
    val outOfService: Int,   // suspended or under inspection
)

@Serializable
data class ChargerDto(
    val chgerId: String,
    val typeCode: String,
    val connectors: Set<Connector>,
    val outputKw: Int?,
    val state: ChargerState,
    val statUpdAtMs: Long?,
    val lastStartAtMs: Long? = null,
    val lastEndAtMs: Long? = null,
    val nowStartAtMs: Long? = null,
)

@Serializable
data class StationSummary(
    val statId: String,
    val name: String,
    val address: String,
    val location: GeoPoint,
    val operatorId: String,  // KECO busiId, e.g. HD (E-pit), ME (환경부), KP (한국전력); same ids as NaviHelper's ChargerOperator
    val operatorName: String,
    val useTime: String? = null,
    val parkingFree: Boolean? = null,
    val restricted: Boolean = false, // limitYn = Y (e.g. residents only)
    val compatible: ChargerCounts,
    val maxKw: Int?,
    val freshness: Freshness,
)

@Serializable
data class StationDetail(
    val summary: StationSummary,
    val chargers: List<ChargerDto>, // every DC charger at the station, compatible or not
    val restrictionNote: String? = null,
    val operatorCall: String? = null,
    val note: String? = null,
)

@Serializable
data class NearbyStation(val station: StationSummary, val distanceKm: Double, val bearingDeg: Double)

@Serializable
data class NearbyResponse(val generatedAtMs: Long, val stations: List<NearbyStation>)

/** `POST /v1/stations/status`: refresh cards the app already shows. Carries no location. */
@Serializable
data class StatusRequest(val statIds: List<String>, val prefs: ChargePrefs = ChargePrefs())

@Serializable
data class StatusResponse(val generatedAtMs: Long, val stations: List<StationSummary>, val missing: List<String>)
