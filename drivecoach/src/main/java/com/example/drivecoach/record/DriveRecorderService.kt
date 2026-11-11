package com.example.drivecoach.record

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.ServiceCompat
import com.example.drivecoach.DriveCoachApp
import com.example.drivecoach.model.TripKind
import com.example.drivecoach.model.TripSummary
import com.example.drivecoach.vehicle.VehicleSource

/**
 * Foreground service that records trips while other apps (navigation, media) are in front.
 * Started only after the user consented; stops as soon as consent is withdrawn.
 */
class DriveRecorderService : Service(), TripRecorder.Listener {

    private lateinit var app: DriveCoachApp
    private lateinit var source: VehicleSource
    private lateinit var recorder: TripRecorder

    private val ticker = object : Runnable {
        override fun run() {
            recorder.tick()
            source.handler.postDelayed(this, TICK_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        app = application as DriveCoachApp
        RecorderNotification.ensureChannels(this)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, RecorderNotification.ONGOING_ID, RecorderNotification.ongoing(this, RecorderState.Waiting), type)

        source = app.vehicle.source
        recorder = TripRecorder(app.tripStore, app.io, SystemClockSource, this)
        recorder.recoverPending()
        source.addSampleListener(recorder)
        source.acquire()
        source.handler.postDelayed(ticker, TICK_MS)
        app.recorderState.value = RecorderState.Waiting
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        source.handler.removeCallbacks(ticker)
        source.removeSampleListener(recorder)
        // A consent withdrawal stops the service and wipes the store; flushing now would write the buffered
        // samples again after that wipe, so they are dropped instead.
        source.handler.post { if (app.settings.values.value.consented) recorder.close() }
        source.release()
        app.recorderState.value = RecorderState.Off
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onRecordingChanged(state: RecorderState) {
        app.recorderState.value = state
        RecorderNotification.updateOngoing(this, state)
    }

    override fun onTripSaved(summary: TripSummary) {
        app.tripStore.prune(System.currentTimeMillis(), app.settings.values.value.retentionDays)
        if (summary.kind == TripKind.DRIVE) RecorderNotification.tripReady(this, summary)
    }

    private object SystemClockSource : TripRecorder.Clock {
        override fun wallMs() = System.currentTimeMillis()
        override fun monoMs() = SystemClock.elapsedRealtime()
    }

    private companion object {
        const val TICK_MS = 30_000L
    }
}
