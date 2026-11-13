@file:OptIn(ExperimentalLayoutApi::class)

package com.example.drivecoach.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.drivecoach.analysis.CoachingTips
import com.example.drivecoach.analysis.PeriodSummary
import com.example.drivecoach.model.TripKind
import com.example.drivecoach.model.TripSummary
import com.example.drivecoach.record.RecorderState
import com.example.drivecoach.ui.MainViewModel
import com.example.drivecoach.ui.components.BodyText
import com.example.drivecoach.ui.components.CardRow
import com.example.drivecoach.ui.components.CardTitle
import com.example.drivecoach.ui.components.CoachCard
import com.example.drivecoach.ui.components.CoachIconView
import com.example.drivecoach.ui.components.CoachIcons
import com.example.drivecoach.ui.components.Dot
import com.example.drivecoach.ui.components.EventChips
import com.example.drivecoach.ui.components.Gauge
import com.example.drivecoach.ui.components.InfoLine
import com.example.drivecoach.ui.components.LocalWideLayout
import com.example.drivecoach.ui.components.OutlinePill
import com.example.drivecoach.ui.components.PageTitle
import com.example.drivecoach.ui.components.PrimaryPill
import com.example.drivecoach.ui.components.StatusPill
import com.example.drivecoach.ui.components.T
import com.example.drivecoach.ui.components.TipGrid
import com.example.drivecoach.ui.components.fmtDateTime
import com.example.drivecoach.ui.components.fmtDuration
import com.example.drivecoach.ui.components.fmtKm
import com.example.drivecoach.ui.components.screenPadding
import com.example.drivecoach.ui.theme.CoachColors
import com.example.drivecoach.ui.theme.CoachTheme
import com.example.drivecoach.vehicle.Signal
import com.example.drivecoach.vehicle.SignalAvailability

/** 홈: `main { padding: 48px 64px 48px 56px; gap: 28px }`. */
@Composable
fun HomeScreen(vm: MainViewModel, onOpenTrip: (String) -> Unit, onConsent: () -> Unit, onGoParking: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val week by vm.week.collectAsStateWithLifecycle()
    val trips by vm.trips.collectAsStateWithLifecycle()
    val recorder by vm.recorder.collectAsStateWithLifecycle()
    val live by vm.live.collectAsStateWithLifecycle()
    val untracked by vm.untrackedKm.collectAsStateWithLifecycle()
    val lastDrive = trips.firstOrNull { it.kind == TripKind.DRIVE }
    val wide = LocalWideLayout.current

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(screenPadding(top = 48.dp, end = 64.dp, bottom = 48.dp, start = 56.dp)),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            PageTitle("드라이브 코치", Modifier.weight(1f, fill = false))
            RecorderPill(recorder, settings.consented, settings.recording)
        }

        if (!settings.consented) {
            CoachCard(Modifier.fillMaxWidth()) {
                CardTitle("운전 습관 기록이 꺼져 있어요")
                BodyText("기록에 동의하면 주행이 끝날 때마다 급가속·급제동, 브레이크 사용 습관 리포트를 보여 드려요. 주차 연습은 지금도 쓸 수 있어요.")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    PrimaryPill("동의하고 기록 시작", onConsent)
                    OutlinePill("주차 연습하기", onGoParking)
                }
            }
            return@Column
        }

        // grid-template-columns: 1.5fr 1fr; gap: 28px
        CardRow(spacing = 28.dp) {
            card(1.5f) { m -> WeekCard(week, m) }
            card(1f) { m -> LastTripCard(lastDrive, onOpenTrip, m) }
        }

        val tips = CoachingTips.forPeriod(week)
        if (tips.isNotEmpty()) {
            CoachCard(Modifier.fillMaxWidth(), gap = 24.dp) {
                CardTitle("코칭")
                TipGrid(tips.take(3), columns = 3)
            }
        }

        CoachCard(Modifier.fillMaxWidth(), padV = 30.dp) {
            val signals: @Composable (Modifier) -> Unit = { m ->
                FlowRow(m, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf(Signal.SPEED, Signal.GEAR, Signal.BRAKE, Signal.STEERING, Signal.CRUISE).forEach { s ->
                        SignalChip(s, live.availability(s))
                    }
                }
            }
            if (wide) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                    CardTitle("차량 신호")
                    signals(Modifier.weight(1f))
                }
            } else {
                CardTitle("차량 신호")
                signals(Modifier)
            }
            InfoLine("가속 페달 값은 차량에서 제공되지 않아, 액셀 습관은 브레이크를 밟지 않은 상태의 속도 변화로 추정해요.")
            if (untracked >= 1f) {
                InfoLine("최근 7일 동안 기록되지 않은 주행이 약 ${fmtKm(untracked)} km 있어요. 차량이 잠든 사이 앱이 종료되면 다음에 앱을 열 때까지 기록되지 않아요.")
            }
        }
    }
}

/** Header pill: grey dot while idle (the design's "기록 대기 중"), accent dot while a trip is being recorded. */
@Composable
private fun RecorderPill(state: RecorderState, consented: Boolean, recordingOn: Boolean) {
    val c = CoachTheme.colors
    when {
        !consented -> StatusPill("기록 꺼짐", c.ink4)
        !recordingOn -> StatusPill("기록 일시 중지", c.ink4)
        state is RecorderState.Recording -> StatusPill("주행 기록 중", c.acc)
        state is RecorderState.Waiting -> StatusPill("기록 대기 중", c.ink4)
        else -> StatusPill("기록 준비 중", c.ink4)
    }
}

/** "최근 7일": the 210 px gauge (number 68 px), then two 26 px lines and the event chips. */
@Composable
private fun WeekCard(week: PeriodSummary, modifier: Modifier) {
    val c = CoachTheme.colors
    CoachCard(modifier) {
        CardTitle("최근 7일")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            Gauge(week.score, 210.dp, 68)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Column {
                    T("주행 ${week.trips}회 · ${fmtKm(week.stats.distanceKm)} km", 26, lh = 1.5f)
                    T("운전 시간 ${fmtDuration(week.stats.durationS)}", 26, color = c.ink2, lh = 1.5f)
                }
                EventChips(week.events)
            }
        }
    }
}

/**
 * "지난 주행": the whole card opens the report (as before); the button sits at the bottom left (`margin-top: auto`).
 * Side by side the card is as tall as its neighbour, so the text takes the space above the button. Stacked
 * (narrow window) the card has no height of its own inside the scrolling page, where a weight would
 * collapse the text to nothing.
 */
@Composable
private fun LastTripCard(t: TripSummary?, onOpenTrip: (String) -> Unit, modifier: Modifier) {
    val c = CoachTheme.colors
    val wide = LocalWideLayout.current
    CoachCard(modifier, gap = 0.dp, onClick = t?.let { d -> { onOpenTrip(d.id) } }) {
        Column(if (wide) Modifier.weight(1f, fill = true) else Modifier, verticalArrangement = Arrangement.spacedBy(20.dp)) {
            CardTitle("지난 주행")
            if (t == null) {
                BodyText("아직 주행 기록이 없어요. 운전을 시작하면 자동으로 기록돼요.")
            } else {
                Column {
                    T(fmtDateTime(t.startEpochMs), 26, lh = 1.5f)
                    T("${fmtKm(t.stats.distanceKm)} km · ${fmtDuration(t.stats.durationS)} · 부드러움 ${t.score ?: "—"}점", 26, color = c.ink2, lh = 1.5f)
                }
                EventChips(t.events)
            }
        }
        if (t != null) {
            Spacer(Modifier.height(20.dp))
            PrimaryPill("리포트 보기 ›", { onOpenTrip(t.id) })
        }
    }
}

/** Signal chip: `padding: 10px 20px; border-radius: 30px; background: tile; 22px / 500` with the 22 px check. */
@Composable
private fun SignalChip(signal: Signal, availability: SignalAvailability) {
    val c = CoachTheme.colors
    Row(
        Modifier.background(c.tile, RoundedCornerShape(30.dp)).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        when (availability) {
            SignalAvailability.AVAILABLE -> CoachIconView(CoachIcons.Check, 22.dp, c.acc)
            SignalAvailability.NO_PERMISSION -> Dot(CoachColors.Amber, 10.dp)
            else -> Dot(c.off, 10.dp)
        }
        T(signal.label, 22, weight = FontWeight.W500, nowrap = true)
        availabilityText(availability)?.let { T(it, 20, color = c.ink3, nowrap = true) }
    }
}

/** What is said next to a signal that is not available (an available one shows the check only). */
private fun availabilityText(a: SignalAvailability): String? = when (a) {
    SignalAvailability.AVAILABLE -> null
    SignalAvailability.NO_PERMISSION -> "권한 필요"
    SignalAvailability.NOT_SUPPORTED -> "미지원"
    SignalAvailability.DISCONNECTED -> "연결 안 됨"
    SignalAvailability.UNKNOWN -> "확인 중"
}
