package com.example.pleosev.shared.charger

import com.example.pleosev.shared.api.ChargerCounts
import com.example.pleosev.shared.api.ChargerState

/**
 * KECO `stat` codes: 1 통신이상, 2 충전대기, 3 충전중, 4 운영중지, 5 점검중, 9 상태미확인
 * (confirm against the 활용가이드 in spike 0-4).
 */
object ChargerStates {

    /** A DC session lasting longer than this is not plausible: treat "charging" as unknown. */
    const val STALE_CHARGING_MS = 3 * 60 * 60 * 1000L

    /**
     * [sessionStartAtMs] is KECO `nowTsdt` (start of the current session). The session length is measured from
     * it: many operators stamp `statUpdDt` again on every report while the state stays the same (spike 0-4), so
     * a charger stuck at "charging" for a day would otherwise look busy forever.
     */
    fun of(stat: String?, statUpdAtMs: Long?, nowMs: Long, sessionStartAtMs: Long? = null): ChargerState = when (stat?.trim()) {
        "2" -> ChargerState.AVAILABLE
        "3" -> {
            val since = sessionStartAtMs ?: statUpdAtMs
            if (since != null && nowMs - since > STALE_CHARGING_MS) ChargerState.CHARGING_STALE else ChargerState.CHARGING
        }
        "4", "5" -> ChargerState.OUT_OF_SERVICE
        else -> ChargerState.UNKNOWN
    }

    fun count(states: Collection<ChargerState>) = ChargerCounts(
        total = states.size,
        available = states.count { it == ChargerState.AVAILABLE },
        charging = states.count { it == ChargerState.CHARGING },
        unknown = states.count { it == ChargerState.UNKNOWN || it == ChargerState.CHARGING_STALE },
        outOfService = states.count { it == ChargerState.OUT_OF_SERVICE },
    )
}
