package com.example.pleosev.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.EvStation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.pleosev.model.ChargeState
import com.example.pleosev.model.ConnectionState
import com.example.pleosev.model.DataSource
import com.example.pleosev.model.EvDashboardState
import com.example.pleosev.ui.theme.ButtonSize
import com.example.pleosev.ui.theme.PleosEvTheme
import java.util.Locale

/** Short charging-advisor message for the dashboard; null when there is nothing to say. */
data class AdvisorBanner(val text: String, val urgent: Boolean)

/** Root screen: SOC gauge + stat cards. Simplifies itself while driving (CarUxRestrictions). */
@Composable
fun DashboardScreen(
    state: EvDashboardState,
    onRetry: () -> Unit,
    advisorBanner: AdvisorBanner? = null,
    onOpenAdvisor: () -> Unit = {},
) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 28.dp, vertical = 20.dp),
        ) {
            HeaderRow(state, onRetry)
            advisorBanner?.let { banner ->
                Spacer(Modifier.height(12.dp))
                AdvisorBannerCard(banner, onOpenAdvisor)
            }
            Spacer(Modifier.height(16.dp))
            BoxWithConstraints(Modifier.fillMaxSize()) {
                // Pleos windows: landscape (2 app windows) is wide, portrait (1 window) is narrow.
                val wide = maxWidth >= 840.dp && maxHeight >= 480.dp
                val narrow = maxWidth < 560.dp
                if (wide) {
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        Box(Modifier.weight(0.42f), contentAlignment = Alignment.Center) {
                            GaugeWithAdvisorButton(state, onOpenAdvisor)
                        }
                        Box(Modifier.weight(0.58f)) { StatGrid(state, columns = 2) }
                    }
                } else {
                    // Gauge scrolls together with the cards (single lazy grid, gauge as full-span header).
                    StatGrid(state, columns = if (narrow) 1 else 2) {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            GaugeWithAdvisorButton(state, onOpenAdvisor)
                        }
                    }
                }
            }
        }
    }
}

/** Tapping opens the advisor; the dashboard never switches screens by itself (driver distraction). */
@Composable
private fun AdvisorBannerCard(banner: AdvisorBanner, onOpen: () -> Unit) {
    Card(
        onClick = onOpen,
        colors = CardDefaults.cardColors(
            containerColor = if (banner.urgent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.heightIn(min = ButtonSize.Medium).padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.EvStation, contentDescription = null, tint = Color.Black, modifier = Modifier.size(ButtonSize.Icon))
            Spacer(Modifier.width(16.dp))
            Text(banner.text, style = MaterialTheme.typography.titleMedium, color = Color.Black, modifier = Modifier.weight(1f))
            Text("보기", style = MaterialTheme.typography.titleMedium, color = Color.Black)
        }
    }
}

/**
 * The advisor's entry point sits right under the gauge as a large button: the header's far corner was
 * hard to reach and its default 40 dp button too small for in-car touch.
 */
@Composable
private fun GaugeWithAdvisorButton(state: EvDashboardState, onOpenAdvisor: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(32.dp)) {
        SocGauge(state)
        OutlinedButton(
            onClick = onOpenAdvisor,
            modifier = Modifier.widthIn(min = 320.dp).heightIn(min = ButtonSize.Medium),
        ) {
            Icon(Icons.Filled.EvStation, contentDescription = null, modifier = Modifier.size(ButtonSize.Icon))
            Spacer(Modifier.width(12.dp))
            Text("충전 도우미", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun HeaderRow(state: EvDashboardState, onRetry: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = ButtonSize.Medium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Text("EV Companion", style = MaterialTheme.typography.headlineMedium, maxLines = 1)
            if (state.isDriving) {
                Text(
                    "주행 중 · 간소화 표시",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            val (label, color) = when {
                state.source == DataSource.DEMO -> "데모 데이터" to MaterialTheme.colorScheme.tertiary
                state.connection == ConnectionState.CONNECTED -> "차량 연결됨" to MaterialTheme.colorScheme.primary
                state.connection == ConnectionState.CONNECTING -> "연결 중…" to MaterialTheme.colorScheme.secondary
                else -> "연결 실패" to MaterialTheme.colorScheme.error
            }
            StatusLabel(label, color)
            if (state.connection == ConnectionState.FAILED || state.source == DataSource.DEMO) {
                Button(onClick = onRetry, modifier = Modifier.heightIn(min = ButtonSize.Medium)) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(ButtonSize.Icon))
                    Spacer(Modifier.width(12.dp))
                    Text("재연결", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
    state.errorMessage?.takeIf { state.connection == ConnectionState.FAILED && !state.isDriving }?.let {
        Text(
            it,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun SocGauge(state: EvDashboardState) {
    val soc = state.socPercent
    val fraction = (soc ?: 0).coerceIn(0, 100) / 100f
    val ringColor = when {
        state.isCharging -> MaterialTheme.colorScheme.secondary
        (soc ?: 100) <= 20 -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }
    val track = MaterialTheme.colorScheme.surfaceVariant
    Box(Modifier.size(320.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = Stroke(width = 26.dp.toPx(), cap = StrokeCap.Round)
            val inset = stroke.width / 2
            val arcSize = Size(size.width - stroke.width, size.height - stroke.width)
            drawArc(
                color = track, startAngle = 135f, sweepAngle = 270f, useCenter = false,
                topLeft = Offset(inset, inset), size = arcSize, style = stroke,
            )
            drawArc(
                color = ringColor, startAngle = 135f, sweepAngle = 270f * fraction, useCenter = false,
                topLeft = Offset(inset, inset), size = arcSize, style = stroke,
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = soc?.let { "$it%" } ?: "--",
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = state.rangeRemainingKm?.let { "주행 가능 ${fmt(it, 0)} km" } ?: "주행 가능 거리 --",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.isCharging) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Bolt, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                    Text("충전 중", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

private data class Stat(val title: String, val value: String, val icon: ImageVector, val hint: String? = null)

@Composable
private fun StatGrid(
    state: EvDashboardState,
    columns: Int,
    header: (@Composable () -> Unit)? = null,
) {
    val stats = buildList {
        add(
            Stat(
                "배터리",
                // The capacity alone would read as the energy left (before CAR_ENERGY is granted only it is known).
                (state.batteryLevelKwh?.let { fmt(it, 1) } ?: "--") +
                    (state.batteryCapacityKwh?.let { " / ${fmt(it, 1)}" } ?: "") + " kWh",
                Icons.Filled.BatteryChargingFull,
                "현재 / 총 용량",
            )
        )
        add(
            Stat(
                "충전",
                when {
                    state.isCharging -> state.chargeRateKw?.let { "충전 중 · ${fmt(it, 1)} kW" } ?: "충전 중"
                    // Unplugged, "complete" / "error" are left over from the last charge (the emulator: from boot).
                    state.chargePortConnected == false -> "대기"
                    state.chargeState == ChargeState.FULLY_CHARGED -> "충전 완료"
                    state.chargeState == ChargeState.ERROR -> "충전 오류"
                    state.chargeSwitchOn == null && state.chargeState == null -> "--"
                    else -> "대기"
                },
                Icons.Filled.Bolt,
                listOfNotNull(
                    state.chargePortConnected?.let { if (it) "커넥터 연결됨" else "커넥터 분리" },
                    state.chargePortOpen?.let { if (it) "포트 열림" else "포트 닫힘" },
                ).joinToString(" · ").ifEmpty { null },
            )
        )
        add(Stat("전비 (현재)", state.currentEfficiency?.let { "${fmt(it, 1)} km/kWh" } ?: "--", Icons.Filled.Eco))
        add(Stat("전비 (충전 후)", state.sinceChargeEfficiency?.let { "${fmt(it, 1)} km/kWh" } ?: "--", Icons.Filled.Eco))
        if (!state.isDriving) {
            add(Stat("이번 주행", state.tripDistanceKm?.let { "${fmt(it, 1)} km" } ?: "--", Icons.Filled.Route))
            add(Stat("주행 시간", state.tripDrivingTimeMin?.let { "${it / 60}h ${it % 60}m" } ?: "--", Icons.Filled.Timer))
            add(Stat("누적 주행거리", state.odometerKm?.let { "${fmt(it, 0)} km" } ?: "--", Icons.Filled.Speed))
        }
        add(Stat("드라이브 모드", state.drivingMode ?: "--", Icons.Filled.DirectionsCar))
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (header != null) {
            item(span = { GridItemSpan(maxLineSpan) }) { header() }
        }
        items(stats) { StatCard(it) }
    }
}

@Composable
private fun StatCard(stat: Stat) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(stat.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                Text(stat.title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
            Text(stat.value, style = MaterialTheme.typography.titleLarge)
            stat.hint?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun fmt(v: Float, decimals: Int): String = String.format(Locale.US, "%,.${decimals}f", v)

@Preview(widthDp = 1200, heightDp = 720, backgroundColor = 0xFF0B0F14, showBackground = true)
@Composable
private fun DashboardPreviewWide() {
    PleosEvTheme {
        DashboardScreen(
            state = EvDashboardState(
                connection = ConnectionState.CONNECTED, source = DataSource.DEMO,
                socPercent = 68, batteryLevelKwh = 52.4f, batteryCapacityKwh = 77.4f,
                rangeRemainingKm = 318f, chargeSwitchOn = true, chargePortOpen = true,
                currentEfficiency = 5.6f, sinceChargeEfficiency = 5.9f,
                odometerKm = 12480f, tripDistanceKm = 23.4f, tripDrivingTimeMin = 31,
                drivingMode = "ECO",
            ),
            onRetry = {},
        )
    }
}

@Preview(widthDp = 600, heightDp = 900, backgroundColor = 0xFF0B0F14, showBackground = true)
@Composable
private fun DashboardPreviewPortraitDriving() {
    PleosEvTheme {
        DashboardScreen(
            state = EvDashboardState(connection = ConnectionState.CONNECTED, socPercent = 18, rangeRemainingKm = 72f, isDriving = true),
            onRetry = {},
        )
    }
}
