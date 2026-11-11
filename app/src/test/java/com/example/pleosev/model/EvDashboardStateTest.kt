package com.example.pleosev.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EvDashboardStateTest {

    @Test
    fun chargingNeedsThePluggedInConnectorWhenTheCarReportsIt() {
        assertTrue(EvDashboardState(chargeState = ChargeState.CHARGING, chargePortConnected = true).isCharging)
        assertTrue(EvDashboardState(chargeState = ChargeState.CHARGING, chargePortConnected = null).isCharging)
        // A state left over from the last charge: advice must go on once the car is unplugged.
        assertFalse(EvDashboardState(chargeState = ChargeState.CHARGING, chargePortConnected = false).isCharging)
    }

    @Test
    fun withoutAChargeStateTheSwitchOrTheChargeRateDecide() {
        assertTrue(EvDashboardState(chargeSwitchOn = true).isCharging)
        assertTrue(EvDashboardState(chargeRateKw = 7.2f).isCharging)
        assertFalse(EvDashboardState(chargeSwitchOn = true, chargePortConnected = false).isCharging)
        // The Virtual Console's charge switch with the emulator's FULLY_CHARGED: not charging.
        assertFalse(EvDashboardState(chargeState = ChargeState.FULLY_CHARGED, chargeSwitchOn = true).isCharging)
    }
}
