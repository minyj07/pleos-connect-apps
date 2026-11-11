package com.example.pleosev.server

import com.example.pleosev.shared.api.AdvisorJson
import com.example.pleosev.shared.api.HealthResponse
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals

class HealthRouteTest {

    private fun health(body: String) = AdvisorJson.decodeFromString(HealthResponse.serializer(), body)

    @Test
    fun healthReportsStartingBeforeTheFirstSync() = testApplication {
        val runtime = fakeRuntime()
        application { advisorModule(runtime) }
        val response = client.get("/v1/health")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("starting", health(response.bodyAsText()).status)
    }

    @Test
    fun healthReportsTheIndexAfterSync() = testApplication {
        val runtime = syncedFakeRuntime()
        application { advisorModule(runtime) }
        val body = health(client.get("/v1/health").bodyAsText())
        assertEquals("ok", body.status)
        assertEquals("fake", body.source)
        assertEquals(Fixture.DC_STATIONS, body.dcStations)
        assertEquals(Fixture.DC_CHARGERS, body.dcChargers)
        assertEquals(0, body.apiCallsToday) // fake data uses no data.go.kr quota
    }
}
