package com.example.drivecoach.data

import com.example.drivecoach.model.Sample
import com.example.drivecoach.model.TripRecord
import com.example.drivecoach.model.TripSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/** Start of a trip that is still being recorded (or was cut off by a process death). */
@Serializable
data class PendingMeta(
    val id: String,
    val startEpochMs: Long,
    val startTMs: Long,
    val odoStartKm: Float? = null,
)

class PendingTrip(val meta: PendingMeta, val samples: List<Sample>)

@Serializable
private class SampleChunk(val samples: List<Sample>)

/**
 * On-device trip storage. Every file is encrypted with [AeadCodec]; nothing leaves the car.
 *
 * Layout under [root]: `trips/<id>.trip` (finished trips), `trips/index` (summaries for lists and
 * aggregates), `pending/<id>/meta` + `pending/<id>/<seq>.chunk` (samples of the trip in progress,
 * appended once a minute so a crash or power loss costs at most a minute of data).
 *
 * Not thread-safe by design beyond @Synchronized: callers use a single IO thread.
 */
class TripStore(private val root: File, private val codec: AeadCodec) {

    private val tripsDir = File(root, "trips")
    private val pendingDir = File(root, "pending")
    private val _summaries = MutableStateFlow<List<TripSummary>>(emptyList())
    val summaries: StateFlow<List<TripSummary>> = _summaries.asStateFlow()

    @Synchronized
    fun load() {
        _summaries.value = readIndex().sortedByDescending { it.startEpochMs }
    }

    @Synchronized
    fun save(record: TripRecord) {
        val id = record.summary.id
        write(File(tripsDir, "$id.trip"), id, json.encodeToString(TripRecord.serializer(), record))
        val index = readIndex().filterNot { it.id == id } + record.summary
        writeIndex(index)
    }

    @Synchronized
    fun record(id: String): TripRecord? = read(File(tripsDir, "$id.trip"), id)?.let { json.decodeFromString(TripRecord.serializer(), it) }

    @Synchronized
    fun delete(id: String) {
        File(tripsDir, "$id.trip").delete()
        writeIndex(readIndex().filterNot { it.id == id })
    }

    /** Drops trips older than [retentionDays]. */
    @Synchronized
    fun prune(nowEpochMs: Long, retentionDays: Int) {
        val cutoff = nowEpochMs - retentionDays * DAY_MS
        val (old, keep) = readIndex().partition { it.startEpochMs < cutoff }
        if (old.isEmpty()) return
        old.forEach { File(tripsDir, "${it.id}.trip").delete() }
        writeIndex(keep)
    }

    /** Consent withdrawn: delete every file and the key itself. */
    @Synchronized
    fun wipe() {
        root.resolve("trips").deleteRecursively()
        root.resolve("pending").deleteRecursively()
        codec.destroyKey()
        _summaries.value = emptyList()
    }

    // ---- trip in progress ----

    @Synchronized
    fun startPending(meta: PendingMeta) {
        val dir = File(pendingDir, meta.id).apply { mkdirs() }
        write(File(dir, "meta"), meta.id, json.encodeToString(PendingMeta.serializer(), meta))
    }

    @Synchronized
    fun appendPending(id: String, seq: Int, samples: List<Sample>) {
        if (samples.isEmpty()) return
        val dir = File(pendingDir, id).apply { mkdirs() }
        write(File(dir, "%06d.chunk".format(seq)), "$id/$seq", json.encodeToString(SampleChunk.serializer(), SampleChunk(samples)))
    }

    @Synchronized
    fun pendingTrips(): List<PendingTrip> {
        val dirs = pendingDir.listFiles()?.filter { it.isDirectory } ?: return emptyList()
        return dirs.mapNotNull { dir ->
            val id = dir.name
            val meta = read(File(dir, "meta"), id)?.let { json.decodeFromString(PendingMeta.serializer(), it) }
            if (meta == null) {
                dir.deleteRecursively()
                return@mapNotNull null
            }
            val samples = dir.listFiles { f -> f.name.endsWith(".chunk") }.orEmpty()
                .sortedBy { it.name }
                .flatMap { f ->
                    val seq = f.name.removeSuffix(".chunk").toIntOrNull() ?: return@flatMap emptyList()
                    read(f, "$id/$seq")?.let { json.decodeFromString(SampleChunk.serializer(), it).samples }.orEmpty()
                }
            PendingTrip(meta, samples)
        }
    }

    @Synchronized
    fun dropPending(id: String) {
        File(pendingDir, id).deleteRecursively()
    }

    // ---- files ----

    private fun readIndex(): List<TripSummary> =
        read(File(tripsDir, "index"), INDEX_AAD)?.let { json.decodeFromString(ListSerializer(TripSummary.serializer()), it) }.orEmpty()

    private fun writeIndex(list: List<TripSummary>) {
        write(File(tripsDir, "index"), INDEX_AAD, json.encodeToString(ListSerializer(TripSummary.serializer()), list))
        _summaries.value = list.sortedByDescending { it.startEpochMs }
    }

    private fun write(file: File, aad: String, text: String) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeBytes(codec.encrypt(text.toByteArray(), aad))
        if (!tmp.renameTo(file)) {
            file.delete()
            check(tmp.renameTo(file)) { "cannot replace $file" }
        }
    }

    /** Null when missing or unreadable (corrupt, or the key was invalidated). */
    private fun read(file: File, aad: String): String? {
        if (!file.exists()) return null
        return runCatching { String(codec.decrypt(file.readBytes(), aad)) }.getOrNull()
    }

    private companion object {
        const val INDEX_AAD = "index"
        const val DAY_MS = 24 * 60 * 60 * 1000L
        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = false
        }
    }
}
