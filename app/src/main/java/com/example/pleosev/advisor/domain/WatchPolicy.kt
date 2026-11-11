package com.example.pleosev.advisor.domain

data class WatchInput(
    val enabled: Boolean,  // location consent, background watching on, not paused by the driver
    val charging: Boolean,
    val busy: Boolean,     // a route is guided, a charging need is open, or the app is on screen
    val guiding: Boolean = false,
)

/**
 * Whether the background watch (a `specialUse` foreground service) should run. Without it the process is
 * frozen 1–2 minutes after the app leaves the screen (Phase 0 spike). It runs while there is something to
 * watch and for [idleGraceMs] after that, so a route started right after opening the app, arriving or
 * cancelling is still seen. Not while charging, except at a charging stop on a guided trip: the trip goes on
 * after it, and the service cannot be started again while the navi covers the app (Android 12+).
 */
class WatchPolicy(private val idleGraceMs: Long = 10 * 60_000L) {

    private var lastBusyAtMs: Long? = null

    fun shouldWatch(input: WatchInput, nowMs: Long): Boolean {
        if (!input.enabled || (input.charging && !input.guiding)) {
            lastBusyAtMs = null
            return false
        }
        if (input.busy) lastBusyAtMs = nowMs
        val last = lastBusyAtMs ?: return false
        return nowMs - last < idleGraceMs
    }
}
