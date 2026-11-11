package com.example.pleosev.server

import com.example.pleosev.server.source.FixtureSource
import kotlinx.coroutines.runBlocking
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** 2026-11-02 12:00 KST. */
val TEST_NOW: Instant = Instant.parse("2026-11-02T03:00:00Z")

/** A clock tests can move forward; zone views share the same instant. */
class MutableClock(var nowMs: Long = TEST_NOW.toEpochMilli()) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId): Clock = ZonedView(this, zone)
    override fun instant(): Instant = Instant.ofEpochMilli(nowMs)
    override fun millis(): Long = nowMs

    fun advanceMinutes(minutes: Long) {
        nowMs += minutes * 60_000L
    }

    private class ZonedView(private val base: MutableClock, private val zone: ZoneId) : Clock() {
        override fun getZone(): ZoneId = zone
        override fun withZone(zone: ZoneId): Clock = ZonedView(base, zone)
        override fun instant(): Instant = base.instant()
    }
}

/** Runtime on the bundled synthetic stations (resources/fake/stations.json), without background loops or files. */
fun fakeRuntime(clock: Clock = MutableClock()): AdvisorRuntime =
    AdvisorRuntime(ServerConfig(fakeSource = true), clock, source = FixtureSource.fromResources(clock), persistSnapshots = false)

fun syncedFakeRuntime(clock: Clock = MutableClock()): AdvisorRuntime = fakeRuntime(clock).also { runBlocking { it.syncNow() } }

/** Facts about resources/fake/stations.json the tests rely on. */
object Fixture {
    const val DC_STATIONS = 22 // 24 entries minus an AC-only station and a deleted one
    const val DC_CHARGERS = 75
    const val GOESAN_WATER = "WTFK0201"         // the navi's AUTO stop in the spike, 8 x 350 kW
    const val GOESAN_ME_SAME_SITE = "MEFK0202"  // same service area and direction, other operator
    const val GOESAN_OTHER_DIRECTION = "MEFK0203"
    const val MUNGYEONG = "HDFK0204"
    const val OUT_OF_SERVICE = "MEFK0104"
    const val OUT_OF_SERVICE_PANGYO = "MEFK0004"
    const val RESTRICTED = "PIFK0005"
    const val AC_ONLY = "MEFK0901"
    const val DELETED = "MEFK0902"
    const val BUSAN_STATION_PARKING = "MEFK0401"
    val PANGYO_NEAR = setOf("EVFK0002", "HDFK0003", "MEFK0001")
}
