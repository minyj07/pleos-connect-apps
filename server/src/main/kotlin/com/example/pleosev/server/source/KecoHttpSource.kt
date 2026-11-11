package com.example.pleosev.server.source

import com.example.pleosev.server.sync.ApiBudget
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.encodeURLParameter
import io.ktor.http.encodeURLQueryComponent
import io.ktor.http.isSuccess
import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.URLEncoder
import kotlin.coroutines.cancellation.CancellationException

/**
 * data.go.kr client for `B552584/EvCharger`. [serviceKey] must be the **Decoding** key: Ktor URL-encodes
 * query parameters once, so passing the Encoding key would double-encode it (SERVICE_KEY_IS_NOT_REGISTERED).
 * Every request is counted in [budget]; the key never appears in thrown messages or logs.
 */
class KecoHttpSource(
    private val serviceKey: String,
    private val budget: ApiBudget,
    private val client: HttpClient = defaultClient(),
    private val baseUrl: String = BASE_URL,
) : ChargerSource, AutoCloseable {

    private val log = LoggerFactory.getLogger(KecoHttpSource::class.java)

    override val name = "keco"

    override suspend fun chargerInfo(zcode: String, pageNo: Int, numOfRows: Int): KecoPage =
        call("getChargerInfo", listOf("pageNo" to "$pageNo", "numOfRows" to "$numOfRows", "zcode" to zcode))

    override suspend fun chargerStatus(periodMin: Int, pageNo: Int, numOfRows: Int, zcode: String?): KecoPage =
        call(
            "getChargerStatus",
            listOfNotNull("pageNo" to "$pageNo", "numOfRows" to "$numOfRows", "period" to "$periodMin", zcode?.let { "zcode" to it }),
        )

    private suspend fun call(operation: String, params: List<Pair<String, String>>): KecoPage {
        var attempt = 0
        while (true) {
            attempt++
            budget.record()
            val retryReason = try {
                val response = client.get("$baseUrl/$operation") {
                    parameter("serviceKey", serviceKey)
                    params.forEach { (name, value) -> parameter(name, value) }
                    parameter("dataType", "JSON")
                }
                val body = response.bodyAsText()
                if (response.status.isSuccess()) return KecoResponseParser.parse(body)
                // The gateway's envelope names the cause: 30 key not registered (403), 22 quota, 05 timeout (504).
                val gateway = KecoResponseParser.gatewayError(body)
                if (response.status.value < 500 || attempt >= MAX_ATTEMPTS) {
                    throw if (gateway != null) {
                        KecoApiException(gateway.code, redact(gateway.detail))
                    } else {
                        KecoApiException("HTTP_${response.status.value}", redact(body.take(200)))
                    }
                }
                "HTTP ${response.status.value}" + (gateway?.let { " (${it.code})" } ?: "")
            } catch (e: CancellationException) {
                throw e
            } catch (e: KecoApiException) {
                throw e // service errors (bad key, quota, bad parameters) are not retried
            } catch (e: Exception) {
                if (attempt >= MAX_ATTEMPTS) {
                    throw IOException("KECO $operation failed after $attempt attempts: ${redact(e.message.orEmpty())}")
                }
                "${e::class.simpleName}: ${redact(e.message.orEmpty())}"
            }
            // Gateway timeouts are common (14 retries in the first real full sync of 59 pages), so say which call.
            log.warn(
                "KECO {} {} attempt {}/{} failed: {}",
                operation, params.joinToString(" ") { (name, value) -> "$name=$value" }, attempt, MAX_ATTEMPTS, retryReason,
            )
            delay(RETRY_DELAY_MS * attempt)
        }
    }

    /** Exception messages can contain the request URL: strip the key in every encoding it may appear in. */
    fun redact(text: String): String = listOf(
        serviceKey,
        serviceKey.encodeURLParameter(),
        serviceKey.encodeURLQueryComponent(),
        URLEncoder.encode(serviceKey, Charsets.UTF_8),
    ).fold(text) { acc, form -> acc.replace(form, "<SERVICE_KEY>") }

    override fun close() = client.close()

    companion object {
        const val BASE_URL = "https://apis.data.go.kr/B552584/EvCharger"
        // About one call in five hit a 30 s gateway timeout on 2026-11-04; with 3 attempts a 59-page full sync
        // would fail roughly one time in three, with 5 about one time in seventy.
        private const val MAX_ATTEMPTS = 5
        private const val RETRY_DELAY_MS = 2_000L

        fun defaultClient() = HttpClient(CIO) {
            expectSuccess = false
            install(HttpTimeout) {
                connectTimeoutMillis = 15_000
                socketTimeoutMillis = 60_000
                requestTimeoutMillis = 120_000 // a 9,999-row master page is several MB
            }
        }
    }
}
