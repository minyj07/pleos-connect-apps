package com.example.drivecoach.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.LocalParking
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.drivecoach.ui.components.LocalWideLayout
import com.example.drivecoach.ui.components.WideLayoutMinWidth
import com.example.drivecoach.ui.consent.ConsentScreen
import com.example.drivecoach.ui.driving.DrivingMinimalView
import com.example.drivecoach.ui.habits.HabitsScreen
import com.example.drivecoach.ui.home.HomeScreen
import com.example.drivecoach.ui.parking.ParkingScreen
import com.example.drivecoach.share.ShareUi
import com.example.drivecoach.ui.settings.SettingsScreen
import com.example.drivecoach.ui.share.ShareScreen
import com.example.drivecoach.ui.theme.CoachSizes
import com.example.drivecoach.ui.trips.TripDetailScreen
import com.example.drivecoach.ui.trips.TripsScreen

enum class Tab(val label: String, val icon: ImageVector) {
    HOME("홈", Icons.Filled.Home),
    TRIPS("주행 기록", Icons.Filled.Timeline),
    HABITS("습관 분석", Icons.Filled.Insights),
    PARKING("주차 연습", Icons.Filled.LocalParking),
    SETTINGS("설정", Icons.Filled.Settings),
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

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when {
            settings.needsConsentDecision -> ConsentScreen(
                onAccept = {
                    vm.acceptConsent()
                    onConsentAccepted()
                },
                onDecline = vm::declineConsent,
            )
            restricted -> DrivingMinimalView(vm)
            else -> Row(Modifier.safeDrawingPadding()) {
                TabRail(tab) {
                    tab = it
                    openTripId = null
                    vm.closeShare()
                }
                // Landscape window (whole app area) or portrait window (two apps side by side): screens
                // read LocalWideLayout to put cards side by side or stack them.
                BoxWithConstraints(Modifier.weight(1f)) {
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
 * Tabs as 108×96 dp targets with 36 dp icons (Material's rail items are phone-sized), grouped at
 * mid-height on the driver's side of the window where they are easiest to reach.
 */
@Composable
private fun TabRail(selected: Tab, onSelect: (Tab) -> Unit) {
    Column(
        Modifier
            .fillMaxHeight()
            .width(132.dp)
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CoachSizes.Gap, Alignment.CenterVertically),
    ) {
        Tab.entries.forEach { t ->
            val active = t == selected
            val color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            Column(
                Modifier
                    .size(width = 108.dp, height = 96.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else Color.Transparent)
                    .selectable(selected = active, role = Role.Tab, onClick = { onSelect(t) }),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
            ) {
                Icon(t.icon, contentDescription = null, tint = color, modifier = Modifier.size(36.dp))
                Text(t.label, style = MaterialTheme.typography.labelLarge, color = color)
            }
        }
    }
}
