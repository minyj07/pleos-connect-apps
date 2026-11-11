@file:OptIn(ExperimentalLayoutApi::class)

package com.example.drivecoach.ui.trips

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.drivecoach.analysis.CoachingTips
import com.example.drivecoach.model.EventType
import com.example.drivecoach.model.TripKind
import com.example.drivecoach.model.TripRecord
import com.example.drivecoach.model.TripSummary
import com.example.drivecoach.ui.MainViewModel
import com.example.drivecoach.ui.components.BarPart
import com.example.drivecoach.ui.components.ButtonKind
import com.example.drivecoach.ui.components.CardRow
import com.example.drivecoach.ui.components.CoachButton
import com.example.drivecoach.ui.components.CoachIconButton
import com.example.drivecoach.ui.components.Dot
import com.example.drivecoach.ui.components.Pill
import com.example.drivecoach.ui.components.SectionCard
import com.example.drivecoach.ui.components.SpeedTimelineChart
import com.example.drivecoach.ui.components.StackedBar
import com.example.drivecoach.ui.components.StatTile
import com.example.drivecoach.ui.components.eventColor
import com.example.drivecoach.ui.components.fmtDateTime
import com.example.drivecoach.ui.components.fmtDuration
import com.example.drivecoach.ui.components.fmtKm
import com.example.drivecoach.ui.home.EventCounts
import com.example.drivecoach.ui.home.TipRow
import com.example.drivecoach.ui.theme.CoachColors
import com.example.drivecoach.ui.theme.CoachSizes
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun TripsScreen(vm: MainViewModel, onOpen: (String) -> Unit) {
    val trips by vm.trips.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("주행 기록", style = MaterialTheme.typography.headlineMedium)
        if (trips.isEmpty()) {
            Text(
                if (settings.consented) "아직 기록된 주행이 없어요. 운전을 마치면 여기에 리포트가 쌓여요." else "운전 습관 기록에 동의하면 주행 리포트가 여기에 쌓여요.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(trips, key = { it.id }) { t -> TripRow(t) { onOpen(t.id) } }
        }
    }
}

@Composable
private fun TripRow(t: TripSummary, onClick: () -> Unit) {
    SectionCard(null, Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(fmtDateTime(t.startEpochMs), style = MaterialTheme.typography.titleMedium)
                Text(
                    if (t.kind == TripKind.DRIVE) "${fmtKm(t.stats.distanceKm)} km · ${fmtDuration(t.stats.durationS)}" else "주차·짧은 이동 · ${fmtDuration(t.stats.durationS)}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (t.kind == TripKind.DRIVE) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    EventType.entries.filter { t.count(it) > 0 && it.scoreWeight > 0 }.forEach { Dot(eventColor(it)) }
                    Text("${t.score ?: "—"}점", style = MaterialTheme.typography.headlineMedium)
                }
            } else {
                Pill("주차", MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
fun TripDetailScreen(vm: MainViewModel, id: String, onBack: () -> Unit) {
    LaunchedEffect(id) { vm.loadTrip(id) }
    val record by vm.openTrip.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // Back at the driver's side; delete is kept at the far end, away from it.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CoachSizes.Gap)) {
            CoachIconButton(Icons.AutoMirrored.Filled.ArrowBack, "뒤로", onClick = onBack)
            Text(record?.summary?.let { fmtDateTime(it.startEpochMs) } ?: "주행 리포트", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            if (vm.shareAvailable && record?.summary?.id == id) {
                CoachButton("폰으로 보기", { vm.shareTrip(id) }, kind = ButtonKind.Secondary, icon = Icons.Filled.QrCode2)
            }
            CoachButton("삭제", { confirmDelete = true }, kind = ButtonKind.Danger, icon = Icons.Filled.Delete)
        }
        val r = record
        if (r == null || r.summary.id != id) {
            CircularProgressIndicator()
            return@Column
        }
        TripReport(r)
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("이 주행 기록을 지울까요?") },
            confirmButton = {
                CoachButton("삭제", {
                    confirmDelete = false
                    vm.deleteTrip(id)
                    onBack()
                }, kind = ButtonKind.Danger)
            },
            dismissButton = { CoachButton("취소", { confirmDelete = false }, kind = ButtonKind.Secondary) },
        )
    }
}

@Composable
private fun TripReport(r: TripRecord) {
    val s = r.summary
    val st = s.stats
    SectionCard(null) {
        // Wraps to a second line in a portrait window instead of running off the card.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            StatTile("부드러움", s.score?.toString() ?: "—", unit = "점", color = MaterialTheme.colorScheme.primary)
            StatTile("거리", fmtKm(st.distanceKm), unit = "km")
            StatTile("시간", fmtDuration(st.durationS))
            StatTile("평균 속도", st.avgMovingKmh.roundToInt().toString(), unit = "km/h")
            StatTile("최고 속도", st.maxKmh.roundToInt().toString(), unit = "km/h")
        }
        EventCounts(s.events)
    }
    if (r.timeline.size > 2) {
        SectionCard("속도와 브레이크") { SpeedTimelineChart(r) }
    }
    CardRow {
        card { m ->
            SectionCard("액셀 사용 (속도 변화로 추정)", m) {
                StackedBar(
                    listOf(
                        BarPart("부드럽게", st.accelGentleS, CoachColors.Gentle),
                        BarPart("보통", st.accelModerateS, CoachColors.Moderate),
                        BarPart("강하게", st.accelHardS, CoachColors.Hard),
                    ),
                )
            }
        }
        card { m ->
            SectionCard("브레이크 사용", m) {
                if (!st.brakeKnown) {
                    Text("이 차량에서는 브레이크 신호를 받을 수 없었어요.", style = MaterialTheme.typography.bodyLarge)
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                        StatTile("10 km당", if (st.distanceKm > 0.1f) String.format(Locale.KOREA, "%.1f", st.brakePresses * 10 / st.distanceKm) else "—", unit = "회")
                        StatTile("평균 밟는 시간", st.avgBrakePressS?.let { String.format(Locale.KOREA, "%.1f", it) } ?: "—", unit = "초")
                    }
                    st.coastShare?.let { share ->
                        StackedBar(
                            listOf(
                                BarPart("회생·관성", share, CoachColors.Coast),
                                BarPart("브레이크", 1f - share, CoachColors.Brake),
                            ),
                        )
                    }
                }
            }
        }
    }
    if (r.events.isNotEmpty()) {
        SectionCard("감지된 순간") {
            r.events.forEach { e ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Dot(eventColor(e.type))
                    Text(
                        "  ${minSec(e.atS)}  ${e.type.label}  ${e.fromKmh.roundToInt()} → ${e.toKmh.roundToInt()} km/h  (${String.format(Locale.KOREA, "%.1f", kotlin.math.abs(e.peakMps2) * 3.6f)} km/h/초)",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
    s.parking?.let { p ->
        SectionCard("주차") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                StatTile("걸린 시간", p.durationS.roundToInt().toString(), unit = "초")
                StatTile("전·후진 전환", p.directionChanges.toString(), unit = "회")
                StatTile("멈춘 횟수", p.stops.toString(), unit = "회")
                p.maxSteerPct?.let { StatTile("최대 핸들", it.toString(), unit = "%") }
            }
            Text(if (p.endedInReverse) "후진으로 주차했어요." else "전진으로 주차했어요.", style = MaterialTheme.typography.bodyLarge)
        }
    }
    val tips = CoachingTips.forTrip(s)
    if (tips.isNotEmpty()) {
        SectionCard("이번 주행 코칭") { tips.forEach { TipRow(it) } }
    }
}

private fun minSec(s: Float): String {
    val t = s.roundToInt()
    return "%d:%02d".format(t / 60, t % 60)
}
