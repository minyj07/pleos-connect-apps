@file:OptIn(ExperimentalLayoutApi::class)

package com.example.drivecoach.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.drivecoach.analysis.CoachingTips
import com.example.drivecoach.analysis.Tip
import com.example.drivecoach.model.EventType
import com.example.drivecoach.model.TripKind
import com.example.drivecoach.model.TripSummary
import com.example.drivecoach.record.RecorderState
import com.example.drivecoach.ui.MainViewModel
import com.example.drivecoach.ui.components.ButtonKind
import com.example.drivecoach.ui.components.CoachButton
import com.example.drivecoach.ui.components.Pill
import com.example.drivecoach.ui.components.ScoreRing
import com.example.drivecoach.ui.components.SectionCard
import com.example.drivecoach.ui.components.eventColor
import com.example.drivecoach.ui.components.fmtDateTime
import com.example.drivecoach.ui.components.CardRow
import com.example.drivecoach.ui.components.fmtDuration
import com.example.drivecoach.ui.components.fmtKm
import com.example.drivecoach.ui.theme.CoachColors
import com.example.drivecoach.ui.theme.CoachSizes
import com.example.drivecoach.vehicle.Signal
import com.example.drivecoach.vehicle.SignalAvailability

@Composable
fun HomeScreen(vm: MainViewModel, onOpenTrip: (String) -> Unit, onConsent: () -> Unit, onGoParking: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val week by vm.week.collectAsStateWithLifecycle()
    val trips by vm.trips.collectAsStateWithLifecycle()
    val recorder by vm.recorder.collectAsStateWithLifecycle()
    val live by vm.live.collectAsStateWithLifecycle()
    val untracked by vm.untrackedKm.collectAsStateWithLifecycle()
    val lastDrive = trips.firstOrNull { it.kind == TripKind.DRIVE }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("드라이브 코치", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            RecorderPill(recorder, settings.consented, settings.recording)
        }

        if (!settings.consented) {
            SectionCard("운전 습관 기록이 꺼져 있어요") {
                Text("기록에 동의하면 주행이 끝날 때마다 급가속·급제동, 브레이크 사용 습관 리포트를 보여 드려요. 주차 연습은 지금도 쓸 수 있어요.", style = MaterialTheme.typography.bodyLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(CoachSizes.Gap)) {
                    CoachButton("동의하고 기록 시작", onConsent)
                    CoachButton("주차 연습하기", onGoParking, kind = ButtonKind.Secondary)
                }
            }
            return@Column
        }

        CardRow {
            card { m ->
                SectionCard("최근 7일", m) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ScoreRing(week.score)
                        Spacer(Modifier.width(24.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("주행 ${week.trips}회 · ${fmtKm(week.stats.distanceKm)} km", style = MaterialTheme.typography.titleMedium)
                            Text("운전 시간 ${fmtDuration(week.stats.durationS)}", style = MaterialTheme.typography.bodyLarge)
                            EventCounts(week.events)
                        }
                    }
                }
            }
            card { m ->
                // The whole card opens the report, not just a line of text.
                SectionCard("지난 주행", m, onClick = lastDrive?.let { d -> { onOpenTrip(d.id) } }) {
                    if (lastDrive == null) {
                        Text("아직 주행 기록이 없어요. 운전을 시작하면 자동으로 기록돼요.", style = MaterialTheme.typography.bodyLarge)
                    } else {
                        LastTrip(lastDrive)
                    }
                }
            }
        }

        val tips = CoachingTips.forPeriod(week)
        if (tips.isNotEmpty()) {
            SectionCard("코칭") {
                tips.take(3).forEach { TipRow(it) }
            }
        }

        SectionCard("차량 신호") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(Signal.SPEED, Signal.GEAR, Signal.BRAKE, Signal.STEERING, Signal.CRUISE).forEach { s ->
                    val a = live.availability(s)
                    Pill("${s.label} ${availabilityText(a)}", availabilityColor(a))
                }
            }
            Text(
                "가속 페달 값은 차량에서 제공되지 않아, 액셀 습관은 브레이크를 밟지 않은 상태의 속도 변화로 추정해요.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (untracked >= 1f) {
                Text(
                    "최근 7일 동안 기록되지 않은 주행이 약 ${fmtKm(untracked)} km 있어요. 차량이 잠든 사이 앱이 종료되면 다음에 앱을 열 때까지 기록되지 않아요.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CoachColors.Moderate,
                )
            }
        }
    }
}

@Composable
private fun RecorderPill(state: RecorderState, consented: Boolean, recordingOn: Boolean) {
    when {
        !consented -> Pill("기록 꺼짐", MaterialTheme.colorScheme.outline)
        !recordingOn -> Pill("기록 일시 중지", MaterialTheme.colorScheme.outline)
        state is RecorderState.Recording -> Pill("주행 기록 중", CoachColors.Gentle)
        state is RecorderState.Waiting -> Pill("기록 대기 중", MaterialTheme.colorScheme.primary)
        else -> Pill("기록 준비 중", MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun LastTrip(t: TripSummary) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(fmtDateTime(t.startEpochMs), style = MaterialTheme.typography.titleMedium)
        Text("${fmtKm(t.stats.distanceKm)} km · ${fmtDuration(t.stats.durationS)} · 부드러움 ${t.score ?: "—"}점", style = MaterialTheme.typography.bodyLarge)
        EventCounts(t.events)
        Text("리포트 보기 ›", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun EventCounts(events: Map<EventType, Int>) {
    val shown = EventType.entries.filter { (events[it] ?: 0) > 0 }
    if (shown.isEmpty()) {
        Text("급가속·급제동 없음", style = MaterialTheme.typography.bodyLarge, color = CoachColors.Gentle)
        return
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        shown.forEach { Pill("${it.label} ${events[it]}", eventColor(it)) }
    }
}

@Composable
fun TipRow(tip: Tip) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(tip.title, style = MaterialTheme.typography.titleMedium, color = if (tip.praise) CoachColors.Gentle else MaterialTheme.colorScheme.onSurface)
        Text(tip.body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

fun availabilityText(a: SignalAvailability): String = when (a) {
    SignalAvailability.AVAILABLE -> "✓"
    SignalAvailability.NO_PERMISSION -> "권한 필요"
    SignalAvailability.NOT_SUPPORTED -> "미지원"
    SignalAvailability.DISCONNECTED -> "연결 안 됨"
    SignalAvailability.UNKNOWN -> "확인 중"
}

@Composable
fun availabilityColor(a: SignalAvailability): Color = when (a) {
    SignalAvailability.AVAILABLE -> CoachColors.Gentle
    SignalAvailability.NO_PERMISSION -> CoachColors.Moderate
    SignalAvailability.UNKNOWN -> MaterialTheme.colorScheme.outline
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
