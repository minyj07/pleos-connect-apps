package com.example.pleosev.server.source

import com.example.pleosev.server.store.KecoNormalizer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import java.time.Clock

/**
 * Offline source for FAKE_SOURCE mode and tests: serves KECO-shaped pages from in-memory rows, by
 * default the synthetic stations bundled in `resources/fake/stations.json` (not real KECO data).
 * Timestamps are shifted at load so the newest status is one minute old; status changes are made
 * with [override] and show up in getChargerStatus like real deltas. Uses no data.go.kr quota.
 */
class FixtureSource(rows: List<Map<String, String>>, private val clock: Clock) : ChargerSource {

    override val name = "fake"

    private val lock = Any()
    private val current: List<MutableMap<String, String>> = rebase(rows, clock.millis())
    private val changes = mutableListOf<Map<String, String>>()

    override suspend fun chargerInfo(zcode: String, pageNo: Int, numOfRows: Int): KecoPage = synchronized(lock) {
        page(current.filter { it["zcode"] == zcode }, pageNo, numOfRows)
    }

    override suspend fun chargerStatus(periodMin: Int, pageNo: Int, numOfRows: Int, zcode: String?): KecoPage = synchronized(lock) {
        val since = clock.millis() - periodMin * 60_000L
        val recent = changes.filter {
            (KecoNormalizer.parseKst(it["statUpdDt"]) ?: 0L) >= since && (zcode == null || it["zcode"] == zcode)
        }
        page(recent, pageNo, numOfRows)
    }

    /** Sets `stat` of one charger, or of every charger of the station when [chgerId] is null, as of now. */
    fun override(statId: String, chgerId: String?, stat: String): Int = synchronized(lock) {
        val now = KecoNormalizer.formatKst(clock.millis())
        var changed = 0
        for (row in current) {
            if (row["statId"] != statId || (chgerId != null && row["chgerId"] != chgerId)) continue
            row["stat"] = stat
            row["statUpdDt"] = now
            changes += mapOf(
                "busiId" to row["busiId"].orEmpty(), "statId" to statId, "chgerId" to row["chgerId"].orEmpty(),
                "stat" to stat, "statUpdDt" to now, "zcode" to row["zcode"].orEmpty(),
            )
            changed++
        }
        changed
    }

    private fun page(rows: List<Map<String, String>>, pageNo: Int, numOfRows: Int): KecoPage {
        val from = (pageNo - 1).coerceAtLeast(0) * numOfRows
        return KecoPage(rows.size, pageNo, numOfRows, rows.drop(from).take(numOfRows).map { it.toMap() })
    }

    // ---- Compact fixture format: one object per station, chargers as [chgerId, chgerType, outputKw, stat, statUpdDt] ----

    @Serializable
    private data class FixtureFile(val stations: List<FixtureStation>)

    @Serializable
    private data class FixtureStation(
        val statId: String,
        val statNm: String,
        val addr: String,
        val lat: Double,
        val lng: Double,
        val busiId: String,
        val busiNm: String,
        val zcode: String,
        val zscode: String? = null,
        val useTime: String = "24시간 이용가능",
        val parkingFree: String = "N",
        val limitYn: String = "N",
        val limitDetail: String? = null,
        val delYn: String = "N",
        val chargers: List<List<JsonPrimitive>>,
    )

    companion object {
        private val TIME_FIELDS = listOf("statUpdDt", "lastTsdt", "lastTedt", "nowTsdt")
        private val json = Json { ignoreUnknownKeys = true }

        fun fromResources(clock: Clock, resource: String = "/fake/stations.json"): FixtureSource {
            val text = FixtureSource::class.java.getResourceAsStream(resource)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                ?: error("missing fixture $resource")
            return FixtureSource(expand(json.decodeFromString(FixtureFile.serializer(), text)), clock)
        }

        private fun expand(file: FixtureFile): List<Map<String, String>> = file.stations.flatMap { s ->
            s.chargers.map { c ->
                val stat = c[3].content
                val statUpdDt = c[4].content
                buildMap {
                    put("statId", s.statId); put("statNm", s.statNm); put("addr", s.addr)
                    put("lat", s.lat.toString()); put("lng", s.lng.toString())
                    put("busiId", s.busiId); put("busiNm", s.busiNm); put("busiCall", "1661-0000")
                    put("zcode", s.zcode); s.zscode?.let { put("zscode", it) }
                    put("useTime", s.useTime); put("parkingFree", s.parkingFree)
                    put("limitYn", s.limitYn); s.limitDetail?.let { put("limitDetail", it) }; put("delYn", s.delYn)
                    put("chgerId", c[0].content); put("chgerType", c[1].content); put("output", c[2].content)
                    put("stat", stat); put("statUpdDt", statUpdDt)
                    if (stat == "3") put("nowTsdt", statUpdDt) else put("lastTedt", statUpdDt)
                }
            }
        }

        /** Shifts every timestamp so the newest statUpdDt becomes one minute before [nowMs]. */
        private fun rebase(rows: List<Map<String, String>>, nowMs: Long): List<MutableMap<String, String>> {
            val newest = rows.mapNotNull { KecoNormalizer.parseKst(it["statUpdDt"]) }.maxOrNull()
            val shift = if (newest == null) 0L else nowMs - 60_000L - newest
            return rows.map { row ->
                row.toMutableMap().apply {
                    for (field in TIME_FIELDS) {
                        val t = KecoNormalizer.parseKst(this[field]) ?: continue
                        this[field] = KecoNormalizer.formatKst(t + shift)
                    }
                }
            }
        }
    }
}
