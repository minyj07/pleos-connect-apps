package com.example.pleosev.server.sync

import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

/** data.go.kr calls per KST day (the dev account allows 10,000). */
class ApiBudget(val dailyLimit: Int, private val clock: Clock) {

    private var day: LocalDate = today()
    private var used = 0

    @Synchronized
    fun record() {
        roll()
        used++
    }

    @Synchronized
    fun usedToday(): Int {
        roll()
        return used
    }

    /** The API itself said the quota is gone (code 22): stop calling until KST midnight. */
    @Synchronized
    fun markExhausted() {
        roll()
        used = maxOf(used, dailyLimit)
    }

    fun fraction(): Double = usedToday().toDouble() / dailyLimit.coerceAtLeast(1)

    fun exhausted(): Boolean = usedToday() >= dailyLimit

    /** Above 95 % of the budget the status poll runs at half speed. */
    fun pollIntervalMultiplier(): Long = if (fraction() >= 0.95) 2 else 1

    private fun roll() {
        val today = today()
        if (today != day) {
            day = today
            used = 0
        }
    }

    private fun today(): LocalDate = LocalDate.now(clock.withZone(KST))

    private companion object {
        val KST: ZoneId = ZoneId.of("Asia/Seoul")
    }
}
