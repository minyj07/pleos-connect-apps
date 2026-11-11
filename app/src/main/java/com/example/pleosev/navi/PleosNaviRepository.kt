package com.example.pleosev.navi

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.NaviMode
import ai.pleos.playground.navi.constants.NavigationStatus
import ai.pleos.playground.navi.constants.RoutePointPoiType
import ai.pleos.playground.navi.constants.RoutePointSettingType
import ai.pleos.playground.navi.constants.RoutePointType
import ai.pleos.playground.navi.constants.WaypointIndex
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.DrivingInfo
import ai.pleos.playground.navi.data.DrivingState
import ai.pleos.playground.navi.data.NavigationScreenRequest
import ai.pleos.playground.navi.data.RecentDestinationInfo
import ai.pleos.playground.navi.data.RequestWaypointInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.data.RouteStateInfo
import ai.pleos.playground.navi.data.TripInfo
import ai.pleos.playground.navi.data.TripRequest
import ai.pleos.playground.navi.data.WaypointArrivedInfo
import ai.pleos.playground.navi.data.WaypointChangedInfo
import ai.pleos.playground.navi.data.WaypointInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.example.pleosev.BuildConfig
import com.example.pleosev.shared.api.GeoPoint
import com.example.pleosev.shared.geo.Geo
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Stock navigation through the Pleos NaviHelper SDK. All `ai.pleos.playground.navi` types stay in
 * this file. Behaviour measured in the Phase 0 spike (docs/SPIKE_NAVI_EV.md):
 *  - results arrive only through listener callbacks, on binder threads: state changes go through
 *    [MutableStateFlow.update]
 *  - remain*Charging values are battery energy in Wh; WaypointInfo coordinates are always 0.0, but a
 *    waypoint picked in the navi's own search is among the recent destinations, with coordinates
 *  - DrivingInfo (stops, including the navi's own charging stops) is pushed on route changes only
 *  - onCurrentRoadSpeedLimit arrives every second and doubles as the connection heartbeat
 *
 * [bringNaviToFront] shows the navi after screen requests that do not raise it themselves (SearchPlace).
 */
class PleosNaviRepository(context: Context, private val bringNaviToFront: () -> Unit) : NaviRepository {

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _state = MutableStateFlow(NaviState())
    override val state: StateFlow<NaviState> = _state.asStateFlow()

    private var navi: NaviHelper? = null
    @Volatile private var connected = false
    @Volatile private var stopped = false
    @Volatile private var lastHeartbeatMs = 0L
    private var lastWatchdogTickMs = 0L
    private var reconnectDelayMs = RECONNECT_MIN_MS

    private val tripMutex = Mutex()
    private val tripResults = MutableSharedFlow<TripInfo>(extraBufferCapacity = 4)

    // ---- NaviRepository ----

    override fun start() {
        stopped = false
        connect()
    }

    override fun stop() {
        stopped = true
        mainHandler.removeCallbacksAndMessages(null)
        disconnect()
    }

    override fun refresh() {
        command("refresh") {
            getRouteStateInfo()
            getDestinationInfo()
            getWaypointInfo()
            getCurrentLocationInfo()
            getRecentDestinationInfo()
        }
    }

    override suspend fun estimateTrip(target: NaviTarget): TripEstimate? = tripMutex.withLock {
        val helper = navi?.takeIf { connected } ?: return null
        val info = withTimeoutOrNull(TRIP_TIMEOUT_MS) {
            coroutineScope {
                // Subscribe before sending: TripInfo carries no request id, so it is paired by its destination.
                val answer = async(start = CoroutineStart.UNDISPATCHED) { tripResults.first { it.isAnswerTo(target) } }
                try {
                    helper.requestTrip(
                        TripRequest(latitude = target.location.lat, longitude = target.location.lon, poiId = target.poiId, poiName = target.name, poiSubId = null),
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "requestTrip failed: ${e.message}")
                    answer.cancel()
                    return@coroutineScope null
                }
                answer.await()
            }
        }
        info?.toEstimate()
    }

    override fun routeTo(target: NaviTarget) = command("requestRoute") {
        requestRoute(
            RouteInfo(
                latitude = target.location.lat,
                longitude = target.location.lon,
                displayLatitude = null,
                displayLongitude = null,
                poiName = target.name,
                poiId = target.poiId,
                address = target.address,
                poiSubId = "",
            ),
        )
    }

    override fun addWaypoint(target: NaviTarget, slot: WaypointSlot) = command("addWaypoint") {
        addWaypoint(
            RequestWaypointInfo(
                latitude = target.location.lat,
                longitude = target.location.lon,
                poiName = target.name,
                waypointIndex = slot.toIndex(),
                poiId = target.poiId,
                address = target.address,
                poiSubId = "",
            ),
        )
    }

    override fun removeWaypoint(slot: WaypointSlot) = command("removeWaypoint") { removeWaypoint(slot.toIndex()) }

    override fun searchInNavi(keyword: String): Boolean {
        // SearchPlace runs the search but leaves the navi in the background (spike), so raise it explicitly.
        val sent = command("searchPlace") { requestNavigationScreen(NavigationScreenRequest.SearchPlace(keyword)) }
        if (sent) bringNaviToFront()
        return sent
    }

    override fun showNearbyChargers() = command("nearbyChargingStations") {
        requestNavigationScreen(NavigationScreenRequest.NearbyChargingStations)
    }

    // ---- Connection ----

    private fun connect() {
        if (stopped) return
        val helper = NaviHelper(appContext)
        navi = helper
        _state.update { it.copy(connection = NaviConnection.CONNECTING) }
        try {
            helper.initialize(
                onConnected = { mainHandler.post { onConnected(helper) } },
                onFailed = { t -> mainHandler.post { onConnectFailed(helper, t) } },
            )
        } catch (e: Exception) {
            onConnectFailed(helper, e)
        }
    }

    private fun onConnected(helper: NaviHelper) {
        if (stopped || navi !== helper) return
        connected = true
        reconnectDelayMs = RECONNECT_MIN_MS
        lastHeartbeatMs = SystemClock.elapsedRealtime()
        helper.addListener(listener)
        update { copy(connection = NaviConnection.CONNECTED, lastError = null) }
        Log.i(TAG, "NaviHelper connected")
        refresh()
        lastWatchdogTickMs = SystemClock.elapsedRealtime()
        mainHandler.postDelayed(watchdog, WATCHDOG_INTERVAL_MS)
        mainHandler.postDelayed(periodicRefresh, REFRESH_INTERVAL_MS)
    }

    private fun onConnectFailed(helper: NaviHelper, t: Throwable) {
        if (stopped || navi !== helper) return
        Log.w(TAG, "NaviHelper unavailable: ${t.message}")
        disconnect()
        update { copy(connection = NaviConnection.UNAVAILABLE, lastError = t.message ?: t.javaClass.simpleName) }
        scheduleReconnect()
    }

    private fun disconnect() {
        mainHandler.removeCallbacks(watchdog)
        mainHandler.removeCallbacks(periodicRefresh)
        val helper = navi ?: return
        navi = null
        if (connected) runCatching { helper.removeListener(listener) }
        connected = false
        runCatching { helper.release() }
    }

    private fun scheduleReconnect() {
        if (stopped) return
        mainHandler.postDelayed({ connect() }, reconnectDelayMs)
        reconnectDelayMs = (reconnectDelayMs * 2).coerceAtMost(RECONNECT_MAX_MS)
    }

    /**
     * No heartbeat for a while means the Navi Service went away (e.g. restarted): reconnect. The speed limit,
     * the only callback that comes by itself, was measured parked on the emulator; another navi build may send it
     * less often, so a quiet navi is asked once (its answer counts as a heartbeat) before the connection is dropped.
     */
    private val watchdog = object : Runnable {
        private var pinged = false

        override fun run() {
            if (stopped || !connected) return
            val now = SystemClock.elapsedRealtime()
            // A late tick means this process was frozen: buffered callbacks are on their way, not lost.
            if (now - lastWatchdogTickMs > 3 * WATCHDOG_INTERVAL_MS) lastHeartbeatMs = now
            lastWatchdogTickMs = now
            if (now - lastHeartbeatMs <= HEARTBEAT_TIMEOUT_MS) {
                pinged = false
            } else if (!pinged) {
                pinged = true
                command("ping") { getRouteStateInfo() }
            } else {
                Log.w(TAG, "no NaviHelper callback for ${now - lastHeartbeatMs} ms; reconnecting")
                pinged = false
                disconnect()
                update { copy(connection = NaviConnection.CONNECTING) }
                scheduleReconnect()
                return
            }
            mainHandler.postDelayed(this, WATCHDOG_INTERVAL_MS)
        }
    }

    /**
     * Destination info is not pushed while the car is parked: poll it while guiding. Without a route only the
     * position is asked for (the start of a nearby search and the 10 km re-fetch rule depend on it).
     */
    private val periodicRefresh = object : Runnable {
        override fun run() {
            if (stopped || !connected) return
            if (_state.value.guiding) refresh() else command("location") { getCurrentLocationInfo() }
            mainHandler.postDelayed(this, REFRESH_INTERVAL_MS)
        }
    }

    /** No new route after a cancel: the trip really ended. */
    private val endReplanning = Runnable { update { if (replanning) withoutRoute() else this } }

    /** A route event after a cancel: the trip goes on (with its new route). */
    private inline fun routeBack(transform: NaviState.() -> NaviState) {
        mainHandler.removeCallbacks(endReplanning)
        event { transform().copy(replanning = false) }
    }

    // ---- Callbacks ----

    private val listener = object : NaviHelperEventListener {
        override fun onRouteStarted(routeStartInfo: RouteStartInfo) = routeBack { copy(guiding = true) }.also { refresh() }
        override fun onRoutingCompleted() = routeBack { this }.also { refresh() }
        override fun onReRouteEnded() = routeBack { this }.also { refresh() }
        override fun onDestinationChanged() = event { this }.also { refresh() }
        // After a waypoint change this (with its RouteStartInfo) is what announces the new route, not onRouteStarted.
        override fun onWaypointChanged(waypointChangedInfo: WaypointChangedInfo) = routeBack { this }.also { refresh() }
        override fun onWayPointArrived(waypointArrivedInfo: WaypointArrivedInfo) = event { this }.also { refresh() }

        // Every waypoint change goes through a cancel and a new route 8–12 s later. Measured on 2026-11-05:
        // onRouteCancelled, FREE_DRIVE, an empty DrivingInfo and DestinationInfo, SAFE_GUIDING; then onWaypointChanged,
        // ROUTE_DRIVE, ROUTE_GUIDING and the new route's info. Until then (or REPLAN_GRACE_MS) the trip is kept, so the
        // advisor does not take the gap for the end of the trip.
        override fun onRouteCancelled() {
            mainHandler.removeCallbacks(endReplanning)
            event { if (guiding) copy(replanning = true) else withoutRoute() }
            mainHandler.postDelayed(endReplanning, REPLAN_GRACE_MS)
        }

        override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) {
            mainHandler.removeCallbacks(endReplanning)
            event { withoutRoute() }
        }

        override fun onNavigationStatus(status: NavigationStatus) {
            if (status == NavigationStatus.ROUTE_DRIVE) routeBack { copy(guiding = true) } else event { if (replanning) this else copy(guiding = false) }
        }

        override fun onRouteStateInfo(routeStateInfo: RouteStateInfo) {
            val guiding = routeStateInfo.naviMode == NaviMode.ROUTE_GUIDING || routeStateInfo.naviMode == NaviMode.SIMULATION_GUIDING
            if (guiding) {
                routeBack { copy(guiding = true, destinationRemainWh = routeStateInfo.remainDestinationChargingInt) }
            } else {
                event { if (replanning) this else withoutRoute() }
            }
        }

        override fun onDestinationInfo(destinationInfo: DestinationInfo) = event {
            val d = destinationInfo
            val known = d.name.isNotBlank() || d.latitude != 0.0
            copy(
                destination = if (!known) null else NaviDestination(
                    name = d.name,
                    location = GeoPoint(d.latitude, d.longitude).takeIf { d.latitude != 0.0 || d.longitude != 0.0 },
                    remainDistanceM = d.remainDistance.takeIf { it > 0 },
                    remainTimeS = d.remainTime.takeIf { it > 0 },
                    poiId = d.poiId.ifBlank { null },
                ),
            )
        }

        override fun onWayPointInfo(waypointInfoList: List<WaypointInfo>) = event {
            debug { "waypoints ${waypointInfoList.map { it.name }}" }
            copy(waypoints = waypointInfoList.map { NaviWaypoint(it.name, it.remainDistance.takeIf { m -> m > 0 }, it.poiId.ifBlank { null }) })
        }

        override fun onRecentDestinationInfo(recentDestinationInfoList: List<RecentDestinationInfo>) = event {
            copy(
                recentPlaces = recentDestinationInfoList.mapNotNull { r ->
                    if (r.latitude == 0.0 && r.longitude == 0.0) null
                    else NaviPlace(r.name, GeoPoint(r.latitude, r.longitude), r.poiId.ifBlank { null })
                },
            )
        }

        override fun onCurrentLocationInfo(currentLocationInfo: CurrentLocationInfo) = event {
            val c = currentLocationInfo
            val id = c.routeId.ifBlank { null }
            if (id != routeId) debug { "routeId $routeId -> $id" }
            if (c.latitude == 0.0 && c.longitude == 0.0) this
            else copy(location = GeoPoint(c.latitude, c.longitude), headingDeg = c.heading, routeId = id)
        }

        override fun onDrivingInfo(drivingInfo: DrivingInfo) = event {
            val dest = drivingInfo.destination
            // After a cancel or before re-routing the navi sends an all-zero DrivingInfo.
            if (dest.distance == 0 && dest.duration == 0) {
                debug { "driving info cleared" }
                copy(stops = emptyList(), stopsKnown = false, destinationStop = null)
            } else {
                debug { "driving info stops ${drivingInfo.waypoints.map { "${it.name} charging=${it.isChargingStation}" }} destination charging=${dest.isChargingStation}" }
                copy(
                    stops = drivingInfo.waypoints.map { it.toStop() },
                    stopsKnown = true,
                    destinationStop = dest.toStop(),
                    destinationRemainWh = dest.remainDestinationChargingValue,
                    targetSocPct = dest.targetSoc.takeIf { it > 0 } ?: targetSocPct,
                )
            }
        }

        override fun onTripInfo(tripInfo: TripInfo) {
            heartbeat()
            tripResults.tryEmit(tripInfo)
        }

        override fun onError(errorCode: NaviErrorCode) = event {
            Log.w(TAG, "navi error $errorCode")
            copy(
                lastError = errorCode.name,
                lowBatteryWarningAtMs = if (errorCode == NaviErrorCode.CAUSE_LOW_BATTERY) System.currentTimeMillis() else lowBatteryWarningAtMs,
            )
        }

        override fun onCurrentRoadSpeedLimit(speedLimit: Int) = heartbeat()
    }

    // ---- helpers ----

    private fun heartbeat() {
        lastHeartbeatMs = SystemClock.elapsedRealtime()
    }

    private inline fun event(transform: NaviState.() -> NaviState) {
        heartbeat()
        update(transform)
    }

    private inline fun update(transform: NaviState.() -> NaviState) {
        // Stamped only on a real change: a callback that repeats the same values must not wake every collector.
        _state.update { old -> old.transform().let { if (it == old) old else it.copy(updatedAtMs = System.currentTimeMillis()) } }
    }

    private inline fun command(name: String, block: NaviHelper.() -> Unit): Boolean {
        val helper = navi?.takeIf { connected }
        if (helper == null) {
            Log.w(TAG, "$name: navi not connected")
            return false
        }
        return try {
            helper.block()
            true
        } catch (e: Exception) {
            Log.w(TAG, "$name failed: ${e.message}")
            update { copy(lastError = "$name: ${e.message}") }
            false
        }
    }

    /** Stop names only, never coordinates; debug builds only. */
    private inline fun debug(message: () -> String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message())
    }

    private fun NaviState.withoutRoute() = copy(
        guiding = false, replanning = false, destination = null, destinationRemainWh = null, stops = emptyList(), stopsKnown = false,
        destinationStop = null, waypoints = emptyList(),
    )

    private fun DrivingState.toStop() = NaviStop(
        name = name,
        distanceFromStartM = distance,
        remainEnergyWh = remainDestinationChargingValue,
        isChargingStation = isChargingStation,
        maxChargingKw = maxChargingPower.takeIf { it > 0 },
        chargerCount = chargerCount.takeIf { it > 0 },
    )

    private fun TripInfo.isAnswerTo(target: NaviTarget): Boolean {
        val points = summary.routePoints
        val end = points.firstOrNull { it.pointType == RoutePointType.DESTINATION } ?: points.lastOrNull() ?: return false
        // The navi snaps the request to the road network (tens of metres in the spike).
        return Geo.distanceKm(GeoPoint(end.latitude, end.longitude), target.location) <= TRIP_MATCH_KM
    }

    private fun TripInfo.toEstimate() = TripEstimate(
        distanceM = distance,
        etaS = eta,
        arrivalSocPct = estimatedSocAtDest,
        energyWh = energyConsumption,
        routePoints = summary.routePoints.map { p ->
            RoutePoint(
                name = p.poiName,
                location = GeoPoint(p.latitude, p.longitude),
                poiId = p.poiId.ifBlank { null },
                ratioOnRoute = p.ratioOnRoute,
                isChargingStation = p.poiType == RoutePointPoiType.EV_CHARGING,
                isAuto = p.settingType == RoutePointSettingType.AUTO,
                isDestination = p.pointType == RoutePointType.DESTINATION,
            )
        },
    )

    private fun WaypointSlot.toIndex() = when (this) {
        WaypointSlot.FIRST -> WaypointIndex.FIRST
        WaypointSlot.SECOND -> WaypointIndex.SECOND
    }

    private companion object {
        const val TAG = "PleosNaviRepo"
        const val TRIP_TIMEOUT_MS = 6_000L
        const val TRIP_MATCH_KM = 0.5
        const val WATCHDOG_INTERVAL_MS = 5_000L
        const val HEARTBEAT_TIMEOUT_MS = 15_000L
        const val REFRESH_INTERVAL_MS = 30_000L
        const val REPLAN_GRACE_MS = 20_000L
        const val RECONNECT_MIN_MS = 2_000L
        const val RECONNECT_MAX_MS = 60_000L
    }
}
