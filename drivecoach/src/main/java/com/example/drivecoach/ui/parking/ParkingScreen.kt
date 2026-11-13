package com.example.drivecoach.ui.parking

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.drivecoach.model.Gear
import com.example.drivecoach.parking.ParkingPlan
import com.example.drivecoach.parking.ParkingPlanner
import com.example.drivecoach.parking.ParkingType
import com.example.drivecoach.parking.ScenarioSpec
import com.example.drivecoach.parking.Side
import com.example.drivecoach.ui.MainViewModel
import com.example.drivecoach.ui.components.BoxShape
import com.example.drivecoach.ui.components.CardShape
import com.example.drivecoach.ui.components.ChoiceGrid
import com.example.drivecoach.ui.components.CoachCard
import com.example.drivecoach.ui.components.CoachIcons
import com.example.drivecoach.ui.components.CoachSlider
import com.example.drivecoach.ui.components.CoachSwitch
import com.example.drivecoach.ui.components.Dot
import com.example.drivecoach.ui.components.Hairline
import com.example.drivecoach.ui.components.LocalWideLayout
import com.example.drivecoach.ui.components.OutlinePill
import com.example.drivecoach.ui.components.PageTitle
import com.example.drivecoach.ui.components.RoundIconButton
import com.example.drivecoach.ui.components.Segmented
import com.example.drivecoach.ui.components.SegmentedStyle
import com.example.drivecoach.ui.components.SteeringWheel
import com.example.drivecoach.ui.components.StepperSlider
import com.example.drivecoach.ui.components.SwitchRow
import com.example.drivecoach.ui.components.T
import com.example.drivecoach.ui.components.TagPill
import com.example.drivecoach.ui.components.cardSurface
import com.example.drivecoach.ui.components.steerLabel
import com.example.drivecoach.ui.components.steerTurns
import com.example.drivecoach.ui.components.tap
import com.example.drivecoach.ui.theme.CoachColors
import com.example.drivecoach.ui.theme.CoachTheme
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

/** Narrow window: the chips end 20 + 45 px below the card's top edge; the drawing starts a little under them. */
private val NarrowCanvasTopInset = 72.dp

private fun scenarioOf(spec: ScenarioSpec) =
    SCENARIOS.firstOrNull { (_, ta) -> spec.type == ta.first && (ta.first != ParkingType.ANGLED || spec.angleDeg == ta.second) }

/**
 * 주차 연습: `main { padding: 40px 48px 40px 40px; grid-template-columns: 330px 1fr 390px; gap: 22px }`:
 * the scenario controls, the drawing with its transport row, and the steps. In the narrow window the three
 * parts are stacked in one scrolling column.
 */
@Composable
fun ParkingScreen(vm: MainViewModel) {
    val spec by vm.parkingSpec.collectAsStateWithLifecycle()
    val plan by vm.plan.collectAsStateWithLifecycle()
    val geometry by vm.geometry.collectAsStateWithLifecycle()
    val live by vm.live.collectAsStateWithLifecycle()
    val restricted by vm.restricted.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val trips by vm.trips.collectAsStateWithLifecycle()
    val c = CoachTheme.colors

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

    val controls: @Composable (Modifier) -> Unit = { m -> ScenarioControls(spec, geometry.name, geometry.turningRadiusM, m) { vm.setParkingSpec(it) } }
    // The drawing card: `border-radius: 28px; overflow: hidden`, the scenario chips 24 px / 20 px from its corner.
    // In the wide window the card is tall and the drawing sits well below the chips. The narrow window's card
    // is short, so there the drawing starts under the chips instead of running through them.
    val canvasTopInset = if (LocalWideLayout.current) 0.dp else NarrowCanvasTopInset
    val canvas: @Composable (Modifier) -> Unit = { m ->
        Box(m.cardSurface().clip(CardShape), contentAlignment = Alignment.Center) {
            if (current == null || timeline == null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    CircularProgressIndicator(Modifier.size(64.dp), color = c.acc, strokeWidth = 6.dp, trackColor = c.track)
                    T("경로를 계산하는 중…", 21, color = c.ink2)
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
                ParkingCanvas(current, pose, Modifier.fillMaxSize().padding(top = canvasTopInset), overlay)
                Row(
                    Modifier.align(Alignment.TopStart).padding(start = 24.dp + Hairline, top = 20.dp + Hairline),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    TagPill(scenarioOf(current.spec)?.first ?: current.spec.type.label)
                    TagPill(current.spec.side.label, accent = false)
                }
            }
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
    if (LocalWideLayout.current) {
        Row(
            Modifier.fillMaxSize().padding(start = 40.dp, top = 40.dp, end = 48.dp, bottom = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            controls(Modifier.width(330.dp).fillMaxHeight().verticalScroll(rememberScrollState()))
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                canvas(Modifier.weight(1f).fillMaxWidth())
                playback(Modifier.fillMaxWidth())
            }
            side(Modifier.width(390.dp).fillMaxHeight().verticalScroll(rememberScrollState()))
        }
    } else {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 20.dp, top = 40.dp, end = 24.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            controls(Modifier.fillMaxWidth())
            canvas(Modifier.fillMaxWidth().height(448.dp + NarrowCanvasTopInset))
            playback(Modifier.fillMaxWidth())
            side(Modifier.fillMaxWidth())
        }
    }
}

/**
 * Left column (gap 30): the title, "주차 방식" as a two-column grid, "목표 칸" as a segmented control, the
 * neighbours switch, three steppers and the car the simulation uses.
 */
@Composable
private fun ScenarioControls(spec: ScenarioSpec, carName: String, turningRadiusM: Double, modifier: Modifier, onChange: (ScenarioSpec) -> Unit) {
    val c = CoachTheme.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(30.dp)) {
        PageTitle("주차 연습", size = 46)
        Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            T("주차 방식", 20, weight = FontWeight.W700, color = c.ink2)
            ChoiceGrid(SCENARIOS, scenarioOf(spec)?.second, columns = 2) { (type, angle) ->
                onChange(ScenarioSpec.defaults(type, angle).copy(side = spec.side, neighbors = spec.neighbors))
            }
            T("목표 칸", 20, Modifier.padding(top = 28.dp), weight = FontWeight.W700, color = c.ink2)
            Segmented(Side.entries.map { it.label to it }, spec.side, SegmentedStyle.Side, Modifier.fillMaxWidth()) { onChange(spec.copy(side = it)) }
        }
        Column(Modifier.padding(start = 4.dp, top = 20.dp, end = 4.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            val parallel = spec.type == ParkingType.PARALLEL
            SwitchRow(if (parallel) "앞뒤 차 있음" else "옆 차 있음", null, spec.neighbors, { onChange(spec.copy(neighbors = it)) }, titleSize = 22)
            MeterStepper("칸 폭", spec.stallWidthM, if (parallel) 1.8f..3.0f else 2.2f..3.3f) { onChange(spec.copy(stallWidthM = it)) }
            MeterStepper("칸 길이", spec.stallLengthM, if (parallel) 5.0f..9.0f else 4.5f..6.5f) { onChange(spec.copy(stallLengthM = it)) }
            MeterStepper(if (parallel) "차로 폭" else "통로 폭", spec.aisleWidthM, 3.0f..9.0f) { onChange(spec.copy(aisleWidthM = it)) }
        }
        T(
            "차량: $carName · 최소회전반경 ${String.format(Locale.KOREA, "%.2f", turningRadiusM)} m",
            18,
            Modifier.padding(horizontal = 8.dp),
            color = c.ink3,
        )
    }
}

/** 0.1 m steps; the planner reruns on a − / + tap or when the slider is released, not on every drag step. */
@Composable
private fun MeterStepper(label: String, value: Double, range: ClosedFloatingPointRange<Float>, onCommit: (Double) -> Unit) {
    StepperSlider(label, value.toFloat(), range, step = 0.1f, format = { String.format(Locale.KOREA, "%.1f m", it) }) { onCommit(it.toDouble()) }
}

/**
 * Transport row (gap 14): previous (84 px round), play / pause (104 px, accent), next, the step progress
 * (10 px bar and "N / M 단계"), back to the start. The bar fills step by step as in the design; touching or
 * dragging it still scrubs through the animation, each step taking an equal share of the bar's width.
 */
@Composable
private fun PlaybackBar(timeline: Timeline, t: Float, playing: Boolean, onPlayPause: () -> Unit, onSeek: (Float) -> Unit, modifier: Modifier) {
    val c = CoachTheme.colors
    val step = timeline.at(t).step
    val seek by rememberUpdatedState(onSeek)
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        RoundIconButton(CoachIcons.Prev, "이전 단계", 84.dp, 28.dp, { onSeek(timeline.stepStart(step - 1)) })
        RoundIconButton(if (playing) CoachIcons.Pause else CoachIcons.Play, if (playing) "일시정지" else "재생", 104.dp, 38.dp, onPlayPause, filled = true)
        RoundIconButton(CoachIcons.Next, "다음 단계", 84.dp, 28.dp, { onSeek(timeline.stepStart(step + 1)) })
        Column(Modifier.weight(1f).padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val shape = RoundedCornerShape(5.dp)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .pointerInput(timeline) {
                        detectTapGestures(onTap = { seek(timeline.timeAt(it.x / size.width)) })
                    }
                    .pointerInput(timeline) {
                        detectHorizontalDragGestures(onDragStart = { seek(timeline.timeAt(it.x / size.width)) }) { input, _ ->
                            input.consume()
                            seek(timeline.timeAt(input.position.x / size.width))
                        }
                    }
                    .clip(shape)
                    .background(c.track),
            ) {
                Box(Modifier.fillMaxWidth((step + 1f) / timeline.steps).fillMaxHeight().background(c.acc, shape))
            }
            T("${step + 1} / ${timeline.steps} 단계", 18, color = c.ink3)
        }
        RoundIconButton(CoachIcons.Reset, "처음부터", 84.dp, 30.dp, { onSeek(0f) })
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
    val c = CoachTheme.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        if (plan == null || timeline == null) return@Column
        val frame = timeline.at(t)
        val step = plan.steps[frame.step]

        // First, and in the same place in both modes, so practice can always be switched off again.
        // `padding: 24px 28px; gap: 18px`; the whole card toggles.
        val interaction = remember { MutableInteractionSource() }
        Row(
            Modifier
                .fillMaxWidth()
                .clip(CardShape)
                .toggleable(practice, interaction, indication = null, role = Role.Switch, onValueChange = onPractice)
                .cardSurface()
                .padding(horizontal = 28.dp + Hairline, vertical = 24.dp + Hairline),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                T("핸들 연습 (P단)", 22, weight = FontWeight.W700)
                T("정차 중 핸들을 돌리면 예상 경로를 보여줘요", 18, color = c.ink3, lh = 1.45f)
            }
            CoachSwitch(practice, null)
        }

        if (!plan.feasible) {
            CoachCard(Modifier.fillMaxWidth(), padV = 28.dp, padH = 28.dp, gap = 12.dp) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Dot(CoachColors.Amber, 14.dp)
                    T("한 번에 들어가기 어려워요", 22, weight = FontWeight.W700)
                }
                T(plan.requirement ?: "", 20, color = c.ink1, lh = 1.6f)
                T(
                    "아래는 가장 근접한 경로예요 (${plan.closestLabel}과 ${String.format(Locale.KOREA, "%.2f", plan.clearanceM)} m).",
                    18,
                    color = c.ink3,
                    lh = 1.45f,
                )
            }
        }

        if (practice) {
            PracticeCard(plan, practiceSteer, lockTurns, realSteering, steeringAvailable, parked, manualSteer, onManualSteer, onCalibrate)
        } else {
            // Step card: `padding: 28px; gap: 20px`; the 128 px wheel beside the step number and title.
            CoachCard(Modifier.fillMaxWidth(), padV = 28.dp, padH = 28.dp) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    SteeringWheel(frame.steer, lockTurns)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        // The step count, and (kept from before) about how many turns of the wheel this is.
                        T(listOfNotNull("${frame.step + 1}/${plan.steps.size} 단계", steerTurns(frame.steer, lockTurns)).joinToString(" · "), 18, color = c.ink3, nowrap = true)
                        T(step.title, 27, weight = FontWeight.W700, lh = 1.3f)
                    }
                }
                T(step.detail, 20, color = c.ink1, lh = 1.6f)
            }
            // "순서": `padding: 24px; gap: 10px`; each step is a row that jumps the animation there.
            CoachCard(Modifier.fillMaxWidth(), padV = 24.dp, padH = 24.dp, gap = 10.dp) {
                T("순서", 22, Modifier.padding(start = 6.dp, end = 6.dp, bottom = 4.dp), weight = FontWeight.W700)
                plan.steps.forEachIndexed { i, s ->
                    val active = i == frame.step
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .tap(BoxShape) { onSeek(timeline.stepStart(i)) }
                            .background(if (active) c.tint else c.tile, BoxShape)
                            .padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Box(Modifier.size(32.dp).background(if (active) c.acc else c.surface, CircleShape), contentAlignment = Alignment.Center) {
                            T("${i + 1}", 17, weight = FontWeight.W700, color = if (active) CoachColors.White else c.ink2, nowrap = true)
                        }
                        T(s.title, 21, Modifier.weight(1f), weight = if (active) FontWeight.W700 else FontWeight.W500, color = if (active) c.acc else c.ink)
                    }
                }
            }
        }

        Column(Modifier.padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            recentParking?.let { T(it, 17, weight = FontWeight.W700, color = c.ink1, lh = 1.5f) }
            T(
                "학습용 시뮬레이션이에요. 실제 주차할 때는 후방 카메라·센서와 주변을 직접 확인하세요. 정차 중 핸들을 오래 돌리면 타이어가 닳을 수 있어요.",
                17,
                color = c.ink3,
                lh = 1.5f,
            )
        }
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
    val c = CoachTheme.colors
    CoachCard(Modifier.fillMaxWidth(), padV = 28.dp, padH = 28.dp) {
        val target = plan.targetSteer.toFloat()
        val gearWord = if (plan.targetGear == Gear.REVERSE) "후진" else "전진"
        T("핸들 감각 연습", 22, weight = FontWeight.W700)
        SteeringWheel(steer, lockTurns, Modifier.fillMaxWidth(), showTurns = true)
        T("목표: ${steerLabel(target)} · $gearWord", 21, weight = FontWeight.W700)
        val diff = target - steer
        T(
            when {
                abs(diff) < 0.08f -> "좋아요! 이 각도로 ${gearWord}하면 계획한 경로를 따라가요."
                diff > 0 -> "핸들을 조금 더 오른쪽으로 돌려 보세요."
                else -> "핸들을 조금 더 왼쪽으로 돌려 보세요."
            },
            20,
            color = if (abs(diff) < 0.08f) CoachColors.ScoreGood else c.ink1,
            lh = 1.6f,
        )
        T("점선은 지금 핸들 각도로 6 m 움직였을 때의 경로예요 (파랑 전진, 노랑 후진).", 18, color = c.ink3, lh = 1.45f)
        if (realSteering) {
            OutlinePill("지금 값을 '끝까지'로 보정", onCalibrate, Modifier.fillMaxWidth())
        } else {
            T(
                when {
                    !steeringAvailable -> "이 차량의 조향 신호를 받을 수 없어 슬라이더로 연습해요."
                    !parked -> "실제 핸들 연동은 P단으로 정차했을 때만 써요. 지금은 슬라이더로 연습해요."
                    else -> "슬라이더로 연습해요."
                },
                18,
                color = c.ink2,
                lh = 1.45f,
            )
            CoachSlider(manualSteer, onManualSteer, -1f..1f)
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

    /** Number of steps of the plan. */
    val steps: Int get() = segments.last().step + 1

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

    /**
     * Time for a position on the step progress bar ([fraction] 0..1 of its width): every step owns an equal
     * share of the bar, so the filled part always reaches the finger.
     */
    fun timeAt(fraction: Float): Float {
        val position = fraction.coerceIn(0f, 1f) * steps
        val step = position.toInt().coerceAtMost(steps - 1)
        val start = stepStart(step)
        val end = if (step + 1 < steps) stepStart(step + 1) else total
        return start + (end - start) * (position - step)
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
