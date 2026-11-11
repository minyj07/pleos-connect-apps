package com.example.pleosev.server.store

import kotlinx.serialization.Serializable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

@Serializable
data class SnapshotStatus(val key: ChargerKey, val status: ChargerStatus)

@Serializable
data class Snapshot(
    val version: Int = 1,
    val savedAtMs: Long,
    val masterSyncedAtMs: Long?,
    val stations: List<StationRecord>,
    val statuses: List<SnapshotStatus>,
    /** The last status poll of an unbroken chain: a restart resumes polling from it instead of syncing again. */
    val lastPollAtMs: Long? = null,
)

/**
 * Live state: the station index (swapped whole by the daily master sync) and per-charger status
 * (updated in place by the status poll). A status is replaced only by one whose `statUpdDt` is the
 * same or newer, so late or out-of-order deltas never roll a charger back.
 */
class StationStore {

    enum class ApplyResult { APPLIED, STALE, UNKNOWN_CHARGER }

    private val indexRef = AtomicReference<StationIndex?>(null)
    private val statusesRef = AtomicReference(ConcurrentHashMap<ChargerKey, ChargerStatus>())

    @Volatile
    var masterSyncedAtMs: Long? = null
        private set

    val index: StationIndex? get() = indexRef.get()

    fun replaceMaster(stations: List<StationRecord>, statuses: Map<ChargerKey, ChargerStatus>, syncedAtMs: Long?) {
        val index = StationIndex(stations)
        val merged = ConcurrentHashMap<ChargerKey, ChargerStatus>(statuses.size * 2)
        for ((key, status) in statuses) if (key in index) merged[key] = status
        // Keep any status we already hold that is newer than the master row (master pages take a while).
        for ((key, status) in statusesRef.get()) {
            if (key in index) merged.merge(key, status) { master, held -> if (isNewer(held, master)) held else master }
        }
        statusesRef.set(merged)
        indexRef.set(index)
        masterSyncedAtMs = syncedAtMs
    }

    fun apply(key: ChargerKey, status: ChargerStatus): ApplyResult {
        val index = indexRef.get() ?: return ApplyResult.UNKNOWN_CHARGER
        if (key !in index) return ApplyResult.UNKNOWN_CHARGER // AC chargers, or stations added since the last master sync
        var result = ApplyResult.APPLIED
        statusesRef.get().compute(key) { _, held ->
            if (held != null && isNewer(held, status)) {
                result = ApplyResult.STALE
                held
            } else {
                status
            }
        }
        return result
    }

    fun status(key: ChargerKey): ChargerStatus? = statusesRef.get()[key]

    fun toSnapshot(savedAtMs: Long, lastPollAtMs: Long? = null) = Snapshot(
        savedAtMs = savedAtMs,
        masterSyncedAtMs = masterSyncedAtMs,
        stations = index?.stations.orEmpty(),
        statuses = statusesRef.get().map { (key, status) -> SnapshotStatus(key, status) },
        lastPollAtMs = lastPollAtMs,
    )

    fun restore(snapshot: Snapshot) = replaceMaster(
        snapshot.stations,
        snapshot.statuses.associate { it.key to it.status.notAfter(snapshot.savedAtMs) },
        snapshot.masterSyncedAtMs,
    )

    private fun isNewer(a: ChargerStatus, b: ChargerStatus): Boolean =
        (a.statUpdAtMs ?: Long.MIN_VALUE) > (b.statUpdAtMs ?: Long.MIN_VALUE)
}
