@file:OptIn(ExperimentalLayoutApi::class)

package com.example.drivecoach.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.drivecoach.BuildConfig
import com.example.drivecoach.data.Settings
import com.example.drivecoach.parking.CarPresets
import com.example.drivecoach.parking.VehicleGeometry
import com.example.drivecoach.ui.MainViewModel
import com.example.drivecoach.ui.components.BoxShape
import com.example.drivecoach.ui.components.CardTitle
import com.example.drivecoach.ui.components.CheckLine
import com.example.drivecoach.ui.components.CoachCard
import com.example.drivecoach.ui.components.CoachDialog
import com.example.drivecoach.ui.components.DemoButton
import com.example.drivecoach.ui.components.LocalWideLayout
import com.example.drivecoach.ui.components.OutlinePill
import com.example.drivecoach.ui.components.PageTitle
import com.example.drivecoach.ui.components.PrimaryPill
import com.example.drivecoach.ui.components.RichT
import com.example.drivecoach.ui.components.Segmented
import com.example.drivecoach.ui.components.SegmentedStyle
import com.example.drivecoach.ui.components.SmallPill
import com.example.drivecoach.ui.components.StepperSlider
import com.example.drivecoach.ui.components.SwitchRow
import com.example.drivecoach.ui.components.T
import com.example.drivecoach.ui.components.bottomBorder
import com.example.drivecoach.ui.components.fmtDateTime
import com.example.drivecoach.ui.components.screenPadding
import com.example.drivecoach.ui.theme.CoachTheme
import com.example.drivecoach.vehicle.LiveVehicleState
import com.example.drivecoach.vehicle.Signal
import java.util.Locale

/**
 * 설정: `main { padding: 44px 64px 56px 56px; gap: 24px; overflow-y: auto }`; the cards are at most 1200 px
 * wide and keep to the driver's side.
 */
@Composable
fun SettingsScreen(vm: MainViewModel, onConsent: () -> Unit) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val geometry by vm.geometry.collectAsStateWithLifecycle()
    val live by vm.live.collectAsStateWithLifecycle()
    var confirmWithdraw by remember { mutableStateOf(false) }
    val c = CoachTheme.colors
    val wide = LocalWideLayout.current
    val card = Modifier.widthIn(max = 1200.dp).fillMaxWidth()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(screenPadding(top = 44.dp, end = 64.dp, bottom = 56.dp, start = 56.dp)),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        PageTitle("설정")

        // 화면: the row starts 22 px under the title.
        CoachCard(card, gap = 0.dp) {
            CardTitle("화면")
            SwitchRow("다크 모드", "차량 화면 전체를 어두운 테마로 바꿔요", s.darkMode, { vm.setDarkMode(it) }, Modifier.padding(top = 22.dp))
        }

        // 주행 기록: rows `padding: 22px 0` with a 2 px line under the first two.
        CoachCard(card, gap = 0.dp) {
            CardTitle("주행 기록")
            SwitchRow(
                "주행 기록", "운전하는 동안 습관을 기록해요", s.recording && s.consented, { vm.setRecording(it) },
                Modifier.bottomBorder(c.hair).padding(vertical = 22.dp),
                enabled = s.consented,
            )
            SwitchRow(
                "부팅 시 자동 시작", "차를 켜면 앱을 열지 않아도 기록을 시작해요", s.autoStartOnBoot, { vm.setAutoStart(it) },
                Modifier.bottomBorder(c.hair).padding(vertical = 22.dp),
                enabled = s.consented,
            )
            val retention: @Composable () -> Unit = {
                Segmented(listOf("30일" to 30, "90일" to 90, "1년" to 365), s.retentionDays, SegmentedStyle.Retention) { vm.setRetention(it) }
            }
            if (wide) {
                Row(Modifier.fillMaxWidth().padding(top = 22.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    T("보관 기간", 23, Modifier.weight(1f), weight = FontWeight.W700, nowrap = true)
                    retention()
                }
            } else {
                Column(Modifier.padding(top = 22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    T("보관 기간", 23, weight = FontWeight.W700, nowrap = true)
                    retention()
                }
            }
        }

        // 차량 제원
        CoachCard(card) {
            CardHeader("차량 제원", "주차 연습 시뮬레이션에 쓰여요")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ProfileRow("자동 (차량이 알려 주는 제원)", s.vehicleProfile == Settings.PROFILE_AUTO) { vm.setVehicleProfile(Settings.PROFILE_AUTO) }
                CarPresets.all.forEach { p -> ProfileRow("${p.name} (참고값)", s.vehicleProfile == p.id) { vm.setVehicleProfile(p.id) } }
                ProfileRow("직접 입력", s.vehicleProfile == Settings.PROFILE_CUSTOM) { vm.setVehicleProfile(Settings.PROFILE_CUSTOM) }
            }
            if (s.vehicleProfile == Settings.PROFILE_CUSTOM) CustomGeometryEditor(s.customGeometry ?: CarPresets.default) { vm.setCustomGeometry(it) }
            // `padding: 20px 24px; border-radius: 18px; background: tile; font-size: 19px; gap: 10px`
            Column(
                Modifier.fillMaxWidth().background(c.tile, BoxShape).padding(horizontal = 24.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                T("사용 중: ${geometry.name} · ${geometry.source.label}", 19, weight = FontWeight.W700)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Spec("전장", "${m2(geometry.lengthM)} m")
                    Spec("전폭", "${m2(geometry.widthM)} m")
                    Spec("축거", "${m2(geometry.wheelbaseM)} m")
                    Spec("최소회전반경", "${m2(geometry.turningRadiusM)} m")
                }
                if (s.vehicleProfile == Settings.PROFILE_AUTO && live.dimensionsMm == null) {
                    T("차량이 제원을 알려 주지 않아 기본 차종(${CarPresets.default.name})으로 계산해요.", 19, color = c.ink2)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    T(
                        "핸들 보정: " + (s.steeringFullLockRaw?.let { "끝까지 = ${String.format(Locale.KOREA, "%.1f", it)}°" } ?: "기본값 사용"),
                        19,
                        Modifier.weight(1f),
                        color = c.ink2,
                    )
                    if (s.steeringFullLockRaw != null) SmallPill("초기화", { vm.calibrateSteering(null) })
                }
            }
        }

        // 개인정보
        CoachCard(card) {
            CardHeader(
                "개인정보",
                "속도·기어·브레이크 여부·핸들 각도·주행 시간과 거리를 운전 습관 분석에만 쓰고, 차량 안에만 암호화해 저장해요. " +
                    "위치는 수집하지 않아요. 직접 ‘폰으로 보기’를 눌렀을 때만 그 리포트의 암호화된 사본을 서버에 7일 올려요.",
            )
            if (s.consented) {
                CheckLine("동의함 · ${fmtDateTime(s.consentAtEpochMs)}")
                OutlinePill("동의 철회 및 모든 기록 삭제", { confirmWithdraw = true }, danger = true)
            } else {
                PrimaryPill("운전 습관 기록에 동의하기", onConsent)
            }
        }

        // 폰으로 보기
        if (vm.shareAvailable) {
            val active by vm.activeShares.collectAsStateWithLifecycle()
            CoachCard(card) {
                CardHeader("폰으로 보기", "주행 리포트나 습관 분석에서 ‘폰으로 보기’를 누르면, 암호화한 사본을 서버에 7일 올리고 폰 카메라로 찍을 QR을 보여 줘요.")
                // `padding: 18px 24px; border-radius: 18px; background: tile`
                Row(
                    Modifier.fillMaxWidth().background(c.tile, BoxShape).padding(horizontal = 24.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    RichT(21, Modifier.weight(1f), weight = FontWeight.W700, nowrap = true) {
                        text("열려 있는 링크 ")
                        styled("${active}개", color = c.acc)
                    }
                    SmallPill("모두 지우기", vm::deleteAllShares, danger = true, enabled = active > 0)
                }
                if (s.shareConsented) {
                    CheckLine("동의함 · ${fmtDateTime(s.shareConsentAtEpochMs)}")
                    OutlinePill("폰으로 보기 동의 철회 (링크 모두 삭제)", vm::withdrawShareConsent, danger = true)
                } else {
                    T("처음 ‘폰으로 보기’를 누를 때 따로 동의를 받아요.", 18, color = c.ink3)
                }
            }
        }

        // 개발용 · debug 빌드 전용
        vm.demo?.let { demo ->
            val playing by demo.playing.collectAsStateWithLifecycle()
            Row(card.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                T("개발용 · debug 빌드 전용", 20, weight = FontWeight.W700, color = c.ink3, nowrap = true)
                Box(Modifier.weight(1f).height(2.dp).background(c.hair))
            }
            CoachCard(card, gap = 10.dp) {
                CardHeader("데모 주행", playing?.let { "재생 중: $it" } ?: "에뮬레이터처럼 속도를 바꿀 수 없을 때 스크립트 주행을 같은 경로로 흘려보내요.")
                demo.scripts.forEachIndexed { i, name ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .then(if (i == demo.scripts.lastIndex) Modifier else Modifier.bottomBorder(c.hair))
                            .padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        T(name, 21, Modifier.weight(1f))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            DemoButton("1×", { vm.playDemo(name, 1) }, enabled = playing == null)
                            DemoButton("10×", { vm.playDemo(name, 10) }, enabled = playing == null)
                        }
                    }
                }
                if (playing != null) PrimaryPill("중지", vm::stopDemo, Modifier.padding(top = 10.dp))
            }
            CoachCard(card, gap = 18.dp) {
                CardTitle("차량 신호")
                SignalTable(live)
            }
        }

        // 앱 정보
        CoachCard(card, gap = 10.dp) {
            CardTitle("앱 정보")
            RichT(22, weight = FontWeight.W700) {
                text("드라이브 코치 ")
                styled(BuildConfig.VERSION_NAME, FontWeight.W500, c.ink2)
            }
            T("운전 습관 분석과 주차 시뮬레이션은 참고용이에요. 안전 운전의 판단과 책임은 운전자에게 있어요.", 19, color = c.ink2, lh = 1.55f)
        }
    }

    if (confirmWithdraw) {
        CoachDialog(
            title = "동의를 철회할까요?",
            text = "기록을 바로 멈추고, 저장된 주행 기록과 암호화 키를 모두 지워요. 되돌릴 수 없어요.",
            confirm = "철회하고 삭제",
            onConfirm = {
                confirmWithdraw = false
                vm.withdrawConsent()
            },
            dismiss = "취소",
            onDismiss = { confirmWithdraw = false },
        )
    }
}

/** Card title with its note 6 px below: 19 px, line-height 1.55, ink2. */
@Composable
private fun CardHeader(title: String, note: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        CardTitle(title)
        T(note, 19, color = CoachTheme.colors.ink2, lh = 1.55f)
    }
}

/**
 * Radio choice where the whole row is the target: `padding: 16px 20px; border-radius: 18px; gap: 16px;
 * font-size: 21px`. Selected: tint background, 700, a 26 px ring with an 8 px accent border; otherwise tile
 * background, 500, a 2 px ink4 border. The ring's inside is the surface colour.
 */
@Composable
private fun ProfileRow(label: String, selected: Boolean, onClick: () -> Unit) {
    val c = CoachTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(BoxShape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .background(if (selected) c.tint else c.tile, BoxShape)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            Modifier
                .size(26.dp)
                .background(c.surface, CircleShape)
                .border(if (selected) 8.dp else 2.dp, if (selected) c.acc else c.ink4, CircleShape),
        )
        T(label, 21, Modifier.weight(1f), weight = if (selected) FontWeight.W700 else FontWeight.W500, nowrap = true)
    }
}

/** One dimension of the car in use: the name in ink3, then the value. */
@Composable
private fun Spec(label: String, value: String) {
    val ink3 = CoachTheme.colors.ink3
    RichT(19, nowrap = true) {
        styled("$label ", color = ink3)
        text(value)
    }
}

/** − / + steps and a slider instead of a keyboard (in-car text entry is limited). */
@Composable
private fun CustomGeometryEditor(initial: VehicleGeometry, onApply: (VehicleGeometry) -> Unit) {
    val c = CoachTheme.colors
    var length by remember(initial) { mutableFloatStateOf(initial.lengthM.toFloat()) }
    var width by remember(initial) { mutableFloatStateOf(initial.widthM.toFloat()) }
    var wheelbase by remember(initial) { mutableFloatStateOf(initial.wheelbaseM.toFloat()) }
    var radius by remember(initial) { mutableFloatStateOf(initial.turningRadiusM.toFloat()) }
    Column(
        Modifier.widthIn(max = 720.dp).fillMaxWidth().background(c.tile, BoxShape).padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        DimStepper("전장", length, 3.5f..5.6f) { length = it }
        DimStepper("전폭", width, 1.6f..2.1f) { width = it }
        DimStepper("축거", wheelbase, 2.3f..3.4f) { wheelbase = it }
        DimStepper("최소회전반경", radius, 4.5f..6.8f) { radius = it }
        PrimaryPill("적용", { onApply(VehicleGeometry.custom(length.toDouble(), width.toDouble(), wheelbase.toDouble(), radius.toDouble())) })
    }
}

@Composable
private fun DimStepper(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    StepperSlider(label, value, range, step = 0.01f, format = { String.format(Locale.KOREA, "%.2f m", it) }, onCommit = onChange)
}

/**
 * Debug only: `grid-template-columns: repeat(2, 1fr); gap: 10px 24px; font-size: 19px` of name / value
 * pairs, the raw dimensions at 17 px and the availability of every signal at 15 px in a monospace face.
 */
@Composable
private fun SignalTable(v: LiveVehicleState) {
    val c = CoachTheme.colors
    val rows = listOf(
        "속도" to (v.speedMps?.let { String.format(Locale.KOREA, "%.1f km/h", it * 3.6f) } ?: "—"),
        "기어" to v.gear.name,
        "브레이크" to (v.brake?.toString() ?: "—"),
        "조향 원값" to (v.steerRaw?.let { String.format(Locale.KOREA, "%.1f°", it) } ?: "—"),
        "조향 범위" to (v.steerMaxRaw?.let { String.format(Locale.KOREA, "±%.1f°", it) } ?: "—"),
        "주차 브레이크" to (v.parkingBrake?.toString() ?: "—"),
        "시동" to v.ignition.name,
        "크루즈" to v.cruiseActive.toString(),
        "누적 거리" to (v.odometerKm?.let { String.format(Locale.KOREA, "%.1f km", it) } ?: "—"),
        "회생 단계/정차 모드" to "${v.regenLevel ?: "—"}/${v.stoppingMode ?: "—"}",
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                pair.forEach { (k, value) ->
                    RichT(19, Modifier.weight(1f), nowrap = true, tabular = true) {
                        styled("$k ", color = c.ink3)
                        text(value)
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
    T("제원(mm) ${v.dimensionsMm?.joinToString(",") ?: "—"}", 17, color = c.ink3)
    T(
        Signal.entries.joinToString(" ") { "${it.label}=${v.availability(it).name}" },
        15,
        color = c.ink4,
        lh = 1.6f,
        family = FontFamily.Monospace,
    )
}

private fun m2(v: Double) = String.format(Locale.KOREA, "%.2f", v)
