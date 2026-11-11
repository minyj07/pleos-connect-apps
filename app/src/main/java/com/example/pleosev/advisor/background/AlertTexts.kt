package com.example.pleosev.advisor.background

import com.example.pleosev.advisor.AdvisorState
import com.example.pleosev.advisor.StationRef
import com.example.pleosev.advisor.domain.AlertTopic
import com.example.pleosev.advisor.domain.NaviActionKind
import com.example.pleosev.advisor.domain.NeedKind
import com.example.pleosev.advisor.domain.PlannedStopVerdict
import com.example.pleosev.advisor.domain.Severity
import com.example.pleosev.advisor.domain.StopRole
import com.example.pleosev.shared.api.Candidate

/** A heads-up notification: what happened, the best station and what its button does. */
data class AlertContent(
    val topic: AlertTopic,
    val severity: Severity,
    val title: String,
    val text: String,
    val station: StationRef?,      // null: nothing to suggest, the button opens the navi's charger list
    val action: NaviActionKind?,
)

/** Notification texts from the advisor state, short enough for the heads-up card next to the navi. */
object AlertTexts {

    /** The alert for [state] if it has a topic worth one; AlertPolicy decides when it is shown. */
    fun alert(state: AdvisorState): AlertContent? {
        val topic = topic(state) ?: return null
        // The cards keep their order between refreshes; the alert suggests one that is free right now.
        val best = state.candidates.firstOrNull { it.candidate.station.compatible.available > 0 } ?: state.candidates.firstOrNull()
        val station = best?.candidate?.station
        val text = when {
            best == null -> state.problem ?: "내비 충전소 목록에서 확인해 주세요"
            topic == AlertTopic.PLANNED_STOP && state.plannedVerdict == PlannedStopVerdict.UNREACHABLE -> "가까운 ${describe(best.candidate)}"
            topic == AlertTopic.PLANNED_STOP -> "대안 ${describe(best.candidate)}"
            else -> describe(best.candidate)
        }
        val title = headline(state)
        return AlertContent(
            topic = topic,
            severity = state.attention,
            title = if (state.attention == Severity.URGENT) "긴급 · $title" else title,
            text = text,
            station = station?.let { StationRef(it.statId, it.name, it.location, it.address) },
            action = best?.actions?.firstOrNull()?.kind,
        )
    }

    /** One line for the background-watch notification. */
    fun watching(state: AdvisorState, guiding: Boolean): String = when {
        state.need.kind == NeedKind.PLANNED_STOP && state.plannedVerdict == PlannedStopVerdict.OK -> {
            val counts = state.plannedStop?.compatible
            "${stopLabel(state)} ${state.plannedStop?.name ?: state.plannedStopName}: 급속 ${counts?.available}/${counts?.total} 이용 가능"
        }
        state.need.active -> headline(state)
        guiding -> "도착할 때까지 충전이 필요하지 않아요"
        else -> "경로 안내를 시작하면 충전 상태를 확인해요"
    }

    private fun topic(state: AdvisorState): AlertTopic? = when (state.need.kind) {
        NeedKind.ON_ROUTE -> AlertTopic.ON_ROUTE.takeIf { state.attention >= Severity.RECOMMENDED }
        NeedKind.NEARBY -> AlertTopic.NEARBY.takeIf { state.attention >= Severity.RECOMMENDED }
        // A planned stop with free chargers needs no alert; one in an unclear state only when nearly empty.
        NeedKind.PLANNED_STOP -> AlertTopic.PLANNED_STOP.takeIf {
            state.plannedVerdict == PlannedStopVerdict.BUSY ||
                (state.attention == Severity.URGENT && state.plannedVerdict != null && state.plannedVerdict != PlannedStopVerdict.OK)
        }
        NeedKind.NONE -> null
    }

    private fun headline(state: AdvisorState): String = when (state.need.kind) {
        NeedKind.ON_ROUTE -> if (state.attention >= Severity.RECOMMENDED) "도착 전에 충전이 필요해요" else "도착할 때 배터리가 빠듯해요"
        NeedKind.PLANNED_STOP -> {
            val stop = stopLabel(state)
            when (state.plannedVerdict) {
                PlannedStopVerdict.BUSY -> "${stop}에 빈 충전기가 없어요"
                PlannedStopVerdict.UNKNOWN, PlannedStopVerdict.NOT_FOUND -> "$stop 상태를 확인할 수 없어요"
                PlannedStopVerdict.NO_MATCHING_CHARGER -> "${stop}에 설정한 출력의 충전기가 없어요"
                PlannedStopVerdict.UNREACHABLE -> "${stop}까지 배터리가 부족해요"
                PlannedStopVerdict.OK -> "${stop}에 빈 충전기가 있어요"
                null -> "${stop}를 확인하고 있어요"
            }
        }
        NeedKind.NEARBY -> "배터리가 얼마 남지 않았어요"
        NeedKind.NONE -> "충전이 필요하지 않아요"
    }

    /** How the route's charging stop is called: the destination itself, or a stop on the way. */
    fun stopLabel(state: AdvisorState): String = if (state.plannedStopRole == StopRole.DESTINATION) "목적지 충전소" else "경로의 충전소"

    private fun describe(c: Candidate): String = listOfNotNull(
        c.station.name,
        "급속 ${c.station.compatible.available}/${c.station.compatible.total}",
        c.estDetourMin?.let { "+${it.toInt()}분" },
        c.estArrivalSocPct?.let { "도착 ${it.toInt()}%" },
    ).joinToString(" · ")
}
