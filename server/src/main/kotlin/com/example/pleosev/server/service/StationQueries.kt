package com.example.pleosev.server.service

import com.example.pleosev.server.ApiException
import com.example.pleosev.server.NotReadyException
import com.example.pleosev.server.store.StationStore
import com.example.pleosev.shared.api.ChargePrefs
import com.example.pleosev.shared.api.GeoPoint
import com.example.pleosev.shared.api.NearbyResponse
import com.example.pleosev.shared.api.NearbyStation
import com.example.pleosev.shared.api.StationDetail
import com.example.pleosev.shared.api.StatusRequest
import com.example.pleosev.shared.api.StatusResponse
import com.example.pleosev.shared.geo.Geo
import io.ktor.http.HttpStatusCode
import java.time.Clock

/** Plain lookups: stations around a point, status refresh by id, one station's chargers. */
class StationQueries(
    private val store: StationStore,
    private val views: StationViews,
    private val clock: Clock,
) {
    /** Nearest first; with [headingDeg], stations within ±90° of the heading come before those behind. */
    fun nearby(center: GeoPoint, radiusKm: Double, prefs: ChargePrefs, headingDeg: Double?, limit: Int): NearbyResponse {
        val index = store.index ?: throw NotReadyException()
        val now = clock.millis()
        val stations = index.near(center, radiusKm)
            .map { (station, km) ->
                NearbyStation(views.summary(station, prefs, now), round(km, 100), round(Geo.bearingDeg(center, station.location), 10))
            }
            .filter { it.station.compatible.total > 0 }
            .sortedBy { if (headingDeg != null && Geo.angleDiffDeg(headingDeg, it.bearingDeg) > 90.0) 1 else 0 }
            .take(limit)
        return NearbyResponse(now, stations)
    }

    fun status(request: StatusRequest): StatusResponse {
        val index = store.index ?: throw NotReadyException()
        val now = clock.millis()
        val ids = request.statIds.distinct()
        return StatusResponse(
            generatedAtMs = now,
            stations = ids.mapNotNull { index.byId[it] }.map { views.summary(it, request.prefs, now) },
            missing = ids.filter { it !in index.byId },
        )
    }

    fun detail(statId: String, prefs: ChargePrefs): StationDetail {
        val index = store.index ?: throw NotReadyException()
        val station = index.byId[statId] ?: throw ApiException(HttpStatusCode.NotFound, "NOT_FOUND", "unknown statId $statId")
        return views.detail(station, prefs)
    }

    private fun round(v: Double, scale: Int): Double = Math.round(v * scale) / scale.toDouble()
}
