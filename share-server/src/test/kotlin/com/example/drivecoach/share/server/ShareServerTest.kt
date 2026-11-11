package com.example.drivecoach.share.server

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.example.drivecoach.share.server.routes.CreatedShare
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.writeFully
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readBytes
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A clock tests can move forward. */
class MutableClock(var nowMs: Long = 1_800_000_000_000L) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId): Clock = this
    override fun instant(): Instant = Instant.ofEpochMilli(nowMs)
    override fun millis(): Long = nowMs
}

/** Bytes with the DCS1 header: the server never decrypts, it only checks the format marker. */
fun sealedBlob(size: Int = 200): ByteArray = "DCS1".toByteArray() + byteArrayOf(0) + ByteArray(size - 5) { (it % 251).toByte() }

class ShareServerTest {

    private val dir: Path = Files.createTempDirectory("shares")
    private val clock = MutableClock()

    private fun shareApp(config: ShareConfig = ShareConfig(), block: suspend ApplicationTestBuilder.(ShareRuntime) -> Unit) = testApplication {
        val runtime = ShareRuntime(config, clock, dir)
        application { shareModule(runtime) }
        block(runtime)
    }

    private suspend fun ApplicationTestBuilder.create(blob: ByteArray = sealedBlob()): CreatedShare {
        val res = client.post("/v1/shares") {
            contentType(ContentType.Application.OctetStream)
            setBody(blob)
        }
        assertEquals(HttpStatusCode.Created, res.status, res.bodyAsText())
        return ShareJson.decodeFromString(CreatedShare.serializer(), res.bodyAsText())
    }

    private suspend fun ApplicationTestBuilder.read(credential: String?): HttpResponse =
        client.get("/v1/share") { if (credential != null) header(HttpHeaders.Authorization, "Bearer $credential") }

    private suspend fun ApplicationTestBuilder.remove(credential: String?): HttpResponse =
        client.delete("/v1/share") { if (credential != null) header(HttpHeaders.Authorization, "Bearer $credential") }

    private fun shareFiles() = dir.listDirectoryEntries()

    @Test
    fun createThenReadReturnsTheSameBytesAndTheExpiry() = shareApp { _ ->
        val blob = sealedBlob(500)
        val share = create(blob)
        assertEquals(22, share.id.length)
        assertEquals(43, share.deleteToken.length)
        assertEquals(168 * 3_600_000L, share.ttlMs)
        val res = read(share.id)
        assertEquals(HttpStatusCode.OK, res.status)
        assertContentEquals(blob, res.bodyAsBytes())
        assertEquals((clock.nowMs + share.ttlMs).toString(), res.headers["X-Expires-At"])
        assertEquals(ContentType.Application.OctetStream, res.contentType()?.withoutParameters())
    }

    @Test
    fun everyMissLooksTheSame() = shareApp { _ ->
        for (credential in listOf(null, "short", "../../etc/passwd", "AAAAAAAAAAAAAAAAAAAAAA")) {
            val res = read(credential)
            assertEquals(HttpStatusCode.NotFound, res.status, "credential $credential")
            assertTrue("NOT_FOUND" in res.bodyAsText())
        }
        assertTrue(shareFiles().isEmpty())
    }

    @Test
    fun expiredShareIsGoneAndItsFileRemoved() = shareApp { _ ->
        val share = create()
        clock.nowMs += share.ttlMs
        assertEquals(HttpStatusCode.NotFound, read(share.id).status)
        assertTrue(shareFiles().isEmpty())
    }

    @Test
    fun deleteNeedsTheTokenAndIsIdempotent() = shareApp { _ ->
        val share = create()
        val wrong = "B".repeat(43)
        assertEquals(HttpStatusCode.Unauthorized, remove(null).status)
        assertEquals(HttpStatusCode.BadRequest, remove(share.id).status)
        assertEquals(HttpStatusCode.Forbidden, remove("${share.id}.$wrong").status)
        assertEquals(HttpStatusCode.OK, read(share.id).status)
        assertEquals(HttpStatusCode.NoContent, remove("${share.id}.${share.deleteToken}").status)
        assertEquals(HttpStatusCode.NotFound, read(share.id).status)
        assertEquals(HttpStatusCode.NoContent, remove("${share.id}.${share.deleteToken}").status)
    }

    @Test
    fun uploadMustBeOctetStream() = shareApp { _ ->
        for (type in listOf(ContentType.Application.Json, ContentType.Text.Plain, ContentType.Application.FormUrlEncoded)) {
            val res = client.post("/v1/shares") {
                contentType(type)
                setBody(sealedBlob().decodeToString())
            }
            assertEquals(HttpStatusCode.UnsupportedMediaType, res.status, "type $type")
        }
        assertTrue(shareFiles().isEmpty())
    }

    @Test
    fun oversizeUploadsAreRejectedWithOrWithoutALength() = shareApp(ShareConfig(maxBytes = 1024)) { _ ->
        val big = sealedBlob(2000)
        val withLength = client.post("/v1/shares") {
            contentType(ContentType.Application.OctetStream)
            setBody(big)
        }
        assertEquals(HttpStatusCode.PayloadTooLarge, withLength.status)
        val chunked = client.post("/v1/shares") {
            setBody(object : OutgoingContent.WriteChannelContent() {
                override val contentType = ContentType.Application.OctetStream
                override suspend fun writeTo(channel: ByteWriteChannel) = channel.writeFully(big)
            })
        }
        assertEquals(HttpStatusCode.PayloadTooLarge, chunked.status)
        create(sealedBlob(1024))
    }

    @Test
    fun onlySealedSharesAreAccepted() = shareApp { _ ->
        for (body in listOf(ByteArray(200) { 7 }, "DCS1".toByteArray() + ByteArray(10))) {
            val res = client.post("/v1/shares") {
                contentType(ContentType.Application.OctetStream)
                setBody(body)
            }
            assertEquals(HttpStatusCode.BadRequest, res.status)
            assertTrue("NOT_A_SHARE" in res.bodyAsText())
        }
    }

    @Test
    fun fullStorageAnswers507() = shareApp(ShareConfig(quotaBytes = 300)) { _ ->
        create(sealedBlob(200))
        val res = client.post("/v1/shares") {
            contentType(ContentType.Application.OctetStream)
            setBody(sealedBlob(200))
        }
        assertEquals(HttpStatusCode.InsufficientStorage, res.status)
    }

    @Test
    fun createIsRateLimitedPerAddress() = shareApp(ShareConfig(createPerHour = 2)) { _ ->
        create()
        create()
        val res = client.post("/v1/shares") {
            contentType(ContentType.Application.OctetStream)
            setBody(sealedBlob())
        }
        assertEquals(HttpStatusCode.TooManyRequests, res.status)
        assertTrue("RATE_LIMITED" in res.bodyAsText())
    }

    @Test
    fun everyResponseCarriesTheSecurityHeaders() = shareApp { _ ->
        val share = create()
        for (res in listOf(client.get("/s"), read(share.id), read("missing"), client.get("/assets/viewer.js"))) {
            assertEquals(CSP, res.headers["Content-Security-Policy"])
            assertEquals("no-referrer", res.headers["Referrer-Policy"])
            assertEquals("nosniff", res.headers["X-Content-Type-Options"])
            assertEquals("no-store", res.headers[HttpHeaders.CacheControl])
        }
    }

    @Test
    fun viewerPageAndAssetsAreServedAndNothingElse() = shareApp { _ ->
        val page = client.get("/s")
        assertEquals(HttpStatusCode.OK, page.status)
        assertEquals(ContentType.Text.Html, page.contentType()?.withoutParameters())
        assertTrue("/assets/viewer.js" in page.bodyAsText())
        assertEquals(HttpStatusCode.OK, client.get("/assets/viewer.js").status)
        assertEquals(ContentType.Text.CSS, client.get("/assets/viewer.css").contentType()?.withoutParameters())
        for (path in listOf("/", "/logback.xml", "/assets/", "/assets/%2e%2e/logback.xml", "/viewer/index.html")) {
            val status = client.get(path).status
            assertTrue(status == HttpStatusCode.NotFound || status == HttpStatusCode.BadRequest, "$path -> $status")
        }
    }

    @Test
    fun idsAndTokensNeverReachTheDiskOrTheLogs() {
        val root = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME) as Logger
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        root.addAppender(appender)
        val secrets = mutableListOf<String>()
        try {
            shareApp { _ ->
                val share = create()
                secrets += listOf(share.id, share.deleteToken)
                read(share.id)
                client.post("/v1/shares") { contentType(ContentType.Text.Plain); setBody("x") }
                remove("${share.id}.${"C".repeat(43)}")
                create().also { secrets += listOf(it.id, it.deleteToken) }
            }
        } finally {
            root.detachAppender(appender)
        }
        val logged = appender.list.joinToString("\n") { it.formattedMessage + (it.throwableProxy?.message ?: "") }
        val files = shareFiles()
        assertEquals(2, files.size, "the wrong-token delete keeps the first share")
        for (secret in secrets) {
            assertFalse(secret in logged, "a log line contains $secret")
            assertFalse(files.any { secret in it.name || secret in it.readBytes().decodeToString() }, "a file contains $secret")
        }
        assertTrue(files.all { it.name.matches(Regex("[0-9a-f]{64}")) })
    }

    @Test
    fun sweepRemovesOnlyExpiredShares() = shareApp { runtime ->
        val old = create()
        clock.nowMs += old.ttlMs / 2
        val fresh = create()
        clock.nowMs += old.ttlMs / 2
        assertEquals(1, runtime.store.sweep())
        assertEquals(HttpStatusCode.OK, read(fresh.id).status)
        assertEquals(1, shareFiles().size)
    }

    @Test
    fun sharesSurviveARestart() {
        val tokenHash = BlobStore.sha256("t".toByteArray())
        BlobStore(dir, clock, 10_000).put("AAAAAAAAAAAAAAAAAAAAAA", tokenHash, clock.nowMs + 1_000, sealedBlob(100))
        val reopened = BlobStore(dir, clock, 10_000)
        assertEquals(144, reopened.usedBytes())
        assertContentEquals(sealedBlob(100), reopened.get("AAAAAAAAAAAAAAAAAAAAAA")?.blob)
    }

    @Test
    fun configComesFromTheEnvironmentWithinLimits() {
        val c = ShareConfig.fromEnv(
            mapOf("PORT" to "9000", "TTL_HOURS" to "5000", "MAX_BYTES" to "10", "TRUST_PROXY" to "yes", "HOST" to " ", "PUBLIC_BASE_URL" to "https://demo.example.com/"),
        )
        assertEquals(9000, c.port)
        assertEquals(30 * 24L, c.ttlHours)
        assertEquals(1024, c.maxBytes)
        assertTrue(c.trustProxy)
        assertEquals("127.0.0.1", c.host)
        assertEquals("https://demo.example.com", c.publicBaseUrl)
        assertEquals(ShareConfig(), ShareConfig.fromEnv(emptyMap()))
        for (bad in listOf("ftp://x", "https://x.com/?a=1", "https://x.com/#k")) {
            kotlin.test.assertFailsWith<IllegalArgumentException>(bad) { ShareConfig.fromEnv(mapOf("PUBLIC_BASE_URL" to bad)) }
        }
    }

    @Test
    fun uploadAnswerCarriesThePublicAddressWhenSet() = shareApp(ShareConfig(publicBaseUrl = "https://demo.example.com")) { _ ->
        assertEquals("https://demo.example.com", create().viewBaseUrl)
    }
}
