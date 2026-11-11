package com.example.pleosev.shared.scoring

import com.example.pleosev.shared.api.Candidate
import com.example.pleosev.shared.api.ChargePrefs
import com.example.pleosev.shared.api.ChargerCounts
import com.example.pleosev.shared.api.FreshnessLevel
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.roundToInt

/** Weights of the five score parts; they sum to 1. */
data class ScoringWeights(
    val availability: Double,
    val power: Double,
    val detour: Double,
    val margin: Double,
    val window: Double,
) {
    companion object {
        val NORMAL = ScoringWeights(availability = 0.35, power = 0.15, detour = 0.25, margin = 0.15, window = 0.10)

        /** Battery critical: getting there safely matters more than a free charger or high power. */
        val URGENT = ScoringWeights(availability = 0.25, power = 0.05, detour = 0.35, margin = 0.30, window = 0.05)
    }
}

/**
 * Everything the scorer needs about one candidate, already estimated by the caller: the server's
 * straight-line first pass or the app's NaviHelper `requestTrip` refinement.
 */
data class ScoreInput(
    val compatible: ChargerCounts,
    val maxKw: Int?,
    val detourMin: Double?,
    val etaMin: Double?,
    val arrivalSocPct: Double?,
    val reserveSocPct: Double,
    val inPreferredWindow: Boolean,
    val freshness: FreshnessLevel,
    val operatorId: String,
)

data class Score(val value: Double, val reasons: List<String>, val warnings: List<String>)

/** Ranks charging stations (0..100, plus operator preference). Shared so server and app rank identically. */
object StationScorer {

    /** Current availability, discounted for stations far away (their status says less about arrival time). */
    fun availability(counts: ChargerCounts, etaMin: Double?): Double {
        val now = when {
            counts.available >= 2 -> 1.0
            counts.available == 1 -> 0.75
            counts.unknown > 0 -> 0.4
            else -> 0.15
        }
        if (etaMin == null) return now
        return 0.5 + (now - 0.5) * max(0.4, 1.0 - etaMin / 120.0)
    }

    /** 50 kW → 0, 350 kW → 1 (log scale). */
    fun power(maxKw: Int?): Double =
        if (maxKw == null || maxKw <= 0) 0.0 else (ln(maxKw / 50.0) / ln(7.0)).coerceIn(0.0, 1.0)

    fun detour(detourMin: Double?, maxDetourMin: Int): Double =
        if (detourMin == null) 0.5 else 1.0 - (detourMin / maxDetourMin.coerceAtLeast(1)).coerceIn(0.0, 1.0)

    /** Arrival SOC above the reserve; 15 points of margin count as full marks. */
    fun margin(arrivalSocPct: Double?, reserveSocPct: Double): Double =
        if (arrivalSocPct == null) 0.5 else ((arrivalSocPct - reserveSocPct) / 15.0).coerceIn(0.0, 1.0)

    fun freshnessFactor(level: FreshnessLevel): Double = when (level) {
        FreshnessLevel.LIVE -> 1.0
        FreshnessLevel.DELAYED -> 0.9
        FreshnessLevel.STALE -> 0.7
    }

    fun score(input: ScoreInput, prefs: ChargePrefs, urgent: Boolean): Score {
        val w = if (urgent) ScoringWeights.URGENT else ScoringWeights.NORMAL
        val parts = w.availability * availability(input.compatible, input.etaMin) +
            w.power * power(input.maxKw) +
            w.detour * detour(input.detourMin, prefs.maxDetourMin) +
            w.margin * margin(input.arrivalSocPct, input.reserveSocPct) +
            w.window * (if (input.inPreferredWindow) 1.0 else 0.6)
        var value = freshnessFactor(input.freshness) * 100.0 * parts
        if (input.operatorId in prefs.preferredOperators) value += 5.0
        if (input.operatorId in prefs.avoidedOperators) value -= 15.0

        val reasons = buildList {
            add("AVAIL_${input.compatible.available}/${input.compatible.total}")
            input.maxKw?.let { add("KW_$it") }
            input.detourMin?.let { add("DETOUR_${it.roundToInt()}MIN") }
            input.arrivalSocPct?.let { add("ARRIVAL_${it.roundToInt()}PCT") }
            if (input.operatorId in prefs.preferredOperators) add("PREFERRED_OPERATOR")
        }
        val warnings = statusWarnings(input.compatible, input.freshness) + buildList {
            if (input.arrivalSocPct != null && input.arrivalSocPct < input.reserveSocPct + 3) add("LOW_ARRIVAL_MARGIN")
            if (input.operatorId in prefs.avoidedOperators) add("AVOIDED_OPERATOR")
        }
        return Score((value * 10).roundToInt() / 10.0, reasons, warnings)
    }

    /** Warnings that follow from the charger status alone, so a status refresh can work them out again. */
    fun statusWarnings(counts: ChargerCounts, freshness: FreshnessLevel): List<String> = buildList {
        when (freshness) {
            FreshnessLevel.STALE -> add("STATUS_STALE")
            FreshnessLevel.DELAYED -> add("STATUS_DELAYED")
            FreshnessLevel.LIVE -> Unit
        }
        if (counts.available == 0) add(if (counts.unknown > 0) "STATUS_UNKNOWN" else "NO_AVAILABLE_NOW")
    }

    val STATUS_WARNINGS = setOf("STATUS_STALE", "STATUS_DELAYED", "STATUS_UNKNOWN", "NO_AVAILABLE_NOW")

    /** Best first: score, then more free chargers, shorter detour, more power. */
    val CANDIDATE_ORDER: Comparator<Candidate> = compareByDescending<Candidate> { it.score }
        .thenByDescending { it.station.compatible.available }
        .thenBy { it.estDetourMin ?: Double.MAX_VALUE }
        .thenByDescending { it.station.maxKw ?: 0 }

    /**
     * Keeps at most [perBucket] candidates per [bucketKm] stretch of road (by [Candidate.estRoadKm]) so
     * the top cards are not the same service area three times. [sorted] must already be in rank order.
     */
    fun diversify(sorted: List<Candidate>, bucketKm: Double = 20.0, perBucket: Int = 2): List<Candidate> {
        val perBucketCount = HashMap<Int, Int>()
        return sorted.filter { candidate ->
            val bucket = (candidate.estRoadKm / bucketKm).toInt()
            val n = perBucketCount.getOrDefault(bucket, 0)
            if (n < perBucket) {
                perBucketCount[bucket] = n + 1
                true
            } else {
                false
            }
        }
    }
}
