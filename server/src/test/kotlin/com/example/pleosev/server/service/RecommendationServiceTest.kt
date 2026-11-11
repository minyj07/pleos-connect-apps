package com.example.pleosev.server.service

import com.example.pleosev.server.Fixture
import com.example.pleosev.server.syncedFakeRuntime
import com.example.pleosev.shared.api.GeoPoint
import com.example.pleosev.shared.api.PlannedStop
import com.example.pleosev.shared.api.RecommendMode
import com.example.pleosev.shared.api.RecommendationRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RecommendationServiceTest {

    private val runtime = syncedFakeRuntime()
    private val pangyo = GeoPoint(37.4127, 127.0947)       // the Pleos emulator's default navi position
    private val busanStation = GeoPoint(35.1151, 129.0422)
    private val goesanNaviStop = GeoPoint(36.8327504, 127.9583201)

    /** The spike's trip: 판교 → 부산역, 378 km / 4 h 4 min, SOC 42 % (≈ 0.2 %/km). */
    private val toBusan = RecommendationRequest(
        mode = RecommendMode.ON_ROUTE,
        origin = pangyo,
        destination = busanStation,
        remainDistanceM = 378_000,
        remainTimeS = 14_640,
        socPct = 42.0,
        rangeKm = 210.0,
    )

    @Test
    fun onRouteKeepsReachableUsableStationsInThePreferredStretch() {
        val response = runtime.recommendations.recommend(toBusan)
        val window = assertNotNull(response.window)
        assertEquals(160.0, window.reachKm)      // (42 - 10) / 0.2
        assertEquals(72.0, window.preferredFromKm)
        assertEquals(136.0, window.preferredToKm)
        assertTrue(window.sinuosity in 1.15..1.3)

        val ids = response.candidates.map { it.station.statId }
        assertTrue(ids.isNotEmpty())
        val neverValid = setOf(Fixture.OUT_OF_SERVICE, Fixture.OUT_OF_SERVICE_PANGYO, Fixture.RESTRICTED, Fixture.AC_ONLY, Fixture.DELETED)
        assertTrue(ids.none { it in neverValid }, "invalid candidate in $ids")
        assertTrue(ids.none { it in Fixture.PANGYO_NEAR }, "stations right after departure should be dropped: $ids")
        assertTrue(Fixture.GOESAN_OTHER_DIRECTION !in ids, "opposite carriageway offered: $ids")
        assertTrue(response.candidates.all { (it.estArrivalSocPct ?: 0.0) >= 3.0 })
        assertTrue(response.candidates.take(3).any { it.estRoadKm in 72.0..136.0 }, "top 3 should include the preferred stretch")
        // No more than two candidates per 20 km of road.
        assertTrue(response.candidates.groupBy { (it.estRoadKm / 20).toInt() }.values.all { it.size <= 2 })
        assertTrue(response.coverageOk)
    }

    @Test
    fun nearbyReturnsReachableStationsAroundTheCar() {
        val response = runtime.recommendations.recommend(
            RecommendationRequest(mode = RecommendMode.NEARBY, origin = pangyo, socPct = 12.0, rangeKm = 50.0),
        )
        assertEquals(Fixture.PANGYO_NEAR, response.candidates.map { it.station.statId }.toSet())
    }

    @Test
    fun urgentModeAcceptsStationsBelowTheNormalArrivalMargin() {
        val almostEmpty = RecommendationRequest(mode = RecommendMode.NEARBY, origin = pangyo, socPct = 2.0, rangeKm = 9.0)
        assertTrue(runtime.recommendations.recommend(almostEmpty).candidates.isEmpty())
        val urgent = runtime.recommendations.recommend(almostEmpty.copy(urgent = true))
        assertEquals(Fixture.PANGYO_NEAR, urgent.candidates.map { it.station.statId }.toSet())
    }

    @Test
    fun aroundPlannedStopIdentifiesTheNaviStopAndOffersOthers() {
        val response = runtime.recommendations.recommend(
            toBusan.copy(
                mode = RecommendMode.AROUND_PLANNED_STOP,
                plannedStop = PlannedStop(goesanNaviStop, "워터 괴산휴게소 창원방향 전기차충전소", maxKw = 350),
            ),
        )
        assertEquals(Fixture.GOESAN_WATER, response.plannedStop?.statId)
        val ids = response.candidates.map { it.station.statId }
        assertTrue(Fixture.GOESAN_WATER !in ids)
        assertTrue(Fixture.GOESAN_OTHER_DIRECTION !in ids, "opposite carriageway offered: $ids")
        assertTrue(Fixture.MUNGYEONG in ids && Fixture.GOESAN_ME_SAME_SITE in ids, "alternatives: $ids")
    }

    @Test
    fun nearDestinationFindsChargersAroundTheDestination() {
        val response = runtime.recommendations.recommend(
            toBusan.copy(mode = RecommendMode.NEAR_DESTINATION, socPct = 90.0, rangeKm = 450.0),
        )
        assertEquals(listOf(Fixture.BUSAN_STATION_PARKING), response.candidates.map { it.station.statId })
    }

    @Test
    fun excludedStationsAreSkipped() {
        val first = runtime.recommendations.recommend(toBusan).candidates.first().station.statId
        val again = runtime.recommendations.recommend(toBusan.copy(excludeStatIds = listOf(first)))
        assertTrue(again.candidates.none { it.station.statId == first })
    }
}
