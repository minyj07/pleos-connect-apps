package com.example.pleosev.shared.charger

import com.example.pleosev.shared.api.ChargerState
import com.example.pleosev.shared.api.Connector
import com.example.pleosev.shared.api.FreshnessLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChargerRulesTest {
    private val now = 1_800_000_000_000L

    @Test
    fun chargerTypeCodesMapToConnectors() {
        assertEquals(setOf(Connector.DC_COMBO), ChargerTypes.connectors("04"))
        assertEquals(setOf(Connector.DC_CHADEMO, Connector.AC3, Connector.DC_COMBO), ChargerTypes.connectors("06"))
        assertEquals(setOf(Connector.AC_SLOW), ChargerTypes.connectors("2"))
        assertTrue(ChargerTypes.connectors("99").isEmpty())
    }

    @Test
    fun onlyDcChargersAreDc() {
        assertTrue(ChargerTypes.isDc(ChargerTypes.connectors("05")))
        assertTrue(ChargerTypes.isDc(ChargerTypes.connectors("03"))) // DC차데모 + AC3상
        assertFalse(ChargerTypes.isDc(ChargerTypes.connectors("07")))
        assertEquals(50, ChargerTypes.defaultOutputKw(setOf(Connector.DC_COMBO)))
        assertEquals(7, ChargerTypes.defaultOutputKw(setOf(Connector.AC_SLOW)))
    }

    @Test
    fun statCodesMapToStates() {
        assertEquals(ChargerState.AVAILABLE, ChargerStates.of("2", now - 86_400_000L, now)) // free all day: still available
        assertEquals(ChargerState.CHARGING, ChargerStates.of("3", now - 30 * 60_000L, now))
        assertEquals(ChargerState.CHARGING_STALE, ChargerStates.of("3", now - 4 * 3_600_000L, now))
        assertEquals(ChargerState.OUT_OF_SERVICE, ChargerStates.of("4", now, now))
        assertEquals(ChargerState.OUT_OF_SERVICE, ChargerStates.of("5", now, now))
        assertEquals(ChargerState.UNKNOWN, ChargerStates.of("1", now, now))
        assertEquals(ChargerState.UNKNOWN, ChargerStates.of("9", now, now))
        assertEquals(ChargerState.UNKNOWN, ChargerStates.of(null, null, now))
        // statUpdDt stamped again 10 minutes ago, but the session (nowTsdt) began 4 hours ago: stuck, not busy.
        assertEquals(ChargerState.CHARGING_STALE, ChargerStates.of("3", now - 10 * 60_000L, now, sessionStartAtMs = now - 4 * 3_600_000L))
        assertEquals(ChargerState.CHARGING, ChargerStates.of("3", now - 10 * 60_000L, now, sessionStartAtMs = now - 30 * 60_000L))
    }

    @Test
    fun staleChargingCountsAsUnknown() {
        val counts = ChargerStates.count(
            listOf(ChargerState.AVAILABLE, ChargerState.CHARGING, ChargerState.CHARGING_STALE, ChargerState.UNKNOWN, ChargerState.OUT_OF_SERVICE),
        )
        assertEquals(5, counts.total)
        assertEquals(1, counts.available)
        assertEquals(1, counts.charging)
        assertEquals(2, counts.unknown)
        assertEquals(1, counts.outOfService)
    }

    @Test
    fun freshnessFollowsServerPollingNotStatUpdDt() {
        assertEquals(FreshnessLevel.LIVE, FreshnessRules.level(now - 90_000L, now, coverageOk = true))
        assertEquals(FreshnessLevel.DELAYED, FreshnessRules.level(now - 10 * 60_000L, now, coverageOk = true))
        assertEquals(FreshnessLevel.STALE, FreshnessRules.level(now - 20 * 60_000L, now, coverageOk = true))
        assertEquals(FreshnessLevel.STALE, FreshnessRules.level(now - 60_000L, now, coverageOk = false))
        assertEquals(FreshnessLevel.STALE, FreshnessRules.level(null, now, coverageOk = true))
    }
}
