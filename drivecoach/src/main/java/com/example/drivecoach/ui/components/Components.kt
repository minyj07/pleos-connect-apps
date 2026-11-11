package com.example.drivecoach.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.example.drivecoach.analysis.WeekBucket
import com.example.drivecoach.model.EventType
import com.example.drivecoach.model.TripRecord
import com.example.drivecoach.ui.theme.CoachColors
import com.example.drivecoach.ui.theme.CoachSizes
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

// ---- formatting ----

fun fmtKm(km: Float): String = if (km < 10f) String.format(Locale.KOREA, "%.1f", km) else km.roundToInt().toString()

fun fmtDuration(seconds: Float): String {
    val min = (seconds / 60f).roundToInt()
    return if (min < 60) "${min}분" else "${min / 60}시간 ${min % 60}분"
}

private val dateTime = DateTimeFormatter.ofPattern("M월 d일 (E) HH:mm", Locale.KOREA)
private val dateOnly = DateTimeFormatter.ofPattern("M/d", Locale.KOREA)

fun fmtDateTime(epochMs: Long): String = dateTime.format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()))

fun fmtDay(epochMs: Long): String = dateOnly.format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()))

fun eventColor(type: EventType): Color = when (type) {
    EventType.HARSH_START, EventType.HARSH_ACCEL -> CoachColors.Moderate
    EventType.HARSH_BRAKE, EventType.HARD_STOP -> CoachColors.Brake
    EventType.ACCEL_THEN_BRAKE -> Color(0xFFB794F6)
}

// ---- building blocks ----

/** Card for one block of a screen; with [onClick] the whole card is the touch target. */
@Composable
fun SectionCard(
    title: String?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    val shape = RoundedCornerShape(20.dp)
    val body: @Composable ColumnScope.() -> Unit = {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (title != null) Text(title, style = MaterialTheme.typography.titleLarge)
            content()
        }
    }
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier, shape = shape, colors = colors, content = body)
    } else {
        Card(modifier = modifier, shape = shape, colors = colors, content = body)
    }
}

// ---- controls at Pleos sizes (see CoachSizes) ----

enum class ButtonKind { Primary, Secondary, Danger }

/** Text button: Medium (80 dp) by default, Large (100 dp) for the one decision a screen asks for. */
@Composable
fun CoachButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.Primary,
    large: Boolean = false,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val sized = modifier.heightIn(min = if (large) CoachSizes.Large else CoachSizes.Medium)
    val shape = RoundedCornerShape(if (large) 32.dp else 24.dp)
    val padding = PaddingValues(horizontal = if (large) 40.dp else 32.dp)
    val content: @Composable RowScope.() -> Unit = {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(12.dp))
        }
        Text(text, style = if (large) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium)
    }
    when (kind) {
        ButtonKind.Primary -> Button(onClick, sized, enabled, shape = shape, contentPadding = padding, content = content)
        ButtonKind.Secondary -> OutlinedButton(
            onClick, sized, enabled,
            shape = shape,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 0.6f else 0.2f)),
            contentPadding = padding,
            content = content,
        )
        ButtonKind.Danger -> OutlinedButton(
            onClick, sized, enabled,
            shape = shape,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.error.copy(alpha = if (enabled) 0.7f else 0.2f)),
            contentPadding = padding,
            content = content,
        )
    }
}

/** Square icon button, 80 dp (or [size]) with a 40 dp icon; [primary] fills it with the accent colour. */
@Composable
fun CoachIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = CoachSizes.Medium,
    primary: Boolean = false,
    enabled: Boolean = true,
) {
    val colors = if (primary) {
        IconButtonDefaults.filledIconButtonColors()
    } else {
        IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurface)
    }
    val radius = when {
        size >= CoachSizes.Large -> 32.dp
        size >= CoachSizes.Medium -> 24.dp
        else -> 16.dp
    }
    FilledIconButton(onClick, modifier.size(size), enabled, shape = RoundedCornerShape(radius), colors = colors) {
        Icon(icon, contentDescription, Modifier.size(if (size >= CoachSizes.Large) 48.dp else if (size >= CoachSizes.Medium) CoachSizes.Icon else 32.dp))
    }
}

/** One option of a single-choice group: Pleos Small (64 dp) toggle button. */
@Composable
fun ChoiceButton(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    Surface(
        selected = selected,
        onClick = onClick,
        modifier = modifier.heightIn(min = CoachSizes.Small),
        shape = RoundedCornerShape(16.dp),
        color = if (selected) accent.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (selected) accent else MaterialTheme.colorScheme.onSurface,
        border = if (selected) BorderStroke(2.dp, accent) else null,
    ) {
        Box(Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, maxLines = 1)
        }
    }
}

/** Single choice laid out as an equal-width grid of [ChoiceButton]s, [columns] per row. */
@Composable
fun <T> ChoiceGrid(options: List<Pair<String, T>>, selected: T?, columns: Int, modifier: Modifier = Modifier, onSelect: (T) -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        options.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { (label, value) -> ChoiceButton(label, value == selected, { onSelect(value) }, Modifier.weight(1f)) }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
fun <T> ChoiceRow(options: List<Pair<String, T>>, selected: T?, modifier: Modifier = Modifier, onSelect: (T) -> Unit) =
    ChoiceGrid(options, selected, columns = options.size, modifier = modifier, onSelect = onSelect)

/** On/off setting where the whole row (at least 80 dp tall) toggles, not only the small switch. */
@Composable
fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = CoachSizes.Medium)
            .clip(RoundedCornerShape(16.dp))
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CoachSizes.Gap),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled, modifier = Modifier.padding(horizontal = 10.dp).scale(1.35f))
    }
}

/** Slider with a 56 dp thumb and a 24 dp track instead of the phone-sized default. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoachSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.heightIn(min = CoachSizes.Small),
        onValueChangeFinished = onValueChangeFinished,
        interactionSource = interaction,
        thumb = { SliderDefaults.Thumb(interactionSource = interaction, thumbSize = DpSize(8.dp, 56.dp)) },
        track = { SliderDefaults.Track(sliderState = it, modifier = Modifier.height(24.dp)) },
        valueRange = valueRange,
    )
}

/**
 * A value with − / + buttons (one tap = one [step]) above a large slider. [onCommit] runs on a tap or
 * when the slider is released, so callers can skip expensive work while it is dragged.
 */
@Composable
fun StepperSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    format: (Float) -> String,
    onCommit: (Float) -> Unit,
) {
    var local by remember(value) { mutableFloatStateOf(value) }
    fun snap(v: Float) = ((v / step).roundToInt() * step).coerceIn(range.start, range.endInclusive)
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("$label ${format(local)}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            CoachIconButton(
                Icons.Filled.Remove, "$label 줄이기",
                onClick = {
                    local = snap(local - step)
                    onCommit(local)
                },
                size = CoachSizes.Small,
                enabled = local > range.start + step / 2,
            )
            CoachIconButton(
                Icons.Filled.Add, "$label 늘리기",
                onClick = {
                    local = snap(local + step)
                    onCommit(local)
                },
                size = CoachSizes.Small,
                enabled = local < range.endInclusive - step / 2,
            )
        }
        CoachSlider(local, { local = snap(it) }, range, Modifier.fillMaxWidth(), onValueChangeFinished = { onCommit(local) })
    }
}

@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, unit: String? = null, color: Color = MaterialTheme.colorScheme.onSurface) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = MaterialTheme.typography.headlineMedium, color = color)
            if (unit != null) {
                Spacer(Modifier.width(4.dp))
                Text(unit, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
            }
        }
    }
}

@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.18f))
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Text(text, color = color, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun Dot(color: Color, size: Dp = 12.dp) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(color),
    )
}

/** 0–100 score as a ring (same visual language as the EV app's SOC gauge). */
@Composable
fun ScoreRing(score: Int?, modifier: Modifier = Modifier, size: Dp = 180.dp, caption: String = "부드러움") {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val color = when {
        score == null -> MaterialTheme.colorScheme.outline
        score >= 85 -> CoachColors.Gentle
        score >= 65 -> CoachColors.Moderate
        else -> CoachColors.Hard
    }
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = 18.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(track, 135f, 270f, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            if (score != null) {
                drawArc(color, 135f, 270f * score / 100f, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(score?.toString() ?: "—", style = MaterialTheme.typography.displaySmall, color = color)
            Text(caption, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

data class BarPart(val label: String, val value: Float, val color: Color)

/** One horizontal bar split into parts, with a legend underneath. */
@Composable
@OptIn(ExperimentalLayoutApi::class)
fun StackedBar(parts: List<BarPart>, modifier: Modifier = Modifier, valueText: (BarPart, Float) -> String = { _, share -> "${(share * 100).roundToInt()}%" }) {
    val total = parts.sumOf { it.value.toDouble() }.toFloat()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(22.dp),
        ) {
            val r = CornerRadius(size.height / 2)
            drawRoundRect(Color(0xFF2A333E), cornerRadius = r)
            if (total > 0f) {
                var x = 0f
                parts.forEach { p ->
                    val w = size.width * p.value / total
                    if (w > 0f) drawRect(p.color, Offset(x, 0f), Size(w, size.height))
                    x += w
                }
            }
        }
        // Legend items move to the next line when the card is narrow (portrait window) instead of squeezing.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            parts.forEach { p ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Dot(p.color)
                    Spacer(Modifier.width(6.dp))
                    val share = if (total > 0f) p.value / total else 0f
                    Text("${p.label} ${valueText(p, share)}", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

/** Speed over time with brake shading and event markers. */
@Composable
fun SpeedTimelineChart(record: TripRecord, modifier: Modifier = Modifier) {
    val points = record.timeline
    val axis = MaterialTheme.colorScheme.outline
    val line = MaterialTheme.colorScheme.primary
    Column(modifier) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(220.dp),
        ) {
            if (points.size < 2) return@Canvas
            val maxT = max(points.last().tS, 1).toFloat()
            val maxV = max(points.maxOf { it.kmh }, 30f) * 1.1f
            fun x(t: Float) = size.width * t / maxT
            fun y(v: Float) = size.height * (1f - v / maxV)
            // brake shading
            points.forEach { p ->
                val b = p.brake ?: return@forEach
                if (b >= 0.5f) drawRect(CoachColors.Brake.copy(alpha = 0.22f), Offset(x(p.tS.toFloat()), 0f), Size(max(size.width / maxT, 1f), size.height))
            }
            // grid every 30 km/h
            var g = 30f
            while (g < maxV) {
                drawLine(axis.copy(alpha = 0.35f), Offset(0f, y(g)), Offset(size.width, y(g)), strokeWidth = 1f)
                g += 30f
            }
            for (i in 1 until points.size) {
                val a = points[i - 1]
                val b = points[i]
                if (b.tS - a.tS > 5) continue
                drawLine(line, Offset(x(a.tS.toFloat()), y(a.kmh)), Offset(x(b.tS.toFloat()), y(b.kmh)), strokeWidth = 4f, cap = StrokeCap.Round)
            }
            record.events.forEach { e ->
                val ex = x(e.atS)
                drawLine(eventColor(e.type), Offset(ex, 0f), Offset(ex, size.height), strokeWidth = 3f)
                drawCircle(eventColor(e.type), 9f, Offset(ex, 12f))
            }
        }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            LegendItem(line, "속도")
            LegendItem(CoachColors.Brake.copy(alpha = 0.6f), "브레이크")
            LegendItem(CoachColors.Moderate, "급가속·급출발")
            LegendItem(CoachColors.Brake, "급감속·급정지")
        }
    }
}

@Composable
fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Dot(color, 10.dp)
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Weekly harsh-event rate (per 10 km); empty weeks are shown as gaps. */
@Composable
fun WeeklyBars(weeks: List<WeekBucket>, modifier: Modifier = Modifier) {
    val maxRate = max(weeks.mapNotNull { it.harshPer10Km }.maxOrNull() ?: 0f, 2f)
    val bar = MaterialTheme.colorScheme.primary
    Row(modifier.height(170.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
        weeks.forEach { w ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                val rate = w.harshPer10Km
                Text(rate?.let { String.format(Locale.KOREA, "%.1f", it) } ?: "—", style = MaterialTheme.typography.bodySmall)
                Canvas(
                    Modifier
                        .fillMaxWidth()
                        .height(110.dp),
                ) {
                    val h = if (rate == null) 0f else size.height * (rate / maxRate).coerceIn(0.02f, 1f)
                    drawRoundRect(bar, Offset(size.width * 0.2f, size.height - h), Size(size.width * 0.6f, h), CornerRadius(8f))
                }
                Text(fmtDay(w.startEpochMs), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * Steering wheel drawn at [steer] (fraction of full lock, left negative) × [lockTurns] turns.
 * The rotation is approximate; the label carries the exact share of full lock.
 */
@Composable
fun SteeringWheel(steer: Float, lockTurns: Double, modifier: Modifier = Modifier, size: Dp = 200.dp, highlight: Color = MaterialTheme.colorScheme.primary) {
    val rim = MaterialTheme.colorScheme.onSurface
    val degrees = (steer * lockTurns * 360.0).toFloat()
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.size(size)) {
            val c = center
            val r = this.size.minDimension / 2 * 0.9f
            rotate(degrees, c) {
                drawCircle(rim, r, c, style = Stroke(r * 0.16f))
                // spokes: left, right, bottom
                drawLine(rim, Offset(c.x - r, c.y), Offset(c.x + r, c.y), strokeWidth = r * 0.14f)
                drawLine(rim, c, Offset(c.x, c.y + r), strokeWidth = r * 0.14f)
                // top marker shows the rotation
                drawCircle(highlight, r * 0.11f, Offset(c.x, c.y - r))
            }
            drawCircle(highlight, r * 0.18f, c)
        }
        Text(steerLabel(steer), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        if (abs(steer) >= 0.05f) {
            val turns = abs(steer) * lockTurns
            Text("약 ${String.format(Locale.KOREA, "%.1f", turns)}바퀴", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

fun steerLabel(steer: Float): String {
    val pct = (abs(steer) * 100).roundToInt()
    val dir = if (steer < 0) "왼쪽" else "오른쪽"
    return when {
        pct < 3 -> "정중앙"
        pct >= 97 -> "$dir 끝까지"
        else -> "$dir $pct%"
    }
}
