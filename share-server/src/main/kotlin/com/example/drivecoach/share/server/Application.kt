package com.example.drivecoach.share.server

import com.example.drivecoach.share.server.routes.shareRoutes
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * Drive coach share server ("폰으로 보기").
 *
 * Binds to 127.0.0.1:8090 by default (HOST / PORT override). The Pleos emulator uploads to
 * http://10.0.2.2:8090; a phone needs an https address in front of it (a tunnel or Caddy).
 */
fun main() {
    val config = ShareConfig.fromEnv()
    val runtime = ShareRuntime(config)
    embeddedServer(Netty, host = config.host, port = config.port) {
        shareModule(runtime)
        runtime.start(this)
    }.start(wait = true)
}

val CreateLimit = RateLimitName("create")
val ReadLimit = RateLimitName("read")

fun Application.shareModule(runtime: ShareRuntime) {
    val log = LoggerFactory.getLogger("ShareServer")

    install(ContentNegotiation) { json(ShareJson) }
    install(RateLimit) {
        register(CreateLimit) {
            rateLimiter(limit = runtime.config.createPerHour, refillPeriod = 1.hours)
            requestKey { call -> clientAddress(call, runtime.config.trustProxy) }
        }
        register(ReadLimit) {
            rateLimiter(limit = runtime.config.readPerMinute, refillPeriod = 1.minutes)
            requestKey { call -> clientAddress(call, runtime.config.trustProxy) }
        }
    }
    install(SecurityHeaders)
    install(StatusPages) {
        exception<ApiException> { call, e -> call.respond(e.status, ApiError.of(e.code, e.message)) }
        exception<BadRequestException> { call, e ->
            call.respond(HttpStatusCode.BadRequest, ApiError.of("BAD_REQUEST", e.message ?: "invalid request"))
        }
        exception<Throwable> { call, e ->
            // Method and path only: links live in the Authorization header and the page fragment, never here.
            log.error("unhandled error on {} {}", call.request.httpMethod.value, call.request.path(), e)
            call.respond(HttpStatusCode.InternalServerError, ApiError.of("INTERNAL", "unexpected server error"))
        }
        status(HttpStatusCode.TooManyRequests) { call, status ->
            call.respond(status, ApiError.of("RATE_LIMITED", "too many requests"))
        }
    }
    routing { shareRoutes(runtime) }
}

/**
 * The caller's address, for rate limiting only (never logged or stored). Behind the reverse proxy the
 * last X-Forwarded-For entry is the address the proxy saw; earlier entries are the caller's own claim.
 */
internal fun clientAddress(call: ApplicationCall, trustProxy: Boolean): String {
    if (trustProxy) {
        call.request.headers[HttpHeaders.XForwardedFor]?.substringAfterLast(',')?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    }
    return call.request.origin.remoteAddress
}

/**
 * The viewer runs only its own script and talks only to this origin; nothing is cached (blobs and pages
 * are short-lived), and no referrer leaves the page.
 */
private val SecurityHeaders = createApplicationPlugin("SecurityHeaders") {
    onCall { call ->
        with(call.response) {
            header("Content-Security-Policy", CSP)
            header("Referrer-Policy", "no-referrer")
            header("X-Content-Type-Options", "nosniff")
            header("Cross-Origin-Resource-Policy", "same-origin")
            header(HttpHeaders.CacheControl, "no-store")
        }
    }
}

const val CSP = "default-src 'none'; script-src 'self'; style-src 'self'; connect-src 'self'; img-src 'self'; " +
    "base-uri 'none'; form-action 'none'; frame-ancestors 'none'; require-trusted-types-for 'script'"
