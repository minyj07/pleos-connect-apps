package com.example.pleosev.advisor.domain

import com.example.pleosev.navi.NaviState
import com.example.pleosev.navi.NaviWaypoint
import com.example.pleosev.navi.WaypointSlot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class WaypointPlannerTest {

    private val goesan = station()
    private fun waypoints(n: Int) = List(n) { NaviWaypoint("경유지 $it", 10_000, null) }

    @Test
    fun withoutGuidanceTheStationBecomesTheDestination() {
        val actions = WaypointPlanner.actions(goesan, NaviState(guiding = false))
        assertIs<NaviAction.RouteTo>(actions[0])
        assertIs<NaviAction.SearchInNavi>(actions[1])
    }

    @Test
    fun withoutTheNavisPoiIdSearchingInTheNaviComesFirst() {
        val actions = WaypointPlanner.actions(goesan, NaviState(guiding = true))
        assertIs<NaviAction.SearchInNavi>(actions[0])
        val add = assertIs<NaviAction.AddWaypoint>(actions[1])
        assertEquals(WaypointSlot.FIRST, add.slot)
        assertEquals(false, add.recognizedAsCharger)
    }

    @Test
    fun theFreeSlotIsUsedAndAFullRouteOnlyOffersSearch() {
        val second = WaypointPlanner.actions(goesan, NaviState(guiding = true, waypoints = waypoints(1)))
        assertEquals(WaypointSlot.SECOND, assertIs<NaviAction.AddWaypoint>(second[1]).slot)
        val full = WaypointPlanner.actions(goesan, NaviState(guiding = true, waypoints = waypoints(2)))
        assertEquals(1, full.size)
        assertIs<NaviAction.SearchInNavi>(full[0])
    }

    @Test
    fun aKnownNaviPoiIdIsAddedDirectly() {
        val actions = WaypointPlanner.actions(goesan, NaviState(guiding = true), naviPoiId = "36mc456q4pqscm5nqhe4")
        val add = assertIs<NaviAction.AddWaypoint>(actions[0])
        assertTrue(add.recognizedAsCharger)
    }

    @Test
    fun anAlternativeToAChargerDestinationBecomesTheNewDestination() {
        val actions = WaypointPlanner.actions(goesan, NaviState(guiding = true, waypoints = waypoints(1)), replacesDestination = true)
        assertIs<NaviAction.RouteTo>(actions[0])
        assertIs<NaviAction.SearchInNavi>(actions[1])
        assertEquals(2, actions.size) // never a stop on the way to the full station
    }

    @Test
    fun searchKeywordsLookLikeNaviPoiNames() {
        assertEquals("괴산휴게소 창원방향 전기차충전소", WaypointPlanner.searchKeyword(goesan))
        assertEquals("E-pit 판교 전기차충전소", WaypointPlanner.searchKeyword(station(name = "E-pit 판교")))
        assertEquals("SW충전소", WaypointPlanner.searchKeyword(station(name = "SW충전소")))
    }
}
