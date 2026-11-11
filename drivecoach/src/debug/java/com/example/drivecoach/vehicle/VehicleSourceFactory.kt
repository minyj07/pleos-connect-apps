package com.example.drivecoach.vehicle

import android.content.Context
import com.example.drivecoach.sim.SimulationPlayer

/** Debug builds: the real hub plus scripted demo drives for emulators without a speed control. */
object VehicleSourceFactory {
    fun create(context: Context): VehicleEnvironment {
        val hub = VehicleHub(context)
        return VehicleEnvironment(hub, SimulationPlayer(hub))
    }
}
