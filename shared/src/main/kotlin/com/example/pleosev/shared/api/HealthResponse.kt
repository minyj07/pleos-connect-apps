package com.example.pleosev.shared.api

import kotlinx.serialization.Serializable

/** `GET /v1/health` of the advisor server. Contains no location data. */
@Serializable
data class HealthResponse(
    val status: String,                   // "starting" until the station index is loaded, then "ok" / "degraded"
    val source: String = "",              // "keco" (data.go.kr) or "fake" (bundled synthetic fixtures)
    val dcStations: Int = 0,              // DC fast-charging stations in the in-memory index
    val dcChargers: Int = 0,
    val masterSyncedAtMs: Long? = null,   // last successful getChargerInfo sync (epoch ms)
    val lastStatusPollAtMs: Long? = null, // last successful getChargerStatus poll (epoch ms)
    val coverageOkSinceMs: Long? = null,  // status deltas cover every minute since this time (null = gap, STALE)
    val apiCallsToday: Int = 0,           // data.go.kr calls since 00:00 KST
    val apiDailyBudget: Int = 0,          // dev account: 10,000 / day
)
