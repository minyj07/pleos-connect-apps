package com.example.drivecoach.share

import com.example.drivecoach.data.ActiveShare
import com.example.drivecoach.data.PendingDelete
import com.example.drivecoach.data.ShareIndex
import com.example.drivecoach.data.ShareStore
import com.example.drivecoach.model.TripRecord
import com.example.drivecoach.model.TripSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.URI
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicInteger

/** What a link shows: one trip, or the habits of the last [Period.days] days. */
sealed interface ShareTarget {
    val key: String

    data class Trip(val tripId: String) : ShareTarget {
        override val key get() = "trip:$tripId"
    }

    data class Period(val days: Int) : ShareTarget {
        override val key get() = "period:$days"
    }
}

/** The "폰으로 보기" page state; it outlives the screens (the upload goes on if the driver leaves). */
sealed interface ShareUi {
    data object Idle : ShareUi
    data class NeedsConsent(val target: ShareTarget) : ShareUi
    data class Working(val target: ShareTarget) : ShareUi
    data class Ready(val target: ShareTarget, val link: String, val expiresAtEpochMs: Long) : ShareUi
    data class Failed(val target: ShareTarget, val kind: ShareErrorKind) : ShareUi
}

/**
 * [uploadBaseUrl] is where the car uploads; [viewBaseUrl] is what goes into the QR. Release builds take
 * the view address only from BuildConfig ([trustServerViewUrl] = false), so a server can't point the
 * phone at another viewer. Debug/staging may take it from the server (a demo tunnel's changing address).
 */
data class ShareLinkConfig(val uploadBaseUrl: String, val viewBaseUrl: String, val trustServerViewUrl: Boolean) {
    val available: Boolean get() = uploadBaseUrl.isNotBlank()
}

/**
 * Makes and removes share links. One live link per target: sharing again replaces the old one, so no
 * link key is ever stored. The delete id and token are saved the moment the server answers (the driver
 * may drive off mid-upload), and deletes the server hasn't confirmed are queued and retried.
 */
class ShareManager(
    private val api: ShareApi,
    private val store: ShareStore,
    private val config: ShareLinkConfig,
    private val loadTrip: suspend (String) -> TripRecord?,
    private val summaries: () -> List<TripSummary>,
    private val now: () -> Long = System::currentTimeMillis,
    private val utcOffsetMin: (Long) -> Int = { TimeZone.getDefault().getOffset(it) / 60_000 },
) {
    private val _ui = MutableStateFlow<ShareUi>(ShareUi.Idle)
    val ui: StateFlow<ShareUi> = _ui.asStateFlow()
    val index: StateFlow<ShareIndex> = store.index
    val available: Boolean get() = config.available

    private val deleting = Mutex()

    /** One upload at a time: a double tap must not leave a second, never-shown link behind. */
    private val sharing = Mutex()

    /** Target key of the upload in flight, and whether a delete asked for it while it was on its way. */
    @Volatile private var inFlight: String? = null
    @Volatile private var inFlightCancelled = false

    /** Orders "keep the new link" against deletes, so a delete never misses a link landing at that moment. */
    private val lock = Any()

    /** Bumped by every new page state; an upload finishing after the page was closed stays out of sight. */
    private val generation = AtomicInteger()

    fun askConsent(target: ShareTarget) {
        generation.incrementAndGet()
        _ui.value = ShareUi.NeedsConsent(target)
    }

    fun close() {
        generation.incrementAndGet()
        _ui.value = ShareUi.Idle
    }

    suspend fun share(target: ShareTarget) {
        val gen = generation.incrementAndGet()
        show(gen, ShareUi.Working(target))
        sharing.withLock { upload(target, gen) }
        flushDeletes()
    }

    private suspend fun upload(target: ShareTarget, gen: Int) {
        val time = now()
        val payload = when (target) {
            is ShareTarget.Trip -> loadTrip(target.tripId)?.let { SharePayloads.trip(it, time, utcOffsetMin(time)) }
            is ShareTarget.Period -> SharePayloads.period(summaries(), target.days, time, utcOffsetMin(time))
        }
        if (payload == null) {
            show(gen, ShareUi.Failed(target, ShareErrorKind.SERVER))
            return
        }
        val sealed = ShareCrypto.seal(SharePayloads.encode(payload))
        inFlight = target.key
        inFlightCancelled = false
        try {
            settle(target, gen, time, sealed.key, api.create(sealed.blob))
        } finally {
            inFlight = null
        }
    }

    private fun settle(target: ShareTarget, gen: Int, time: Long, key: ByteArray, r: ShareResult<CreatedShare>) {
        when (r) {
            // The old link stays until a new one exists: a failed re-share must not take it away.
            is ShareResult.Failed -> show(gen, ShareUi.Failed(target, r.kind))
            is ShareResult.Ok -> {
                val expiresAt = now() + r.value.ttlMs
                val created = ActiveShare(target.key, r.value.id, r.value.deleteToken, time, expiresAt)
                val kept = synchronized(lock) {
                    if (inFlightCancelled) {
                        // "모두 지우기", a consent withdrawal or the trip's deletion came while uploading: never keep it.
                        store.update { it.copy(pendingDeletes = it.pendingDeletes + PendingDelete(created.id, created.deleteToken, expiresAt)) }
                        false
                    } else {
                        val old = store.index.value.active.filter { it.target == target.key }
                        // Saved before anything else: the page may be gone (driving started), the copy must stay deletable.
                        store.update { it.copy(active = it.active + created) }
                        old.forEach { retire(it) }
                        true
                    }
                }
                if (!kept) return
                show(gen, ShareUi.Ready(target, ShareLink.build(viewBase(r.value.viewBaseUrl), r.value.id, key), expiresAt))
            }
        }
    }

    private fun show(gen: Int, state: ShareUi) {
        if (generation.get() == gen) _ui.value = state
    }

    /** Deletes the link of [target] (the share page's "링크 지우기", a deleted trip). */
    suspend fun delete(target: ShareTarget) {
        synchronized(lock) {
            if (inFlight == target.key) inFlightCancelled = true
            store.index.value.active.filter { it.target == target.key }.forEach { retire(it) }
        }
        if ((_ui.value as? ShareUi.Ready)?.target == target) close()
        flushDeletes()
    }

    /** Settings "모두 지우기", share-consent withdrawal and main-consent withdrawal. */
    suspend fun deleteAll() {
        synchronized(lock) {
            if (inFlight != null) inFlightCancelled = true
            store.index.value.active.forEach { retire(it) }
        }
        if (_ui.value !is ShareUi.Idle) close()
        flushDeletes()
    }

    /** Sends queued deletes; the ones that fail stay queued for the next try (app start, next share). */
    suspend fun flushDeletes() = deleting.withLock {
        val time = now()
        for (p in store.index.value.pendingDeletes) {
            val done = p.expiresAtEpochMs <= time || api.delete(p.id, p.deleteToken) is ShareResult.Ok
            if (done) store.update { it.copy(pendingDeletes = it.pendingDeletes - p) }
        }
    }

    private fun retire(share: ActiveShare) {
        store.update {
            it.copy(active = it.active - share, pendingDeletes = it.pendingDeletes + PendingDelete(share.id, share.deleteToken, share.expiresAtEpochMs))
        }
    }

    private fun viewBase(fromServer: String?): String {
        if (config.trustServerViewUrl && fromServer != null && isPlainHttpBase(fromServer)) return fromServer
        return config.viewBaseUrl
    }

    private fun isPlainHttpBase(url: String): Boolean = runCatching {
        val u = URI(url)
        (u.scheme == "https" || u.scheme == "http") && !u.host.isNullOrBlank() && u.rawQuery == null && u.rawFragment == null
    }.getOrDefault(false)
}
