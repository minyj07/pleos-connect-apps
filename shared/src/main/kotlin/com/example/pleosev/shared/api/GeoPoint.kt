package com.example.pleosev.shared.api

import kotlinx.serialization.Serializable

/** WGS84 coordinate exchanged between the car app and the advisor server. */
@Serializable
data class GeoPoint(val lat: Double, val lon: Double)
