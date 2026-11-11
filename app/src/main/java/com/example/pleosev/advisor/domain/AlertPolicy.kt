package com.example.pleosev.advisor.domain

/** What a heads-up notification is about. */
enum class AlertTopic { ON_ROUTE, PLANNED_STOP, NEARBY }

data class AlertInput(
    val topic: AlertTopic?,     // null: nothing worth interrupting the driver for
    val severity: Severity,
    val tripKey: String?,       // the destination: a re-route keeps it, a new trip changes it
    val resultsReady: Boolean,  // the cards (or a failure) for the current need have arrived
    val appVisible: Boolean,    // the app is on screen and shows the same thing
    val subject: String? = null, // what the topic is about when one trip has several (the route's charging stop)
)

sealed interface AlertDecision {
    data object None : AlertDecision

    data object Hide : AlertDecision

    /** Post the alert. [headsUp] false updates the alert already shown, without sound or pop-up. */
    data class Show(val headsUp: Boolean, val timeoutMs: Long) : AlertDecision
}

/**
 * When the advisor may interrupt the driver with a heads-up notification:
 *  - a topic must hold for [settleMs] first; urgent ones alert at once
 *  - a topic alerts again only after [repeatAfterMs], or at once when it became more severe
 *  - "later" (or swiping the alert away) silences the topic for [snoozeMs], unless it becomes more severe
 *  - while the app is on screen only urgent topics alert
 *  - an alert lasts [shownForMs] (notification timeout) and is removed as soon as its topic is gone
 */
class AlertPolicy(
    private val settleMs: Long = 45_000L,
    private val repeatAfterMs: Long = 15 * 60_000L,
    private val snoozeMs: Long = 30 * 60_000L,
    private val shownForMs: Long = 10 * 60_000L,
) {
    private data class Alerted(val atMs: Long, val severity: Severity)

    private val history = HashMap<String, Alerted>()
    private var pendingKey: String? = null
    private var pendingSinceMs = 0L
    private var shownKey: String? = null
    private var shownAtMs = 0L
    private var snoozedKey: String? = null
    private var snoozedUntilMs = 0L

    fun decide(input: AlertInput, nowMs: Long): AlertDecision {
        if (shownKey != null && nowMs - shownAtMs >= shownForMs) shownKey = null // timed out
        // The subject keeps a second busy stop on the same trip apart from the first one the driver already answered.
        val key = input.topic?.takeIf { input.severity >= Severity.RECOMMENDED }?.let { "$it|${input.tripKey}|${input.subject}" }
        if (key != pendingKey) {
            // An urgent topic that went away alerts again as soon as it is back: the repeat interval is for a topic
            // that stays (E2E: a short false urgent alert muted the real one 8 minutes later).
            pendingKey?.let { old -> if (history[old]?.severity == Severity.URGENT) history.remove(old) }
            pendingKey = key
            pendingSinceMs = nowMs
        }
        if (key == null) return hide()
        val urgent = input.severity == Severity.URGENT
        val showingThis = shownKey == key
        val settled = input.resultsReady && (urgent || nowMs - pendingSinceMs >= settleMs)
        if (!settled) return if (showingThis) AlertDecision.None else hide()

        val last = history[key]
        val escalated = last != null && input.severity > last.severity
        val snoozed = key == snoozedKey && nowMs < snoozedUntilMs && !escalated
        val due = last == null || escalated || (!showingThis && nowMs - last.atMs >= repeatAfterMs)
        return when {
            snoozed -> hide()
            input.appVisible && !urgent -> if (showingThis) update(nowMs) else hide()
            due -> {
                if (key == snoozedKey) snoozedKey = null // only a worse picture gets here: it ends the snooze
                history[key] = Alerted(nowMs, input.severity)
                shownKey = key
                shownAtMs = nowMs
                AlertDecision.Show(headsUp = true, timeoutMs = shownForMs)
            }
            showingThis -> update(nowMs)
            else -> AlertDecision.None
        }
    }

    /** "Later", or the driver swiped the alert away. */
    fun snooze(nowMs: Long) {
        snoozedKey = shownKey ?: pendingKey
        snoozedUntilMs = nowMs + snoozeMs
        shownKey = null
    }

    /** The driver acted on the alert and the navi takes it from there: the topic counts as answered. */
    fun acted(nowMs: Long) = snooze(nowMs)

    private fun hide(): AlertDecision {
        if (shownKey == null) return AlertDecision.None
        shownKey = null
        return AlertDecision.Hide
    }

    private fun update(nowMs: Long) = AlertDecision.Show(headsUp = false, timeoutMs = shownForMs - (nowMs - shownAtMs))
}
