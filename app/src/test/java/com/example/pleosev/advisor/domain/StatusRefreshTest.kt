package com.example.pleosev.advisor.domain

import com.example.pleosev.advisor.domain.PlannedStopVerdict.BUSY
import com.example.pleosev.advisor.domain.PlannedStopVerdict.NOT_FOUND
import com.example.pleosev.advisor.domain.PlannedStopVerdict.OK
import com.example.pleosev.advisor.domain.PlannedStopVerdict.UNKNOWN
import com.example.pleosev.shared.api.FreshnessLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StatusRefreshTest {

    @Test
    fun freshCountsReplaceTheOldOnesInTheSameOrder() {
        val a = candidate(station(statId = "A", available = 2))
        val b = candidate(station(statId = "B", available = 1))
        val merged = StatusRefresh.merge(listOf(a, b), mapOf("B" to station(statId = "B", available = 0)))
        assertEquals(listOf("A", "B"), merged.map { it.station.statId })
        assertEquals(2, merged[0].station.compatible.available)
        assertEquals(0, merged[1].station.compatible.available)
        assertEquals(b.estArrivalSocPct, merged[1].estArrivalSocPct)
    }

    @Test
    fun statusWarningsFollowTheFreshStatusAndTheOthersStay() {
        val wasFull = candidate(station(statId = "A", available = 0)).copy(warnings = listOf("NO_AVAILABLE_NOW", "LOW_ARRIVAL_MARGIN"))
        val wasFree = candidate(station(statId = "B", available = 2))
        val merged = StatusRefresh.merge(
            listOf(wasFull, wasFree),
            mapOf("A" to station(statId = "A", available = 2), "B" to station(statId = "B", available = 0)),
        )
        assertEquals(listOf("LOW_ARRIVAL_MARGIN"), merged[0].warnings)
        assertEquals(listOf("NO_AVAILABLE_NOW"), merged[1].warnings)
    }

    @Test
    fun aPlannedStopThatFilledUpOrNothingFreeCallsForNewRecommendations() {
        val free = listOf(candidate(station(statId = "A", available = 1)))
        val full = listOf(candidate(station(statId = "A", available = 0)))
        assertTrue(StatusRefresh.needsNewRecommendations(OK, BUSY, free))
        assertTrue(StatusRefresh.needsNewRecommendations(OK, UNKNOWN, free))
        assertFalse(StatusRefresh.needsNewRecommendations(BUSY, BUSY, free)) // already showing alternatives
        assertFalse(StatusRefresh.needsNewRecommendations(null, null, free))
        assertTrue(StatusRefresh.needsNewRecommendations(null, null, full))
        assertFalse(StatusRefresh.needsNewRecommendations(null, null, emptyList()))
    }

    @Test
    fun verdictOnThePlannedStop() {
        assertEquals(NOT_FOUND, PlannedStopVerdict.of(null))
        assertEquals(OK, PlannedStopVerdict.of(station(available = 1)))
        assertEquals(BUSY, PlannedStopVerdict.of(station(available = 0)))
        val unclear = station(available = 0).let { it.copy(compatible = it.compatible.copy(charging = 0, unknown = 2)) }
        assertEquals(UNKNOWN, PlannedStopVerdict.of(unclear))
        val stale = station(available = 3).let { it.copy(freshness = it.freshness.copy(level = FreshnessLevel.STALE)) }
        assertEquals(UNKNOWN, PlannedStopVerdict.of(stale))
        // Only 50 kW chargers there and the driver asked for 100 kW+: the server counts 0 of 0, which is not "full".
        val noneAtThatPower = station(available = 0).let { it.copy(compatible = it.compatible.copy(total = 0, charging = 0)) }
        assertEquals(PlannedStopVerdict.NO_MATCHING_CHARGER, PlannedStopVerdict.of(noneAtThatPower))
    }
}
