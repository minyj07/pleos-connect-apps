package com.example.pleosev.server

import io.ktor.http.HttpStatusCode

/** A client-visible error; StatusPages renders it as `{"error":{"code","message"}}` with [status]. */
class ApiException(val status: HttpStatusCode, val code: String, override val message: String) : RuntimeException(message)

/** The station index is not loaded yet (first sync running): 503 with Retry-After. */
class NotReadyException : RuntimeException("station index is still loading")

fun badRequest(code: String, message: String): Nothing = throw ApiException(HttpStatusCode.BadRequest, code, message)
