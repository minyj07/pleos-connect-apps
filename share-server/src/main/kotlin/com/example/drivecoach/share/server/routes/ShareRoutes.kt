package com.example.drivecoach.share.server.routes

import com.example.drivecoach.share.server.ApiException
import com.example.drivecoach.share.server.BlobStore
import com.example.drivecoach.share.server.CreateLimit
import com.example.drivecoach.share.server.ReadLimit
import com.example.drivecoach.share.server.ShareRuntime
import com.example.drivecoach.share.server.badRequest
import com.example.drivecoach.share.server.notFound
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.withCharset
import io.ktor.server.application.ApplicationCall
import io.ktor.server.http.content.staticResources
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.contentLength
import io.ktor.server.request.contentType
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.utils.io.readBuffer
import kotlinx.io.readByteArray
import kotlinx.serialization.Serializable

/** What the car gets back: the id goes into the link, the token stays on the car for deleting. */
@Serializable
data class CreatedShare(val id: String, val deleteToken: String, val ttlMs: Long, val viewBaseUrl: String? = null)

@Serializable
data class Health(val status: String)

private val ID = Regex("^[A-Za-z0-9_-]{22}$")
private val TOKEN = Regex("^[A-Za-z0-9_-]{43}$")
private val SHARE_MAGIC = "DCS1".toByteArray(Charsets.US_ASCII)

/** "DCS1" + flags + 12-byte IV + 16-byte GCM tag: the smallest sealed share. */
private const val MIN_BLOB = 5 + 12 + 16

/**
 * Share API. Ids and tokens travel only in the Authorization header (never in a path or query string,
 * which proxies log); the phone page gets its id and key from the link fragment.
 */
fun Route.shareRoutes(runtime: ShareRuntime) {
    rateLimit(CreateLimit) {
        post("/v1/shares") {
            // A form or a plain cross-site fetch can't send this type without a CORS preflight, which is never granted.
            if (!call.request.contentType().match(ContentType.Application.OctetStream)) {
                throw ApiException(HttpStatusCode.UnsupportedMediaType, "UNSUPPORTED_MEDIA_TYPE", "send application/octet-stream")
            }
            val max = runtime.config.maxBytes
            call.request.contentLength()?.let { if (it > max) tooLarge(max) }
            // Counted while reading, so chunked uploads without a length are bounded too.
            val blob = call.receiveChannel().readBuffer(max + 1L).readByteArray()
            if (blob.size > max) tooLarge(max)
            if (blob.size < MIN_BLOB || !blob.copyOfRange(0, SHARE_MAGIC.size).contentEquals(SHARE_MAGIC)) {
                badRequest("NOT_A_SHARE", "expected a sealed DCS1 blob")
            }
            val id = runtime.randomToken(16)
            val token = runtime.randomToken(32)
            val ttl = runtime.config.ttlMs
            val tokenHash = BlobStore.sha256(token.toByteArray(Charsets.US_ASCII))
            if (!runtime.store.put(id, tokenHash, runtime.clock.millis() + ttl, blob)) {
                throw ApiException(HttpStatusCode.InsufficientStorage, "FULL", "share storage is full, try again later")
            }
            call.respond(HttpStatusCode.Created, CreatedShare(id, token, ttl, runtime.config.publicBaseUrl))
        }
    }
    rateLimit(ReadLimit) {
        get("/v1/share") {
            val id = bearer(call)?.takeIf { ID.matches(it) } ?: notFound()
            val stored = runtime.store.get(id) ?: notFound()
            call.response.header("X-Expires-At", stored.expiresAtMs.toString())
            call.respondBytes(stored.blob, ContentType.Application.OctetStream)
        }
        delete("/v1/share") {
            val credential = bearer(call)
                ?: throw ApiException(HttpStatusCode.Unauthorized, "UNAUTHORIZED", "send Authorization: Bearer <id>.<deleteToken>")
            val id = credential.substringBefore('.')
            val token = credential.substringAfter('.', "")
            if (!ID.matches(id) || !TOKEN.matches(token)) badRequest("BAD_CREDENTIAL", "expected <id>.<deleteToken>")
            when (runtime.store.delete(id, token)) {
                // Unknown or already expired counts as done: the car only wants the copy gone.
                BlobStore.Deleted.DELETED, BlobStore.Deleted.ABSENT -> call.respond(HttpStatusCode.NoContent)
                BlobStore.Deleted.WRONG_TOKEN -> throw ApiException(HttpStatusCode.Forbidden, "FORBIDDEN", "wrong delete token")
            }
        }
    }
    get("/v1/health") { call.respond(Health("ok")) }

    // The phone viewer: one page for every link (the link itself lives in the fragment) and its assets.
    get("/s") { call.respondBytes(viewerPage, ContentType.Text.Html.withCharset(Charsets.UTF_8)) }
    staticResources("/assets", "viewer/assets", index = null)
}

private val viewerPage: ByteArray by lazy {
    val stream = requireNotNull(ShareRuntime::class.java.classLoader.getResourceAsStream("viewer/index.html")) { "viewer/index.html missing" }
    stream.use { it.readBytes() }
}

private fun bearer(call: ApplicationCall): String? = call.request.headers[HttpHeaders.Authorization]?.trim()
    ?.takeIf { it.length > 7 && it.regionMatches(0, "Bearer ", 0, 7, ignoreCase = true) }
    ?.substring(7)?.trim()

private fun tooLarge(max: Int): Nothing = throw ApiException(HttpStatusCode.PayloadTooLarge, "TOO_LARGE", "shares are limited to $max bytes")
