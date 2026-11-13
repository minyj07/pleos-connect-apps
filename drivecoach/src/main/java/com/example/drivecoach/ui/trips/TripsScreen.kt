@file:OptIn(ExperimentalLayoutApi::class)

package com.example.drivecoach.ui.trips

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.drivecoach.analysis.CoachingTips
import com.example.drivecoach.analysis.PeriodSummary
import com.example.drivecoach.model.EventType
import com.example.drivecoach.model.HabitStats
import com.example.drivecoach.model.ParkingSession
import com.example.drivecoach.model.TripKind
import com.example.drivecoach.model.TripRecord
import com.example.drivecoach.model.TripSummary
import com.example.drivecoach.ui.MainViewModel
import com.example.drivecoach.ui.components.BackButton
import com.example.drivecoach.ui.components.BarLegend
import com.example.drivecoach.ui.components.BarPart
import com.example.drivecoach.ui.components.BodyText
import com.example.drivecoach.ui.components.CardPill
import com.example.drivecoach.ui.components.CardRow
import com.example.drivecoach.ui.components.CardTitle
import com.example.drivecoach.ui.components.CoachCard
import com.example.drivecoach.ui.components.CoachDialog
import com.example.drivecoach.ui.components.CoachIconView
import com.example.drivecoach.ui.components.CoachIcons
import com.example.drivecoach.ui.components.Dot
import com.example.drivecoach.ui.components.EventChip
import com.example.drivecoach.ui.components.EventChips
import com.example.drivecoach.ui.components.HeaderButton
import com.example.drivecoach.ui.components.LegendItem
import com.example.drivecoach.ui.components.LocalWideLayout
import com.example.drivecoach.ui.components.PageTitle
import com.example.drivecoach.ui.components.RichT
import com.example.drivecoach.ui.components.SpeedChart
import com.example.drivecoach.ui.components.StackedBar
import com.example.drivecoach.ui.components.Stat
import com.example.drivecoach.ui.components.StatStyle
import com.example.drivecoach.ui.components.T
import com.example.drivecoach.ui.components.TagPill
import com.example.drivecoach.ui.components.TipGrid
import com.example.drivecoach.ui.components.bottomBorder
import com.example.drivecoach.ui.components.cardSlice
import com.example.drivecoach.ui.components.durationParts
import com.example.drivecoach.ui.components.eventColor
import com.example.drivecoach.ui.components.fmt1
import com.example.drivecoach.ui.components.fmtDateTime
import com.example.drivecoach.ui.components.fmtDuration
import com.example.drivecoach.ui.components.fmtKm
import com.example.drivecoach.ui.components.minSec
import com.example.drivecoach.ui.components.scoreColor
import com.example.drivecoach.ui.components.screenPadding
import com.example.drivecoach.ui.components.shownEvents
import com.example.drivecoach.ui.theme.CoachColors
import com.example.drivecoach.ui.theme.CoachTheme
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 주행 기록: `main { padding: 48px 64px 48px 56px; gap: 28px }`, the header with the event legend, and one
 * card (`padding: 12px 40px`) holding a 104 px row per trip. The rows are built lazily; each draws its
 * slice of the card.
 */
@Composable
fun TripsScreen(vm: MainViewModel, onOpen: (String) -> Unit) {
    val trips by vm.trips.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val c = CoachTheme.colors
    val weekCount = remember(trips) {
        val from = System.currentTimeMillis() - PeriodSummary.WEEK_MS
        trips.count { it.startEpochMs >= from }
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = screenPadding(top = 48.dp, end = 64.dp, bottom = 48.dp, start = 56.dp),
    ) {
        item(key = "header") {
            TripsHeader(weekCount, showSummary = trips.isNotEmpty())
            Spacer(Modifier.height(28.dp))
        }
        if (trips.isEmpty()) {
            item(key = "empty") {
                BodyText(
                    if (settings.consented) "아직 기록된 주행이 없어요. 운전을 마치면 여기에 리포트가 쌓여요." else "운전 습관 기록에 동의하면 주행 리포트가 여기에 쌓여요.",
                    color = c.ink2,
                )
            }
        } else {
            itemsIndexed(trips, key = { _, t -> t.id }) { i, t ->
                TripRow(t, first = i == 0, last = i == trips.lastIndex) { onOpen(t.id) }
            }
        }
    }
}

@Composable
private fun TripsHeader(weekCount: Int, showSummary: Boolean) {
    val c = CoachTheme.colors
    val summary: @Composable (Modifier) -> Unit = { m ->
        FlowRow(m, horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), itemVerticalAlignment = Alignment.CenterVertically) {
            LegendItem(CoachColors.Amber, "급가속·급출발", 20, c.ink2, dot = 12.dp, gap = 10.dp)
            LegendItem(CoachColors.Red, "급감속·급정지", 20, c.ink2, dot = 12.dp, gap = 10.dp)
            CardPill("최근 7일 · ${weekCount}회")
        }
    }
    if (LocalWideLayout.current) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            PageTitle("주행 기록")
            if (showSummary) summary(Modifier)
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            PageTitle("주행 기록")
            if (showSummary) summary(Modifier)
        }
    }
}

/**
 * `grid-template-columns: 1fr 200px 220px 120px 28px; gap: 24px; height: 104px` with a 2 px line under every
 * row but the last: date and distance, one dot per kind of harsh event, the score bar, the score, a chevron.
 * A parking-only row has the "주차" pill instead of a score. The narrow window drops the bar.
 */
@Composable
private fun TripRow(t: TripSummary, first: Boolean, last: Boolean, onClick: () -> Unit) {
    val c = CoachTheme.colors
    val wide = LocalWideLayout.current
    val drive = t.kind == TripKind.DRIVE
    Box(
        Modifier
            .fillMaxWidth()
            .cardSlice(first, last, c)
            .padding(start = 42.dp, end = 42.dp, top = if (first) 14.dp else 0.dp, bottom = if (last) 14.dp else 0.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .then(if (last) Modifier else Modifier.bottomBorder(c.hair))
                .height(104.dp)
                .clickable(onClick = onClick),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                T(fmtDateTime(t.startEpochMs), 26, weight = FontWeight.W700, nowrap = true)
                T(
                    if (drive) "${fmtKm(t.stats.distanceKm)} km · ${fmtDuration(t.stats.durationS)}" else "주차·짧은 이동 · ${fmtDuration(t.stats.durationS)}",
                    21,
                    color = c.ink2,
                    nowrap = true,
                )
            }
            Row((if (wide) Modifier.width(200.dp) else Modifier), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                if (drive) {
                    EventType.entries.filter { t.count(it) > 0 && it.scoreWeight > 0 }.forEach { Dot(eventColor(it), 14.dp) }
                }
            }
            if (drive) {
                val score = t.score
                if (wide) {
                    Box(Modifier.width(220.dp).height(10.dp).clip(RoundedCornerShape(6.dp)).background(c.track)) {
                        if (score != null && score > 0) {
                            Box(Modifier.fillMaxWidth(score.coerceIn(0, 100) / 100f).fillMaxHeight().background(scoreColor(score), RoundedCornerShape(6.dp)))
                        }
                    }
                }
                Row(Modifier.width(if (wide) 120.dp else 96.dp), horizontalArrangement = Arrangement.End) {
                    T(score?.toString() ?: "—", 34, Modifier.alignByBaseline(), weight = FontWeight.W700, nowrap = true)
                    T("점", 20, Modifier.alignByBaseline().padding(start = 4.dp), weight = FontWeight.W500, color = c.ink2, nowrap = true)
                }
            } else {
                if (wide) Spacer(Modifier.width(220.dp))
                Box(Modifier.width(if (wide) 120.dp else 96.dp), contentAlignment = Alignment.CenterEnd) { TagPill("주차") }
            }
            Box(Modifier.width(28.dp)) { CoachIconView(CoachIcons.Chevron, 24.dp, CoachColors.Chevron) }
        }
    }
}

/** 주행 리포트: `main { padding: 44px 64px 56px 56px; gap: 24px; overflow-y: auto }`. */
@Composable
fun TripDetailScreen(vm: MainViewModel, id: String, onBack: () -> Unit) {
    LaunchedEffect(id) { vm.loadTrip(id) }
    val record by vm.openTrip.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    val c = CoachTheme.colors
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(screenPadding(top = 44.dp, end = 64.dp, bottom = 56.dp, start = 56.dp)),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        val title = record?.summary?.let { fmtDateTime(it.startEpochMs) } ?: "주행 리포트"
        // Back at the driver's side; delete is kept at the far end, away from it.
        val actions: @Composable () -> Unit = {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                if (vm.shareAvailable && record?.summary?.id == id) {
                    HeaderButton("폰으로 보기", CoachIcons.Qr, { vm.shareTrip(id) })
                }
                HeaderButton("삭제", CoachIcons.Trash, { confirmDelete = true }, color = CoachColors.Red)
            }
        }
        if (LocalWideLayout.current) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                BackButton(onBack)
                PageTitle(title, Modifier.weight(1f), size = 46)
                actions()
            }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                BackButton(onBack)
                PageTitle(title, Modifier.weight(1f), size = 46)
            }
            actions()
        }
        val r = record
        if (r == null || r.summary.id != id) {
            CircularProgressIndicator(Modifier.size(64.dp), color = c.acc, strokeWidth = 6.dp, trackColor = c.track)
            return@Column
        }
        TripReport(r)
    }
    if (confirmDelete) {
        CoachDialog(
            title = "이 주행 기록을 지울까요?",
            confirm = "삭제",
            onConfirm = {
                confirmDelete = false
                vm.deleteTrip(id)
                onBack()
            },
            dismiss = "취소",
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun TripReport(r: TripRecord) {
    val s = r.summary
    val st = s.stats
    SummaryCard(s)

    // grid-template-columns: 2.1fr 1fr; a card that has nothing to show leaves the row to the other one.
    val hasChart = r.timeline.size > 2
    val hasEvents = r.events.isNotEmpty()
    when {
        hasChart && hasEvents -> CardRow {
            card(2.1f) { m -> ChartCard(r, m) }
            card(1f) { m -> EventsCard(r, m) }
        }
        hasChart -> ChartCard(r, Modifier.fillMaxWidth())
        hasEvents -> EventsCard(r, Modifier.fillMaxWidth())
    }

    // grid-template-columns: repeat(3, 1fr); without a parking session the two cards share the row.
    CardRow {
        card { m -> AccelCard(st, m) }
        card { m -> BrakeCard(st, m) }
        s.parking?.let { p -> card { m -> ParkingCard(p, m) } }
    }

    val tips = CoachingTips.forTrip(s)
    if (tips.isNotEmpty()) {
        CoachCard(Modifier.fillMaxWidth(), gap = 24.dp) {
            CardTitle("이번 주행 코칭")
            TipGrid(tips, columns = 2, horizontal = true)
        }
    }
}

/** `padding: 28px 40px`: five stats (gap 40, a 2 px line after the score) and the event chips two per row. */
@Composable
private fun SummaryCard(s: TripSummary) {
    val c = CoachTheme.colors
    val st = s.stats
    CoachCard(Modifier.fillMaxWidth(), padV = 28.dp) {
        if (LocalWideLayout.current) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                Row(Modifier.weight(1f).height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                    Stat("부드러움", s.score?.toString() ?: "—", "점", StatStyle.Summary, valueColor = c.acc)
                    Box(Modifier.width(2.dp).fillMaxHeight().background(c.hair))
                    SummaryStats(st)
                }
                EventChipGrid(s.events)
            }
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(40.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Stat("부드러움", s.score?.toString() ?: "—", "점", StatStyle.Summary, valueColor = c.acc)
                SummaryStats(st)
            }
            EventChips(s.events, dots = true)
        }
    }
}

@Composable
private fun SummaryStats(st: HabitStats) {
    Stat("거리", fmtKm(st.distanceKm), "km", StatStyle.Summary)
    Stat("시간", durationParts(st.durationS), StatStyle.Summary)
    Stat("평균 속도", st.avgMovingKmh.roundToInt().toString(), "km/h", StatStyle.Summary)
    Stat("최고 속도", st.maxKmh.roundToInt().toString(), "km/h", StatStyle.Summary)
}

/** `grid-template-columns: repeat(2, auto); gap: 10px`: chips fill the grid row by row, and a column is as wide as its widest chip. */
@Composable
private fun EventChipGrid(events: Map<EventType, Int>) {
    val shown = shownEvents(events)
    if (shown.isEmpty()) {
        EventChip("급가속·급제동 없음", null, dot = CoachColors.ScoreGood)
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        for (column in 0..1) {
            val items = shown.filterIndexed { i, _ -> i % 2 == column }
            if (items.isNotEmpty()) {
                Column(Modifier.width(IntrinsicSize.Max), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items.forEach { EventChip(it.label, events[it], Modifier.fillMaxWidth(), dot = eventColor(it)) }
                }
            }
        }
    }
}

/** "속도와 브레이크": `padding: 30px 40px; gap: 18px`, the title with the legend at the right, then the chart. */
@Composable
private fun ChartCard(r: TripRecord, modifier: Modifier) {
    val c = CoachTheme.colors
    CoachCard(modifier, padV = 30.dp, gap = 18.dp) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            T("속도와 브레이크", 28, weight = FontWeight.W700, nowrap = true)
            FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.End), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LegendItem(c.acc, "속도", 18, c.ink2, swatch = true)
                LegendItem(CoachColors.BrakeSwatch, "브레이크", 18, c.ink2, swatch = true)
                LegendItem(CoachColors.Amber, "급가속·급출발", 18, c.ink2)
                LegendItem(CoachColors.Red, "급감속·급정지", 18, c.ink2)
            }
        }
        SpeedChart(r, Modifier.fillMaxWidth())
    }
}

/** "감지된 순간": `padding: 30px 36px`; rows `grid-template-columns: 64px 14px 1fr; gap: 14px; padding: 16px 0`. */
@Composable
private fun EventsCard(r: TripRecord, modifier: Modifier) {
    val c = CoachTheme.colors
    CoachCard(modifier, padV = 30.dp, padH = 36.dp, gap = 0.dp) {
        CardTitle("감지된 순간", Modifier.padding(bottom = 6.dp))
        r.events.forEachIndexed { i, e ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .then(if (i == r.events.lastIndex) Modifier else Modifier.bottomBorder(c.hair))
                    .padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                T(minSec(e.atS), 20, Modifier.width(64.dp), weight = FontWeight.W700, color = c.ink2, nowrap = true, tabular = true)
                Dot(eventColor(e.type), 14.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    RichT(23, weight = FontWeight.W700) {
                        text("${e.type.label} ")
                        styled("${e.fromKmh.roundToInt()} → ${e.toKmh.roundToInt()} km/h", FontWeight.W500)
                    }
                    T("${fmt1(abs(e.peakMps2) * 3.6f)} km/h/초", 18, color = c.ink3)
                }
            }
        }
    }
}

/** "액셀 사용": `padding: 30px 36px; gap: 20px`; title 26 px with "속도 변화로 추정" under it, the bar, the legend. */
@Composable
private fun AccelCard(st: HabitStats, modifier: Modifier) {
    val c = CoachTheme.colors
    val parts = listOf(
        BarPart("부드럽게", st.accelGentleS, c.acc),
        BarPart("보통", st.accelModerateS, CoachColors.Amber),
        BarPart("강하게", st.accelHardS, CoachColors.Red),
    )
    CoachCard(modifier, padV = 30.dp, padH = 36.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            T("액셀 사용", 26, weight = FontWeight.W700)
            T("속도 변화로 추정", 18, color = c.ink3)
        }
        StackedBar(parts)
        BarLegend(parts, 18, c.ink2, 18.dp)
    }
}

/** "브레이크 사용": two stats under the title (gap 14, 32 apart), then the regeneration / brake split. */
@Composable
private fun BrakeCard(st: HabitStats, modifier: Modifier) {
    val c = CoachTheme.colors
    CoachCard(modifier, padV = 30.dp, padH = 36.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            T("브레이크 사용", 26, weight = FontWeight.W700, nowrap = true)
            if (st.brakeKnown) {
                Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                    Stat("10 km당", if (st.distanceKm > 0.1f) fmt1(st.brakePresses * 10 / st.distanceKm) else "—", "회", StatStyle.Brake)
                    Stat("평균 밟는 시간", st.avgBrakePressS?.let { fmt1(it) } ?: "—", "초", StatStyle.Brake)
                }
            }
        }
        if (!st.brakeKnown) {
            T("이 차량에서는 브레이크 신호를 받을 수 없었어요.", 20, color = c.ink1, lh = 1.5f)
        } else {
            st.coastShare?.let { share ->
                val parts = listOf(BarPart("회생·관성", share, c.acc), BarPart("브레이크", 1f - share, CoachColors.Red))
                StackedBar(parts)
                BarLegend(parts, 18, c.ink2, 18.dp)
            }
        }
    }
}

/** "주차": `grid-template-columns: repeat(2, 1fr); gap: 16px 24px` of stats, then how the car was parked. */
@Composable
private fun ParkingCard(p: ParkingSession, modifier: Modifier) {
    val c = CoachTheme.colors
    val stats = buildList {
        add(Triple("걸린 시간", p.durationS.roundToInt().toString(), "초"))
        add(Triple("전·후진 전환", p.directionChanges.toString(), "회"))
        add(Triple("멈춘 횟수", p.stops.toString(), "회"))
        p.maxSteerPct?.let { add(Triple("최대 핸들", it.toString(), "%")) }
    }
    CoachCard(modifier, padV = 30.dp, padH = 36.dp) {
        T("주차", 26, weight = FontWeight.W700)
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            stats.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    row.forEach { (label, value, unit) -> Stat(label, value, unit, StatStyle.Parking, Modifier.weight(1f)) }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        T(if (p.endedInReverse) "후진으로 주차했어요." else "전진으로 주차했어요.", 20, color = c.ink1)
    }
}
