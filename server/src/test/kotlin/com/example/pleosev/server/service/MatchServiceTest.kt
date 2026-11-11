package com.example.pleosev.server.service

import com.example.pleosev.server.Fixture
import com.example.pleosev.server.syncedFakeRuntime
import com.example.pleosev.shared.api.ChargePrefs
import com.example.pleosev.shared.api.GeoPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MatchServiceTest {

    private val runtime = syncedFakeRuntime()
    private val goesanNaviStop = GeoPoint(36.8327504, 127.9583201) // TripInfo.routePoints of the spike

    @Test
    fun naviAutoStopMatchesItsStationAndListsTheSameSite() {
        val match = runtime.matcher.match(goesanNaviStop, "워터 괴산휴게소 창원방향 전기차충전소", 350, ChargePrefs())
        val best = assertNotNull(match.best)
        assertEquals(Fixture.GOESAN_WATER, best.station.statId)
        assertTrue(best.confidence >= 0.9, "confidence ${best.confidence}")
        assertEquals(listOf(Fixture.GOESAN_ME_SAME_SITE), match.siteStatIds)
        assertTrue(match.alternatives.none { it.station.statId == Fixture.GOESAN_OTHER_DIRECTION }) // ~700 m away
    }

    @Test
    fun closeScoresAtOneSiteStillGiveAMatch() {
        // No kW and the bare KECO name: both operators at the site score almost the same.
        val match = runtime.matcher.match(goesanNaviStop, "괴산휴게소(창원방향)", null, ChargePrefs())
        assertEquals(Fixture.GOESAN_WATER, match.best?.station?.statId)
        assertEquals(listOf(Fixture.GOESAN_ME_SAME_SITE), match.siteStatIds)
    }

    @Test
    fun nothingNearbyMeansNoMatch() {
        val match = runtime.matcher.match(GeoPoint(38.0, 128.5), "어딘가 충전소", 100, ChargePrefs())
        assertNull(match.best)
        assertTrue(match.alternatives.isEmpty())
        assertTrue(match.siteStatIds.isEmpty())
    }
}
