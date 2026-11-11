package com.example.pleosev.shared.match

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NameMatcherTest {
    private val naviName = "워터 괴산휴게소 창원방향 전기차충전소" // DrivingState.name from the stock navi (spike)

    @Test
    fun normalizeDropsSpacingBracketsAndChargerWords() {
        assertEquals("워터괴산휴게소창원", NameMatcher.normalize(naviName))
        assertEquals("괴산휴게소창원", NameMatcher.normalize("괴산휴게소(창원방향)"))
        assertEquals("문의청남대휴게소영덕", NameMatcher.normalize("문의청남대휴게소 (영덕) 전기차충전소"))
    }

    @Test
    fun sameServiceAreaAndDirectionIsMostSimilar() {
        val sameDirection = NameMatcher.similarity(naviName, "괴산휴게소(창원방향)")
        val otherDirection = NameMatcher.similarity(naviName, "괴산휴게소(서울방향)")
        val otherArea = NameMatcher.similarity(naviName, "충주휴게소(서울방향)")
        assertTrue(sameDirection > 0.8, "same direction $sameDirection")
        assertTrue(otherDirection < sameDirection)
        assertTrue(otherArea < otherDirection)
    }

    @Test
    fun emptyNamesHaveNoSimilarity() {
        assertEquals(0.0, NameMatcher.similarity("", "괴산휴게소"))
        assertEquals(1.0, NameMatcher.similarity("판교역 공영주차장", "판교역공영주차장"), 1e-9)
    }
}
