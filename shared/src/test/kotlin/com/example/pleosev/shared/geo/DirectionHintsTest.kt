package com.example.pleosev.shared.geo

import com.example.pleosev.shared.api.GeoPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DirectionHintsTest {
    private val goesan = GeoPoint(36.8327504, 127.9583201)
    private val towardsBusan = Geo.bearingDeg(goesan, GeoPoint(35.1151, 129.0422))

    @Test
    fun directionNamesResolveToCities() {
        assertEquals(35.2279, assertNotNull(DirectionHints.target("괴산휴게소(창원방향)")).lat, 1e-9)
        assertEquals(35.2279, assertNotNull(DirectionHints.target("문경휴게소 (창원) 전기차충전소")).lat, 1e-9)
        assertEquals(35.2279, assertNotNull(DirectionHints.target("워터 괴산휴게소 창원방향 전기차충전소")).lat, 1e-9)
        assertEquals(37.4917, assertNotNull(DirectionHints.target("괴산휴게소(양평방향)")).lat, 1e-9)
    }

    @Test
    fun namesWithoutADirectionGiveNothing() {
        assertNull(DirectionHints.target("부산역 공영주차장"))
        assertNull(DirectionHints.target("판교역 공영주차장"))
        assertNull(DirectionHints.target("E-pit 판교"))
        assertNull(DirectionHints.target("행담도휴게소(하행)"))
    }

    @Test
    fun directionsFromTheRealStationNames() {
        // Both sides of 용인처인휴게소 came back on a Seoul -> Busan route before 포천 was in the table.
        val cheoin = GeoPoint(37.340518, 127.221019)
        val south = Geo.bearingDeg(cheoin, GeoPoint(35.1151, 129.0422))
        assertTrue(DirectionHints.isOppositeCarriageway("E1 용인처인휴게소(포천방향) 전기차충전소", cheoin, south))
        assertFalse(DirectionHints.isOppositeCarriageway("E1 용인처인휴게소(세종방향) 전기차충전소", cheoin, south))
        // 음성 is not in the table; 제천 gives the same eastward direction.
        assertEquals(37.1326, assertNotNull(DirectionHints.target("HK하이웨이 평택휴게소(하행_음성제천방향)")).lat, 1e-9)
        assertEquals(37.3947, assertNotNull(DirectionHints.target("시흥하늘휴게소(판교방향 2층)")).lat, 1e-9)
        assertNull(DirectionHints.target("걸포중앙공원 청소년수련관방향"))
    }

    @Test
    fun oppositeCarriagewayIsDetected() {
        assertTrue(DirectionHints.isOppositeCarriageway("괴산휴게소(양평방향)", goesan, towardsBusan))
        assertFalse(DirectionHints.isOppositeCarriageway("괴산휴게소(창원방향)", goesan, towardsBusan))
        assertFalse(DirectionHints.isOppositeCarriageway("부산역 공영주차장", goesan, towardsBusan))
    }
}
