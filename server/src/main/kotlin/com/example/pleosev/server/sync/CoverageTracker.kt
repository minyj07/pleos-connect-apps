package com.example.pleosev.server.sync

/**
 * Whether the status deltas form an unbroken chain since the last full sync.
 *
 * A poll started at t with period p returns changes in [t - p, t]. If that window does not reach back
 * to the previous successful poll (minus a safety margin for clock skew), some changes may have been
 * missed: coverage is broken and every station is reported STALE until the next full sync, whose
 * getChargerInfo rows carry each charger's current `stat`.
 */
class CoverageTracker {

    @Volatile
    var lastSuccessAtMs: Long? = null
        private set

    @Volatile
    var coverageOkSinceMs: Long? = null
        private set

    val coverageOk: Boolean get() = coverageOkSinceMs != null

    /** A full sync started at [atMs]: every charger's status is known as of then. */
    @Synchronized
    fun baseline(atMs: Long) {
        lastSuccessAtMs = atMs
        coverageOkSinceMs = atMs
    }

    /** Period the poll starting at [nowMs] must request to stay continuous, or null if even the maximum is too short. */
    fun periodFor(nowMs: Long, defaultPeriodMin: Int): Int? {
        val last = lastSuccessAtMs ?: return null
        val needed = nowMs - last + SAFETY_MS
        return when {
            needed <= defaultPeriodMin * MINUTE_MS -> defaultPeriodMin
            needed <= MAX_PERIOD_MIN * MINUTE_MS -> MAX_PERIOD_MIN
            else -> null
        }
    }

    @Synchronized
    fun recordPoll(startedAtMs: Long, periodMin: Int) {
        val last = lastSuccessAtMs
        val continuous = last != null && startedAtMs - periodMin * MINUTE_MS + SAFETY_MS <= last
        if (!continuous) coverageOkSinceMs = null
        lastSuccessAtMs = startedAtMs
    }

    @Synchronized
    fun markBroken() {
        coverageOkSinceMs = null
    }

    companion object {
        const val MAX_PERIOD_MIN = 10 // KECO getChargerStatus accepts 1..10
        const val SAFETY_MS = 30_000L
        private const val MINUTE_MS = 60_000L

        /** How far back a poll made right now can still reach, with room for the poll itself to start. */
        const val CATCH_UP_WINDOW_MS = MAX_PERIOD_MIN * MINUTE_MS - 2 * SAFETY_MS
    }
}
