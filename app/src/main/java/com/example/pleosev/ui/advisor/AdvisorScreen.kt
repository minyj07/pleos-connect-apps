package com.example.pleosev.ui.advisor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
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
import com.example.pleosev.model.EvDashboardState
import com.example.pleosev.navi.NaviConnection
import com.example.pleosev.navi.NaviState
import com.example.pleosev.shared.api.FreshnessLevel
import com.example.pleosev.shared.api.StationSummary
import com.example.pleosev.ui.design.Chip
import com.example.pleosev.ui.design.ChipRow
import com.example.pleosev.ui.design.EvBackdrop
import com.example.pleosev.ui.design.EvDesignScreen
import com.example.pleosev.ui.design.EvIcon
import com.example.pleosev.ui.design.EvIcons
import com.example.pleosev.ui.design.EvText
import com.example.pleosev.ui.design.GradientButton
import com.example.pleosev.ui.design.GradientCard
import com.example.pleosev.ui.design.IllustrationBox
import com.example.pleosev.ui.design.LocalDesignScale
import com.example.pleosev.ui.design.PillButton
import com.example.pleosev.ui.design.RoundIconButton
import com.example.pleosev.ui.design.Segmented
import com.example.pleosev.ui.design.StationThumb
import com.example.pleosev.ui.design.StatusPill
import com.example.pleosev.ui.design.ToggleSwitch
import com.example.pleosev.ui.design.evSurface
import com.example.pleosev.ui.design.realDp
import com.example.pleosev.ui.theme.EvColors
import com.example.pleosev.ui.theme.EvWeight
import com.example.pleosev.ui.theme.evText
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * 충전 도우미 screen of the "EV Companion" design: the settings card on the left, the recommended stations
 * on the right. While driving (CarUxRestrictions) it keeps to at most three cards with one large button
 * each; settings, secondary actions, stars and sorting appear only when parked, and nothing scrolls.
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
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    var order by rememberSaveable { mutableStateOf(SortOrder.RECOMMENDED) }
    val ui = AdvisorUi(
        state = state,
        vehicle = vehicle,
        navi = navi,
        prefs = prefs,
        favorites = favorites,
        driving = vehicle.isDriving,
        notificationsAllowed = notificationsAllowed,
        order = order,
        onBack = onBack,
        onRefresh = { viewModel.refresh() },
        onAgree = {
            viewModel.setConsent(true)
            if (!notificationsAllowed) onAllowNotifications()
        },
        onRevoke = { viewModel.setConsent(false) },
        onReserve = viewModel::setReserveSocPct,
        onMinKw = viewModel::setMinKw,
        onBackgroundWatch = viewModel::setBackgroundWatch,
        onAllowNotifications = onAllowNotifications,
        onAction = { action, station -> viewModel.apply(action, station) },
        onFavorite = viewModel::toggleFavorite,
        onNaviChargers = { viewModel.showNaviChargers() },
        onOrder = { order = it },
    )
    EvDesignScreen(
        backdrop = EvBackdrop.ASSIST,
        narrowScrolls = !ui.driving,
        wide = { AssistWide(ui) },
        narrow = { compact -> AssistNarrow(ui, compact) },
    )
}

/** Everything the screen shows and can do, handed down as one value. */
private class AdvisorUi(
    val state: AdvisorState,
    val vehicle: EvDashboardState,
    val navi: NaviState,
    val prefs: AdvisorPrefs,
    val favorites: Set<String>,
    val driving: Boolean,
    val notificationsAllowed: Boolean,
    val order: SortOrder,
    val onBack: () -> Unit,
    val onRefresh: () -> Unit,
    val onAgree: () -> Unit,
    val onRevoke: () -> Unit,
    val onReserve: (Int) -> Unit,
    val onMinKw: (Int) -> Unit,
    val onBackgroundWatch: (Boolean) -> Unit,
    val onAllowNotifications: () -> Unit,
    val onAction: (NaviAction, StationSummary) -> Unit,
    val onFavorite: (String) -> Unit,
    val onNaviChargers: () -> Unit,
    val onOrder: (SortOrder) -> Unit,
)

/** How the cards are ordered: as the advisor ranked them, or by distance. */
private enum class SortOrder(val label: String) { RECOMMENDED("추천 순"), NEAREST("가까운 순") }

/** Where the panel is laid out: the design's page, the stacked page of a narrow window, or a phone-sized one. */
private enum class PanelStyle(val narrow: Boolean, val compact: Boolean) {
    WIDE(false, false),
    NARROW(true, false),
    COMPACT(true, true),
}

private enum class PanelBody { CONSENT, LOADING, PROBLEM, IDLE, CARDS }

// ---- page layouts ----

/** The design's 1600×900 page: `padding: 20px 52px 0 58px`, the 72 px header, 46 px, then `518px | 1fr` with a 22 px gap. */
@Composable
private fun AssistWide(ui: AdvisorUi) {
    Column(Modifier.fillMaxSize().padding(start = 58.dp, top = 20.dp, end = 52.dp)) {
        AssistHeader(ui, compact = false)
        Spacer(Modifier.height(46.dp))
        Row(
            Modifier.fillMaxWidth().weight(1f).padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(22.dp),
            verticalAlignment = Alignment.Top,
        ) {
            if (!ui.driving) {
                Column(Modifier.width(518.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    SettingsCard(ui)
                    SettingsExtras(ui)
                }
            }
            StationPanel(ui, PanelStyle.WIDE, Modifier.weight(1f))
        }
    }
}

/**
 * A narrow window: header, stations, then the settings, in one column. Parked it scrolls as a whole;
 * while driving nothing scrolls, so the panel is fitted into the window instead.
 */
@Composable
private fun AssistNarrow(ui: AdvisorUi, compact: Boolean) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = if (compact) 16.dp else 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        AssistHeader(ui, compact)
        StationPanel(
            ui,
            if (compact) PanelStyle.COMPACT else PanelStyle.NARROW,
            Modifier.fillMaxWidth().then(if (ui.driving) Modifier.weight(1f, fill = false) else Modifier),
        )
        if (!ui.driving) {
            SettingsCard(ui)
            SettingsExtras(ui)
        }
    }
}

// ---- header ----

@Composable
private fun AssistHeader(ui: AdvisorUi, compact: Boolean) {
    val navi = ui.navi
    val (label, tone) = when {
        navi.demo -> "데모 내비" to EvColors.Amber
        navi.connection == NaviConnection.CONNECTED -> "내비 연결됨" to EvColors.Green
        navi.connection == NaviConnection.CONNECTING -> "내비 연결 중…" to EvColors.TextSub
        else -> "내비 없음" to EvColors.Red
    }
    Row(
        Modifier.fillMaxWidth().height(72.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 14.dp else 28.dp),
    ) {
        RoundIconButton(EvIcons.ArrowLeft, "대시보드", ui.onBack)
        EvText("충전 도우미", evText(if (compact) 30 else 36, EvWeight.Bold, letterSpacingEm = -0.01f), Modifier.weight(1f), singleLine = true)
        StatusPill(label, tone, small = true)
        RoundIconButton(EvIcons.Refresh, "새로고침", ui.onRefresh)
    }
}

// ---- settings ----

/** The design's "충전 설정" card: `padding: 28px 28px 30px 34px`, two segmented settings and the watch switch. */
@Composable
private fun SettingsCard(ui: AdvisorUi, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .evSurface(EvColors.Panel, EvColors.PanelBorder, 24.dp)
            .padding(start = 35.dp, top = 29.dp, end = 29.dp, bottom = 31.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.Top) {
            EvIcon(EvIcons.Gear, 46.dp, tint = EvColors.Green)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                EvText("충전 설정", evText(31, EvWeight.Bold), singleLine = true)
                EvText("충전소 추천 기준을 설정해 주세요.", evText(19, color = EvColors.TextSub))
            }
        }
        SettingRow(
            EvIcons.BatteryReserve, "도착 예비 배터리", "목적지 도착 시 남겨둘 배터리 잔량",
            options = listOf(10, 15, 20), selected = ui.prefs.reserveSocPct, onSelect = ui.onReserve,
        ) { "$it%" }
        SettingRow(
            EvIcons.BoltOutline, "최소 충전 출력", "추천할 충전기의 최소 출력",
            options = listOf(50, 100, 200), selected = ui.prefs.minKw, onSelect = ui.onMinKw,
        ) { "$it kW+" }
        Row(
            Modifier.padding(top = 30.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            EvIcon(EvIcons.Monitor, 44.dp, tint = EvColors.Ink)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                EvText("내비 화면에서도 감시", evText(25, EvWeight.Bold))
                EvText("주행 중에도 충전 상태를 확인", evText(18, color = EvColors.TextSub))
            }
            ToggleSwitch(ui.prefs.backgroundWatch, ui.onBackgroundWatch)
        }
    }
}

/** One setting: icon, title and description, the segmented choice 70 px in, and a hairline under it. */
@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    description: String,
    options: List<Int>,
    selected: Int,
    onSelect: (Int) -> Unit,
    label: (Int) -> String,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                // `border-bottom: 1px solid rgba(255,255,255,0.1)`
                val line = 1.dp.toPx()
                drawRect(EvColors.Divider, Offset(0f, size.height - line), Size(size.width, line))
            }
            .padding(top = 30.dp, bottom = 29.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            EvIcon(icon, 38.dp, Modifier.padding(horizontal = 4.dp), tint = EvColors.Ink)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                EvText(title, evText(25, EvWeight.Bold))
                EvText(description, evText(18, color = EvColors.TextSub))
            }
        }
        Segmented(options, selected, onSelect, label, Modifier.fillMaxWidth().padding(start = 70.dp))
    }
}

/** Under the card: the design's "위치 전송 동의 철회" pill, and the app's notification request in the same shape. */
@Composable
private fun SettingsExtras(ui: AdvisorUi) {
    val askNotifications = ui.prefs.backgroundWatch && !ui.notificationsAllowed
    if (!ui.prefs.locationConsent && !askNotifications) return
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (ui.prefs.locationConsent) PillButton("위치 전송 동의 철회", ui.onRevoke)
            if (askNotifications) PillButton("알림 허용", ui.onAllowNotifications)
        }
        if (askNotifications) {
            EvText("알림이 꺼져 있어 내비 화면에서는 알려 드릴 수 없어요", evText(18, color = EvColors.Amber), Modifier.padding(start = 6.dp))
        }
    }
}

// ---- station panel ----

/**
 * The design's "추천 충전소" panel (`padding: 22px 22px 14px; gap: 18px; min-height: 656px`). Its body is
 * the consent request, one of the app's other empty states in the same centred layout, or the cards
 * under a one-line status.
 */
@Composable
private fun StationPanel(ui: AdvisorUi, style: PanelStyle, modifier: Modifier = Modifier) {
    val s = ui.state
    val body = when {
        !s.consent -> PanelBody.CONSENT
        s.candidates.isEmpty() && s.loading -> PanelBody.LOADING
        s.problem != null && s.candidates.isEmpty() -> PanelBody.PROBLEM
        s.candidates.isEmpty() -> PanelBody.IDLE
        else -> PanelBody.CARDS
    }
    // Inside the scrolling narrow page the height is unbounded; there the parts take their own height.
    val bounded = !style.narrow || ui.driving
    val summary = summary(s, ui.vehicle.socPercent, ui.vehicle.rangeRemainingKm)
    val edge = if (style.compact) 13.dp else 23.dp
    Column(
        modifier
            .then(
                when {
                    style.narrow -> Modifier
                    body == PanelBody.CARDS -> Modifier.heightIn(min = 656.dp)
                    else -> Modifier.height(656.dp)
                },
            )
            .evSurface(EvColors.Panel, EvColors.PanelBorder, 24.dp)
            .padding(start = edge, top = 23.dp, end = edge, bottom = 15.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        PanelHeader(ui, style, showSort = body == PanelBody.CARDS && !ui.driving)
        if (body == PanelBody.CARDS) {
            StatusStrip(s, summary, stacked = style.narrow)
            CardList(ui, style, if (bounded) Modifier.weight(1f, fill = false) else Modifier)
        } else {
            val room = when {
                !style.narrow -> Modifier.weight(1f)
                bounded -> Modifier.weight(1f, fill = false)
                else -> Modifier.heightIn(min = 440.dp)
            }
            CentreState(ui, body, summary, style, Modifier.fillMaxWidth().then(room))
        }
    }
}

/** `height: 60px; padding: 0 10px 0 14px; gap: 30px`: the pin, the title and (with cards, parked) the sort control. */
@Composable
private fun PanelHeader(ui: AdvisorUi, style: PanelStyle, showSort: Boolean) {
    Row(
        Modifier.fillMaxWidth().height(60.dp).padding(start = 14.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (style.compact) 14.dp else 30.dp),
    ) {
        EvIcon(EvIcons.Pin, 34.dp, height = 40.dp, tint = EvColors.Green)
        EvText("추천 충전소", evText(31, EvWeight.Bold), singleLine = true)
        Box(Modifier.weight(1f)) {
            // What the last card button did ("내비에 요청했어요"); narrow pages show it in the status lines.
            if (!style.narrow) ui.state.lastAction?.let { Chip(it, color = EvColors.Green, fill = GreenChip) }
        }
        if (showSort) SortControl(ui, style)
    }
}

/**
 * The design's "내 위치 기준 · 가까운 순" control. The first half says what the list is based on (around
 * the car, or along the route); the chevron opens the choice of order.
 */
@Composable
private fun SortControl(ui: AdvisorUi, style: PanelStyle) {
    var open by remember { mutableStateOf(false) }
    val alongRoute = ui.navi.guiding && ui.state.need.kind != NeedKind.NEARBY && ui.state.plannedVerdict != PlannedStopVerdict.UNREACHABLE
    val basis = if (alongRoute) "경로 기준" else "내 위치 기준"
    val density = LocalDensity.current
    val viewConfiguration = LocalViewConfiguration.current
    val scale = LocalDesignScale.current
    Box {
        Row(
            Modifier.clickable(interactionSource = null, indication = null, role = Role.DropdownList, onClickLabel = "정렬") { open = true },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            EvText(if (style.compact) ui.order.label else "$basis · ${ui.order.label}", evText(20, color = EvColors.TextSoft), singleLine = true)
            EvIcon(EvIcons.ChevronDown, 22.dp, tint = EvColors.TextSoft)
        }
        if (open) {
            Popup(
                alignment = Alignment.TopEnd,
                offset = with(density) { IntOffset(0, 44.dp.roundToPx()) },
                onDismissRequest = { open = false },
                properties = PopupProperties(focusable = true),
            ) {
                // A popup is a window of its own: it does not take over the design space's density.
                CompositionLocalProvider(
                    LocalDensity provides density,
                    LocalViewConfiguration provides viewConfiguration,
                    LocalDesignScale provides scale,
                ) {
                    SortMenu(ui.order) {
                        ui.onOrder(it)
                        open = false
                    }
                }
            }
        }
    }
}

@Composable
private fun SortMenu(selected: SortOrder, onSelect: (SortOrder) -> Unit) {
    Column(
        Modifier
            .width(236.dp)
            .evSurface(EvColors.RoundButton, EvColors.OutlineBorder, 18.dp)
            .padding(9.dp),
    ) {
        SortOrder.entries.forEach { option ->
            val chosen = option == selected
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = realDp(64f))
                    .selectable(selected = chosen, interactionSource = null, indication = null, role = Role.RadioButton, onClick = { onSelect(option) })
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                EvText(
                    option.label,
                    evText(20, if (chosen) EvWeight.SemiBold else EvWeight.Regular, if (chosen) EvColors.Ink else EvColors.TextSoft),
                    Modifier.weight(1f),
                    singleLine = true,
                )
                if (chosen) EvIcon(EvIcons.Check, 22.dp, tint = EvColors.Green)
            }
        }
    }
}

// ---- status ----

private enum class Tone { NORMAL, ATTENTION, URGENT }

private class Summary(val title: String, val detail: String, val tone: Tone, val active: Boolean) {
    val color: Color
        get() = when (tone) {
            Tone.URGENT -> EvColors.Red
            Tone.ATTENTION -> EvColors.Amber
            Tone.NORMAL -> EvColors.Ink
        }
}

/** What the advisor found (need, planned stop verdict) and the numbers behind it. */
private fun summary(state: AdvisorState, socPercent: Int?, rangeKm: Float?): Summary {
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
    val tone = when {
        urgent -> Tone.URGENT
        state.attention >= Severity.RECOMMENDED -> Tone.ATTENTION
        else -> Tone.NORMAL
    }
    return Summary(if (urgent) "긴급 · $title" else title, detail, tone, need.active)
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

/** The advisor's headline between the panel header and the cards: white, amber when it wants attention, red when urgent. */
@Composable
private fun StatusStrip(state: AdvisorState, summary: Summary, stacked: Boolean) {
    Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (stacked) {
            EvText(summary.title, evText(21, EvWeight.SemiBold, summary.color), maxLines = 2)
            if (summary.detail.isNotEmpty()) EvText(summary.detail, evText(18, color = EvColors.TextSub), maxLines = 2)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                EvText(summary.title, evText(21, EvWeight.SemiBold, summary.color), Modifier.weight(1f, fill = false), singleLine = true)
                if (summary.detail.isNotEmpty()) EvText(summary.detail, evText(19, color = EvColors.TextSub), singleLine = true)
            }
        }
        // A refresh failed after an earlier answer: the last cards stay (each shows its age), with the reason.
        state.problem?.let { EvText("$it · 마지막으로 받은 정보예요", evText(18, color = EvColors.Amber), maxLines = 2) }
        if (stacked) state.lastAction?.let { EvText(it, evText(18, color = EvColors.Green), singleLine = true) }
    }
}

/**
 * The design's centred state: the 132×96 illustration, a 19 px status line, the 27 px title, a description
 * and the gradient button. Drawn as designed for the consent request; the app's other states without
 * cards (checking, nothing found or no server, nothing needed) use the same layout.
 */
@Composable
private fun CentreState(ui: AdvisorUi, body: PanelBody, summary: Summary, style: PanelStyle, modifier: Modifier = Modifier) {
    val s = ui.state
    val soc = ui.vehicle.socPercent
    val status: String?
    val statusColor: Color
    val title: String
    val titleColor: Color
    val description: String?
    when (body) {
        PanelBody.CONSENT -> {
            // The design's "배터리 N% · 주행 가능 N km", led by the advisor's finding when there is one.
            status = listOfNotNull(summary.title.takeIf { summary.active }, summary.detail.ifEmpty { null }).joinToString(" · ").ifEmpty { null }
            statusColor = when {
                summary.tone != Tone.NORMAL -> summary.color
                soc != null && soc <= 20 -> EvColors.Amber
                else -> EvColors.Ink
            }
            title = "실시간 충전소 추천을 쓰려면 동의가 필요해요"
            titleColor = EvColors.Ink
            description = "차량의 현재 위치(약 100 m 단위)와 목적지를 충전 정보 서버로 보내 주변 충전소의 실시간 상태를 확인합니다. " +
                "서버는 위치를 저장하지 않으며, 설정에서 언제든 동의를 철회할 수 있습니다."
        }
        PanelBody.LOADING -> {
            status = summary.title
            statusColor = summary.color
            title = "주변 충전소 상태를 확인하고 있어요"
            titleColor = EvColors.Ink
            description = summary.detail.ifEmpty { null }
        }
        PanelBody.PROBLEM -> {
            status = summary.title
            statusColor = summary.color
            title = s.problem.orEmpty()
            titleColor = EvColors.Ink
            description = summary.detail.ifEmpty { null }
        }
        else -> {
            status = null
            statusColor = EvColors.Ink
            title = summary.title
            titleColor = summary.color
            description = summary.detail.ifEmpty { null }
        }
    }
    val side = if (style.compact) 4.dp else 60.dp
    Column(
        modifier.padding(start = side, end = side, bottom = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(22.dp, Alignment.CenterVertically),
    ) {
        IllustrationBox {
            when (body) {
                PanelBody.LOADING -> CircularProgressIndicator(Modifier.size(44.dp), color = EvColors.GreenGlyph, strokeWidth = 4.dp, trackColor = Color.Transparent)
                PanelBody.PROBLEM -> EvIcon(EvIcons.ChargerGlyph, 56.dp)
                else -> EvIcon(EvIcons.Pin, 44.dp, height = 52.dp, tint = EvColors.GreenGlyph)
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            status?.let { EvText(it, evText(19, EvWeight.SemiBold, statusColor), maxLines = 2, textAlign = TextAlign.Center) }
            EvText(title, evText(27, EvWeight.Bold, titleColor).copy(lineBreak = LineBreak.Heading), textAlign = TextAlign.Center)
            description?.let {
                EvText(
                    it,
                    evText(19, color = EvColors.TextSub, lineHeight = 1.5f).copy(lineBreak = LineBreak.Paragraph),
                    Modifier.widthIn(max = 620.dp),
                    textAlign = TextAlign.Center,
                )
            }
        }
        when (body) {
            PanelBody.CONSENT ->
                if (ui.driving) {
                    EvText("정차 후 동의할 수 있어요", evText(21, EvWeight.SemiBold, EvColors.Amber), textAlign = TextAlign.Center)
                } else {
                    GradientButton(ui.onAgree, Modifier.size(288.dp, 72.dp), shadow = EvColors.GlowGreen.copy(alpha = 0.2f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            EvText("동의하고 사용", evText(23, EvWeight.Bold, EvColors.OnGreen), singleLine = true)
                            EvIcon(EvIcons.ArrowRight, 28.dp, tint = EvColors.OnGreen)
                        }
                    }
                }
            PanelBody.PROBLEM ->
                GradientButton(
                    ui.onNaviChargers,
                    Modifier.height(if (ui.driving) maxOf(72.dp, realDp(100f)) else 72.dp).widthIn(min = 288.dp),
                    shadow = EvColors.GlowGreen.copy(alpha = 0.2f),
                ) {
                    Row(
                        Modifier.padding(horizontal = 36.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        EvText("내비 충전소 목록 보기", evText(23, EvWeight.Bold, EvColors.OnGreen), singleLine = true)
                        EvIcon(EvIcons.ArrowRight, 28.dp, tint = EvColors.OnGreen)
                    }
                }
            else -> Unit
        }
        if (body != PanelBody.CONSENT) s.lastAction?.let { EvText(it, evText(18, color = EvColors.Green), singleLine = true) }
    }
}

// ---- station cards ----

@Composable
private fun CardList(ui: AdvisorUi, style: PanelStyle, modifier: Modifier = Modifier) {
    // "N분 전 확인" has to age even when nothing else changes (failed refreshes keep the same cards).
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(AGE_TICK_MS)
            value = System.currentTimeMillis()
        }
    }
    val candidates = ui.state.candidates
    val nearestFirst = ui.order == SortOrder.NEAREST && !ui.driving
    val cards = remember(candidates, nearestFirst) {
        if (nearestFirst) candidates.sortedBy { it.candidate.estRoadKm } else candidates
    }
    val scrollState = rememberScrollState()
    Column(
        modifier.then(
            when {
                // The design's page does not scroll; with more than fits, the cards do (parked only).
                !style.narrow && !ui.driving -> Modifier.verticalScroll(scrollState)
                ui.driving -> Modifier.clipToBounds()
                else -> Modifier
            },
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        cards.forEach { item ->
            key(item.candidate.station.statId) { StationCard(ui, item, style, now) }
        }
        val coordinateNote = !ui.driving && cards.any { card ->
            card.actions.drop(1).any { it is NaviAction.AddWaypoint && !it.recognizedAsCharger }
        }
        if (coordinateNote) {
            EvText("좌표로 추가하면 내비가 충전소를 하나 더 넣을 수 있어요", evText(17, color = EvColors.TextSub), Modifier.padding(horizontal = 14.dp))
        }
    }
}

/**
 * The design's station card: `height: 176px; padding: 16px 18px; gap: 34px` on `#1a222b`, the 160×144
 * picture slot, the facts, and on the right the star over the action button.
 */
@Composable
private fun StationCard(ui: AdvisorUi, item: AdvisorCandidate, style: PanelStyle, now: Long) {
    val station = item.candidate.station
    val primary = item.actions.firstOrNull()
    val secondary = if (ui.driving) emptyList() else item.actions.drop(1)
    val favorite = station.statId in ui.favorites
    val card = Modifier.fillMaxWidth().background(EvColors.StationCard, RoundedCornerShape(20.dp))
    val inside = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)
    when {
        !style.narrow && !ui.driving ->
            Row(
                card.height(176.dp).then(inside),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(34.dp),
            ) {
                StationThumb()
                StationInfo(item, driving = false, now, nameLines = 1, Modifier.weight(1f))
                // `align-items: flex-end; justify-content: space-between; padding: 4px 0 18px`
                Column(
                    Modifier.fillMaxHeight().padding(top = 4.dp, bottom = 18.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        secondary.forEach { action -> SecondaryAction(action) { ui.onAction(action, station) } }
                        FavoriteStar(favorite, { ui.onFavorite(station.statId) }, Modifier.padding(end = 4.dp))
                    }
                    primary?.let { action -> PrimaryAction(action, 58.dp) { ui.onAction(action, station) } }
                }
            }
        !style.narrow ->
            // Driving: the same card over the whole width, with nothing but its one large button.
            Row(
                card.heightIn(min = 176.dp).then(inside),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(34.dp),
            ) {
                StationThumb()
                StationInfo(item, driving = true, now, nameLines = 1, Modifier.weight(1f))
                primary?.let { action -> PrimaryAction(action, drivingButtonHeight(), minWidth = 200.dp) { ui.onAction(action, station) } }
            }
        ui.driving ->
            // Driving in a narrow window: no picture, so three cards fit without scrolling.
            Row(
                card.then(inside),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                StationInfo(item, driving = true, now, nameLines = 1, Modifier.weight(1f))
                primary?.let { action -> PrimaryAction(action, drivingButtonHeight()) { ui.onAction(action, station) } }
            }
        else ->
            Column(card.then(inside), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(if (style.compact) 12.dp else 24.dp)) {
                    if (!style.compact) StationThumb()
                    StationInfo(item, driving = false, now, nameLines = 2, Modifier.weight(1f))
                    FavoriteStar(favorite, { ui.onFavorite(station.statId) })
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    secondary.forEach { action -> SecondaryAction(action) { ui.onAction(action, station) } }
                    primary?.let { action -> PrimaryAction(action, 58.dp) { ui.onAction(action, station) } }
                }
            }
    }
}

/** The one button of a card while driving: at least Pleos "Large" (100 dp) tall on the real display. */
@Composable
private fun drivingButtonHeight(): Dp = maxOf(58.dp, realDp(100f))

/** Name, availability, the trip figures and the chips (`gap: 6px`). */
@Composable
private fun StationInfo(item: AdvisorCandidate, driving: Boolean, now: Long, nameLines: Int, modifier: Modifier = Modifier) {
    val c = item.candidate
    val s = c.station
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        EvText(s.name, evText(27, EvWeight.Bold), maxLines = nameLines)
        EvText(
            "급속 대기 ${s.compatible.available}/${s.compatible.total}" + (s.maxKw?.let { " · $it kW" } ?: ""),
            evText(21, EvWeight.SemiBold, availabilityColor(s)),
            singleLine = true,
        )
        val detour = c.estDetourMin?.let { "+${it.toInt()}분" }
        val arrival = c.estArrivalSocPct?.let { "도착 ${it.toInt()}%" }
        val distance = if (driving) null else "${c.estRoadKm.toInt()} km"
        if (detour != null || arrival != null || distance != null) {
            Row(
                Modifier.padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(40.dp),
            ) {
                detour?.let { Metric(EvIcons.Clock, 22.dp, it) }
                arrival?.let { Metric(EvIcons.BatteryOutline, 22.dp, it) }
                distance?.let { Metric(EvIcons.Pin, 18.dp, it) }
            }
        }
        val freshness = freshnessText(s, now)
        val stale = s.freshness.level == FreshnessLevel.STALE
        // Operator and warning only when parked; a warning that repeats the freshness chip is not said twice.
        val warning = if (driving) null else c.warnings.firstOrNull()?.let(::warningText)?.takeIf { it != freshness }
        ChipRow(Modifier.padding(top = 6.dp)) {
            if (stale) Chip(freshness, color = EvColors.Amber, fill = AmberChip) else Chip(freshness)
            warning?.let { Chip(it, color = EvColors.Amber, fill = AmberChip) }
            if (!driving && s.operatorName.isNotBlank()) Chip(s.operatorName)
        }
    }
}

@Composable
private fun Metric(icon: ImageVector, iconWidth: Dp, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        EvIcon(icon, iconWidth, height = 22.dp, tint = EvColors.IconStroke)
        EvText(text, evText(20, color = EvColors.IconStroke), singleLine = true)
    }
}

/** The design's 44 px star: outlined, filled green for a favourite. */
@Composable
private fun FavoriteStar(favorite: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(44.dp)
            .toggleable(value = favorite, interactionSource = null, indication = null, role = Role.Checkbox, onValueChange = { onToggle() }),
        contentAlignment = Alignment.Center,
    ) {
        EvIcon(
            if (favorite) EvIcons.StarFilled else EvIcons.StarOutline,
            30.dp,
            tint = if (favorite) EvColors.Green else EvColors.IconStroke,
            contentDescription = "즐겨찾기",
        )
    }
}

/** The design's 146×58 "안내" button; it grows with a longer label, and while driving in height. */
@Composable
private fun PrimaryAction(action: NaviAction, height: Dp, minWidth: Dp = 146.dp, onClick: () -> Unit) {
    GradientButton(onClick, Modifier.height(height).widthIn(min = minWidth), brush = GradientCard, onClickLabel = actionLabel(action)) {
        Row(
            Modifier.padding(horizontal = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            EvIcon(EvIcons.Navigate, 24.dp, tint = EvColors.OnGreen)
            EvText(shortLabel(action), evText(22, EvWeight.Bold, EvColors.OnGreen), singleLine = true)
        }
    }
}

/** The other way to add the station (parked only): a small outlined pill beside the star. */
@Composable
private fun SecondaryAction(action: NaviAction, onClick: () -> Unit) {
    PillButton(actionLabel(action), onClick, height = 40.dp, horizontalPadding = 16.dp, textStyle = evText(17, color = EvColors.TextLabel))
}

private val AmberChip = EvColors.Amber.copy(alpha = 0.14f)
private val GreenChip = EvColors.Green.copy(alpha = 0.14f)

private fun availabilityColor(s: StationSummary): Color = when {
    s.compatible.available >= 2 -> EvColors.Green
    s.compatible.available == 1 -> EvColors.Amber
    else -> EvColors.Red
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

/** The full wording of an action (the secondary pill, and what the main button says to accessibility services). */
private fun actionLabel(action: NaviAction): String = when (action) {
    is NaviAction.RouteTo -> "이 충전소로 안내"
    is NaviAction.AddWaypoint -> if (action.recognizedAsCharger) "경유지로 추가" else "좌표로 경유지 추가"
    is NaviAction.SearchInNavi -> "내비에서 찾아 추가"
}

/** The main button's label, short like the design's "안내". */
private fun shortLabel(action: NaviAction): String = when (action) {
    is NaviAction.RouteTo -> "안내"
    is NaviAction.AddWaypoint -> if (action.recognizedAsCharger) "경유지 추가" else "좌표로 추가"
    is NaviAction.SearchInNavi -> "내비에서 추가"
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
