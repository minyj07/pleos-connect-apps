package com.example.pleosev.advisor

import android.app.Application
import android.app.Notification
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.pleosev.BuildConfig
import com.example.pleosev.advisor.background.AdvisorNotifier
import com.example.pleosev.advisor.background.AdvisorService
import com.example.pleosev.advisor.background.AlertContent
import com.example.pleosev.advisor.background.AlertTexts
import com.example.pleosev.advisor.domain.AlertDecision
import com.example.pleosev.advisor.domain.AlertInput
import com.example.pleosev.advisor.domain.AlertPolicy
import com.example.pleosev.advisor.domain.AlertTopic
import com.example.pleosev.advisor.domain.NaviActionKind
import com.example.pleosev.advisor.domain.Severity
import com.example.pleosev.advisor.domain.WatchInput
import com.example.pleosev.advisor.domain.WatchPolicy
import com.example.pleosev.data.settings.AdvisorPrefs
import com.example.pleosev.model.EvDashboardState
import com.example.pleosev.navi.NaviState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Keeps the advisor working while the navi covers the app (Phase 3):
 *  - runs the background watch ([AdvisorService]) while [WatchPolicy] says so. A foreground service may
 *    only be started while the app is on screen, so it starts then and keeps running once the app is hidden
 *  - turns the advisor state into heads-up notifications ([AlertPolicy], [AdvisorNotifier])
 *  - carries out notification buttons against the current route
 * Main thread only (the graph scope, receivers and the service all run there).
 */
class AdvisorMonitor(
    private val app: Application,
    private val scope: CoroutineScope,
    private val advisor: ChargingAdvisor,
    private val vehicle: StateFlow<EvDashboardState>,
    private val navi: StateFlow<NaviState>,
    private val prefs: Flow<AdvisorPrefs>,
    private val visible: StateFlow<Boolean>,
    private val notifier: AdvisorNotifier,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private data class Snapshot(
        val advisor: AdvisorState,
        val vehicle: EvDashboardState,
        val navi: NaviState,
        val prefs: AdvisorPrefs,
        val visible: Boolean,
    )

    private enum class ServiceState { STOPPED, STARTING, RUNNING }

    private val watchPolicy = WatchPolicy()
    private val alertPolicy = AlertPolicy()
    private var latest: Snapshot? = null
    private var service = ServiceState.STOPPED
    private var pausedByDriver = false
    private var shownAlert: AlertContent? = null
    private var alertEndsAtMs = 0L     // when the system removes the shown alert (setTimeoutAfter)
    private var watchText: String? = null
    private var lastTrace: String? = null // debug builds: alert inputs, logged when they change

    fun start() {
        notifier.ensureChannels()
        scope.launch {
            combine(advisor.state, vehicle, navi, prefs, visible) { a, v, n, p, vis -> Snapshot(a, v, n, p, vis) }
                .collect { onSnapshot(it) }
        }
        // Time alone moves the policies on: settling, repeats, the idle grace.
        scope.launch {
            while (isActive) {
                delay(TICK_MS)
                latest?.let(::onSnapshot)
            }
        }
    }

    fun onServiceStarted() {
        service = ServiceState.RUNNING
        latest?.let(::onSnapshot)
    }

    fun onServiceStopped() {
        service = ServiceState.STOPPED
        watchText = null
    }

    fun watchNotification(): Notification {
        val text = latest?.let { AlertTexts.watching(it.advisor, it.navi.guiding) } ?: DEFAULT_WATCH_TEXT
        watchText = text
        return notifier.watching(text)
    }

    /**
     * "Find in the navi", "add as waypoint" or "navigate there" from an alert. Only a request the navi took
     * answers the alert; otherwise it stays, so the driver can try again.
     */
    suspend fun runAlertAction(station: StationRef, kind: NaviActionKind) {
        if (advisor.applyFromAlert(station, kind)) answered()
    }

    fun showNaviChargers() {
        if (advisor.showNaviChargers()) answered()
    }

    /** "Later". */
    fun snoozeAlert() {
        alertPolicy.snooze(clock())
        notifier.cancelAlert()
        shownAlert = null
    }

    /**
     * The alert left the notification list: swiped away (same as "later"), or its display time ran out. The
     * system sends the same delete intent for both; a timeout is not an answer and must not snooze the topic.
     */
    fun alertDismissed() {
        if (clock() >= alertEndsAtMs - TIMEOUT_SLACK_MS) shownAlert = null else snoozeAlert()
    }

    /** "Off" on the watch notification: no background watch until the app is opened again. */
    fun pauseWatch() {
        pausedByDriver = true
        latest?.let(::onSnapshot)
    }

    /** Debug summary without positions. */
    fun describe(): String =
        "service=$service paused=$pausedByDriver visible=${latest?.visible} notifications=${notifier.canNotify} alert='${shownAlert?.title}'"

    private fun answered() {
        alertPolicy.acted(clock())
        notifier.cancelAlert()
        shownAlert = null
    }

    private fun onSnapshot(s: Snapshot) {
        if (s.visible && latest?.visible != true) pausedByDriver = false // opening the app ends a pause
        latest = s
        val now = clock()
        syncService(s, now)
        // The navi plans the route again after a waypoint change: the trip key is gone for a few seconds, which must
        // not take a shown alert away or start a new topic.
        if (!s.navi.replanning) syncAlert(s, now)
    }

    private fun syncService(s: Snapshot, now: Long) {
        val watch = watchPolicy.shouldWatch(
            WatchInput(
                enabled = s.prefs.locationConsent && s.prefs.backgroundWatch && !pausedByDriver,
                charging = s.vehicle.isCharging || s.vehicle.chargePortConnected == true,
                busy = s.navi.guiding || s.advisor.need.active || s.visible,
                guiding = s.navi.guiding,
            ),
            now,
        )
        when {
            watch && service == ServiceState.STOPPED && s.visible -> startService()
            // A starting service is stopped once it is in the foreground (onServiceStarted).
            !watch && service == ServiceState.RUNNING -> stopService()
            service == ServiceState.RUNNING -> {
                val text = AlertTexts.watching(s.advisor, s.navi.guiding)
                if (text != watchText) {
                    watchText = text
                    notifier.updateWatching(text)
                }
            }
        }
    }

    private fun syncAlert(s: Snapshot, now: Long) {
        // Notifications off: the policy must not count anything as alerted, or a topic still open when the driver
        // allows them would wait out its repeat interval before the first heads-up.
        if (!notifier.canNotify) {
            shownAlert = null
            return
        }
        val alert = if (s.prefs.locationConsent) AlertTexts.alert(s.advisor) else null
        val decision = alertPolicy.decide(
            AlertInput(
                topic = alert?.topic,
                severity = alert?.severity ?: Severity.NONE,
                // The trip by its destination, as ChargingAdvisor's route key.
                tripKey = s.navi.destination?.takeIf { s.navi.guiding }
                    ?.let { d -> "${d.name}@${d.location?.let { "${round3(it.lat)},${round3(it.lon)}" }}" },
                resultsReady = resultsReady(s.advisor),
                appVisible = s.visible,
                subject = s.advisor.plannedStopName.takeIf { alert?.topic == AlertTopic.PLANNED_STOP },
            ),
            now,
        )
        if (BuildConfig.DEBUG) {
            val trace = "topic=${alert?.topic} severity=${alert?.severity} ready=${resultsReady(s.advisor)} visible=${s.visible} " +
                "trip#${s.navi.destination?.name.hashCode()} -> ${decision.javaClass.simpleName}"
            if (trace != lastTrace) Log.d(TAG, "alert input $trace")
            lastTrace = trace
        }
        when (decision) {
            AlertDecision.None -> Unit
            AlertDecision.Hide -> {
                notifier.cancelAlert()
                shownAlert = null
            }
            is AlertDecision.Show -> if (alert != null && (decision.headsUp || alert != shownAlert)) {
                val posted = notifier.showAlert(alert, decision.headsUp, decision.timeoutMs)
                if (BuildConfig.DEBUG) Log.d(TAG, "alert ${alert.topic} headsUp=${decision.headsUp} posted=$posted '${alert.title}'")
                shownAlert = alert
                alertEndsAtMs = now + decision.timeoutMs
            }
        }
    }

    /** The cards (or a failure) for the current need are in: an alert never shows a half-finished picture. */
    private fun resultsReady(a: AdvisorState): Boolean {
        if (a.loading) return false
        val updated = a.updatedAtMs
        return (updated != null && updated >= a.need.sinceMs) || a.problem != null
    }

    private fun startService() {
        service = ServiceState.STARTING
        runCatching { ContextCompat.startForegroundService(app, Intent(app, AdvisorService::class.java)) }
            .onSuccess { Log.i(TAG, "background watch starting") }
            .onFailure {
                service = ServiceState.STOPPED
                Log.w(TAG, "cannot start the background watch: ${it.message}")
            }
    }

    private fun stopService() {
        Log.i(TAG, "background watch stopping")
        app.stopService(Intent(app, AdvisorService::class.java))
    }

    private fun round3(v: Double) = Math.round(v * 1000) / 1000.0

    private companion object {
        const val TAG = "AdvisorMonitor"
        const val TICK_MS = 15_000L
        const val TIMEOUT_SLACK_MS = 5_000L
        const val DEFAULT_WATCH_TEXT = "충전이 필요하면 알려 드릴게요"
    }
}
