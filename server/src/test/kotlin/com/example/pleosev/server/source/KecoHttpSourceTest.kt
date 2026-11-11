package com.example.pleosev.server.source

import com.example.pleosev.server.MutableClock
import com.example.pleosev.server.sync.ApiBudget
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.encodeURLParameter
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class KecoHttpSourceTest {

    private val key = "ab+cd/ef==" // shaped like a real Decoding key: + / = must survive exactly one encoding
    private val okBody = """{"resultCode":"00","totalCount":1,"items":{"item":[{"statId":"ME000001","chgerId":"01"}]},"pageNo":1,"numOfRows":10}"""
    private val json = headersOf(HttpHeaders.ContentType, "application/json")

    @Test
    fun decodingKeyIsEncodedExactlyOnce() = runBlocking {
        var seen: Url? = null
        val budget = ApiBudget(10_000, MutableClock())
        val source = KecoHttpSource(key, budget, HttpClient(MockEngine { request ->
            seen = request.url
            respond(okBody, HttpStatusCode.OK, json)
        }))
        assertEquals(1, source.chargerStatus(periodMin = 5, pageNo = 1, numOfRows = 100, zcode = null).items.size)

        val url = assertNotNull(seen)
        assertEquals("/B552584/EvCharger/getChargerStatus", url.encodedPath)
        assertEquals(key, url.parameters["serviceKey"])
        assertFalse("%25" in url.encodedQuery, "double-encoded: ${url.encodedQuery}")
        assertEquals("5", url.parameters["period"])
        assertEquals("JSON", url.parameters["dataType"])
        assertEquals(null, url.parameters["zcode"])
        assertEquals(1, budget.usedToday())
    }

    @Test
    fun serverErrorsAreRetriedAndCounted() = runBlocking {
        var calls = 0
        val budget = ApiBudget(10_000, MutableClock())
        val source = KecoHttpSource(key, budget, HttpClient(MockEngine {
            calls++
            if (calls == 1) respond("busy", HttpStatusCode.ServiceUnavailable) else respond(okBody, HttpStatusCode.OK, json)
        }))
        assertEquals(1, source.chargerInfo("11", 1, 10).items.size)
        assertEquals(2, calls)
        assertEquals(2, budget.usedToday())
    }

    @Test
    fun serviceErrorsAreNotRetried() = runBlocking {
        var calls = 0
        val source = KecoHttpSource(key, ApiBudget(10_000, MutableClock()), HttpClient(MockEngine {
            calls++
            respond(
                "<OpenAPI_ServiceResponse><cmmMsgHeader><returnAuthMsg>SERVICE_KEY_IS_NOT_REGISTERED_ERROR</returnAuthMsg>" +
                    "<returnReasonCode>30</returnReasonCode></cmmMsgHeader></OpenAPI_ServiceResponse>",
                HttpStatusCode.OK,
            )
        }))
        val e = assertFailsWith<KecoApiException> { source.chargerInfo("11", 1, 10) }
        assertEquals("30", e.code)
        assertEquals(1, calls)
    }

    @Test
    fun gatewayEnvelopeKeepsItsCodeOnAnHttpError() = runBlocking {
        // What data.go.kr sent in spike 0-4 for an unregistered key: HTTP 403 with a JSON envelope.
        var calls = 0
        val source = KecoHttpSource(key, ApiBudget(10_000, MutableClock()), HttpClient(MockEngine {
            calls++
            respond(
                """{"OpenAPI_ServiceResponse":{"cmmMsgHeader":{"errMsg":"SERVICE_KEY_IS_NOT_REGISTERED_ERROR","returnReasonCode":"30"}}}""",
                HttpStatusCode.Forbidden,
                json,
            )
        }))
        val e = assertFailsWith<KecoApiException> { source.chargerInfo("11", 1, 10) }
        assertEquals("30", e.code)
        assertTrue("SERVICE_KEY_IS_NOT_REGISTERED_ERROR" in e.detail)
        assertEquals(1, calls)
    }

    @Test
    fun redactRemovesTheKeyInEveryEncoding() {
        val source = KecoHttpSource(key, ApiBudget(1, MutableClock()), HttpClient(MockEngine { respond("") }))
        val message = "timeout url=https://apis.data.go.kr/x?serviceKey=${key.encodeURLParameter()}&raw=$key"
        val redacted = source.redact(message)
        assertFalse(key in redacted)
        assertFalse(key.encodeURLParameter() in redacted)
        assertTrue("<SERVICE_KEY>" in redacted)
    }
}
