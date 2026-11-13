@file:OptIn(ExperimentalLayoutApi::class)

package com.example.drivecoach.ui.habits

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import com.example.drivecoach.model.EventType
import com.example.drivecoach.ui.MainViewModel
import com.example.drivecoach.ui.components.BarLegend
import com.example.drivecoach.ui.components.BarPart
import com.example.drivecoach.ui.components.BodyText
import com.example.drivecoach.ui.components.CardRow
import com.example.drivecoach.ui.components.CardTitle
import com.example.drivecoach.ui.components.CoachCard
import com.example.drivecoach.ui.components.CoachIcons
import com.example.drivecoach.ui.components.Gauge
import com.example.drivecoach.ui.components.Hairline
import com.example.drivecoach.ui.components.HeaderButton
import com.example.drivecoach.ui.components.LocalWideLayout
import com.example.drivecoach.ui.components.PageTitle
import com.example.drivecoach.ui.components.RateRow
import com.example.drivecoach.ui.components.Segmented
import com.example.drivecoach.ui.components.SegmentedStyle
import com.example.drivecoach.ui.components.StackedBar
import com.example.drivecoach.ui.components.Stat
import com.example.drivecoach.ui.components.StatStyle
import com.example.drivecoach.ui.components.T
import com.example.drivecoach.ui.components.TipGrid
import com.example.drivecoach.ui.components.WeeklyBars
import com.example.drivecoach.ui.components.cardSurface
import com.example.drivecoach.ui.components.eventColor
import com.example.drivecoach.ui.components.fmt1
import com.example.drivecoach.ui.components.fmtKm
import com.example.drivecoach.ui.components.screenPadding
import com.example.drivecoach.ui.theme.CoachColors
import com.example.drivecoach.ui.theme.CoachTheme
import kotlin.math.roundToInt

/** 습관 분석: `main { padding: 44px 64px 56px 56px; gap: 24px; overflow-y: auto }`. */
@Composable
fun HabitsScreen(vm: MainViewModel) {
    val days by vm.periodDays.collectAsStateWithLifecycle()
    val p by vm.period.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val c = CoachTheme.colors

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(screenPadding(top = 44.dp, end = 64.dp, bottom = 56.dp, start = 56.dp)),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        val showShare = vm.shareAvailable && settings.consented && p.trips > 0
        val period: @Composable () -> Unit = {
            Segmented(listOf(7, 30, 90).map { "${it}일" to it }, days, SegmentedStyle.Period) { vm.setPeriodDays(it) }
        }
        val share: @Composable () -> Unit = {
            if (showShare) HeaderButton("폰으로 보기", CoachIcons.Qr, { vm.sharePeriod(days) })
        }
        if (LocalWideLayout.current) {
            // Period right after the title (driver's side), the share button at the far end.
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                    PageTitle("습관 분석")
                    period()
                }
                share()
            }
        } else {
            // In a portrait window the controls move to the next line instead of being squeezed.
            PageTitle("습관 분석")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                period()
                share()
            }
        }
        if (!settings.consented || p.trips == 0) {
            BodyText(if (!settings.consented) "운전 습관 기록에 동의하면 습관 분석을 볼 수 있어요." else "이 기간에 기록된 주행이 없어요.", color = c.ink2)
            return@Column
        }

        // grid-template-columns: 1fr 1.6fr
        CardRow {
            card(1f) { m -> ScoreCard(p, m) }
            card(1.6f) { m -> RatesCard(p, m) }
        }

        CardRow {
            card { m -> AccelCard(p, m) }
            card { m -> DecelCard(p, m) }
        }

        // grid-template-columns: 1.6fr 1fr; without parking sessions the weekly chart takes the row.
        if (p.parking.isNotEmpty()) {
            CardRow {
                card(1.6f) { m -> WeeklyCard(p, m) }
                card(1f) { m -> ParkingHabitsCard(p, m) }
            }
        } else {
            WeeklyCard(p, Modifier.fillMaxWidth())
        }

        val tips = CoachingTips.forPeriod(p)
        if (tips.isNotEmpty()) {
            CoachCard(Modifier.fillMaxWidth(), gap = 24.dp) {
                CardTitle("코칭")
                TipGrid(tips, columns = 3)
            }
        }
    }
}

/** `padding: 34px 40px; display: flex; align-items: center; gap: 40px`: the 220 px gauge (number 72 px) and two stats. */
@Composable
private fun ScoreCard(p: PeriodSummary, modifier: Modifier) {
    Row(
        modifier.cardSurface().padding(horizontal = 40.dp + Hairline, vertical = 34.dp + Hairline),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(40.dp),
    ) {
        Gauge(p.score, 220.dp, 72)
        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Stat("주행", p.trips.toString(), "회", StatStyle.Habits)
            Stat("거리", fmtKm(p.stats.distanceKm), "km", StatStyle.Habits)
        }
    }
}

/** "10 km당 횟수": one bar per event type, as wide as its share of the largest value in the list. */
@Composable
private fun RatesCard(p: PeriodSummary, modifier: Modifier) {
    val types = PeriodSummary.HARSH_TYPES.plus(EventType.ACCEL_THEN_BRAKE)
    val max = types.maxOf { p.per10Km(it) }
    CoachCard(modifier, gap = 18.dp) {
        CardTitle("10 km당 횟수")
        types.forEach { type -> RateRow(type.label, p.per10Km(type), max, eventColor(type)) }
    }
}

@Composable
private fun AccelCard(p: PeriodSummary, modifier: Modifier) {
    val c = CoachTheme.colors
    val parts = listOf(
        BarPart("부드럽게", p.stats.accelGentleS, c.acc),
        BarPart("보통", p.stats.accelModerateS, CoachColors.Amber),
        BarPart("강하게", p.stats.accelHardS, CoachColors.Red),
    )
    CoachCard(modifier) {
        CardTitle("액셀 사용")
        StackedBar(parts)
        BarLegend(parts, 19, c.ink1, 20.dp)
        T(
            "가속 페달 값은 차량에서 제공되지 않아, 브레이크를 밟지 않은 동안의 속도 증가율로 추정해요 (부드럽게 < 1.5, 강하게 ≥ 2.8 m/s²).",
            18,
            color = c.ink3,
            lh = 1.55f,
        )
    }
}

@Composable
private fun DecelCard(p: PeriodSummary, modifier: Modifier) {
    val c = CoachTheme.colors
    CoachCard(modifier) {
        CardTitle("감속 방식")
        val share = p.stats.coastShare
        if (share == null) {
            BodyText(if (!p.stats.brakeKnown) "브레이크 신호를 받을 수 없어 나눌 수 없어요." else "아직 감속 기록이 충분하지 않아요.")
        } else {
            val parts = listOf(BarPart("회생·관성", share, c.acc), BarPart("브레이크", 1f - share, CoachColors.Red))
            StackedBar(parts)
            BarLegend(parts, 19, c.ink1, 20.dp)
            T("발을 떼서(회생제동) 줄인 속도 에너지의 비율이에요. 높을수록 전비에 좋아요.", 18, color = c.ink3, lh = 1.55f)
        }
        if (p.stats.brakeKnown && p.stats.distanceKm > 0.1f) {
            Row(horizontalArrangement = Arrangement.spacedBy(48.dp)) {
                Stat("브레이크 10 km당", fmt1(p.stats.brakePresses * 10 / p.stats.distanceKm), "회", StatStyle.Habits)
                Stat("평균 밟는 시간", p.stats.avgBrakePressS?.let { fmt1(it) } ?: "—", "초", StatStyle.Habits)
            }
        }
    }
}

/** "주별 급가속·급제동" with "10 km당" on the title's baseline, then the bars. */
@Composable
private fun WeeklyCard(p: PeriodSummary, modifier: Modifier) {
    val c = CoachTheme.colors
    CoachCard(modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            T("주별 급가속·급제동", 28, Modifier.alignByBaseline(), weight = FontWeight.W700, nowrap = true)
            T("10 km당", 20, Modifier.alignByBaseline(), color = c.ink3, nowrap = true)
        }
        WeeklyBars(p.weeks)
    }
}

/** "주차 습관" with the number of sessions on the title's baseline; stats two per row, 24 px apart. */
@Composable
private fun ParkingHabitsCard(p: PeriodSummary, modifier: Modifier) {
    val c = CoachTheme.colors
    val stats = listOf(
        Triple("평균 걸린 시간", p.parking.map { it.durationS }.average().roundToInt().toString(), "초"),
        Triple("평균 전·후진 전환", fmt1(p.parking.map { it.directionChanges }.average().toFloat()), "회"),
        Triple("후진 주차", "${(p.parking.count { it.endedInReverse } * 100 / p.parking.size)}", "%"),
    )
    CoachCard(modifier, gap = 24.dp) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            T("주차 습관", 28, Modifier.alignByBaseline(), weight = FontWeight.W700, nowrap = true)
            T("${p.parking.size}회", 20, Modifier.alignByBaseline(), color = c.ink3, nowrap = true)
        }
        Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
            stats.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    row.forEach { (label, value, unit) -> Stat(label, value, unit, StatStyle.Habits, Modifier.weight(1f)) }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}
