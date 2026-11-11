package com.example.drivecoach.analysis

import com.example.drivecoach.model.EventType
import com.example.drivecoach.model.HabitStats
import com.example.drivecoach.model.ParkingSession
import com.example.drivecoach.model.TripKind
import com.example.drivecoach.model.TripSummary

/** Habits over a period (driving trips only; parking sessions also from short manoeuvres). */
data class PeriodSummary(
    val trips: Int,
    val stats: HabitStats,
    val events: Map<EventType, Int>,
    val score: Int?,
    val parking: List<ParkingSession>,
    val weeks: List<WeekBucket>,
) {
    fun per10Km(type: EventType): Float =
        if (stats.distanceKm < 0.1f) 0f else (events[type] ?: 0) * 10f / stats.distanceKm

    companion object {
        fun of(summaries: List<TripSummary>, nowEpochMs: Long, days: Int): PeriodSummary {
            val from = nowEpochMs - days * DAY_MS
            val inPeriod = summaries.filter { it.startEpochMs >= from }
            val drives = inPeriod.filter { it.kind == TripKind.DRIVE }
            val stats = drives.fold(HabitStats()) { acc, t -> acc + t.stats }
            val events = drives.flatMap { it.events.entries }
                .groupBy({ it.key }, { it.value })
                .mapValues { (_, v) -> v.sum() }
            val weeks = (0 until (days + 6) / 7).map { w ->
                val end = nowEpochMs - w * WEEK_MS
                val start = end - WEEK_MS
                val inWeek = drives.filter { it.startEpochMs in start until end }
                val km = inWeek.sumOf { it.stats.distanceKm.toDouble() }.toFloat()
                val counts = inWeek.flatMap { it.events.entries }.groupBy({ it.key }, { it.value }).mapValues { (_, v) -> v.sum() }
                WeekBucket(
                    startEpochMs = start,
                    distanceKm = km,
                    harshPer10Km = if (km < 0.1f) null else HARSH_TYPES.sumOf { counts[it] ?: 0 } * 10f / km,
                    score = if (inWeek.isEmpty()) null else Scoring.smoothness(counts, km),
                )
            }.reversed()
            return PeriodSummary(
                trips = drives.size,
                stats = stats,
                events = events,
                score = if (drives.isEmpty()) null else Scoring.smoothness(events, stats.distanceKm),
                parking = inPeriod.mapNotNull { it.parking },
                weeks = weeks,
            )
        }

        val HARSH_TYPES = listOf(EventType.HARSH_START, EventType.HARSH_ACCEL, EventType.HARSH_BRAKE, EventType.HARD_STOP)
        const val DAY_MS = 24 * 60 * 60 * 1000L
        const val WEEK_MS = 7 * DAY_MS
    }
}

data class WeekBucket(val startEpochMs: Long, val distanceKm: Float, val harshPer10Km: Float?, val score: Int?)

data class Tip(val title: String, val body: String, val praise: Boolean = false)

/**
 * Rule-based coaching sentences. Deliberately plain: no ranking, streaks or rewards (store policy
 * forbids gamifying driving), and nothing that sounds like a safety guarantee.
 */
object CoachingTips {

    fun forStats(stats: HabitStats, events: Map<EventType, Int>, parking: List<ParkingSession> = emptyList()): List<Tip> {
        val km = stats.distanceKm
        if (km < 1f) return emptyList()
        fun rate(vararg types: EventType) = types.sumOf { events[it] ?: 0 } * 10f / km
        val tips = mutableListOf<Tip>()

        val braking = rate(EventType.HARSH_BRAKE, EventType.HARD_STOP)
        if (braking >= 1f) {
            tips += Tip(
                "급제동이 잦아요",
                "앞차와 거리를 넉넉히 두고, 신호나 정체가 보이면 미리 발을 떼어 회생제동으로 감속해 보세요.",
            )
        }
        if (rate(EventType.HARSH_START) >= 0.5f) {
            tips += Tip("출발이 급해요", "출발 후 2~3초는 페달을 천천히 밟아 보세요. 승차감과 전비가 함께 좋아져요.")
        }
        if (rate(EventType.HARSH_ACCEL) >= 0.5f) {
            tips += Tip("가속이 강한 편이에요", "합류나 추월이 아니라면 속도를 천천히 올려도 도착 시간 차이는 거의 없어요.")
        }
        if (rate(EventType.ACCEL_THEN_BRAKE) >= 1f) {
            tips += Tip("가속 직후 제동이 잦아요", "앞 상황을 멀리 보고 가속을 줄이면 불필요한 제동이 줄어들어요.")
        }
        stats.coastShare?.let { share ->
            if (share < 0.5f) {
                tips += Tip(
                    "브레이크로 감속하는 비율이 높아요",
                    "감속할 때 브레이크 대신 일찍 발을 떼면 회생제동으로 에너지를 되찾을 수 있어요. i-PEDAL이나 회생제동 단계를 높여 보는 것도 방법이에요.",
                )
            } else if (share >= 0.75f) {
                tips += Tip("회생제동을 잘 활용하고 있어요", "감속의 ${(share * 100).toInt()}%를 브레이크 없이 처리했어요.", praise = true)
            }
        }
        val recent = parking.takeLast(10)
        if (recent.size >= 3 && recent.map { it.directionChanges }.average() >= 3.0) {
            tips += Tip("주차할 때 전·후진 전환이 많아요", "주차 연습에서 기준점과 핸들을 꺾는 시점을 미리 확인해 보세요.")
        }
        if (tips.none { !it.praise }) {
            tips.add(0, Tip("부드럽게 운전하고 있어요", "급가속·급제동이 거의 없어요. 지금처럼 유지해 보세요.", praise = true))
        }
        return tips
    }

    fun forPeriod(p: PeriodSummary): List<Tip> = forStats(p.stats, p.events, p.parking)

    fun forTrip(t: TripSummary): List<Tip> = forStats(t.stats, t.events, listOfNotNull(t.parking))
}
