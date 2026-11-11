package com.example.drivecoach.share

import com.example.drivecoach.analysis.SyntheticDrive
import com.example.drivecoach.data.AeadCodec
import com.example.drivecoach.data.KeyProvider
import com.example.drivecoach.data.ShareStore
import com.example.drivecoach.data.TripStore
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import java.util.Base64
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ShareManagerTest {

    private class SoftwareKeyProvider : KeyProvider {
        var key: SecretKey? = null
        override fun key(): SecretKey = key ?: KeyGenerator.getInstance("AES").apply { init(256) }.generateKey().also { key = it }
        override fun destroy() {
            key = null
        }
    }

    /** Records uploads; [online] = false answers like an unreachable server. */
    private class FakeApi : ShareApi {
        var online = true
        var viewBaseUrl: String? = null
        val uploads = mutableListOf<ByteArray>()
        val deletes = mutableListOf<String>()
        /** Runs while an upload is "on the wire". */
        var duringCreate: (suspend () -> Unit)? = null
        private var n = 0

        override suspend fun create(blob: ByteArray): ShareResult<CreatedShare> {
            duringCreate?.invoke()
            if (!online) return ShareResult.Failed(ShareErrorKind.OFFLINE, "down")
            uploads += blob
            n++
            return ShareResult.Ok(CreatedShare("id$n".padEnd(22, 'x'), "token$n", TTL, viewBaseUrl))
        }

        override suspend fun delete(id: String, deleteToken: String): ShareResult<Unit> {
            if (!online) return ShareResult.Failed(ShareErrorKind.OFFLINE, "down")
            deletes += id
            return ShareResult.Ok(Unit)
        }
    }

    private val dir: File = Files.createTempDirectory("share-manager").toFile()
    private val api = FakeApi()
    private var nowMs = 1_800_000_000_000L
    private val trip = assertNotNull(SyntheticDrive.analyze(SyntheticDrive().ramp(50.0, 6.0).hold(60.0).ramp(0.0, 6.0).samples))
    private val shareKeys = SoftwareKeyProvider()
    private val store = ShareStore(File(dir, "shares/index"), AeadCodec(shareKeys))

    private fun manager(config: ShareLinkConfig = ShareLinkConfig("http://up", "https://view.example.com", trustServerViewUrl = false)) = ShareManager(
        api = api,
        store = store,
        config = config,
        loadTrip = { id -> trip.takeIf { id == "t1" } },
        summaries = { listOf(trip.summary) },
        now = { nowMs },
        utcOffsetMin = { 540 },
    )

    @Test
    fun shareUploadsASealedCopyAndShowsALinkThatOpensIt() = runBlocking<Unit> {
        val m = manager()
        m.share(ShareTarget.Trip("t1"))
        val ready = assertIs<ShareUi.Ready>(m.ui.value)
        assertEquals(nowMs + TTL, ready.expiresAtEpochMs)
        assertTrue(ready.link.startsWith("https://view.example.com/s#"))
        val key = Base64.getUrlDecoder().decode(ready.link.substringAfterLast('.'))
        val payload = SharePayloads.decode(ShareCrypto.open(api.uploads.single(), key))
        assertEquals(trip.summary.startEpochMs, payload.trip?.startEpochMs)
        assertEquals(540, payload.utcOffsetMin)
        assertEquals(listOf("trip:t1"), store.index.value.active.map { it.target })
    }

    @Test
    fun sharingAgainReplacesTheOldLink() = runBlocking<Unit> {
        val m = manager()
        m.share(ShareTarget.Trip("t1"))
        val first = store.index.value.active.single().id
        m.share(ShareTarget.Trip("t1"))
        assertEquals(listOf(first), api.deletes)
        assertEquals(1, store.index.value.active.size)
        assertTrue(store.index.value.pendingDeletes.isEmpty())
    }

    @Test
    fun aFailedReShareKeepsTheOldLink() = runBlocking<Unit> {
        val m = manager()
        m.share(ShareTarget.Trip("t1"))
        val first = store.index.value.active.single()
        api.online = false
        m.share(ShareTarget.Trip("t1"))
        assertIs<ShareUi.Failed>(m.ui.value)
        assertEquals(listOf(first), store.index.value.active)
        assertTrue(store.index.value.pendingDeletes.isEmpty())
    }

    @Test
    fun deleteAllDuringAnUploadAlsoRemovesTheLinkBeingMade() = runBlocking<Unit> {
        val m = manager()
        api.duringCreate = {
            api.duringCreate = null
            m.deleteAll()
        }
        m.share(ShareTarget.Trip("t1"))
        assertTrue(store.index.value.active.isEmpty())
        assertEquals(1, api.deletes.size, "the new copy is deleted on the server")
        assertTrue(store.index.value.pendingDeletes.isEmpty())
        assertIs<ShareUi.Idle>(m.ui.value)
    }

    @Test
    fun releaseLinksIgnoreTheServerAddressDebugMayUseIt() = runBlocking<Unit> {
        api.viewBaseUrl = "https://evil.example.com"
        val release = manager()
        release.share(ShareTarget.Period(30))
        assertTrue(assertIs<ShareUi.Ready>(release.ui.value).link.startsWith("https://view.example.com/s#"))
        val debug = manager(ShareLinkConfig("http://up", "http://10.0.2.2:8090", trustServerViewUrl = true))
        api.viewBaseUrl = "https://demo.trycloudflare.com"
        debug.share(ShareTarget.Period(30))
        assertTrue(assertIs<ShareUi.Ready>(debug.ui.value).link.startsWith("https://demo.trycloudflare.com/s#"))
        api.viewBaseUrl = "https://x.example.com/?redirect=1"
        debug.share(ShareTarget.Period(90))
        assertTrue(assertIs<ShareUi.Ready>(debug.ui.value).link.startsWith("http://10.0.2.2:8090/s#"), "a base with a query is not used")
    }

    @Test
    fun offlineShareFailsWithoutLeavingAnything() = runBlocking<Unit> {
        api.online = false
        val m = manager()
        m.share(ShareTarget.Trip("t1"))
        assertEquals(ShareErrorKind.OFFLINE, assertIs<ShareUi.Failed>(m.ui.value).kind)
        assertTrue(store.index.value.active.isEmpty())
        m.share(ShareTarget.Trip("missing"))
        assertIs<ShareUi.Failed>(m.ui.value)
    }

    @Test
    fun deletesThatFailAreQueuedAndRetried() = runBlocking<Unit> {
        val m = manager()
        m.share(ShareTarget.Trip("t1"))
        m.share(ShareTarget.Period(30))
        api.online = false
        m.deleteAll()
        assertEquals(ShareUi.Idle, m.ui.value)
        assertTrue(store.index.value.active.isEmpty())
        assertEquals(2, store.index.value.pendingDeletes.size)
        api.online = true
        m.flushDeletes()
        assertEquals(2, api.deletes.size)
        assertTrue(store.index.value.pendingDeletes.isEmpty())
    }

    @Test
    fun queuedDeletesSurviveTheTripWipeAndARestart() = runBlocking<Unit> {
        val m = manager()
        m.share(ShareTarget.Trip("t1"))
        api.online = false
        m.delete(ShareTarget.Trip("t1"))
        // Consent withdrawal destroys the trip key; the share list has its own key and stays readable.
        TripStore(File(dir, "trips-root"), AeadCodec(SoftwareKeyProvider())).wipe()
        val reopened = ShareStore(File(dir, "shares/index"), AeadCodec(shareKeys)).apply { load(nowMs) }
        assertEquals(1, reopened.index.value.pendingDeletes.size)
        nowMs += TTL
        reopened.load(nowMs)
        assertTrue(reopened.index.value.pendingDeletes.isEmpty(), "expired on the server anyway")
    }

    @Test
    fun anUploadFinishingAfterThePageClosedStaysHiddenButDeletable() = runBlocking<Unit> {
        lateinit var m: ShareManager
        val slow = object : ShareApi by api {
            override suspend fun create(blob: ByteArray): ShareResult<CreatedShare> {
                m.close() // the driver drove off while the upload was running
                return api.create(blob)
            }
        }
        m = ShareManager(slow, store, ShareLinkConfig("http://up", "https://view.example.com", false), { trip }, { emptyList() }, { nowMs }, { 0 })
        m.share(ShareTarget.Trip("t1"))
        assertEquals(ShareUi.Idle, m.ui.value)
        assertEquals(1, store.index.value.active.size)
    }

    private companion object {
        const val TTL = 7 * 24 * 3_600_000L
    }
}
