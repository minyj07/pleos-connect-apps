package com.example.pleosev.ui.design

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The icons are built from the design's SVG path data when the screen first uses them, so a slip in a
 * path string would only show (or crash) on the device. These checks read every icon on the JVM.
 */
class EvIconsTest {

    @Test
    fun `every icon is built from its path data`() {
        val icons = EvIcons.all
        assertEquals(29, icons.size)
        icons.forEach { (name, icon) ->
            assertTrue(icon.viewportWidth > 0f && icon.viewportHeight > 0f, "$name has no view box")
            assertTrue(paths(icon.root).isNotEmpty(), "$name has no paths")
        }
    }

    @Test
    fun `every path stays inside its view box`() {
        EvIcons.all.forEach { (name, icon) ->
            paths(icon.root).forEach { path ->
                assertTrue(path.pathData.isNotEmpty(), "$name has an empty path")
                onCurvePoints(path.pathData).forEach { (x, y) ->
                    assertTrue(
                        x in -TOLERANCE..icon.viewportWidth + TOLERANCE && y in -TOLERANCE..icon.viewportHeight + TOLERANCE,
                        "$name leaves its ${icon.viewportWidth}x${icon.viewportHeight} view box at ($x, $y)",
                    )
                }
            }
        }
    }

    @Test
    fun `the icons keep the design's view boxes`() {
        val icons = EvIcons.all
        assertEquals(26f to 22f, icons.getValue("Car").let { it.viewportWidth to it.viewportHeight })
        assertEquals(32f to 32f, icons.getValue("Charger").let { it.viewportWidth to it.viewportHeight })
        assertEquals(24f to 28f, icons.getValue("Pin").let { it.viewportWidth to it.viewportHeight })
        (icons.keys - setOf("Car", "Charger", "ChargerGlyph", "Pin")).forEach { name ->
            assertEquals(24f to 24f, icons.getValue(name).let { it.viewportWidth to it.viewportHeight }, name)
        }
    }

    private fun paths(group: VectorGroup): List<VectorPath> = group.flatMap { node ->
        when (node) {
            is VectorPath -> listOf(node)
            is VectorGroup -> paths(node)
        }
    }

    /** The points the pen stops at (control points may lie outside the box, these may not). */
    private fun onCurvePoints(nodes: List<PathNode>): List<Pair<Float, Float>> {
        val points = ArrayList<Pair<Float, Float>>()
        var x = 0f
        var y = 0f
        var startX = 0f
        var startY = 0f
        fun moveTo(nx: Float, ny: Float, start: Boolean = false) {
            x = nx
            y = ny
            if (start) {
                startX = nx
                startY = ny
            }
            points += x to y
        }
        nodes.forEach { node ->
            when (node) {
                is PathNode.Close -> moveTo(startX, startY)
                is PathNode.MoveTo -> moveTo(node.x, node.y, start = true)
                is PathNode.RelativeMoveTo -> moveTo(x + node.dx, y + node.dy, start = true)
                is PathNode.LineTo -> moveTo(node.x, node.y)
                is PathNode.RelativeLineTo -> moveTo(x + node.dx, y + node.dy)
                is PathNode.HorizontalTo -> moveTo(node.x, y)
                is PathNode.RelativeHorizontalTo -> moveTo(x + node.dx, y)
                is PathNode.VerticalTo -> moveTo(x, node.y)
                is PathNode.RelativeVerticalTo -> moveTo(x, y + node.dy)
                is PathNode.CurveTo -> moveTo(node.x3, node.y3)
                is PathNode.RelativeCurveTo -> moveTo(x + node.dx3, y + node.dy3)
                is PathNode.ReflectiveCurveTo -> moveTo(node.x2, node.y2)
                is PathNode.RelativeReflectiveCurveTo -> moveTo(x + node.dx2, y + node.dy2)
                is PathNode.QuadTo -> moveTo(node.x2, node.y2)
                is PathNode.RelativeQuadTo -> moveTo(x + node.dx2, y + node.dy2)
                is PathNode.ReflectiveQuadTo -> moveTo(node.x, node.y)
                is PathNode.RelativeReflectiveQuadTo -> moveTo(x + node.dx, y + node.dy)
                is PathNode.ArcTo -> moveTo(node.arcStartX, node.arcStartY)
                is PathNode.RelativeArcTo -> moveTo(x + node.arcStartDx, y + node.arcStartDy)
            }
        }
        return points
    }

    private companion object {
        const val TOLERANCE = 0.01f
    }
}
