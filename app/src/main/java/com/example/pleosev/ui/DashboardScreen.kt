package com.example.pleosev.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.pleosev.model.ChargeState
import com.example.pleosev.model.ConnectionState
import com.example.pleosev.model.DataSource
import com.example.pleosev.model.EvDashboardState
import com.example.pleosev.ui.design.EvBackdrop
import com.example.pleosev.ui.design.EvDesignScreen
import com.example.pleosev.ui.design.EvIcon
import com.example.pleosev.ui.design.EvIcons
import com.example.pleosev.ui.design.EvText
import com.example.pleosev.ui.design.GradientButton
import com.example.pleosev.ui.design.PillButton
import com.example.pleosev.ui.design.StatusPill
import com.example.pleosev.ui.design.evSurface
import com.example.pleosev.ui.design.pullUp
import com.example.pleosev.ui.design.softShadow
import com.example.pleosev.ui.theme.EvColors
import com.example.pleosev.ui.theme.EvWeight
import com.example.pleosev.ui.theme.PleosEvTheme
import com.example.pleosev.ui.theme.evText
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The charging advisor's message for the dashboard's alert; null when there is nothing to say.
 * [title] is the alert's headline, [action] the label of its button (it opens the advisor).
 */
data class AdvisorBanner(val title: String, val action: String, val urgent: Boolean) {
    /** Closing the alert hides it until this changes. */
    val key: String get() = "$urgent|$title"
}

/**
 * Home screen of the "EV Companion" design: the status header, the SOC gauge with the advisor's button
 * and the vehicle tiles. While driving (CarUxRestrictions) the trip tiles are left out.
 */
@Composable
fun DashboardScreen(
    state: EvDashboardState,
    onRetry: () -> Unit,
    advisorBanner: AdvisorBanner? = null,
    onOpenAdvisor: () -> Unit = {},
    onDismissBanner: () -> Unit = {},
) {
    EvDesignScreen(
        backdrop = EvBackdrop.HOME,
        wide = { HomeWide(state, advisorBanner, onRetry, onOpenAdvisor, onDismissBanner) },
        narrow = { compact -> HomeNarrow(state, advisorBanner, compact, onRetry, onOpenAdvisor, onDismissBanner) },
    )
}

/** The design's 1600×900 page: `padding: 44px 88px`, header, then `440px | 1fr` with an 88 px gap. */
@Composable
private fun BoxScope.HomeWide(
    state: EvDashboardState,
    banner: AdvisorBanner?,
    onRetry: () -> Unit,
    onOpenAdvisor: () -> Unit,
    onDismissBanner: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 88.dp, vertical = 44.dp),
        verticalArrangement = Arrangement.spacedBy(36.dp),
    ) {
        HomeHeader(state, onRetry, compact = false)
        Row(horizontalArrangement = Arrangement.spacedBy(88.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.width(440.dp), verticalArrangement = Arrangement.spacedBy(44.dp)) {
                TitleBlock(state.isDriving, Modifier.pullUp(40.dp))
                SocGauge(state, Modifier.align(Alignment.CenterHorizontally))
                AssistButton(onOpenAdvisor, Modifier.align(Alignment.CenterHorizontally).width(420.dp))
            }
            TileGrid(state, columns = 2, Modifier.weight(1f))
        }
    }
    // In the gap between the header and the tiles, so the page keeps the design's positions.
    connectionError(state)?.let { message ->
        EvText(
            message,
            evText(18, color = EvColors.Red),
            Modifier.align(Alignment.TopEnd).padding(top = 98.dp, end = 88.dp).widthIn(max = 896.dp),
            singleLine = true,
        )
    }
    banner?.let {
        LowAlert(it, state, compact = false, onOpenAdvisor, onDismissBanner, Modifier.align(Alignment.TopCenter).padding(top = 34.dp).width(720.dp))
    }
}

/** A narrow window (the 841 dp split window): the same parts in one scrolling column. */
@Composable
private fun HomeNarrow(
    state: EvDashboardState,
    banner: AdvisorBanner?,
    compact: Boolean,
    onRetry: () -> Unit,
    onOpenAdvisor: () -> Unit,
    onDismissBanner: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = if (compact) 24.dp else 44.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        HomeHeader(state, onRetry, compact)
        connectionError(state)?.let { EvText(it, evText(18, color = EvColors.Red), Modifier.fillMaxWidth(), textAlign = TextAlign.End) }
        banner?.let { LowAlert(it, state, compact, onOpenAdvisor, onDismissBanner, Modifier.fillMaxWidth()) }
        TitleBlock(state.isDriving)
        SocGauge(state, Modifier.align(Alignment.CenterHorizontally))
        AssistButton(onOpenAdvisor, Modifier.align(Alignment.CenterHorizontally).widthIn(max = 420.dp).fillMaxWidth())
        TileGrid(state, columns = if (compact) 1 else 2, Modifier.fillMaxWidth())
    }
}

private fun connectionError(state: EvDashboardState): String? =
    state.errorMessage?.takeIf { state.connection == ConnectionState.FAILED && !state.isDriving }

// ---- header ----

/** Right-aligned: Bluetooth, Wi-Fi, the time and the connection pill (with "재연결" when it can help). */
@Composable
private fun HomeHeader(state: EvDashboardState, onRetry: () -> Unit, compact: Boolean) {
    val (label, tone) = when {
        state.source == DataSource.DEMO -> "데모 데이터" to EvColors.Amber
        state.connection == ConnectionState.CONNECTED -> "차량 연결됨" to EvColors.Green
        state.connection == ConnectionState.CONNECTING -> "연결 중…" to EvColors.TextSub
        else -> "연결 실패" to EvColors.Red
    }
    val canRetry = state.connection == ConnectionState.FAILED || state.source == DataSource.DEMO
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(if (compact) 20.dp else 40.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!compact) {
            EvIcon(EvIcons.Bluetooth, 24.dp, tint = EvColors.Ink)
            EvIcon(EvIcons.Wifi, 30.dp, height = 24.dp, tint = EvColors.Ink)
        }
        EvText(clockText(), evText(26, EvWeight.SemiBold, letterSpacingEm = 0.01f, tabular = true), singleLine = true)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            StatusPill(label, tone)
            if (canRetry) {
                PillButton(
                    "재연결",
                    onRetry,
                    height = 47.dp,
                    horizontalPadding = 22.dp,
                    fill = EvColors.RoundButton,
                    outline = null,
                    textStyle = evText(19, EvWeight.SemiBold),
                    leading = { EvIcon(EvIcons.Refresh, 22.dp, tint = EvColors.Ink) },
                )
            }
        }
    }
}

/** The time of day as "HH:mm", renewed on the minute. */
@Composable
private fun clockText(): String {
    val time by produceState(initialValue = currentTime()) {
        while (true) {
            delay(60_000L - System.currentTimeMillis() % 60_000L + 20L)
            value = currentTime()
        }
    }
    return time
}

private val ClockFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private fun currentTime(): String = LocalTime.now().format(ClockFormat)

// ---- left column ----

@Composable
private fun TitleBlock(driving: Boolean, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        EvText("EV Companion", evText(46, EvWeight.Bold, letterSpacingEm = -0.01f), singleLine = true)
        if (driving) {
            EvText("주행 중 · 간소화 표시", evText(25, color = EvColors.Amber), singleLine = true)
        } else {
            EvText("충전은 더 쉽고, 주행은 더 스마트하게", evText(25, color = EvColors.TextSub), singleLine = true)
        }
    }
}

/**
 * The design's 400×370 gauge: a 270° arc of radius 180 around (200, 200), 34 px thick with round caps,
 * filled up to the state of charge; amber from 20 % down (unless the car is charging).
 */
@Composable
private fun SocGauge(state: EvDashboardState, modifier: Modifier = Modifier) {
    val soc = state.socPercent
    val fraction = (soc ?: 0).coerceIn(0, 100) / 100f
    val low = soc != null && soc <= 20 && !state.isCharging
    val brush = remember(low) {
        if (low) ArcBrush(EvColors.ArcLowStart, EvColors.ArcLowEnd) else ArcBrush(EvColors.ArcStart, EvColors.ArcEnd)
    }
    Box(modifier.size(400.dp, 370.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            val unit = size.width / 400f
            val stroke = Stroke(width = 34f * unit, cap = StrokeCap.Round)
            val topLeft = Offset(20f * unit, 20f * unit)
            val arcSize = Size(360f * unit, 360f * unit)
            drawArc(EvColors.ArcTrack, startAngle = 135f, sweepAngle = 270f, useCenter = false, topLeft = topLeft, size = arcSize, style = stroke)
            if (soc != null) {
                if (fraction > 0f) {
                    drawArc(brush, startAngle = 135f, sweepAngle = 270f * fraction, useCenter = false, topLeft = topLeft, size = arcSize, style = stroke)
                } else {
                    // A zero-length dash with round caps is a dot at the arc's start.
                    drawCircle(brush, radius = 17f * unit, center = Offset(72.7f * unit, 327.3f * unit))
                }
            }
        }
        // `inset: 0 0 8% 0`
        Column(
            Modifier.fillMaxSize().padding(bottom = 29.6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
        ) {
            EvText(
                soc?.let { "$it%" } ?: DASH,
                evText(100, EvWeight.ExtraBold, lineHeight = 1f, letterSpacingEm = -0.02f),
                singleLine = true,
            )
            EvText(
                "주행 가능 ${state.rangeRemainingKm?.let { fmt(it, 0) } ?: DASH} km",
                evText(25, color = EvColors.TextSoft),
                singleLine = true,
            )
            if (state.isCharging) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EvIcon(EvIcons.BoltFilled, 22.dp, tint = EvColors.Green)
                    EvText("충전 중", evText(21, EvWeight.SemiBold, EvColors.Green), singleLine = true)
                }
            }
        }
    }
}

/**
 * The gauge's `<linearGradient x1="0" y1="1" x2="0.4" y2="0">` in objectBoundingBox units: the unit square
 * stretched over the arc's bounding box (20, 20, 360 × 307.28 in the 400-wide view box).
 */
private class ArcBrush(private val from: Color, private val to: Color) : ShaderBrush() {
    override fun createShader(size: Size): Shader {
        val unit = size.width / 400f
        return android.graphics.LinearGradient(0f, 1f, 0.4f, 0f, from.toArgb(), to.toArgb(), android.graphics.Shader.TileMode.CLAMP).apply {
            setLocalMatrix(
                android.graphics.Matrix().apply {
                    setScale(360f * unit, 307.28f * unit)
                    postTranslate(20f * unit, 20f * unit)
                },
            )
        }
    }
}

/** The advisor's entry: the 84 px gradient pill with the charger icon, and the arrow 42 px from its right edge. */
@Composable
private fun AssistButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    GradientButton(onClick, modifier.height(84.dp), shadow = EvColors.GlowGreen.copy(alpha = 0.25f)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            EvIcon(EvIcons.Charger, 36.dp)
            EvText("충전 도우미", evText(27, EvWeight.Bold, EvColors.OnGreen), singleLine = true)
        }
        EvIcon(EvIcons.ArrowRight, 32.dp, Modifier.align(Alignment.CenterEnd).padding(end = 42.dp), tint = EvColors.OnGreen)
    }
}

// ---- tiles ----

/** [primary]: the first two tiles (10 px gaps and a hint line); the others have 14 px gaps and `min-height: 108px`. */
private class Tile(
    val label: String,
    val value: String,
    val icon: ImageVector,
    val tint: Color?,
    val hint: String? = null,
    val primary: Boolean = false,
    val valueSpacingEm: Float = 0f,
)

private fun tiles(state: EvDashboardState): List<Tile> = buildList {
    add(
        Tile(
            "배터리",
            // The capacity alone would read as the energy left (before CAR_ENERGY is granted only it is known).
            (state.batteryLevelKwh?.let { fmt(it, 1) } ?: DASH) + (state.batteryCapacityKwh?.let { " / ${fmt(it, 1)}" } ?: "") + " kWh",
            EvIcons.BatteryFilled,
            tint = null,
            hint = "현재 / 총 용량",
            primary = true,
            valueSpacingEm = -0.005f,
        ),
    )
    add(
        Tile(
            "충전",
            when {
                state.isCharging -> state.chargeRateKw?.let { "충전 중 · ${fmt(it, 1)} kW" } ?: "충전 중"
                // Unplugged, "complete" / "error" are left over from the last charge (the emulator: from boot).
                state.chargePortConnected == false -> "대기"
                state.chargeState == ChargeState.FULLY_CHARGED -> "충전 완료"
                state.chargeState == ChargeState.ERROR -> "충전 오류"
                state.chargeSwitchOn == null && state.chargeState == null -> DASH
                else -> "대기"
            },
            EvIcons.BoltFilled,
            tint = EvColors.Green,
            hint = listOfNotNull(
                state.chargePortConnected?.let { if (it) "커넥터 연결됨" else "커넥터 분리" },
                state.chargePortOpen?.let { if (it) "포트 열림" else "포트 닫힘" },
            ).joinToString(" · ").ifEmpty { null },
            primary = true,
        ),
    )
    add(Tile("전비 (현재)", state.currentEfficiency?.let { "${fmt(it, 1)} km/kWh" } ?: DASH, EvIcons.Leaf, EvColors.IconStroke))
    add(Tile("전비 (충전 후)", state.sinceChargeEfficiency?.let { "${fmt(it, 1)} km/kWh" } ?: DASH, EvIcons.Leaf, EvColors.IconStroke))
    if (!state.isDriving) {
        add(Tile("이번 주행", state.tripDistanceKm?.let { "${fmt(it, 1)} km" } ?: DASH, EvIcons.Route, EvColors.IconStroke))
        add(Tile("주행 시간", state.tripDrivingTimeMin?.let { "${it / 60}h ${it % 60}m" } ?: DASH, EvIcons.Timer, EvColors.IconStroke))
        add(Tile("누적 주행거리", state.odometerKm?.let { "${fmt(it, 0)} km" } ?: DASH, EvIcons.Gauge, EvColors.IconStroke))
    }
    add(Tile("드라이브 모드", state.drivingMode ?: DASH, EvIcons.CarOutline, EvColors.IconStroke))
}

/** `grid-template-columns: 1fr 1fr; gap: 20px 26px`: the tiles of a row share its height. */
@Composable
private fun TileGrid(state: EvDashboardState, columns: Int, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        tiles(state).chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(26.dp)) {
                row.forEach { TileCard(it, Modifier.weight(1f).fillMaxHeight()) }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun TileCard(tile: Tile, modifier: Modifier = Modifier) {
    Column(
        modifier
            .heightIn(min = if (tile.primary) 0.dp else 108.dp)
            .evSurface(EvColors.Tile, EvColors.TileBorder, 22.dp)
            // `padding: 22px 34px 24px` inside the 1 px border.
            .padding(start = 35.dp, top = 23.dp, end = 35.dp, bottom = 25.dp),
        verticalArrangement = Arrangement.spacedBy(if (tile.primary) 10.dp else 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            EvIcon(tile.icon, 26.dp, tint = tile.tint)
            EvText(tile.label, evText(20, color = EvColors.TextLabel), singleLine = true)
        }
        EvText(tile.value, evText(31, EvWeight.Bold, letterSpacingEm = tile.valueSpacingEm), singleLine = true)
        tile.hint?.let { EvText(it, evText(19, color = EvColors.TextHint), singleLine = true) }
    }
}

// ---- alert ----

private class AlertTone(val accent: Color, val sub: Color, val fill: Color, val border: Color, val onAccent: Color)

private val AmberTone = AlertTone(EvColors.Amber, EvColors.AmberSub, EvColors.AmberBackground, EvColors.AmberBorder, EvColors.OnAmber)
private val RedTone = AlertTone(EvColors.Red, EvColors.RedSub, EvColors.RedBackground, EvColors.RedBorder, EvColors.OnRed)

/**
 * The design's low-battery alert (amber; red when urgent) with the advisor's message. The button, and a
 * tap anywhere on the alert, open the advisor; the dashboard never switches screens by itself.
 */
@Composable
private fun LowAlert(
    banner: AdvisorBanner,
    state: EvDashboardState,
    compact: Boolean,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tone = if (banner.urgent) RedTone else AmberTone
    val shape = RoundedCornerShape(22.dp)
    val battery = listOfNotNull(
        state.socPercent?.let { "배터리 $it%" },
        state.rangeRemainingKm?.let { "주행 가능 ${fmt(it, 0)} km" },
    ).joinToString(" · ")
    Row(
        modifier
            .softShadow(Color.Black.copy(alpha = 0.45f), blur = 60.dp, offsetY = 20.dp, cornerRadius = 22.dp)
            .background(tone.fill, shape)
            .drawBehind {
                val w = 1.5.dp.toPx()
                drawRoundRect(
                    color = tone.border,
                    topLeft = Offset(w / 2f, w / 2f),
                    size = Size(size.width - w, size.height - w),
                    cornerRadius = CornerRadius(22.dp.toPx() - w / 2f),
                    style = Stroke(w),
                )
            }
            .clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onOpen)
            // `padding: 16px 16px 16px 24px` inside the 1.5 px border.
            .padding(start = 25.5.dp, top = 17.5.dp, end = 17.5.dp, bottom = 17.5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        if (!compact) EvIcon(EvIcons.BatteryAlert, 34.dp, tint = tone.accent)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            EvText(banner.title, evText(23, EvWeight.Bold, tone.accent), maxLines = if (compact) 3 else 2)
            if (battery.isNotEmpty()) EvText(battery, evText(18, color = tone.sub), singleLine = true)
        }
        PillButton(
            banner.action,
            onOpen,
            height = 54.dp,
            horizontalPadding = 24.dp,
            fill = tone.accent,
            outline = null,
            textStyle = evText(19, EvWeight.Bold, tone.onAccent),
        )
        val interaction = remember { MutableInteractionSource() }
        val pressed by interaction.collectIsPressedAsState()
        Box(
            Modifier
                .size(48.dp)
                .background(if (pressed) Color.White.copy(alpha = 0.08f) else Color.Transparent, CircleShape)
                .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            EvIcon(EvIcons.Close, 22.dp, tint = tone.sub, contentDescription = "닫기")
        }
    }
}

private const val DASH = "—"

private fun fmt(v: Float, decimals: Int): String = String.format(Locale.US, "%,.${decimals}f", v)

// ---- previews ----

@Preview(widthDp = 1682, heightDp = 1210)
@Composable
private fun DashboardPreviewWide() {
    PleosEvTheme {
        DashboardScreen(
            state = EvDashboardState(
                connection = ConnectionState.CONNECTED, source = DataSource.DEMO,
                socPercent = 42, batteryLevelKwh = 32.5f, batteryCapacityKwh = 77.4f,
                rangeRemainingKm = 210f, chargeSwitchOn = false, chargePortOpen = false, chargePortConnected = false,
                currentEfficiency = 5.6f, sinceChargeEfficiency = 5.9f,
                odometerKm = 12480f, tripDistanceKm = 23.4f, tripDrivingTimeMin = 31,
                drivingMode = "ECO",
            ),
            onRetry = {},
        )
    }
}

@Preview(widthDp = 841, heightDp = 1210)
@Composable
private fun DashboardPreviewSplitLowBattery() {
    PleosEvTheme {
        DashboardScreen(
            state = EvDashboardState(connection = ConnectionState.CONNECTED, socPercent = 18, rangeRemainingKm = 72f, isDriving = true),
            onRetry = {},
            advisorBanner = AdvisorBanner("배터리가 부족해요", "충전소 찾기", urgent = false),
        )
    }
}
