package com.example.pleosev.advisor

import android.util.Log
import com.example.pleosev.BuildConfig
import com.example.pleosev.advisor.domain.AddedStation
import com.example.pleosev.advisor.domain.CandidateRefiner
import com.example.pleosev.advisor.domain.ChargeNeed
import com.example.pleosev.advisor.domain.ChargeNeedEvaluator
import com.example.pleosev.advisor.domain.EstimateBasis
import com.example.pleosev.advisor.domain.NaviAction
import com.example.pleosev.advisor.domain.NaviActionKind
import com.example.pleosev.advisor.domain.NeedInput
import com.example.pleosev.advisor.domain.NeedKind
import com.example.pleosev.advisor.domain.PlannedStopResolver
import com.example.pleosev.advisor.domain.PlannedStopVerdict
import com.example.pleosev.advisor.domain.Reach
import com.example.pleosev.advisor.domain.RefineContext
import com.example.pleosev.advisor.domain.Severity
import com.example.pleosev.advisor.domain.StatusRefresh
import com.example.pleosev.advisor.domain.StopRole
import com.example.pleosev.advisor.domain.WaypointPlanner
import com.example.pleosev.data.remote.AdvisorApi
import com.example.pleosev.data.remote.ApiResult
import com.example.pleosev.data.settings.AdvisorPrefs
import com.example.pleosev.model.EvDashboardState
import com.example.pleosev.navi.NaviConnection
import com.example.pleosev.navi.NaviRepository
import com.example.pleosev.navi.NaviState
import com.example.pleosev.navi.NaviTarget
import com.example.pleosev.navi.RoutePoint
import com.example.pleosev.shared.api.Candidate
import com.example.pleosev.shared.api.ChargePrefs
import com.example.pleosev.shared.api.GeoPoint
import com.example.pleosev.shared.api.PlannedStop
import com.example.pleosev.shared.api.ReachWindow
import com.example.pleosev.shared.api.RecommendMode
import com.example.pleosev.shared.api.RecommendationRequest
import com.example.pleosev.shared.api.RecommendationResponse
import com.example.pleosev.shared.api.StationSummary
import com.example.pleosev.shared.geo.Geo
import com.example.pleosev.shared.scoring.StationScorer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

/** A recommended station with what the driver can do with it (primary action first). */
data class AdvisorCandidate(val candidate: Candidate, val actions: List<NaviAction>, val refinedByNavi: Boolean)

/** A station named in a notification: enough to act on it after the advisor moved on (or the process restarted). */
data class StationRef(val statId: String, val name: String, val location: GeoPoint, val address: String)

data class AdvisorState(
    val need: ChargeNeed = ChargeNeed.NONE,
    val attention: Severity = Severity.NONE,     // need severity, raised when the planned stop is busy
    val consent: Boolean = false,
    val loading: Boolean = false,
    val plannedStopName: String? = null,         // as the navi names it
    val plannedStopRole: StopRole? = null,       // the navi's own stop, a waypoint the driver added, or the destination
    val plannedStop: StationSummary? = null,     // live status from the server
    val plannedVerdict: PlannedStopVerdict? = null,
    val candidates: List<AdvisorCandidate> = emptyList(),
    val window: ReachWindow? = null,
    val problem: String? = null,                 // why nothing could be shown
    val serverReachable: Boolean = true,
    val updatedAtMs: Long? = null,
    val lastAction: String? = null,
)

/**
 * Process-wide charging advisor: watches the vehicle, the stock navi and the settings, decides when a
 * recommendation is needed (ChargeNeedEvaluator), asks the relay server, refines the best candidates
 * with the navi's own trip estimates and exposes the result. Nothing is sent to the server before the
 * driver agreed to share the car's position ([AdvisorPrefs.locationConsent]).
 *
 * Between full recommendations only the charger status is refreshed (station ids, no position); the
 * position goes out again when the need or the route changes, after [REFETCH_AFTER_KM] of driving or
 * [FULL_REFRESH_EVERY_MS], or when the fresh status looks worse.
 */
class ChargingAdvisor(
    private val scope: CoroutineScope,
    private val vehicle: StateFlow<EvDashboardState>,
    private val navi: NaviRepository,
    private val prefs: Flow<AdvisorPrefs>,
    private val api: AdvisorApi,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private data class Inputs(val vehicle: EvDashboardState, val navi: NaviState, val prefs: AdvisorPrefs)

    private val evaluator = ChargeNeedEvaluator()
    private val _state = MutableStateFlow(AdvisorState())
    val state: StateFlow<AdvisorState> = _state.asStateFlow()

    @Volatile private var latest: Inputs? = null
    private var fetchJob: Job? = null
    private var lastFetchKey: String? = null
    private var lastFetchAtMs = 0L
    private var lastFullFetchAtMs = 0L
    private var lastFullFetchOrigin: GeoPoint? = null // memory only, never logged
    @Volatile private var lastPlannedLocation: GeoPoint? = null // the route's charging stop of the last full fetch
    /** The last full recommendation failed (not just a status refresh): only that is retried with the position. */
    @Volatile private var fullFetchFailed = false
    /** The fetch key of the cards on screen. */
    @Volatile private var publishedKey: String? = null

    /** Stations the driver added (or navigated to) through the advisor, by the name the navi will report. */
    private val addedStations = ConcurrentHashMap<String, AddedStation>()

    /** The navi's own charging stops per route, found with requestTrip ([PlannedStopResolver] says when). */
    private val probedStops = ConcurrentHashMap<String, List<RoutePoint>>()
    @Volatile private var probingRoute: String? = null

    fun start() {
        scope.launch {
            combine(vehicle, navi.state, prefs) { v, n, p -> Inputs(v, n, p) }.collect { onInputs(it) }
        }
        scope.launch {
            while (isActive) {
                delay(TICK_MS)
                val inputs = latest ?: continue
                val key = lastFetchKey ?: continue
                val failed = !_state.value.serverReachable
                if (clock() - lastFetchAtMs >= if (failed) RETRY_AFTER_FAILURE_MS else REFRESH_EVERY_MS) {
                    // A failed status refresh is retried as one (station ids only), not with the position.
                    if (canRefreshStatusOnly(inputs)) fetchStatus(inputs, key) else fetch(inputs, _state.value.need, key)
                }
            }
        }
    }

    /** Fetch again now (pull to refresh, or after the route changed). */
    fun refresh() {
        lastFetchKey = null
        latest?.let(::onInputs)
    }

    /** Fresh charger counts for the current cards now; a full refresh when that is not enough. */
    fun refreshStatus() {
        val inputs = latest ?: return
        val key = lastFetchKey ?: return
        if (canRefreshStatusOnly(inputs)) fetchStatus(inputs, key) else fetch(inputs, _state.value.need, key)
    }

    /** A card button. The card may be minutes old: the action is planned again on the current route (free slot). */
    fun apply(action: NaviAction, station: StationSummary) {
        val current = navi.state.value
        val actions = WaypointPlanner.actions(station, current, replacesDestination = stopRole(current) == StopRole.DESTINATION)
        apply(actions.firstOrNull { it.kind == action.kind } ?: actions.first(), station.statId)
    }

    /**
     * Runs a notification action. The alert may be minutes old (or the process new), so the action is
     * planned again on the current route, e.g. "add as waypoint" takes whichever slot is free now.
     */
    suspend fun applyFromAlert(ref: StationRef, kind: NaviActionKind): Boolean {
        val connected = withTimeoutOrNull(NAVI_WAIT_MS) { navi.state.first { it.connection == NaviConnection.CONNECTED } }
        if (connected == null) {
            _state.update { it.copy(lastAction = "내비에 연결되지 않았어요") }
            return false
        }
        val station = _state.value.candidates.firstOrNull { it.candidate.station.statId == ref.statId }?.candidate?.station
        val target = station?.let { WaypointPlanner.target(it) } ?: NaviTarget(ref.name, ref.location, address = ref.address)
        val current = navi.state.value
        val actions = WaypointPlanner.actions(target, current, replacesDestination = stopRole(current) == StopRole.DESTINATION)
        return apply(actions.firstOrNull { it.kind == kind } ?: actions.first(), ref.statId)
    }

    fun showNaviChargers() = navi.showNearbyChargers()

    private fun apply(action: NaviAction, statId: String): Boolean {
        val sent = when (action) {
            is NaviAction.RouteTo -> navi.routeTo(action.target)
            is NaviAction.AddWaypoint -> navi.addWaypoint(action.target, action.slot)
            is NaviAction.SearchInNavi -> navi.searchInNavi(action.keyword)
        }
        // Remembered so the station is checked (with its statId) once the navi reports it as a stop or the destination.
        val target = (action as? NaviAction.RouteTo)?.target ?: (action as? NaviAction.AddWaypoint)?.target
        if (sent && target != null) addedStations[target.name] = AddedStation(target.location, statId)
        _state.update { it.copy(lastAction = if (sent) "내비에 요청했어요" else "내비에 연결되지 않았어요") }
        if (sent && action !is NaviAction.SearchInNavi) {
            // The navi re-routes in ~10 s (spike); look again once the new route is in.
            scope.launch {
                delay(ROUTE_SETTLE_MS)
                refresh()
            }
        }
        return sent
    }

    // ---- evaluation ----

    private fun onInputs(inputs: Inputs) {
        latest = inputs
        // The navi is planning the route again after a waypoint change: the trip goes on, nothing to decide yet.
        if (inputs.navi.replanning) return
        probeRouteIfNeeded(inputs.navi)
        val need = evaluator.evaluate(needInput(inputs), clock())
        _state.update { it.copy(need = need, consent = inputs.prefs.locationConsent, attention = attention(need, it.plannedVerdict)) }
        val key = fetchKey(need, inputs)
        if (key == null) {
            fetchJob?.cancel()
            lastFetchKey = null
            _state.update {
                it.copy(
                    loading = false, candidates = emptyList(), plannedStop = null, plannedStopName = null,
                    plannedStopRole = null, plannedVerdict = null, window = null, problem = null, attention = need.severity,
                )
            }
            return
        }
        // Until the route is fully reported (destination, the navi's charging stop) the need may still change:
        // send the position once, after.
        val probing = probingRoute != null && probingRoute == routeKey(inputs.navi)
        val routeIncomplete = inputs.navi.guiding && inputs.navi.destination == null
        if (probing || routeIncomplete) return
        if (key != lastFetchKey || plannedStopOutOfReach(inputs, need)) fetch(inputs, need, key)
    }

    /**
     * Reachability is decided when the recommendation is fetched; the range can drop afterwards without changing
     * the fetch key (or arrive later than the SOC). Checked locally: nothing is sent until it flips.
     */
    private fun plannedStopOutOfReach(i: Inputs, need: ChargeNeed): Boolean {
        if (need.kind != NeedKind.PLANNED_STOP || need.severity != Severity.URGENT) return false
        val s = _state.value
        if (fetchJob?.isActive == true || !s.serverReachable || clock() - lastFetchAtMs < REACH_RECHECK_MIN_MS) return false
        if (s.plannedVerdict == null || s.plannedVerdict == PlannedStopVerdict.UNREACHABLE) return false
        val stop = lastPlannedLocation ?: return false
        val here = i.navi.location ?: return false
        return !Reach.canReach(here, stop, rangeKm(i.vehicle), i.vehicle.socPercent?.toDouble(), need.ratePctPerKm)
    }

    /** The car's range for decisions: null while it contradicts the SOC (the dashboard still shows it). */
    private fun rangeKm(v: EvDashboardState): Double? =
        Reach.usableRangeKm(v.rangeRemainingKm?.toDouble(), v.socPercent?.toDouble())

    private fun needInput(i: Inputs) = NeedInput(
        socPct = i.vehicle.socPercent?.toDouble(),
        rangeKm = rangeKm(i.vehicle),
        capacityKwh = i.vehicle.batteryCapacityKwh?.toDouble(),
        charging = i.vehicle.isCharging || i.vehicle.chargePortConnected == true,
        guiding = i.navi.guiding,
        remainDistanceKm = i.navi.destination?.remainDistanceM?.let { it / 1000.0 },
        destinationRemainWh = i.navi.destinationRemainWh,
        naviTargetSocPct = i.navi.targetSocPct,
        chargingStopInRoute = routeStopName(i.navi) != null,
        lowBatteryWarning = i.navi.lowBatteryWarningAtMs?.let { clock() - it < LOW_BATTERY_WARNING_MS } ?: false,
        routeKey = routeKey(i.navi),
        userReserveSocPct = i.prefs.reserveSocPct,
    )

    /** What a fetch depends on; a new key means a new request. Null: nothing to fetch. */
    private fun fetchKey(need: ChargeNeed, i: Inputs): String? {
        if (!need.active || !i.prefs.locationConsent) return null
        return listOf(
            need.kind, need.severity, need.routeKey, routeStopName(i.navi),
            // Destination and position arrive in separate callbacks: ask again once they are known.
            i.navi.destination?.location != null, i.navi.location != null,
            i.prefs.reserveSocPct, i.prefs.minKw, i.prefs.maxDetourMin,
        ).joinToString("|")
    }

    /**
     * The trip, by its destination. The navi's routeId changes whenever the navi re-routes by itself (every
     * few minutes while guiding in E2E), which is still the same trip: no new need, request or probe.
     */
    private fun routeKey(navi: NaviState): String? =
        navi.destination?.let { d -> "${d.name}@${d.location?.let { "${round3(it.lat)},${round3(it.lon)}" }}" }

    private fun routeStopName(navi: NaviState): String? =
        PlannedStopResolver.stopName(navi, addedStations, routeKey(navi)?.let { probedStops[it] })

    private fun stopRole(navi: NaviState): StopRole? = routeStopName(navi)?.let { PlannedStopResolver.role(it, navi) }

    /** Guiding without DrivingInfo (e.g. after a restart): ask the navi once which charging stop it plans. */
    private fun probeRouteIfNeeded(navi: NaviState) {
        val key = routeKey(navi) ?: return
        // The route id can arrive before the destination: without its position there is nothing to ask yet.
        if (!navi.guiding || navi.destination?.location == null || probedStops.containsKey(key) || probingRoute == key) return
        if (!PlannedStopResolver.needsProbe(navi, addedStations)) return
        probingRoute = key
        scope.launch {
            val stops = probe(navi)
            if (BuildConfig.DEBUG) Log.d(TAG, "probe: navi plans ${stops?.map { it.name }}")
            probedStops[key] = stops.orEmpty() // no answer: taken as no stop rather than asking again on every update
            probingRoute = null
            latest?.let(::onInputs)
        }
    }

    /** The charging stops the navi plans by itself on the way to the destination (manual waypoints ignored). */
    private suspend fun probe(navi: NaviState): List<RoutePoint>? {
        val destination = navi.destination ?: return null
        val location = destination.location ?: return null
        return this.navi.estimateTrip(NaviTarget(destination.name, location, destination.poiId.orEmpty()))?.autoChargingStops
    }

    // ---- fetching ----

    private fun fetch(trigger: Inputs, triggerNeed: ChargeNeed, key: String) {
        lastFetchKey = key
        lastFetchAtMs = clock()
        fetchJob?.cancel()
        fetchJob = scope.launch {
            _state.update { it.copy(loading = true) }
            // Route and vehicle state arrive in separate callbacks within a second (SOC before range): one request
            // covers them all, built from what has arrived by then. A change of the fetch key cancels this job.
            delay(INPUT_SETTLE_MS)
            val inputs = latest ?: trigger
            val need = _state.value.need.takeIf { it.kind == triggerNeed.kind } ?: triggerNeed
            val origin = inputs.navi.location
            if (origin == null) {
                _state.update { it.copy(loading = false, problem = "내비가 현재 위치를 아직 알려 주지 않았어요") }
                return@launch
            }
            lastFullFetchAtMs = clock()
            lastFullFetchOrigin = origin
            val plannedName = if (need.kind == NeedKind.PLANNED_STOP) routeStopName(inputs.navi) else null
            val planned = plannedName?.let { locate(it, inputs.navi) }
            lastPlannedLocation = planned?.location
            // Nearly empty and the stop is out of range (the navi planned it on a fuller battery): nearby stations instead.
            val unreachable = planned != null && need.severity == Severity.URGENT && !Reach.canReach(
                origin, planned.location, rangeKm(inputs.vehicle), inputs.vehicle.socPercent?.toDouble(), need.ratePctPerKm,
            )
            val request = request(inputs, need, origin, planned.takeUnless { unreachable }, nearbyOnly = unreachable)
            if (BuildConfig.DEBUG) Log.d(TAG, "fetch ${request.mode} stop='$plannedName' located=${planned != null} unreachable=$unreachable")
            when (val result = api.recommend(request)) {
                is ApiResult.Failure -> {
                    Log.w(TAG, "recommend failed: ${result.kind} ${result.message}")
                    fullFetchFailed = true
                    // The last cards stay ("마지막으로 받은 정보예요") only when they answer the same question: after a
                    // change of need, route or stop they (and the old stop's verdict) would describe another trip.
                    val samePicture = key == publishedKey
                    _state.update {
                        if (samePicture) {
                            it.copy(loading = false, serverReachable = false, problem = problemText(result))
                        } else {
                            it.copy(
                                loading = false, serverReachable = false, problem = problemText(result), candidates = emptyList(),
                                plannedStop = null, plannedStopName = null, plannedStopRole = null, plannedVerdict = null,
                                window = null, attention = need.severity,
                            )
                        }
                    }
                }
                is ApiResult.Ok -> {
                    publish(inputs, need, request, result.value, plannedName, unreachable)
                    publishedKey = key
                }
            }
        }
    }

    /** New numbers for the shown stations only: the request carries station ids, not the position. */
    private fun fetchStatus(inputs: Inputs, key: String) {
        // A full recommendation on its way brings fresh numbers anyway; cancelling it would also leave `loading` set.
        if (fetchJob?.isActive == true) return
        lastFetchAtMs = clock()
        fetchJob = scope.launch {
            val before = _state.value
            val ids = (listOfNotNull(before.plannedStop?.statId) + before.candidates.map { it.candidate.station.statId })
                .distinct().take(STATUS_MAX_IDS)
            when (val result = api.status(ids, chargePrefs(inputs.prefs, before.need))) {
                is ApiResult.Failure -> {
                    Log.w(TAG, "status failed: ${result.kind} ${result.message}")
                    _state.update { it.copy(serverReachable = false, problem = problemText(result)) }
                }
                is ApiResult.Ok -> {
                    val fresh = result.value.stations.associateBy { it.statId }
                    val planned = before.plannedStop?.let { fresh[it.statId] ?: it }
                    val verdict = when {
                        before.need.kind != NeedKind.PLANNED_STOP -> null
                        // Out of reach is decided on the position, which a status refresh does not send.
                        before.plannedVerdict == PlannedStopVerdict.UNREACHABLE -> PlannedStopVerdict.UNREACHABLE
                        else -> PlannedStopVerdict.of(planned)
                    }
                    val merged = StatusRefresh.merge(before.candidates.map { it.candidate }, fresh)
                    if (BuildConfig.DEBUG) Log.d(TAG, "status ${ids.size} stations, planned ${before.plannedVerdict} -> $verdict")
                    if (StatusRefresh.needsNewRecommendations(before.plannedVerdict, verdict, merged)) {
                        fetch(latest ?: inputs, _state.value.need, key)
                        return@launch
                    }
                    _state.update {
                        it.copy(
                            serverReachable = true,
                            problem = null,
                            candidates = before.candidates.zip(merged) { c, m -> c.copy(candidate = m) },
                            plannedStop = planned,
                            plannedVerdict = verdict,
                            attention = attention(it.need, verdict),
                            updatedAtMs = clock(),
                        )
                    }
                }
            }
        }
    }

    /** Whether new charger counts are enough, or the position has to be sent again for new recommendations. */
    private fun canRefreshStatusOnly(inputs: Inputs): Boolean {
        val s = _state.value
        if (fullFetchFailed || (s.candidates.isEmpty() && s.plannedStop == null)) return false
        if (clock() - lastFullFetchAtMs >= FULL_REFRESH_EVERY_MS) return false
        val here = inputs.navi.location ?: return false
        val then = lastFullFetchOrigin ?: return false
        return Geo.distanceKm(here, then) < REFETCH_AFTER_KM
    }

    private suspend fun publish(
        inputs: Inputs,
        need: ChargeNeed,
        request: RecommendationRequest,
        response: RecommendationResponse,
        plannedName: String?,
        unreachable: Boolean,
    ) {
        val destination = inputs.navi.destination
        val context = RefineContext(
            destination = destination?.location,
            remainTimeS = destination?.remainTimeS,
            avgSpeedKmh = averageSpeed(inputs.navi),
            reserveSocPct = need.reserveSocPct.toDouble(),
            window = response.window,
            prefs = request.prefs,
            urgent = request.urgent,
            socPct = inputs.vehicle.socPercent?.toDouble(),
            ratePctPerKm = need.ratePctPerKm,
        )
        // Refine the best few one at a time (requestTrip answers carry no request id).
        val startedMs = clock()
        val refined = HashMap<String, Boolean>()
        val candidates = response.candidates.mapIndexedNotNull { index, candidate ->
            if (index >= REFINE_COUNT) return@mapIndexedNotNull candidate
            val trip = navi.estimateTrip(WaypointPlanner.target(candidate.station)) ?: return@mapIndexedNotNull candidate
            CandidateRefiner.refine(candidate, trip, context)?.also {
                if (CandidateRefiner.REFINED in it.reasons) refined[it.station.statId] = true
            }
        }
        if (BuildConfig.DEBUG) Log.d(TAG, "${response.candidates.size} candidates, ${refined.size} refined in ${clock() - startedMs} ms")
        val sorted = candidates.sortedWith(StationScorer.CANDIDATE_ORDER)
        // Spreading the cards over 20 km stretches only makes sense along a route; around the car estRoadKm is just
        // the distance, and the server leaves nearby results as they are too (else only 2 cards would show).
        val alongRoute = request.mode != RecommendMode.NEARBY && FALLBACK_NEARBY !in response.notices
        val ranked = (if (alongRoute) StationScorer.diversify(sorted) else sorted).take(SHOWN_COUNT)
        fullFetchFailed = false
        val verdict = when {
            need.kind != NeedKind.PLANNED_STOP -> null
            unreachable -> PlannedStopVerdict.UNREACHABLE
            else -> PlannedStopVerdict.of(response.plannedStop)
        }
        val role = plannedName?.let { PlannedStopResolver.role(it, inputs.navi) }
        _state.update {
            it.copy(
                loading = false,
                serverReachable = true,
                problem = if (ranked.isEmpty()) "조건에 맞는 충전소를 찾지 못했어요" else null,
                candidates = ranked.map { c ->
                    val actions = WaypointPlanner.actions(c.station, inputs.navi, replacesDestination = role == StopRole.DESTINATION)
                    AdvisorCandidate(c, actions, refined[c.station.statId] == true)
                },
                window = response.window,
                plannedStopName = plannedName,
                plannedStopRole = role,
                plannedStop = response.plannedStop,
                plannedVerdict = verdict,
                attention = attention(need, verdict),
                updatedAtMs = clock(),
            )
        }
    }

    /** Where the route's charging stop is; for the navi's own stop this may take a trip request. */
    private suspend fun locate(name: String, navi: NaviState): PlannedStop? {
        val key = routeKey(navi)
        PlannedStopResolver.locate(name, navi, addedStations, key?.let { probedStops[it] })?.let { return it }
        // Only the navi's own stop is in the probe, unless the probe is missing or older.
        if (key == null || PlannedStopResolver.role(name, navi) != StopRole.NAVI) return null
        val probed = probe(navi) ?: return null
        probedStops[key] = probed
        return PlannedStopResolver.locate(name, navi, addedStations, probed)
    }

    private fun request(i: Inputs, need: ChargeNeed, origin: GeoPoint, planned: PlannedStop?, nearbyOnly: Boolean): RecommendationRequest {
        val destination = i.navi.destination?.takeIf { i.navi.guiding }
        val mode = when {
            nearbyOnly -> RecommendMode.NEARBY
            need.kind == NeedKind.PLANNED_STOP && planned != null -> RecommendMode.AROUND_PLANNED_STOP
            need.kind != NeedKind.NEARBY && destination?.location != null -> RecommendMode.ON_ROUTE
            else -> RecommendMode.NEARBY
        }
        return RecommendationRequest(
            mode = mode,
            origin = origin.rounded(),
            headingDeg = i.navi.headingDeg,
            destination = destination?.location?.rounded(),
            remainDistanceM = destination?.remainDistanceM,
            remainTimeS = destination?.remainTimeS,
            // With a charging stop in the route the navi's arrival figure includes that charge.
            naviArrivalSocPct = need.arrivalSocPct.takeIf { need.basis == EstimateBasis.NAVI && routeStopName(i.navi) == null },
            // Rounded like every other point: for a charging station as destination it is the destination itself.
            plannedStop = planned?.let { it.copy(location = it.location.rounded()) },
            socPct = i.vehicle.socPercent?.toDouble(),
            rangeKm = rangeKm(i.vehicle),
            urgent = need.severity == Severity.URGENT,
            prefs = chargePrefs(i.prefs, need),
        )
    }

    private fun chargePrefs(prefs: AdvisorPrefs, need: ChargeNeed) =
        ChargePrefs(minKw = prefs.minKw, reserveSocPct = need.reserveSocPct, maxDetourMin = prefs.maxDetourMin)

    private fun attention(need: ChargeNeed, verdict: PlannedStopVerdict?): Severity = when {
        need.kind == NeedKind.PLANNED_STOP &&
            (verdict == PlannedStopVerdict.BUSY || verdict == PlannedStopVerdict.UNKNOWN || verdict == PlannedStopVerdict.UNREACHABLE) ->
            maxOf(need.severity, Severity.RECOMMENDED)
        else -> need.severity
    }

    private fun averageSpeed(navi: NaviState): Double {
        val d = navi.destination
        val km = d?.remainDistanceM?.div(1000.0)
        val h = d?.remainTimeS?.div(3600.0)
        return if (km != null && h != null && h > 0.0) (km / h).coerceIn(20.0, 110.0) else DEFAULT_SPEED_KMH
    }

    private fun problemText(failure: ApiResult.Failure): String = when (failure.kind) {
        ApiResult.Failure.Kind.NOT_CONFIGURED -> "충전 정보 서버가 설정되지 않았어요"
        ApiResult.Failure.Kind.OFFLINE -> "충전 정보 서버에 연결할 수 없어요"
        ApiResult.Failure.Kind.NOT_READY -> "충전 정보를 불러오는 중이에요. 잠시 후 다시 시도할게요"
        ApiResult.Failure.Kind.SERVER -> "충전 정보 서버 오류 (${failure.message})"
    }

    /** ~100 m: enough for station search, less precise than the car's position. */
    private fun GeoPoint.rounded() = GeoPoint(round3(lat), round3(lon))

    private fun round3(v: Double) = Math.round(v * 1000) / 1000.0

    private companion object {
        const val TAG = "ChargingAdvisor"
        const val TICK_MS = 30_000L
        const val REFRESH_EVERY_MS = 3 * 60_000L
        const val FULL_REFRESH_EVERY_MS = 15 * 60_000L
        const val REFETCH_AFTER_KM = 10.0
        const val STATUS_MAX_IDS = 20
        const val RETRY_AFTER_FAILURE_MS = 60_000L
        const val ROUTE_SETTLE_MS = 15_000L
        const val INPUT_SETTLE_MS = 1_000L
        const val NAVI_WAIT_MS = 5_000L
        const val LOW_BATTERY_WARNING_MS = 10 * 60_000L
        const val REFINE_COUNT = 3
        const val SHOWN_COUNT = 3
        const val DEFAULT_SPEED_KMH = 80.0
        const val REACH_RECHECK_MIN_MS = 30_000L
        const val FALLBACK_NEARBY = "FALLBACK_NEARBY" // RecommendationResponse.notices
    }
}
