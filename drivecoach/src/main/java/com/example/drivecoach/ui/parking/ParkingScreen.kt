package com.example.drivecoach.ui.parking

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.drivecoach.model.Gear
import com.example.drivecoach.parking.ParkingPlan
import com.example.drivecoach.parking.ParkingPlanner
import com.example.drivecoach.parking.ParkingType
import com.example.drivecoach.parking.ScenarioSpec
import com.example.drivecoach.parking.Side
import com.example.drivecoach.ui.MainViewModel
import com.example.drivecoach.ui.components.ButtonKind
import com.example.drivecoach.ui.components.ChoiceGrid
import com.example.drivecoach.ui.components.ChoiceRow
import com.example.drivecoach.ui.components.CoachButton
import com.example.drivecoach.ui.components.CoachIconButton
import com.example.drivecoach.ui.components.CoachSlider
import com.example.drivecoach.ui.components.SectionCard
import com.example.drivecoach.ui.components.SteeringWheel
import com.example.drivecoach.ui.components.StepperSlider
import com.example.drivecoach.ui.components.SwitchRow
import com.example.drivecoach.ui.components.steerLabel
import com.example.drivecoach.ui.theme.CoachColors
import com.example.drivecoach.ui.theme.CoachSizes
import com.example.drivecoach.vehicle.DrivingState
import com.example.drivecoach.vehicle.Signal
import com.example.drivecoach.vehicle.SignalAvailability
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/** Scenario presets shown as choice buttons: type + stall angle. */
private val SCENARIOS = listOf(
    "후진 직각" to (ParkingType.REVERSE_PERPENDICULAR to 90),
    "평행" to (ParkingType.PARALLEL to 90),
    "전진 직각" to (ParkingType.FORWARD_PERPENDICULAR to 90),
    "사선 45°" to (ParkingType.ANGLED to 45),
    "사선 60°" to (ParkingType.ANGLED to 60),
)

@Composable
fun ParkingScreen(vm: MainViewModel) {
    val spec by vm.parkingSpec.collectAsStateWithLifecycle()
    val plan by vm.plan.collectAsStateWithLifecycle()
    val geometry by vm.geometry.collectAsStateWithLifecycle()
    val live by vm.live.collectAsStateWithLifecycle()
    val restricted by vm.restricted.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val trips by vm.trips.collectAsStateWithLifecycle()

    val current = plan?.takeIf { it.spec == spec && it.geometry == geometry }
    val timeline = remember(current) { current?.let { Timeline.of(it) } }
    var t by remember(current) { mutableFloatStateOf(0f) }
    var playing by remember(current) { mutableStateOf(false) }

    LaunchedEffect(playing, timeline) {
        val tl = timeline ?: return@LaunchedEffect
        if (!playing) return@LaunchedEffect
        if (t >= tl.total) t = 0f
        var last = withFrameNanos { it }
        while (playing && t < tl.total) {
            val now = withFrameNanos { it }
            t = min(tl.total, t + (now - last) / 1e9f)
            last = now
        }
        playing = false
    }

    // P-gear practice: the real steering wheel (or a slider when there is no steering signal).
    var practice by remember { mutableStateOf(false) }
    var manualSteer by remember { mutableFloatStateOf(0f) }
    val realSteeringUsable = DrivingState.canPractice(restricted, live)
    val calibrated = settings.steeringFullLockRaw
    val liveSteer = if (calibrated != null && live.steerRaw != null) (live.steerRaw!! / calibrated).coerceIn(-1f, 1f) else live.steer
    val practiceSteer = if (realSteeringUsable && liveSteer != null) liveSteer else manualSteer

    val seek: (Float) -> Unit = {
        playing = false
        t = it
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 1100.dp
        val controls: @Composable (Modifier) -> Unit = { m -> ScenarioControls(spec, geometry.name, geometry.turningRadiusM, m) { vm.setParkingSpec(it) } }
        val canvas: @Composable (Modifier) -> Unit = { m ->
            if (current == null || timeline == null) {
                Column(m, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    CircularProgressIndicator()
                    Text("경로를 계산하는 중…", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 12.dp))
                }
            } else {
                val frame = timeline.at(t)
                val overlay = if (practice) {
                    PracticeOverlay(
                        forward = ParkingPlanner.preview(current.startPose, geometry, practiceSteer.toDouble(), Gear.DRIVE),
                        reverse = ParkingPlanner.preview(current.startPose, geometry, practiceSteer.toDouble(), Gear.REVERSE),
                    )
                } else {
                    null
                }
                val pose = if (practice) current.startPose else current.poses[frame.poseIndex].pose
                ParkingCanvas(current, pose, m.clip(RoundedCornerShape(20.dp)), overlay)
            }
        }
        // Playback sits right under the drawing it drives, at the bottom of the window near the driver's hand.
        val playback: @Composable (Modifier) -> Unit = { m ->
            if (!practice && timeline != null) PlaybackBar(timeline, t, playing, onPlayPause = { playing = !playing }, onSeek = seek, modifier = m)
        }
        val side: @Composable (Modifier) -> Unit = { m ->
            StepsPanel(
                plan = current,
                timeline = timeline,
                t = t,
                lockTurns = geometry.lockTurns,
                onSeek = seek,
                practice = practice,
                onPractice = {
                    practice = it
                    playing = false
                },
                practiceSteer = practiceSteer,
                realSteering = realSteeringUsable,
                steeringAvailable = live.availability(Signal.STEERING) == SignalAvailability.AVAILABLE,
                parked = live.isParked,
                manualSteer = manualSteer,
                onManualSteer = { manualSteer = it },
                onCalibrate = { vm.calibrateSteering(live.steerRaw) },
                recentParking = trips.mapNotNull { it.parking }.take(10).let { list ->
                    if (list.isEmpty()) null else "최근 실제 주차 ${list.size}회 · 평균 ${list.map { it.durationS }.average().roundToInt()}초 · 전·후진 전환 평균 ${String.format(Locale.KOREA, "%.1f", list.map { it.directionChanges }.average())}회"
                },
                modifier = m,
            )
        }
        if (wide) {
            Row(Modifier.fillMaxSize().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                controls(Modifier.width(360.dp).fillMaxHeight().verticalScroll(rememberScrollState()))
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    canvas(Modifier.weight(1f).fillMaxWidth())
                    playback(Modifier.fillMaxWidth())
                }
                side(Modifier.width(400.dp).fillMaxHeight().verticalScroll(rememberScrollState()))
            }
        } else {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                controls(Modifier.fillMaxWidth())
                canvas(Modifier.fillMaxWidth().height(420.dp))
                playback(Modifier.fillMaxWidth())
                side(Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun ScenarioControls(spec: ScenarioSpec, carName: String, turningRadiusM: Double, modifier: Modifier, onChange: (ScenarioSpec) -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("주차 연습", style = MaterialTheme.typography.headlineMedium)
        val chosen = SCENARIOS.firstOrNull { (_, ta) -> spec.type == ta.first && (ta.first != ParkingType.ANGLED || spec.angleDeg == ta.second) }?.second
        ChoiceGrid(SCENARIOS, chosen, columns = 2) { (type, angle) ->
            onChange(ScenarioSpec.defaults(type, angle).copy(side = spec.side, neighbors = spec.neighbors))
        }
        ChoiceRow(Side.entries.map { it.label to it }, spec.side) { onChange(spec.copy(side = it)) }
        SwitchRow(if (spec.type == ParkingType.PARALLEL) "앞뒤 차 있음" else "옆 차 있음", null, spec.neighbors, { onChange(spec.copy(neighbors = it)) })
        val parallel = spec.type == ParkingType.PARALLEL
        MeterStepper("칸 폭", spec.stallWidthM, if (parallel) 1.8f..3.0f else 2.2f..3.3f) { onChange(spec.copy(stallWidthM = it)) }
        MeterStepper("칸 길이", spec.stallLengthM, if (parallel) 5.0f..9.0f else 4.5f..6.5f) { onChange(spec.copy(stallLengthM = it)) }
        MeterStepper(if (parallel) "차로 폭" else "통로 폭", spec.aisleWidthM, 3.0f..9.0f) { onChange(spec.copy(aisleWidthM = it)) }
        Text(
            "차량: $carName · 최소회전반경 ${String.format(Locale.KOREA, "%.2f", turningRadiusM)} m",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 0.1 m steps; the planner reruns on a − / + tap or when the slider is released, not on every drag step. */
@Composable
private fun MeterStepper(label: String, value: Double, range: ClosedFloatingPointRange<Float>, onCommit: (Double) -> Unit) {
    StepperSlider(label, value.toFloat(), range, step = 0.1f, format = { String.format(Locale.KOREA, "%.1f m", it) }) { onCommit(it.toDouble()) }
}

/** Previous / play-pause (largest, the most used) / next, the scrubber, then back to the start. */
@Composable
private fun PlaybackBar(timeline: Timeline, t: Float, playing: Boolean, onPlayPause: () -> Unit, onSeek: (Float) -> Unit, modifier: Modifier) {
    val step = timeline.at(t).step
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CoachSizes.Gap)) {
        CoachIconButton(Icons.Filled.SkipPrevious, "이전 단계", onClick = { onSeek(timeline.stepStart(step - 1)) })
        CoachIconButton(
            if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            if (playing) "일시정지" else "재생",
            onClick = onPlayPause,
            size = CoachSizes.Large,
            primary = true,
        )
        CoachIconButton(Icons.Filled.SkipNext, "다음 단계", onClick = { onSeek(timeline.stepStart(step + 1)) })
        CoachSlider(t, onSeek, 0f..timeline.total, Modifier.weight(1f))
        CoachIconButton(Icons.Filled.Replay, "처음부터", onClick = { onSeek(0f) })
    }
}

@Composable
private fun StepsPanel(
    plan: ParkingPlan?,
    timeline: Timeline?,
    t: Float,
    lockTurns: Double,
    onSeek: (Float) -> Unit,
    practice: Boolean,
    onPractice: (Boolean) -> Unit,
    practiceSteer: Float,
    realSteering: Boolean,
    steeringAvailable: Boolean,
    parked: Boolean,
    manualSteer: Float,
    onManualSteer: (Float) -> Unit,
    onCalibrate: () -> Unit,
    recentParking: String?,
    modifier: Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (plan == null || timeline == null) return@Column
        val frame = timeline.at(t)
        val step = plan.steps[frame.step]

        // First, and in the same place in both modes, so practice can always be switched off again.
        SectionCard(null) {
            SwitchRow("핸들 연습 (P단)", "정차 중 핸들을 돌리면 예상 경로를 보여 줘요", practice, onPractice)
        }

        if (!plan.feasible) {
            SectionCard("한 번에 들어가기 어려워요") {
                Text(plan.requirement ?: "", style = MaterialTheme.typography.bodyLarge, color = CoachColors.Moderate)
                Text("아래는 가장 근접한 경로예요 (${plan.closestLabel}과 ${String.format(Locale.KOREA, "%.2f", plan.clearanceM)} m).", style = MaterialTheme.typography.bodyMedium)
            }
        }

        if (practice) {
            PracticeCard(plan, practiceSteer, lockTurns, realSteering, steeringAvailable, parked, manualSteer, onManualSteer, onCalibrate)
        } else {
            SectionCard(null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SteeringWheel(frame.steer, lockTurns, size = 150.dp, highlight = if (step.gear == Gear.REVERSE) CoachColors.Reverse else CoachColors.Drive)
                    Column(Modifier.padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("${frame.step + 1}/${plan.steps.size} 단계", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(step.title, style = MaterialTheme.typography.titleLarge)
                    }
                }
                Text(step.detail, style = MaterialTheme.typography.bodyLarge)
            }
            SectionCard("순서") {
                // Each step is a full-width 64 dp row that jumps the animation there.
                plan.steps.forEachIndexed { i, s ->
                    val active = i == frame.step
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = CoachSizes.Small)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .clickable { onSeek(timeline.stepStart(i)) }
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(
                            "${i + 1}. ${s.title}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }

        recentParking?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Text(
            "학습용 시뮬레이션이에요. 실제 주차할 때는 후방 카메라·센서와 주변을 직접 확인하세요. 정차 중 핸들을 오래 돌리면 타이어가 닳을 수 있어요.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PracticeCard(
    plan: ParkingPlan,
    steer: Float,
    lockTurns: Double,
    realSteering: Boolean,
    steeringAvailable: Boolean,
    parked: Boolean,
    manualSteer: Float,
    onManualSteer: (Float) -> Unit,
    onCalibrate: () -> Unit,
) {
    SectionCard("핸들 감각 연습") {
        val target = plan.targetSteer.toFloat()
        val gearWord = if (plan.targetGear == Gear.REVERSE) "후진" else "전진"
        SteeringWheel(steer, lockTurns, Modifier.fillMaxWidth(), size = 170.dp)
        Text("목표: ${steerLabel(target)} · $gearWord", style = MaterialTheme.typography.titleMedium)
        val diff = target - steer
        Text(
            when {
                abs(diff) < 0.08f -> "좋아요! 이 각도로 ${gearWord}하면 계획한 경로를 따라가요."
                diff > 0 -> "핸들을 조금 더 오른쪽으로 돌려 보세요."
                else -> "핸들을 조금 더 왼쪽으로 돌려 보세요."
            },
            style = MaterialTheme.typography.bodyLarge,
            color = if (abs(diff) < 0.08f) CoachColors.Gentle else CoachColors.Moderate,
        )
        Text("점선은 지금 핸들 각도로 6 m 움직였을 때의 경로예요 (파랑 전진, 노랑 후진).", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (realSteering) {
            CoachButton("지금 값을 '끝까지'로 보정", onCalibrate, Modifier.fillMaxWidth(), kind = ButtonKind.Secondary)
        } else {
            Text(
                when {
                    !steeringAvailable -> "이 차량의 조향 신호를 받을 수 없어 슬라이더로 연습해요."
                    !parked -> "실제 핸들 연동은 P단으로 정차했을 때만 써요. 지금은 슬라이더로 연습해요."
                    else -> "슬라이더로 연습해요."
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            CoachSlider(manualSteer, onManualSteer, -1f..1f, Modifier.fillMaxWidth())
        }
    }
}

/**
 * Animation timeline: before each move the wheel turns while the car stands still (that is how the
 * manoeuvres are taught), then the car moves at walking pace.
 */
private class Timeline(private val segments: List<Seg>, val total: Float) {

    class Seg(val step: Int, val start: Float, val duration: Float, val turning: Boolean, val fromSteer: Float, val toSteer: Float, val firstPose: Int, val lastPose: Int)

    class Frame(val step: Int, val poseIndex: Int, val steer: Float)

    fun at(t: Float): Frame {
        val seg = segments.lastOrNull { it.start <= t } ?: segments.first()
        val f = if (seg.duration <= 0f) 1f else ((t - seg.start) / seg.duration).coerceIn(0f, 1f)
        return if (seg.turning) {
            Frame(seg.step, seg.firstPose, seg.fromSteer + (seg.toSteer - seg.fromSteer) * f)
        } else {
            Frame(seg.step, seg.firstPose + ((seg.lastPose - seg.firstPose) * f).roundToInt(), seg.toSteer)
        }
    }

    fun stepStart(step: Int): Float {
        val s = step.coerceIn(0, segments.last().step)
        return segments.first { it.step == s }.start
    }

    companion object {
        fun of(plan: ParkingPlan): Timeline {
            val segs = mutableListOf<Seg>()
            var t = 0f
            var wheel = 0f
            plan.steps.forEachIndexed { i, s ->
                val target = s.steer.toFloat()
                if (abs(target - wheel) > 0.02f) {
                    segs += Seg(i, t, TURN_S * abs(target - wheel).coerceAtLeast(0.3f), true, wheel, target, s.firstPose, s.firstPose)
                    t = segs.last().start + segs.last().duration
                    wheel = target
                }
                val speed = if (i == 0) APPROACH_MPS else MANOEUVRE_MPS
                val d = if (s.lengthM <= 0.0) PAUSE_S else maxOf(0.6f, (s.lengthM / speed).toFloat())
                segs += Seg(i, t, d, false, wheel, wheel, s.firstPose, s.lastPose)
                t += d
            }
            return Timeline(segs, t)
        }

        const val TURN_S = 1.0f
        const val APPROACH_MPS = 2.0
        const val MANOEUVRE_MPS = 1.0
        const val PAUSE_S = 0.6f
    }
}
