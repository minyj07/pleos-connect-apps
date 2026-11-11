package com.example.drivecoach.record

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.drivecoach.DriveCoachApp
import com.example.drivecoach.data.Settings

/** Starts the recorder only when the user consented and left recording on. */
object RecorderController {

    fun sync(context: Context, v: Settings.Values) {
        if (v.consented && v.recording) start(context) else stop(context)
    }

    fun start(context: Context) {
        runCatching { ContextCompat.startForegroundService(context, Intent(context, DriveRecorderService::class.java)) }
            .onFailure { Log.w(TAG, "cannot start recorder: ${it.message}") }
    }

    fun stop(context: Context) {
        context.stopService(Intent(context, DriveRecorderService::class.java))
    }

    private const val TAG = "RecorderController"
}

/** Cold boot: resume recording without opening the app (only with consent and the option on). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val v = (context.applicationContext as DriveCoachApp).settings.values.value
        if (v.consented && v.recording && v.autoStartOnBoot) RecorderController.start(context)
    }
}
