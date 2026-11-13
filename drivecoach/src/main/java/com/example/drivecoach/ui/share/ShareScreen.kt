package com.example.drivecoach.ui.share

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.drivecoach.BuildConfig
import com.example.drivecoach.share.ShareErrorKind
import com.example.drivecoach.share.ShareTarget
import com.example.drivecoach.share.ShareUi
import com.example.drivecoach.ui.components.BodyText
import com.example.drivecoach.ui.components.CoachIconView
import com.example.drivecoach.ui.components.CoachIcons
import com.example.drivecoach.ui.components.LocalWideLayout
import com.example.drivecoach.ui.components.OutlinePill
import com.example.drivecoach.ui.components.PrimaryPill
import com.example.drivecoach.ui.components.T
import com.example.drivecoach.ui.components.TileShape
import com.example.drivecoach.ui.components.fmtDateTime
import com.example.drivecoach.ui.components.screenPadding
import com.example.drivecoach.ui.theme.CoachColors
import com.example.drivecoach.ui.theme.CoachTheme
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlin.math.floor

/**
 * "폰으로 보기" page, in place of the tab content (never while driving: the root shows the driving view
 * then, and the view model closes this page). In a landscape window the picture sits left of the text and
 * the buttons; in a portrait window (two apps side by side) it sits above them, so the text keeps a
 * readable width and the buttons stay on screen. The design has no screen for this page; it uses the
 * design's page padding, type scale, tiles and pills.
 */
@Composable
fun ShareScreen(
    state: ShareUi,
    onAccept: () -> Unit,
    onRetry: () -> Unit,
    onDeleteLink: () -> Unit,
    onClose: () -> Unit,
) {
    val padding = screenPadding(top = 44.dp, end = 64.dp, bottom = 56.dp, start = 56.dp)
    if (LocalWideLayout.current) {
        Row(
            Modifier
                .fillMaxSize()
                .padding(padding),
            horizontalArrangement = Arrangement.spacedBy(48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(560.dp), contentAlignment = Alignment.Center) {
                ShareVisual(state, Modifier.fillMaxSize(), badgeSize = 240.dp)
            }
            Column(
                Modifier
                    .weight(1f)
                    .widthIn(max = 900.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                ShareTexts(state, onAccept, onRetry, onDeleteLink, onClose)
            }
        }
    } else {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // About 440 dp: still an easy scan for a phone camera, and the buttons fit below it.
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                ShareVisual(state, Modifier.widthIn(max = 440.dp).fillMaxWidth().aspectRatio(1f), badgeSize = 140.dp)
            }
            ShareTexts(state, onAccept, onRetry, onDeleteLink, onClose)
        }
    }
}

/** The QR, or a round badge for the page's state (like the coaching tiles' icon circles); [qrModifier] sizes the QR. */
@Composable
private fun ShareVisual(state: ShareUi, qrModifier: Modifier, badgeSize: Dp) {
    val c = CoachTheme.colors
    when (state) {
        is ShareUi.Ready -> QrCode(state.link, qrModifier)
        is ShareUi.Working -> CircularProgressIndicator(Modifier.size(badgeSize * 0.5f), color = c.acc, strokeWidth = badgeSize / 24, trackColor = c.track)
        is ShareUi.Failed -> Box(Modifier.size(badgeSize).background(CoachColors.DangerSoft, CircleShape), contentAlignment = Alignment.Center) {
            CoachIconView(CoachIcons.AlertMark, badgeSize * 0.6f, CoachColors.Red)
        }
        else -> Box(Modifier.size(badgeSize).background(c.tint, CircleShape), contentAlignment = Alignment.Center) {
            CoachIconView(CoachIcons.Qr, badgeSize * 0.42f, c.acc)
        }
    }
}

/** What the page says and its buttons, laid into the caller's column. */
@Composable
private fun ShareTexts(state: ShareUi, onAccept: () -> Unit, onRetry: () -> Unit, onDeleteLink: () -> Unit, onClose: () -> Unit) {
    val c = CoachTheme.colors
    val target = when (state) {
        is ShareUi.NeedsConsent -> state.target
        is ShareUi.Working -> state.target
        is ShareUi.Ready -> state.target
        is ShareUi.Failed -> state.target
        ShareUi.Idle -> null
    }
    target?.let { T("폰으로 보기 · ${targetLabel(it)}", 22, weight = FontWeight.W700, color = c.acc) }
    when (state) {
        is ShareUi.NeedsConsent -> ConsentBody(onAccept, onClose)
        is ShareUi.Working -> Headline("리포트를 암호화해서 올리는 중이에요…")
        is ShareUi.Ready -> ReadyBody(state, onDeleteLink, onClose)
        is ShareUi.Failed -> FailedBody(state.kind, onRetry, onClose)
        ShareUi.Idle -> Unit
    }
}

@Composable
private fun Headline(text: String) = T(text, 40, weight = FontWeight.W700, lh = 1.25f)

private fun targetLabel(t: ShareTarget) = when (t) {
    is ShareTarget.Trip -> "주행 리포트"
    is ShareTarget.Period -> "최근 ${t.days}일 습관 분석"
}

/** The separate opt-in (선택 동의): only the button counts, refusing keeps every other feature. */
@Composable
private fun ConsentBody(onAccept: () -> Unit, onClose: () -> Unit) {
    val c = CoachTheme.colors
    Headline("폰으로 보내기 전에 확인해 주세요")
    val items = listOf(
        "올라가는 것" to "이 리포트의 날짜·시간, 거리, 속도 그래프, 급가속·급제동 기록, 액셀·브레이크 통계, 주차 기록, 코칭 문구예요. 위치·누적 주행거리·차량 정보는 올리지 않아요.",
        "방법" to "차에서 암호화한 사본만 드라이브 코치 서버에 올려요. 푸는 열쇠는 QR 안에만 있어서 서버에는 암호화된 사본만 있어요.",
        "목적·보관" to "폰 브라우저에서 이 리포트를 보는 데만 써요. 7일 뒤 서버에서 자동으로 지워지고, '링크 지우기'나 설정에서 먼저 지울 수 있어요.",
        "주의" to "QR(링크)을 가진 사람은 누구나 볼 수 있어요. 다른 사람에게 보내지 마세요. 서버는 요청 제한을 위해 접속 주소(IP)를 잠깐 메모리에만 써요.",
        "거부" to "동의하지 않아도 차에서 보는 기록과 리포트는 그대로 쓸 수 있어요. 동의는 설정에서 언제든 철회할 수 있어요.",
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items.forEach { (title, body) ->
            Column(
                Modifier.fillMaxWidth().background(c.tile, TileShape).padding(horizontal = 28.dp, vertical = 22.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                T(title, 22, weight = FontWeight.W700)
                T(body, 20, color = c.ink1, lh = 1.55f)
            }
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        PrimaryPill("동의하고 QR 만들기", onAccept, Modifier.weight(1f), large = true)
        OutlinePill("취소", onClose, Modifier.weight(1f), large = true)
    }
}

@Composable
private fun ReadyBody(state: ShareUi.Ready, onDeleteLink: () -> Unit, onClose: () -> Unit) {
    val c = CoachTheme.colors
    Headline("폰 카메라로 QR을 찍으세요")
    BodyText("폰 브라우저에서 이 리포트가 열려요. 차와 폰 모두 인터넷에 연결돼 있어야 해요.")
    T("${fmtDateTime(state.expiresAtEpochMs)}까지 열 수 있어요", 23, weight = FontWeight.W700, color = c.acc)
    T("QR을 가진 사람은 누구나 볼 수 있어요. 다 봤으면 링크를 지워 주세요.", 19, color = c.ink3, lh = 1.55f)
    if (BuildConfig.DEBUG) {
        // Debug only: lets tools read the link (accessibility services could read it too, so never in release).
        T(state.link, 15, color = c.ink4, lh = 1.6f)
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        PrimaryPill("닫기", onClose, Modifier.weight(1f), large = true)
        OutlinePill("링크 지우기", onDeleteLink, Modifier.weight(1f), danger = true, large = true)
    }
}

@Composable
private fun FailedBody(kind: ShareErrorKind, onRetry: () -> Unit, onClose: () -> Unit) {
    val (title, body) = when (kind) {
        ShareErrorKind.OFFLINE -> "서버에 연결할 수 없어요" to "차의 인터넷 연결을 확인한 뒤 다시 시도해 주세요."
        ShareErrorKind.RATE_LIMITED -> "요청이 너무 많아요" to "잠시 뒤 다시 시도해 주세요."
        ShareErrorKind.TOO_LARGE -> "리포트가 너무 커서 올릴 수 없어요" to "기간을 줄여서 다시 시도해 주세요."
        ShareErrorKind.NOT_CONFIGURED -> "공유 서버가 설정되지 않았어요" to "이 빌드에는 '폰으로 보기' 서버 주소가 없어요."
        ShareErrorKind.SERVER -> "리포트를 올리지 못했어요" to "서버나 기록에 문제가 있어요. 잠시 뒤 다시 시도해 주세요."
    }
    Headline(title)
    BodyText(body)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        if (kind != ShareErrorKind.NOT_CONFIGURED) PrimaryPill("다시 시도", onRetry, Modifier.weight(1f), large = true)
        OutlinePill("닫기", onClose, Modifier.weight(1f), large = true)
    }
}

/**
 * Dark modules on white with a 4-module quiet zone, each module a whole number of pixels so the code stays
 * sharp (a ~100-character link is version 6–7 at level M: easy for a phone even from a small window).
 * Always black on white, in the dark theme too: phone cameras read that most reliably.
 */
@Composable
fun QrCode(text: String, modifier: Modifier = Modifier) {
    val matrix = remember(text) { encodeQr(text) }
    Box(
        modifier
            .clip(RoundedCornerShape(28.dp))
            .background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val cell = floor(minOf(size.width, size.height) / matrix.width).coerceAtLeast(1f)
            val origin = Offset((size.width - cell * matrix.width) / 2f, (size.height - cell * matrix.height) / 2f).let { Offset(floor(it.x), floor(it.y)) }
            for (y in 0 until matrix.height) {
                for (x in 0 until matrix.width) {
                    if (matrix[x, y]) drawRect(Color.Black, Offset(origin.x + x * cell, origin.y + y * cell), Size(cell, cell))
                }
            }
        }
    }
}

private fun encodeQr(text: String): BitMatrix = QRCodeWriter().encode(
    text,
    BarcodeFormat.QR_CODE,
    0,
    0,
    mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 4, EncodeHintType.CHARACTER_SET to "UTF-8"),
)
