@file:OptIn(ExperimentalLayoutApi::class)

package com.example.drivecoach.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.drivecoach.BuildConfig
import com.example.drivecoach.data.Settings
import com.example.drivecoach.parking.CarPresets
import com.example.drivecoach.parking.VehicleGeometry
import com.example.drivecoach.ui.MainViewModel
import com.example.drivecoach.ui.components.ButtonKind
import com.example.drivecoach.ui.components.ChoiceRow
import com.example.drivecoach.ui.components.CoachButton
import com.example.drivecoach.ui.components.SectionCard
import com.example.drivecoach.ui.components.StepperSlider
import com.example.drivecoach.ui.components.SwitchRow
import com.example.drivecoach.ui.components.fmtDateTime
import com.example.drivecoach.ui.theme.CoachSizes
import com.example.drivecoach.vehicle.LiveVehicleState
import com.example.drivecoach.vehicle.Signal
import java.util.Locale

@Composable
fun SettingsScreen(vm: MainViewModel, onConsent: () -> Unit) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val geometry by vm.geometry.collectAsStateWithLifecycle()
    val live by vm.live.collectAsStateWithLifecycle()
    var confirmWithdraw by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
            // A list of settings: keep each switch near its label (and the driver) instead of the far edge.
            .wrapContentWidth(Alignment.Start)
            .widthIn(max = 1160.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("설정", style = MaterialTheme.typography.headlineMedium)

        SectionCard("주행 기록") {
            SwitchRow("주행 기록", "운전하는 동안 습관을 기록해요", s.recording && s.consented, { vm.setRecording(it) }, enabled = s.consented)
            SwitchRow("부팅 시 자동 시작", "차를 켜면 앱을 열지 않아도 기록을 시작해요", s.autoStartOnBoot, { vm.setAutoStart(it) }, enabled = s.consented)
            Text("보관 기간", style = MaterialTheme.typography.titleMedium)
            ChoiceRow(listOf("30일" to 30, "90일" to 90, "1년" to 365), s.retentionDays, Modifier.widthIn(max = 480.dp)) { vm.setRetention(it) }
        }

        SectionCard("차량 제원 (주차 연습)") {
            ProfileRow("자동 (차량이 알려 주는 제원)", s.vehicleProfile == Settings.PROFILE_AUTO) { vm.setVehicleProfile(Settings.PROFILE_AUTO) }
            CarPresets.all.forEach { p -> ProfileRow("${p.name} (참고값)", s.vehicleProfile == p.id) { vm.setVehicleProfile(p.id) } }
            ProfileRow("직접 입력", s.vehicleProfile == Settings.PROFILE_CUSTOM) { vm.setVehicleProfile(Settings.PROFILE_CUSTOM) }
            if (s.vehicleProfile == Settings.PROFILE_CUSTOM) CustomGeometryEditor(s.customGeometry ?: CarPresets.default) { vm.setCustomGeometry(it) }
            Text(
                "사용 중: ${geometry.name} · ${geometry.source.label} · 전장 ${m2(geometry.lengthM)} m · 전폭 ${m2(geometry.widthM)} m · 축거 ${m2(geometry.wheelbaseM)} m · 최소회전반경 ${m2(geometry.turningRadiusM)} m",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (s.vehicleProfile == Settings.PROFILE_AUTO && live.dimensionsMm == null) {
                Text("차량이 제원을 알려 주지 않아 기본 차종(${CarPresets.default.name})으로 계산해요.", style = MaterialTheme.typography.bodyMedium)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CoachSizes.Gap)) {
                Text(
                    "핸들 보정: " + (s.steeringFullLockRaw?.let { "끝까지 = ${String.format(Locale.KOREA, "%.1f", it)}°" } ?: "기본값 사용"),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                if (s.steeringFullLockRaw != null) CoachButton("초기화", { vm.calibrateSteering(null) }, kind = ButtonKind.Secondary)
            }
        }

        SectionCard("개인정보") {
            Text(
                "속도·기어·브레이크 여부·핸들 각도·주행 시간과 거리를 운전 습관 분석에만 쓰고, 차량 안에만 암호화해 저장해요. " +
                    "위치는 수집하지 않아요. 직접 '폰으로 보기'를 눌렀을 때만 그 리포트의 암호화된 사본을 서버에 7일 올려요.",
                style = MaterialTheme.typography.bodyLarge,
            )
            if (s.consented) {
                Text("동의함 · ${fmtDateTime(s.consentAtEpochMs)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                CoachButton("동의 철회 및 모든 기록 삭제", { confirmWithdraw = true }, kind = ButtonKind.Danger)
            } else {
                CoachButton("운전 습관 기록에 동의하기", onConsent)
            }
        }

        if (vm.shareAvailable) {
            val active by vm.activeShares.collectAsStateWithLifecycle()
            SectionCard("폰으로 보기") {
                Text(
                    "주행 리포트나 습관 분석에서 '폰으로 보기'를 누르면, 암호화한 사본을 서버에 7일 올리고 폰 카메라로 찍을 QR을 보여 줘요.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CoachSizes.Gap)) {
                    Text("열려 있는 링크 ${active}개", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    CoachButton("모두 지우기", vm::deleteAllShares, kind = ButtonKind.Danger, enabled = active > 0)
                }
                if (s.shareConsented) {
                    Text("동의함 · ${fmtDateTime(s.shareConsentAtEpochMs)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    CoachButton("폰으로 보기 동의 철회 (링크 모두 삭제)", vm::withdrawShareConsent, kind = ButtonKind.Danger)
                } else {
                    Text("처음 '폰으로 보기'를 누를 때 따로 동의를 받아요.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        vm.demo?.let { demo ->
            val playing by demo.playing.collectAsStateWithLifecycle()
            SectionCard("개발용: 데모 주행 (debug 빌드 전용)") {
                Text(playing?.let { "재생 중: $it" } ?: "에뮬레이터처럼 속도를 바꿀 수 없을 때 스크립트 주행을 같은 경로로 흘려보내요.", style = MaterialTheme.typography.bodyMedium)
                demo.scripts.forEach { name ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CoachSizes.Gap)) {
                        Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        CoachButton("1×", { vm.playDemo(name, 1) }, kind = ButtonKind.Secondary, enabled = playing == null)
                        CoachButton("10×", { vm.playDemo(name, 10) }, kind = ButtonKind.Secondary, enabled = playing == null)
                    }
                }
                if (playing != null) CoachButton("중지", vm::stopDemo)
            }
            SectionCard("개발용: 차량 신호") { SignalTable(live) }
        }

        SectionCard("앱 정보") {
            Text("드라이브 코치 ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyLarge)
            Text("운전 습관 분석과 주차 시뮬레이션은 참고용이에요. 안전 운전의 판단과 책임은 운전자에게 있어요.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (confirmWithdraw) {
        AlertDialog(
            onDismissRequest = { confirmWithdraw = false },
            title = { Text("동의를 철회할까요?") },
            text = { Text("기록을 바로 멈추고, 저장된 주행 기록과 암호화 키를 모두 지워요. 되돌릴 수 없어요.") },
            confirmButton = {
                CoachButton("철회하고 삭제", {
                    confirmWithdraw = false
                    vm.withdrawConsent()
                }, kind = ButtonKind.Danger)
            },
            dismissButton = { CoachButton("취소", { confirmWithdraw = false }, kind = ButtonKind.Secondary) },
        )
    }
}

/** Radio choice where the whole 64 dp row is the target. */
@Composable
private fun ProfileRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = CoachSizes.Small)
            .clip(RoundedCornerShape(16.dp))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CoachSizes.Gap),
    ) {
        RadioButton(selected = selected, onClick = null, modifier = Modifier.scale(1.4f))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

/** − / + steps and a slider instead of a keyboard (in-car text entry is limited). */
@Composable
private fun CustomGeometryEditor(initial: VehicleGeometry, onApply: (VehicleGeometry) -> Unit) {
    var length by remember(initial) { mutableFloatStateOf(initial.lengthM.toFloat()) }
    var width by remember(initial) { mutableFloatStateOf(initial.widthM.toFloat()) }
    var wheelbase by remember(initial) { mutableFloatStateOf(initial.wheelbaseM.toFloat()) }
    var radius by remember(initial) { mutableFloatStateOf(initial.turningRadiusM.toFloat()) }
    Column(Modifier.widthIn(max = 720.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DimStepper("전장", length, 3.5f..5.6f) { length = it }
        DimStepper("전폭", width, 1.6f..2.1f) { width = it }
        DimStepper("축거", wheelbase, 2.3f..3.4f) { wheelbase = it }
        DimStepper("최소회전반경", radius, 4.5f..6.8f) { radius = it }
        CoachButton("적용", { onApply(VehicleGeometry.custom(length.toDouble(), width.toDouble(), wheelbase.toDouble(), radius.toDouble())) })
    }
}

@Composable
private fun DimStepper(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    StepperSlider(label, value, range, step = 0.01f, format = { String.format(Locale.KOREA, "%.2f m", it) }, onCommit = onChange)
}

@Composable
private fun SignalTable(v: LiveVehicleState) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
            "제원(mm)" to (v.dimensionsMm?.joinToString(",") ?: "—"),
            "회생 단계/정차 모드" to "${v.regenLevel ?: "—"}/${v.stoppingMode ?: "—"}",
        )
        rows.forEach { (k, value) -> Text("$k: $value", style = MaterialTheme.typography.bodyMedium) }
    }
    Text(
        Signal.entries.joinToString("  ") { "${it.label}=${v.availability(it).name}" },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun m2(v: Double) = String.format(Locale.KOREA, "%.2f", v)
