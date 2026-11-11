package com.example.pleosev.advisor.domain

import com.example.pleosev.navi.TripEstimate
import com.example.pleosev.shared.api.Candidate
import com.example.pleosev.shared.api.ChargePrefs
import com.example.pleosev.shared.api.GeoPoint
import com.example.pleosev.shared.api.ReachWindow
import com.example.pleosev.shared.geo.Geo
import com.example.pleosev.shared.scoring.ScoreInput
import com.example.pleosev.shared.scoring.StationScorer

/** What the refinement needs to know about the current route. */
data class RefineContext(
    val destination: GeoPoint?,
    val remainTimeS: Int?,
    val avgSpeedKmh: Double,
    val reserveSocPct: Double,
    val window: ReachWindow?,
    val prefs: ChargePrefs,
    val urgent: Boolean,
    val socPct: Double? = null,       // the car's charge now
    val ratePctPerKm: Double = ChargeNeed.DEFAULT_RATE,
)

/**
 * Replaces the server's straight-line estimates with the navi's own trip estimate (NaviHelper
 * requestTrip: road distance, ETA, arrival SOC) and scores the candidate again with the same scorer.
 */
object CandidateRefiner {

    private const val AFTER_STOP_SINUOSITY = 1.25
    private const val ARRIVAL_SLACK_PCT = 2.0

    /**
     * Null when the station is out of reach on the current charge. The navi adds a charging stop of its own as
     * soon as the arrival would fall below its minimum (DrivingState.targetSoc, 10 %), so with a low battery even
     * a station next door comes back with one (E2E, SOC 7 %). Its figures then run via that other charger: the
     * server's estimate is kept unless that says the station cannot be reached at all.
     */
    fun refine(candidate: Candidate, trip: TripEstimate, context: RefineContext): Candidate? {
        if (trip.autoChargingStops.isNotEmpty()) {
            val arrival = candidate.estArrivalSocPct
            return candidate.takeIf { arrival == null || arrival > 0.0 }
        }
        val roadKm = trip.distanceM / 1000.0
        val etaMin = trip.etaS / 60.0
        // The navi works out the charge from its own battery figures, which can disagree with the car's SOC
        // (emulator: battery energy left unchanged). Arriving with more than the car has now is impossible.
        val soc = context.socPct
        val arrivalSoc = if (soc != null && trip.arrivalSocPct > soc + ARRIVAL_SLACK_PCT) {
            round1((soc - context.ratePctPerKm * roadKm).coerceAtLeast(0.0))
        } else {
            trip.arrivalSocPct.toDouble()
        }
        val destination = context.destination
        val remainTimeS = context.remainTimeS
        val detourMin = if (destination != null && remainTimeS != null) {
            // Station → destination is not known to the navi here: straight line scaled to road distance, by the
            // ratio the server measured on this route (as for the cards it did not refine) when it sent one.
            val sinuosity = context.window?.sinuosity ?: AFTER_STOP_SINUOSITY
            val onwardS = Geo.distanceKm(candidate.station.location, destination) * sinuosity / context.avgSpeedKmh * 3600.0
            ((trip.etaS + onwardS - remainTimeS) / 60.0).coerceAtLeast(0.0)
        } else {
            etaMin
        }
        val window = context.window
        val score = StationScorer.score(
            ScoreInput(
                compatible = candidate.station.compatible,
                maxKw = candidate.station.maxKw,
                detourMin = detourMin,
                etaMin = etaMin,
                arrivalSocPct = arrivalSoc,
                reserveSocPct = context.reserveSocPct,
                inPreferredWindow = window == null || roadKm in window.preferredFromKm..window.preferredToKm,
                freshness = candidate.station.freshness.level,
                operatorId = candidate.station.operatorId,
            ),
            context.prefs,
            context.urgent,
        )
        return candidate.copy(
            estRoadKm = round1(roadKm),
            estDetourMin = round1(detourMin),
            estArrivalSocPct = arrivalSoc,
            score = score.value,
            reasons = score.reasons + REFINED,
            warnings = if (candidate.station.restricted) score.warnings + "RESTRICTED_ACCESS" else score.warnings,
        )
    }

    const val REFINED = "NAVI_ESTIMATE"

    private fun round1(v: Double) = Math.round(v * 10) / 10.0
}
