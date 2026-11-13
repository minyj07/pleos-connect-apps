package com.example.pleosev.ui.design

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.RadialGradient
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.PathParser
import com.example.pleosev.ui.theme.EvColors

/** Which of the design's two decorative backgrounds to draw. */
enum class EvBackdrop { HOME, ASSIST }

/** The decorative SVG's viewBox; `preserveAspectRatio="none"` stretches it over the whole frame. */
private const val VIEW_W = 1672f
private const val VIEW_H = 941f

/** One `<path>` of the decorative SVG: either a wide, blurred, translucent glow or a 1.5 px gradient line. */
private class Curve(
    val d: String,
    val glow: Color? = null,
    val glowAlpha: Float = 0f,
    val glowWidth: Float = 0f,
    val line: Gradient? = null,
)

/** A `<linearGradient>` in objectBoundingBox units: from (x1, y1) to (x2, y2) of the path's bounds. */
private class Gradient(val x1: Float, val y1: Float, val x2: Float, val y2: Float, vararg val stops: Pair<Float, Color>)

private val GTop = Gradient(0f, 0f, 1f, 0f, 0f to EvColors.GlowGreen.copy(alpha = 0f), 1f to EvColors.Green.copy(alpha = 0.7f))
private val GBot = Gradient(
    0f, 0f, 1f, 0f,
    0f to EvColors.GlowGreen.copy(alpha = 0.55f), 0.5f to EvColors.GlowGreen.copy(alpha = 0f), 1f to EvColors.Green.copy(alpha = 0.5f),
)
private val ATop = Gradient(0f, 1f, 1f, 0f, 0f to EvColors.GlowGreen.copy(alpha = 0f), 1f to EvColors.Green.copy(alpha = 0.75f))
private val ABot = Gradient(0f, 0f, 1f, 0f, 0f to EvColors.GlowGreen.copy(alpha = 0f), 1f to EvColors.Green.copy(alpha = 0.85f))

private val HomeCurves = listOf(
    Curve("M980 110 C1060 40 1130 10 1160 0 L1672 0 L1672 0", glow = EvColors.GlowGreen, glowAlpha = 0.12f, glowWidth = 18f),
    Curve("M985 110 C1050 50 1110 15 1155 0", line = GTop),
    Curve("M0 760 C200 860 380 941 700 941", glow = EvColors.GlowGreenDeep, glowAlpha = 0.10f, glowWidth = 22f),
    Curve("M0 765 C180 850 340 920 520 941", line = GBot),
    Curve("M1000 941 C1250 860 1480 815 1672 808", glow = EvColors.GlowGreenDeep, glowAlpha = 0.08f, glowWidth = 20f),
    Curve("M1000 941 C1250 860 1480 815 1672 808", line = GBot),
)

private val AssistCurves = listOf(
    Curve("M940 140 C1020 70 1090 25 1160 0", glow = EvColors.GlowGreen, glowAlpha = 0.12f, glowWidth = 18f),
    Curve("M940 140 C1020 70 1090 25 1160 0", line = ATop),
    Curve("M1040 941 C1250 880 1470 862 1672 865", glow = EvColors.GlowGreenDeep, glowAlpha = 0.12f, glowWidth = 20f),
    Curve("M1040 941 C1250 880 1470 862 1672 865", line = ABot),
)

/** `feGaussianBlur stdDeviation="12"` in viewBox units. */
private const val GLOW_SIGMA = 12f
private const val LINE_WIDTH = 1.5f

/**
 * The design's page background over the whole window: `radial-gradient(120% 90% at 50% 40%, #111a23 0%,
 * #0b1118 70%)` and the green curves (a blurred glow under a hairline with a gradient stroke).
 */
fun Modifier.evBackdrop(kind: EvBackdrop): Modifier = drawWithCache {
    val sx = size.width / VIEW_W
    val sy = size.height / VIEW_H
    val unit = (sx + sy) / 2f
    val background = PageGradient()
    val stretch = Matrix().apply { scale(sx, sy) }
    val curves = (if (kind == EvBackdrop.HOME) HomeCurves else AssistCurves).map { curve ->
        val path = PathParser().parsePathString(curve.d).toPath().apply { transform(stretch) }
        PreparedCurve(curve, path, curve.line?.let { lineBrush(it, path.getBounds()) })
    }
    // BlurMaskFilter radius for a Gaussian sigma (Skia: sigma = 0.57735 * radius + 0.5).
    val blurRadius = ((GLOW_SIGMA * unit - 0.5f) / 0.57735f).coerceAtLeast(1f)
    val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        maskFilter = BlurMaskFilter(blurRadius, BlurMaskFilter.Blur.NORMAL)
    }
    onDrawBehind {
        drawRect(background)
        curves.forEach { prepared ->
            val glow = prepared.curve.glow
            if (glow != null) {
                glowPaint.color = glow.copy(alpha = prepared.curve.glowAlpha).toArgb()
                glowPaint.strokeWidth = prepared.curve.glowWidth * unit
                drawIntoCanvas { it.nativeCanvas.drawPath(prepared.path.asAndroidPath(), glowPaint) }
            }
            prepared.brush?.let { drawPath(prepared.path, it, style = Stroke(width = LINE_WIDTH * unit)) }
        }
    }
}

private class PreparedCurve(val curve: Curve, val path: Path, val brush: Brush?)

private fun lineBrush(gradient: Gradient, bounds: Rect): Brush = BoundingBoxGradient(gradient, bounds)

/**
 * `gradientUnits="objectBoundingBox"`: the gradient is defined in the unit square and that square is
 * stretched over the path's bounds, so on a box that is not square the colour bands are skewed as in SVG.
 */
private class BoundingBoxGradient(private val gradient: Gradient, private val bounds: Rect) : ShaderBrush() {
    override fun createShader(size: Size): Shader =
        android.graphics.LinearGradient(
            gradient.x1, gradient.y1, gradient.x2, gradient.y2,
            IntArray(gradient.stops.size) { gradient.stops[it].second.toArgb() },
            FloatArray(gradient.stops.size) { gradient.stops[it].first },
            android.graphics.Shader.TileMode.CLAMP,
        ).apply {
            setLocalMatrix(
                android.graphics.Matrix().apply {
                    setScale(bounds.width.coerceAtLeast(1f), bounds.height.coerceAtLeast(1f))
                    postTranslate(bounds.left, bounds.top)
                },
            )
        }
}

/** An elliptical radial gradient: a circular one of the vertical radius, stretched horizontally about its centre. */
private class PageGradient : ShaderBrush() {
    override fun createShader(size: Size): Shader {
        val cx = size.width * 0.5f
        val cy = size.height * 0.4f
        val rx = size.width * 1.2f
        val ry = (size.height * 0.9f).coerceAtLeast(1f)
        return RadialGradient(
            cx, cy, ry,
            intArrayOf(EvColors.BackgroundInner.toArgb(), EvColors.BackgroundOuter.toArgb(), EvColors.BackgroundOuter.toArgb()),
            floatArrayOf(0f, 0.7f, 1f),
            android.graphics.Shader.TileMode.CLAMP,
        ).apply { setLocalMatrix(android.graphics.Matrix().apply { setScale(rx / ry, 1f, cx, cy) }) }
    }
}
