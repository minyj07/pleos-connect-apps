package com.example.pleosev

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.pleosev.advisor.AdvisorState
import com.example.pleosev.advisor.background.AlertTexts
import com.example.pleosev.advisor.domain.NeedKind
import com.example.pleosev.advisor.domain.PlannedStopVerdict
import com.example.pleosev.advisor.domain.Severity
import com.example.pleosev.ui.AdvisorBanner
import com.example.pleosev.ui.DashboardScreen
import com.example.pleosev.ui.DashboardViewModel
import com.example.pleosev.ui.advisor.AdvisorScreen
import com.example.pleosev.ui.design.EvText
import com.example.pleosev.ui.design.GradientButton
import com.example.pleosev.ui.design.PillButton
import com.example.pleosev.ui.theme.ButtonSize
import com.example.pleosev.ui.theme.EvColors
import com.example.pleosev.ui.theme.EvWeight
import com.example.pleosev.ui.theme.PleosEvTheme
import com.example.pleosev.ui.theme.evText
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val requestCarEnergyPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            Log.i(TAG, "$CAR_ENERGY_PERMISSION granted=$granted")
            // The properties were subscribed before the answer and failed: subscribe again now.
            if (granted) appGraph.vehicle.onCarEnergyGranted()
        }

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            Log.i(TAG, "POST_NOTIFICATIONS granted=$granted")
            notificationsAllowed = notificationsEnabled()
        }

    private var notificationsAllowed by mutableStateOf(false)

    /** Bumped by intents that ask for the advisor screen (notifications), including ones to the running activity. */
    private var openAdvisorRequests by mutableIntStateOf(0)

    private var carEnergyAsked = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null && intent.opensAdvisor()) openAdvisorRequests++
        // CCP Back (and touch Back) on the top screen hides the app instead of closing it (Pleos CCP policy);
        // the advisor screen's BackHandler, registered later, goes back to the dashboard first.
        onBackPressedDispatcher.addCallback(this) { moveTaskToBack(true) }
        appGraph.updates.checkOnce()
        // Permission dialogs are not distraction optimized: ask only while parked.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                appGraph.vehicle.state.map { it.isDriving }.distinctUntilChanged().collect { driving ->
                    if (!driving) askForCarEnergyOnce()
                }
            }
        }
        setContent {
            PleosEvTheme {
                var showAdvisor by rememberSaveable { mutableStateOf(openAdvisorRequests > 0) }
                // The alert the driver closed (AdvisorBanner.key); kept here so a visit to the advisor does not bring it back.
                var dismissedBanner by rememberSaveable { mutableStateOf<String?>(null) }
                val requests = openAdvisorRequests
                LaunchedEffect(requests) { if (requests > 0) showAdvisor = true }
                if (showAdvisor) {
                    BackHandler { showAdvisor = false }
                    AdvisorScreen(
                        viewModel(),
                        onBack = { showAdvisor = false },
                        notificationsAllowed = notificationsAllowed,
                        onAllowNotifications = ::askForNotifications,
                    )
                } else {
                    val viewModel: DashboardViewModel = viewModel()
                    val state by viewModel.uiState.collectAsStateWithLifecycle()
                    val advisor by viewModel.advisor.collectAsStateWithLifecycle()
                    val banner = banner(advisor)
                    // Closed until the need changes: another message shows again, and so does the same one after it went away.
                    LaunchedEffect(banner?.key) { if (banner == null) dismissedBanner = null }
                    DashboardScreen(
                        state = state,
                        onRetry = viewModel::retry,
                        advisorBanner = banner?.takeIf { it.key != dismissedBanner },
                        onOpenAdvisor = { showAdvisor = true },
                        onDismissBanner = { dismissedBanner = banner?.key },
                    )
                }
                UpdatePrompt()
            }
        }
    }

    /** Pleos App Market update prompt (wording from the Pleos app update guide), only while parked. */
    @Composable
    private fun UpdatePrompt() {
        val updates = appGraph.updates
        val newer by updates.newerVersionCode.collectAsStateWithLifecycle()
        val driving by remember { appGraph.vehicle.state.map { it.isDriving }.distinctUntilChanged() }
            .collectAsStateWithLifecycle(initialValue = true)
        if (newer == null || driving) return
        // In the design's palette: the panel colour, a gradient pill to confirm and an outlined pill to decline.
        // A dialog is a window of its own, outside the scaled design space: its sizes are real dp.
        AlertDialog(
            onDismissRequest = updates::answered,
            shape = RoundedCornerShape(24.dp),
            containerColor = EvColors.PanelSolid,
            tonalElevation = 0.dp,
            text = {
                EvText("최신 버전이 존재합니다. 업데이트를 위해 페이지를 이동할까요?", evText(21, color = EvColors.Ink, lineHeight = 1.5f))
            },
            // Filled / outlined rather than text buttons, so the in-car sized touch areas are visible.
            confirmButton = {
                GradientButton(onClick = { updates.answered(); openMarketPage() }, modifier = DialogButtonSize) {
                    EvText("예", evText(22, EvWeight.Bold, EvColors.OnGreen), singleLine = true)
                }
            },
            dismissButton = {
                PillButton(
                    "아니요",
                    onClick = updates::answered,
                    modifier = DialogButtonSize,
                    height = ButtonSize.Small,
                    textStyle = evText(22, EvWeight.SemiBold, EvColors.TextLabel),
                )
            },
        )
    }

    /** The app's page on the Pleos App Market, where the driver starts the update (path from the update guide). */
    private fun openMarketPage() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://detail?package=$packageName"))
        runCatching { startActivity(intent) }.onFailure { Log.w(TAG, "cannot open the app market: ${it.message}") }
    }

    override fun onResume() {
        super.onResume()
        notificationsAllowed = notificationsEnabled()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.opensAdvisor()) openAdvisorRequests++
    }

    /**
     * android.car.permission.CAR_ENERGY is a *dangerous* AAOS permission (range / battery level),
     * so it must be granted at runtime. Pleos `pleos.car.permission.*` permissions are manifest-only.
     */
    private fun askForCarEnergyOnce() {
        if (carEnergyAsked || ContextCompat.checkSelfPermission(this, CAR_ENERGY_PERMISSION) == PackageManager.PERMISSION_GRANTED) return
        carEnergyAsked = true
        runCatching { requestCarEnergyPermission.launch(CAR_ENERGY_PERMISSION) }
            .onFailure { Log.w(TAG, "permission request failed: ${it.message}") }
    }

    /** Alerts while the navi covers the app need POST_NOTIFICATIONS (asked from the advisor screen, parked). */
    private fun askForNotifications() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || notificationsEnabled()) return
        runCatching { requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }
            .onFailure { Log.w(TAG, "permission request failed: ${it.message}") }
    }

    private fun notificationsEnabled() = NotificationManagerCompat.from(this).areNotificationsEnabled()

    private fun Intent?.opensAdvisor() = this?.getBooleanExtra(EXTRA_OPEN_ADVISOR, false) == true

    /** The dashboard alert: what the advisor found (the alert's headline) and what its button offers. */
    private fun banner(advisor: AdvisorState): AdvisorBanner? {
        if (advisor.attention < Severity.RECOMMENDED) return null
        val urgent = advisor.attention == Severity.URGENT
        val stop = AlertTexts.stopLabel(advisor)
        val plannedStop = advisor.need.kind == NeedKind.PLANNED_STOP
        val (title, action) = when {
            plannedStop && advisor.plannedVerdict == PlannedStopVerdict.BUSY -> "${stop}에 빈 충전기가 없어요" to "대안 보기"
            plannedStop && advisor.plannedVerdict == PlannedStopVerdict.UNREACHABLE -> "${stop}까지 배터리가 부족해요" to "가까운 충전소 보기"
            // Urgent with a usable stop (attention follows the battery, not the stop): say so, not "can't check".
            plannedStop && advisor.plannedVerdict == PlannedStopVerdict.OK -> "${stop}에 빈 충전기가 있어요" to "충전소 보기"
            plannedStop && advisor.plannedVerdict == null -> "${stop}를 확인하고 있어요" to "보기"
            plannedStop && advisor.plannedVerdict == PlannedStopVerdict.NO_MATCHING_CHARGER -> "${stop}에 설정한 출력의 충전기가 없어요" to "대안 보기"
            plannedStop -> "$stop 상태를 확인할 수 없어요" to "대안 보기"
            advisor.need.kind == NeedKind.ON_ROUTE -> "목적지 도착 전에 충전이 필요해요" to "충전소 보기"
            // Plain low battery: the design's alert as it is.
            else -> "배터리가 부족해요" to "충전소 찾기"
        }
        return AdvisorBanner(if (urgent) "긴급 · $title" else title, action, urgent)
    }

    companion object {
        /** Opens the charging advisor screen directly (e.g. from a notification). */
        const val EXTRA_OPEN_ADVISOR = "open_advisor"
        private const val TAG = "MainActivity"
        private const val CAR_ENERGY_PERMISSION = "android.car.permission.CAR_ENERGY"
        private val DialogButtonSize = Modifier.widthIn(min = 160.dp).heightIn(min = ButtonSize.Small)
    }
}
