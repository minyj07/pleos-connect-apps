package com.example.pleosev.advisor.domain

import com.example.pleosev.shared.api.Candidate
import com.example.pleosev.shared.api.StationSummary
import com.example.pleosev.shared.scoring.StationScorer

/**
 * Periodic refresh between full recommendations: only the charger status is asked again
 * (`POST /stations/status` carries station ids, not the car's position). Cards keep their order so they
 * do not jump while driving; a worse picture calls for new recommendations instead of new numbers.
 */
object StatusRefresh {

    /** New counts and freshness, and the status warnings worked out again from them (the others stay). */
    fun merge(candidates: List<Candidate>, fresh: Map<String, StationSummary>): List<Candidate> =
        candidates.map { c ->
            val station = fresh[c.station.statId] ?: return@map c
            c.copy(
                station = station,
                warnings = StationScorer.statusWarnings(station.compatible, station.freshness.level) +
                    c.warnings.filterNot { it in StationScorer.STATUS_WARNINGS },
            )
        }

    fun needsNewRecommendations(before: PlannedStopVerdict?, after: PlannedStopVerdict?, candidates: List<Candidate>): Boolean {
        val plannedGotWorse = before == PlannedStopVerdict.OK && after != PlannedStopVerdict.OK
        val nothingFree = candidates.isNotEmpty() && candidates.none { it.station.compatible.available > 0 }
        return plannedGotWorse || nothingFree
    }
}
