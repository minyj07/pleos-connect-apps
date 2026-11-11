package com.example.pleosev.debug

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * Phase 0 spike: can a non-navigation app show a driving-related heads-up notification on Pleos,
 * and can its action reach NaviHelper (add a waypoint) without opening the app?
 */
object ProbeNotifications {
    const val ACTION_ADD_WAYPOINT = "com.example.pleosev.debug.ADD_WAYPOINT"
    const val ID_ALERT = 4201
    const val ID_MONITOR = 4202

    private const val CHANNEL_ALERT = "probe_charge_alert"
    private const val CHANNEL_MONITOR = "probe_monitor"

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL_ALERT, "충전 추천 (프로브)", NotificationManager.IMPORTANCE_HIGH))
        nm.createNotificationChannel(NotificationChannel(CHANNEL_MONITOR, "충전 도우미 감시 (프로브)", NotificationManager.IMPORTANCE_LOW))
    }

    fun postAlert(context: Context, navigationCategory: Boolean) {
        ensureChannels(context)
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) {
            NaviProbe.log("notifications disabled -> adb shell pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS")
            return
        }
        val addWaypoint = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, ProbeActionReceiver::class.java).setAction(ACTION_ADD_WAYPOINT),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val category = if (navigationCategory) NotificationCompat.CATEGORY_NAVIGATION else NotificationCompat.CATEGORY_RECOMMENDATION
        val notification = NotificationCompat.Builder(context, CHANNEL_ALERT)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("충전이 필요해요 (프로브)")
            .setContentText("천안 충전소 · 급속 대기 2/4 · +4분")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(category)
            .setAutoCancel(true)
            .addAction(0, "경유지로 추가", addWaypoint)
            .build()
        try {
            manager.notify(ID_ALERT, notification)
            NaviProbe.log("posted alert notification (category=$category)")
        } catch (e: SecurityException) {
            NaviProbe.log("notify failed: ${e.message}")
        }
    }

    fun monitorNotification(context: Context): Notification {
        ensureChannels(context)
        return NotificationCompat.Builder(context, CHANNEL_MONITOR)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("충전 도우미가 경로를 보고 있어요 (프로브)")
            .setOngoing(true)
            .build()
    }
}
