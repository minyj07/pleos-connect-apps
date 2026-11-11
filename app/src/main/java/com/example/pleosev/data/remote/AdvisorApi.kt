package com.example.pleosev.data.remote

import com.example.pleosev.shared.api.AdvisorJson
import com.example.pleosev.shared.api.ApiError
import com.example.pleosev.shared.api.AppVersionResponse
import com.example.pleosev.shared.api.ChargePrefs
import com.example.pleosev.shared.api.GeoPoint
import com.example.pleosev.shared.api.MatchResponse
import com.example.pleosev.shared.api.RecommendationRequest
import com.example.pleosev.shared.api.RecommendationResponse
import com.example.pleosev.shared.api.StatusRequest
import com.example.pleosev.shared.api.StatusResponse
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.KSerializer
import kotlin.coroutines.cancellation.CancellationException

sealed interface ApiResult<out T> {
    data class Ok<T>(val value: T) : ApiResult<T>

    data class Failure(val kind: Kind, val message: String) : ApiResult<Nothing> {
        enum class Kind { NOT_CONFIGURED, OFFLINE, NOT_READY, SERVER }
    }
}

/** The charging-advisor relay server (:server). Coordinates are rounded by the caller before sending. */
interface AdvisorApi {
    suspend fun recommend(request: RecommendationRequest): ApiResult<RecommendationResponse>

    suspend fun status(statIds: List<String>, prefs: ChargePrefs): ApiResult<StatusResponse>

    suspend fun match(location: GeoPoint, name: String?, kw: Int?): ApiResult<MatchResponse>

    /** Newest release on the Pleos App Market (sends nothing about the car or the driver). */
    suspend fun appVersion(): ApiResult<AppVersionResponse>
}

class KtorAdvisorApi(
    private val baseUrl: String,
    private val client: HttpClient = defaultClient(),
) : AdvisorApi {

    override suspend fun recommend(request: RecommendationRequest) = call(RecommendationResponse.serializer()) {
        client.post("$baseUrl/v1/recommendations") {
            contentType(ContentType.Application.Json)
            setBody(AdvisorJson.encodeToString(RecommendationRequest.serializer(), request))
        }
    }

    override suspend fun status(statIds: List<String>, prefs: ChargePrefs) = call(StatusResponse.serializer()) {
        client.post("$baseUrl/v1/stations/status") {
            contentType(ContentType.Application.Json)
            setBody(AdvisorJson.encodeToString(StatusRequest.serializer(), StatusRequest(statIds, prefs)))
        }
    }

    override suspend fun match(location: GeoPoint, name: String?, kw: Int?) = call(MatchResponse.serializer()) {
        client.get("$baseUrl/v1/stations/match") {
            parameter("lat", location.lat)
            parameter("lon", location.lon)
            name?.let { parameter("name", it) }
            kw?.let { parameter("kw", it) }
        }
    }

    override suspend fun appVersion() = call(AppVersionResponse.serializer()) {
        client.get("$baseUrl/v1/app-version")
    }

    private suspend fun <T> call(serializer: KSerializer<T>, send: suspend () -> HttpResponse): ApiResult<T> {
        if (baseUrl.isBlank()) return ApiResult.Failure(ApiResult.Failure.Kind.NOT_CONFIGURED, "advisor server is not configured")
        return try {
            val response = send()
            val body = response.bodyAsText()
            when {
                response.status.isSuccess() -> ApiResult.Ok(AdvisorJson.decodeFromString(serializer, body))
                response.status.value == 503 -> ApiResult.Failure(ApiResult.Failure.Kind.NOT_READY, errorMessage(body))
                else -> ApiResult.Failure(ApiResult.Failure.Kind.SERVER, "HTTP ${response.status.value} ${errorMessage(body)}")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ApiResult.Failure(ApiResult.Failure.Kind.OFFLINE, e.message ?: e.javaClass.simpleName)
        }
    }

    private fun errorMessage(body: String): String =
        runCatching { AdvisorJson.decodeFromString(ApiError.serializer(), body).error.let { "${it.code}: ${it.message}" } }
            .getOrDefault(body.take(120))

    companion object {
        fun defaultClient() = HttpClient(OkHttp) {
            expectSuccess = false
            install(HttpTimeout) {
                connectTimeoutMillis = 5_000
                requestTimeoutMillis = 15_000
            }
        }
    }
}
