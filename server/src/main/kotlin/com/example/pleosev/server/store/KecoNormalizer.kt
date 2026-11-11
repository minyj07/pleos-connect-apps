package com.example.pleosev.server.store

import com.example.pleosev.shared.api.GeoPoint
import com.example.pleosev.shared.charger.ChargerTypes
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlin.math.roundToInt

/** Turns KECO item maps into the server model. Only DC chargers with a usable location are kept. */
object KecoNormalizer {

    private val KST: ZoneId = ZoneId.of("Asia/Seoul")
    private val TIMESTAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")

    /** KECO timestamps are KST `yyyyMMddHHmmss`; anything else (blank, partial) is null. */
    fun parseKst(value: String?): Long? {
        val v = value?.trim().orEmpty()
        if (v.length < 14) return null
        return try {
            LocalDateTime.parse(v.substring(0, 14), TIMESTAMP).atZone(KST).toInstant().toEpochMilli()
        } catch (e: DateTimeParseException) {
            null
        }
    }

    fun formatKst(epochMs: Long): String = TIMESTAMP.format(Instant.ofEpochMilli(epochMs).atZone(KST))

    /** One getChargerInfo row. [station] carries no chargers yet; [group] assembles them. */
    data class InfoRow(val station: StationRecord, val charger: ChargerRecord, val status: ChargerStatus)

    /** Null for deleted rows, AC-only chargers, unknown charger types and rows without a location in Korea. */
    fun infoRow(item: Map<String, String>): InfoRow? {
        val statId = item.field("statId") ?: return null
        val chgerId = item.field("chgerId") ?: return null
        if (item.field("delYn").equals("Y", ignoreCase = true)) return null
        val typeCode = item.field("chgerType") ?: return null
        val connectors = ChargerTypes.connectors(typeCode)
        if (!ChargerTypes.isDc(connectors)) return null
        val lat = item.field("lat")?.toDoubleOrNull()
        val lon = item.field("lng")?.toDoubleOrNull()
        if (lat == null || lon == null || lat !in 33.0..39.5 || lon !in 124.0..132.0) return null

        val station = StationRecord(
            statId = statId,
            name = item.field("statNm") ?: statId,
            address = item.field("addr").orEmpty(),
            location = GeoPoint(lat, lon),
            operatorId = item.field("busiId").orEmpty(),
            operatorName = item.field("busiNm") ?: item.field("bnm").orEmpty(),
            operatorCall = item.field("busiCall"),
            useTime = item.field("useTime"),
            parkingFree = item.field("parkingFree")?.equals("Y", ignoreCase = true),
            restricted = item.field("limitYn").equals("Y", ignoreCase = true),
            restrictionNote = item.field("limitDetail"),
            note = item.field("note"),
            zcode = item.field("zcode"),
            zscode = item.field("zscode"),
            chargers = emptyList(),
        )
        val outputKw = item.field("output")?.toDoubleOrNull()?.roundToInt()?.takeIf { it > 0 }
        return InfoRow(station, ChargerRecord(chgerId, typeCode, connectors, outputKw), status(item))
    }

    /** One getChargerStatus row (busiId, statId, chgerId, stat, statUpdDt, lastTsdt, lastTedt, nowTsdt). */
    fun statusRow(item: Map<String, String>): Pair<ChargerKey, ChargerStatus>? {
        val statId = item.field("statId") ?: return null
        val chgerId = item.field("chgerId") ?: return null
        return ChargerKey(statId, chgerId) to status(item)
    }

    /** Groups charger rows into stations (station fields from the first row), de-duplicating chargers. */
    fun group(rows: Iterable<InfoRow>): Pair<List<StationRecord>, Map<ChargerKey, ChargerStatus>> {
        val stations = LinkedHashMap<String, StationRecord>()
        val chargers = HashMap<String, LinkedHashMap<String, ChargerRecord>>()
        val statuses = HashMap<ChargerKey, ChargerStatus>()
        for (row in rows) {
            val statId = row.station.statId
            stations.putIfAbsent(statId, row.station)
            chargers.getOrPut(statId) { LinkedHashMap() }[row.charger.chgerId] = row.charger
            statuses[ChargerKey(statId, row.charger.chgerId)] = row.status
        }
        val built = stations.values.map { station ->
            station.copy(chargers = chargers[station.statId].orEmpty().values.sortedBy { it.chgerId })
        }
        return built to statuses
    }

    private fun status(item: Map<String, String>) = ChargerStatus(
        stat = item.field("stat") ?: "9",
        statUpdAtMs = parseKst(item["statUpdDt"]),
        lastStartAtMs = parseKst(item["lastTsdt"]),
        lastEndAtMs = parseKst(item["lastTedt"]),
        nowStartAtMs = parseKst(item["nowTsdt"]),
    )

    private fun Map<String, String>.field(name: String): String? = this[name]?.trim()?.takeIf { it.isNotEmpty() }
}
