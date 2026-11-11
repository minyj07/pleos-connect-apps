package com.example.pleosev.debug

import ai.pleos.playground.navi.constants.WaypointIndex
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Debug-only screen for the Phase 0 NaviHelper spike. Buttons run [NaviProbe] actions; the same
 * actions can be sent from adb (comma-separated):
 *
 *   adb shell am start -n com.example.pleosev/.debug.NaviProbeActivity --es probe init,getall
 */
class NaviProbeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                ProbeScreen(onAction = { NaviProbe.run(this, it) })
            }
        }
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        NaviProbe.log("activity onStart")
    }

    override fun onStop() {
        NaviProbe.log("activity onStop")
        super.onStop()
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val kind = intent.getStringExtra(EXTRA_CUSTOM)
        if (kind != null) {
            // adb shell am start ... --es custom wp --es name 괴산휴게소 --es lat 36.83 --es lon 127.95 [--es poiId X] [--es index SECOND]
            intent.removeExtra(EXTRA_CUSTOM)
            val lat = intent.getStringExtra("lat")?.toDoubleOrNull()
            val lon = intent.getStringExtra("lon")?.toDoubleOrNull()
            if (lat == null || lon == null) {
                NaviProbe.log("custom: lat / lon extras missing")
            } else {
                NaviProbe.custom(
                    kind = kind,
                    name = intent.getStringExtra("name") ?: "프로브 지점",
                    lat = lat,
                    lon = lon,
                    poiId = intent.getStringExtra("poiId").orEmpty(),
                    poiProvider = intent.getStringExtra("poiProvider").orEmpty(),
                    index = if (intent.getStringExtra("index") == "SECOND") WaypointIndex.SECOND else WaypointIndex.FIRST,
                )
            }
        }
        val actions = intent.getStringExtra(EXTRA_PROBE) ?: return
        intent.removeExtra(EXTRA_PROBE)
        actions.split(',').map { it.trim() }.filter { it.isNotEmpty() }.forEach { NaviProbe.run(this, it) }
    }

    private companion object {
        const val EXTRA_PROBE = "probe"
        const val EXTRA_CUSTOM = "custom"
    }
}

@Composable
private fun ProbeScreen(onAction: (String) -> Unit) {
    val lines by NaviProbe.lines.collectAsStateWithLifecycle()
    Surface(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize().padding(16.dp)) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(150.dp),
                modifier = Modifier.weight(0.4f).fillMaxHeight(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(NaviProbe.ACTIONS) { action ->
                    Button(onClick = { onAction(action) }) { Text(action) }
                }
            }
            Spacer(Modifier.width(16.dp))
            LazyColumn(Modifier.weight(0.6f).fillMaxHeight()) {
                items(lines) { line ->
                    Text(line, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}
