package com.example.drivecoach.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.drivecoach.share.ShareUi
import com.example.drivecoach.ui.components.CoachIcon
import com.example.drivecoach.ui.components.CoachIconView
import com.example.drivecoach.ui.components.CoachIcons
import com.example.drivecoach.ui.components.LocalWideLayout
import com.example.drivecoach.ui.components.T
import com.example.drivecoach.ui.components.WideLayoutMinWidth
import com.example.drivecoach.ui.consent.ConsentScreen
import com.example.drivecoach.ui.driving.DrivingMinimalView
import com.example.drivecoach.ui.habits.HabitsScreen
import com.example.drivecoach.ui.home.HomeScreen
import com.example.drivecoach.ui.parking.ParkingScreen
import com.example.drivecoach.ui.settings.SettingsScreen
import com.example.drivecoach.ui.share.ShareScreen
import com.example.drivecoach.ui.theme.CoachColors
import com.example.drivecoach.ui.theme.CoachTheme
import com.example.drivecoach.ui.trips.TripDetailScreen
import com.example.drivecoach.ui.trips.TripsScreen

/** [icon] is the design's SVG; 주차 연습 has none: the design draws the letter "P" there. */
enum class Tab(val label: String, val icon: CoachIcon?) {
    HOME("홈", CoachIcons.Home),
    TRIPS("주행 기록", CoachIcons.Trips),
    HABITS("습관 분석", CoachIcons.Habits),
    PARKING("주차 연습", null),
    SETTINGS("설정", CoachIcons.Settings),
}

/**
 * Top level: consent first, the static driving view whenever CarUxRestrictions apply, otherwise a
 * navigation rail with at most one level below each tab (trip list -> trip detail).
 */
@Composable
fun DriveCoachRoot(vm: MainViewModel, onConsentAccepted: () -> Unit, onHide: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val restricted by vm.restricted.collectAsStateWithLifecycle()
    val share by vm.share.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var openTripId by rememberSaveable { mutableStateOf<String?>(null) }

    BackHandler {
        when {
            share !is ShareUi.Idle && !restricted -> vm.closeShare()
            openTripId != null -> openTripId = null
            tab != Tab.HOME && !restricted -> tab = Tab.HOME
            else -> onHide()
        }
    }

    Box(Modifier.fillMaxSize().background(CoachTheme.colors.bg)) {
        GlowLayer(Modifier.matchParentSize())
        when {
            settings.needsConsentDecision -> ConsentScreen(
                onAccept = {
                    vm.acceptConsent()
                    onConsentAccepted()
                },
                onDecline = vm::declineConsent,
            )
            restricted -> DrivingMinimalView(vm)
            else -> Row(Modifier.fillMaxSize().safeDrawingPadding()) {
                TabRail(tab) {
                    tab = it
                    openTripId = null
                    vm.closeShare()
                }
                // Landscape window (whole app area) or portrait window (two apps side by side): screens
                // read LocalWideLayout to put cards side by side or stack them.
                BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
                    CompositionLocalProvider(LocalWideLayout provides (maxWidth >= WideLayoutMinWidth)) {
                        TabContent(vm, tab, share, openTripId, onTab = { tab = it }, onOpenTrip = { openTripId = it }, onConsentAccepted = onConsentAccepted)
                    }
                }
            }
        }
    }
}

@Composable
private fun TabContent(
    vm: MainViewModel,
    tab: Tab,
    share: ShareUi,
    openTripId: String?,
    onTab: (Tab) -> Unit,
    onOpenTrip: (String?) -> Unit,
    onConsentAccepted: () -> Unit,
) {
    if (share !is ShareUi.Idle) {
        ShareScreen(
            state = share,
            onAccept = vm::acceptShareConsent,
            onRetry = vm::retryShare,
            onDeleteLink = vm::deleteShownLink,
            onClose = vm::closeShare,
        )
        return
    }
    when (tab) {
        Tab.HOME -> HomeScreen(
            vm,
            onOpenTrip = {
                onOpenTrip(it)
                onTab(Tab.TRIPS)
            },
            onConsent = {
                vm.acceptConsent()
                onConsentAccepted()
            },
            onGoParking = { onTab(Tab.PARKING) },
        )
        Tab.TRIPS -> if (openTripId != null) {
            TripDetailScreen(vm, openTripId, onBack = { onOpenTrip(null) })
        } else {
            TripsScreen(vm, onOpen = { onOpenTrip(it) })
        }
        Tab.HABITS -> HabitsScreen(vm)
        Tab.PARKING -> ParkingScreen(vm)
        Tab.SETTINGS -> SettingsScreen(
            vm,
            onConsent = {
                vm.acceptConsent()
                onConsentAccepted()
            },
        )
    }
}

/**
 * The design's background glow: three elliptical radial gradients over the whole app area, under everything
 * else, at the theme's `--glow` opacity (1 light, 0.45 dark).
 */
@Composable
private fun GlowLayer(modifier: Modifier) {
    val glow = CoachTheme.colors.glow
    Box(
        modifier
            .graphicsLayer {
                alpha = glow
                // CSS `opacity` fades the three gradients as one picture, not each on its own.
                compositingStrategy = if (glow < 1f) CompositingStrategy.Offscreen else CompositingStrategy.Auto
            }
            .drawBehind {
                // CSS paints the first listed background on top, so the list is drawn back to front.
                radialGlow(0.60f, 0.55f, 0.40f, 0.40f, CoachColors.GlowC) // radial-gradient(40% 40% at 60% 55%, …)
                radialGlow(0.20f, 1.00f, 0.50f, 0.50f, CoachColors.GlowB) // radial-gradient(50% 50% at 20% 100%, …)
                radialGlow(0.92f, 0.08f, 0.60f, 0.55f, CoachColors.GlowA) // radial-gradient(60% 55% at 92% 8%, …)
            },
    )
}

/** `radial-gradient(<rx>% <ry>% at <cx>% <cy>%, color 0%, transparent 100%)` with all values as fractions of the area. */
private fun DrawScope.radialGlow(cx: Float, cy: Float, rx: Float, ry: Float, color: Color) {
    val radiusX = size.width * rx
    val radiusY = size.height * ry
    if (radiusX <= 0f || radiusY <= 0f) return
    withTransform({
        translate(size.width * cx, size.height * cy)
        scale(1f, radiusY / radiusX, pivot = Offset.Zero)
    }) {
        drawCircle(
            brush = Brush.radialGradient(listOf(color, color.copy(alpha = 0f)), center = Offset.Zero, radius = radiusX),
            radius = radiusX,
            center = Offset.Zero,
        )
    }
}

/**
 * The design's navigation rail: 150 px wide with a 2 px line on its right edge, five 112×112 items
 * (radius 24) 16 px apart, centred vertically. Active: `accSoft` background, accent colour, 700;
 * inactive: ink2, 500. Icons are 36 px; the label is 20 px, 8 px under the icon.
 */
@Composable
private fun TabRail(selected: Tab, onSelect: (Tab) -> Unit) {
    val c = CoachTheme.colors
    val shape = RoundedCornerShape(24.dp)
    Column(
        Modifier
            .fillMaxHeight()
            .width(150.dp)
            .drawBehind {
                val w = 2.dp.toPx()
                drawRect(c.line, Offset(size.width - w, 0f), Size(w, size.height))
            }
            .padding(end = 2.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Tab.entries.forEach { t ->
            val active = t == selected
            val color = if (active) c.acc else c.ink2
            Column(
                Modifier
                    .size(112.dp)
                    .clip(shape)
                    .selectable(selected = active, role = Role.Tab, onClick = { onSelect(t) })
                    .background(if (active) c.accSoft else Color.Transparent),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            ) {
                if (t.icon != null) {
                    CoachIconView(t.icon, 36.dp, color)
                } else {
                    // <span style="font-size:38px;font-weight:700;line-height:36px">P</span>
                    T("P", 38, weight = FontWeight.W700, color = color, lh = 36f / 38f)
                }
                T(t.label, 20, weight = if (active) FontWeight.W700 else FontWeight.W500, color = color, nowrap = true)
            }
        }
    }
}
