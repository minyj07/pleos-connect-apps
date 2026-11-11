package com.example.pleosev.debug

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.NavigationStatus
import ai.pleos.playground.navi.constants.RouteOption
import ai.pleos.playground.navi.constants.WaypointIndex
import ai.pleos.playground.navi.data.BookmarkInfo
import ai.pleos.playground.navi.data.CameraAlert
import ai.pleos.playground.navi.data.CautionPoint
import ai.pleos.playground.navi.data.ChargerOperator
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.DrivingInfo
import ai.pleos.playground.navi.data.NavigationScreenRequest
import ai.pleos.playground.navi.data.RecentDestinationInfo
import ai.pleos.playground.navi.data.RequestWaypointInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteOverviewShowInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.data.RouteStateInfo
import ai.pleos.playground.navi.data.RouteWithWaypointsRequest
import ai.pleos.playground.navi.data.TBTInfo
import ai.pleos.playground.navi.data.TripInfo
import ai.pleos.playground.navi.data.TripRequest
import ai.pleos.playground.navi.data.WaypointArrivedInfo
import ai.pleos.playground.navi.data.WaypointChangedInfo
import ai.pleos.playground.navi.data.WaypointInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Phase 0 spike (debug builds only): drives the stock navigation through NaviHelper and logs every
 * callback with its gap to the previous one and the delivering thread, so we can see what the Pleos
 * Navi Service actually sends. Process-scoped so callbacks keep flowing while the probe activity is
 * in the background (and so the notification action / foreground service can reuse it).
 *
 *   adb shell am start -n com.example.pleosev/.debug.NaviProbeActivity --es probe init,getall
 *   adb logcat -s NaviProbe
 */
object NaviProbe {

    private const val TAG = "NaviProbe"
    private const val MAX_LINES = 400

    /** onCurrentRoadSpeedLimit is documented to arrive every second: count all, log every Nth. */
    private const val SPEED_LIMIT_LOG_EVERY = 15

    private data class Place(val name: String, val lat: Double, val lon: Double, val address: String)

    // poiId is left empty on purpose: does the navi accept raw coordinates from a 3rd party app?
    private val BUSAN_STATION = Place("부산역", 35.1151, 129.0422, "부산 동구 중앙대로 206")
    private val CHEONAN = Place("프로브 경유지 천안", 36.8151, 127.1139, "충남 천안시 동남구")
    private val DAEJEON = Place("프로브 경유지 대전", 36.3504, 127.3845, "대전 중구")
    private val GIMCHEON = Place("프로브 경유지 김천", 36.1398, 128.1136, "경북 김천시")

    /** Actions accepted by [run] (also the probe screen's buttons). */
    val ACTIONS = listOf(
        "init", "release", "getall", "route_busan", "route_ww", "wp1", "wp2", "wp1_again",
        "rm1", "rm2", "rmall", "cancel", "reroute", "trip_daejeon", "trip_busan",
        "screen_nearby", "screen_search", "screen_overview", "screen_fg",
        "notify", "notify_nav", "notify_later", "notify_nav_later", "fgs_start", "fgs_stop", "stats", "clear",
    )

    /** Delay for "notify_later": time to bring the navi to the front before the alert is posted. */
    private const val NOTIFY_LATER_MS = 8_000L
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines.asStateFlow()

    private class CallbackStats {
        var count = 0
        var lastAt = 0L
        var minGapMs = Long.MAX_VALUE
        var maxGapMs = 0L
        val threads = LinkedHashSet<String>()
    }

    private val stats = LinkedHashMap<String, CallbackStats>()
    private val pendingActions = ArrayDeque<String>()
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

    private var navi: NaviHelper? = null
    @Volatile private var connected = false

    fun run(context: Context, action: String) {
        log("> $action")
        val app = context.applicationContext
        try {
            when (action) {
                "init" -> init(app)
                "release" -> release()
                "getall" -> withNavi(app, action) {
                    getRouteStateInfo()
                    getDestinationInfo()
                    getWaypointInfo()
                    getCurrentLocationInfo()
                    getTBTInfo()
                    getChargerOperatorInfo()
                    getBookmarkInfo()
                    getRecentDestinationInfo()
                }
                "route_busan" -> withNavi(app, action) { requestRoute(BUSAN_STATION.toRouteInfo()) }
                "route_ww" -> withNavi(app, action) {
                    requestRouteWithWaypoints(
                        RouteWithWaypointsRequest(BUSAN_STATION.toRouteInfo(), listOf(CHEONAN.toWaypoint(WaypointIndex.FIRST))),
                    )
                }
                "wp1" -> withNavi(app, action) { addWaypoint(CHEONAN.toWaypoint(WaypointIndex.FIRST)) }
                "wp2" -> withNavi(app, action) { addWaypoint(DAEJEON.toWaypoint(WaypointIndex.SECOND)) }
                "wp1_again" -> withNavi(app, action) { addWaypoint(GIMCHEON.toWaypoint(WaypointIndex.FIRST)) }
                "rm1" -> withNavi(app, action) { removeWaypoint(WaypointIndex.FIRST) }
                "rm2" -> withNavi(app, action) { removeWaypoint(WaypointIndex.SECOND) }
                "rmall" -> withNavi(app, action) { removeWaypoint(WaypointIndex.ALL) }
                "cancel" -> withNavi(app, action) { cancelRoute() }
                "reroute" -> withNavi(app, action) { requestReRoute() }
                "trip_daejeon" -> withNavi(app, action) { requestTrip(DAEJEON.toTripRequest()) }
                "trip_busan" -> withNavi(app, action) { requestTrip(BUSAN_STATION.toTripRequest()) }
                "screen_nearby" -> withNavi(app, action) { requestNavigationScreen(NavigationScreenRequest.NearbyChargingStations) }
                "screen_search" -> withNavi(app, action) { requestNavigationScreen(NavigationScreenRequest.SearchPlace("충전소")) }
                "screen_overview" -> withNavi(app, action) {
                    requestNavigationScreen(NavigationScreenRequest.OverviewRoute(RouteOverviewShowInfo(5000)))
                }
                "screen_fg" -> withNavi(app, action) { requestNavigationScreen(NavigationScreenRequest.Foreground) }
                "notify" -> ProbeNotifications.postAlert(app, navigationCategory = false)
                "notify_nav" -> ProbeNotifications.postAlert(app, navigationCategory = true)
                "notify_later" -> mainHandler.postDelayed({ ProbeNotifications.postAlert(app, navigationCategory = false) }, NOTIFY_LATER_MS)
                "notify_nav_later" -> mainHandler.postDelayed({ ProbeNotifications.postAlert(app, navigationCategory = true) }, NOTIFY_LATER_MS)
                "fgs_start" -> ContextCompat.startForegroundService(app, Intent(app, ProbeForegroundService::class.java))
                "fgs_stop" -> app.stopService(Intent(app, ProbeForegroundService::class.java))
                "stats" -> dumpStats()
                "clear" -> {
                    synchronized(stats) { stats.clear() }
                    _lines.value = emptyList()
                }
                else -> log("unknown action '$action' (known: ${ACTIONS.joinToString()})")
            }
        } catch (e: Exception) {
            log("! $action threw ${e.javaClass.name}: ${e.message}")
        }
    }

    /**
     * Ad-hoc request built from adb extras (see [NaviProbeActivity]): kind = wp | route | trip.
     * Unlike [run] it is not queued: the probe must already be connected.
     */
    fun custom(kind: String, name: String, lat: Double, lon: Double, poiId: String, poiProvider: String, index: WaypointIndex) {
        log("> custom $kind '$name' ($lat, $lon) poiId='$poiId' provider='$poiProvider' $index")
        val n = navi
        if (n == null || !connected) {
            log("custom: not connected (run init first)")
            return
        }
        try {
            when (kind) {
                "wp" -> n.addWaypoint(
                    RequestWaypointInfo(
                        latitude = lat, longitude = lon, poiName = name, waypointIndex = index,
                        poiId = poiId, address = "", poiSubId = "", poiProvider = poiProvider,
                    ),
                )
                "route" -> n.requestRoute(
                    RouteInfo(
                        latitude = lat, longitude = lon, displayLatitude = null, displayLongitude = null,
                        poiName = name, poiId = poiId, address = "", poiSubId = "", poiProvider = poiProvider,
                    ),
                )
                "trip" -> n.requestTrip(TripRequest(latitude = lat, longitude = lon, poiId = poiId, poiName = name, poiSubId = null))
                else -> log("custom: unknown kind '$kind' (wp | route | trip)")
            }
        } catch (e: Exception) {
            log("! custom $kind threw ${e.javaClass.name}: ${e.message}")
        }
    }

    fun log(message: String) {
        Log.i(TAG, message)
        val stamped = "${LocalTime.now().format(timeFormat)} $message"
        _lines.update { (listOf(stamped) + it).take(MAX_LINES) }
    }

    // ---- NaviHelper lifecycle ----

    private fun init(app: Context) {
        if (navi != null) {
            log("already initialized (connected=$connected)")
            return
        }
        val n = NaviHelper(app)
        navi = n
        val startedAt = SystemClock.elapsedRealtime()
        n.initialize(
            onConnected = {
                connected = true
                log("initialize: connected after ${SystemClock.elapsedRealtime() - startedAt} ms [${Thread.currentThread().name}]")
                n.addListener(listener)
                while (pendingActions.isNotEmpty()) run(app, pendingActions.removeFirst())
            },
            onFailed = { t ->
                connected = false
                log("initialize: FAILED after ${SystemClock.elapsedRealtime() - startedAt} ms: ${t.javaClass.name}: ${t.message}")
            },
        )
    }

    private fun release() {
        val n = navi ?: run { log("not initialized"); return }
        runCatching { n.removeListener(listener) }
        runCatching { n.release() }
        navi = null
        connected = false
        log("released")
    }

    private inline fun withNavi(app: Context, action: String, block: NaviHelper.() -> Unit) {
        val n = navi
        if (n != null && connected) {
            n.block()
        } else {
            pendingActions.addLast(action)
            log("not connected yet: '$action' runs after initialize")
            if (n == null) init(app)
        }
    }

    // ---- Callbacks (every one is overridden so nothing goes unnoticed) ----

    private val listener = object : NaviHelperEventListener {
        override fun onRouteStarted(routeStartInfo: RouteStartInfo) = cb("onRouteStarted", routeStartInfo)
        override fun onRoutingCompleted() = cb("onRoutingCompleted", null)
        override fun onReRouteEnded() = cb("onReRouteEnded", null)
        override fun onRouteOptionChanged(routeOption: RouteOption) = cb("onRouteOptionChanged", routeOption)
        override fun onRouteCancelled() = cb("onRouteCancelled", null)
        override fun onWaypointChanged(waypointChangedInfo: WaypointChangedInfo) = cb("onWaypointChanged", waypointChangedInfo)
        override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) = cb("onDestinationArrived", destinationArrivedInfo)
        override fun onWayPointArrived(waypointArrivedInfo: WaypointArrivedInfo) = cb("onWayPointArrived", waypointArrivedInfo)
        override fun onBookMarkChanged() = cb("onBookMarkChanged", null)
        override fun onDestinationChanged() = cb("onDestinationChanged", null)
        override fun onError(errorCode: NaviErrorCode) = cb("onError", errorCode)
        override fun onCurrentLocationInfo(currentLocationInfo: CurrentLocationInfo) = cb("onCurrentLocationInfo", currentLocationInfo)
        override fun onRouteStateInfo(routeStateInfo: RouteStateInfo) = cb("onRouteStateInfo", routeStateInfo)
        override fun onDestinationInfo(destinationInfo: DestinationInfo) = cb("onDestinationInfo", destinationInfo)
        override fun onWayPointInfo(waypointInfoList: List<WaypointInfo>) = cb("onWayPointInfo", waypointInfoList)
        override fun onRecentDestinationInfo(recentDestinationInfoList: List<RecentDestinationInfo>) =
            cb("onRecentDestinationInfo", recentDestinationInfoList)
        override fun onBookmarkInfo(bookmarkInfoList: List<BookmarkInfo>) = cb("onBookmarkInfo", bookmarkInfoList)
        override fun onCurrentRoadSpeedLimit(speedLimit: Int) = cb("onCurrentRoadSpeedLimit", speedLimit, SPEED_LIMIT_LOG_EVERY)
        override fun onCameraAlert(cameraAlertList: List<CameraAlert>) = cb("onCameraAlert", cameraAlertList)
        override fun onDrivingInfo(drivingInfo: DrivingInfo) = cb("onDrivingInfo", drivingInfo)
        override fun onNavigationStatus(status: NavigationStatus) = cb("onNavigationStatus", status)
        override fun onTBTInfo(tbtInfoList: List<TBTInfo>) = cb("onTBTInfo", tbtInfoList)
        override fun onChargerOperatorInfo(chargerOperatorList: List<ChargerOperator>) = cb("onChargerOperatorInfo", chargerOperatorList)
        override fun onCautionPointInfo(cautionPointList: List<CautionPoint>) = cb("onCautionPointInfo", cautionPointList)
        override fun onTripInfo(tripInfo: TripInfo) = cb("onTripInfo", tripInfo)
    }

    private fun cb(name: String, payload: Any?, logEvery: Int = 1) {
        val now = SystemClock.elapsedRealtime()
        val thread = Thread.currentThread().name
        val line = synchronized(stats) {
            val s = stats.getOrPut(name) { CallbackStats() }
            val gap = if (s.lastAt == 0L) null else now - s.lastAt
            if (gap != null) {
                s.minGapMs = minOf(s.minGapMs, gap)
                s.maxGapMs = maxOf(s.maxGapMs, gap)
            }
            s.lastAt = now
            s.count++
            if (s.threads.size < 4) s.threads.add(thread)
            if (logEvery == 1 || s.count % logEvery == 1) {
                "$name #${s.count}${gap?.let { " +${it}ms" }.orEmpty()} [$thread] ${payload ?: ""}"
            } else {
                null
            }
        }
        line?.let(::log)
    }

    private fun dumpStats() {
        val snapshot = synchronized(stats) {
            stats.map { (name, s) ->
                val gaps = if (s.count > 1) "${s.minGapMs}..${s.maxGapMs}ms" else "-"
                "$name count=${s.count} gap=$gaps threads=${s.threads}"
            }
        }
        if (snapshot.isEmpty()) log("stats: no callbacks yet") else snapshot.forEach { log("stats: $it") }
    }

    // ---- Request builders ----

    private fun Place.toRouteInfo() = RouteInfo(
        latitude = lat,
        longitude = lon,
        displayLatitude = null,
        displayLongitude = null,
        poiName = name,
        poiId = "",
        address = address,
        poiSubId = "",
    )

    private fun Place.toWaypoint(index: WaypointIndex) = RequestWaypointInfo(
        latitude = lat,
        longitude = lon,
        poiName = name,
        waypointIndex = index,
        poiId = "",
        address = address,
        poiSubId = "",
    )

    private fun Place.toTripRequest() = TripRequest(latitude = lat, longitude = lon, poiId = "", poiName = name, poiSubId = null)
}
