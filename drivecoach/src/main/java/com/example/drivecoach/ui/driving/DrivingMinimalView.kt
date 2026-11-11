package com.example.drivecoach.ui.driving

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.drivecoach.record.RecorderState
import com.example.drivecoach.ui.MainViewModel
import kotlinx.coroutines.delay

/**
 * Shown while CarUxRestrictions apply: static, large, two lines at most. No animation, no lists,
 * no numbers that invite a glance (no speed, no live event counter).
 */
@Composable
fun DrivingMinimalView(vm: MainViewModel) {
    val recorder by vm.recorder.collectAsStateWithLifecycle()
    val recording = recorder as? RecorderState.Recording
    val minutes by produceState(initialValue = 0L, recording) {
        while (true) {
            value = recording?.let { (System.currentTimeMillis() - it.sinceEpochMs) / 60_000 } ?: 0L
            delay(30_000)
        }
    }
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
    ) {
        Icon(Icons.Filled.DirectionsCar, contentDescription = null, modifier = Modifier.size(96.dp), tint = MaterialTheme.colorScheme.primary)
        Text(
            if (recording != null) "주행 기록 중 · ${minutes}분" else "주행 중",
            style = MaterialTheme.typography.displaySmall,
        )
        Text("정차 후 리포트를 확인할 수 있어요", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
