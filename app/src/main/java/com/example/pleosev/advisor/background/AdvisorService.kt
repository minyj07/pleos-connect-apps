package com.example.pleosev.advisor.background

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import com.example.pleosev.appGraph

/**
 * Background watch: a `specialUse` foreground service whose only job is to keep the process running while
 * the navi covers the app; the process-wide ChargingAdvisor and AdvisorMonitor do the work. Without it the
 * process is frozen 1–2 minutes after the app leaves the screen (Phase 0 spike). AdvisorMonitor starts it
 * while the app is on screen (Android 12+ does not allow it from the background) and stops it when there
 * is nothing left to watch.
 */
class AdvisorService : Service() {

    override fun onCreate() {
        super.onCreate()
        val monitor = appGraph.monitor
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        try {
            ServiceCompat.startForeground(this, AdvisorNotifier.ID_WATCH, monitor.watchNotification(), type)
        } catch (e: Exception) {
            Log.w(TAG, "cannot run in the foreground: ${e.message}")
            stopSelf()
            return
        }
        monitor.onServiceStarted()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    override fun onDestroy() {
        appGraph.monitor.onServiceStopped()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private companion object {
        const val TAG = "AdvisorService"
    }
}
