package com.example.pleosev.server.service

import com.example.pleosev.server.NotReadyException
import com.example.pleosev.server.store.StationIndex
import com.example.pleosev.server.store.StationRecord
import com.example.pleosev.server.store.StationStore
import com.example.pleosev.server.sync.CoverageTracker
import com.example.pleosev.shared.api.Candidate
import com.example.pleosev.shared.api.GeoPoint
import com.example.pleosev.shared.api.ReachWindow
import com.example.pleosev.shared.api.RecommendMode
import com.example.pleosev.shared.api.RecommendationRequest
import com.example.pleosev.shared.api.RecommendationResponse
import com.example.pleosev.shared.api.StationSummary
import com.example.pleosev.shared.geo.DirectionHints
import com.example.pleosev.shared.geo.Geo
import com.example.pleosev.shared.scoring.ScoreInput
import com.example.pleosev.shared.scoring.StationScorer
import java.time.Clock
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

/**
 * Server first pass of the recommendation. The navi gives no route polyline, so the route is modelled
 * as straight legs origin → waypoints → destination scaled by the road/straight ratio implied by the
 * navi's remaining distance. The app refines the top candidates with NaviHelper `requestTrip`
 * (road distance, ETA, arrival SOC) and re-ranks them with the same StationScorer.
 */
class RecommendationService(
    private val store: StationStore,
    private val views: StationViews,
    private val matcher: MatchService,
    private val coverage: CoverageTracker,
    private val clock: Clock,
) {
    fun recommend(req: RecommendationRequest): RecommendationResponse {
        val index = store.index ?: throw NotReadyException()
        val now = clock.millis()
        val energy = EnergyModel.of(req)
        val notices = mutableListOf<String>()
        var window: ReachWindow? = null
        var planned: StationSummary? = null

        val candidates = when (req.mode) {
            RecommendMode.ON_ROUTE -> {
                val route = Route(req, energy)
                window = route.window
                rank(onRoute(index, route, energy, widen = false), req, energy, now, earlyStopWindow = route.window)
                    .ifEmpty {
                        notices += "WIDENED_SEARCH"
                        rank(onRoute(index, route, energy, widen = true), req, energy, now, earlyStopWindow = route.window)
                    }
                    .ifEmpty {
                        notices += "FALLBACK_NEARBY"
                        rank(nearby(index, req, energy, req.origin), req, energy, now, diversify = false, relaxDetour = true)
                    }
            }
            RecommendMode.NEARBY -> {
                val estimates = nearby(index, req, energy, req.origin)
                rank(estimates, req, energy, now, diversify = false).ifEmpty {
                    notices += "RELAXED_DETOUR"
                    rank(estimates, req, energy, now, diversify = false, relaxDetour = true)
                }
            }
            RecommendMode.AROUND_PLANNED_STOP -> {
                val stop = requireNotNull(req.plannedStop) { "validated by the route" }
                val station = stop.statId?.let { index.byId[it] }
                    ?: matcher.match(stop.location, stop.name, stop.maxKw, req.prefs).best?.let { index.byId[it.station.statId] }
                if (station == null) notices += "PLANNED_STOP_UNMATCHED" else planned = views.summary(station, req.prefs, now)
                val estimates = if (req.destination != null) {
                    val route = Route(req, energy)
                    window = route.window
                    onRoute(index, route, energy, widen = true, around = stop.location, aroundKm = AROUND_PLANNED_STOP_KM)
                } else {
                    // An alternative replaces the stop: the extra driving is counted from the stop, not from the car.
                    nearby(index, req, energy, stop.location, AROUND_PLANNED_STOP_KM, detourFrom = stop.location)
                }
                rank(estimates.filter { it.station.statId != station?.statId }, req, energy, now)
            }
            RecommendMode.NEAR_DESTINATION -> rank(nearDestination(index, req, energy), req, energy, now, diversify = false)
        }
        return RecommendationResponse(
            generatedAtMs = now,
            dataAsOfMs = coverage.lastSuccessAtMs,
            coverageOk = coverage.coverageOk,
            window = window,
            plannedStop = planned,
            candidates = candidates,
            notices = notices,
        )
    }

    // ---- Estimates ----

    private data class Estimate(
        val station: StationRecord,
        val straightKm: Double,   // from the origin
        val roadKm: Double,       // estimated road distance from the origin
        val detourMin: Double?,
        val etaMin: Double?,
        val arrivalSoc: Double?,
        val inWindow: Boolean,
    )

    /** Battery model from the request: %/km from the navi's own arrival estimate when plausible. */
    private class EnergyModel(
        val socPct: Double?,
        val reservePct: Double,
        val ratePctPerKm: Double,
        val reachKm: Double,       // road km until the reserve is reached
        val remainKm: Double?,
        val speedKmh: Double,
    ) {
        fun arrivalSoc(roadKm: Double): Double? = socPct?.let { it - ratePctPerKm * roadKm }

        companion object {
            fun of(req: RecommendationRequest): EnergyModel {
                val soc = req.socPct
                val naviArrival = req.naviArrivalSocPct
                val rangeKm = req.rangeKm
                val remainTimeS = req.remainTimeS
                val reserve = req.prefs.reserveSocPct.toDouble()
                val remainKm = req.remainDistanceM?.takeIf { it > 0 }?.let { it / 1000.0 }
                val naviRate = if (soc != null && naviArrival != null && remainKm != null && remainKm > 1.0) {
                    (soc - naviArrival) / remainKm
                } else {
                    null
                }
                val rangeRate = if (soc != null && rangeKm != null && rangeKm > 1.0) soc / rangeKm else null
                val rate = naviRate?.takeIf { it in PLAUSIBLE_RATE } ?: rangeRate?.takeIf { it in PLAUSIBLE_RATE } ?: DEFAULT_RATE
                val reach = when {
                    soc != null -> max(0.0, (soc - reserve) / rate)
                    rangeKm != null -> rangeKm * (1.0 - reserve / 100.0)
                    else -> DEFAULT_REACH_KM
                }
                val speed = if (remainKm != null && remainTimeS != null && remainTimeS > 60) {
                    (remainKm / (remainTimeS / 3600.0)).coerceIn(20.0, 110.0)
                } else {
                    DEFAULT_SPEED_KMH
                }
                return EnergyModel(soc, reserve, rate, reach, remainKm, speed)
            }
        }
    }

    /** Straight-leg model of the guided route. */
    private class Route(req: RecommendationRequest, energy: EnergyModel) {
        val points: List<GeoPoint> = listOf(req.origin) + req.waypoints + listOfNotNull(req.destination)
        val legs: List<Pair<GeoPoint, GeoPoint>> = points.zipWithNext()
        val legStartKm: List<Double> = legs.runningFold(0.0) { acc, (a, b) -> acc + Geo.distanceKm(a, b) }
        val straightKm: Double = legStartKm.last().coerceAtLeast(0.1)
        val sinuosity: Double = energy.remainKm?.let { (it / straightKm).coerceIn(1.05, 1.6) } ?: DEFAULT_SINUOSITY
        val maxExcessKm: Double = max(10.0, 0.12 * Geo.distanceKm(points.first(), points.last()))
        val window: ReachWindow = window(energy, sinuosity)
    }

    private fun onRoute(
        index: StationIndex,
        route: Route,
        energy: EnergyModel,
        widen: Boolean,
        around: GeoPoint? = null,
        aroundKm: Double = 0.0,
    ): List<Estimate> {
        if (route.legs.isEmpty()) return emptyList()
        val maxExcess = route.maxExcessKm * (if (widen) WIDEN_FACTOR else 1.0)
        val lastLeg = route.legs.lastIndex
        return index.inBox(route.points, maxExcess).mapNotNull { station ->
            if (around != null && Geo.distanceKm(around, station.location) > aroundKm) return@mapNotNull null
            var bestLeg = 0
            var bestExcess = Double.MAX_VALUE
            route.legs.forEachIndexed { i, (a, b) ->
                val excess = Geo.detourKm(a, station.location, b)
                if (excess < bestExcess) {
                    bestExcess = excess
                    bestLeg = i
                }
            }
            if (bestExcess > maxExcess) return@mapNotNull null
            val (a, b) = route.legs[bestLeg]
            val legKm = Geo.distanceKm(a, b)
            val angle = Math.toRadians(Geo.angleDiffDeg(Geo.bearingDeg(a, b), Geo.bearingDeg(a, station.location)))
            val projectionKm = Geo.distanceKm(a, station.location) * cos(angle)
            // Behind the car, or past the destination: not a stop on the way.
            if (bestLeg == 0 && projectionKm < -OUTSIDE_ROUTE_KM) return@mapNotNull null
            if (bestLeg == lastLeg && projectionKm > legKm + OUTSIDE_ROUTE_KM) return@mapNotNull null
            // Service area on the other carriageway ("…(양평방향)" while driving south).
            val travelBearing = if (Geo.distanceKm(station.location, b) > 5.0) Geo.bearingDeg(station.location, b) else Geo.bearingDeg(a, b)
            if (DirectionHints.isOppositeCarriageway(station.name, station.location, travelBearing)) return@mapNotNull null
            val roadKm = route.sinuosity * (route.legStartKm[bestLeg] + projectionKm.coerceIn(0.0, legKm))
            Estimate(
                station = station,
                straightKm = Geo.distanceKm(route.points.first(), station.location),
                roadKm = roadKm,
                detourMin = minutes(route.sinuosity * bestExcess, energy.speedKmh),
                etaMin = minutes(roadKm, energy.speedKmh),
                arrivalSoc = energy.arrivalSoc(roadKm),
                inWindow = roadKm in route.window.preferredFromKm..route.window.preferredToKm,
            )
        }
    }

    private fun nearby(
        index: StationIndex,
        req: RecommendationRequest,
        energy: EnergyModel,
        center: GeoPoint,
        radiusKm: Double = (energy.reachKm * 0.8).coerceIn(MIN_NEARBY_KM, MAX_NEARBY_KM),
        detourFrom: GeoPoint? = null, // null: the detour is the drive from the car
    ): List<Estimate> = index.near(center, radiusKm).map { (station, _) ->
        val straight = Geo.distanceKm(req.origin, station.location)
        val roadKm = straight * NEARBY_SINUOSITY
        val eta = minutes(roadKm, URBAN_SPEED_KMH)
        val detour = detourFrom?.let { minutes(Geo.distanceKm(it, station.location) * NEARBY_SINUOSITY, URBAN_SPEED_KMH) } ?: eta
        val ahead = req.headingDeg?.let { Geo.angleDiffDeg(it, Geo.bearingDeg(req.origin, station.location)) <= HEADING_CONE_DEG } ?: true
        Estimate(station, straight, roadKm, detourMin = detour, etaMin = eta, arrivalSoc = energy.arrivalSoc(roadKm), inWindow = ahead || straight < 1.0)
    }

    private fun nearDestination(index: StationIndex, req: RecommendationRequest, energy: EnergyModel): List<Estimate> {
        val destination = requireNotNull(req.destination) { "validated by the route" }
        val toDestinationKm = energy.remainKm ?: (Geo.distanceKm(req.origin, destination) * DEFAULT_SINUOSITY)
        return index.near(destination, NEAR_DESTINATION_KM).map { (station, km) ->
            val localKm = km * NEARBY_SINUOSITY
            Estimate(
                station = station,
                straightKm = Geo.distanceKm(req.origin, station.location),
                roadKm = toDestinationKm + localKm,
                detourMin = minutes(localKm, URBAN_SPEED_KMH),
                etaMin = minutes(toDestinationKm, energy.speedKmh),
                arrivalSoc = energy.arrivalSoc(toDestinationKm + localKm),
                inWindow = true,
            )
        }
    }

    // ---- Ranking ----

    /**
     * [earlyStopWindow] (on a route): a free charger 2 km after departure scores high (no detour, certain status)
     * but is a poor stop for a trip that needs charging later. Once the preferred stretch has enough stations that
     * qualify (power, connector, in service, detour…), the ones far too early are dropped. Not when urgent.
     */
    private fun rank(
        estimates: List<Estimate>,
        req: RecommendationRequest,
        energy: EnergyModel,
        now: Long,
        diversify: Boolean = true,
        relaxDetour: Boolean = false,
        earlyStopWindow: ReachWindow? = null,
    ): List<Candidate> {
        val minArrival = if (req.urgent) URGENT_MIN_ARRIVAL_SOC else MIN_ARRIVAL_SOC
        val excluded = req.excludeStatIds.toSet()
        val eligible = estimates.asSequence()
            .filter { it.station.statId !in excluded }
            .filter { !it.station.restricted || req.prefs.includeRestricted }
            .filter { if (it.arrivalSoc != null) it.arrivalSoc >= minArrival else it.roadKm <= energy.reachKm }
            .filter { relaxDetour || req.urgent || it.detourMin == null || it.detourMin <= 2.0 * req.prefs.maxDetourMin }
            .mapNotNull { e -> candidate(e, req, energy, now)?.let { e to it } }
            .toList()
        val kept = if (earlyStopWindow != null && !req.urgent && eligible.count { it.first.inWindow } >= MIN_IN_WINDOW) {
            eligible.filter { it.first.roadKm >= earlyStopWindow.preferredFromKm * 0.5 }
        } else {
            eligible
        }
        val ranked = kept.map { it.second }.sortedWith(StationScorer.CANDIDATE_ORDER)
        return (if (diversify) StationScorer.diversify(ranked) else ranked).take(req.limit.coerceIn(1, MAX_LIMIT))
    }

    private fun candidate(est: Estimate, req: RecommendationRequest, energy: EnergyModel, now: Long): Candidate? {
        val summary = views.summary(est.station, req.prefs, now)
        val counts = summary.compatible
        if (counts.total == 0 || counts.outOfService == counts.total) return null
        val score = StationScorer.score(
            ScoreInput(
                compatible = counts,
                maxKw = summary.maxKw,
                detourMin = est.detourMin,
                etaMin = est.etaMin,
                arrivalSocPct = est.arrivalSoc,
                reserveSocPct = energy.reservePct,
                inPreferredWindow = est.inWindow,
                freshness = summary.freshness.level,
                operatorId = summary.operatorId,
            ),
            req.prefs,
            req.urgent,
        )
        return Candidate(
            station = summary,
            straightKm = round1(est.straightKm),
            estRoadKm = round1(est.roadKm),
            estDetourMin = est.detourMin?.let(::round1),
            estArrivalSocPct = est.arrivalSoc?.let(::round1),
            score = score.value,
            reasons = score.reasons,
            warnings = if (summary.restricted) score.warnings + "RESTRICTED_ACCESS" else score.warnings,
        )
    }

    private companion object {
        val PLAUSIBLE_RATE = 0.05..0.6       // % SOC per km
        const val DEFAULT_RATE = 0.22        // ≈ 77 kWh pack at 5.9 km/kWh
        const val DEFAULT_REACH_KM = 150.0
        const val DEFAULT_SPEED_KMH = 80.0
        const val DEFAULT_SINUOSITY = 1.25
        const val NEARBY_SINUOSITY = 1.3
        const val URBAN_SPEED_KMH = 40.0
        const val WIDEN_FACTOR = 1.5
        const val OUTSIDE_ROUTE_KM = 2.0
        const val MIN_NEARBY_KM = 5.0
        const val MAX_NEARBY_KM = 50.0
        const val HEADING_CONE_DEG = 60.0
        const val AROUND_PLANNED_STOP_KM = 30.0
        const val NEAR_DESTINATION_KM = 5.0
        const val MIN_IN_WINDOW = 3
        const val MIN_ARRIVAL_SOC = 3.0
        const val URGENT_MIN_ARRIVAL_SOC = 0.5
        const val MAX_LIMIT = 20

        fun window(energy: EnergyModel, sinuosity: Double): ReachWindow {
            val reach = energy.reachKm
            var from = 0.45 * reach
            var to = 0.85 * reach
            energy.remainKm?.let { remain ->
                from = max(from, 5.0)
                to = min(to, remain - 10.0)
            }
            if (to <= from) {
                from = 0.0
                to = reach
            }
            return ReachWindow(round1(reach), round1(from), round1(to), Math.round(sinuosity * 100) / 100.0)
        }

        fun minutes(km: Double, kmh: Double): Double = km / kmh * 60.0

        fun round1(v: Double): Double = Math.round(v * 10) / 10.0
    }
}
