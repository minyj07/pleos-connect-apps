package com.example.pleosev.shared.scoring

import com.example.pleosev.shared.api.Candidate
import com.example.pleosev.shared.api.ChargePrefs
import com.example.pleosev.shared.api.ChargerCounts
import com.example.pleosev.shared.api.Freshness
import com.example.pleosev.shared.api.FreshnessLevel
import com.example.pleosev.shared.api.GeoPoint
import com.example.pleosev.shared.api.StationSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StationScorerTest {
    private val prefs = ChargePrefs()

    private fun counts(available: Int, charging: Int = 0, unknown: Int = 0) =
        ChargerCounts(total = available + charging + unknown, available = available, charging = charging, unknown = unknown, outOfService = 0)

    private fun input(
        compatible: ChargerCounts = counts(2, 2),
        maxKw: Int? = 350,
        detourMin: Double? = 0.0,
        arrival: Double? = 40.0,
        freshness: FreshnessLevel = FreshnessLevel.LIVE,
        operatorId: String = "ME",
    ) = ScoreInput(compatible, maxKw, detourMin, etaMin = null, arrivalSocPct = arrival, reserveSocPct = 10.0,
        inPreferredWindow = true, freshness = freshness, operatorId = operatorId)

    @Test
    fun availabilityLadderAndDistanceDiscount() {
        assertEquals(1.0, StationScorer.availability(counts(2), null), 1e-9)
        assertEquals(0.75, StationScorer.availability(counts(1, 3), null), 1e-9)
        assertEquals(0.4, StationScorer.availability(counts(0, 2, unknown = 1), null), 1e-9)
        assertEquals(0.15, StationScorer.availability(counts(0, 4), null), 1e-9)
        // An hour away, "2 free now" is worth less: 0.5 + 0.5 * 0.5
        assertEquals(0.75, StationScorer.availability(counts(2), 60.0), 1e-9)
    }

    @Test
    fun powerIsLogScaledBetween50And350Kw() {
        assertEquals(0.0, StationScorer.power(50), 1e-9)
        assertEquals(1.0, StationScorer.power(350), 1e-9)
        assertEquals(1.0, StationScorer.power(400), 1e-9)
        assertTrue(StationScorer.power(100) in 0.35..0.36)
    }

    @Test
    fun perfectCandidateScores100() {
        assertEquals(100.0, StationScorer.score(input(), prefs, urgent = false).value, 1e-9)
    }

    @Test
    fun freeChargersBeatBusyOnes() {
        val free = StationScorer.score(input(compatible = counts(3, 1)), prefs, urgent = false)
        val busy = StationScorer.score(input(compatible = counts(0, 4)), prefs, urgent = false)
        assertTrue(free.value > busy.value)
        assertTrue("NO_AVAILABLE_NOW" in busy.warnings)
    }

    @Test
    fun staleStatusAndAvoidedOperatorArePenalised() {
        val live = StationScorer.score(input(), prefs, urgent = false).value
        val stale = StationScorer.score(input(freshness = FreshnessLevel.STALE), prefs, urgent = false)
        assertEquals(70.0, stale.value, 1e-9)
        assertTrue("STATUS_STALE" in stale.warnings)
        val avoided = StationScorer.score(input(operatorId = "XX"), prefs.copy(avoidedOperators = setOf("XX")), urgent = false)
        assertEquals(live - 15.0, avoided.value, 1e-9)
    }

    @Test
    fun urgentModeFavoursShortDetourOverFreeChargers() {
        val nearBusy = input(compatible = counts(0, 4), detourMin = 1.0, arrival = 12.0)
        val farFree = input(compatible = counts(4), detourMin = 14.0, arrival = 12.0)
        assertTrue(StationScorer.score(nearBusy, prefs, urgent = true).value > StationScorer.score(farFree, prefs, urgent = true).value)
    }

    @Test
    fun diversifyKeepsTwoPerTwentyKmStretch() {
        val ranked = listOf(10.0, 12.0, 15.0, 50.0).mapIndexed { i, km -> candidate("S$i", km, score = 90.0 - i) }
        val kept = StationScorer.diversify(ranked)
        assertEquals(listOf("S0", "S1", "S3"), kept.map { it.station.statId })
    }

    @Test
    fun candidateOrderBreaksTiesByFreeChargersThenDetour() {
        val a = candidate("A", 10.0, score = 80.0, available = 1, detour = 2.0)
        val b = candidate("B", 10.0, score = 80.0, available = 3, detour = 9.0)
        val c = candidate("C", 10.0, score = 80.0, available = 3, detour = 4.0)
        assertEquals(listOf("C", "B", "A"), listOf(a, b, c).sortedWith(StationScorer.CANDIDATE_ORDER).map { it.station.statId })
    }

    private fun candidate(id: String, roadKm: Double, score: Double, available: Int = 2, detour: Double = 0.0) = Candidate(
        station = StationSummary(
            statId = id, name = id, address = "", location = GeoPoint(36.0, 127.0), operatorId = "ME", operatorName = "환경부",
            compatible = counts(available, 1), maxKw = 100,
            freshness = Freshness(serverCheckedAtMs = 0L, sourceUpdatedAtMs = null, level = FreshnessLevel.LIVE),
        ),
        straightKm = roadKm, estRoadKm = roadKm, estDetourMin = detour, estArrivalSocPct = 30.0,
        score = score, reasons = emptyList(), warnings = emptyList(),
    )
}
