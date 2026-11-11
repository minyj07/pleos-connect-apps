package com.example.pleosev.shared.geo

import com.example.pleosev.shared.api.GeoPoint
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Spherical-earth helpers. Accurate to ~0.5 %, which is plenty for ranking charging stations. */
object Geo {
    const val EARTH_RADIUS_KM = 6371.0088
    private const val KM_PER_DEG_LAT = 111.32

    /** Great-circle (haversine) distance in km. */
    fun distanceKm(a: GeoPoint, b: GeoPoint): Double {
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val sinLat = sin(dLat / 2)
        val sinLon = sin(dLon / 2)
        val h = sinLat * sinLat + cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sinLon * sinLon
        return 2 * EARTH_RADIUS_KM * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }

    /** Initial great-circle bearing from [a] to [b] in degrees, 0..360 (0 = north, 90 = east). */
    fun bearingDeg(a: GeoPoint, b: GeoPoint): Double {
        val lat1 = Math.toRadians(a.lat)
        val lat2 = Math.toRadians(b.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    /** Smallest absolute difference between two bearings, 0..180. */
    fun angleDiffDeg(a: Double, b: Double): Double {
        val d = abs(a - b) % 360.0
        return if (d > 180.0) 360.0 - d else d
    }

    /** Extra straight-line km of going [a] → [via] → [b] instead of [a] → [b] (0 when [via] is on the line). */
    fun detourKm(a: GeoPoint, via: GeoPoint, b: GeoPoint): Double =
        distanceKm(a, via) + distanceKm(via, b) - distanceKm(a, b)

    /** Degrees of latitude spanned by [km]; used for cheap bounding-box pre-filters. */
    fun latDeltaDeg(km: Double): Double = km / KM_PER_DEG_LAT

    /** Degrees of longitude spanned by [km] at latitude [atLat]. */
    fun lonDeltaDeg(km: Double, atLat: Double): Double =
        km / (KM_PER_DEG_LAT * cos(Math.toRadians(atLat)).coerceAtLeast(0.01))
}
