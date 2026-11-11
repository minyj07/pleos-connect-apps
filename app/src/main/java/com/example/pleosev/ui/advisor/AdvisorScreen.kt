package com.example.pleosev.ui.advisor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EvStation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pleosev.advisor.AdvisorCandidate
import com.example.pleosev.advisor.AdvisorState
import com.example.pleosev.advisor.background.AlertTexts
import com.example.pleosev.advisor.domain.NaviAction
import com.example.pleosev.advisor.domain.NeedKind
import com.example.pleosev.advisor.domain.PlannedStopVerdict
import com.example.pleosev.advisor.domain.Severity
import com.example.pleosev.advisor.domain.StopRole
import com.example.pleosev.data.settings.AdvisorPrefs
import com.example.pleosev.navi.NaviConnection
import com.example.pleosev.navi.NaviState
import com.example.pleosev.shared.api.FreshnessLevel
import com.example.pleosev.shared.api.StationSummary
import com.example.pleosev.ui.StatusLabel
import com.example.pleosev.ui.theme.ButtonSize
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * Charging advisor screen. While driving (CarUxRestrictions) it keeps to at most three cards with one
 * large button each; details, secondary actions and settings appear only when parked.
 * [onAllowNotifications] asks for POST_NOTIFICATIONS (alerts while the navi covers the app).
 */
@Composable
fun AdvisorScreen(
    viewModel: AdvisorViewModel,
    onBack: () -> Unit,
    notificationsAllowed: Boolean,
    onAllowNotifications: () -> Unit,
) {
    val state by viewModel.advisor.collectAsStateWithLifecycle()
    val vehicle by viewModel.vehicle.collectAsStateWithLifecycle()
    val navi by viewModel.navi.collectAsStateWithLifecycle()
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()
    val driving = vehicle.isDriving

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 28.dp, vertical = 20.dp)
                .then(if (driving) Modifier else Modifier.verticalScroll(rememberScrollState())),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Header(navi, onBack, onRefresh = viewModel::refresh)
            NeedSummary(state, socPercent = vehicle.socPercent, rangeKm = vehicle.rangeRemainingKm)
            when {
                !state.consent -> ConsentCard(
                    driving,
                    onAgree = {
                        viewModel.setConsent(true)
                        if (!notificationsAllowed) onAllowNotifications()
                    },
                )
                state.candidates.isEmpty() && state.loading -> Loading()
                state.problem != null && state.candidates.isEmpty() ->
                    Problem(state.problem.orEmpty(), onNaviChargers = viewModel::showNaviChargers)
                else -> {
                    // A refresh failed after an earlier answer: the last cards stay (each shows its age), with the reason.
                    state.problem?.let {
                        Text("$it · 마지막으로 받은 정보예요", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.tertiary)
                    }
                    Candidates(state.candidates, driving, onAction = viewModel::apply)
                }
            }
            state.lastAction?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary) }
            if (!driving) {
                Settings(
                    prefs,
                    notificationsAllowed = notificationsAllowed,
                    onReserve = viewModel::setReserveSocPct,
                    onMinKw = viewModel::setMinKw,
                    onBackgroundWatch = viewModel::setBackgroundWatch,
                    onAllowNotifications = onAllowNotifications,
                    onRevoke = { viewModel.setConsent(false) },
                )
            }
        }
    }
}

@Composable
private fun Header(navi: NaviState, onBack: () -> Unit, onRefresh: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        HeaderIconButton(Icons.AutoMirrored.Filled.ArrowBack, "대시보드", onBack)
        Text("충전 도우미", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f), maxLines = 1)
        val (label, color) = when {
            navi.demo -> "데모 내비" to MaterialTheme.colorScheme.tertiary
            navi.connection == NaviConnection.CONNECTED -> "내비 연결됨" to MaterialTheme.colorScheme.primary
            navi.connection == NaviConnection.CONNECTING -> "내비 연결 중…" to MaterialTheme.colorScheme.secondary
            else -> "내비 없음" to MaterialTheme.colorScheme.error
        }
        StatusLabel(label, color)
        HeaderIconButton(Icons.Filled.Refresh, "새로고침", onRefresh)
    }
}

/** Filled so the whole [ButtonSize.Medium] touch area is visible, not just the icon in its middle. */
@Composable
private fun HeaderIconButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    FilledTonalIconButton(
        onClick = onClick,
        modifier = Modifier.size(ButtonSize.Medium),
        colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(ButtonSize.Icon))
    }
}

@Composable
private fun NeedSummary(state: AdvisorState, socPercent: Int?, rangeKm: Float?) {
    val need = state.need
    val battery = listOfNotNull(socPercent?.let { "배터리 $it%" }, rangeKm?.let { "주행 가능 ${it.toInt()} km" }).joinToString(" · ")
    val (title, detail) = when (need.kind) {
        NeedKind.NONE -> "지금은 충전이 필요하지 않아요" to battery
        NeedKind.ON_ROUTE -> "목적지 도착 시 배터리가 부족할 수 있어요" to
            listOfNotNull(
                // The range estimate goes below zero when the battery runs out on the way ("-109 %" in E2E).
                need.arrivalSocPct?.let { if (it < 0.0) "도착 전 소진 예상" else "도착 예상 ${it.toInt()}%" },
                "예비 ${need.reserveSocPct}%",
                battery.ifEmpty { null },
            ).joinToString(" · ")
        NeedKind.PLANNED_STOP -> plannedStopText(state) to battery
        NeedKind.NEARBY -> "배터리가 얼마 남지 않았어요" to battery
    }
    val urgent = state.attention == Severity.URGENT
    val color = when {
        urgent -> MaterialTheme.colorScheme.error
        state.attention >= Severity.RECOMMENDED -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurface
    }
    Column {
        Text(if (urgent) "긴급 · $title" else title, style = MaterialTheme.typography.titleLarge, color = color)
        if (detail.isNotEmpty()) Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun plannedStopText(state: AdvisorState): String {
    val name = state.plannedStop?.name ?: state.plannedStopName ?: "충전소"
    val counts = state.plannedStop?.compatible
    val whose = when (state.plannedStopRole) {
        StopRole.DESTINATION -> "목적지"
        StopRole.WAYPOINT -> "경유지"
        StopRole.NAVI, null -> "내비가 고른"
    }
    return when (state.plannedVerdict) {
        PlannedStopVerdict.OK -> "$whose $name: 급속 ${counts?.available}/${counts?.total} 이용 가능"
        PlannedStopVerdict.BUSY -> "$whose $name: 지금 빈 충전기가 없어요"
        PlannedStopVerdict.UNKNOWN -> "$whose $name: 지금 상태를 확인할 수 없어요"
        PlannedStopVerdict.NOT_FOUND -> "$whose 충전소($name)의 실시간 정보를 찾지 못했어요"
        PlannedStopVerdict.NO_MATCHING_CHARGER -> "$whose $name: 설정한 출력 이상인 충전기가 없어요"
        PlannedStopVerdict.UNREACHABLE -> "$whose $name: 배터리가 부족해 갈 수 없어요"
        null -> "${AlertTexts.stopLabel(state)}를 확인하고 있어요"
    }
}

@Composable
private fun ConsentCard(driving: Boolean, onAgree: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("실시간 충전소 추천을 쓰려면 동의가 필요해요", style = MaterialTheme.typography.titleMedium)
            Text(
                "차량의 현재 위치(약 100 m 단위)와 목적지를 충전 정보 서버로 보내 주변 충전소의 실시간 상태를 확인합니다. " +
                    "서버는 위치를 저장하지 않으며, 설정에서 언제든 동의를 철회할 수 있습니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (driving) {
                Text("정차 후 동의할 수 있어요", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.tertiary)
            } else {
                Button(onClick = onAgree, modifier = Modifier.widthIn(min = 280.dp).heightIn(min = ButtonSize.Medium)) {
                    Text("동의하고 사용", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun Loading() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CircularProgressIndicator()
        Text("주변 충전소 상태를 확인하고 있어요", style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun Problem(problem: String, onNaviChargers: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(problem, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.tertiary)
        OutlinedButton(onClick = onNaviChargers, modifier = Modifier.heightIn(min = ButtonSize.Medium)) {
            Icon(Icons.Filled.EvStation, contentDescription = null, modifier = Modifier.size(ButtonSize.Icon))
            Spacer(Modifier.width(12.dp))
            Text("내비 충전소 목록 보기", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun Candidates(candidates: List<AdvisorCandidate>, driving: Boolean, onAction: (NaviAction, StationSummary) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val wide = maxWidth >= 840.dp
        if (wide) {
            // Side by side the cards share one height, so their main buttons line up along the bottom.
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                candidates.forEach { CandidateCard(it, driving, onAction, Modifier.weight(1f).fillMaxHeight(), buttonsAtBottom = true) }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                candidates.forEach { CandidateCard(it, driving, onAction, Modifier.fillMaxWidth(), buttonsAtBottom = false) }
            }
        }
    }
}

@Composable
private fun CandidateCard(
    item: AdvisorCandidate,
    driving: Boolean,
    onAction: (NaviAction, StationSummary) -> Unit,
    modifier: Modifier,
    buttonsAtBottom: Boolean,
) {
    val c = item.candidate
    val s = c.station
    // "N분 전 확인" has to age even when nothing else changes (failed refreshes keep the same cards).
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(AGE_TICK_MS)
            value = System.currentTimeMillis()
        }
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = modifier) {
        Column(
            Modifier.padding(20.dp).then(if (buttonsAtBottom) Modifier.fillMaxHeight() else Modifier),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(s.name, style = MaterialTheme.typography.titleLarge, maxLines = if (driving) 1 else 2, overflow = TextOverflow.Ellipsis)
            Text(
                "급속 대기 ${s.compatible.available}/${s.compatible.total}" + (s.maxKw?.let { " · $it kW" } ?: ""),
                style = MaterialTheme.typography.titleMedium,
                color = availabilityColor(s),
            )
            val trip = listOfNotNull(
                c.estDetourMin?.let { "+${it.toInt()}분" },
                c.estArrivalSocPct?.let { "도착 ${it.toInt()}%" },
                if (driving) null else "${c.estRoadKm.toInt()} km",
            ).joinToString(" · ")
            if (trip.isNotEmpty()) Text(trip, style = MaterialTheme.typography.bodyLarge)
            Text(freshnessText(s, now), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!driving) {
                Text(s.operatorName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                c.warnings.firstOrNull()?.let { Text(warningText(it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.tertiary) }
            }
            Spacer(if (buttonsAtBottom) Modifier.weight(1f) else Modifier.height(4.dp))
            // Secondary actions (parked only) go above the main one, so the main button always ends the card.
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!driving) {
                    item.actions.drop(1).forEach { action ->
                        OutlinedButton(
                            onClick = { onAction(action, s) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = ButtonSize.Small),
                        ) { Text(actionLabel(action)) }
                        if (action is NaviAction.AddWaypoint && !action.recognizedAsCharger) {
                            Text(
                                "좌표로 추가하면 내비가 충전소를 하나 더 넣을 수 있어요",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                item.actions.firstOrNull()?.let { primary ->
                    Button(
                        onClick = { onAction(primary, s) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = if (driving) ButtonSize.Large else ButtonSize.Medium),
                    ) {
                        Text(
                            actionLabel(primary),
                            style = if (driving) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Settings(
    prefs: AdvisorPrefs,
    notificationsAllowed: Boolean,
    onReserve: (Int) -> Unit,
    onMinKw: (Int) -> Unit,
    onBackgroundWatch: (Boolean) -> Unit,
    onAllowNotifications: () -> Unit,
    onRevoke: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("설정", style = MaterialTheme.typography.titleMedium)
        ChoiceRow("도착 예비 배터리", listOf(10, 15, 20), prefs.reserveSocPct, onReserve) { "$it%" }
        ChoiceRow("최소 충전 출력", listOf(50, 100, 200), prefs.minKw, onMinKw) { "$it kW+" }
        ChoiceRow("내비 화면에서도 감시", listOf(true, false), prefs.backgroundWatch, onBackgroundWatch) { if (it) "켜기" else "끄기" }
        if (prefs.backgroundWatch && !notificationsAllowed) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    "알림이 꺼져 있어 내비 화면에서는 알려 드릴 수 없어요",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.weight(1f, fill = false),
                )
                OutlinedButton(onClick = onAllowNotifications, modifier = Modifier.heightIn(min = ButtonSize.Small)) { Text("알림 허용") }
            }
        }
        if (prefs.locationConsent) {
            OutlinedButton(onClick = onRevoke, modifier = Modifier.heightIn(min = ButtonSize.Small)) { Text("위치 전송 동의 철회") }
        }
    }
}

/**
 * One setting as a segmented choice (Pleos segmented menu): equally wide segments, [ButtonSize.Small] tall.
 * The chosen segment is tonal green; M3's default (secondaryContainer) is not in our scheme and showed baseline purple.
 */
@Composable
private fun <T> ChoiceRow(label: String, options: List<T>, selected: T, onSelect: (T) -> Unit, text: (T) -> String) {
    val colors = SegmentedButtonDefaults.colors(
        activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
        activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        activeBorderColor = MaterialTheme.colorScheme.primary,
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.width(240.dp))
        SingleChoiceSegmentedButtonRow(Modifier.width(SEGMENT_WIDTH * options.size)) {
            options.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                    modifier = Modifier.height(ButtonSize.Small),
                    colors = colors,
                ) { Text(text(option), style = MaterialTheme.typography.titleMedium) }
            }
        }
    }
}

private val SEGMENT_WIDTH = 160.dp

@Composable
private fun availabilityColor(s: StationSummary): Color = when {
    s.compatible.available >= 2 -> MaterialTheme.colorScheme.primary
    s.compatible.available == 1 -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.error
}

private const val AGE_TICK_MS = 30_000L

private fun freshnessText(s: StationSummary, nowMs: Long): String {
    val checked = s.freshness.serverCheckedAtMs ?: return "상태 확인 시각 모름"
    val minutes = ((nowMs - checked) / 60_000L).coerceAtLeast(0)
    return when (s.freshness.level) {
        FreshnessLevel.STALE -> "상태 정보가 오래됐어요"
        else -> if (minutes == 0L) "방금 확인" else String.format(Locale.KOREA, "%d분 전 확인", minutes)
    }
}

private fun actionLabel(action: NaviAction): String = when (action) {
    is NaviAction.RouteTo -> "이 충전소로 안내"
    is NaviAction.AddWaypoint -> if (action.recognizedAsCharger) "경유지로 추가" else "좌표로 경유지 추가"
    is NaviAction.SearchInNavi -> "내비에서 찾아 추가"
}

private fun warningText(code: String): String = when (code) {
    "STATUS_STALE" -> "상태 정보가 오래됐어요"
    "STATUS_DELAYED" -> "상태 갱신이 늦어지고 있어요"
    "NO_AVAILABLE_NOW" -> "지금 빈 충전기가 없어요"
    "STATUS_UNKNOWN" -> "충전기 상태를 확인할 수 없어요"
    "LOW_ARRIVAL_MARGIN" -> "도착 배터리 여유가 적어요"
    "AVOIDED_OPERATOR" -> "피하는 운영사예요"
    "RESTRICTED_ACCESS" -> "이용 제한이 있어요"
    else -> code
}
