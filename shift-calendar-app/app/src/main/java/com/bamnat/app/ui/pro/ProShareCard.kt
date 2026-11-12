package com.bamnat.app.ui.pro

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import com.bamnat.app.ui.components.AlertDialog
import com.bamnat.app.ui.components.Button
import androidx.compose.material3.MaterialTheme
import com.bamnat.app.ui.components.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import com.bamnat.app.AppContainer
import com.bamnat.app.tier.Gate
import com.bamnat.app.tier.ProShare
import com.bamnat.app.ui.components.SectionCard
import kotlinx.coroutines.launch
import java.time.ZoneId

/**
 * 1.36 [PSH-01] 프로 나눔 1인 (사용자 결정 2026-10-31): someone who bought 프로 gives it to one person of their choosing, for
 * as long as they keep it. Receivers of a shared calendar see it free either way; this is the one person who also gets
 * the 프로 features. The buyer makes a one-time code and sends it to that person only; a new person takes the place of
 * the one before. The person given 프로 can give it back; the code goes in at the bottom of 프로 ([CodeInbox], 2.0.0 [RVO-06]).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ProShareCard(c: AppContainer, gate: Gate, zone: ZoneId, onMessage: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var share by remember { mutableStateOf<ProShare?>(null) }
    var ending by remember { mutableStateOf(false) }
    val bought = gate.boughtPro
    val given = gate.access.reason == com.bamnat.core.tier.ProReason.SHARED
    LaunchedEffect(bought, given) {
        if (bought || given) runCatching { share = c.tierApi.share(c.companyStore.device) }
    }
    suspend fun after(s: ProShare) {
        share = s
        // The given 프로 comes with the passes, so they are read again.
        runCatching { c.proStore.setPasses(c.tierApi.passes(c.companyStore.device)) }
        c.featureGate.refresh()
    }
    SectionCard(title = "프로 나눠 주기") {
        val s = share
        when {
            bought -> {
                Text("직접 구독한 프로를 한 사람에게 나눠 줄 수 있어요. 내가 프로를 쓰는 동안 그 사람도 프로 기능을 써요.",
                    style = MaterialTheme.typography.bodyMedium)
                when {
                    s?.code != null -> {
                        Text(s.code, style = MaterialTheme.typography.headlineSmall, fontFamily = FontFamily.Monospace)
                        Text("이 코드를 나눠 줄 한 사람에게만 보내 주세요. ${s.codeUntil?.let { dateWords(lastDay(it, zone)) + "까지" } ?: "일주일 동안"} 한 번 쓸 수 있어요." +
                            if (s.given) " 그 사람이 코드를 넣으면 지금 나눠 받은 분은 빠져요." else "",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    s?.given == true -> Text("한 사람에게 나눠 주고 있어요" + (s.givenAt?.let { " (${dateWords(it.atZone(zone).toLocalDate())}부터)" } ?: "") + ".",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(androidx.compose.ui.unit.Dp(8f))) {
                    val made = s?.code
                    if (made != null) Button(onClick = {
                        val text = shareCodeText(made)
                        runCatching { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "코드 보내기")) }
                    }) { Text("코드 보내기") }
                    OutlinedButton(onClick = {
                        scope.launch { runServer(onMessage) { after(c.tierApi.shareCode(c.companyStore.device)) } }
                    }) { Text(if (made == null) (if (s?.given == true) "다른 사람에게 주기" else "나눠 줄 코드 만들기") else "새 코드") }
                    if (s?.given == true || made != null) TextButton(onClick = { ending = true }) { Text("나눔 끊기") }
                }
            }
            given -> {
                Text(com.bamnat.core.tier.ProReason.SHARED.words, style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { ending = true }) { Text("나눠 받은 프로 돌려주기") }
            }
            else -> Text(shareLead(gate), style = MaterialTheme.typography.bodyMedium)
        }
    }
    if (ending) AlertDialog(
        onDismissRequest = { ending = false },
        title = { Text(if (bought) "나눔을 끊을까요?" else "나눠 받은 프로를 돌려줄까요?") },
        text = { Text(if (bought) "나눠 받은 분의 프로 기능이 바로 꺼지고, 보낸 코드도 쓸 수 없어요. 나중에 새 코드로 다시 나눌 수 있어요."
            else "이 휴대폰의 프로 기능이 바로 꺼져요. 다시 받으려면 새 코드가 필요해요.") },
        confirmButton = { TextButton(onClick = {
            ending = false
            scope.launch { runServer(onMessage) { after(c.tierApi.shareEnd(c.companyStore.device)) } }
        }) { Text(if (bought) "끊기" else "돌려주기") } },
        dismissButton = { TextButton(onClick = { ending = false }) { Text("취소") } },
    )
}


/**
 * 1.36 사용자 결정 (2026-11-01): the card's words for someone who cannot share yet. A free trial is not bought 프로: it says it
 * shares once paid (the reason then turns SUBSCRIPTION and the card opens by itself). Free (PC Y: '무료 상태에서도 미리'): that
 * paying lets them share with one person.
 */
internal fun shareLead(gate: Gate): String = when {
    gate.live && gate.access.pro && gate.access.reason == com.bamnat.core.tier.ProReason.TRIAL ->
        "체험이 끝나고 결제되면 1명에게 나눌 수 있어요."
    // 2.0.0 [RVO-06] 받은 코드는 프로 화면 맨 아래 '코드를 받으셨나요?'에서 받아요.
    else -> "프로를 결제하면 1명에게 프로를 나눠 줄 수 있어요."
}

/** 2.0.0 [RVO-06] What the buyer sends with the code: where it goes in (프로 화면 맨 아래 한 칸). */
internal fun shareCodeText(code: String): String =
    "밤낮 프로를 나눠 드려요. 밤낮 › 설정 › 프로 › 맨 아래 ‘$CODE_INBOX_TITLE’에 이 코드를 넣어 주세요: ${com.bamnat.core.tier.InviteRules.shown(code)}"
