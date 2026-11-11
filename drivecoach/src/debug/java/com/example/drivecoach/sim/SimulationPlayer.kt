package com.example.drivecoach.sim

import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import com.example.drivecoach.model.Gear
import com.example.drivecoach.vehicle.DemoControls
import com.example.drivecoach.vehicle.Ignition
import com.example.drivecoach.vehicle.LiveVehicleState
import com.example.drivecoach.vehicle.Signal
import com.example.drivecoach.vehicle.SignalAvailability
import com.example.drivecoach.vehicle.VehicleHub
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Plays a [ScriptedDrives] script through the [VehicleHub] at 1× or faster. Sample timestamps advance
 * by the script's 100 ms per frame, so analysis results don't depend on the playback speed.
 */
class SimulationPlayer(private val hub: VehicleHub) : DemoControls {

    private val thread = HandlerThread("demo-drive").apply { start() }
    private val handler = Handler(thread.looper)
    private val _playing = MutableStateFlow<String?>(null)
    override val playing: StateFlow<String?> = _playing.asStateFlow()
    override val scripts: List<String> = ScriptedDrives.names

    private var frames: List<SimFrame> = emptyList()
    private var index = 0
    private var baseMs = 0L
    private var periodMs = 100L

    private val step = object : Runnable {
        override fun run() {
            if (index >= frames.size) {
                finish()
                return
            }
            val f = frames[index]
            hub.simulate(f.toState(baseMs + index * FRAME_MS))
            index++
            handler.postDelayed(this, periodMs)
        }
    }

    override fun play(script: String, speedup: Int) {
        handler.post {
            handler.removeCallbacks(step)
            frames = ScriptedDrives.frames(script)
            index = 0
            baseMs = SystemClock.elapsedRealtime()
            periodMs = (FRAME_MS / speedup.coerceIn(1, 50)).coerceAtLeast(2)
            _playing.value = script
            hub.beginSimulation()
            handler.post(step)
        }
    }

    override fun stop() {
        handler.post {
            handler.removeCallbacks(step)
            if (_playing.value != null) finish()
        }
    }

    private fun finish() {
        _playing.value = null
        hub.endSimulation()
    }

    private fun SimFrame.toState(tMs: Long) = LiveVehicleState(
        tMs = tMs,
        speedMps = kmh / 3.6f,
        gear = gear,
        brake = brake,
        steerRaw = steer * LiveVehicleState.DEFAULT_FRONT_WHEEL_LOCK_DEG,
        steerMaxRaw = LiveVehicleState.DEFAULT_FRONT_WHEEL_LOCK_DEG,
        parkingBrake = gear == Gear.PARK,
        ignition = if (ignitionOff) Ignition.OFF else Ignition.ON,
        cruiseActive = cruise,
        availability = Signal.entries.associateWith { SignalAvailability.AVAILABLE },
        simulated = true,
    )

    private companion object {
        const val FRAME_MS = 100L
    }
}
