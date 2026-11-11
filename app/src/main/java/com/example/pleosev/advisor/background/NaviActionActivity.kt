package com.example.pleosev.advisor.background

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.example.pleosev.appGraph
import kotlinx.coroutines.launch

/**
 * "Find in the navi" from an alert. The navi runs the search but stays in the background (Phase 0 spike),
 * so the app has to bring it forward, and since Android 12 a notification's broadcast may not start
 * activities (notification trampolines). The alert opens this invisible activity instead: it hands the
 * search to the navi, raises it and closes.
 */
class NaviActionActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val station = AlertExtras.station(intent)
        val kind = AlertExtras.kind(intent)
        if (savedInstanceState != null || station == null || kind == null) {
            finish()
            return
        }
        lifecycleScope.launch {
            appGraph.monitor.runAlertAction(station, kind)
            finish()
        }
    }
}
