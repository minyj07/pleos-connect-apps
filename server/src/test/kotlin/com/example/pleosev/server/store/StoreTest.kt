package com.example.pleosev.server.store

import com.example.pleosev.server.TEST_NOW
import com.example.pleosev.shared.api.Connector
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class StoreTest {

    private fun item(statId: String, chgerId: String, type: String = "04", stat: String = "2", upd: String = "20261102110000") = mapOf(
        "statId" to statId, "chgerId" to chgerId, "statNm" to "테스트 $statId", "addr" to "주소", "lat" to "37.4", "lng" to "127.1",
        "busiId" to "ME", "busiNm" to "환경부", "chgerType" to type, "output" to "100.0", "stat" to stat, "statUpdDt" to upd,
        "zcode" to "41", "limitYn" to "N", "parkingFree" to "Y",
    )

    @Test
    fun normalizerKeepsDcRowsWithLocationsOnly() {
        val row = assertNotNull(KecoNormalizer.infoRow(item("ME1", "01")))
        assertEquals(setOf(Connector.DC_COMBO), row.charger.connectors)
        assertEquals(100, row.charger.outputKw)
        assertEquals(true, row.station.parkingFree)
        assertEquals(TEST_NOW.toEpochMilli() - 3_600_000L, row.status.statUpdAtMs) // 11:00 KST

        assertNull(KecoNormalizer.infoRow(item("ME1", "02", type = "02")))       // AC 완속
        assertNull(KecoNormalizer.infoRow(item("ME1", "03", type = "99")))       // unknown type
        assertNull(KecoNormalizer.infoRow(item("ME1", "04") + ("delYn" to "Y")))
        assertNull(KecoNormalizer.infoRow(item("ME1", "05") + ("lat" to "")))
        assertNull(KecoNormalizer.infoRow(item("ME1", "06") + ("lat" to "1.5")))   // outside Korea
        val noOutput = assertNotNull(KecoNormalizer.infoRow(item("ME1", "07") + ("output" to "")))
        assertNull(noOutput.charger.outputKw) // StationViews then assumes 50 kW for DC
    }

    @Test
    fun kstTimestampsRoundTrip() {
        assertEquals(TEST_NOW.toEpochMilli(), KecoNormalizer.parseKst("20261102120000"))
        assertEquals("20261102120000", KecoNormalizer.formatKst(TEST_NOW.toEpochMilli()))
        assertNull(KecoNormalizer.parseKst(""))
        assertNull(KecoNormalizer.parseKst("2026-11-02"))
    }

    @Test
    fun groupBuildsStationsAndDeduplicatesChargers() {
        val rows = listOf(item("ME1", "02"), item("ME1", "01"), item("ME1", "01"), item("ME2", "01")).mapNotNull(KecoNormalizer::infoRow)
        val (stations, statuses) = KecoNormalizer.group(rows)
        assertEquals(listOf("ME1", "ME2"), stations.map { it.statId })
        assertEquals(listOf("01", "02"), stations[0].chargers.map { it.chgerId })
        assertEquals(3, statuses.size)
    }

    @Test
    fun statusOnlyMovesForward() {
        val store = StationStore()
        val key = ChargerKey("ME1", "01")
        assertEquals(StationStore.ApplyResult.UNKNOWN_CHARGER, store.apply(key, ChargerStatus("3", 1_000L)))

        val (stations, statuses) = KecoNormalizer.group(listOfNotNull(KecoNormalizer.infoRow(item("ME1", "01", upd = "20261102110000"))))
        store.replaceMaster(stations, statuses, TEST_NOW.toEpochMilli())
        val t = TEST_NOW.toEpochMilli()
        assertEquals(StationStore.ApplyResult.APPLIED, store.apply(key, ChargerStatus("3", t)))
        assertEquals(StationStore.ApplyResult.STALE, store.apply(key, ChargerStatus("2", t - 60_000L)))
        assertEquals("3", store.status(key)?.stat)
        assertEquals(StationStore.ApplyResult.APPLIED, store.apply(key, ChargerStatus("2", t))) // same time: accepted
        assertEquals(StationStore.ApplyResult.UNKNOWN_CHARGER, store.apply(ChargerKey("ME1", "99"), ChargerStatus("2", t)))
    }

    @Test
    fun masterReplacementKeepsNewerHeldStatuses() {
        val store = StationStore()
        val key = ChargerKey("ME1", "01")
        val first = KecoNormalizer.group(listOfNotNull(KecoNormalizer.infoRow(item("ME1", "01", upd = "20261102110000"))))
        store.replaceMaster(first.first, first.second, 0L)
        store.apply(key, ChargerStatus("3", TEST_NOW.toEpochMilli()))
        // A master page fetched a little earlier still says "free since 11:30": the newer delta wins.
        val second = KecoNormalizer.group(listOfNotNull(KecoNormalizer.infoRow(item("ME1", "01", stat = "2", upd = "20261102113000"))))
        store.replaceMaster(second.first, second.second, 1L)
        assertEquals("3", store.status(key)?.stat)
    }

    @Test
    fun snapshotRoundTripsThroughGzipFile() {
        val dir = Files.createTempDirectory("advisor-snapshot")
        val store = StationStore()
        val (stations, statuses) = KecoNormalizer.group(listOf(item("ME1", "01"), item("ME1", "02")).mapNotNull(KecoNormalizer::infoRow))
        store.replaceMaster(stations, statuses, 42L)
        SnapshotStore(dir).save(store.toSnapshot(43L))

        val restored = StationStore()
        restored.restore(assertNotNull(SnapshotStore(dir).load()))
        assertEquals(42L, restored.masterSyncedAtMs)
        assertEquals(stations, restored.index?.stations)
        assertEquals("2", restored.status(ChargerKey("ME1", "02"))?.stat)

        Files.writeString(dir.resolve("snapshot.json.gz"), "not gzip")
        assertNull(SnapshotStore(dir).load())
    }
}
