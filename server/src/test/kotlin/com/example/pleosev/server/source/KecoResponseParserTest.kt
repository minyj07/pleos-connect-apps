package com.example.pleosev.server.source

import com.example.pleosev.server.store.KecoNormalizer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Real KECO responses saved by scripts/keco-probe.ps1 (resources/fixtures, spike 0-4 on 2026-11-04; the probe may
 * refresh them, so only shape is asserted) and synthetic ones in the other shapes data.go.kr APIs use.
 */
class KecoResponseParserTest {

    private fun fixture(name: String) = checkNotNull(javaClass.getResource("/fixtures/$name")) { "missing fixture $name" }.readText()

    @Test
    fun realInfoPageInJsonAndXmlCarriesTheFieldsTheNormalizerReads() {
        val page = KecoResponseParser.parse(fixture("keco-info-seoul-10.json"))
        val xml = KecoResponseParser.parse(fixture("keco-info-seoul-10.xml"))
        assertEquals(10, page.items.size)
        assertTrue(page.totalCount > page.items.size)
        assertEquals(page.items.map { it["statId"] to it["chgerId"] }, xml.items.map { it["statId"] to it["chgerId"] })
        for (item in page.items) {
            for (field in listOf("statId", "chgerId", "chgerType", "lat", "lng", "stat", "statUpdDt")) {
                assertFalse(item[field].isNullOrBlank(), "$field missing in ${item["statId"]}/${item["chgerId"]}")
            }
            assertNotNull(KecoNormalizer.parseKst(item["statUpdDt"]))
        }
    }

    @Test
    fun realStatusPageNormalizesEveryRow() {
        val page = KecoResponseParser.parse(fixture("keco-status-all-period5.json"))
        assertEquals(100, page.items.size)
        assertTrue(page.totalCount >= page.items.size)
        val rows = page.items.mapNotNull { KecoNormalizer.statusRow(it) }
        assertEquals(page.items.size, rows.size)
        assertTrue(rows.all { (_, status) -> status.statUpdAtMs != null })
    }

    @Test
    fun realGatewayEnvelopesInJson() {
        // HTTP 403 for an unregistered key and 504 for a timed-out query (zscode), JSON because dataType=JSON was asked for.
        val badKey = fixture("keco-error-invalid-key.txt")
        val e = assertFailsWith<KecoApiException> { KecoResponseParser.parse(badKey) }
        assertEquals("30", e.code)
        assertTrue("SERVICE_KEY_IS_NOT_REGISTERED_ERROR" in e.detail)
        assertEquals("05", KecoResponseParser.gatewayError(fixture("keco-info-gangnam-zscode.json"))?.code)
        assertEquals("30", KecoResponseParser.gatewayError(badKey)?.code)
        assertEquals(null, KecoResponseParser.gatewayError(fixture("keco-info-seoul-10.json")))
        assertEquals(null, KecoResponseParser.gatewayError("<html><body>502 Bad Gateway</body></html>"))
        assertEquals(null, KecoResponseParser.gatewayError("Service Unavailable"))
    }

    @Test
    fun flatJsonWithItemArrayAndMixedNumberTypes() {
        val page = KecoResponseParser.parse(
            """{"resultMsg":"NORMAL SERVICE.","totalCount":2,"items":{"item":[
                {"statId":"ME000001","chgerId":"01","lat":37.5,"output":"100","stat":2},
                {"statId":"ME000001","chgerId":"02","lat":"37.5","note":null}
            ]},"pageNo":1,"resultCode":"00","numOfRows":10}""",
        )
        assertEquals(2, page.totalCount)
        assertEquals(10, page.numOfRows)
        assertEquals("37.5", page.items[0]["lat"])
        assertEquals("2", page.items[0]["stat"])
        assertEquals(null, page.items[1]["note"])
    }

    @Test
    fun wrappedJsonWithSingleItemObject() {
        val page = KecoResponseParser.parse(
            """{"response":{"header":{"resultCode":"00","resultMsg":"NORMAL SERVICE."},
               "body":{"items":{"item":{"statId":"ME000001","chgerId":"01"}},"numOfRows":10,"pageNo":1,"totalCount":1}}}""",
        )
        assertEquals(1, page.items.size)
        assertEquals("ME000001", page.items.single()["statId"])
    }

    @Test
    fun emptyItemsAndNoDataAreEmptyPages() {
        assertTrue(KecoResponseParser.parse("""{"resultCode":"00","totalCount":0,"items":""}""").items.isEmpty())
        assertTrue(KecoResponseParser.parse("""{"resultCode":"03","resultMsg":"NODATA_ERROR"}""").items.isEmpty())
    }

    @Test
    fun xmlItems() {
        val page = KecoResponseParser.parse(
            """<?xml version="1.0" encoding="UTF-8"?>
            <response><header><resultCode>00</resultCode><resultMsg>NORMAL SERVICE.</resultMsg></header>
            <body><items>
              <item><statId>ME000001</statId><chgerId>01</chgerId><stat>2</stat></item>
              <item><statId>ME000001</statId><chgerId>02</chgerId><stat>3</stat></item>
            </items><numOfRows>10</numOfRows><pageNo>1</pageNo><totalCount>2</totalCount></body></response>""",
        )
        assertEquals(2, page.totalCount)
        assertEquals(listOf("2", "3"), page.items.map { it["stat"] })
    }

    @Test
    fun serviceKeyErrorEnvelopeBecomesAnException() {
        val e = assertFailsWith<KecoApiException> {
            KecoResponseParser.parse(
                """<OpenAPI_ServiceResponse><cmmMsgHeader><errMsg>SERVICE ERROR</errMsg>
                <returnAuthMsg>SERVICE_KEY_IS_NOT_REGISTERED_ERROR</returnAuthMsg>
                <returnReasonCode>30</returnReasonCode></cmmMsgHeader></OpenAPI_ServiceResponse>""",
            )
        }
        assertEquals("30", e.code)
    }

    @Test
    fun quotaErrorIsRecognised() {
        val e = assertFailsWith<KecoApiException> {
            KecoResponseParser.parse("""{"resultCode":"22","resultMsg":"LIMITED_NUMBER_OF_SERVICE_REQUESTS_EXCEEDS_ERROR"}""")
        }
        assertTrue(e.isQuotaExceeded)
        // The gateway's own envelope with HTTP 200 must not read as an empty page.
        val envelope = assertFailsWith<KecoApiException> {
            KecoResponseParser.parse(
                """{"OpenAPI_ServiceResponse":{"cmmMsgHeader":{"errMsg":"LIMITED_NUMBER_OF_SERVICE_REQUESTS_EXCEEDS_ERROR","returnReasonCode":"22"}}}""",
            )
        }
        assertTrue(envelope.isQuotaExceeded)
    }

    @Test
    fun doctypeAndGarbageAreRejected() {
        assertFailsWith<KecoApiException> {
            KecoResponseParser.parse("""<!DOCTYPE r [<!ENTITY x SYSTEM "file:///etc/passwd">]><response>&x;</response>""")
        }
        assertFailsWith<KecoApiException> { KecoResponseParser.parse("Service Unavailable") }
        assertFailsWith<KecoApiException> { KecoResponseParser.parse("  ") }
    }
}
