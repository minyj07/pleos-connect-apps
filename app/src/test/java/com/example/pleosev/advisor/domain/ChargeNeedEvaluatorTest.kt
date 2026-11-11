package com.example.pleosev.advisor.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChargeNeedEvaluatorTest {

    private val capacityKwh = 77.4

    /** Wh left at the destination for an arrival SOC of [pct] with a 77.4 kWh pack. */
    private fun wh(pct: Double) = (capacityKwh * 1000 * pct / 100).toInt()

    private fun input(
        soc: Double? = 60.0,
        range: Double? = 300.0,
        charging: Boolean = false,
        guiding: Boolean = false,
        remainKm: Double? = null,
        remainWh: Int? = null,
        naviTarget: Int? = null,
        chargingStop: Boolean = false,
        lowWarning: Boolean = false,
        route: String? = null,
        reserve: Int = 10,
    ) = NeedInput(soc, range, capacityKwh, charging, guiding, remainKm, remainWh, naviTarget, chargingStop, lowWarning, route, reserve)

    @Test
    fun healthyBatteryNeedsNothingAndChargingSuppressesEverything() {
        val evaluator = ChargeNeedEvaluator()
        assertEquals(NeedKind.NONE, evaluator.evaluate(input(), 0L).kind)
        assertEquals(NeedKind.NONE, evaluator.evaluate(input(soc = 5.0, charging = true), 0L).kind)
        assertEquals(NeedKind.NONE, evaluator.evaluate(input(soc = null), 0L).kind)
    }

    @Test
    fun lowBatteryWithoutGuidanceAsksForNearbyAndRecoversWithMargin() {
        val evaluator = ChargeNeedEvaluator()
        assertEquals(NeedKind.NEARBY, evaluator.evaluate(input(soc = 19.0, range = 80.0), 0L).kind)
        assertEquals(NeedKind.NEARBY, evaluator.evaluate(input(soc = 22.0, range = 90.0), 1L).kind) // not yet 25 %
        assertEquals(NeedKind.NONE, evaluator.evaluate(input(soc = 26.0, range = 100.0), 2L).kind)
        assertEquals(NeedKind.NEARBY, evaluator.evaluate(input(soc = 40.0, range = 45.0), 3L).kind) // range alone
    }

    @Test
    fun almostEmptyIsUrgent() {
        val need = ChargeNeedEvaluator().evaluate(input(soc = 7.0, range = 30.0), 0L)
        assertEquals(NeedKind.NEARBY, need.kind)
        assertEquals(Severity.URGENT, need.severity)
        // 16 % with 64 km left is low, not yet urgent; 20 km left is.
        assertEquals(Severity.RECOMMENDED, ChargeNeedEvaluator().evaluate(input(soc = 16.0, range = 64.0), 0L).severity)
        assertEquals(Severity.URGENT, ChargeNeedEvaluator().evaluate(input(soc = 12.0, range = 20.0), 0L).severity)
    }

    @Test
    fun urgencyIsLeftOnlyWithAMargin() {
        val evaluator = ChargeNeedEvaluator()
        assertEquals(Severity.URGENT, evaluator.evaluate(input(soc = 12.0, range = 24.8), 0L).severity)
        // Regen lifts the range just over 25 km: still urgent, no new request and no new alert.
        assertEquals(Severity.URGENT, evaluator.evaluate(input(soc = 12.0, range = 25.3), 1L).severity)
        assertEquals(Severity.RECOMMENDED, evaluator.evaluate(input(soc = 12.0, range = 31.0), 2L).severity)
    }

    @Test
    fun onRouteUsesTheNavisArrivalEnergy() {
        val need = ChargeNeedEvaluator().evaluate(input(soc = 42.0, range = 210.0, guiding = true, remainKm = 378.0, remainWh = wh(9.0)), 0L)
        assertEquals(NeedKind.ON_ROUTE, need.kind)
        assertEquals(Severity.RECOMMENDED, need.severity)
        assertEquals(EstimateBasis.NAVI, need.basis)
        assertEquals(9.0, need.arrivalSocPct!!, 0.05)
        assertEquals((42.0 - 9.0) / 378.0, need.ratePctPerKm, 1e-3)
    }

    @Test
    fun arrivalNearTheReserveIsAdvisoryAndShortfallSticksUntilThreeAbove() {
        val evaluator = ChargeNeedEvaluator()
        fun at(pct: Double) = evaluator.evaluate(input(soc = 50.0, guiding = true, remainKm = 200.0, remainWh = wh(pct), route = "r1"), 0L)
        assertEquals(Severity.ADVISORY, at(12.0).severity)
        assertEquals(Severity.RECOMMENDED, at(9.0).severity)
        assertEquals(Severity.RECOMMENDED, at(11.5).severity) // hysteresis: needs reserve + 3
        assertEquals(Severity.ADVISORY, at(13.5).severity)
        assertEquals(NeedKind.NONE, at(16.0).kind)
    }

    @Test
    fun anewRouteResetsTheHysteresis() {
        val evaluator = ChargeNeedEvaluator()
        evaluator.evaluate(input(soc = 50.0, guiding = true, remainKm = 200.0, remainWh = wh(9.0), route = "r1"), 0L)
        val next = evaluator.evaluate(input(soc = 50.0, guiding = true, remainKm = 200.0, remainWh = wh(11.5), route = "r2"), 1L)
        assertEquals(Severity.ADVISORY, next.severity)
    }

    @Test
    fun theNavisTargetSocRaisesTheReserve() {
        val need = ChargeNeedEvaluator().evaluate(
            input(soc = 50.0, guiding = true, remainKm = 200.0, remainWh = wh(12.0), naviTarget = 15),
            0L,
        )
        assertEquals(15, need.reserveSocPct)
        assertEquals(Severity.RECOMMENDED, need.severity)
    }

    @Test
    fun aChargingStopInTheRouteIsCheckedInsteadOfTheArrivalFigure() {
        val need = ChargeNeedEvaluator().evaluate(
            input(soc = 42.0, guiding = true, remainKm = 378.0, remainWh = wh(10.0), chargingStop = true),
            0L,
        )
        assertEquals(NeedKind.PLANNED_STOP, need.kind)
        assertEquals(Severity.ADVISORY, need.severity)
    }

    @Test
    fun aNaviArrivalAboveTheSocIsLeftOverAndTheRangeDecides() {
        // Route planned at 42 %, then the Virtual Console dropped the battery to 7 %: the navi still says 38 %.
        val need = ChargeNeedEvaluator().evaluate(input(soc = 7.0, range = 25.0, guiding = true, remainKm = 20.0, remainWh = wh(38.0)), 0L)
        assertEquals(EstimateBasis.RANGE, need.basis)
        assertEquals(NeedKind.ON_ROUTE, need.kind)
        assertEquals(Severity.URGENT, need.severity)
        // Whole-percent SOC: a figure a little above it is still the navi's.
        val nearlyThere = ChargeNeedEvaluator().evaluate(input(soc = 42.0, guiding = true, remainKm = 1.0, remainWh = wh(42.6)), 0L)
        assertEquals(EstimateBasis.NAVI, nearlyThere.basis)
    }

    @Test
    fun aNaviArrivalFromBeforeAChargeIsNotTrusted() {
        // Planned at 40 % with 6 % left on arrival, then charged to 90 % without the navi planning again.
        val evaluator = ChargeNeedEvaluator()
        val planned = evaluator.evaluate(input(soc = 40.0, range = 160.0, guiding = true, remainKm = 200.0, remainWh = wh(6.0), route = "r1"), 0L)
        assertEquals(NeedKind.ON_ROUTE, planned.kind)
        val charged = evaluator.evaluate(input(soc = 90.0, range = 400.0, guiding = true, remainKm = 200.0, remainWh = wh(6.0), route = "r1"), 1L)
        assertEquals(EstimateBasis.RANGE, charged.basis)
        assertEquals(NeedKind.NONE, charged.kind) // 90 % and 400 km for 200 km: about 40 % left
        assertEquals(90.0 / 400.0, charged.ratePctPerKm, 1e-9) // SOC / range, as the server works it out
        // The navi plans again (a new figure): it counts again.
        val replanned = evaluator.evaluate(input(soc = 90.0, range = 400.0, guiding = true, remainKm = 200.0, remainWh = wh(45.0), route = "r1"), 2L)
        assertEquals(EstimateBasis.NAVI, replanned.basis)
    }

    @Test
    fun rangeIsTheFallbackWhenTheNaviHasNoEnergyFigure() {
        val need = ChargeNeedEvaluator().evaluate(input(soc = 30.0, range = 120.0, guiding = true, remainKm = 120.0), 0L)
        assertEquals(NeedKind.ON_ROUTE, need.kind)
        assertEquals(EstimateBasis.RANGE, need.basis)
        assertTrue(need.arrivalSocPct!! < 0.0)
    }

    @Test
    fun anUnchangedNeedKeepsItsStartTime() {
        val evaluator = ChargeNeedEvaluator()
        val first = evaluator.evaluate(input(soc = 15.0), 1_000L)
        val second = evaluator.evaluate(input(soc = 14.0), 61_000L)
        assertEquals(1_000L, second.sinceMs)
        assertEquals(first.kind, second.kind)
        val urgent = evaluator.evaluate(input(soc = 6.0), 62_000L)
        assertEquals(62_000L, urgent.sinceMs)
    }
}
