package com.example.pleosev.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.pleosev.advisor.background.AdvisorNotifier
import com.example.pleosev.advisor.background.AlertTexts
import com.example.pleosev.appGraph

/**
 * E2E hooks for the background watch, without bringing the app on screen (which would change what it
 * alerts about):
 *   adb shell am broadcast --user current -n com.example.pleosev/.debug.AdvisorDebugReceiver --es cmd status
 *     refresh : new recommendations now
 *     status  : new charger counts now (no position sent; a full refresh when that is not enough)
 *     alert   : pop up the current alert again, bypassing AlertPolicy
 *     dump    : background watch and alert state to logcat (tag AdvisorDebug)
 */
class AdvisorDebugReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val graph = context.appGraph
        val cmd = intent.getStringExtra("cmd")
        when (cmd) {
            "refresh" -> graph.advisor.refresh()
            "status" -> graph.advisor.refreshStatus()
            // Pops up the current alert again, bypassing AlertPolicy (heads-up rendering checks).
            "alert" -> AlertTexts.alert(graph.advisor.state.value)
                ?.let { AdvisorNotifier(context).showAlert(it, headsUp = true, timeoutMs = 10 * 60_000L) }
                ?: Log.w(TAG, "nothing to alert about")
            "dump" -> Unit
            else -> Log.w(TAG, "unknown cmd '$cmd'")
        }
        Log.i(TAG, "$cmd: ${graph.monitor.describe()}")
    }

    private companion object {
        const val TAG = "AdvisorDebug"
    }
}
