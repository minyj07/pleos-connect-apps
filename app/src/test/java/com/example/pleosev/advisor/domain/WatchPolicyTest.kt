package com.example.pleosev.advisor.domain

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WatchPolicyTest {

    private val min = 60_000L

    private fun input(enabled: Boolean = true, charging: Boolean = false, busy: Boolean = true, guiding: Boolean = false) =
        WatchInput(enabled, charging, busy, guiding)

    @Test
    fun watchesWhileBusyAndTenMinutesLonger() {
        val policy = WatchPolicy()
        assertTrue(policy.shouldWatch(input(), 0L))
        assertTrue(policy.shouldWatch(input(busy = false), 9 * min))
        assertFalse(policy.shouldWatch(input(busy = false), 10 * min))
        assertTrue(policy.shouldWatch(input(), 11 * min))
    }

    @Test
    fun nothingToWatchNoService() {
        assertFalse(WatchPolicy().shouldWatch(input(busy = false), 0L))
    }

    @Test
    fun chargingOrTurningItOffStopsAtOnce() {
        val policy = WatchPolicy()
        policy.shouldWatch(input(), 0L)
        assertFalse(policy.shouldWatch(input(charging = true), min))
        assertFalse(policy.shouldWatch(input(busy = false), 2 * min)) // no grace left over from before charging
        assertTrue(policy.shouldWatch(input(), 3 * min))
        assertFalse(policy.shouldWatch(input(enabled = false), 4 * min))
    }

    @Test
    fun aChargingStopOnAGuidedTripKeepsTheWatch() {
        val policy = WatchPolicy()
        assertTrue(policy.shouldWatch(input(guiding = true), 0L))
        // Plugged in at the navi's charging stop with the navi on screen: the rest of the trip still needs watching.
        assertTrue(policy.shouldWatch(input(charging = true, guiding = true), min))
        assertTrue(policy.shouldWatch(input(guiding = true), 30 * min))
        // Charging at the end of the trip (no route left) stops it.
        assertFalse(policy.shouldWatch(input(charging = true, busy = false), 31 * min))
    }
}
