package com.example.drivecoach.vehicle

import android.content.Context

/** Release builds: real vehicle signals only (the store forbids dummy data in submitted apps). */
object VehicleSourceFactory {
    fun create(context: Context): VehicleEnvironment = VehicleEnvironment(VehicleHub(context), demo = null)
}
