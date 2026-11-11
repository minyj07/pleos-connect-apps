package com.example.pleosev.server.source

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Parses KECO EvCharger responses without assuming one exact shape, because data.go.kr APIs vary:
 *  - JSON, flat `{resultCode, totalCount, items: {item: [...]}}` (what KECO sends, spike 0-4) or wrapped `{response: {header, body}}`
 *  - `item` as an array or (single row) as an object; `items` as "" when there are no rows
 *  - XML `<response><header/><body><items><item>…`
 *  - the gateway's `OpenAPI_ServiceResponse` error envelope, as JSON or XML
 * Field values are kept as strings; KecoNormalizer interprets them.
 */
object KecoResponseParser {

    private val SUCCESS_CODES = setOf("00", "0", "INFO-000")
    private const val NO_DATA = "03" // NODATA_ERROR: an empty page, not a failure
    private const val GATEWAY_ENVELOPE = "OpenAPI_ServiceResponse"
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(body: String): KecoPage {
        val text = body.trim().removePrefix("﻿")
        if (text.isEmpty()) throw KecoApiException("EMPTY", "empty response body")
        return if (text.startsWith("<")) parseXml(text) else parseJson(text)
    }

    /**
     * The data.go.kr gateway's own error in [body], or null when [body] is something else. The gateway sends its
     * envelope with any HTTP status, e.g. 403 + 30 (key not registered) or 504 + 05 (timeout), as JSON when
     * dataType=JSON was asked for.
     */
    fun gatewayError(body: String): KecoApiException? {
        val text = body.trim().removePrefix("﻿")
        return if (text.startsWith("<")) {
            runCatching { secureBuilder().parse(InputSource(StringReader(text))).documentElement }.getOrNull()
                ?.takeIf { it.tagName == GATEWAY_ENVELOPE }
                ?.let { xmlGatewayError(it) }
        } else {
            runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull()
                ?.let { it[GATEWAY_ENVELOPE] as? JsonObject }
                ?.let { jsonGatewayError(it) }
        }
    }

    // ---- JSON ----

    private fun parseJson(text: String): KecoPage {
        val root = try {
            json.parseToJsonElement(text) as? JsonObject
        } catch (e: Exception) {
            null
        } ?: throw KecoApiException("PARSE", "not a JSON object: ${text.take(120)}")
        // Without this, a quota envelope sent with HTTP 200 would read as an empty page.
        (root[GATEWAY_ENVELOPE] as? JsonObject)?.let { throw jsonGatewayError(it) }
        val response = root["response"] as? JsonObject
        val header = response?.get("header") as? JsonObject
        val body = response?.get("body") as? JsonObject ?: response ?: root
        val code = (header ?: root).text("resultCode") ?: body.text("resultCode")
        if (code == NO_DATA) return KecoPage(0, 1, 0, emptyList())
        if (code != null && code !in SUCCESS_CODES) {
            throw KecoApiException(code, (header ?: root).text("resultMsg") ?: body.text("resultMsg").orEmpty())
        }
        val items = itemsOf(body["items"])
        return KecoPage(
            totalCount = body.text("totalCount")?.toIntOrNull() ?: items.size,
            pageNo = body.text("pageNo")?.toIntOrNull() ?: 1,
            numOfRows = body.text("numOfRows")?.toIntOrNull() ?: items.size,
            items = items,
        )
    }

    private fun jsonGatewayError(envelope: JsonObject): KecoApiException {
        val header = envelope["cmmMsgHeader"] as? JsonObject ?: envelope
        return envelopeError(header.text("returnReasonCode"), header.text("errMsg"), header.text("returnAuthMsg"))
    }

    private fun itemsOf(element: JsonElement?): List<Map<String, String>> = when (element) {
        is JsonObject -> when (val item = element["item"]) {
            is JsonArray -> item.mapNotNull { (it as? JsonObject)?.toStringMap() }
            is JsonObject -> listOf(item.toStringMap())
            else -> emptyList()
        }
        is JsonArray -> element.mapNotNull { (it as? JsonObject)?.toStringMap() }
        else -> emptyList() // absent, null or "" when the page has no rows
    }

    private fun JsonObject.toStringMap(): Map<String, String> = buildMap {
        for ((key, value) in this@toStringMap) {
            if (value is JsonPrimitive && value !is JsonNull) put(key, value.content)
        }
    }

    private fun JsonObject.text(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull?.trim()

    // ---- XML ----

    private fun parseXml(text: String): KecoPage {
        val root = try {
            secureBuilder().parse(InputSource(StringReader(text))).documentElement
        } catch (e: Exception) {
            throw KecoApiException("PARSE", "not valid XML: ${text.take(120)}")
        }
        if (root.tagName == GATEWAY_ENVELOPE) throw xmlGatewayError(root)
        val code = root.firstText("resultCode")
        if (code == NO_DATA) return KecoPage(0, 1, 0, emptyList())
        if (code != null && code !in SUCCESS_CODES) throw KecoApiException(code, root.firstText("resultMsg").orEmpty())
        val nodes = root.getElementsByTagName("item")
        val items = (0 until nodes.length).mapNotNull { i ->
            (nodes.item(i) as? Element)?.childElements()?.associate { it.tagName to it.textContent.trim() }
        }
        return KecoPage(
            totalCount = root.firstText("totalCount")?.toIntOrNull() ?: items.size,
            pageNo = root.firstText("pageNo")?.toIntOrNull() ?: 1,
            numOfRows = root.firstText("numOfRows")?.toIntOrNull() ?: items.size,
            items = items,
        )
    }

    private fun xmlGatewayError(root: Element) =
        envelopeError(root.firstText("returnReasonCode"), root.firstText("errMsg"), root.firstText("returnAuthMsg"))

    private fun Element.firstText(tag: String): String? {
        val nodes = getElementsByTagName(tag)
        return if (nodes.length == 0) null else nodes.item(0).textContent?.trim()
    }

    private fun Element.childElements(): List<Element> =
        (0 until childNodes.length).mapNotNull { childNodes.item(it) as? Element }

    /** errMsg is the constant (SERVICE_KEY_IS_NOT_REGISTERED_ERROR) in JSON but "SERVICE ERROR" in XML, so both texts are kept. */
    private fun envelopeError(code: String?, vararg messages: String?) = KecoApiException(
        code ?: "ERROR",
        messages.filterNotNull().filter { it.isNotBlank() }.distinct().joinToString(" · ").ifEmpty { "service error" },
    )

    /** No DTDs or external entities: the response comes from the network. */
    private fun secureBuilder(): DocumentBuilder = DocumentBuilderFactory.newInstance().apply {
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
        isXIncludeAware = false
        isExpandEntityReferences = false
    }.newDocumentBuilder()
}
