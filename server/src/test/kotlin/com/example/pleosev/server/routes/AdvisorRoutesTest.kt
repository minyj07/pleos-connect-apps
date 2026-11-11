package com.example.pleosev.server.routes

import com.example.pleosev.server.Fixture
import com.example.pleosev.server.advisorModule
import com.example.pleosev.server.fakeRuntime
import com.example.pleosev.server.syncedFakeRuntime
import com.example.pleosev.shared.api.AdvisorJson
import com.example.pleosev.shared.api.ApiError
import com.example.pleosev.shared.api.GeoPoint
import com.example.pleosev.shared.api.MatchResponse
import com.example.pleosev.shared.api.NearbyResponse
import com.example.pleosev.shared.api.RecommendMode
import com.example.pleosev.shared.api.RecommendationRequest
import com.example.pleosev.shared.api.RecommendationResponse
import com.example.pleosev.shared.api.StationDetail
import com.example.pleosev.shared.api.StatusRequest
import com.example.pleosev.shared.api.StatusResponse
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.KSerializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AdvisorRoutesTest {

    private val pangyo = GeoPoint(37.4127, 127.0947)

    private suspend fun <T> HttpResponse.decode(serializer: KSerializer<T>): T = AdvisorJson.decodeFromString(serializer, bodyAsText())

    private suspend fun ApplicationTestBuilder.postJson(path: String, body: String): HttpResponse = client.post(path) {
        contentType(ContentType.Application.Json)
        setBody(body)
    }

    private fun synced(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        val runtime = syncedFakeRuntime()
        application { advisorModule(runtime) }
        block()
    }

    @Test
    fun beforeTheFirstSyncQueriesAnswer503WithRetryAfter() = testApplication {
        val runtime = fakeRuntime()
        application { advisorModule(runtime) }
        val response = client.get("/v1/stations/nearby?lat=37.4127&lon=127.0947")
        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertEquals("30", response.headers[HttpHeaders.RetryAfter])
        assertEquals("NOT_READY", response.decode(ApiError.serializer()).error.code)
    }

    @Test
    fun nearbyListsDcStationsAndIsNeverCached() = synced {
        val response = client.get("/v1/stations/nearby?lat=${pangyo.lat}&lon=${pangyo.lon}&radiusKm=5")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
        val ids = response.decode(NearbyResponse.serializer()).stations.map { it.station.statId }
        assertTrue(ids.containsAll(Fixture.PANGYO_NEAR))
        assertTrue(Fixture.AC_ONLY !in ids)
    }

    @Test
    fun badCoordinatesAndMissingParametersAre400() = synced {
        val outside = client.get("/v1/stations/nearby?lat=10&lon=10")
        assertEquals(HttpStatusCode.BadRequest, outside.status)
        assertEquals("INVALID_COORDINATE", outside.decode(ApiError.serializer()).error.code)
        val missing = client.get("/v1/stations/nearby?lat=37.4")
        assertEquals("MISSING_PARAMETER", missing.decode(ApiError.serializer()).error.code)
    }

    @Test
    fun recommendationsValidateAndAnswer() = synced {
        val noDestination = RecommendationRequest(mode = RecommendMode.ON_ROUTE, origin = pangyo, socPct = 42.0)
        val rejected = postJson("/v1/recommendations", AdvisorJson.encodeToString(RecommendationRequest.serializer(), noDestination))
        assertEquals(HttpStatusCode.BadRequest, rejected.status)
        assertEquals("MISSING_DESTINATION", rejected.decode(ApiError.serializer()).error.code)

        val request = noDestination.copy(destination = GeoPoint(35.1151, 129.0422), remainDistanceM = 378_000, remainTimeS = 14_640, rangeKm = 210.0)
        val ok = postJson("/v1/recommendations", AdvisorJson.encodeToString(RecommendationRequest.serializer(), request))
        assertEquals(HttpStatusCode.OK, ok.status)
        assertTrue(ok.decode(RecommendationResponse.serializer()).candidates.isNotEmpty())

        assertEquals(HttpStatusCode.BadRequest, postJson("/v1/recommendations", """{"mode":"ON_ROUTE"""").status)
        assertEquals(HttpStatusCode.BadRequest, postJson("/v1/recommendations", """{"mode":"NEARBY"}""").status) // origin missing
    }

    @Test
    fun statusDetailAndMatch() = synced {
        val status = postJson(
            "/v1/stations/status",
            AdvisorJson.encodeToString(StatusRequest.serializer(), StatusRequest(listOf(Fixture.GOESAN_WATER, "NOPE0000"))),
        ).decode(StatusResponse.serializer())
        assertEquals(listOf(Fixture.GOESAN_WATER), status.stations.map { it.statId })
        assertEquals(listOf("NOPE0000"), status.missing)

        val detail = client.get("/v1/stations/${Fixture.GOESAN_WATER}").decode(StationDetail.serializer())
        assertEquals(8, detail.chargers.size)
        assertEquals(HttpStatusCode.NotFound, client.get("/v1/stations/NOPE0000").status)

        val match = client.get("/v1/stations/match") {
            parameter("lat", 36.8327504)
            parameter("lon", 127.9583201)
            parameter("name", "워터 괴산휴게소 창원방향 전기차충전소")
            parameter("kw", 350)
        }.decode(MatchResponse.serializer())
        assertEquals(Fixture.GOESAN_WATER, match.best?.station?.statId)
    }

    @Test
    fun fakeModeCanMakeTheNaviStopFull() = synced {
        val before = client.get("/v1/stations/${Fixture.GOESAN_WATER}").decode(StationDetail.serializer())
        assertTrue(before.summary.compatible.available > 0)

        val override = postJson("/debug/status-override", """{"statId":"${Fixture.GOESAN_WATER}","stat":"3"}""")
        assertEquals(HttpStatusCode.OK, override.status)
        assertEquals("""{"changed":8}""", override.bodyAsText())

        val after = client.get("/v1/stations/${Fixture.GOESAN_WATER}").decode(StationDetail.serializer())
        assertEquals(0, after.summary.compatible.available)
        assertEquals(8, after.summary.compatible.charging)
    }
}
