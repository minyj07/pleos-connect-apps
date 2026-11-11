package com.example.pleosev.server

import com.example.pleosev.server.source.FixtureSource
import com.example.pleosev.shared.api.AdvisorJson
import com.example.pleosev.shared.api.AppVersionResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Behaviour that only matters once the server runs behind the reverse proxy for the cars. */
class DeploymentTest {

    private fun runtime(config: ServerConfig): AdvisorRuntime {
        val clock = MutableClock()
        return AdvisorRuntime(config, clock, source = FixtureSource.fromResources(clock), persistSnapshots = false)
    }

    private suspend fun HttpClient.health(forwardedFor: String?) =
        get("/v1/health") { forwardedFor?.let { header(HttpHeaders.XForwardedFor, it) } }.status

    @Test
    fun theAppVersionIsServedOnlyWhenConfigured() = testApplication {
        application { advisorModule(runtime(ServerConfig(fakeSource = true))) }
        val none = AdvisorJson.decodeFromString(AppVersionResponse.serializer(), client.get("/v1/app-version").bodyAsText())
        assertNull(none.latestVersionCode)
    }

    @Test
    fun theConfiguredAppVersionIsServed() = testApplication {
        application { advisorModule(runtime(ServerConfig(fakeSource = true, appLatestVersionCode = 3, appLatestVersionName = "1.0.2"))) }
        val body = AdvisorJson.decodeFromString(AppVersionResponse.serializer(), client.get("/v1/app-version").bodyAsText())
        assertEquals(AppVersionResponse(3, "1.0.2"), body)
    }

    @Test
    fun behindTheProxyEachCarHasItsOwnRateLimit() = testApplication {
        application { advisorModule(runtime(ServerConfig(fakeSource = true, rateLimitPerMinute = 2, trustProxy = true))) }
        assertEquals(HttpStatusCode.OK, client.health("203.0.113.1"))
        // A caller-supplied first entry does not make a new client: the proxy's entry (last) decides.
        assertEquals(HttpStatusCode.OK, client.health("198.51.100.7, 203.0.113.1"))
        assertEquals(HttpStatusCode.TooManyRequests, client.health("203.0.113.1"))
        assertEquals(HttpStatusCode.OK, client.health("203.0.113.2"))
    }

    @Test
    fun withoutTheProxyForwardedHeadersAreIgnored() = testApplication {
        application { advisorModule(runtime(ServerConfig(fakeSource = true, rateLimitPerMinute = 2))) }
        assertEquals(HttpStatusCode.OK, client.health("203.0.113.1"))
        assertEquals(HttpStatusCode.OK, client.health("203.0.113.2"))
        assertEquals(HttpStatusCode.TooManyRequests, client.health("203.0.113.3"))
    }
}
