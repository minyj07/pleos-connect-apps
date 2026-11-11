package com.example.pleosev.server

import java.nio.file.Path
import java.nio.file.Paths

/** Runtime settings. [fromEnv] reads them from environment variables (see README). */
data class ServerConfig(
    val host: String = "127.0.0.1",
    val port: Int = 8080,
    val serviceKey: String? = null,         // DATA_GO_KR_SERVICE_KEY: the data.go.kr "Decoding" key
    val fakeSource: Boolean = false,        // FAKE_SOURCE=1: bundled synthetic stations, no quota used
    val dataDir: Path = Paths.get("data"),  // snapshot location (server/data when started via :server:run)
    val pollIntervalSec: Long = 90,
    val statusPeriodMin: Int = 5,
    val masterSyncHourKst: Int = 4,
    val dailyCallBudget: Int = 10_000,      // data.go.kr dev account
    val rateLimitPerMinute: Int = 120,      // per client address
    val pageSize: Int = 9_999,              // KECO maximum numOfRows
    val zcodes: List<String> = ALL_ZCODES,
    val statusPerZcode: Boolean = false,    // getChargerStatus works without zcode (spike 0-4); per-zcode polling stays an option
    val trustProxy: Boolean = false,        // TRUST_PROXY=1: behind one reverse proxy (Caddy) that appends X-Forwarded-For
    val appLatestVersionCode: Int? = null,  // APP_LATEST_VERSION_CODE: newest market release, for the app's update prompt
    val appLatestVersionName: String? = null,
) {
    val sourceName: String get() = if (fakeSource) "fake" else "keco"

    companion object {
        /**
         * 17 시도. 강원 and 전북 only by their 특별자치도 codes 51 and 52: in spike 0-4 (2026-11-04) the old 42 timed out
         * (503 after 57 s, which would abort every full sync) and 45 returned no rows.
         */
        val ALL_ZCODES = listOf(
            "11", "26", "27", "28", "29", "30", "31", "36", "41", "43", "44", "46", "47", "48", "50", "51", "52",
        )

        fun fromEnv(env: Map<String, String> = System.getenv()): ServerConfig {
            val fake = flag(env["FAKE_SOURCE"])
            val key = env["DATA_GO_KR_SERVICE_KEY"]?.trim()?.takeIf { it.isNotEmpty() }
            require(fake || key != null) {
                "DATA_GO_KR_SERVICE_KEY is not set (data.go.kr Decoding key). Set FAKE_SOURCE=1 to run with bundled synthetic stations."
            }
            return ServerConfig(
                host = env["HOST"] ?: "127.0.0.1",
                port = env["PORT"]?.toIntOrNull() ?: 8080,
                serviceKey = key,
                fakeSource = fake,
                dataDir = Paths.get(env["DATA_DIR"] ?: "data"),
                pollIntervalSec = env["POLL_INTERVAL_SEC"]?.toLongOrNull() ?: 90,
                dailyCallBudget = env["DAILY_CALL_BUDGET"]?.toIntOrNull() ?: 10_000,
                statusPerZcode = flag(env["STATUS_PER_ZCODE"]),
                trustProxy = flag(env["TRUST_PROXY"]),
                appLatestVersionCode = env["APP_LATEST_VERSION_CODE"]?.trim()?.toIntOrNull(),
                appLatestVersionName = env["APP_LATEST_VERSION_NAME"]?.trim()?.takeIf { it.isNotEmpty() },
            )
        }

        private fun flag(value: String?): Boolean = value?.trim()?.lowercase() in setOf("1", "true", "yes")
    }
}
