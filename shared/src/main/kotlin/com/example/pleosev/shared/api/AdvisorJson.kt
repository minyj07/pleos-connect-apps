package com.example.pleosev.shared.api

import kotlinx.serialization.json.Json

/**
 * JSON settings for the advisor API, shared by the server and the car app.
 * Unknown keys are ignored so either side can add fields without breaking the other.
 */
val AdvisorJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}
