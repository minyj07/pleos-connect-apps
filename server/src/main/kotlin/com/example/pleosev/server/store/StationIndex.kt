package com.example.pleosev.server.store

import com.example.pleosev.shared.api.GeoPoint
import com.example.pleosev.shared.geo.Geo
import kotlin.math.abs

/**
 * Immutable set of DC stations. Queries scan linearly behind a bounding-box check: with ~25k DC
 * stations nationwide that is a few milliseconds, so no spatial tree is needed.
 */
class StationIndex(val stations: List<StationRecord>) {

    val byId: Map<String, StationRecord> = stations.associateBy { it.statId }
    val chargerCount: Int = stations.sumOf { it.chargers.size }
    private val chargerKeys: Set<ChargerKey> =
        stations.flatMapTo(HashSet()) { s -> s.chargers.map { ChargerKey(s.statId, it.chgerId) } }

    operator fun contains(key: ChargerKey): Boolean = key in chargerKeys

    /** Stations within [radiusKm] of [center], nearest first, with their distance in km. */
    fun near(center: GeoPoint, radiusKm: Double): List<Pair<StationRecord, Double>> {
        val dLat = Geo.latDeltaDeg(radiusKm)
        val dLon = Geo.lonDeltaDeg(radiusKm, center.lat)
        return stations.asSequence()
            .filter { abs(it.location.lat - center.lat) <= dLat && abs(it.location.lon - center.lon) <= dLon }
            .map { it to Geo.distanceKm(center, it.location) }
            .filter { it.second <= radiusKm }
            .sortedBy { it.second }
            .toList()
    }

    /** Stations inside the bounding box of [points] widened by [marginKm]. */
    fun inBox(points: List<GeoPoint>, marginKm: Double): List<StationRecord> {
        if (points.isEmpty()) return emptyList()
        val dLat = Geo.latDeltaDeg(marginKm)
        val minLat = points.minOf { it.lat } - dLat
        val maxLat = points.maxOf { it.lat } + dLat
        val dLon = Geo.lonDeltaDeg(marginKm, (minLat + maxLat) / 2)
        val minLon = points.minOf { it.lon } - dLon
        val maxLon = points.maxOf { it.lon } + dLon
        return stations.filter { it.location.lat in minLat..maxLat && it.location.lon in minLon..maxLon }
    }
}
