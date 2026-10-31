package com.bamnat.app.ui.pro

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
 * the one before. Anyone can put a code in here, and the person given 프로 can give it back.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ProShareCard(c: AppContainer, gate: Gate, zone: ZoneId, onMessage: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var share by remember { mutableStateOf<ProShare?>(null) }
    var entering by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }
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
                        val text = "밤낮 프로를 나눠 드려요. 밤낮 › 설정 › 프로 › 프로 나눠 주기 › 코드 넣기에 이 코드를 넣어 주세요: $made"
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
            else -> Text("프로를 구독한 분이 한 사람에게 프로를 나눠 줄 수 있어요. 받은 코드가 있으면 넣어 주세요.",
                style = MaterialTheme.typography.bodyMedium)
        }
        if (!bought && !given) {
            TextButton(onClick = { entering = !entering }) { Text("코드 넣기") }
            if (entering) {
                OutlinedTextField(code, { code = it.uppercase().filter { ch -> ch.isLetterOrDigit() }.take(8) },
                    label = { Text("나눔 코드 (8자리)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Button(enabled = code.length == 8, onClick = {
                    scope.launch {
                        runServer(onMessage) {
                            after(c.tierApi.shareClaim(c.companyStore.device, code))
                            entering = false; code = ""
                            onMessage("나눠 받은 프로를 켰어요.")
                        }
                    }
                }) { Text("프로 받기") }
            }
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

