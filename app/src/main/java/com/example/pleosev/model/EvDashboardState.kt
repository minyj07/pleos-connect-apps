package com.example.pleosev.model

/** Where the numbers on screen come from. */
enum class DataSource { PLEOS_SDK, DEMO }

/** Connection to the Pleos Vehicle Service. */
enum class ConnectionState { CONNECTING, CONNECTED, FAILED }

/** AAOS VehiclePropertyIds.EV_CHARGE_STATE mapped to app-level values. */
enum class ChargeState { UNKNOWN, CHARGING, FULLY_CHARGED, NOT_CHARGING, ERROR }

/**
 * Single immutable snapshot rendered by the dashboard.
 * Every vehicle value is nullable: `null` means "not received yet / not supported".
 *
 * Data sources (see docs/PLEOS_SDK_GUIDE.md, "SDK API 정책: AAOS Alignment"):
 *  - Pleos Vehicle SDK  : Pleos-specific data (SOC %, EV efficiency, trip statistics, driving mode)
 *  - AAOS CarPropertyManager : standard vehicle properties (range, battery Wh, charge port/state ...)
 */
data class EvDashboardState(
    val connection: ConnectionState = ConnectionState.CONNECTING,
    val source: DataSource = DataSource.PLEOS_SDK,
    val errorMessage: String? = null,

    // --- Pleos Vehicle SDK: EvBattery (pleos.car.permission.CAR_ENERGY)
    val socPercent: Int? = null,              // getEvBatteryStateOfCharge, %
    val chargeSwitchOn: Boolean? = null,      // getEvChargeSwitchState
    val currentEfficiency: Float? = null,     // getCurrentEvEfficiency, km/kWh
    val sinceChargeEfficiency: Float? = null, // getSinceChargeEvEfficiency, km/kWh

    // --- Pleos Vehicle SDK: Odometer (pleos.car.permission.CAR_DRIVE)
    val odometerKm: Float? = null,            // getCurrentDistanceDriven, km
    val tripDistanceKm: Float? = null,        // getTripCurrentDrivingDistance (m) / 1000
    val tripDrivingTimeMin: Int? = null,      // getTripCurrentDrivingTime, minutes

    // --- Pleos Vehicle SDK: DrivingMode (pleos.car.permission.CAR_DRIVE)
    val drivingMode: String? = null,          // getCurrentDrivingModeState -> NORMAL / ECO / SPORT

    // --- AAOS CarPropertyManager (android.car.permission.CAR_ENERGY / CAR_ENERGY_PORTS / CAR_INFO)
    val rangeRemainingKm: Float? = null,      // RANGE_REMAINING (m) / 1000
    val batteryLevelKwh: Float? = null,       // EV_BATTERY_LEVEL (Wh) / 1000
    val batteryCapacityKwh: Float? = null,    // INFO_EV_BATTERY_CAPACITY (Wh) / 1000
    val chargePortOpen: Boolean? = null,      // EV_CHARGE_PORT_OPEN
    val chargePortConnected: Boolean? = null, // EV_CHARGE_PORT_CONNECTED
    val chargeRateKw: Float? = null,          // EV_BATTERY_INSTANTANEOUS_CHARGE_RATE (mW) / 1e6
    val chargeState: ChargeState? = null,     // EV_CHARGE_STATE
    val carPropertiesAvailable: Boolean = false,

    // --- Platform (CarUxRestrictions)
    val isDriving: Boolean = false,

    val lastUpdatedMillis: Long = 0L,
) {
    /**
     * Only with the connector plugged in, when the car reports it: a charge state left over from the last
     * charge must not hold back charging advice (the emulator reports FULLY_CHARGED from boot at any SOC).
     */
    val isCharging: Boolean
        get() = chargePortConnected != false && (
            chargeState == ChargeState.CHARGING ||
                (chargeState == null && (chargeSwitchOn == true || (chargeRateKw ?: 0f) > 0f))
            )
}
