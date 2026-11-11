package com.example.drivecoach.share

import com.example.drivecoach.analysis.CoachingTips
import com.example.drivecoach.analysis.PeriodSummary
import com.example.drivecoach.analysis.Tip
import com.example.drivecoach.model.EventType
import com.example.drivecoach.model.HabitStats
import com.example.drivecoach.model.ParkingSession
import com.example.drivecoach.model.TimelinePoint
import com.example.drivecoach.model.TripKind
import com.example.drivecoach.model.TripRecord
import com.example.drivecoach.model.TripSummary
import kotlinx.serialization.json.Json
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

/** Builds the [SharePayload] for one trip or a period from what the reports already show (pure). */
object SharePayloads {

    /** Enough for a phone-width chart; a coarser speed trace also says less about the route. */
    const val MAX_TIMELINE_POINTS = 300
    const val MAX_DRIVE_ROWS = 200

    private val json = Json { encodeDefaults = true; explicitNulls = false }

    fun encode(payload: SharePayload): ByteArray = json.encodeToString(SharePayload.serializer(), payload).toByteArray(Charsets.UTF_8)

    fun decode(bytes: ByteArray): SharePayload = json.decodeFromString(SharePayload.serializer(), bytes.toString(Charsets.UTF_8))

    fun trip(record: TripRecord, nowEpochMs: Long, utcOffsetMin: Int): SharePayload {
        val s = record.summary
        val st = s.stats
        return SharePayload(
            kind = ShareKind.TRIP,
            createdAtEpochMs = nowEpochMs,
            utcOffsetMin = utcOffsetMin,
            trip = TripShare(
                startEpochMs = s.startEpochMs,
                endEpochMs = s.endEpochMs,
                drive = s.kind == TripKind.DRIVE,
                distanceKm = round1(st.distanceKm),
                durationS = st.durationS.roundToInt(),
                avgKmh = st.avgMovingKmh.roundToInt(),
                maxKmh = st.maxKmh.roundToInt(),
                score = s.score,
                events = counts(s.events),
                moments = record.events.map { e ->
                    MomentShare(e.type.name, e.atS.roundToInt(), e.fromKmh.roundToInt(), e.toKmh.roundToInt(), round1(abs(e.peakMps2) * 3.6f))
                },
                accel = mix(st),
                brake = brake(st),
                parking = s.parking?.let { ParkingShare(it.durationS.roundToInt(), it.directionChanges, it.stops, it.maxSteerPct, it.endedInReverse) },
                timeline = timeline(record.timeline),
                tips = CoachingTips.forTrip(s).map(::tip),
            ),
        )
    }

    fun period(summaries: List<TripSummary>, days: Int, nowEpochMs: Long, utcOffsetMin: Int): SharePayload {
        val p = PeriodSummary.of(summaries, nowEpochMs, days)
        val from = nowEpochMs - days * PeriodSummary.DAY_MS
        val drives = summaries.filter { it.kind == TripKind.DRIVE && it.startEpochMs >= from }.sortedByDescending { it.startEpochMs }
        return SharePayload(
            kind = ShareKind.PERIOD,
            createdAtEpochMs = nowEpochMs,
            utcOffsetMin = utcOffsetMin,
            period = PeriodShare(
                days = days,
                trips = p.trips,
                distanceKm = round1(p.stats.distanceKm),
                durationS = p.stats.durationS.roundToInt(),
                score = p.score,
                events = counts(p.events),
                per10Km = EventType.entries.associate { it.name to round2(p.per10Km(it)) },
                accel = mix(p.stats),
                brake = brake(p.stats),
                weeks = p.weeks.map { WeekShare(it.startEpochMs, round1(it.distanceKm), it.harshPer10Km?.let(::round1), it.score) },
                parking = parkingHabit(p.parking),
                drives = drives.take(MAX_DRIVE_ROWS).map { t ->
                    DriveRow(t.startEpochMs, round1(t.stats.distanceKm), t.stats.durationS.roundToInt(), t.score, PeriodSummary.HARSH_TYPES.sumOf { t.count(it) })
                },
                tips = CoachingTips.forPeriod(p).map(::tip),
            ),
        )
    }

    /** Buckets of whole seconds: mean speed, brake = 1 when it was pressed for at least half a second. */
    fun timeline(points: List<TimelinePoint>): TimelineShare? {
        if (points.isEmpty()) return null
        val step = ceil(points.size / MAX_TIMELINE_POINTS.toDouble()).toInt().coerceAtLeast(1)
        val buckets = points.chunked(step)
        val brakeKnown = points.any { it.brake != null }
        return TimelineShare(
            stepS = step,
            tS = buckets.map { it.first().tS },
            kmh = buckets.map { b -> b.map { it.kmh }.average().roundToInt() },
            brake = if (brakeKnown) buckets.map { b -> if (b.any { (it.brake ?: 0f) >= 0.5f }) 1 else 0 } else null,
        )
    }

    private fun counts(events: Map<EventType, Int>): Map<String, Int> = events.filterValues { it > 0 }.mapKeys { it.key.name }

    private fun mix(st: HabitStats) = MixShare(round1(st.accelGentleS), round1(st.accelModerateS), round1(st.accelHardS))

    private fun brake(st: HabitStats): BrakeShare? = if (!st.brakeKnown) null else BrakeShare(
        presses = st.brakePresses,
        per10Km = if (st.distanceKm > 0.1f) round1(st.brakePresses * 10f / st.distanceKm) else null,
        avgPressS = st.avgBrakePressS?.let(::round1),
        coastShare = st.coastShare?.let(::round2),
    )

    private fun parkingHabit(sessions: List<ParkingSession>): ParkingHabitShare? = if (sessions.isEmpty()) null else ParkingHabitShare(
        sessions = sessions.size,
        avgDurationS = sessions.map { it.durationS }.average().roundToInt(),
        avgDirectionChanges = round1(sessions.map { it.directionChanges }.average().toFloat()),
        reverseShare = round2(sessions.count { it.endedInReverse }.toFloat() / sessions.size),
    )

    private fun tip(t: Tip) = TipShare(t.title, t.body, t.praise)

    private fun round1(v: Float) = (v * 10).roundToInt() / 10f

    private fun round2(v: Float) = (v * 100).roundToInt() / 100f
}
