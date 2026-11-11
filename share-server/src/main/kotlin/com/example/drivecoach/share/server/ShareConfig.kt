package com.example.drivecoach.share.server

/**
 * Server settings from environment variables (no config file). Defaults suit a local run next to the
 * emulator: the car uploads to http://10.0.2.2:8090, and the phone needs an https address in front
 * (WebCrypto only works on https or localhost) — see docs/DRIVECOACH.md "폰으로 보기".
 */
data class ShareConfig(
    val host: String = "127.0.0.1",
    val port: Int = 8090,
    val dataDir: String = "data",
    /** How long a link lives. The car shows the resulting date, so it is the same for every share. */
    val ttlHours: Long = 168,
    /** A trip report is 10–40 KB; anything far above that is not a drive coach share. */
    val maxBytes: Int = 256 * 1024,
    /** Disk budget for all live shares together; uploads beyond it get 507. */
    val quotaBytes: Long = 512L * 1024 * 1024,
    /** Per client address. Behind a local tunnel every request comes from 127.0.0.1, so keep these adjustable. */
    val createPerHour: Int = 30,
    val readPerMinute: Int = 120,
    /** Behind the reverse proxy: take the client address from its X-Forwarded-For entry. */
    val trustProxy: Boolean = false,
    /**
     * The https address phones reach this server at (e.g. a demo tunnel), told to the car with each upload.
     * Only debug/staging car builds use it; release builds put their own fixed address into the QR.
     */
    val publicBaseUrl: String? = null,
) {
    val ttlMs: Long get() = ttlHours * 3_600_000L

    companion object {
        fun fromEnv(env: Map<String, String> = System.getenv()): ShareConfig {
            val d = ShareConfig()
            return ShareConfig(
                host = env["HOST"]?.trim()?.takeIf { it.isNotEmpty() } ?: d.host,
                port = env["PORT"]?.toIntOrNull() ?: d.port,
                dataDir = env["DATA_DIR"]?.trim()?.takeIf { it.isNotEmpty() } ?: d.dataDir,
                ttlHours = env["TTL_HOURS"]?.toLongOrNull()?.coerceIn(1, 30 * 24) ?: d.ttlHours,
                maxBytes = env["MAX_BYTES"]?.toIntOrNull()?.coerceIn(1024, 4 * 1024 * 1024) ?: d.maxBytes,
                quotaBytes = env["QUOTA_BYTES"]?.toLongOrNull() ?: d.quotaBytes,
                createPerHour = env["CREATE_PER_HOUR"]?.toIntOrNull() ?: d.createPerHour,
                readPerMinute = env["READ_PER_MINUTE"]?.toIntOrNull() ?: d.readPerMinute,
                trustProxy = flag(env["TRUST_PROXY"]),
                publicBaseUrl = env["PUBLIC_BASE_URL"]?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() }?.also {
                    require(Regex("^https?://[^/?#\\s]+(/[^?#\\s]*)?$").matches(it)) { "PUBLIC_BASE_URL must be an http(s) address without query or fragment" }
                },
            )
        }

        private fun flag(v: String?) = v?.trim()?.lowercase() in setOf("1", "true", "yes")
    }
}
