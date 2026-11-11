package com.example.pleosev.advisor.domain

import com.example.pleosev.shared.api.GeoPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReachTest {

    private val pangyo = GeoPoint(37.4127, 127.0947)
    private val mungyeong = GeoPoint(36.6215556, 128.1495167) // ≈ 128 km straight from Pangyo

    @Test
    fun aStopBeyondTheRemainingRangeIsOutOfReach() {
        // E2E: SOC 7 %, 24 km left, the navi's stop at 문경 planned on a fuller battery.
        assertFalse(Reach.canReach(pangyo, mungyeong, rangeKm = 24.0, socPct = 7.0, ratePctPerKm = 0.22))
        assertTrue(Reach.canReach(pangyo, mungyeong, rangeKm = 210.0, socPct = 42.0, ratePctPerKm = 0.22))
    }

    @Test
    fun theRoadIsLongerThanTheStraightLine() {
        // 128 km straight ≈ 167 km by road: 150 km of range is not enough.
        assertFalse(Reach.canReach(pangyo, mungyeong, rangeKm = 150.0, socPct = null, ratePctPerKm = 0.22))
    }

    @Test
    fun aRangeThatContradictsTheSocIsNotUsed() {
        // Virtual Console E2E: its 0 m default arrived with SOC 100 % and read as an urgent "battery low".
        assertNull(Reach.usableRangeKm(rangeKm = 0.0, socPct = 100.0))
        assertNull(Reach.usableRangeKm(rangeKm = 14.0, socPct = 30.0)) // a full battery under 50 km
        assertNull(Reach.usableRangeKm(rangeKm = -1.0, socPct = null))
        assertNull(Reach.usableRangeKm(rangeKm = null, socPct = 50.0))
    }

    @Test
    fun realRangesAreKeptEvenForSmallPacksInWinter() {
        assertEquals(500.0, Reach.usableRangeKm(rangeKm = 500.0, socPct = 100.0))
        assertEquals(75.0, Reach.usableRangeKm(rangeKm = 75.0, socPct = 50.0))  // 35 kWh pack in the cold: 0.67 %/km
        assertEquals(25.0, Reach.usableRangeKm(rangeKm = 25.0, socPct = 7.0))
        assertEquals(0.0, Reach.usableRangeKm(rangeKm = 0.0, socPct = 0.0))
        assertEquals(40.0, Reach.usableRangeKm(rangeKm = 40.0, socPct = null))
    }

    @Test
    fun withoutRangeTheSocAndConsumptionDecideAndWithoutEitherItIsReachable() {
        assertFalse(Reach.canReach(pangyo, mungyeong, rangeKm = null, socPct = 7.0, ratePctPerKm = 0.22)) // ≈ 32 km
        assertTrue(Reach.canReach(pangyo, mungyeong, rangeKm = null, socPct = null, ratePctPerKm = 0.22))
    }
}
