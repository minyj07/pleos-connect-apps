package com.example.pleosev.server.sync

import com.example.pleosev.server.ServerConfig
import com.example.pleosev.server.source.KecoApiException
import com.example.pleosev.server.store.Snapshot
import com.example.pleosev.server.store.SnapshotStore
import com.example.pleosev.server.store.StationStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Duration
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.coroutines.cancellation.CancellationException

/**
 * Runs the sync jobs: snapshot restore at start-up, a full sync when the data is old, the status
 * poll every [ServerConfig.pollIntervalSec], the daily full sync at [ServerConfig.masterSyncHourKst]
 * KST and a snapshot every 10 minutes. Master sync and poll never overlap (one mutex); every full sync is
 * followed at once by a catch-up poll (see [MasterSyncJob.run]).
 */
class SyncScheduler(
    private val master: MasterSyncJob,
    private val poller: StatusPollJob,
    private val store: StationStore,
    private val coverage: CoverageTracker,
    private val budget: ApiBudget,
    private val snapshots: SnapshotStore?,
    private val clock: Clock,
    private val config: ServerConfig,
) {
    private val mutex = Mutex()
    private val log = LoggerFactory.getLogger(SyncScheduler::class.java)

    /** A failed full sync is not retried before this time (each attempt can cost ~75 calls). */
    @Volatile
    private var nextMasterAttemptAtMs = 0L

    @Volatile
    var lastError: String? = null
        private set

    fun start(scope: CoroutineScope): Job = scope.launch(Dispatchers.IO) {
        snapshots?.load()?.let(::restore)
        val masterAge = store.masterSyncedAtMs?.let { clock.millis() - it }
        // A recent snapshot is resumed with a poll, which closes the gap (or asks for a full sync when the gap is
        // longer than the status window): a restart no longer costs a whole getChargerInfo download.
        if (store.index == null || masterAge == null || masterAge > MASTER_MAX_AGE_MS) runMaster("start-up") else pollOnce()
        launch { pollLoop() }
        launch { dailyMasterLoop() }
        launch { snapshotLoop() }
    }

    /** Stations and statuses from a snapshot; the status chain resumes from its last poll when it had one. */
    fun restore(snapshot: Snapshot) {
        store.restore(snapshot)
        snapshot.lastPollAtMs?.let(coverage::baseline)
        log.info("restored snapshot: {} DC stations, status chain {}", snapshot.stations.size, if (snapshot.lastPollAtMs != null) "resumed" else "broken")
    }

    suspend fun runMaster(reason: String) = mutex.withLock { masterLocked(reason) }

    suspend fun pollOnce() = mutex.withLock {
        if (budget.exhausted()) {
            lastError = "daily data.go.kr budget used up"
            return@withLock
        }
        when (pollLocked()) {
            StatusPollJob.Result.NeedsFullSync -> masterLocked("status gap")
            // No stations yet: the start-up full sync failed. Retried here (after its backoff), not only at 04:00.
            StatusPollJob.Result.NotReady -> masterLocked("no stations yet")
            else -> Unit
        }
    }

    /** One status poll while holding the mutex; null when it failed. */
    private suspend fun pollLocked(): StatusPollJob.Result? = try {
        poller.pollOnce().also { result ->
            if (result is StatusPollJob.Result.Polled) {
                log.debug("status poll: period {} min, {} changes, {} applied", result.periodMin, result.changes, result.applied)
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        failed("status poll", e)
        null
    }

    private suspend fun masterLocked(reason: String) {
        val now = clock.millis()
        if (now < nextMasterAttemptAtMs || budget.exhausted()) return
        val r = try {
            master.run()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            nextMasterAttemptAtMs = now + MASTER_RETRY_BACKOFF_MS
            failed("full sync ($reason)", e)
            return
        }
        lastError = null
        val gap = if (r.uncoveredMs > 0) ", first ${r.uncoveredMs / 1_000} s not re-checked" else ""
        log.info("full sync ({}): {} DC stations, {} chargers, {} calls, {} ms{}", reason, r.stations, r.chargers, r.calls, r.tookMs, gap)
        // Catch up on what changed while the sync was downloading; a gap answer here is not chased with another sync.
        pollLocked()
        saveSnapshot()
    }

    private fun failed(what: String, e: Exception) {
        if (e is KecoApiException && e.isQuotaExceeded) budget.markExhausted()
        lastError = "$what failed: ${e.message}"
        log.warn("{} failed: {}", what, e.message)
    }

    private suspend fun pollLoop() {
        while (currentCoroutineContext().isActive) {
            delay(config.pollIntervalSec * 1_000L * budget.pollIntervalMultiplier())
            pollOnce()
        }
    }

    private suspend fun dailyMasterLoop() {
        while (currentCoroutineContext().isActive) {
            delay(millisUntilNextKst(config.masterSyncHourKst))
            runMaster("daily")
        }
    }

    private suspend fun snapshotLoop() {
        val store = snapshots ?: return
        while (currentCoroutineContext().isActive) {
            delay(SNAPSHOT_EVERY_MS)
            saveSnapshot(store)
        }
    }

    fun saveSnapshot(target: SnapshotStore? = snapshots) {
        if (target == null || store.index == null) return
        try {
            target.save(store.toSnapshot(clock.millis(), lastPollAtMs = coverage.lastSuccessAtMs?.takeIf { coverage.coverageOk }))
        } catch (e: Exception) {
            log.warn("snapshot save failed: {}", e.message)
        }
    }

    private fun millisUntilNextKst(hour: Int): Long {
        val now = ZonedDateTime.now(clock.withZone(KST))
        var next = now.with(LocalTime.of(hour, 0))
        if (!next.isAfter(now)) next = next.plusDays(1)
        return Duration.between(now, next).toMillis()
    }

    private companion object {
        val KST: ZoneId = ZoneId.of("Asia/Seoul")
        const val MASTER_MAX_AGE_MS = 24 * 60 * 60_000L // the station list itself is refreshed daily
        const val MASTER_RETRY_BACKOFF_MS = 15 * 60_000L
        const val SNAPSHOT_EVERY_MS = 10 * 60_000L
    }
}
