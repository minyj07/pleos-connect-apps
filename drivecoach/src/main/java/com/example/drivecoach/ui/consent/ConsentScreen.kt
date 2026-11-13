package com.example.drivecoach.ui.consent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.drivecoach.ui.components.BodyText
import com.example.drivecoach.ui.components.CoachCard
import com.example.drivecoach.ui.components.OutlinePill
import com.example.drivecoach.ui.components.PageTitle
import com.example.drivecoach.ui.components.PrimaryPill
import com.example.drivecoach.ui.components.T
import com.example.drivecoach.ui.theme.CoachTheme

/**
 * Explicit consent before anything is recorded (store privacy review): items, purpose, retention,
 * where data lives and the right to refuse. Only the button counts as consent; Back does not.
 * The two large buttons stay pinned at the bottom while the text scrolls. The design has no screen for
 * this page; it uses the design's page title, cards, body text and pills.
 */
@Composable
fun ConsentScreen(onAccept: () -> Unit, onDecline: () -> Unit) {
    val c = CoachTheme.colors
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 56.dp, top = 48.dp, end = 64.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ConsentText()
        }
        // The same 2 px line the navigation rail has, here above the pinned buttons.
        Box(
            Modifier
                .fillMaxWidth()
                .drawBehind { drawRect(c.line, Offset.Zero, Size(size.width, 2.dp.toPx())) }
                .padding(start = 56.dp, top = 26.dp, end = 64.dp, bottom = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(Modifier.widthIn(max = 900.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                PrimaryPill("동의하고 시작", onAccept, Modifier.weight(1f), large = true)
                OutlinePill("동의하지 않음 (주차 연습만)", onDecline, Modifier.weight(1f), large = true)
            }
        }
    }
}

@Composable
private fun ConsentText() {
    Column(Modifier.widthIn(max = 900.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        PageTitle("드라이브 코치")
        BodyText("평소 운전 습관을 모아 주행이 끝나면 리포트와 코칭을 보여 드려요. 시작하기 전에 아래 내용을 확인해 주세요.")
        ConsentItem("수집 항목", "차량 속도, 기어(P·R·N·D), 브레이크 페달을 밟았는지 여부, 핸들(조향) 각도, 크루즈 작동 여부, 주행 시간과 거리, 누적 주행거리. 위치(GPS)는 수집하지 않아요.")
        ConsentItem("이용 목적", "급가속·급제동 같은 운전 습관 분석, 주행 리포트와 코칭 문구, 주차 습관 분석에만 써요. 광고나 마케팅에 쓰지 않아요.")
        ConsentItem(
            "보관 기간과 장소",
            "차량 안에만 암호화해서 저장해요. 기본 보관 기간은 90일이며 설정에서 30일·1년으로 바꿀 수 있어요. " +
                "직접 '폰으로 보기'를 누르고 따로 동의했을 때만 그 리포트의 암호화된 사본을 서버에 7일 올리고, 그 밖에는 외부로 보내지 않아요.",
        )
        ConsentItem("거부할 권리", "동의하지 않아도 주차 연습은 그대로 쓸 수 있어요. 동의는 설정에서 언제든 철회할 수 있고, 철회하면 기록을 바로 멈추고 저장된 기록을 모두 지워요.")
        ConsentItem(
            "권한 안내",
            "동의하면 시스템이 '위치 정보 액세스'와 알림 권한을 물어요. 차량 속도 권한이 위치 권한 그룹에 묶여 있어서 그렇게 표시될 뿐, GPS 위치는 읽지 않아요. 알림은 주행 리포트가 준비됐을 때만 조용히 보내요.",
        )
        T("운전 습관 분석은 참고용이에요. 안전 운전 판단은 운전자에게 있어요.", 19, color = CoachTheme.colors.ink3, lh = 1.55f)
    }
}

@Composable
private fun ConsentItem(title: String, body: String) {
    CoachCard(Modifier.fillMaxWidth(), padV = 30.dp, padH = 36.dp, gap = 10.dp) {
        T(title, 26, weight = FontWeight.W700)
        BodyText(body)
    }
}
