package com.example.pleosev.server.service

import com.example.pleosev.server.NotReadyException
import com.example.pleosev.server.store.StationRecord
import com.example.pleosev.server.store.StationStore
import com.example.pleosev.shared.api.ChargePrefs
import com.example.pleosev.shared.api.GeoPoint
import com.example.pleosev.shared.api.MatchCandidate
import com.example.pleosev.shared.api.MatchResponse
import com.example.pleosev.shared.geo.Geo
import com.example.pleosev.shared.match.NameMatcher
import java.time.Clock
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Finds the KECO station behind a navi charging POI. The navi gives precise coordinates for its AUTO
 * charging stops (TripInfo.routePoints), so distance dominates; the name separates the carriageways of
 * a service area and kW breaks ties. Stations of other operators at the same site are listed in
 * [MatchResponse.siteStatIds] instead of making the match ambiguous.
 */
class MatchService(
    private val store: StationStore,
    private val views: StationViews,
    private val clock: Clock,
) {
    fun match(location: GeoPoint, name: String?, kw: Int?, prefs: ChargePrefs): MatchResponse {
        val index = store.index ?: throw NotReadyException()
        var radiusKm = NEAR_RADIUS_KM
        var near = index.near(location, radiusKm)
        if (near.isEmpty()) {
            radiusKm = FALLBACK_RADIUS_KM
            near = index.near(location, radiusKm)
        }
        val now = clock.millis()
        val scored = near.map { (station, km) -> score(station, km, radiusKm, name, kw, prefs, now) to station }
            .sortedByDescending { it.first.confidence }

        val first = scored.getOrNull(0)
        val second = scored.getOrNull(1)
        val best = first?.takeIf { (top, station) ->
            top.confidence >= MIN_CONFIDENCE && (
                second == null ||
                    top.confidence - second.first.confidence >= MIN_MARGIN ||
                    sameSite(station, second.second)
                )
        }
        val site = best?.second?.let { station ->
            index.near(station.location, SITE_RADIUS_KM)
                .map { it.first }
                .filter { it.statId != station.statId && NameMatcher.similarity(it.name, station.name) >= SITE_NAME_SIMILARITY }
                .map { it.statId }
        }.orEmpty()
        return MatchResponse(
            best = best?.first,
            alternatives = scored.map { it.first }.filter { it !== best?.first }.take(MAX_ALTERNATIVES),
            siteStatIds = site,
        )
    }

    private fun score(
        station: StationRecord, km: Double, radiusKm: Double, name: String?, kw: Int?, prefs: ChargePrefs, now: Long,
    ): MatchCandidate {
        val similarity = name?.let { NameMatcher.similarity(it, station.name) } ?: 0.0
        val stationKw = station.chargers.maxOfOrNull { views.outputKw(it) }
        val kwScore = when {
            kw == null || kw <= 0 || stationKw == null -> 0.5
            abs(stationKw - kw) <= 0.2 * kw -> 1.0
            else -> 0.0
        }
        val distanceScore = (1.0 - km / radiusKm).coerceIn(0.0, 1.0)
        val confidence = 0.5 * distanceScore + 0.35 * similarity + 0.15 * kwScore
        return MatchCandidate(
            station = views.summary(station, prefs, now),
            distanceM = (km * 1000).roundToLong().toDouble(),
            nameSimilarity = round3(similarity),
            confidence = round3(confidence),
        )
    }

    private fun sameSite(a: StationRecord, b: StationRecord): Boolean =
        Geo.distanceKm(a.location, b.location) <= SITE_RADIUS_KM && NameMatcher.similarity(a.name, b.name) >= SITE_NAME_SIMILARITY

    private fun round3(v: Double) = Math.round(v * 1000) / 1000.0

    private companion object {
        const val NEAR_RADIUS_KM = 0.4
        const val FALLBACK_RADIUS_KM = 1.0
        const val SITE_RADIUS_KM = 0.08
        const val SITE_NAME_SIMILARITY = 0.5
        const val MIN_CONFIDENCE = 0.6
        const val MIN_MARGIN = 0.1
        const val MAX_ALTERNATIVES = 5
    }
}
