package com.example.pleosev.server.store

import com.example.pleosev.shared.api.Connector
import com.example.pleosev.shared.api.GeoPoint
import kotlinx.serialization.Serializable

/** KECO identifies a charger by station id + charger id (statId "ME174001", chgerId "01"). */
@Serializable
data class ChargerKey(val statId: String, val chgerId: String)

@Serializable
data class ChargerRecord(
    val chgerId: String,
    val typeCode: String,
    val connectors: Set<Connector>,
    val outputKw: Int?, // null when KECO leaves `output` empty
)

/** One station from getChargerInfo, DC chargers only. Status lives separately in the store. */
@Serializable
data class StationRecord(
    val statId: String,
    val name: String,
    val address: String,
    val location: GeoPoint,
    val operatorId: String,
    val operatorName: String,
    val operatorCall: String? = null,
    val useTime: String? = null,
    val parkingFree: Boolean? = null,
    val restricted: Boolean = false,
    val restrictionNote: String? = null,
    val note: String? = null,
    val zcode: String? = null,
    val zscode: String? = null,
    val chargers: List<ChargerRecord>,
)

/** Raw KECO status of one charger; turned into a ChargerState only when a response is built. */
@Serializable
data class ChargerStatus(
    val stat: String,
    val statUpdAtMs: Long?,
    val lastStartAtMs: Long? = null,
    val lastEndAtMs: Long? = null,
    val nowStartAtMs: Long? = null,
) {
    /**
     * A status stamped in the future (an operator's clock) would outrank every real update after it, until that
     * time came: it counts as stamped [ms] at the latest.
     */
    fun notAfter(ms: Long): ChargerStatus = if ((statUpdAtMs ?: return this) > ms) copy(statUpdAtMs = ms) else this
}
