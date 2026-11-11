package com.example.drivecoach.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Selected gear (AAOS GEAR_SELECTION). */
@Serializable
enum class Gear {
    UNKNOWN, PARK, REVERSE, NEUTRAL, DRIVE;

    val isDirection: Boolean get() = this == DRIVE || this == REVERSE
}

/**
 * One vehicle reading as the analysis sees it.
 *
 * [tMs] is monotonic time (SystemClock.elapsedRealtime based, keeps counting in deep sleep) so gaps
 * caused by suspend are visible. [steer] is the fraction of full steering lock, -1..1, left negative
 * (the Pleos / AAOS PERF_STEERING_ANGLE sign). Short serial names keep the recovery chunks small.
 */
@Serializable
data class Sample(
    @SerialName("t") val tMs: Long,
    @SerialName("v") val speedMps: Float,
    @SerialName("g") val gear: Gear = Gear.UNKNOWN,
    @SerialName("b") val brake: Boolean? = null,
    @SerialName("s") val steer: Float? = null,
    @SerialName("c") val cruise: Boolean = false,
    @SerialName("i") val ignitionOff: Boolean = false,
)

@Serializable
enum class EventType(val label: String, val scoreWeight: Int) {
    HARSH_ACCEL("급가속", 4),
    HARSH_START("급출발", 4),
    HARSH_BRAKE("급감속", 6),
    HARD_STOP("급정지", 6),
    /** Informational: accelerating and then braking within a few seconds (anticipation). */
    ACCEL_THEN_BRAKE("가속 직후 제동", 0),
}

@Serializable
data class DrivingEvent(
    val type: EventType,
    /** Seconds from the trip start. */
    val atS: Float,
    val durationS: Float,
    /** Signed peak acceleration, m/s². */
    val peakMps2: Float,
    val fromKmh: Float,
    val toKmh: Float,
)

/** One second of the trip, for the report chart. */
@Serializable
data class TimelinePoint(
    val tS: Int,
    val kmh: Float,
    val accel: Float,
    /** Share of the second with the brake pressed (0..1), null when the brake signal is unknown. */
    val brake: Float? = null,
    val steer: Float? = null,
)

@Serializable
data class ParkingSession(
    val durationS: Float,
    val distanceM: Float,
    /** D <-> R switches while manoeuvring. */
    val directionChanges: Int,
    val stops: Int,
    /** Largest steering input as % of full lock, null when steering is unknown. */
    val maxSteerPct: Int? = null,
    val endedInReverse: Boolean,
)

/** Speed band upper bounds (km/h) used by [HabitStats.speedBandS]; the last band is open-ended. */
val SPEED_BAND_LIMITS_KMH = listOf(30, 60, 90, 110)

@Serializable
data class HabitStats(
    val distanceKm: Float = 0f,
    val durationS: Float = 0f,
    val movingS: Float = 0f,
    val idleS: Float = 0f,
    val maxKmh: Float = 0f,
    val speedBandS: List<Float> = List(SPEED_BAND_LIMITS_KMH.size + 1) { 0f },
    /** Time spent accelerating without the brake, split by intensity (see Thresholds). */
    val accelGentleS: Float = 0f,
    val accelModerateS: Float = 0f,
    val accelHardS: Float = 0f,
    val brakeKnown: Boolean = false,
    /** Brake applications that started above walking speed (red-light holds are not habits). */
    val brakePresses: Int = 0,
    val brakePressS: Float = 0f,
    /** Specific kinetic energy (m²/s², i.e. J/kg) removed with the brake pedal vs. without it (regen / coasting). */
    val decelBrakeJ: Float = 0f,
    val decelCoastJ: Float = 0f,
    val cruiseS: Float = 0f,
) {
    val avgMovingKmh: Float get() = if (movingS > 1f) distanceKm / (movingS / 3600f) else 0f

    /** Share of braking energy handled without the brake pedal, null if unknown or too little braking. */
    val coastShare: Float?
        get() {
            val total = decelBrakeJ + decelCoastJ
            return if (!brakeKnown || total < MIN_DECEL_J) null else decelCoastJ / total
        }

    val avgBrakePressS: Float? get() = if (brakePresses > 0) brakePressS / brakePresses else null

    operator fun plus(o: HabitStats) = HabitStats(
        distanceKm = distanceKm + o.distanceKm,
        durationS = durationS + o.durationS,
        movingS = movingS + o.movingS,
        idleS = idleS + o.idleS,
        maxKmh = maxOf(maxKmh, o.maxKmh),
        speedBandS = speedBandS.zip(o.speedBandS) { a, b -> a + b },
        accelGentleS = accelGentleS + o.accelGentleS,
        accelModerateS = accelModerateS + o.accelModerateS,
        accelHardS = accelHardS + o.accelHardS,
        brakeKnown = brakeKnown || o.brakeKnown,
        brakePresses = brakePresses + o.brakePresses,
        brakePressS = brakePressS + o.brakePressS,
        decelBrakeJ = decelBrakeJ + o.decelBrakeJ,
        decelCoastJ = decelCoastJ + o.decelCoastJ,
        cruiseS = cruiseS + o.cruiseS,
    )

    private companion object {
        /** About one stop from 30 km/h; below that the split says nothing. */
        const val MIN_DECEL_J = 35f
    }
}

@Serializable
enum class TripKind {
    /** A real drive (≥ 300 m): counts towards habits and scores. */
    DRIVE,

    /** Re-parking / short move: only its parking session is used. */
    MANEUVER,
}

@Serializable
enum class EndReason { PARKED, IGNITION_OFF, LONG_STOP, SIGNAL_GAP, RECOVERED }

@Serializable
data class TripSummary(
    val id: String,
    val startEpochMs: Long,
    val endEpochMs: Long,
    val kind: TripKind,
    val stats: HabitStats,
    val events: Map<EventType, Int> = emptyMap(),
    /** "부드러움 점수" 0..100, null for manoeuvres. */
    val score: Int? = null,
    val parking: ParkingSession? = null,
    val odoStartKm: Float? = null,
    val odoEndKm: Float? = null,
    val endReason: EndReason,
) {
    fun count(type: EventType): Int = events[type] ?: 0
}

@Serializable
data class TripRecord(
    val summary: TripSummary,
    val events: List<DrivingEvent>,
    val timeline: List<TimelinePoint>,
)
