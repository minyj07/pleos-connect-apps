package com.example.pleosev.advisor.domain

import com.example.pleosev.shared.api.GeoPoint
import com.example.pleosev.shared.geo.Geo

/**
 * Whether the car gets to a place on its remaining range. The navi gives no route line to the stop, so
 * the straight line is stretched by [ROAD_FACTOR]; only used to drop a planned stop when the battery is
 * nearly empty, where a cautious answer is the useful one.
 */
object Reach {

    const val ROAD_FACTOR = 1.3

    /** Below this many km per percent of charge (a full battery under 50 km) a range is not a real EV's. */
    const val MIN_KM_PER_SOC_PCT = 0.5

    /**
     * The car's range, or null when it contradicts the SOC: a value the car has not worked out yet. The
     * Virtual Console sends its 0 m default with its first driving-info write, and at SOC 100 % that read
     * as an urgent "battery low" (whose alert then held back the real one for 15 minutes).
     */
    fun usableRangeKm(rangeKm: Double?, socPct: Double?): Double? {
        if (rangeKm == null || rangeKm < 0.0) return null
        if (socPct != null && rangeKm < socPct * MIN_KM_PER_SOC_PCT) return null
        return rangeKm
    }

    /** Without a range or SOC there is nothing to go by: reachable. */
    fun canReach(origin: GeoPoint, target: GeoPoint, rangeKm: Double?, socPct: Double?, ratePctPerKm: Double): Boolean {
        val reachKm = rangeKm ?: socPct?.let { it / ratePctPerKm } ?: return true
        return Geo.distanceKm(origin, target) * ROAD_FACTOR <= reachKm
    }
}
