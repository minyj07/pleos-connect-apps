package com.example.pleosev.shared.api

import kotlin.test.Test
import kotlin.test.assertEquals

class AdvisorJsonTest {

    @Test
    fun requestRoundTripsWithDefaults() {
        val request = RecommendationRequest(
            mode = RecommendMode.ON_ROUTE,
            origin = GeoPoint(37.413, 127.095),
            destination = GeoPoint(35.115, 129.042),
            remainDistanceM = 378_000,
            socPct = 42.0,
        )
        val json = AdvisorJson.encodeToString(RecommendationRequest.serializer(), request)
        assertEquals(request, AdvisorJson.decodeFromString(RecommendationRequest.serializer(), json))
    }

    @Test
    fun unknownFieldsAndMissingOptionalsAreTolerated() {
        val json = """{"mode":"NEARBY","origin":{"lat":37.4,"lon":127.1},"futureField":true}"""
        val request = AdvisorJson.decodeFromString(RecommendationRequest.serializer(), json)
        assertEquals(RecommendMode.NEARBY, request.mode)
        assertEquals(ChargePrefs(), request.prefs)
        assertEquals(null, request.destination)
    }
}
