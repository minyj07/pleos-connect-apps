package com.example.drivecoach.share

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** What the share server answers to an upload (share-server `routes/ShareRoutes.kt`). */
@Serializable
data class CreatedShare(
    val id: String,
    val deleteToken: String,
    val ttlMs: Long,
    /** The server's public address; only debug/staging builds use it (release links come from BuildConfig). */
    val viewBaseUrl: String? = null,
)

enum class ShareErrorKind { NOT_CONFIGURED, OFFLINE, RATE_LIMITED, TOO_LARGE, SERVER }

sealed interface ShareResult<out T> {
    data class Ok<T>(val value: T) : ShareResult<T>
    data class Failed(val kind: ShareErrorKind, val message: String) : ShareResult<Nothing>
}

/** The two calls the car makes; tests substitute a fake. */
interface ShareApi {
    suspend fun create(blob: ByteArray): ShareResult<CreatedShare>
    suspend fun delete(id: String, deleteToken: String): ShareResult<Unit>
}

/**
 * Plain HttpURLConnection: two small calls don't justify an HTTP library in the APK. Ids and tokens go in
 * the Authorization header, never in the URL (Pleos security guide: URLs end up in proxy logs).
 */
class HttpShareApi(private val baseUrl: String) : ShareApi {

    override suspend fun create(blob: ByteArray): ShareResult<CreatedShare> =
        call("POST", "/v1/shares", auth = null, body = blob) { json.decodeFromString(CreatedShare.serializer(), it) }

    override suspend fun delete(id: String, deleteToken: String): ShareResult<Unit> =
        call("DELETE", "/v1/share", auth = "Bearer $id.$deleteToken", body = null) { }

    private suspend fun <T> call(method: String, path: String, auth: String?, body: ByteArray?, parse: (String) -> T): ShareResult<T> {
        if (baseUrl.isBlank()) return ShareResult.Failed(ShareErrorKind.NOT_CONFIGURED, "share server not configured")
        return try {
            runInterruptible(Dispatchers.IO) {
                val conn = URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection
                try {
                    conn.requestMethod = method
                    conn.connectTimeout = CONNECT_TIMEOUT_MS
                    conn.readTimeout = READ_TIMEOUT_MS
                    conn.useCaches = false
                    conn.instanceFollowRedirects = false
                    auth?.let { conn.setRequestProperty("Authorization", it) }
                    if (body != null) {
                        conn.doOutput = true
                        conn.setRequestProperty("Content-Type", "application/octet-stream")
                        conn.setFixedLengthStreamingMode(body.size)
                        conn.outputStream.use { it.write(body) }
                    }
                    val code = conn.responseCode
                    val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.use { it.readBytes().decodeToString() }.orEmpty()
                    when (code) {
                        in 200..299 -> ShareResult.Ok(parse(text))
                        429 -> ShareResult.Failed(ShareErrorKind.RATE_LIMITED, "HTTP 429")
                        413 -> ShareResult.Failed(ShareErrorKind.TOO_LARGE, "HTTP 413")
                        else -> ShareResult.Failed(ShareErrorKind.SERVER, "HTTP $code ${text.take(120)}")
                    }
                } finally {
                    conn.disconnect()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            ShareResult.Failed(ShareErrorKind.OFFLINE, e.javaClass.simpleName)
        } catch (e: SerializationException) {
            ShareResult.Failed(ShareErrorKind.SERVER, "unexpected response")
        } catch (e: IllegalArgumentException) {
            ShareResult.Failed(ShareErrorKind.SERVER, "unexpected response")
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 5_000
        const val READ_TIMEOUT_MS = 15_000
        val json = Json { ignoreUnknownKeys = true }
    }
}
