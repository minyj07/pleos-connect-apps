package com.example.pleosev.server

import com.example.pleosev.server.routes.advisorRoutes
import com.example.pleosev.shared.api.AdvisorJson
import com.example.pleosev.shared.api.ApiError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationStopping
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.ContentTransformationException
import io.ktor.server.plugins.UnsupportedMediaTypeException
import io.ktor.server.plugins.compression.Compression
import io.ktor.server.plugins.compression.gzip
import io.ktor.server.plugins.compression.minimumSize
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.path
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.seconds

/**
 * Charging-advisor relay server.
 *
 * Binds to 127.0.0.1:8080 by default (HOST / PORT env vars override). The Pleos emulator reaches it
 * via `adb reverse tcp:8080 tcp:8080` (then http://127.0.0.1:8080) or http://10.0.2.2:8080.
 * Needs DATA_GO_KR_SERVICE_KEY (data.go.kr Decoding key) or FAKE_SOURCE=1 for bundled synthetic data.
 */
fun main() {
    val config = ServerConfig.fromEnv()
    val runtime = AdvisorRuntime(config)
    embeddedServer(Netty, host = config.host, port = config.port) {
        advisorModule(runtime)
        runtime.start(this)
        monitor.subscribe(ApplicationStopping) { runtime.close() }
    }.start(wait = true)
}

fun Application.advisorModule(runtime: AdvisorRuntime) {
    val log = LoggerFactory.getLogger("AdvisorServer")

    install(ContentNegotiation) { json(AdvisorJson) }
    install(Compression) {
        gzip { minimumSize(1024) }
    }
    install(RateLimit) {
        global {
            rateLimiter(limit = runtime.config.rateLimitPerMinute, refillPeriod = 60.seconds)
            requestKey { call -> clientAddress(call, runtime.config.trustProxy) }
        }
    }
    install(NoStore)
    install(StatusPages) {
        exception<ApiException> { call, e -> call.respond(e.status, ApiError.of(e.code, e.message)) }
        exception<NotReadyException> { call, e ->
            call.response.header(HttpHeaders.RetryAfter, "30")
            call.respond(HttpStatusCode.ServiceUnavailable, ApiError.of("NOT_READY", e.message ?: "not ready"))
        }
        exception<BadRequestException> { call, e ->
            call.respond(HttpStatusCode.BadRequest, ApiError.of("BAD_REQUEST", e.message ?: "invalid request"))
        }
        // A body that is not JSON (wrong or missing Content-Type) is the caller's mistake, not a server error.
        exception<UnsupportedMediaTypeException> { call, _ ->
            call.respond(HttpStatusCode.UnsupportedMediaType, ApiError.of("UNSUPPORTED_MEDIA_TYPE", "send the body as application/json"))
        }
        exception<ContentTransformationException> { call, e ->
            call.respond(HttpStatusCode.BadRequest, ApiError.of("BAD_REQUEST", e.message ?: "invalid request body"))
        }
        exception<Throwable> { call, e ->
            // Path only: query strings and bodies may contain the caller's location.
            log.error("unhandled error on {}", call.request.path(), e)
            call.respond(HttpStatusCode.InternalServerError, ApiError.of("INTERNAL", "unexpected server error"))
        }
        status(HttpStatusCode.TooManyRequests) { call, status ->
            call.respond(status, ApiError.of("RATE_LIMITED", "too many requests"))
        }
    }
    routing { advisorRoutes(runtime) }
}

/**
 * The caller's address, for rate limiting only (never logged). Behind the reverse proxy every request comes
 * from the proxy, which appends the address it saw as the last X-Forwarded-For entry; earlier entries are
 * whatever the caller sent and are not trusted.
 */
internal fun clientAddress(call: ApplicationCall, trustProxy: Boolean): String {
    if (trustProxy) {
        call.request.headers[HttpHeaders.XForwardedFor]?.substringAfterLast(',')?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    }
    return call.request.origin.remoteAddress
}

/** Responses are live data and may describe the caller's route: never cache them. */
private val NoStore = createApplicationPlugin("NoStore") {
    onCall { call -> call.response.header(HttpHeaders.CacheControl, "no-store") }
}
