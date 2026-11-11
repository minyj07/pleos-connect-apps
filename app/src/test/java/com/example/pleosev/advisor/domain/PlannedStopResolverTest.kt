package com.example.pleosev.advisor.domain

import com.example.pleosev.navi.NaviDestination
import com.example.pleosev.navi.NaviPlace
import com.example.pleosev.navi.NaviState
import com.example.pleosev.navi.NaviStop
import com.example.pleosev.navi.NaviWaypoint
import com.example.pleosev.navi.RoutePoint
import com.example.pleosev.shared.api.GeoPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlannedStopResolverTest {

    // Names and positions as the Pleos emulator's navi reports them.
    private val goesan = "워터 괴산휴게소 창원방향 전기차충전소"
    private val mungyeong = "워터 문경휴게소 창원방향 전기차충전소"
    private val mungyeongAt = GeoPoint(36.6215556, 128.1495167)
    private val probe = listOf(RoutePoint(goesan, GeoPoint(36.8327504, 127.9583201), "36mc456q4pqscm5nqhe4", 32.5, true, true, false))

    private fun stop(name: String, charging: Boolean = true) = NaviStop(name, 150_000, 9_000, charging, 350, 8)
    private fun waypoint(name: String, poiId: String? = null) = NaviWaypoint(name, 155_000, poiId)

    @Test
    fun drivingInfoDecidesWhenItIsKnown() {
        val navi = NaviState(guiding = true, stopsKnown = true, stops = listOf(stop("천안 맛집", charging = false), stop(mungyeong)))
        assertEquals(mungyeong, PlannedStopResolver.stopName(navi, emptyMap(), probe))
        val noCharger = NaviState(guiding = true, stopsKnown = true, stops = listOf(stop("천안 맛집", charging = false)))
        assertNull(PlannedStopResolver.stopName(noCharger, emptyMap(), probe))
        assertFalse(PlannedStopResolver.needsProbe(noCharger, emptyMap()))
    }

    @Test
    fun aChargerAddedHereByItsCoordinatesStaysTheStopOnceDrivingInfoComes() {
        // Emulator 2026-11-05: "좌표로 경유지 추가" for 괴산 -> DrivingInfo flags it as no charger and the navi adds 문경 after it.
        val ours = "괴산휴게소(창원방향)"
        val navi = NaviState(guiding = true, stopsKnown = true, stops = listOf(stop(ours, charging = false), stop(mungyeong)), waypoints = listOf(waypoint(ours)))
        val added = mapOf(ours to AddedStation(GeoPoint(36.8327504, 127.9583201), "WTFK0201"))
        assertEquals(ours, PlannedStopResolver.stopName(navi, added, null))
        assertEquals("WTFK0201", PlannedStopResolver.locate(ours, navi, added, null)!!.statId)
        // Not added here: the navi's own stop, as before.
        assertEquals(mungyeong, PlannedStopResolver.stopName(navi, emptyMap(), null))
    }

    @Test
    fun withoutDrivingInfoAChargerWaypointBeatsTheProbe() {
        // The probe plans 괴산 by itself, but the driver picked 문경 in the navi (it ignores manual waypoints).
        val navi = NaviState(guiding = true, waypoints = listOf(waypoint(mungyeong, "36m5hj956p68cm5nqhe9")))
        assertEquals(mungyeong, PlannedStopResolver.stopName(navi, emptyMap(), probe))
        assertFalse(PlannedStopResolver.needsProbe(navi, emptyMap()))
        // Stations added through the advisor carry the server's name, without "충전".
        val ours = NaviState(guiding = true, waypoints = listOf(waypoint("문경휴게소(창원방향)")))
        val added = mapOf("문경휴게소(창원방향)" to AddedStation(mungyeongAt, "HDFK0204"))
        assertEquals("문경휴게소(창원방향)", PlannedStopResolver.stopName(ours, added, probe))
    }

    @Test
    fun withoutDrivingInfoOrChargerWaypointsTheProbeStandsIn() {
        val navi = NaviState(guiding = true, waypoints = listOf(waypoint("천안 맛집")))
        assertTrue(PlannedStopResolver.needsProbe(navi, emptyMap()))
        assertNull(PlannedStopResolver.stopName(navi, emptyMap(), null))
        assertEquals(goesan, PlannedStopResolver.stopName(navi, emptyMap(), probe))
    }

    @Test
    fun aWaypointPickedInTheNaviIsFoundAmongItsRecentDestinations() {
        val navi = NaviState(
            guiding = true,
            stopsKnown = true,
            stops = listOf(stop(mungyeong)),
            waypoints = listOf(waypoint(mungyeong, "36m5hj956p68cm5nqhe9")),
            recentPlaces = listOf(NaviPlace("부산역", GeoPoint(35.1151, 129.0422), null), NaviPlace("문경 충전소", mungyeongAt, "36m5hj956p68cm5nqhe9")),
        )
        val planned = PlannedStopResolver.locate(mungyeong, navi, emptyMap(), probe)!!
        assertEquals(mungyeongAt, planned.location)
        assertEquals(350, planned.maxKw)
        // Not in the recent list: unknown, never the probe's (different) station.
        assertNull(PlannedStopResolver.locate(mungyeong, navi.copy(recentPlaces = emptyList()), emptyMap(), probe))
    }

    @Test
    fun theNavisOwnStopIsLocatedByItsExactNameOnly() {
        val navi = NaviState(guiding = true, stopsKnown = true, stops = listOf(stop(goesan)))
        assertEquals(probe[0].location, PlannedStopResolver.locate(goesan, navi, emptyMap(), probe)!!.location)
        val other = NaviState(guiding = true, stopsKnown = true, stops = listOf(stop(mungyeong)))
        assertNull(PlannedStopResolver.locate(mungyeong, other, emptyMap(), probe))
        assertEquals(StopRole.NAVI, PlannedStopResolver.role(mungyeong, other))
    }

    @Test
    fun stationsAddedHereKeepTheirStatId() {
        val navi = NaviState(guiding = true, stopsKnown = true, stops = listOf(stop("문경휴게소(창원방향)")), waypoints = listOf(waypoint("문경휴게소(창원방향)")))
        val planned = PlannedStopResolver.locate("문경휴게소(창원방향)", navi, mapOf("문경휴게소(창원방향)" to AddedStation(mungyeongAt, "HDFK0204")), null)!!
        assertEquals("HDFK0204", planned.statId)
        assertEquals(StopRole.WAYPOINT, PlannedStopResolver.role("문경휴게소(창원방향)", navi))
    }

    @Test
    fun aChargingStationAsTheDestinationIsTheStopToCheck() {
        // Sent by the advisor ("이 충전소로 안내"): the server's name, its statId and position are known.
        val epit = NaviDestination("E-pit 판교", GeoPoint(37.401, 127.108), 2_400, 600, null)
        val navi = NaviState(guiding = true, stopsKnown = true, destination = epit)
        val added = mapOf("E-pit 판교" to AddedStation(epit.location!!, "HDFK0003"))
        assertEquals("E-pit 판교", PlannedStopResolver.stopName(navi, added, null))
        assertEquals(StopRole.DESTINATION, PlannedStopResolver.role("E-pit 판교", navi))
        assertEquals("HDFK0003", PlannedStopResolver.locate("E-pit 판교", navi, added, null)!!.statId)
        // Picked in the navi itself: its charger flag (DrivingInfo) or the POI name, and the destination's own position.
        val picked = NaviState(guiding = true, stopsKnown = true, destination = epit.copy(name = mungyeong, location = mungyeongAt))
        assertEquals(mungyeong, PlannedStopResolver.stopName(picked, emptyMap(), null))
        assertEquals(mungyeongAt, PlannedStopResolver.locate(mungyeong, picked, emptyMap(), null)!!.location)
        val flagged = NaviState(guiding = true, stopsKnown = true, destination = epit.copy(name = "판교 OO아파트"), destinationStop = stop("판교 OO아파트"))
        assertEquals("판교 OO아파트", PlannedStopResolver.stopName(flagged, emptyMap(), null))
        // A plain destination is not a charging stop.
        assertNull(PlannedStopResolver.stopName(navi.copy(destination = epit.copy(name = "부산역")), emptyMap(), null))
    }

    @Test
    fun aChargingStopOnTheWayComesBeforeAChargerDestination() {
        val navi = NaviState(
            guiding = true,
            stopsKnown = true,
            stops = listOf(stop(goesan)),
            destination = NaviDestination("E-pit 부산", GeoPoint(35.1151, 129.0422), 378_000, 16_800, null),
        )
        assertEquals(goesan, PlannedStopResolver.stopName(navi, mapOf("E-pit 부산" to AddedStation(GeoPoint(35.1151, 129.0422), "HDFK9999")), probe))
    }
}
