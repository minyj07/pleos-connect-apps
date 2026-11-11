package com.example.pleosev.advisor.background

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.pleosev.appGraph
import kotlinx.coroutines.launch

/** Notification buttons that need no screen: the navi takes these requests in the background (Phase 0 spike). */
class AdvisorActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val graph = context.appGraph
        when (intent.action) {
            ACTION_APPLY -> {
                val station = AlertExtras.station(intent) ?: return
                val kind = AlertExtras.kind(intent) ?: return
                val pending = goAsync()
                graph.scope.launch {
                    try {
                        graph.monitor.runAlertAction(station, kind)
                    } finally {
                        pending.finish()
                    }
                }
            }
            ACTION_NAVI_CHARGERS -> graph.monitor.showNaviChargers()
            ACTION_SNOOZE -> graph.monitor.snoozeAlert()
            ACTION_DISMISSED -> graph.monitor.alertDismissed()
            ACTION_PAUSE_WATCH -> graph.monitor.pauseWatch()
        }
    }

    companion object {
        const val ACTION_APPLY = "com.example.pleosev.advisor.APPLY"
        const val ACTION_NAVI_CHARGERS = "com.example.pleosev.advisor.NAVI_CHARGERS"
        const val ACTION_SNOOZE = "com.example.pleosev.advisor.SNOOZE"
        const val ACTION_DISMISSED = "com.example.pleosev.advisor.DISMISSED"
        const val ACTION_PAUSE_WATCH = "com.example.pleosev.advisor.PAUSE_WATCH"
    }
}
