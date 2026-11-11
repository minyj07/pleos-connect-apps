package com.example.pleosev.advisor.domain

import com.example.pleosev.navi.RoutePoint
import com.example.pleosev.navi.TripEstimate
import com.example.pleosev.shared.api.ChargePrefs
import com.example.pleosev.shared.api.GeoPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CandidateRefinerTest {

    private val context = RefineContext(
        destination = BUSAN_STATION,
        remainTimeS = 14_640,      // 판교 → 부산역 in the spike
        avgSpeedKmh = 93.0,
        reserveSocPct = 10.0,
        window = null,
        prefs = ChargePrefs(),
        urgent = false,
        socPct = 42.0,
        ratePctPerKm = 0.2,
    )

    /** requestTrip to 괴산 at SOC 42 % in the spike: 123 km, 4984 s, arrival 18 %. */
    private fun trip(vararg extra: RoutePoint) = TripEstimate(
        distanceM = 123_151,
        etaS = 4_984,
        arrivalSocPct = 18,
        energyWh = 17_917.0,
        routePoints = extra.toList() + RoutePoint("괴산", GOESAN, null, 100.0, isChargingStation = false, isAuto = false, isDestination = true),
    )

    @Test
    fun theNavisTripReplacesTheStraightLineEstimate() {
        val refined = assertNotNull(CandidateRefiner.refine(candidate(), trip(), context))
        assertEquals(123.2, refined.estRoadKm)
        assertEquals(18.0, refined.estArrivalSocPct)
        assertTrue(refined.estDetourMin!! in 5.0..20.0, "detour ${refined.estDetourMin}")
        assertTrue(CandidateRefiner.REFINED in refined.reasons)
    }

    @Test
    fun whenTheNaviWouldChargeOnTheWayOnlyAStationOutOfReachIsDropped() {
        val autoStop = RoutePoint("충주휴게소", GeoPoint(37.0, 127.8), "poi", 40.0, isChargingStation = true, isAuto = true, isDestination = false)
        // Below the navi's 10 % minimum but still reachable: kept with the server's own estimate.
        val low = candidate().copy(estArrivalSocPct = 6.0)
        assertEquals(low, CandidateRefiner.refine(low, trip(autoStop), context))
        assertNull(CandidateRefiner.refine(candidate().copy(estArrivalSocPct = -3.0), trip(autoStop), context))
    }

    @Test
    fun anArrivalAboveTheCurrentChargeFallsBackToTheCarsRate() {
        // Emulator: SOC set to 15 % while the battery energy still said 42 %; the navi answered 41 % for 2 km.
        val nearby = TripEstimate(2_000, 360, 41, 300.0, listOf(RoutePoint("E-pit 판교", GOESAN, null, 100.0, false, false, true)))
        val refined = assertNotNull(CandidateRefiner.refine(candidate(), nearby, context.copy(destination = null, socPct = 15.0, ratePctPerKm = 0.25)))
        assertEquals(14.5, refined.estArrivalSocPct)
    }

    @Test
    fun withoutADestinationTheDetourIsTheTripItself() {
        val refined = assertNotNull(CandidateRefiner.refine(candidate(), trip(), context.copy(destination = null)))
        assertEquals(83.1, refined.estDetourMin!!, 0.05) // 4984 s
    }
}
