package com.example.pleosev.shared.api

import kotlinx.serialization.Serializable

/**
 * `GET /v1/app-version`: the newest app release on the Pleos App Market. The market has no automatic
 * updates, so the app checks this at start and offers the market page (Pleos app update guide).
 */
@Serializable
data class AppVersionResponse(
    val latestVersionCode: Int? = null,  // null: not configured on the server, the app does not ask
    val latestVersionName: String? = null,
)
