package com.example.pleosev.advisor.domain

import kotlin.math.max

enum class NeedKind {
    NONE,
    ON_ROUTE,      // guidance active, arrival SOC below the reserve and no charging stop in the route
    PLANNED_STOP,  // guidance active with a charging stop (usually the navi's own): check it is usable
    NEARBY,        // no guidance, battery low
}

enum class Severity { NONE, ADVISORY, RECOMMENDED, URGENT }

enum class EstimateBasis { NAVI, RANGE, NONE }

/** Everything the evaluator looks at; built from the vehicle state, the navi state and the settings. */
data class NeedInput(
    val socPct: Double?,
    val rangeKm: Double?,
    val capacityKwh: Double?,
    val charging: Boolean,
    val guiding: Boolean,
    val remainDistanceKm: Double?,
    val destinationRemainWh: Int?,  // navi: battery energy left at the destination
    val naviTargetSocPct: Int?,     // navi: minimum arrival SOC
    val chargingStopInRoute: Boolean,
    val lowBatteryWarning: Boolean, // navi reported CAUSE_LOW_BATTERY recently
    val routeKey: String?,
    val userReserveSocPct: Int,
)

data class ChargeNeed(
    val kind: NeedKind,
    val severity: Severity,
    val reserveSocPct: Int,
    val arrivalSocPct: Double?,     // at the destination while guiding
    val basis: EstimateBasis,
    val ratePctPerKm: Double,
    val reachKm: Double?,           // km until the reserve is reached
    val routeKey: String?,
    val sinceMs: Long,              // when this kind / severity began (notifications wait for it to settle)
) {
    val active: Boolean get() = kind != NeedKind.NONE

    companion object {
        const val DEFAULT_RATE = 0.22 // % per km, ≈ 77 kWh pack at 5.9 km/kWh

        val NONE = ChargeNeed(NeedKind.NONE, Severity.NONE, 10, null, EstimateBasis.NONE, DEFAULT_RATE, null, null, 0L)
    }
}

/**
 * Decides whether the driver needs a charging recommendation. Stateful only for hysteresis: a need is
 * entered at one threshold and left at a safer one, so values hovering around a limit do not flap.
 *
 * Arrival SOC: the navi's remaining energy (Wh) over the battery capacity when available (spike:
 * remain*Charging values are Wh), otherwise SOC scaled by remaining distance / range with a 10 % margin.
 * With a charging stop in the route the navi's figure already includes that charge, so the stop itself
 * is checked instead (PLANNED_STOP).
 */
class ChargeNeedEvaluator {

    private var previous: ChargeNeed = ChargeNeed.NONE

    // The navi's arrival figure as it last came, and the lowest SOC seen since (see trackNaviFigure).
    private var naviFigureWh: Int? = null
    private var naviFigureRoute: String? = null
    private var lowestSocSinceFigure: Double? = null
    private var naviFigureStale = false

    fun evaluate(input: NeedInput, nowMs: Long): ChargeNeed {
        val reserve = max(input.userReserveSocPct, input.naviTargetSocPct ?: 0)
        val soc = input.socPct
        val sameRoute = input.routeKey == previous.routeKey

        trackNaviFigure(input)
        val (arrival, basis) = arrivalSoc(input)
        val rate = rate(input, soc, arrival, basis)
        val reach = soc?.let { max(0.0, (it - reserve) / rate) }
        // Urgency is about running empty, not about dipping into the reserve: prefer the car's own range. It is left
        // only with some margin: the range moves by a few km (regen), and every flip changes the request and the alert.
        val rangeToEmpty = input.rangeKm ?: soc?.let { it / rate }
        val wasUrgent = sameRoute && previous.severity == Severity.URGENT
        val socLimit = if (wasUrgent) URGENT_SOC + URGENT_EXIT_SOC else URGENT_SOC
        val rangeLimit = if (wasUrgent) URGENT_RANGE_KM + URGENT_EXIT_KM else URGENT_RANGE_KM
        val urgent = soc != null &&
            (soc <= socLimit || (rangeToEmpty != null && rangeToEmpty < rangeLimit) || input.lowBatteryWarning)

        val (kind, severity) = if (input.charging || soc == null) {
            NeedKind.NONE to Severity.NONE
        } else if (input.guiding && input.chargingStopInRoute) {
            NeedKind.PLANNED_STOP to (if (urgent) Severity.URGENT else Severity.ADVISORY)
        } else if (input.guiding && arrival != null) {
            val wasShort = sameRoute && previous.kind == NeedKind.ON_ROUTE && previous.severity >= Severity.RECOMMENDED
            when {
                arrival < reserve || (wasShort && arrival < reserve + EXIT_MARGIN) ->
                    NeedKind.ON_ROUTE to (if (urgent) Severity.URGENT else Severity.RECOMMENDED)
                arrival < reserve + ADVISORY_MARGIN -> NeedKind.ON_ROUTE to Severity.ADVISORY
                else -> NeedKind.NONE to Severity.NONE
            }
        } else {
            val range = input.rangeKm
            val low = soc <= LOW_SOC || (range != null && range <= LOW_RANGE_KM)
            val recovered = soc >= RECOVERED_SOC && (range == null || range >= RECOVERED_RANGE_KM)
            if (low || (previous.kind == NeedKind.NEARBY && !recovered)) {
                NeedKind.NEARBY to (if (urgent) Severity.URGENT else Severity.RECOMMENDED)
            } else {
                NeedKind.NONE to Severity.NONE
            }
        }

        val unchanged = kind == previous.kind && severity == previous.severity && sameRoute
        val need = ChargeNeed(
            kind = kind,
            severity = severity,
            reserveSocPct = reserve,
            arrivalSocPct = arrival,
            basis = if (arrival == null) EstimateBasis.NONE else basis,
            ratePctPerKm = rate,
            reachKm = reach,
            routeKey = input.routeKey,
            sinceMs = if (unchanged) previous.sinceMs else nowMs,
        )
        previous = need
        return need
    }

    /**
     * The navi works its arrival figure out from the battery it had when the route was planned and keeps it while
     * the battery changes under it (Virtual Console E2E 2026-11-04: SOC 25 -> 100 % left 6.4 kWh). A drop shows in
     * arrivalSoc (the figure ends up above the SOC). A charge does not: once the SOC rose above its lowest value
     * since the figure came, the figure is about another battery until the navi plans again (a new figure or route).
     */
    private fun trackNaviFigure(input: NeedInput) {
        val soc = input.socPct
        if (input.destinationRemainWh != naviFigureWh || input.routeKey != naviFigureRoute) {
            naviFigureWh = input.destinationRemainWh
            naviFigureRoute = input.routeKey
            lowestSocSinceFigure = soc
            naviFigureStale = false
            return
        }
        if (soc == null) return
        val lowest = lowestSocSinceFigure?.let { minOf(it, soc) } ?: soc
        lowestSocSinceFigure = lowest
        if (soc > lowest + NAVI_ARRIVAL_SLACK_PCT) naviFigureStale = true
    }

    private fun arrivalSoc(input: NeedInput): Pair<Double?, EstimateBasis> {
        if (!input.guiding) return null to EstimateBasis.NONE
        val soc = input.socPct
        val capacity = input.capacityKwh
        val remainWh = input.destinationRemainWh
        if (remainWh != null && capacity != null && capacity > 0.0 && !naviFigureStale) {
            val navi = remainWh / (capacity * 1000.0) * 100.0
            // After a drop the frozen figure can be above the SOC, which no arrival is: the range decides then.
            if (soc == null || navi <= soc + NAVI_ARRIVAL_SLACK_PCT) return navi to EstimateBasis.NAVI
        }
        val range = input.rangeKm
        val remain = input.remainDistanceKm
        if (soc != null && range != null && range > 1.0 && remain != null) {
            return soc * (1.0 - RANGE_SAFETY * remain / range) to EstimateBasis.RANGE
        }
        return null to EstimateBasis.NONE
    }

    private fun rate(input: NeedInput, soc: Double?, arrival: Double?, basis: EstimateBasis): Double {
        val remain = input.remainDistanceKm
        // Without a charging stop the navi's arrival figure gives the consumption it expects on this route. The
        // range-based arrival carries a 10 % margin, which is not a consumption: then SOC / range, as the server does.
        if (basis == EstimateBasis.NAVI && soc != null && arrival != null && remain != null && remain > 1.0 && !input.chargingStopInRoute) {
            ((soc - arrival) / remain).takeIf { it in PLAUSIBLE_RATE }?.let { return it }
        }
        val range = input.rangeKm
        if (soc != null && range != null && range > 1.0) {
            (soc / range).takeIf { it in PLAUSIBLE_RATE }?.let { return it }
        }
        return ChargeNeed.DEFAULT_RATE
    }

    private companion object {
        val PLAUSIBLE_RATE = 0.05..0.6
        const val NAVI_ARRIVAL_SLACK_PCT = 2.0 // as CandidateRefiner: SOC is whole percent, the navi rounds its own way
        const val RANGE_SAFETY = 1.1
        const val EXIT_MARGIN = 3        // ON_ROUTE stays until arrival >= reserve + 3
        const val ADVISORY_MARGIN = 5    // reserve <= arrival < reserve + 5: "may need charging"
        const val LOW_SOC = 20.0
        const val LOW_RANGE_KM = 50.0
        const val RECOVERED_SOC = 25.0
        const val RECOVERED_RANGE_KM = 60.0
        const val URGENT_SOC = 8.0
        const val URGENT_RANGE_KM = 25.0
        const val URGENT_EXIT_SOC = 2.0      // urgent until SOC > 10 %
        const val URGENT_EXIT_KM = 5.0       // and range >= 30 km
    }
}
