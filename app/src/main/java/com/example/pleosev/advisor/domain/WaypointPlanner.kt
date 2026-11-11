package com.example.pleosev.advisor.domain

import com.example.pleosev.navi.NaviState
import com.example.pleosev.navi.NaviTarget
import com.example.pleosev.navi.WaypointSlot
import com.example.pleosev.shared.api.StationSummary

enum class NaviActionKind { ROUTE_TO, ADD_WAYPOINT, SEARCH_IN_NAVI }

/** What the driver can do with a recommended station. Every route change is an explicit tap. */
sealed interface NaviAction {
    val kind: NaviActionKind

    /** No guidance: navigate to the station. */
    data class RouteTo(val target: NaviTarget) : NaviAction {
        override val kind get() = NaviActionKind.ROUTE_TO
    }

    /**
     * Add the station as a waypoint. Without the navi's POI id ([recognizedAsCharger] false) the navi
     * treats it as a plain waypoint and may still plan a charging stop of its own (Phase 0 spike).
     */
    data class AddWaypoint(val target: NaviTarget, val slot: WaypointSlot) : NaviAction {
        override val kind get() = NaviActionKind.ADD_WAYPOINT
        val recognizedAsCharger: Boolean get() = target.poiId.isNotBlank()
    }

    /** Open the navi's place search for the station; adding it from there gives the navi its POI id. */
    data class SearchInNavi(val keyword: String) : NaviAction {
        override val kind get() = NaviActionKind.SEARCH_IN_NAVI
    }
}

object WaypointPlanner {

    fun target(station: StationSummary, naviPoiId: String? = null) = NaviTarget(
        name = station.name,
        location = station.location,
        poiId = naviPoiId.orEmpty(),
        address = station.address,
    )

    fun searchKeyword(station: StationSummary) = searchKeyword(station.name)

    /** "괴산휴게소(창원방향)" → "괴산휴게소 창원방향 전기차충전소": the navi's POI names follow this shape. */
    fun searchKeyword(name: String): String {
        val base = name.replace('(', ' ').replace(')', ' ').replace(Regex("""\s+"""), " ").trim()
        return if (base.contains("충전")) base else "$base 전기차충전소"
    }

    /** Actions for [station], primary first. [naviPoiId] is the navi's POI id when known. */
    fun actions(station: StationSummary, navi: NaviState, naviPoiId: String? = null, replacesDestination: Boolean = false): List<NaviAction> =
        actions(target(station, naviPoiId), navi, replacesDestination)

    /**
     * Actions for [target] on the current route, primary first; a target with the navi's POI id is added directly.
     * [replacesDestination]: the route ends at the charging station this one stands in for, so it becomes the
     * new destination instead of a stop on the way there.
     */
    fun actions(target: NaviTarget, navi: NaviState, replacesDestination: Boolean = false): List<NaviAction> {
        val search = NaviAction.SearchInNavi(searchKeyword(target.name))
        if (!navi.guiding || replacesDestination) return listOf(NaviAction.RouteTo(target), search)
        val slot = when (navi.waypoints.size) {
            0 -> WaypointSlot.FIRST
            1 -> WaypointSlot.SECOND
            else -> null // the navi supports two waypoints
        }
        val known = target.poiId.isNotBlank()
        return buildList {
            if (slot != null && known) add(NaviAction.AddWaypoint(target, slot))
            add(search)
            if (slot != null && !known) add(NaviAction.AddWaypoint(target, slot))
        }
    }
}
