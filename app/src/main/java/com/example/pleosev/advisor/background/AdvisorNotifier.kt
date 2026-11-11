package com.example.pleosev.advisor.background

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.pleosev.MainActivity
import com.example.pleosev.R
import com.example.pleosev.advisor.domain.NaviActionKind

/**
 * The advisor's notifications: a quiet one while the background watch runs, and heads-up alerts with one
 * station action. Actions that only send a request to the navi go through [AdvisorActionReceiver] (no
 * screen, Phase 0 spike); "find in the navi" has to bring the navi forward and goes through
 * [NaviActionActivity].
 */
class AdvisorNotifier(private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)

    /** False without POST_NOTIFICATIONS (or with the app's notifications turned off). */
    val canNotify: Boolean get() = manager.areNotificationsEnabled()

    fun ensureChannels() {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(NotificationChannel(CHANNEL_WATCH, "충전 도우미 경로 감시", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CHANNEL_ALERT, "충전 추천", NotificationManager.IMPORTANCE_HIGH))
    }

    fun watching(text: String): Notification = NotificationCompat.Builder(context, CHANNEL_WATCH)
        .setSmallIcon(R.drawable.ic_stat_charging)
        .setContentTitle("충전 도우미가 경로를 보고 있어요")
        .setContentText(text)
        .setOngoing(true)
        .setSilent(true)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .setContentIntent(openAdvisor(REQUEST_OPEN_FROM_WATCH))
        .addAction(0, "끄기", broadcast(AdvisorActionReceiver.ACTION_PAUSE_WATCH, REQUEST_PAUSE))
        .build()

    fun updateWatching(text: String) = notify(ID_WATCH, watching(text))

    /** Posts [alert]. [headsUp] pops it up again; otherwise setOnlyAlertOnce keeps an update silent. */
    fun showAlert(alert: AlertContent, headsUp: Boolean, timeoutMs: Long): Boolean {
        val (label, primary) = primaryAction(alert)
        val notification = NotificationCompat.Builder(context, CHANNEL_ALERT)
            .setSmallIcon(R.drawable.ic_stat_charging)
            .setContentTitle(alert.title)
            .setContentText(alert.text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setOnlyAlertOnce(true)
            // No auto-cancel: a tap would remove it without telling AlertPolicy, and the next quiet update would
            // then be a new post that pops up with sound. It goes when its topic does, or by timeout or swipe.
            .setTimeoutAfter(timeoutMs)
            .setContentIntent(openAdvisor(REQUEST_OPEN_FROM_ALERT))
            // Swiping the alert away counts as "later"; the system sends the same intent on timeout (AdvisorMonitor tells them apart).
            .setDeleteIntent(broadcast(AdvisorActionReceiver.ACTION_DISMISSED, REQUEST_DISMISS))
            .addAction(0, label, primary)
            .addAction(0, "다른 곳 보기", openAdvisor(REQUEST_MORE))
            .addAction(0, "나중에", broadcast(AdvisorActionReceiver.ACTION_SNOOZE, REQUEST_SNOOZE))
            .build()
        if (headsUp) manager.cancel(ID_ALERT)
        return notify(ID_ALERT, notification)
    }

    fun cancelAlert() = manager.cancel(ID_ALERT)

    private fun primaryAction(alert: AlertContent): Pair<String, PendingIntent> {
        val station = alert.station
        val kind = alert.action
        if (station == null || kind == null) {
            return "내비 충전소 목록" to broadcast(AdvisorActionReceiver.ACTION_NAVI_CHARGERS, REQUEST_PRIMARY)
        }
        val label = when (kind) {
            NaviActionKind.ROUTE_TO -> "이 충전소로 안내"
            NaviActionKind.ADD_WAYPOINT -> "경유지로 추가"
            NaviActionKind.SEARCH_IN_NAVI -> "내비에서 찾아 추가"
        }
        val intent = if (kind == NaviActionKind.SEARCH_IN_NAVI) {
            val activity = AlertExtras.put(Intent(context, NaviActionActivity::class.java), station, kind)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            PendingIntent.getActivity(context, REQUEST_PRIMARY, activity, FLAGS)
        } else {
            val receiver = Intent(context, AdvisorActionReceiver::class.java).setAction(AdvisorActionReceiver.ACTION_APPLY)
            PendingIntent.getBroadcast(context, REQUEST_PRIMARY, AlertExtras.put(receiver, station, kind), FLAGS)
        }
        return label to intent
    }

    private fun openAdvisor(requestCode: Int): PendingIntent = PendingIntent.getActivity(
        context,
        requestCode,
        Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_ADVISOR, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        FLAGS,
    )

    private fun broadcast(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getBroadcast(context, requestCode, Intent(context, AdvisorActionReceiver::class.java).setAction(action), FLAGS)

    @SuppressLint("MissingPermission") // canNotify covers POST_NOTIFICATIONS
    private fun notify(id: Int, notification: Notification): Boolean {
        if (!canNotify) return false
        return runCatching { manager.notify(id, notification) }.isSuccess
    }

    companion object {
        const val ID_WATCH = 2001
        const val ID_ALERT = 2002
        private const val CHANNEL_WATCH = "advisor_watch"
        private const val CHANNEL_ALERT = "advisor_alert"
        private const val FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        private const val REQUEST_OPEN_FROM_WATCH = 0
        private const val REQUEST_PAUSE = 1
        private const val REQUEST_OPEN_FROM_ALERT = 2
        private const val REQUEST_PRIMARY = 3
        private const val REQUEST_MORE = 4
        private const val REQUEST_SNOOZE = 5
        private const val REQUEST_DISMISS = 6
    }
}
