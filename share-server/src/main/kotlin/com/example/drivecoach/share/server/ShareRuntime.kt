package com.example.drivecoach.share.server

import io.ktor.server.application.Application
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.security.SecureRandom
import java.time.Clock
import java.util.Base64
import kotlin.time.Duration.Companion.minutes

/** Everything the routes need; tests build one over a temp directory and a movable clock. */
class ShareRuntime(
    val config: ShareConfig,
    val clock: Clock = Clock.systemUTC(),
    dataDir: Path = Path.of(config.dataDir, "shares"),
) {
    val store = BlobStore(dataDir, clock, config.quotaBytes)
    private val random = SecureRandom()
    private val log = LoggerFactory.getLogger("ShareServer")

    /** base64url of [bytes] random bytes: 16 → 22-character id, 32 → 43-character delete token. */
    fun randomToken(bytes: Int): String = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(bytes).also(random::nextBytes))

    /** Expired shares are also dropped when read; this keeps unread ones from piling up. */
    fun start(app: Application) {
        app.launch(Dispatchers.IO) {
            while (currentCoroutineContext().isActive) {
                delay(SWEEP_EVERY)
                runCatching { store.sweep() }
                    .onSuccess { if (it > 0) log.info("removed {} expired shares", it) }
                    .onFailure { log.warn("sweep failed: {}", it.message) }
            }
        }
    }

    private companion object {
        val SWEEP_EVERY = 10.minutes
    }
}
