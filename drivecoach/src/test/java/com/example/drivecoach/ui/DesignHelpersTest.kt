package com.example.drivecoach.ui

import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.PathParser
import com.example.drivecoach.ui.components.CoachIcon
import com.example.drivecoach.ui.components.CoachIcons
import com.example.drivecoach.ui.components.durationParts
import com.example.drivecoach.ui.components.keepAll
import com.example.drivecoach.ui.components.scoreColor
import com.example.drivecoach.ui.components.steerTurns
import com.example.drivecoach.ui.theme.CoachColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure helpers of the design layer: icon path data, keep-all line breaking, small formatters. */
class DesignHelpersTest {

    private val joiner = 0xFEFF.toChar()

    private fun icons(): Map<String, CoachIcon> = CoachIcons::class.java.declaredFields
        .filter { it.type == CoachIcon::class.java }
        .associate { field ->
            field.isAccessible = true
            field.name to field.get(CoachIcons) as CoachIcon
        }

    @Test
    fun `every icon path of the design parses and starts with a move`() {
        val icons = icons()
        assertEquals(22, icons.size)
        icons.forEach { (name, icon) ->
            assertTrue("$name has no paths", icon.parts.isNotEmpty())
            icon.parts.forEach { part ->
                val nodes = PathParser().parsePathString(part.d).toNodes()
                assertTrue("$name: empty path", nodes.size >= 2)
                assertTrue("$name: path must start with M", nodes.first() is PathNode.MoveTo)
                assertTrue("$name: stroke width", part.stroke == null || part.stroke!! > 0f)
            }
        }
    }

    @Test
    fun `icon paths stay inside the 24 unit view box`() {
        // Absolute coordinates only; relative segments are covered by the parse test above.
        icons().forEach { (name, icon) ->
            icon.parts.forEach { part ->
                PathParser().parsePathString(part.d).toNodes().forEach { node ->
                    val points = when (node) {
                        is PathNode.MoveTo -> listOf(node.x to node.y)
                        is PathNode.LineTo -> listOf(node.x to node.y)
                        else -> emptyList()
                    }
                    points.forEach { (x, y) -> assertTrue("$name: $x,$y", x in 0f..icon.viewBox && y in 0f..icon.viewBox) }
                }
            }
        }
    }

    @Test
    fun `keep-all joins the syllables of a Korean word but not across spaces`() {
        assertEquals("주${joiner}행 기${joiner}록", keepAll("주행 기록"))
        // Blink's rule: letters and digits stick together (7일, PEDAL이나), punctuation does not (·, 괄호).
        assertEquals("7${joiner}일", keepAll("7일"))
        assertEquals("PEDAL${joiner}이${joiner}나", keepAll("PEDAL이나"))
        assertEquals("급${joiner}가${joiner}속·급${joiner}출${joiner}발", keepAll("급가속·급출발"))
        assertEquals("떼${joiner}서(회${joiner}생${joiner}제${joiner}동)", keepAll("떼서(회생제동)"))
    }

    @Test
    fun `keep-all leaves text without Korean untouched`() {
        listOf("", "A", "0.6 km/h", "https://example.com/v/abc#key", "12:34", "→").forEach { assertEquals(it, keepAll(it)) }
    }

    @Test
    fun `duration is split into number and unit pairs`() {
        assertEquals(listOf("6" to "분"), durationParts(6 * 60f))
        assertEquals(listOf("0" to "분"), durationParts(20f))
        assertEquals(listOf("1" to "시간", "5" to "분"), durationParts(65 * 60f))
    }

    @Test
    fun `score colours follow the design thresholds`() {
        assertEquals(CoachColors.ScoreGood, scoreColor(80))
        assertEquals(CoachColors.ScoreMid, scoreColor(79))
        assertEquals(CoachColors.ScoreMid, scoreColor(50))
        assertEquals(CoachColors.ScoreBad, scoreColor(49))
    }

    @Test
    fun `wheel turns are only named once the wheel is clearly turned`() {
        assertNull(steerTurns(0.04f, 1.3))
        assertEquals("약 1.3바퀴", steerTurns(-1f, 1.3))
        assertEquals("약 0.7바퀴", steerTurns(0.5f, 1.3))
    }
}
