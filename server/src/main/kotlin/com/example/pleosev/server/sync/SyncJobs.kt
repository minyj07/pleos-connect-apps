package com.example.pleosev.server.sync

import com.example.pleosev.server.ServerConfig
import com.example.pleosev.server.source.ChargerSource
import com.example.pleosev.server.store.KecoNormalizer
import com.example.pleosev.server.store.StationStore
import java.time.Clock

/** Upper bound on pages per query, in case a source keeps returning rows. */
private const val MAX_PAGES = 200

/**
 * Full download of getChargerInfo for every 시도: rebuilds the DC station index and every charger's
 * status. Refuses to replace a healthy index with a much smaller one (a partial outage would
 * otherwise wipe stations).
 */
class MasterSyncJob(
    private val source: ChargerSource,
    private val store: StationStore,
    private val coverage: CoverageTracker,
    private val clock: Clock,
    private val config: ServerConfig,
) {
    /** [uncoveredMs]: how much of the start of a long sync the catch-up poll cannot re-check (see [run]). */
    data class Result(val stations: Int, val chargers: Int, val calls: Int, val tookMs: Long, val uncoveredMs: Long = 0)

    suspend fun run(): Result {
        val startedAt = clock.millis()
        val rows = ArrayList<KecoNormalizer.InfoRow>()
        var calls = 0
        for (zcode in config.zcodes) {
            var pageNo = 1
            var fetched = 0
            while (pageNo <= MAX_PAGES) {
                val page = source.chargerInfo(zcode, pageNo, config.pageSize)
                calls++
                page.items.mapNotNullTo(rows) { KecoNormalizer.infoRow(it) }
                fetched += page.items.size
                if (page.items.isEmpty() || fetched >= page.totalCount) break
                pageNo++
            }
        }
        val (stations, grouped) = KecoNormalizer.group(rows)
        val previous = store.index?.stations?.size ?: 0
        check(previous == 0 || stations.size >= previous * MIN_KEEP_RATIO) {
            "master sync returned ${stations.size} DC stations (previously $previous); keeping the old index"
        }
        val fetchedAt = clock.millis()
        val statuses = grouped.mapValues { (_, status) -> status.notAfter(fetchedAt) }
        store.replaceMaster(stations, statuses, startedAt)
        // Each row's status is as of its own page, and status deltas reach back at most 10 minutes. The real
        // full sync takes longer than that (about 11 minutes on 2026-11-04), so chaining from its start would
        // make the next poll ask for another full sync, forever. The chain starts instead at the oldest point
        // the catch-up poll right after this (SyncScheduler) still covers; changes during the first minutes of
        // such a sync are not re-checked and show again with the charger's next status change.
        val endedAt = clock.millis()
        val chainFrom = maxOf(startedAt, endedAt - CoverageTracker.CATCH_UP_WINDOW_MS)
        coverage.baseline(chainFrom)
        // The snapshot is saved by SyncScheduler after the catch-up poll: a failed save is not a failed sync.
        return Result(stations.size, statuses.size, calls, endedAt - startedAt, chainFrom - startedAt)
    }

    private companion object {
        const val MIN_KEEP_RATIO = 0.9
    }
}

/** One getChargerStatus round: applies every changed charger and keeps the coverage chain. */
class StatusPollJob(
    private val source: ChargerSource,
    private val store: StationStore,
    private val coverage: CoverageTracker,
    private val clock: Clock,
    private val config: ServerConfig,
) {
    sealed interface Result {
        data class Polled(val periodMin: Int, val changes: Int, val applied: Int, val calls: Int) : Result
        data object NeedsFullSync : Result
        data object NotReady : Result
    }

    suspend fun pollOnce(): Result {
        if (store.index == null) return Result.NotReady
        val startedAt = clock.millis()
        val period = coverage.periodFor(startedAt, config.statusPeriodMin)
        if (period == null) {
            coverage.markBroken()
            return Result.NeedsFullSync
        }
        var changes = 0
        var applied = 0
        var calls = 0
        val zcodes: List<String?> = if (config.statusPerZcode) config.zcodes else listOf(null)
        for (zcode in zcodes) {
            var pageNo = 1
            var fetched = 0
            while (pageNo <= MAX_PAGES) {
                val page = source.chargerStatus(period, pageNo, config.pageSize, zcode)
                calls++
                val fetchedAt = clock.millis()
                for (item in page.items) {
                    val (key, status) = KecoNormalizer.statusRow(item) ?: continue
                    changes++
                    if (store.apply(key, status.notAfter(fetchedAt)) == StationStore.ApplyResult.APPLIED) applied++
                }
                fetched += page.items.size
                if (page.items.isEmpty() || fetched >= page.totalCount) break
                pageNo++
            }
        }
        coverage.recordPoll(startedAt, period)
        return Result.Polled(period, changes, applied, calls)
    }
}
