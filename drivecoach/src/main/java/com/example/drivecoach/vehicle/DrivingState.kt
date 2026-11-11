package com.example.drivecoach.vehicle

import com.example.drivecoach.model.Gear

object DrivingState {

    /**
     * Whether the UI must fall back to the minimal driving view. CarUxRestrictions decides when it is
     * known; otherwise fail safe: anything but P (or a moving car) counts as driving. A simulated
     * demo drive is judged by its own gear and speed so it shows what a real drive would.
     */
    fun restricted(uxDriving: Boolean, uxAvailable: Boolean, v: LiveVehicleState): Boolean = when {
        v.simulated -> v.gear.isDirection || (v.speedMps ?: 0f) > STILL_MPS
        uxAvailable -> uxDriving
        else -> (v.gear != Gear.PARK && v.gear != Gear.UNKNOWN) || (v.speedMps ?: 0f) > STILL_MPS
    }

    /** Parking practice with the real steering wheel needs a parked, unrestricted car. */
    fun canPractice(restricted: Boolean, v: LiveVehicleState): Boolean =
        !restricted && v.isParked && v.availability(Signal.STEERING) == SignalAvailability.AVAILABLE

    private const val STILL_MPS = 3f / 3.6f
}
