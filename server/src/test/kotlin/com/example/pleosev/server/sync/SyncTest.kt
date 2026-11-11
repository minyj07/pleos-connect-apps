package com.example.pleosev.server.sync

import com.example.pleosev.server.Fixture
import com.example.pleosev.server.MutableClock
import com.example.pleosev.server.ServerConfig
import com.example.pleosev.server.TEST_NOW
import com.example.pleosev.server.source.ChargerSource
import com.example.pleosev.server.source.FixtureSource
import com.example.pleosev.server.source.KecoPage
import com.example.pleosev.server.store.ChargerKey
import com.example.pleosev.server.store.StationStore
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SyncTest {

    private val t0 = TEST_NOW.toEpochMilli()
    private val minute = 60_000L

    @Test
    fun coveragePicksThePeriodThatClosesTheGap() {
        val coverage = CoverageTracker()
        assertNull(coverage.periodFor(t0, 5)) // no baseline yet
        coverage.baseline(t0)
        assertEquals(5, coverage.periodFor(t0 + 90_000L, 5))
        assertEquals(10, coverage.periodFor(t0 + 6 * minute, 5))
        assertNull(coverage.periodFor(t0 + 11 * minute, 5))
    }

    @Test
    fun aPollThatDoesNotReachBackBreaksCoverage() {
        val coverage = CoverageTracker()
        coverage.baseline(t0)
        coverage.recordPoll(t0 + 90_000L, 5)
        assertTrue(coverage.coverageOk)
        coverage.recordPoll(t0 + 90_000L + 7 * minute, 5) // a 5-minute window cannot cover a 7-minute gap
        assertFalse(coverage.coverageOk)
        coverage.baseline(t0 + 20 * minute)
        assertTrue(coverage.coverageOk)
    }

    @Test
    fun budgetResetsAtKstMidnightAndSlowsDownNearTheLimit() {
        val clock = MutableClock(java.time.Instant.parse("2026-11-02T14:30:00Z").toEpochMilli()) // 23:30 KST
        val budget = ApiBudget(100, clock)
        repeat(96) { budget.record() }
        assertEquals(2L, budget.pollIntervalMultiplier())
        clock.advanceMinutes(40) // 00:10 KST next day
        assertEquals(0, budget.usedToday())
        assertEquals(1L, budget.pollIntervalMultiplier())
        budget.markExhausted()
        assertTrue(budget.exhausted())
    }

    @Test
    fun masterSyncIndexesDcStationsAndPollAppliesChanges() = runBlocking {
        val clock = MutableClock()
        val source = FixtureSource.fromResources(clock)
        val store = StationStore()
        val coverage = CoverageTracker()
        val config = ServerConfig(fakeSource = true)
        val result = MasterSyncJob(source, store, coverage, clock, config).run()
        assertEquals(Fixture.DC_STATIONS, result.stations)
        assertEquals(Fixture.DC_CHARGERS, result.chargers)
        val index = store.index!!
        assertTrue(Fixture.AC_ONLY !in index.byId && Fixture.DELETED !in index.byId)

        clock.advanceMinutes(1)
        assertEquals(8, source.override(Fixture.GOESAN_WATER, null, "3"))
        val poll = StatusPollJob(source, store, coverage, clock, config).pollOnce()
        assertIs<StatusPollJob.Result.Polled>(poll)
        assertEquals(8, poll.applied)
        assertEquals("3", store.status(ChargerKey(Fixture.GOESAN_WATER, "01"))?.stat)
        assertTrue(coverage.coverageOk)
    }

    @Test
    fun aMuchSmallerMasterResultDoesNotReplaceTheIndex() = runBlocking {
        val clock = MutableClock()
        val store = StationStore()
        val coverage = CoverageTracker()
        val config = ServerConfig(fakeSource = true)
        MasterSyncJob(FixtureSource.fromResources(clock), store, coverage, clock, config).run()

        val broken = object : ChargerSource {
            override val name = "broken"
            override suspend fun chargerInfo(zcode: String, pageNo: Int, numOfRows: Int) = KecoPage(0, 1, numOfRows, emptyList())
            override suspend fun chargerStatus(periodMin: Int, pageNo: Int, numOfRows: Int, zcode: String?) = KecoPage(0, 1, numOfRows, emptyList())
        }
        assertFailsWith<IllegalStateException> { MasterSyncJob(broken, store, coverage, clock, config).run() }
        assertEquals(Fixture.DC_STATIONS, store.index?.stations?.size)
    }

    /** getChargerInfo pages that take a minute each: 17 시도 make a 17-minute full sync, longer than any status window. */
    private class SlowInfoPages(private val inner: ChargerSource, private val clock: MutableClock) : ChargerSource by inner {
        override suspend fun chargerInfo(zcode: String, pageNo: Int, numOfRows: Int): KecoPage {
            clock.advanceMinutes(1)
            return inner.chargerInfo(zcode, pageNo, numOfRows)
        }
    }

    @Test
    fun aFullSyncLongerThanTheStatusWindowIsCaughtUpInsteadOfRepeated() = runBlocking {
        val clock = MutableClock()
        val source = SlowInfoPages(FixtureSource.fromResources(clock), clock)
        val store = StationStore()
        val coverage = CoverageTracker()
        val config = ServerConfig(fakeSource = true)
        val poller = StatusPollJob(source, store, coverage, clock, config)
        val scheduler = SyncScheduler(
            MasterSyncJob(source, store, coverage, clock, config), poller, store, coverage, ApiBudget(10_000, clock), null, clock, config,
        )

        scheduler.runMaster("test")
        assertEquals(clock.millis(), coverage.lastSuccessAtMs) // the catch-up poll ran right after the sync
        assertTrue(coverage.coverageOk)
        clock.nowMs += config.pollIntervalSec * 1_000L
        val next = poller.pollOnce()
        assertIs<StatusPollJob.Result.Polled>(next) // not NeedsFullSync: no endless full syncs
        assertEquals(config.statusPeriodMin, next.periodMin)
        assertTrue(coverage.coverageOk)
    }

    @Test
    fun aFailedFirstFullSyncIsRetriedAfterTheBackoffNotTheNextMorning() = runBlocking {
        val clock = MutableClock()
        val good = FixtureSource.fromResources(clock)
        var failing = true
        val flaky = object : ChargerSource by good {
            override suspend fun chargerInfo(zcode: String, pageNo: Int, numOfRows: Int): KecoPage {
                if (failing) throw java.io.IOException("gateway timeout")
                return good.chargerInfo(zcode, pageNo, numOfRows)
            }
        }
        val store = StationStore()
        val coverage = CoverageTracker()
        val config = ServerConfig(fakeSource = true)
        val scheduler = SyncScheduler(
            MasterSyncJob(flaky, store, coverage, clock, config), StatusPollJob(flaky, store, coverage, clock, config),
            store, coverage, ApiBudget(10_000, clock), null, clock, config,
        )
        scheduler.runMaster("start-up")
        assertNull(store.index)
        failing = false
        clock.advanceMinutes(5)
        scheduler.pollOnce()
        assertNull(store.index) // still in the 15-minute backoff
        clock.advanceMinutes(11)
        scheduler.pollOnce()
        assertEquals(Fixture.DC_STATIONS, store.index?.stations?.size)
    }

    @Test
    fun aRestartFromARecentSnapshotCatchesUpWithAPollInsteadOfAFullSync() = runBlocking {
        val clock = MutableClock()
        val source = FixtureSource.fromResources(clock)
        val config = ServerConfig(fakeSource = true)
        val store = StationStore()
        val coverage = CoverageTracker()
        MasterSyncJob(source, store, coverage, clock, config).run()
        StatusPollJob(source, store, coverage, clock, config).pollOnce()
        val snapshot = store.toSnapshot(clock.millis(), lastPollAtMs = coverage.lastSuccessAtMs)

        clock.advanceMinutes(3) // the restart
        val store2 = StationStore()
        val coverage2 = CoverageTracker()
        val scheduler = SyncScheduler(
            MasterSyncJob(source, store2, coverage2, clock, config), StatusPollJob(source, store2, coverage2, clock, config),
            store2, coverage2, ApiBudget(10_000, clock), null, clock, config,
        )
        scheduler.restore(snapshot)
        assertTrue(coverage2.coverageOk)
        scheduler.pollOnce()
        assertEquals(clock.millis(), coverage2.lastSuccessAtMs) // polled
        assertEquals(store.masterSyncedAtMs, store2.masterSyncedAtMs) // and the station list was not downloaded again
        assertTrue(coverage2.coverageOk)
    }

    @Test
    fun aStatusStampedInTheFutureDoesNotBlockTheNextOnes() {
        val store = StationStore()
        val key = ChargerKey("ME1", "01")
        val (stations, statuses) = com.example.pleosev.server.store.KecoNormalizer.group(emptyList())
        store.replaceMaster(stations, statuses, 0L)
        val now = t0
        val future = com.example.pleosev.server.store.ChargerStatus("2", now + 24 * 60 * minute)
        assertEquals(now, future.notAfter(now).statUpdAtMs)
        assertEquals(now - minute, future.copy(statUpdAtMs = now - minute).notAfter(now).statUpdAtMs)
        assertNull(store.status(key))
    }

    @Test
    fun aLongPollingGapAsksForAFullSync() = runBlocking {
        val clock = MutableClock()
        val source = FixtureSource.fromResources(clock)
        val store = StationStore()
        val coverage = CoverageTracker()
        val config = ServerConfig(fakeSource = true)
        MasterSyncJob(source, store, coverage, clock, config).run()
        clock.advanceMinutes(15)
        assertEquals(StatusPollJob.Result.NeedsFullSync, StatusPollJob(source, store, coverage, clock, config).pollOnce())
        assertFalse(coverage.coverageOk)
    }
}
