@file:OptIn(ExperimentalLayoutApi::class)

package com.example.drivecoach.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.drivecoach.analysis.Tip
import com.example.drivecoach.analysis.WeekBucket
import com.example.drivecoach.model.EventType
import com.example.drivecoach.model.TripRecord
import com.example.drivecoach.ui.theme.CoachColors
import com.example.drivecoach.ui.theme.CoachPalette
import com.example.drivecoach.ui.theme.CoachTheme
import com.example.drivecoach.ui.theme.CssNormalLineHeight
import com.example.drivecoach.ui.theme.NotoSansKr
import com.example.drivecoach.ui.theme.ProvideCarTouchTargets
import com.example.drivecoach.ui.theme.coachTextStyle
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

// ---- formatting ----

fun fmtKm(km: Float): String = if (km < 10f) String.format(Locale.KOREA, "%.1f", km) else km.roundToInt().toString()

fun fmtDuration(seconds: Float): String {
    val min = (seconds / 60f).roundToInt()
    return if (min < 60) "${min}분" else "${min / 60}시간 ${min % 60}분"
}

/** [fmtDuration] as number / unit pairs, for the stat blocks that draw the unit small. */
fun durationParts(seconds: Float): List<Pair<String, String?>> {
    val min = (seconds / 60f).roundToInt()
    return if (min < 60) listOf("$min" to "분") else listOf("${min / 60}" to "시간", "${min % 60}" to "분")
}

fun fmt1(v: Float): String = String.format(Locale.KOREA, "%.1f", v)

fun minSec(seconds: Float): String {
    val t = seconds.roundToInt()
    return "%d:%02d".format(t / 60, t % 60)
}

private val dateTime = DateTimeFormatter.ofPattern("M월 d일 (E) HH:mm", Locale.KOREA)
private val dateOnly = DateTimeFormatter.ofPattern("M/d", Locale.KOREA)

fun fmtDateTime(epochMs: Long): String = dateTime.format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()))

fun fmtDay(epochMs: Long): String = dateOnly.format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()))

/** Event colours of the design: amber for hard acceleration, red for hard braking, purple for accelerate-then-brake. */
fun eventColor(type: EventType): Color = when (type) {
    EventType.HARSH_START, EventType.HARSH_ACCEL -> CoachColors.Amber
    EventType.HARSH_BRAKE, EventType.HARD_STOP -> CoachColors.Red
    EventType.ACCEL_THEN_BRAKE -> CoachColors.Purple
}

/** Score bar colour of the trip list: `score >= 80 ? green : score >= 50 ? amber : red`. */
fun scoreColor(score: Int): Color = when {
    score >= 80 -> CoachColors.ScoreGood
    score >= 50 -> CoachColors.ScoreMid
    else -> CoachColors.ScoreBad
}

// ---- text ----

/** U+FEFF ZERO WIDTH NO-BREAK SPACE: line-break class WJ, and Android keeps it in the surrounding font run. */
private val WORD_JOINER = 0xFEFF.toChar()

/** Hangul syllables and jamo, CJK ideographs and kana: the scripts Android breaks between any two characters. */
private fun Char.isCjk(): Boolean = when (code) {
    in 0xAC00..0xD7A3, in 0x1100..0x11FF, in 0x3130..0x318F, in 0x4E00..0x9FFF, in 0x3040..0x30FF -> true
    else -> false
}

private fun Char.isLetterOrNumber(): Boolean = isLetter() || when (category) {
    CharCategory.DECIMAL_DIGIT_NUMBER, CharCategory.LETTER_NUMBER, CharCategory.OTHER_NUMBER -> true
    else -> false
}

/**
 * CSS `word-break: keep-all` (set on the design's root): Korean text wraps between words, not between
 * syllables. Android breaks Hangul like ideographs, and its phrase-based breaking has no Korean support on the
 * emulator's Android 14, so the break opportunities are removed in the text itself: a zero-width no-break
 * space goes between two letters or digits when one of them is Hangul or CJK (the pairs keep-all joins).
 */
fun keepAll(text: String): String {
    var first = -1
    for (i in 1 until text.length) {
        if (joins(text[i - 1], text[i])) {
            first = i
            break
        }
    }
    if (first < 0) return text
    val out = StringBuilder(text.length + text.length / 2)
    out.append(text, 0, first)
    for (i in first until text.length) {
        if (joins(text[i - 1], text[i])) out.append(WORD_JOINER)
        out.append(text[i])
    }
    return out.toString()
}

private fun joins(a: Char, b: Char): Boolean = (a.isCjk() || b.isCjk()) && a.isLetterOrNumber() && b.isLetterOrNumber()

/**
 * Text at the design's numbers: [size] is the CSS font-size in px, [lh] the CSS line-height as a multiple
 * (default: `normal`), [spacing] the letter-spacing in px. [nowrap] is `white-space: nowrap`: one line, cut
 * with an ellipsis if the window is too narrow for it. Text that may wrap gets the keep-all treatment
 * ([keepAll]); use [spacing] only together with [nowrap] (Android would add it around the joiners too).
 */
@Composable
fun T(
    text: String,
    size: Int,
    modifier: Modifier = Modifier,
    weight: FontWeight = FontWeight.W400,
    color: Color = Color.Unspecified,
    lh: Float = CssNormalLineHeight,
    spacing: Float = 0f,
    align: TextAlign = TextAlign.Unspecified,
    nowrap: Boolean = false,
    tabular: Boolean = false,
    family: FontFamily = NotoSansKr,
) {
    val shown = if (nowrap) text else remember(text) { keepAll(text) }
    val style = remember(size, weight, lh, spacing, align, tabular, family) {
        coachTextStyle(size.toFloat(), weight, lh, spacing, tabular, family).copy(textAlign = align)
    }
    Text(
        text = shown,
        // Screen readers get the text without the invisible joiners.
        modifier = if (shown.length != text.length) modifier.semantics { contentDescription = text } else modifier,
        color = color,
        softWrap = !nowrap,
        maxLines = if (nowrap) 1 else Int.MAX_VALUE,
        overflow = if (nowrap) TextOverflow.Ellipsis else TextOverflow.Clip,
        style = style,
    )
}

/** Pieces of one text with their own weight or colour (the design's `<b>` and inline `<span>`s). */
class RichText internal constructor(private val wraps: Boolean) {
    private val builder = AnnotatedString.Builder()
    private val plain = StringBuilder()

    fun text(s: String) {
        builder.append(if (wraps) keepAll(s) else s)
        plain.append(s)
    }

    fun styled(s: String, weight: FontWeight? = null, color: Color = Color.Unspecified) {
        builder.withStyle(SpanStyle(fontWeight = weight, color = color)) { append(if (wraps) keepAll(s) else s) }
        plain.append(s)
    }

    fun bold(s: String, color: Color = Color.Unspecified) = styled(s, FontWeight.W700, color)

    internal fun annotated(): AnnotatedString = builder.toAnnotatedString()

    internal fun plain(): String = plain.toString()
}

@Composable
fun RichT(
    size: Int,
    modifier: Modifier = Modifier,
    weight: FontWeight = FontWeight.W400,
    color: Color = Color.Unspecified,
    lh: Float = CssNormalLineHeight,
    nowrap: Boolean = false,
    tabular: Boolean = false,
    content: RichText.() -> Unit,
) {
    val rich = RichText(wraps = !nowrap).apply(content)
    val shown = rich.annotated()
    val plain = rich.plain()
    val style = remember(size, weight, lh, tabular) { coachTextStyle(size.toFloat(), weight, lh, tabular = tabular) }
    Text(
        text = shown,
        modifier = if (shown.length != plain.length) modifier.semantics { contentDescription = plain } else modifier,
        color = color,
        softWrap = !nowrap,
        maxLines = if (nowrap) 1 else Int.MAX_VALUE,
        overflow = if (nowrap) TextOverflow.Ellipsis else TextOverflow.Clip,
        style = style,
    )
}

// ---- surfaces ----

val CardShape = RoundedCornerShape(28.dp)
val TileShape = RoundedCornerShape(22.dp)
val BoxShape = RoundedCornerShape(18.dp)

/** CSS border width of cards, chips and outlined buttons. Compose draws it inside the bounds, like `border-box`. */
val Hairline = 2.dp

/** Clickable with the ripple clipped to [shape]; small targets still take touches up to 64 dp around them. */
fun Modifier.tap(shape: Shape, enabled: Boolean = true, role: Role? = Role.Button, label: String? = null, onClick: () -> Unit): Modifier =
    clip(shape).clickable(enabled = enabled, onClickLabel = label, role = role, onClick = onClick)

/** `background: var(--card); border: 2px solid var(--cardBd); border-radius: 28px`. */
@Composable
fun Modifier.cardSurface(shape: Shape = CardShape): Modifier {
    val c = CoachTheme.colors
    return background(c.card, shape).border(Hairline, c.cardBd, shape)
}

/** `border-bottom: 2px solid <color>` of a list row; like in CSS it adds its width to the row's height. */
fun Modifier.bottomBorder(color: Color, width: Dp = Hairline): Modifier = drawBehind {
    val w = width.toPx()
    drawRect(color, Offset(0f, size.height - w), Size(size.width, w))
}.padding(bottom = width)

/**
 * The design's card. [padV] / [padH] are the CSS padding, [gap] the flex gap between children. With
 * [onClick] the whole card is the touch target.
 */
@Composable
fun CoachCard(
    modifier: Modifier = Modifier,
    padV: Dp = 34.dp,
    padH: Dp = 40.dp,
    gap: Dp = 20.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .then(if (onClick != null) Modifier.tap(CardShape, onClick = onClick) else Modifier)
            .cardSurface()
            .padding(horizontal = padH + Hairline, vertical = padV + Hairline),
        verticalArrangement = Arrangement.spacedBy(gap),
        content = content,
    )
}

/** Card heading: 28 px / 700. */
@Composable
fun CardTitle(text: String, modifier: Modifier = Modifier) = T(text, 28, modifier, weight = FontWeight.W700)

/** Free-form message inside a card or on the page (empty states, notices): the design's body text. */
@Composable
fun BodyText(text: String, modifier: Modifier = Modifier, color: Color = CoachTheme.colors.ink1) =
    T(text, 21, modifier, color = color, lh = 1.55f)

/** Page title: 50 px / 700, letter-spacing −1 px ([size] 46 on the report and parking pages). */
@Composable
fun PageTitle(text: String, modifier: Modifier = Modifier, size: Int = 50) =
    T(text, size, modifier, weight = FontWeight.W700, spacing = -1f, nowrap = true)

/** Main-area padding of a screen as in the design's CSS (top, right, bottom, left); halved sideways in the narrow window. */
@Composable
fun screenPadding(top: Dp, end: Dp, bottom: Dp, start: Dp): PaddingValues =
    if (LocalWideLayout.current) PaddingValues(start, top, end, bottom) else PaddingValues(start / 2, top, end / 2, bottom)

@Composable
fun Dot(color: Color, size: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).background(color, CircleShape))
}

// ---- chips and pills ----

private val ChipShape = RoundedCornerShape(30.dp)
private val PillShape = RoundedCornerShape(40.dp)

/** Event count chip: `padding: 7px 16px; border-radius: 30px; background: tile; border: 2px solid line; font-size: 20px`. */
@Composable
fun EventChip(label: String, count: Int?, modifier: Modifier = Modifier, dot: Color? = null) {
    val c = CoachTheme.colors
    Row(
        modifier
            .background(c.tile, ChipShape)
            .border(Hairline, c.line, ChipShape)
            .padding(horizontal = 16.dp + Hairline, vertical = 7.dp + Hairline),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (dot != null) Dot(dot, 10.dp)
        RichT(20, nowrap = true) {
            if (count == null) {
                text(label)
            } else {
                text("$label ")
                bold(count.toString())
            }
        }
    }
}

/** Event types with at least one occurrence, in the model's order. */
fun shownEvents(events: Map<EventType, Int>): List<EventType> = EventType.entries.filter { (events[it] ?: 0) > 0 }

/** Wrapping row of event chips; "급가속·급제동 없음" when there were none. */
@Composable
fun EventChips(events: Map<EventType, Int>, modifier: Modifier = Modifier, dots: Boolean = false) {
    val shown = shownEvents(events)
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (shown.isEmpty()) {
            EventChip("급가속·급제동 없음", null, dot = CoachColors.ScoreGood)
        } else {
            shown.forEach { EventChip(it.label, events[it], dot = if (dots) eventColor(it) else null) }
        }
    }
}

/** Status pill of the home header: `padding: 12px 24px; border-radius: 40px; background: track; 22px / 500`. */
@Composable
fun StatusPill(text: String, dot: Color, modifier: Modifier = Modifier) {
    val c = CoachTheme.colors
    Row(
        modifier.background(c.track, PillShape).padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Dot(dot, 14.dp)
        T(text, 22, weight = FontWeight.W500, color = c.ink1, nowrap = true)
    }
}

/** Outlined pill on the card colour (trip list header): `padding: 12px 24px; border-radius: 40px`. */
@Composable
fun CardPill(text: String, modifier: Modifier = Modifier) {
    val c = CoachTheme.colors
    Box(modifier.cardSurface(PillShape).padding(horizontal = 24.dp + Hairline, vertical = 12.dp + Hairline)) {
        T(text, 22, weight = FontWeight.W500, color = c.ink1, nowrap = true)
    }
}

/** Tinted label pill ("주차", the scenario name): `padding: 8px 18px; border-radius: 30px; 20px`. */
@Composable
fun TagPill(text: String, modifier: Modifier = Modifier, accent: Boolean = true) {
    val c = CoachTheme.colors
    Box(modifier.background(if (accent) c.tint else c.surface, ChipShape).padding(horizontal = 18.dp, vertical = 8.dp)) {
        T(text, 20, weight = if (accent) FontWeight.W700 else FontWeight.W500, color = if (accent) c.acc else c.ink1, nowrap = true)
    }
}

/** Legend entry: a dot (or the chart's 18×10 swatch) and a label. */
@Composable
fun LegendItem(color: Color, label: String, size: Int, textColor: Color, modifier: Modifier = Modifier, swatch: Boolean = false, dot: Dp = 10.dp, gap: Dp = 8.dp) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(gap)) {
        if (swatch) {
            Box(Modifier.size(18.dp, 10.dp).background(color, RoundedCornerShape(2.dp)))
        } else {
            Dot(color, dot)
        }
        T(label, size, color = textColor, nowrap = true)
    }
}

/** Note with the grey "i" badge (home): `gap: 12px; font-size: 20px; line-height: 1.5; color: ink2`. */
@Composable
fun InfoLine(text: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        InfoBadge(24.dp, Modifier.padding(top = 2.dp))
        T(text, 20, Modifier.weight(1f), color = CoachTheme.colors.ink2, lh = 1.5f)
    }
}

/** "동의함 · 날짜" line of the settings cards: an 18 px check and 18 px ink3 text. */
@Composable
fun CheckLine(text: String, modifier: Modifier = Modifier) {
    val c = CoachTheme.colors
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CoachIconView(CoachIcons.Check, 18.dp, c.acc)
        T(text, 18, color = c.ink3)
    }
}

// ---- buttons ----

/** Filled accent pill: `padding: 14px 28px; border-radius: 40px; background: acc; color: #fff; 23px / 700`. */
@Composable
fun PrimaryPill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, large: Boolean = false, enabled: Boolean = true) {
    val c = CoachTheme.colors
    Box(
        modifier
            .alpha(if (enabled) 1f else 0.4f)
            .tap(PillShape, enabled, onClick = onClick)
            .background(c.acc, PillShape)
            .padding(horizontal = if (large) 36.dp else 28.dp, vertical = if (large) 22.dp else 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        T(text, 23, weight = FontWeight.W700, color = CoachColors.White, nowrap = true)
    }
}

/**
 * Outlined pill: `padding: 14px 26px; border-radius: 40px; border: 2px solid; 20px / 700`. [danger] is the
 * design's red variant (동의 철회); the neutral one uses the line and ink colours.
 */
@Composable
fun OutlinePill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, danger: Boolean = false, large: Boolean = false, enabled: Boolean = true) {
    val c = CoachTheme.colors
    Box(
        modifier
            .alpha(if (enabled) 1f else 0.4f)
            .tap(PillShape, enabled, onClick = onClick)
            .border(Hairline, if (danger) CoachColors.DangerBorder else c.line, PillShape)
            .padding(horizontal = (if (large) 34.dp else 26.dp) + Hairline, vertical = (if (large) 22.dp else 14.dp) + Hairline),
        contentAlignment = Alignment.Center,
    ) {
        T(text, if (large) 23 else 20, weight = FontWeight.W700, color = if (danger) CoachColors.Red else c.ink, nowrap = true)
    }
}

/** Small outlined pill ("모두 지우기"): `padding: 10px 22px; border-radius: 30px; border: 2px solid line; 19px / 700`. */
@Composable
fun SmallPill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, danger: Boolean = false, enabled: Boolean = true) {
    val c = CoachTheme.colors
    val active = danger && enabled
    Box(
        modifier
            .tap(ChipShape, enabled, onClick = onClick)
            .border(Hairline, if (active) CoachColors.DangerBorder else c.line, ChipShape)
            .padding(horizontal = 22.dp + Hairline, vertical = 10.dp + Hairline),
        contentAlignment = Alignment.Center,
    ) {
        T(text, 19, weight = FontWeight.W700, color = if (active) CoachColors.Red else if (enabled) c.ink else c.ink4, nowrap = true)
    }
}

/** Header action of the report and habits pages: `padding: 16px 28px; border-radius: 28px; card colours; 22px / 700`. */
@Composable
fun HeaderButton(text: String, icon: CoachIcon, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = CoachTheme.colors.ink) {
    Row(
        modifier
            .tap(CardShape, onClick = onClick)
            .cardSurface()
            .padding(horizontal = 28.dp + Hairline, vertical = 16.dp + Hairline),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CoachIconView(icon, 24.dp, color)
        T(text, 22, weight = FontWeight.W700, color = color, nowrap = true)
    }
}

/** Back button of the report page: 72×72 plus the 2 px border, radius 28, a 30 px chevron. */
@Composable
fun BackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(72.dp + Hairline * 2)
            .tap(CardShape, label = "뒤로", onClick = onClick)
            .cardSurface()
            .semantics { contentDescription = "뒤로" },
        contentAlignment = Alignment.Center,
    ) {
        CoachIconView(CoachIcons.Back, 30.dp, CoachTheme.colors.ink)
    }
}

/** − / + of a stepper: 52×52 plus the 2 px border, radius 18, surface colour, a 22 px icon. */
@Composable
fun StepButton(icon: CoachIcon, description: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = CoachTheme.colors
    Box(
        modifier
            .size(52.dp + Hairline * 2)
            .tap(BoxShape, enabled, onClick = onClick)
            .background(c.surface, BoxShape)
            .border(Hairline, c.line, BoxShape)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        CoachIconView(icon, 22.dp, if (enabled) c.ink else c.ink4)
    }
}

/** Speed buttons of the debug demo rows: 84×56 plus the border, radius 16, tile colour, 20px / 700. */
@Composable
fun DemoButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = CoachTheme.colors
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier
            .alpha(if (enabled) 1f else 0.4f)
            .size(84.dp + Hairline * 2, 56.dp + Hairline * 2)
            .tap(shape, enabled, onClick = onClick)
            .background(c.tile, shape)
            .border(Hairline, c.line, shape),
        contentAlignment = Alignment.Center,
    ) {
        T(text, 20, weight = FontWeight.W700, nowrap = true)
    }
}

/** Round transport button of the parking page: [size] across, transparent or filled with the accent. */
@Composable
fun RoundIconButton(icon: CoachIcon, description: String, size: Dp, iconSize: Dp, onClick: () -> Unit, modifier: Modifier = Modifier, filled: Boolean = false) {
    val c = CoachTheme.colors
    Box(
        modifier
            .size(size)
            .tap(CircleShape, onClick = onClick)
            .then(if (filled) Modifier.background(c.acc, CircleShape) else Modifier)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        CoachIconView(icon, iconSize, if (filled) CoachColors.White else c.ink)
    }
}

/** One option of the scenario grid: `height: 60px; border-radius: 18px; border: 2px solid; 21px / 700`. */
@Composable
fun ChoiceButton(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = CoachTheme.colors
    Box(
        modifier
            .height(60.dp + Hairline * 2)
            .clip(BoxShape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .background(if (selected) c.tint else c.surface, BoxShape)
            .border(Hairline, if (selected) c.acc else c.line, BoxShape),
        contentAlignment = Alignment.Center,
    ) {
        T(label, 21, weight = FontWeight.W700, color = if (selected) c.acc else c.ink, nowrap = true)
    }
}

/** Single choice as an equal-width grid of [ChoiceButton]s, [columns] per row, 10 px apart. */
@Composable
fun <V> ChoiceGrid(options: List<Pair<String, V>>, selected: V?, columns: Int, modifier: Modifier = Modifier, onSelect: (V) -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        options.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (label, value) -> ChoiceButton(label, value == selected, { onSelect(value) }, Modifier.weight(1f)) }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

// ---- segmented controls ----

/** How one of the design's three segmented controls is drawn. */
enum class SegmentedStyle { Period, Side, Retention }

/**
 * - [SegmentedStyle.Period] (습관 분석): card-coloured container with a border, padding 6, radius 28;
 *   items `padding: 12px 30px`, radius 30, 22 px; selected = accent fill, white text.
 * - [SegmentedStyle.Side] (목표 칸): track-coloured container, padding 5, radius 20; equal-width items
 *   52 high, radius 15, 21 px; selected = surface fill, accent text.
 * - [SegmentedStyle.Retention] (보관 기간): track-coloured container, padding 5, radius 30; items
 *   `padding: 10px 26px`, radius 24, 20 px; selected = accent fill, white text.
 */
@Composable
fun <V> Segmented(options: List<Pair<String, V>>, selected: V?, style: SegmentedStyle, modifier: Modifier = Modifier, enabled: Boolean = true, onSelect: (V) -> Unit) {
    val c = CoachTheme.colors
    val container = when (style) {
        SegmentedStyle.Period -> Modifier.cardSurface(CardShape).padding(6.dp + Hairline)
        SegmentedStyle.Side -> Modifier.background(c.track, RoundedCornerShape(20.dp)).padding(5.dp)
        SegmentedStyle.Retention -> Modifier.background(c.track, RoundedCornerShape(30.dp)).padding(5.dp)
    }
    Row(modifier.alpha(if (enabled) 1f else 0.4f).then(container), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { (label, value) ->
            val on = value == selected
            val shape = RoundedCornerShape(
                when (style) {
                    SegmentedStyle.Period -> 30.dp
                    SegmentedStyle.Side -> 15.dp
                    SegmentedStyle.Retention -> 24.dp
                },
            )
            val fill = when {
                !on -> Color.Transparent
                style == SegmentedStyle.Side -> c.surface
                else -> c.acc
            }
            val ink = when {
                !on -> c.ink2
                style == SegmentedStyle.Side -> c.acc
                else -> CoachColors.White
            }
            val item = when (style) {
                SegmentedStyle.Period -> Modifier.padding(horizontal = 30.dp, vertical = 12.dp)
                SegmentedStyle.Side -> Modifier.height(52.dp)
                SegmentedStyle.Retention -> Modifier.padding(horizontal = 26.dp, vertical = 10.dp)
            }
            Box(
                (if (style == SegmentedStyle.Side) Modifier.weight(1f) else Modifier)
                    .clip(shape)
                    .selectable(selected = on, enabled = enabled, role = Role.RadioButton, onClick = { onSelect(value) })
                    .background(fill, shape)
                    .then(item),
                contentAlignment = Alignment.Center,
            ) {
                val size = when (style) {
                    SegmentedStyle.Period -> 22
                    SegmentedStyle.Side -> 21
                    SegmentedStyle.Retention -> 20
                }
                T(label, size, weight = FontWeight.W700, color = ink, nowrap = true)
            }
        }
    }
}

// ---- switch and slider ----

/** CSS `box-shadow: 0 <dy> <blur> <color>` as an Android shadow layer (its radius maps to sigma = 0.57735 r + 0.5). */
private fun shadowPaint(fill: Color, shadow: Color, dyPx: Float, blurPx: Float): android.graphics.Paint =
    android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = fill.toArgb()
        setShadowLayer(((blurPx / 2f - 0.5f) / 0.57735f).coerceAtLeast(0.01f), 0f, dyPx, shadow.toArgb())
    }

/**
 * The design's switch: 76×42, radius 21, `acc` when on and `off` when off; a 34 px white knob 4 px from the
 * edge with `box-shadow: 0 1px 3px rgba(0,0,0,0.25)`. Pass null as [onCheckedChange] when a parent row toggles.
 */
@Composable
fun CoachSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = CoachTheme.colors
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .alpha(if (enabled) 1f else 0.4f)
            .size(76.dp, 42.dp)
            .then(
                if (onCheckedChange != null) {
                    Modifier.toggleable(checked, interaction, indication = null, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
                } else {
                    Modifier
                },
            )
            .drawWithCache {
                val knob = shadowPaint(CoachColors.White, CoachColors.KnobShadow, 1.dp.toPx(), 3.dp.toPx())
                val radius = CornerRadius(size.height / 2f)
                val r = 17.dp.toPx()
                val inset = 4.dp.toPx()
                onDrawBehind {
                    drawRoundRect(if (checked) c.acc else c.off, cornerRadius = radius)
                    val cx = if (checked) size.width - inset - r else inset + r
                    drawIntoCanvas { it.nativeCanvas.drawCircle(cx, size.height / 2f, r, knob) }
                }
            },
    )
}

/**
 * Setting with a switch (설정): title 23 px / 700, subtitle 19 px ink3. The whole row toggles, as before;
 * only the switch is drawn.
 */
@Composable
fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, titleSize: Int = 23) {
    val c = CoachTheme.colors
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier
            .fillMaxWidth()
            .toggleable(checked, interaction, indication = null, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            T(title, titleSize, weight = FontWeight.W700, color = if (enabled) c.ink else c.ink4, nowrap = true)
            if (subtitle != null) T(subtitle, 19, color = c.ink3)
        }
        CoachSwitch(checked, null, enabled = enabled)
    }
}

/**
 * The design's slider: a 40 px high strip, 14 px side margins; track 10 high (radius 5, `track`), accent
 * fill, a 24 px accent thumb with `box-shadow: 0 1px 4px rgba(0,0,0,0.2)`. A tap or a horizontal drag sets
 * the value; a vertical drag is left to the scrolling page around it.
 */
@Composable
fun CoachSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val c = CoachTheme.colors
    val change by rememberUpdatedState(onValueChange)
    val finished by rememberUpdatedState(onValueChangeFinished)
    val span = valueRange.endInclusive - valueRange.start
    val fraction = if (span > 0f) ((value - valueRange.start) / span).coerceIn(0f, 1f) else 0f
    Box(
        modifier
            .fillMaxWidth()
            .height(40.dp)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(value, valueRange) }
            .pointerInput(valueRange) {
                fun valueAt(x: Float): Float {
                    val margin = 14.dp.toPx()
                    val f = ((x - margin) / (size.width - 2 * margin).coerceAtLeast(1f)).coerceIn(0f, 1f)
                    return valueRange.start + f * span
                }
                detectTapGestures(onTap = {
                    change(valueAt(it.x))
                    finished?.invoke()
                })
            }
            .pointerInput(valueRange) {
                fun valueAt(x: Float): Float {
                    val margin = 14.dp.toPx()
                    val f = ((x - margin) / (size.width - 2 * margin).coerceAtLeast(1f)).coerceIn(0f, 1f)
                    return valueRange.start + f * span
                }
                detectHorizontalDragGestures(
                    onDragStart = { change(valueAt(it.x)) },
                    onDragEnd = { finished?.invoke() },
                    onDragCancel = { finished?.invoke() },
                ) { input, _ ->
                    input.consume()
                    change(valueAt(input.position.x))
                }
            }
            .drawWithCache {
                val thumb = shadowPaint(c.acc, CoachColors.ThumbShadow, 1.dp.toPx(), 4.dp.toPx())
                val margin = 14.dp.toPx()
                val h = 10.dp.toPx()
                val radius = CornerRadius(h / 2f)
                onDrawBehind {
                    val w = size.width - 2 * margin
                    val top = (size.height - h) / 2f
                    drawRoundRect(c.track, Offset(margin, top), Size(w, h), radius)
                    if (fraction > 0f) drawRoundRect(c.acc, Offset(margin, top), Size(w * fraction, h), radius)
                    drawIntoCanvas { it.nativeCanvas.drawCircle(margin + w * fraction, size.height / 2f, 12.dp.toPx(), thumb) }
                }
            },
    )
}

/**
 * A value with − / + buttons (one tap = one [step]) above the slider: `label <b>value</b>` at 22 px / 500.
 * [onCommit] runs on a tap or when the slider is released, so callers can skip expensive work while it is dragged.
 */
@Composable
fun StepperSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    format: (Float) -> String,
    modifier: Modifier = Modifier,
    onCommit: (Float) -> Unit,
) {
    var local by remember(value) { mutableFloatStateOf(value) }
    fun snap(v: Float) = ((v / step).roundToInt() * step).coerceIn(range.start, range.endInclusive)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RichT(22, Modifier.weight(1f), weight = FontWeight.W500, nowrap = true) {
                text("$label ")
                bold(format(local))
            }
            StepButton(
                CoachIcons.Minus, "$label 줄이기",
                onClick = {
                    local = snap(local - step)
                    onCommit(local)
                },
                enabled = local > range.start + step / 2,
            )
            StepButton(
                CoachIcons.Plus, "$label 늘리기",
                onClick = {
                    local = snap(local + step)
                    onCommit(local)
                },
                enabled = local < range.endInclusive - step / 2,
            )
        }
        CoachSlider(local, { local = snap(it) }, range, onValueChangeFinished = { onCommit(local) })
    }
}

// ---- numbers ----

/**
 * Sizes of one of the design's stat blocks: label, value (700) and unit (500, ink2) in px, the unit's left
 * margin, the gap under the label, the value's line-height, and which ink the label uses.
 */
enum class StatStyle(val label: Int, val value: Int, val unit: Int, val unitGap: Int, val gap: Int, val lh: Float, val labelInk3: Boolean) {
    /** Report summary: 20 / 44 / 20. */
    Summary(20, 44, 20, 6, 6, 1f, false),

    /** 습관 분석: 18 / 36 / 18. */
    Habits(18, 36, 18, 5, 4, 1.1f, true),

    /** Report "주차" card: 18 / 32 / 18. */
    Parking(18, 32, 18, 4, 2, CssNormalLineHeight, true),

    /** Report "브레이크 사용" card: 16 / 28 / 16. */
    Brake(16, 28, 16, 3, 2, CssNormalLineHeight, true),
}

@Composable
fun Stat(label: String, value: String, unit: String?, style: StatStyle, modifier: Modifier = Modifier, valueColor: Color = Color.Unspecified) =
    Stat(label, listOf(value to unit), style, modifier, valueColor)

/** [values] are number / unit pairs drawn on one baseline ("1시간 5분" is two pairs). */
@Composable
fun Stat(label: String, values: List<Pair<String, String?>>, style: StatStyle, modifier: Modifier = Modifier, valueColor: Color = Color.Unspecified) {
    val c = CoachTheme.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(style.gap.dp)) {
        T(label, style.label, color = if (style.labelInk3) c.ink3 else c.ink2, nowrap = true)
        Row {
            values.forEachIndexed { i, (value, unit) ->
                T(value, style.value, Modifier.alignByBaseline().padding(start = if (i > 0) 8.dp else 0.dp), weight = FontWeight.W700, color = valueColor, lh = style.lh, nowrap = true)
                if (unit != null) {
                    T(unit, style.unit, Modifier.alignByBaseline().padding(start = style.unitGap.dp), weight = FontWeight.W500, color = c.ink2, lh = style.lh, nowrap = true)
                }
            }
        }
    }
}

/** Length of the design's gauge track: `stroke-dasharray="471 629"` on a circle of radius 100. */
private val GaugeSweep = 471f / (2f * PI.toFloat() * 100f) * 360f

/**
 * 부드러움 score gauge: `viewBox 0 0 240 240`, a circle of radius 100 with a 24 wide round-capped stroke,
 * starting at 135° and running 471 of its 629 units; the value arc covers [score] % of that. The number is
 * [numberSize] px / 700 in the accent colour (line-height 1), with "부드러움" 8 px below.
 */
@Composable
fun Gauge(score: Int?, size: Dp, numberSize: Int, modifier: Modifier = Modifier) {
    val c = CoachTheme.colors
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val k = this.size.minDimension / 240f
            val stroke = Stroke(24f * k, cap = StrokeCap.Round)
            val topLeft = Offset(20f * k, 20f * k)
            val arc = Size(200f * k, 200f * k)
            drawArc(c.tint, 135f, GaugeSweep, false, topLeft, arc, style = stroke)
            if (score != null) {
                val sweep = GaugeSweep * score.coerceIn(0, 100) / 100f
                if (sweep > 0f) {
                    drawArc(c.acc, 135f, sweep, false, topLeft, arc, style = stroke)
                } else {
                    // A zero-length dash with round caps is still a dot in SVG.
                    val a = 135.0 * PI / 180.0
                    drawCircle(c.acc, 12f * k, Offset((120f + 100f * cos(a).toFloat()) * k, (120f + 100f * sin(a).toFloat()) * k))
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            T(score?.toString() ?: "—", numberSize, weight = FontWeight.W700, color = if (score != null) c.acc else c.ink4, lh = 1f, nowrap = true)
            T("부드러움", 21, Modifier.padding(top = 8.dp), color = c.ink2, nowrap = true)
        }
    }
}

data class BarPart(val label: String, val value: Float, val color: Color)

/** One 18 px bar (radius 9) split into parts 3 px apart. */
@Composable
fun StackedBar(parts: List<BarPart>, modifier: Modifier = Modifier) {
    val track = CoachTheme.colors.track
    Canvas(modifier.fillMaxWidth().height(18.dp)) {
        val total = parts.sumOf { it.value.toDouble() }.toFloat()
        val outline = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(size.height / 2f))) }
        clipPath(outline) {
            val shown = parts.filter { it.value > 0f }
            if (total <= 0f || shown.isEmpty()) {
                drawRect(track)
            } else {
                val gap = 3.dp.toPx()
                val usable = size.width - gap * (shown.size - 1)
                var x = 0f
                shown.forEach { p ->
                    val w = usable * p.value / total
                    drawRect(p.color, Offset(x, 0f), Size(w, size.height))
                    x += w + gap
                }
            }
        }
    }
}

/** Legend of a [StackedBar]: dot, label and the share in bold; [size] 18 (ink2) on the report, 19 (ink1) in 습관 분석. */
@Composable
fun BarLegend(parts: List<BarPart>, size: Int, color: Color, gap: Dp, modifier: Modifier = Modifier) {
    val total = parts.sumOf { it.value.toDouble() }.toFloat()
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(gap), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        parts.forEach { p ->
            val share = if (total > 0f) p.value / total else 0f
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Dot(p.color, 10.dp)
                RichT(size, color = color, nowrap = true) {
                    text("${p.label} ")
                    bold("${(share * 100).roundToInt()}%")
                }
            }
        }
    }
}

/** "10 km당 횟수" row: `grid-template-columns: 150px 1fr 56px; gap: 20px`; the bar is [value] / [max] wide. */
@Composable
fun RateRow(label: String, value: Float, max: Float, color: Color, modifier: Modifier = Modifier) {
    val c = CoachTheme.colors
    val shape = RoundedCornerShape(7.dp)
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        T(label, 22, Modifier.width(150.dp), nowrap = true)
        Box(Modifier.weight(1f).height(14.dp).clip(shape).background(c.track)) {
            val f = if (max > 0f) (value / max).coerceIn(0f, 1f) else 0f
            if (f > 0f) Box(Modifier.fillMaxWidth(f).fillMaxHeight().background(color, shape))
        }
        T(fmt1(value), 24, Modifier.width(56.dp), weight = FontWeight.W700, align = TextAlign.End, nowrap = true, tabular = true)
    }
}

// ---- coaching ----

/** Icon of a coaching tile: the design's four, and for the tips it does not show an icon in the same set. */
private fun tipIcon(tip: Tip): Pair<CoachIcon?, Dp> = when (tip.title) {
    "급제동이 잦아요" -> CoachIcons.Bolt to 28.dp
    "출발이 급해요" -> CoachIcons.Start to 26.dp
    "가속이 강한 편이에요" -> CoachIcons.Bars to 28.dp
    "브레이크로 감속하는 비율이 높아요" -> CoachIcons.Refresh to 28.dp
    "가속 직후 제동이 잦아요" -> CoachIcons.Trips to 28.dp
    "주차할 때 전·후진 전환이 많아요" -> null to 28.dp
    else -> (if (tip.praise) CoachIcons.Check else CoachIcons.Bars) to 28.dp
}

/** 60 px circle in the tint colour with the tip's icon in the accent colour. */
@Composable
private fun TipIcon(tip: Tip) {
    val c = CoachTheme.colors
    val (icon, size) = tipIcon(tip)
    Box(Modifier.size(60.dp).background(c.tint, CircleShape), contentAlignment = Alignment.Center) {
        if (icon != null) {
            CoachIconView(icon, size, c.acc)
        } else {
            T("P", 30, weight = FontWeight.W700, color = c.acc, lh = 1f)
        }
    }
}

/**
 * Coaching tile: `background: tile; border-radius: 22px; padding: 28px`; title 26 px / 700, body 21 px with
 * line-height 1.55 in ink1. [horizontal] is the report's layout (icon left, gap 22); otherwise the icon is on top (gap 16).
 */
@Composable
fun TipTile(tip: Tip, modifier: Modifier = Modifier, horizontal: Boolean = false) {
    val c = CoachTheme.colors
    val base = modifier.background(c.tile, TileShape).padding(28.dp)
    if (horizontal) {
        Row(base, horizontalArrangement = Arrangement.spacedBy(22.dp), verticalAlignment = Alignment.Top) {
            TipIcon(tip)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                T(tip.title, 26, weight = FontWeight.W700)
                T(tip.body, 21, color = c.ink1, lh = 1.55f)
            }
        }
    } else {
        Column(base, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            TipIcon(tip)
            T(tip.title, 26, weight = FontWeight.W700)
            T(tip.body, 21, color = c.ink1, lh = 1.55f)
        }
    }
}

/** Tiles [columns] per row with equal heights (CSS grid, gap 20); one per row in the narrow window. */
@Composable
fun TipGrid(tips: List<Tip>, columns: Int, modifier: Modifier = Modifier, horizontal: Boolean = false) {
    if (!LocalWideLayout.current) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(20.dp)) {
            tips.forEach { TipTile(it, Modifier.fillMaxWidth(), horizontal = true) }
        }
        return
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        tips.chunked(columns).forEach { row ->
            Row(Modifier.height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                row.forEach { TipTile(it, Modifier.weight(1f).fillMaxHeight(), horizontal) }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

// ---- charts ----

/**
 * "속도와 브레이크": the design's SVG (`viewBox 0 0 1000 210`, stretched to the card width × 278 px with
 * `preserveAspectRatio="none"`) drawn from the trip's timeline. Grid lines at y = 20 / 110 / 200 mark the
 * axis maximum, its half and zero; brake time is shaded from y = 10; the speed line keeps its 3.5 px width
 * (non-scaling stroke); events are dashed lines with a dot at y = 14. Everything else stretches with the
 * viewBox exactly as the browser draws it.
 */
@Composable
fun SpeedChart(record: TripRecord, modifier: Modifier = Modifier) {
    val c = CoachTheme.colors
    val points = record.timeline
    if (points.size < 2) return
    val totalS = max(points.last().tS, 1).toFloat()
    // Axis top: the next multiple of 20 km/h (100 for the design's sample trip), at least 40.
    val axisMax = max(40, ceil(points.maxOf { it.kmh } / 20f).toInt() * 20)
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.width(48.dp).height(278.dp)) {
            listOf(axisMax, axisMax / 2, 0).forEachIndexed { i, v ->
                T(v.toString(), 16, Modifier.align(Alignment.TopEnd).offset(y = (16 + 119 * i).dp), color = c.ink4, nowrap = true)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Canvas(Modifier.fillMaxWidth().height(278.dp)) {
                val sx = size.width / 1000f
                val sy = size.height / 210f
                fun vx(t: Float) = t / totalS * 1000f
                fun vy(kmh: Float) = 200f - kmh / axisMax * 180f

                // Runs of seconds that belong together (a gap over 5 s is a break in the recording).
                val runs = mutableListOf<MutableList<Int>>()
                points.indices.forEach { i ->
                    if (i == 0 || points[i].tS - points[i - 1].tS > 5) runs += mutableListOf<Int>()
                    runs.last() += i
                }

                withTransform({ scale(sx, sy, pivot = Offset.Zero) }) {
                    drawLine(c.hair, Offset(0f, 20f), Offset(1000f, 20f), strokeWidth = 1f)
                    drawLine(c.hair, Offset(0f, 110f), Offset(1000f, 110f), strokeWidth = 1f)
                    drawLine(c.hair2, Offset(0f, 200f), Offset(1000f, 200f), strokeWidth = 1.5f)

                    var bandStart = -1
                    var bandEnd = -1
                    fun flush() {
                        if (bandStart >= 0) {
                            val x0 = vx(bandStart.toFloat())
                            val x1 = vx(min((bandEnd + 1).toFloat(), totalS))
                            drawRect(CoachColors.BrakeBand, Offset(x0, 10f), Size(max(x1 - x0, 1f), 190f))
                        }
                        bandStart = -1
                    }
                    points.forEach { p ->
                        val braking = (p.brake ?: 0f) >= 0.5f
                        if (braking && bandStart >= 0 && p.tS == bandEnd + 1) {
                            bandEnd = p.tS
                        } else {
                            flush()
                            if (braking) {
                                bandStart = p.tS
                                bandEnd = p.tS
                            }
                        }
                    }
                    flush()

                    runs.forEach { run ->
                        if (run.size < 2) return@forEach
                        val area = Path().apply {
                            moveTo(vx(points[run.first()].tS.toFloat()), 200f)
                            run.forEach { lineTo(vx(points[it].tS.toFloat()), vy(points[it].kmh)) }
                            lineTo(vx(points[run.last()].tS.toFloat()), 200f)
                            close()
                        }
                        drawPath(area, c.accArea)
                    }
                }

                val line = Path()
                runs.forEach { run ->
                    run.forEachIndexed { n, i ->
                        val x = vx(points[i].tS.toFloat()) * sx
                        val y = vy(points[i].kmh) * sy
                        if (n == 0) line.moveTo(x, y) else line.lineTo(x, y)
                    }
                }
                drawPath(line, c.acc, style = Stroke(3.5.dp.toPx(), join = StrokeJoin.Round))

                withTransform({ scale(sx, sy, pivot = Offset.Zero) }) {
                    val dash = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))
                    record.events.forEach { e ->
                        val x = vx(e.atS.coerceIn(0f, totalS))
                        val color = eventColor(e.type)
                        drawLine(color, Offset(x, 14f), Offset(x, 200f), strokeWidth = 2f, pathEffect = dash)
                        drawCircle(color, 7f, Offset(x, 14f))
                        drawCircle(CoachColors.White, 7f, Offset(x, 14f), style = Stroke(3f))
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                (0..4).forEach { T(minSec(totalS * it / 4f), 16, color = c.ink4, nowrap = true) }
            }
        }
    }
}

/**
 * "주별 급가속·급제동": a 200 px plot with a 2 px baseline; per week a 120 px column, 20 px apart, with a
 * 64 px bar (top radius 12, the highest 150 px) and its value (22 px / 700) 8 px above; the week label
 * (18 px, ink3) 20 px under the baseline. Weeks without driving show "—" and no bar. When the weeks do not
 * fit at that size (90 days = 13 weeks, or the narrow window) the columns share the width and the labels shrink.
 */
@Composable
fun WeeklyBars(weeks: List<WeekBucket>, modifier: Modifier = Modifier) {
    val c = CoachTheme.colors
    val measurer = rememberTextMeasurer()
    val maxRate = max(weeks.mapNotNull { it.harshPer10Km }.maxOrNull() ?: 0f, 2f)
    val summary = weeks.joinToString(", ") { w -> "${fmtDay(w.startEpochMs)} 주 ${w.harshPer10Km?.let { fmt1(it) } ?: "기록 없음"}" }
    Canvas(modifier.fillMaxWidth().height(248.dp).semantics { contentDescription = summary }) {
        val n = weeks.size
        val plot = 200.dp.toPx()
        drawRect(c.hair2, Offset(0f, plot), Size(size.width, 2.dp.toPx()))
        if (n == 0) return@Canvas

        val designFits = n * 120.dp.toPx() + (n - 1) * 20.dp.toPx() + 40.dp.toPx() <= size.width
        val side = if (designFits) 20.dp.toPx() else 8.dp.toPx()
        val gap = if (designFits) 20.dp.toPx() else 8.dp.toPx()
        val col = if (designFits) 120.dp.toPx() else (size.width - 2 * side - (n - 1) * gap) / n
        val barW = if (designFits) 64.dp.toPx() else min(64.dp.toPx(), col * 0.62f)

        val values = weeks.map { w -> w.harshPer10Km?.let { fmt1(it) } ?: "—" }
        val longDates = weeks.map { "${fmtDay(it.startEpochMs)} 주" }
        val shortDates = weeks.map { fmtDay(it.startEpochMs) }

        /** The largest of [sizes] at which every label fits its column. */
        fun fit(labels: List<String>, weight: FontWeight, sizes: List<Int>): Pair<TextStyle, Boolean> {
            for (s in sizes) {
                val style = coachTextStyle(s.toFloat(), weight)
                if (labels.all { measurer.measure(it, style, softWrap = false, maxLines = 1).size.width <= col + gap * 0.5f }) return style to true
            }
            return coachTextStyle(sizes.last().toFloat(), weight) to false
        }

        val valueStyle = if (designFits) coachTextStyle(22f, FontWeight.W700) else fit(values, FontWeight.W700, listOf(22, 18, 16, 14, 12)).first
        val (dateStyle, dates) = if (designFits) {
            coachTextStyle(18f) to longDates
        } else {
            val long = fit(longDates, FontWeight.W400, listOf(18, 16))
            if (long.second) long.first to longDates else fit(shortDates, FontWeight.W400, listOf(18, 16, 14, 12)).first to shortDates
        }

        weeks.forEachIndexed { i, w ->
            val cx = side + i * (col + gap) + col / 2f
            val rate = w.harshPer10Km
            val h = if (rate == null) 0f else 150.dp.toPx() * (rate / maxRate).coerceIn(0f, 1f)
            if (h > 0f) {
                val r = min(12.dp.toPx(), min(h, barW / 2f))
                val bar = Path().apply {
                    addRoundRect(RoundRect(cx - barW / 2f, plot - h, cx + barW / 2f, plot, topLeftCornerRadius = CornerRadius(r), topRightCornerRadius = CornerRadius(r)))
                }
                drawPath(bar, c.acc)
            }
            val value = measurer.measure(values[i], valueStyle, softWrap = false, maxLines = 1)
            drawText(value, color = if (rate == null) c.ink4 else c.ink, topLeft = Offset(cx - value.size.width / 2f, plot - h - 8.dp.toPx() - value.size.height))
            val date = measurer.measure(dates[i], dateStyle, softWrap = false, maxLines = 1)
            drawText(date, color = c.ink3, topLeft = Offset(cx - date.size.width / 2f, plot + 2.dp.toPx() + 20.dp.toPx()))
        }
    }
}

// ---- steering ----

/**
 * The design's steering wheel (`viewBox 0 0 100 100`): rim r = 42 and the three spokes 8 wide in the ink
 * colour, hub r = 10 and the top marker r = 6 in the accent, all turned by [steer] (fraction of full lock,
 * left negative) × [lockTurns] turns. Under it the position in words (18 px / 700, accent). With
 * [showTurns] a second line says about how many turns that is; the line keeps its place while the wheel is
 * centred, so the card around it does not jump when the wheel moves.
 */
@Composable
fun SteeringWheel(steer: Float, lockTurns: Double, modifier: Modifier = Modifier, size: Dp = 128.dp, showTurns: Boolean = false) {
    val c = CoachTheme.colors
    val degrees = (steer * lockTurns * 360.0).toFloat()
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(Modifier.size(size)) {
            val k = this.size.minDimension / 100f
            val mid = Offset(50f * k, 50f * k)
            rotate(degrees, mid) {
                drawCircle(c.ink, 42f * k, mid, style = Stroke(8f * k))
                drawLine(c.ink, Offset(8f * k, 50f * k), Offset(92f * k, 50f * k), strokeWidth = 8f * k)
                drawLine(c.ink, mid, Offset(50f * k, 92f * k), strokeWidth = 8f * k)
                drawCircle(c.acc, 10f * k, mid)
                drawCircle(c.acc, 6f * k, Offset(50f * k, 8f * k))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            T(steerLabel(steer), 18, weight = FontWeight.W700, color = c.acc, nowrap = true)
            if (showTurns) T(steerTurns(steer, lockTurns) ?: " ", 16, color = c.ink3, nowrap = true)
        }
    }
}

/** "약 1.3바퀴" once the wheel is turned at least 5 % of full lock. */
fun steerTurns(steer: Float, lockTurns: Double): String? =
    if (abs(steer) >= 0.05f) "약 ${String.format(Locale.KOREA, "%.1f", abs(steer) * lockTurns)}바퀴" else null

fun steerLabel(steer: Float): String {
    val pct = (abs(steer) * 100).roundToInt()
    val dir = if (steer < 0) "왼쪽" else "오른쪽"
    return when {
        pct < 3 -> "정중앙"
        pct >= 97 -> "$dir 끝까지"
        else -> "$dir $pct%"
    }
}

// ---- dialog ----

/** Confirmation in the design's language: an opaque card with the title, a note and two pills. */
@Composable
fun CoachDialog(
    title: String,
    confirm: String,
    onConfirm: () -> Unit,
    dismiss: String,
    onDismiss: () -> Unit,
    text: String? = null,
    danger: Boolean = true,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        ProvideCarTouchTargets {
            val c = CoachTheme.colors
            Column(
                Modifier
                    .padding(32.dp)
                    .widthIn(max = 640.dp)
                    .background(if (c.dark) c.card else c.surface, CardShape)
                    .border(Hairline, c.cardBd, CardShape)
                    .padding(horizontal = 40.dp + Hairline, vertical = 34.dp + Hairline),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                CardTitle(title)
                if (text != null) BodyText(text)
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.End)) {
                    OutlinePill(dismiss, onDismiss)
                    if (danger) OutlinePill(confirm, onConfirm, danger = true) else PrimaryPill(confirm, onConfirm)
                }
            }
        }
    }
}

/**
 * Card background and border for one row of a lazily built card (the trip list): each row draws its own
 * slice of the card, so the translucent fill is never painted twice; the rounded ends appear on the first
 * and last row only.
 */
fun Modifier.cardSlice(first: Boolean, last: Boolean, palette: CoachPalette): Modifier = drawBehind {
    val r = 28.dp.toPx()
    val bw = Hairline.toPx()
    val top = if (first) 0f else -2f * r
    val bottom = if (last) size.height else size.height + 2f * r
    clipRect {
        drawRoundRect(palette.card, Offset(0f, top), Size(size.width, bottom - top), CornerRadius(r))
        drawRoundRect(
            palette.cardBd,
            Offset(bw / 2f, top + bw / 2f),
            Size(size.width - bw, bottom - top - bw),
            CornerRadius(r - bw / 2f),
            style = Stroke(bw),
        )
    }
}
