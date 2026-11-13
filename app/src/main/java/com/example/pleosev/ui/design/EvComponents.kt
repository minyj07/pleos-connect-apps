package com.example.pleosev.ui.design

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import com.example.pleosev.ui.theme.EvColors
import com.example.pleosev.ui.theme.EvWeight
import com.example.pleosev.ui.theme.evText
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// ---- text ----

/**
 * Text in one of the design's styles ([evText]). [singleLine] labels never wrap and end in an ellipsis
 * when their box is too narrow.
 */
@Composable
fun EvText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
    maxLines: Int = Int.MAX_VALUE,
    textAlign: TextAlign? = null,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = if (textAlign == null) style else style.copy(textAlign = textAlign),
        overflow = TextOverflow.Ellipsis,
        softWrap = !singleLine,
        maxLines = if (singleLine) 1 else maxLines,
    )
}

// ---- gradients, shadows, CSS box model ----

/** `linear-gradient(90deg, #86ef7a, #3ee05f)`: the main buttons and the chosen segment. */
val GradientPrimary: Brush = Brush.horizontalGradient(listOf(EvColors.GradientStart, EvColors.GradientEnd))

/** `linear-gradient(90deg, #6eea6e, #3ee05f)`: the station cards' action button. */
val GradientCard: Brush = Brush.horizontalGradient(listOf(EvColors.GradientStartCard, EvColors.GradientEnd))

/** `linear-gradient(160deg, #1f5a32, #123b22)`: the illustration tiles. */
val GradientIllustration: Brush = CssLinearGradient(160f, listOf(EvColors.IllustrationStart, EvColors.IllustrationEnd))

/** CSS `linear-gradient(<angle>deg, …)`: 0deg points up, 90deg to the right; the line spans the box's corners. */
private class CssLinearGradient(private val angleDeg: Float, private val colors: List<Color>) : ShaderBrush() {
    override fun createShader(size: Size): Shader {
        val angle = Math.toRadians(angleDeg.toDouble())
        val dx = sin(angle).toFloat()
        val dy = -cos(angle).toFloat()
        val half = (abs(size.width * dx) + abs(size.height * dy)) / 2f
        val centre = Offset(size.width / 2f, size.height / 2f)
        return LinearGradientShader(
            from = Offset(centre.x - dx * half, centre.y - dy * half),
            to = Offset(centre.x + dx * half, centre.y + dy * half),
            colors = colors,
        )
    }
}

/**
 * CSS `box-shadow: 0 <offsetY> <blur> <color>` of a pill ([cornerRadius] unset) or a rounded box, drawn
 * outside the box only, as CSS does.
 */
fun Modifier.softShadow(color: Color, blur: Dp, offsetY: Dp, cornerRadius: Dp = Dp.Unspecified): Modifier = drawWithCache {
    val radius = if (cornerRadius.isSpecified) cornerRadius.toPx() else size.minDimension / 2f
    // CSS blur radius = 2 sigma; Skia: sigma = 0.57735 * radius + 0.5.
    val sigma = blur.toPx() / 2f
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color.toArgb()
        maskFilter = BlurMaskFilter(((sigma - 0.5f) / 0.57735f).coerceAtLeast(0.5f), BlurMaskFilter.Blur.NORMAL)
    }
    val box = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(radius))) }
    val dy = offsetY.toPx()
    onDrawBehind {
        clipPath(box, ClipOp.Difference) {
            drawIntoCanvas { it.nativeCanvas.drawRoundRect(0f, dy, size.width, size.height + dy, radius, radius, paint) }
        }
    }
}

/** CSS `margin-top: -<amount>`: the element moves up and takes that much less room in its column. */
fun Modifier.pullUp(amount: Dp): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val shift = amount.roundToPx()
    layout(placeable.width, (placeable.height - shift).coerceAtLeast(0)) { placeable.place(0, -shift) }
}

/** A translucent card of the design: fill, hairline border, rounded corners. */
fun Modifier.evSurface(fill: Color, border: Color, radius: Dp): Modifier {
    val shape = RoundedCornerShape(radius)
    return background(fill, shape).drawBehind {
        val w = 1.dp.toPx()
        drawRoundRect(
            color = border,
            topLeft = Offset(w / 2f, w / 2f),
            size = Size(size.width - w, size.height - w),
            cornerRadius = CornerRadius(radius.toPx() - w / 2f),
            style = Stroke(w),
        )
    }
}

// ---- buttons ----

/**
 * The design's green gradient pill button. Pressing scales it to 0.98 (the design's `:active`); [shadow]
 * is the colour of its `0 10px 40px` glow. The caller sets the size through [modifier].
 */
@Composable
fun GradientButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    brush: Brush = GradientPrimary,
    shadow: Color? = null,
    onClickLabel: String? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier
            .graphicsLayer {
                val scale = if (pressed) 0.98f else 1f
                scaleX = scale
                scaleY = scale
            }
            .then(if (shadow != null) Modifier.softShadow(shadow, blur = 40.dp, offsetY = 10.dp) else Modifier)
            .background(brush, CircleShape)
            .clickable(interactionSource = interaction, indication = null, onClickLabel = onClickLabel, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/** The 72 px round header button (`#1b232c`, lighter while pressed as on the design's hover). */
@Composable
fun RoundIconButton(icon: ImageVector, description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier
            .size(72.dp)
            .background(if (pressed) EvColors.RoundButtonPressed else EvColors.RoundButton, CircleShape)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        EvIcon(icon, 32.dp, tint = EvColors.Ink, contentDescription = description)
    }
}

/**
 * A pill button with a plain fill and / or an outline: the design's "위치 전송 동의 철회" (outline, 56 high),
 * the alert's "충전소 찾기" (filled, 54 high) and the app's own small actions in the same shape.
 */
@Composable
fun PillButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 56.dp,
    horizontalPadding: Dp = 30.dp,
    fill: Color = Color.Transparent,
    outline: Color? = EvColors.OutlineBorder,
    outlineWidth: Dp = 1.5.dp,
    textStyle: TextStyle = evText(19, color = EvColors.TextLabel),
    leading: (@Composable () -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        modifier
            .height(height)
            .background(fill, CircleShape)
            .drawBehind {
                // The design's hover: a faint light wash over outlined pills, a brighter fill on filled ones.
                if (pressed) drawRoundRect(Color.White.copy(alpha = if (outline != null) 0.04f else 0.10f), cornerRadius = CornerRadius(size.height / 2f))
                if (outline != null) {
                    val w = outlineWidth.toPx()
                    drawRoundRect(
                        color = outline,
                        topLeft = Offset(w / 2f, w / 2f),
                        size = Size(size.width - w, size.height - w),
                        cornerRadius = CornerRadius((size.height - w) / 2f),
                        style = Stroke(w),
                    )
                }
            }
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            // CSS padding lies inside the border of the border-box.
            .padding(horizontal = horizontalPadding + if (outline != null) outlineWidth else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
    ) {
        leading?.invoke()
        EvText(label, textStyle, singleLine = true)
    }
}

// ---- status pill, chips ----

/** The header's connection pill: 2 px outline, car icon and label in one colour. */
@Composable
fun StatusPill(label: String, color: Color, modifier: Modifier = Modifier, small: Boolean = false) {
    // CSS padding (10 22 / 9 20) plus the 2 px border of the border-box.
    val horizontal = if (small) 22.dp else 24.dp
    val vertical = if (small) 11.dp else 12.dp
    Row(
        modifier
            .drawBehind {
                val w = 2.dp.toPx()
                drawRoundRect(
                    color = color,
                    topLeft = Offset(w / 2f, w / 2f),
                    size = Size(size.width - w, size.height - w),
                    cornerRadius = CornerRadius((size.height - w) / 2f),
                    style = Stroke(w),
                )
            }
            .padding(horizontal = horizontal, vertical = vertical),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (small) 8.dp else 10.dp),
    ) {
        if (small) EvIcon(EvIcons.Car, 24.dp, height = 20.dp, tint = color) else EvIcon(EvIcons.Car, 26.dp, height = 22.dp, tint = color)
        EvText(label, evText(19, EvWeight.SemiBold, color), singleLine = true)
    }
}

/** A station card chip: `padding: 5px 16px`, translucent white pill, 17 px. */
@Composable
fun Chip(text: String, modifier: Modifier = Modifier, color: Color = EvColors.TextLabel, fill: Color = EvColors.Chip) {
    EvText(
        text,
        evText(17, color = color),
        modifier.background(fill, CircleShape).padding(horizontal = 16.dp, vertical = 5.dp),
        singleLine = true,
    )
}

/**
 * Chips in one line, 12 px apart. A chip that no longer fits is cut with an ellipsis, and left out
 * altogether when less than [minChipWidth] remains for it.
 */
@Composable
fun ChipRow(modifier: Modifier = Modifier, gap: Dp = 12.dp, minChipWidth: Dp = 96.dp, content: @Composable () -> Unit) {
    Layout(content, modifier) { measurables, constraints ->
        val gapPx = gap.roundToPx()
        val minPx = minChipWidth.roundToPx()
        var x = 0
        val placed = ArrayList<Pair<androidx.compose.ui.layout.Placeable, Int>>(measurables.size)
        for (measurable in measurables) {
            val remaining = if (constraints.hasBoundedWidth) constraints.maxWidth - x else Constraints.Infinity
            if (remaining < minPx && x > 0) break
            val placeable = measurable.measure(Constraints(maxWidth = remaining.coerceAtLeast(0), maxHeight = constraints.maxHeight))
            placed += placeable to x
            x += placeable.width + gapPx
        }
        val width = (x - gapPx).coerceAtLeast(0).coerceIn(constraints.minWidth, constraints.maxWidth)
        val height = (placed.maxOfOrNull { it.first.height } ?: 0).coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(width, height) { placed.forEach { (placeable, left) -> placeable.place(left, 0) } }
    }
}

// ---- settings controls ----

/**
 * The design's segmented choice: a 54 px pill outline (1.5 px, white 14 %) with equally wide options. Each
 * option reaches 1.5 px over the outline (CSS `margin: -1.5px`), so the chosen one — green gradient, dark
 * 700 label — covers it; a 1 px divider (36 % of the height) stands between two options that are both unchosen.
 */
@Composable
fun <T> Segmented(
    options: List<T>,
    selected: T?,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
) {
    val selectedIndex = options.indexOf(selected)
    Layout(
        content = {
            options.forEachIndexed { index, option ->
                val chosen = index == selectedIndex
                val divider = index > 0 && !chosen && index - 1 != selectedIndex
                Box(
                    Modifier
                        .then(if (chosen) Modifier.background(GradientPrimary, CircleShape) else Modifier)
                        .drawBehind {
                            if (divider) drawRect(EvColors.SegmentDivider, Offset(0f, size.height * 0.32f), Size(1.dp.toPx(), size.height * 0.36f))
                        }
                        .selectable(selected = chosen, interactionSource = null, indication = null, role = Role.RadioButton, onClick = { onSelect(option) }),
                    contentAlignment = Alignment.Center,
                ) {
                    EvText(
                        label(option),
                        evText(21, if (chosen) EvWeight.Bold else EvWeight.Medium, if (chosen) EvColors.OnGreen else EvColors.Ink),
                        singleLine = true,
                    )
                }
            }
        },
        modifier = modifier
            .height(54.dp)
            .drawBehind {
                val w = 1.5.dp.toPx()
                drawRoundRect(
                    color = EvColors.SegmentBorder,
                    topLeft = Offset(w / 2f, w / 2f),
                    size = Size(size.width - w, size.height - w),
                    cornerRadius = CornerRadius((size.height - w) / 2f),
                    style = Stroke(w),
                )
            }
            .selectableGroup(),
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val border = 1.5.dp.toPx()
        val unit = (width - 2 * border) / measurables.size.coerceAtLeast(1)
        val cell = Constraints.fixed((unit + 2 * border).roundToInt().coerceAtLeast(0), height)
        val placeables = measurables.map { it.measure(cell) }
        layout(width, height) {
            placeables.forEachIndexed { index, placeable -> placeable.place((index * unit).roundToInt(), 0) }
        }
    }
}

/** CSS `transition: … .2s` (default timing function `ease`). */
private val CssEase = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)

/** The design's 68×40 switch: `#3ee05f` on / `#3a444f` off, a 32 px white knob 4 px from the edge. */
@Composable
fun ToggleSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val track by animateColorAsState(if (checked) EvColors.ToggleOn else EvColors.ToggleOff, tween(200, easing = CssEase), label = "track")
    val knobLeft by animateDpAsState(if (checked) 32.dp else 4.dp, tween(200, easing = CssEase), label = "knob")
    Box(
        modifier
            .size(68.dp, 40.dp)
            .background(track, CircleShape)
            .toggleable(value = checked, interactionSource = null, indication = null, role = Role.Switch, onValueChange = onCheckedChange),
    ) {
        Box(Modifier.offset(x = knobLeft, y = 4.dp).size(32.dp).background(Color.White, CircleShape))
    }
}

// ---- illustrations ----

/** The 132×96 dark green tile of the design's empty state; [content] is its glyph. */
@Composable
fun IllustrationBox(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier.size(132.dp, 96.dp).background(GradientIllustration, RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/**
 * A station card's 160×144 picture slot. The app has no station photos, so the slot holds a neutral tile
 * in the design's own illustration style (dark green gradient, charger glyph).
 */
@Composable
fun StationThumb(modifier: Modifier = Modifier) {
    Box(
        modifier.size(160.dp, 144.dp).background(GradientIllustration, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        EvIcon(EvIcons.ChargerGlyph, 68.dp)
    }
}
