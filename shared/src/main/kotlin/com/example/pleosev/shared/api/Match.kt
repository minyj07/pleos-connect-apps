package com.example.pleosev.shared.api

import kotlinx.serialization.Serializable

@Serializable
data class MatchCandidate(
    val station: StationSummary,
    val distanceM: Double,
    val nameSimilarity: Double,
    val confidence: Double,
)

/**
 * `GET /v1/stations/match`: which KECO station is the navi's charging POI?
 * [best] is null when nothing is confidently closest; the app then shows "상태 확인 불가".
 */
@Serializable
data class MatchResponse(
    val best: MatchCandidate?,
    val alternatives: List<MatchCandidate>,
    val siteStatIds: List<String>, // other operators' stations at the same site as [best]
)
