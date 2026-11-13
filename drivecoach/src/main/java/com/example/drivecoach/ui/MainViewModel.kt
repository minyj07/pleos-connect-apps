package com.example.drivecoach.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.drivecoach.DriveCoachApp
import com.example.drivecoach.analysis.PeriodSummary
import com.example.drivecoach.data.Settings
import com.example.drivecoach.model.TripRecord
import com.example.drivecoach.model.TripSummary
import com.example.drivecoach.parking.ParkingPlan
import com.example.drivecoach.parking.ParkingPlanner
import com.example.drivecoach.parking.ParkingType
import com.example.drivecoach.parking.ScenarioSpec
import com.example.drivecoach.parking.VehicleGeometry
import com.example.drivecoach.record.RecorderController
import com.example.drivecoach.record.RecorderState
import com.example.drivecoach.share.ShareTarget
import com.example.drivecoach.share.ShareUi
import com.example.drivecoach.vehicle.DemoControls
import com.example.drivecoach.vehicle.DrivingState
import com.example.drivecoach.vehicle.LiveVehicleState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as DriveCoachApp

    val settings: StateFlow<Settings.Values> = app.settings.values
    val trips: StateFlow<List<TripSummary>> = app.tripStore.summaries
    val live: StateFlow<LiveVehicleState> = app.vehicle.source.state
    val recorder: StateFlow<RecorderState> = app.recorderState
    val demo: DemoControls? = app.vehicle.demo

    val restricted: StateFlow<Boolean> = combine(app.ux.isDriving, app.ux.available, live) { driving, available, v ->
        DrivingState.restricted(driving, available, v)
    }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        // A QR must not stay up while driving (UX_RESTRICTIONS_NO_SETUP_QR_SCAN); the link itself stays valid.
        viewModelScope.launch { restricted.collect { if (it) app.shares.close() } }
    }

    // ---- reports ----

    val week: StateFlow<PeriodSummary> = trips.map { PeriodSummary.of(it, System.currentTimeMillis(), 7) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PeriodSummary.of(emptyList(), 0L, 7))

    private val _periodDays = MutableStateFlow(30)
    val periodDays: StateFlow<Int> = _periodDays.asStateFlow()
    val period: StateFlow<PeriodSummary> = combine(trips, _periodDays) { t, d -> PeriodSummary.of(t, System.currentTimeMillis(), d) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PeriodSummary.of(emptyList(), 0L, 30))

    /** Kilometres the odometer advanced between recorded trips over the last 7 days (coverage gaps). */
    val untrackedKm: StateFlow<Float> = trips.map { list ->
        val from = System.currentTimeMillis() - PeriodSummary.WEEK_MS
        list.filter { it.startEpochMs >= from }.sortedBy { it.startEpochMs }.zipWithNext { a, b ->
            val end = a.odoEndKm
            val start = b.odoStartKm
            if (end != null && start != null && start - end > MIN_GAP_KM) start - end else 0f
        }.sum()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0f)

    private val _openTrip = MutableStateFlow<TripRecord?>(null)
    val openTrip: StateFlow<TripRecord?> = _openTrip.asStateFlow()

    fun setPeriodDays(days: Int) {
        _periodDays.value = days
    }

    fun loadTrip(id: String) {
        _openTrip.value = null
        app.io.execute { _openTrip.value = runCatching { app.tripStore.record(id) }.getOrNull() }
    }

    fun deleteTrip(id: String) {
        app.io.execute { runCatching { app.tripStore.delete(id) } }
        app.appScope.launch { app.shares.delete(ShareTarget.Trip(id)) }
    }

    // ---- 폰으로 보기 ----

    val share: StateFlow<ShareUi> = app.shares.ui
    val shareAvailable: Boolean = app.shares.available
    val activeShares: StateFlow<Int> = app.shares.index.map { it.active.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun shareTrip(id: String) = requestShare(ShareTarget.Trip(id))

    fun sharePeriod(days: Int) = requestShare(ShareTarget.Period(days))

    private fun requestShare(target: ShareTarget) {
        if (settings.value.shareConsented) startShare(target) else app.shares.askConsent(target)
    }

    /** The separate opt-in for uploading copies; only the button on the notice counts. */
    fun acceptShareConsent() {
        app.settings.update { copy(shareConsentVersion = Settings.SHARE_CONSENT_VERSION, shareConsentAtEpochMs = System.currentTimeMillis()) }
        (share.value as? ShareUi.NeedsConsent)?.let { startShare(it.target) }
    }

    fun retryShare() {
        (share.value as? ShareUi.Failed)?.let { startShare(it.target) }
    }

    fun closeShare() = app.shares.close()

    /** "링크 지우기" on the share page. */
    fun deleteShownLink() {
        val target = (share.value as? ShareUi.Ready)?.target ?: return
        app.appScope.launch { app.shares.delete(target) }
    }

    fun deleteAllShares() {
        app.appScope.launch { app.shares.deleteAll() }
    }

    fun withdrawShareConsent() {
        app.settings.update { copy(shareConsentVersion = 0, shareConsentAtEpochMs = 0L) }
        deleteAllShares()
    }

    private fun startShare(target: ShareTarget) {
        app.appScope.launch { app.shares.share(target) }
    }

    // ---- parking ----

    private val _parkingSpec = MutableStateFlow(ScenarioSpec.defaults(ParkingType.REVERSE_PERPENDICULAR))
    val parkingSpec: StateFlow<ScenarioSpec> = _parkingSpec.asStateFlow()

    val geometry: StateFlow<VehicleGeometry> = combine(settings, live.map { it.dimensionsMm }.distinctUntilChanged()) { s, dims ->
        app.settings.geometry(s, dims)
    }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.Eagerly, app.settings.geometry(settings.value, null))

    private val planCache = ConcurrentHashMap<Pair<ScenarioSpec, VehicleGeometry>, ParkingPlan>()

    val plan: StateFlow<ParkingPlan?> = combine(_parkingSpec, geometry) { spec, g -> spec to g }
        .mapLatest { key -> planCache.getOrPut(key) { ParkingPlanner.plan(key.first, key.second) } }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setParkingSpec(spec: ScenarioSpec) {
        _parkingSpec.value = spec
    }

    // ---- consent & settings ----

    fun acceptConsent() = app.settings.update {
        copy(consentVersion = Settings.CONSENT_VERSION, consentAtEpochMs = System.currentTimeMillis(), consentDeclined = false)
    }

    fun declineConsent() = app.settings.update { copy(consentDeclined = true) }

    /**
     * Withdrawal: stop recording at once and delete every stored trip and the key (store privacy rule), and
     * every phone link. Links have their own store and key, so their deletes survive the trip wipe until sent.
     */
    fun withdrawConsent() {
        app.settings.update { copy(consentVersion = 0, consentAtEpochMs = 0L, consentDeclined = true, shareConsentVersion = 0, shareConsentAtEpochMs = 0L) }
        RecorderController.stop(app)
        deleteAllShares()
        app.io.execute { runCatching { app.tripStore.wipe() } }
        _openTrip.value = null
    }

    fun setRecording(on: Boolean) {
        app.settings.update { copy(recording = on) }
        RecorderController.sync(app, app.settings.values.value)
    }

    fun setAutoStart(on: Boolean) = app.settings.update { copy(autoStartOnBoot = on) }

    /** 설정 > 화면 > 다크 모드: the whole app follows it at once (see DriveCoachTheme). */
    fun setDarkMode(on: Boolean) = app.settings.update { copy(darkMode = on) }

    fun setRetention(days: Int) {
        app.settings.update { copy(retentionDays = days) }
        app.io.execute { runCatching { app.tripStore.prune(System.currentTimeMillis(), days) } }
    }

    fun setVehicleProfile(profile: String) = app.settings.update { copy(vehicleProfile = profile) }

    fun setCustomGeometry(g: VehicleGeometry) = app.settings.update { copy(vehicleProfile = Settings.PROFILE_CUSTOM, customGeometry = g) }

    /** Steering calibration: the user holds full lock in P, we store that raw value. */
    fun calibrateSteering(rawAtFullLock: Float?) = app.settings.update { copy(steeringFullLockRaw = rawAtFullLock?.let { kotlin.math.abs(it) }) }

    fun playDemo(script: String, speedup: Int) {
        viewModelScope.launch { demo?.play(script, speedup) }
    }

    fun stopDemo() {
        demo?.stop()
    }

    private companion object {
        const val MIN_GAP_KM = 0.5f
    }
}
