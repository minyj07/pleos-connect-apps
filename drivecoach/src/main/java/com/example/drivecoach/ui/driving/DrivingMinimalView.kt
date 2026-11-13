package com.example.drivecoach.ui.driving

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.drivecoach.record.RecorderState
import com.example.drivecoach.ui.MainViewModel
import com.example.drivecoach.ui.components.CoachIconView
import com.example.drivecoach.ui.components.CoachIcons
import com.example.drivecoach.ui.components.T
import com.example.drivecoach.ui.theme.CoachTheme
import kotlinx.coroutines.delay

/**
 * Shown while CarUxRestrictions apply: static, large, two lines at most. No animation, no lists,
 * no numbers that invite a glance (no speed, no live event counter). The design has no screen for it; it
 * uses the design's page-title type, the tinted icon circle of the coaching tiles and the 주행 기록 icon.
 */
@Composable
fun DrivingMinimalView(vm: MainViewModel) {
    val c = CoachTheme.colors
    val recorder by vm.recorder.collectAsStateWithLifecycle()
    val recording = recorder as? RecorderState.Recording
    val minutes by produceState(initialValue = 0L, recording) {
        while (true) {
            value = recording?.let { (System.currentTimeMillis() - it.sinceEpochMs) / 60_000 } ?: 0L
            delay(30_000)
        }
    }
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterVertically),
    ) {
        Box(Modifier.size(160.dp).background(c.tint, CircleShape), contentAlignment = Alignment.Center) {
            CoachIconView(CoachIcons.Trips, 76.dp, c.acc)
        }
        T(
            if (recording != null) "주행 기록 중 · ${minutes}분" else "주행 중",
            50,
            weight = FontWeight.W700,
            spacing = -1f,
            nowrap = true,
        )
        T("정차 후 리포트를 확인할 수 있어요", 26, color = c.ink2, align = TextAlign.Center)
    }
}
