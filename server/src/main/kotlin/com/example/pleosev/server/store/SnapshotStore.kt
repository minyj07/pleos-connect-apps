package com.example.pleosev.server.store

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import org.slf4j.LoggerFactory
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * gzip JSON snapshot of the store, so a restart serves stations at once and resumes the status chain from
 * its last poll. Contains no client data. Streamed both ways: the document is ~19 MB of JSON, which as one
 * String would be a large allocation on the server's small heap.
 */
@OptIn(ExperimentalSerializationApi::class)
class SnapshotStore(private val dir: Path) {

    private val file: Path get() = dir.resolve("snapshot.json.gz")
    private val json = Json { ignoreUnknownKeys = true }
    private val log = LoggerFactory.getLogger(SnapshotStore::class.java)

    /** Synchronized and with its own temp file: the periodic save, the post-sync save and close() can overlap. */
    @Synchronized
    fun save(snapshot: Snapshot) {
        Files.createDirectories(dir)
        val tmp = Files.createTempFile(dir, "snapshot", ".json.gz.tmp")
        try {
            GZIPOutputStream(Files.newOutputStream(tmp)).buffered().use { json.encodeToStream(Snapshot.serializer(), snapshot, it) }
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (e: AtomicMoveNotSupportedException) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(tmp)
        }
    }

    fun load(): Snapshot? {
        if (!Files.exists(file)) return null
        return try {
            GZIPInputStream(Files.newInputStream(file)).buffered().use { json.decodeFromStream(Snapshot.serializer(), it) }
        } catch (e: Exception) {
            log.warn("ignoring unreadable snapshot {}: {}", file, e.message)
            null
        }
    }
}
