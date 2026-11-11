package com.example.pleosev.advisor.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AlertPolicyTest {

    private val min = 60_000L

    private fun input(
        topic: AlertTopic? = AlertTopic.PLANNED_STOP,
        severity: Severity = Severity.RECOMMENDED,
        trip: String? = "부산역@35.115",
        ready: Boolean = true,
        visible: Boolean = false,
        subject: String? = null,
    ) = AlertInput(topic, severity, trip, ready, visible, subject)

    private fun AlertDecision.isHeadsUp() = this is AlertDecision.Show && headsUp

    @Test
    fun aTopicHasToHoldBeforeItAlertsButAnUrgentOneDoesNot() {
        val policy = AlertPolicy()
        assertEquals(AlertDecision.None, policy.decide(input(), 0L))
        assertEquals(AlertDecision.None, policy.decide(input(), 44_000L))
        assertTrue(policy.decide(input(), 45_000L).isHeadsUp())
        assertTrue(AlertPolicy().decide(input(severity = Severity.URGENT), 0L).isHeadsUp())
    }

    @Test
    fun noAlertBeforeTheCardsAreIn() {
        val policy = AlertPolicy()
        assertEquals(AlertDecision.None, policy.decide(input(ready = false), 0L))
        assertEquals(AlertDecision.None, policy.decide(input(ready = false), 2 * min))
        assertTrue(policy.decide(input(), 2 * min + 1).isHeadsUp())
    }

    @Test
    fun theSameTopicUpdatesQuietlyAndPopsUpAgainOnlyAfterFifteenMinutes() {
        val policy = AlertPolicy()
        policy.decide(input(), 0L)
        assertTrue(policy.decide(input(), 45_000L).isHeadsUp())
        val update = assertIs<AlertDecision.Show>(policy.decide(input(), 3 * min))
        assertFalse(update.headsUp)
        assertEquals(10 * min - (3 * min - 45_000L), update.timeoutMs) // the notification keeps its original end
        assertEquals(AlertDecision.None, policy.decide(input(), 12 * min)) // timed out after 10 min
        assertTrue(policy.decide(input(), 45_000L + 15 * min).isHeadsUp())
    }

    @Test
    fun becomingUrgentPopsUpAtOnce() {
        val policy = AlertPolicy()
        policy.decide(input(), 0L)
        assertTrue(policy.decide(input(), 45_000L).isHeadsUp())
        assertTrue(policy.decide(input(severity = Severity.URGENT), 2 * min).isHeadsUp())
        assertFalse(policy.decide(input(severity = Severity.URGENT), 3 * min).isHeadsUp())
    }

    @Test
    fun laterSilencesTheTopicForHalfAnHourUnlessItGetsWorse() {
        val policy = AlertPolicy()
        policy.decide(input(), 0L)
        policy.decide(input(), 45_000L)
        policy.snooze(min)
        assertEquals(AlertDecision.None, policy.decide(input(), 20 * min))
        assertTrue(policy.decide(input(), 31 * min + 1).isHeadsUp())

        val worse = AlertPolicy()
        worse.decide(input(), 0L)
        worse.decide(input(), 45_000L)
        worse.snooze(min)
        assertTrue(worse.decide(input(severity = Severity.URGENT), 5 * min).isHeadsUp())
        // Found in E2E: the next update must not fall back into the snooze and take the urgent alert away.
        assertIs<AlertDecision.Show>(worse.decide(input(severity = Severity.URGENT), 5 * min + 500))
    }

    @Test
    fun onScreenOnlyUrgentTopicsPopUp() {
        val policy = AlertPolicy()
        policy.decide(input(visible = true), 0L)
        assertEquals(AlertDecision.None, policy.decide(input(visible = true), min))
        assertTrue(policy.decide(input(visible = true, severity = Severity.URGENT), 2 * min).isHeadsUp())
    }

    @Test
    fun theAlertGoesWithItsTopicAndDoesNotFlapBack() {
        val policy = AlertPolicy()
        policy.decide(input(), 0L)
        assertTrue(policy.decide(input(), 45_000L).isHeadsUp())
        assertEquals(AlertDecision.Hide, policy.decide(input(topic = null), 2 * min))
        policy.decide(input(), 3 * min)
        assertEquals(AlertDecision.None, policy.decide(input(), 4 * min)) // back within 15 min: no new pop-up
        assertTrue(policy.decide(input(), 16 * min).isHeadsUp())
    }

    @Test
    fun anUrgentTopicThatWentAwayAlertsAgainAsSoonAsItIsBack() {
        // E2E: a short false urgent alert (range 0 m) muted the real one 8 minutes later.
        val policy = AlertPolicy()
        val urgent = input(topic = AlertTopic.NEARBY, severity = Severity.URGENT, trip = null)
        assertTrue(policy.decide(urgent, 0L).isHeadsUp())
        assertEquals(AlertDecision.Hide, policy.decide(input(topic = null, trip = null), 2 * min))
        assertTrue(policy.decide(urgent, 8 * min).isHeadsUp())
    }

    @Test
    fun anotherBusyStopOnTheSameTripIsNotSilencedByTheAnsweredOne() {
        val policy = AlertPolicy()
        policy.decide(input(subject = "괴산휴게소"), 0L)
        assertTrue(policy.decide(input(subject = "괴산휴게소"), 45_000L).isHeadsUp())
        policy.acted(2 * min) // the driver added 문경 instead
        assertEquals(AlertDecision.None, policy.decide(input(topic = null), 3 * min))
        policy.decide(input(subject = "문경휴게소"), 12 * min) // 문경 fills up too
        assertTrue(policy.decide(input(subject = "문경휴게소"), 12 * min + 45_000L).isHeadsUp())
    }

    @Test
    fun aNewTripIsANewTopic() {
        val policy = AlertPolicy()
        policy.decide(input(), 0L)
        policy.decide(input(), 45_000L)
        assertEquals(AlertDecision.Hide, policy.decide(input(trip = "대전역@36.332"), min)) // the old trip's alert goes
        assertTrue(policy.decide(input(trip = "대전역@36.332"), min + 45_000L).isHeadsUp())
    }
}
