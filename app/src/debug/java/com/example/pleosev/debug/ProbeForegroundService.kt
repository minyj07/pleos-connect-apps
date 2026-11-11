package com.example.pleosev.debug

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat

/** Phase 0 spike: does Pleos let a non-media app keep a `specialUse` foreground service running? */
class ProbeForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        try {
            ServiceCompat.startForeground(this, ProbeNotifications.ID_MONITOR, ProbeNotifications.monitorNotification(this), type)
            NaviProbe.log("FGS started (specialUse)")
        } catch (e: Exception) {
            NaviProbe.log("FGS start failed: ${e.javaClass.name}: ${e.message}")
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        NaviProbe.log("FGS destroyed")
        super.onDestroy()
    }
}
