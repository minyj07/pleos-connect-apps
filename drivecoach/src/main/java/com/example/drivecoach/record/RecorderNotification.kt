package com.example.drivecoach.record

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.drivecoach.MainActivity
import com.example.drivecoach.R
import com.example.drivecoach.model.TripSummary
import java.util.Locale

/** Quiet notifications only (no heads-up): the ongoing recording status and "report ready". */
object RecorderNotification {

    const val ONGOING_ID = 1001
    private const val REPORT_ID = 1002
    private const val CHANNEL_RECORDING = "recording"
    private const val CHANNEL_REPORTS = "reports"

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(NotificationChannel(CHANNEL_RECORDING, "운전 습관 기록", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CHANNEL_REPORTS, "주행 리포트", NotificationManager.IMPORTANCE_LOW))
    }

    fun ongoing(context: Context, state: RecorderState): Notification =
        NotificationCompat.Builder(context, CHANNEL_RECORDING)
            .setSmallIcon(R.drawable.ic_stat_drive)
            .setContentTitle(if (state is RecorderState.Recording) "주행 기록 중" else "운전 습관 기록 대기 중")
            .setContentText("주행이 끝나면 리포트를 만들어요")
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(openApp(context))
            .build()

    fun updateOngoing(context: Context, state: RecorderState) = notify(context, ONGOING_ID, ongoing(context, state))

    fun tripReady(context: Context, summary: TripSummary) {
        val km = String.format(Locale.KOREA, "%.1f", summary.stats.distanceKm)
        val text = summary.score?.let { "$km km · 부드러움 ${it}점" } ?: "$km km"
        notify(
            context,
            REPORT_ID,
            NotificationCompat.Builder(context, CHANNEL_REPORTS)
                .setSmallIcon(R.drawable.ic_stat_drive)
                .setContentTitle("주행 리포트가 준비됐어요")
                .setContentText(text)
                .setAutoCancel(true)
                .setSilent(true)
                .setContentIntent(openApp(context))
                .build(),
        )
    }

    private fun notify(context: Context, id: Int, n: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        runCatching { NotificationManagerCompat.from(context).notify(id, n) }
    }

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
