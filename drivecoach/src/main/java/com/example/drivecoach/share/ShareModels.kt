package com.example.drivecoach.share

import kotlinx.serialization.Serializable

/**
 * What "폰으로 보기" sends (sealed with [ShareCrypto]): a report-shaped copy, separate from the storage
 * models so the phone viewer has a stable format. Leaves out what the phone report does not need:
 * odometer, per-second steering, trip ids, vehicle name. Event types are [com.example.drivecoach.model.EventType] names.
 */
@Serializable
data class SharePayload(
    val v: Int = VERSION,
    val kind: ShareKind,
    val createdAtEpochMs: Long,
    /** Minutes east of UTC on the car, so the phone shows the same clock times as the car. */
    val utcOffsetMin: Int,
    val trip: TripShare? = null,
    val period: PeriodShare? = null,
) {
    companion object {
        const val VERSION = 1
    }
}

@Serializable
enum class ShareKind { TRIP, PERIOD }

@Serializable
data class TripShare(
    val startEpochMs: Long,
    val endEpochMs: Long,
    /** false for a parking / short move (only its parking session matters). */
    val drive: Boolean,
    val distanceKm: Float,
    val durationS: Int,
    val avgKmh: Int,
    val maxKmh: Int,
    val score: Int?,
    val events: Map<String, Int>,
    val moments: List<MomentShare>,
    val accel: MixShare,
    /** null when the car gave no brake signal. */
    val brake: BrakeShare?,
    val parking: ParkingShare?,
    val timeline: TimelineShare?,
    val tips: List<TipShare>,
)

@Serializable
data class MomentShare(val type: String, val atS: Int, val fromKmh: Int, val toKmh: Int, val peakKmhPerS: Float)

/** Seconds spent accelerating without the brake, by intensity (0.1 s, so the phone shows the car's percentages). */
@Serializable
data class MixShare(val gentleS: Float, val moderateS: Float, val hardS: Float)

@Serializable
data class BrakeShare(val presses: Int, val per10Km: Float?, val avgPressS: Float?, val coastShare: Float?)

@Serializable
data class ParkingShare(val durationS: Int, val directionChanges: Int, val stops: Int, val maxSteerPct: Int?, val endedInReverse: Boolean)

/** Parallel arrays, one entry per [stepS] seconds (at most [SharePayloads.MAX_TIMELINE_POINTS]); brake = 1 if pressed. */
@Serializable
data class TimelineShare(val stepS: Int, val tS: List<Int>, val kmh: List<Int>, val brake: List<Int>?)

@Serializable
data class TipShare(val title: String, val body: String, val praise: Boolean)

@Serializable
data class PeriodShare(
    val days: Int,
    val trips: Int,
    val distanceKm: Float,
    val durationS: Int,
    val score: Int?,
    val events: Map<String, Int>,
    val per10Km: Map<String, Float>,
    val accel: MixShare,
    val brake: BrakeShare?,
    /** Oldest first, as on the car's habits screen. */
    val weeks: List<WeekShare>,
    val parking: ParkingHabitShare?,
    /** Newest first. */
    val drives: List<DriveRow>,
    val tips: List<TipShare>,
)

@Serializable
data class WeekShare(val startEpochMs: Long, val distanceKm: Float, val harshPer10Km: Float?, val score: Int?)

@Serializable
data class ParkingHabitShare(val sessions: Int, val avgDurationS: Int, val avgDirectionChanges: Float, val reverseShare: Float)

@Serializable
data class DriveRow(val startEpochMs: Long, val distanceKm: Float, val durationS: Int, val score: Int?, val harsh: Int)
