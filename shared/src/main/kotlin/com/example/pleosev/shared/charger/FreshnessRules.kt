package com.example.pleosev.shared.charger

import com.example.pleosev.shared.api.FreshnessLevel

/**
 * Freshness comes from the server's own polling, not from `statUpdDt`: a charger that has been free
 * all day legitimately has an old `statUpdDt`.
 */
object FreshnessRules {
    const val LIVE_MS = 3 * 60_000L
    const val DELAYED_MS = 15 * 60_000L

    /** [coverageOk] is false after a polling gap until the next full sync: some changes may be missing. */
    fun level(serverCheckedAtMs: Long?, nowMs: Long, coverageOk: Boolean): FreshnessLevel {
        if (!coverageOk || serverCheckedAtMs == null) return FreshnessLevel.STALE
        val age = nowMs - serverCheckedAtMs
        return when {
            age <= LIVE_MS -> FreshnessLevel.LIVE
            age <= DELAYED_MS -> FreshnessLevel.DELAYED
            else -> FreshnessLevel.STALE
        }
    }
}
