@file:OptIn(ExperimentalLayoutApi::class)

package com.example.drivecoach.ui.habits

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.drivecoach.analysis.CoachingTips
import com.example.drivecoach.analysis.PeriodSummary
import com.example.drivecoach.model.EventType
import com.example.drivecoach.ui.MainViewModel
import com.example.drivecoach.ui.components.BarPart
import com.example.drivecoach.ui.components.ButtonKind
import com.example.drivecoach.ui.components.CardRow
import com.example.drivecoach.ui.components.ChoiceRow
import com.example.drivecoach.ui.components.CoachButton
import com.example.drivecoach.ui.components.ScoreRing
import com.example.drivecoach.ui.components.SectionCard
import com.example.drivecoach.ui.components.StackedBar
import com.example.drivecoach.ui.components.StatTile
import com.example.drivecoach.ui.components.WeeklyBars
import com.example.drivecoach.ui.components.eventColor
import com.example.drivecoach.ui.components.fmtKm
import com.example.drivecoach.ui.home.TipRow
import com.example.drivecoach.ui.theme.CoachColors
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun HabitsScreen(vm: MainViewModel) {
    val days by vm.periodDays.collectAsStateWithLifecycle()
    val p by vm.period.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // Period right after the title (driver's side) instead of the far right edge. In a portrait window
        // the button moves to the next line instead of being squeezed.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("습관 분석", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.align(Alignment.CenterVertically))
            ChoiceRow(listOf(7, 30, 90).map { "${it}일" to it }, days, Modifier.width(390.dp).align(Alignment.CenterVertically)) { vm.setPeriodDays(it) }
            if (vm.shareAvailable && settings.consented && p.trips > 0) {
                CoachButton("폰으로 보기", { vm.sharePeriod(days) }, Modifier.align(Alignment.CenterVertically), kind = ButtonKind.Secondary, icon = Icons.Filled.QrCode2)
            }
        }
        if (!settings.consented || p.trips == 0) {
            Text(
                if (!settings.consented) "운전 습관 기록에 동의하면 습관 분석을 볼 수 있어요." else "이 기간에 기록된 주행이 없어요.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        CardRow {
            card { m ->
                SectionCard(null, m) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ScoreRing(p.score, size = 160.dp)
                        Spacer(Modifier.width(24.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("주행 ${p.trips}회", style = MaterialTheme.typography.titleLarge)
                            Text("${fmtKm(p.stats.distanceKm)} km", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
            card(1.4f) { m ->
                SectionCard("10 km당 횟수", m) {
                    PeriodSummary.HARSH_TYPES.plus(EventType.ACCEL_THEN_BRAKE).forEach { type ->
                        RateRow(type.label, p.per10Km(type), eventColor(type))
                    }
                }
            }
        }

        CardRow {
            card { m -> AccelCard(p, m) }
            card { m -> DecelCard(p, m) }
        }

        SectionCard("주별 급가속·급제동 (10 km당)") { WeeklyBars(p.weeks) }

        if (p.parking.isNotEmpty()) {
            SectionCard("주차 습관 (${p.parking.size}회)") {
                Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                    StatTile("평균 걸린 시간", p.parking.map { it.durationS }.average().roundToInt().toString(), unit = "초")
                    StatTile("평균 전·후진 전환", String.format(Locale.KOREA, "%.1f", p.parking.map { it.directionChanges }.average()), unit = "회")
                    StatTile("후진 주차", "${(p.parking.count { it.endedInReverse } * 100 / p.parking.size)}", unit = "%")
                }
            }
        }

        SectionCard("코칭") { CoachingTips.forPeriod(p).forEach { TipRow(it) } }
    }
}

@Composable
private fun AccelCard(p: PeriodSummary, modifier: Modifier) {
    SectionCard("액셀 사용", modifier) {
        StackedBar(
            listOf(
                BarPart("부드럽게", p.stats.accelGentleS, CoachColors.Gentle),
                BarPart("보통", p.stats.accelModerateS, CoachColors.Moderate),
                BarPart("강하게", p.stats.accelHardS, CoachColors.Hard),
            ),
        )
        Text(
            "가속 페달 값은 차량에서 제공되지 않아 브레이크를 밟지 않은 동안의 속도 증가율로 추정해요 (부드럽게 < 1.5, 강하게 ≥ 2.8 m/s²).",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DecelCard(p: PeriodSummary, modifier: Modifier) {
    SectionCard("감속 방식", modifier) {
        val share = p.stats.coastShare
        if (share == null) {
            Text(
                if (!p.stats.brakeKnown) "브레이크 신호를 받을 수 없어 나눌 수 없어요." else "아직 감속 기록이 충분하지 않아요.",
                style = MaterialTheme.typography.bodyLarge,
            )
        } else {
            StackedBar(listOf(BarPart("회생·관성", share, CoachColors.Coast), BarPart("브레이크", 1f - share, CoachColors.Brake)))
            Text("발을 떼서(회생제동) 줄인 속도 에너지의 비율이에요. 높을수록 전비에 좋아요.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (p.stats.brakeKnown && p.stats.distanceKm > 0.1f) {
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                StatTile("브레이크 10 km당", String.format(Locale.KOREA, "%.1f", p.stats.brakePresses * 10 / p.stats.distanceKm), unit = "회")
                StatTile("평균 밟는 시간", p.stats.avgBrakePressS?.let { String.format(Locale.KOREA, "%.1f", it) } ?: "—", unit = "초")
            }
        }
    }
}

/** Rate bar scaled to 3 events per 10 km (anything above is "a lot"). */
@Composable
private fun RateRow(label: String, value: Float, color: Color) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.width(150.dp))
        Canvas(
            Modifier
                .weight(1f)
                .height(18.dp),
        ) {
            val r = CornerRadius(size.height / 2)
            drawRoundRect(track, cornerRadius = r)
            val w = size.width * (value / RATE_SCALE).coerceIn(0f, 1f)
            if (w > 0f) drawRoundRect(color, size = Size(w, size.height), cornerRadius = r)
        }
        Text(String.format(Locale.KOREA, "%.1f", value), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 12.dp))
    }
}

private const val RATE_SCALE = 3f
