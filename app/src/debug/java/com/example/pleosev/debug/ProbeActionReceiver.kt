package com.example.pleosev.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat

/** Notification action of the probe alert: adds the probe waypoint through NaviHelper. */
class ProbeActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        NaviProbe.log("notification action received: ${intent.action}")
        if (intent.action == ProbeNotifications.ACTION_ADD_WAYPOINT) {
            NaviProbe.run(context, "wp1")
            NotificationManagerCompat.from(context).cancel(ProbeNotifications.ID_ALERT)
        }
    }
}
