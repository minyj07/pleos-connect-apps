package com.example.pleosev.server

import com.example.pleosev.server.service.MatchService
import com.example.pleosev.server.service.RecommendationService
import com.example.pleosev.server.service.StationQueries
import com.example.pleosev.server.service.StationViews
import com.example.pleosev.server.source.ChargerSource
import com.example.pleosev.server.source.FixtureSource
import com.example.pleosev.server.source.KecoHttpSource
import com.example.pleosev.server.store.SnapshotStore
import com.example.pleosev.server.store.StationStore
import com.example.pleosev.server.sync.ApiBudget
import com.example.pleosev.server.sync.CoverageTracker
import com.example.pleosev.server.sync.MasterSyncJob
import com.example.pleosev.server.sync.StatusPollJob
import com.example.pleosev.server.sync.SyncScheduler
import com.example.pleosev.shared.api.HealthResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import java.time.Clock

/** Wires the server together. Tests build one with a FixtureSource and a fixed clock. */
class AdvisorRuntime(
    val config: ServerConfig,
    val clock: Clock = Clock.systemUTC(),
    source: ChargerSource? = null,
    persistSnapshots: Boolean = true,
) : AutoCloseable {

    val budget = ApiBudget(config.dailyCallBudget, clock)
    val source: ChargerSource = source
        ?: if (config.fakeSource) FixtureSource.fromResources(clock) else KecoHttpSource(requireNotNull(config.serviceKey), budget)
    val fixture: FixtureSource? get() = source as? FixtureSource

    val store = StationStore()
    val coverage = CoverageTracker()
    // Synthetic data starts clean: a FAKE snapshot would carry /debug/status-override changes into the next run.
    private val snapshots = if (persistSnapshots && !config.fakeSource) SnapshotStore(config.dataDir) else null

    val views = StationViews(store, coverage, clock)
    val matcher = MatchService(store, views, clock)
    val recommendations = RecommendationService(store, views, matcher, coverage, clock)
    val queries = StationQueries(store, views, clock)
    val scheduler = SyncScheduler(
        master = MasterSyncJob(this.source, store, coverage, clock, config),
        poller = StatusPollJob(this.source, store, coverage, clock, config),
        store = store,
        coverage = coverage,
        budget = budget,
        snapshots = snapshots,
        clock = clock,
        config = config,
    )

    fun start(scope: CoroutineScope): Job = scheduler.start(scope)

    /** Full sync followed by one status poll, without the background loops (tests, demos). */
    suspend fun syncNow() {
        scheduler.runMaster("manual")
        scheduler.pollOnce()
    }

    fun health(): HealthResponse {
        val index = store.index
        return HealthResponse(
            status = when {
                index == null -> "starting"
                !coverage.coverageOk -> "degraded"
                else -> "ok"
            },
            source = source.name,
            dcStations = index?.stations?.size ?: 0,
            dcChargers = index?.chargerCount ?: 0,
            masterSyncedAtMs = store.masterSyncedAtMs,
            lastStatusPollAtMs = coverage.lastSuccessAtMs,
            coverageOkSinceMs = coverage.coverageOkSinceMs,
            apiCallsToday = budget.usedToday(),
            apiDailyBudget = budget.dailyLimit,
        )
    }

    override fun close() {
        scheduler.saveSnapshot()
        (source as? AutoCloseable)?.close()
    }
}
