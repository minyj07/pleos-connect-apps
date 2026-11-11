package com.example.drivecoach.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** A link the car made and can still delete (no link key: a link is shown once, re-sharing makes a new one). */
@Serializable
data class ActiveShare(val target: String, val id: String, val deleteToken: String, val createdAtEpochMs: Long, val expiresAtEpochMs: Long)

/** A delete the server hasn't confirmed yet (offline, or consent withdrawn while driving away). */
@Serializable
data class PendingDelete(val id: String, val deleteToken: String, val expiresAtEpochMs: Long)

@Serializable
data class ShareIndex(val active: List<ActiveShare> = emptyList(), val pendingDeletes: List<PendingDelete> = emptyList()) {
    /** The server drops a share at its expiry, so nothing older needs deleting. */
    fun pruned(nowEpochMs: Long) = ShareIndex(
        active = active.filter { it.expiresAtEpochMs > nowEpochMs },
        pendingDeletes = pendingDeletes.filter { it.expiresAtEpochMs > nowEpochMs },
    )
}

/**
 * The car's list of live share links, encrypted with its own Keystore key: TripStore.wipe() destroys the
 * trip key on consent withdrawal, and the delete tokens must outlive that until the server confirms.
 */
class ShareStore(private val file: File, private val codec: AeadCodec) {

    private val _index = MutableStateFlow(ShareIndex())
    val index: StateFlow<ShareIndex> = _index.asStateFlow()

    /** Until the file was read, the in-memory index is empty and must not overwrite it. */
    private var loaded = false

    @Synchronized
    fun load(nowEpochMs: Long) {
        val stored = if (file.exists()) runCatching { json.decodeFromString(ShareIndex.serializer(), String(codec.decrypt(file.readBytes(), AAD))) }.getOrNull() else null
        _index.value = (stored ?: ShareIndex()).pruned(nowEpochMs)
        loaded = true
    }

    @Synchronized
    fun update(transform: (ShareIndex) -> ShareIndex): ShareIndex {
        if (!loaded) load(System.currentTimeMillis())
        val next = transform(_index.value)
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeBytes(codec.encrypt(json.encodeToString(ShareIndex.serializer(), next).toByteArray(), AAD))
        if (!tmp.renameTo(file)) {
            file.delete()
            check(tmp.renameTo(file)) { "cannot replace $file" }
        }
        _index.value = next
        return next
    }

    /** Only once nothing is left to delete on the server. */
    @Synchronized
    fun wipe() {
        file.delete()
        codec.destroyKey()
        _index.value = ShareIndex()
        loaded = true
    }

    private companion object {
        const val AAD = "shares"
        val json = Json { ignoreUnknownKeys = true }
    }
}
