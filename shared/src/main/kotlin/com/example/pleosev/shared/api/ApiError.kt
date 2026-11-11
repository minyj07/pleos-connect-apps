package com.example.pleosev.shared.api

import kotlinx.serialization.Serializable

/** Error body of every non-2xx advisor response: `{"error":{"code":"…","message":"…"}}`. */
@Serializable
data class ApiError(val error: Body) {
    @Serializable
    data class Body(val code: String, val message: String)

    companion object {
        fun of(code: String, message: String) = ApiError(Body(code, message))
    }
}
