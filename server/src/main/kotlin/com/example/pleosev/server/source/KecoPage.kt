package com.example.pleosev.server.source

/** One page of a KECO response; items are flattened to string maps (KECO mixes strings and numbers). */
data class KecoPage(
    val totalCount: Int,
    val pageNo: Int,
    val numOfRows: Int,
    val items: List<Map<String, String>>,
)

/** data.go.kr / KECO service error: a resultCode other than 00, or the gateway's OpenAPI_ServiceResponse envelope. */
class KecoApiException(val code: String, val detail: String) : Exception("KECO $code: $detail") {
    /** 22 = LIMITED_NUMBER_OF_SERVICE_REQUESTS_EXCEEDS_ERROR: the daily quota is used up. */
    val isQuotaExceeded: Boolean get() = code == "22"
}
