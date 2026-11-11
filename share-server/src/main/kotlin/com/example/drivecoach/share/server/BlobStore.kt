package com.example.drivecoach.share.server

import java.nio.ByteBuffer
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.security.MessageDigest
import java.time.Clock
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

/**
 * One file per share: a small header (expiry, SHA-256 of the delete token) followed by the opaque blob.
 * The file name is hex(SHA-256(id)), never the id itself, so a request can't steer a path and the id
 * (half of the link) never sits on disk. The blob is ciphertext only; its key never reaches this server.
 */
class BlobStore(private val dir: Path, private val clock: Clock, private val quotaBytes: Long) {

    class Stored(val expiresAtMs: Long, val blob: ByteArray)

    enum class Deleted { DELETED, ABSENT, WRONG_TOKEN }

    private val used = AtomicLong()

    init {
        Files.createDirectories(dir)
        dir.listDirectoryEntries().filter { it.name.endsWith(TMP) }.forEach { Files.deleteIfExists(it) }
        used.set(shareFiles().sumOf { Files.size(it) })
    }

    /** False when the disk budget is used up. */
    fun put(id: String, deleteTokenHash: ByteArray, expiresAtMs: Long, blob: ByteArray): Boolean {
        val size = HEADER + blob.size.toLong()
        // Reserve first, so parallel uploads can't all pass the check against the same old total.
        if (used.addAndGet(size) > quotaBytes) {
            used.addAndGet(-size)
            return false
        }
        val bytes = ByteBuffer.allocate(size.toInt())
            .put(MAGIC).putLong(expiresAtMs).put(deleteTokenHash).put(blob)
            .array()
        val file = fileOf(id)
        val tmp = dir.resolve("${file.name}.${UUID.randomUUID()}$TMP")
        try {
            Files.write(tmp, bytes)
            try {
                Files.move(tmp, file, REPLACE_EXISTING, ATOMIC_MOVE)
            } catch (e: AtomicMoveNotSupportedException) {
                Files.move(tmp, file, REPLACE_EXISTING)
            }
        } catch (e: Exception) {
            used.addAndGet(-size)
            Files.deleteIfExists(tmp)
            throw e
        }
        return true
    }

    /** Null when unknown or expired (an expired file is removed on the spot). */
    fun get(id: String): Stored? {
        val file = fileOf(id)
        val bytes = read(file) ?: return null
        val expiresAt = ByteBuffer.wrap(bytes, MAGIC.size, 8).long
        if (expiresAt <= clock.millis()) {
            remove(file)
            return null
        }
        return Stored(expiresAt, bytes.copyOfRange(HEADER, bytes.size))
    }

    fun delete(id: String, deleteToken: String): Deleted {
        val file = fileOf(id)
        val bytes = read(file) ?: return Deleted.ABSENT
        val stored = bytes.copyOfRange(MAGIC.size + 8, HEADER)
        if (!MessageDigest.isEqual(stored, sha256(deleteToken.toByteArray(Charsets.US_ASCII)))) return Deleted.WRONG_TOKEN
        remove(file)
        return Deleted.DELETED
    }

    /** Removes expired shares; returns how many. */
    fun sweep(): Int {
        val now = clock.millis()
        var removed = 0
        for (file in shareFiles()) {
            val head = runCatching { Files.newInputStream(file).use { it.readNBytes(MAGIC.size + 8) } }.getOrNull() ?: continue
            if (head.size == MAGIC.size + 8 && ByteBuffer.wrap(head, MAGIC.size, 8).long <= now) {
                remove(file)
                removed++
            }
        }
        return removed
    }

    fun usedBytes(): Long = used.get()

    private fun read(file: Path): ByteArray? {
        val bytes = try {
            Files.readAllBytes(file)
        } catch (e: NoSuchFileException) {
            return null
        }
        return bytes.takeIf { it.size >= HEADER && it.copyOfRange(0, MAGIC.size).contentEquals(MAGIC) }
    }

    private fun remove(file: Path) {
        val size = runCatching { Files.size(file) }.getOrNull() ?: return
        if (Files.deleteIfExists(file)) used.addAndGet(-size)
    }

    private fun shareFiles(): List<Path> = dir.listDirectoryEntries().filter { it.name.length == 64 && !it.name.endsWith(TMP) }

    private fun fileOf(id: String): Path = dir.resolve(hex(sha256(id.toByteArray(Charsets.US_ASCII))))

    companion object {
        private val MAGIC = "DCSV".toByteArray(Charsets.US_ASCII)
        private const val HEADER = 4 + 8 + 32
        private const val TMP = ".tmp"

        fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)

        private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }
    }
}
