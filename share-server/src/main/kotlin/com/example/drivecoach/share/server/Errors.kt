package com.example.drivecoach.share.server

import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A client-visible error; StatusPages renders it as `{"error":{"code","message"}}` with [status]. */
class ApiException(val status: HttpStatusCode, val code: String, override val message: String) : RuntimeException(message)

@Serializable
data class ApiError(val error: Body) {
    @Serializable
    data class Body(val code: String, val message: String)

    companion object {
        fun of(code: String, message: String) = ApiError(Body(code, message))
    }
}

val ShareJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}

/** Every miss looks the same (unknown, expired, deleted, malformed), so ids can't be probed. */
fun notFound(): Nothing = throw ApiException(HttpStatusCode.NotFound, "NOT_FOUND", "link expired or deleted")

fun badRequest(code: String, message: String): Nothing = throw ApiException(HttpStatusCode.BadRequest, code, message)
