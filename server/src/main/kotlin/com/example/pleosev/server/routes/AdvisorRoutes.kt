package com.example.pleosev.server.routes

import com.example.pleosev.server.AdvisorRuntime
import com.example.pleosev.server.ApiException
import com.example.pleosev.server.badRequest
import com.example.pleosev.shared.api.AppVersionResponse
import com.example.pleosev.shared.api.ChargePrefs
import com.example.pleosev.shared.api.Connector
import com.example.pleosev.shared.api.GeoPoint
import com.example.pleosev.shared.api.RecommendMode
import com.example.pleosev.shared.api.RecommendationRequest
import com.example.pleosev.shared.api.StatusRequest
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.contentLength
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable

private const val MAX_STATUS_IDS = 20
private const val MAX_NEARBY_RADIUS_KM = 50.0
private const val MAX_WAYPOINTS = 2 // NaviHelper supports FIRST and SECOND only

/** A request body is about 1 KB: anything far bigger (or of unknown size) is refused before it is read into memory. */
private const val MAX_BODY_BYTES = 64 * 1024L

private suspend inline fun <reified T : Any> ApplicationCall.receiveSmall(): T {
    val length = request.contentLength()
    if (length == null || length > MAX_BODY_BYTES) {
        throw ApiException(HttpStatusCode.PayloadTooLarge, "PAYLOAD_TOO_LARGE", "the JSON body needs a Content-Length of at most $MAX_BODY_BYTES bytes")
    }
    return receive<T>()
}

@Serializable
data class StatusOverride(val statId: String, val chgerId: String? = null, val stat: String)

@Serializable
data class StatusOverrideResult(val changed: Int)

fun Route.advisorRoutes(runtime: AdvisorRuntime) {
    route("/v1") {
        get("/health") {
            call.respond(runtime.health())
        }

        get("/app-version") {
            call.respond(AppVersionResponse(runtime.config.appLatestVersionCode, runtime.config.appLatestVersionName))
        }

        post("/recommendations") {
            val request = call.receiveSmall<RecommendationRequest>()
            validate(request)
            call.respond(runtime.recommendations.recommend(request))
        }

        get("/stations/nearby") {
            val q = call.request.queryParameters
            val center = point(q["lat"], q["lon"])
            val radiusKm = q["radiusKm"]?.toDoubleOrNull()?.coerceIn(0.5, MAX_NEARBY_RADIUS_KM) ?: 10.0
            val limit = q["limit"]?.toIntOrNull()?.coerceIn(1, 50) ?: 20
            call.respond(runtime.queries.nearby(center, radiusKm, prefsFrom(q), q["headingDeg"]?.toDoubleOrNull(), limit))
        }

        post("/stations/status") {
            val request = call.receiveSmall<StatusRequest>()
            if (request.statIds.size > MAX_STATUS_IDS) badRequest("TOO_MANY_IDS", "at most $MAX_STATUS_IDS statIds per request")
            call.respond(runtime.queries.status(request))
        }

        get("/stations/match") {
            val q = call.request.queryParameters
            call.respond(runtime.matcher.match(point(q["lat"], q["lon"]), q["name"], q["kw"]?.toIntOrNull(), prefsFrom(q)))
        }

        get("/stations/{statId}") {
            call.respond(runtime.queries.detail(call.parameters["statId"].orEmpty(), prefsFrom(call.request.queryParameters)))
        }
    }

    // FAKE_SOURCE only: change a charger's status to exercise the app (e.g. make the navi's planned stop full).
    val fixture = runtime.fixture ?: return
    post("/debug/status-override") {
        val body = call.receiveSmall<StatusOverride>()
        val changed = fixture.override(body.statId, body.chgerId, body.stat)
        if (changed == 0) throw ApiException(HttpStatusCode.NotFound, "NOT_FOUND", "no such charger in the fake data")
        runtime.scheduler.pollOnce()
        call.respond(StatusOverrideResult(changed))
    }
}

private fun validate(r: RecommendationRequest) {
    requireKorea(r.origin, "origin")
    r.destination?.let { requireKorea(it, "destination") }
    r.waypoints.forEach { requireKorea(it, "waypoint") }
    r.plannedStop?.let { requireKorea(it.location, "plannedStop") }
    if (r.waypoints.size > MAX_WAYPOINTS) badRequest("TOO_MANY_WAYPOINTS", "at most $MAX_WAYPOINTS waypoints")
    when (r.mode) {
        RecommendMode.ON_ROUTE, RecommendMode.NEAR_DESTINATION ->
            if (r.destination == null) badRequest("MISSING_DESTINATION", "${r.mode} needs a destination")
        RecommendMode.AROUND_PLANNED_STOP ->
            if (r.plannedStop == null) badRequest("MISSING_PLANNED_STOP", "AROUND_PLANNED_STOP needs plannedStop")
        RecommendMode.NEARBY -> Unit
    }
    r.socPct?.let { if (it !in 0.0..100.0) badRequest("INVALID_SOC", "socPct must be within 0..100") }
    if (r.prefs.connectors.isEmpty()) badRequest("INVALID_PREFS", "prefs.connectors must not be empty")
}

private fun point(lat: String?, lon: String?): GeoPoint {
    val la = lat?.toDoubleOrNull()
    val lo = lon?.toDoubleOrNull()
    if (la == null || lo == null) badRequest("MISSING_PARAMETER", "lat and lon are required")
    return GeoPoint(la, lo).also { requireKorea(it, "lat/lon") }
}

private fun requireKorea(p: GeoPoint, what: String) {
    if (p.lat !in 33.0..39.5 || p.lon !in 124.0..132.0) badRequest("INVALID_COORDINATE", "$what is outside Korea")
}

private fun prefsFrom(q: Parameters): ChargePrefs {
    val defaults = ChargePrefs()
    val connectors = q["connectors"]
        ?.split(',')
        ?.mapNotNull { name -> Connector.entries.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) } }
        ?.toSet()
        ?.takeIf { it.isNotEmpty() }
    return defaults.copy(
        minKw = q["minKw"]?.toIntOrNull()?.coerceIn(0, 400) ?: defaults.minKw,
        connectors = connectors ?: defaults.connectors,
    )
}
