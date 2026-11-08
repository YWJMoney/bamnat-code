package com.bamnat.app.ui.hub

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.bamnat.app.ui.components.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bamnat.app.AppContainer
import com.bamnat.app.company.CompanyException
import com.bamnat.app.ui.company.SoftCard
import com.bamnat.core.plaza.CareNote
import com.bamnat.core.plaza.CareStanding
import com.bamnat.core.plaza.CommunityCare
import com.bamnat.core.plaza.PlazaRules
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * 1.39 [CARE-03][CARE-10] 마을 맨 위: 운영자의 안내는 마을을 열 때 한 번 부드러운 창으로(확인했어요 · 운영 원칙 보기 · 해명하기),
 * 쉬는 중이면 탭 아래 얇은 한 줄(누르면 까닭·되는 것·해명하기). 아무 일이 없으면 아무것도 그리지 않아요. 서버가 모르는 옛 서버나
 * 마을 돌봄이 꺼진 동안은 늘 비어 있어요. 사용자 2026-11-07 "미리 경고도 해야 한다 부드럽게", "해명을 한다면 풀어주는 길".
 */
@Composable
fun CommunityCareBar(c: AppContainer) {
    val agreed by remember { c.settingsRepository.settings.map { it.plazaRulesAgreed } }.collectAsStateWithLifecycle(c.settingsRepository.current.plazaRulesAgreed)
    if (!c.companyApi.enabled || agreed <= 0) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var standing by remember { mutableStateOf(CareStanding()) }
    var looks by remember { mutableIntStateOf(0) }
    // 이번에 연 동안 한 번만: 닫아도 다음에 마을을 열면 '확인했어요' 전까지 다시 보여요.
    var warningShown by rememberSaveable { mutableStateOf<String?>(null) }
    var holdOpen by remember { mutableStateOf(false) }
    var appealFor by remember { mutableStateOf<String?>(null) }
    var said by remember { mutableStateOf<String?>(null) }
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) { looks++ }
    androidx.compose.runtime.LaunchedEffect(looks) {
        standing = try { c.companyApi.careStanding(c.companyStore.device) } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { if (com.bamnat.app.BuildConfig.DEBUG) android.util.Log.i("BamnatCare", "standing failed: $e"); return@LaunchedEffect }
        if (com.bamnat.app.BuildConfig.DEBUG) android.util.Log.i("BamnatCare", "standing=$standing")
    }
    val warning = standing.warning
    if (warning != null && warningShown != warning.id && appealFor == null) {
        AlertDialog(
            onDismissRequest = { warningShown = warning.id },
            title = { Text(CommunityCare.warningTitle(warning)) },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) { Text(CommunityCare.warningBody(warning)) } },
            confirmButton = {
                TextButton(onClick = {
                    warningShown = warning.id
                    scope.launch {
                        runCatching { c.companyApi.careSeen(c.companyStore.device, warning.id) }
                        standing = standing.copy(warning = null)
                    }
                }) { Text("확인했어요") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { com.bamnat.app.ui.components.PolicyPage.open(context, PlazaRules.URL, "광장 운영 규칙") }) { Text("운영 원칙") }
                    if (warning.appeal == null) TextButton(onClick = { warningShown = warning.id; appealFor = warning.id }) { Text("해명하기") }
                }
            },
        )
    }
    val hold = standing.hold
    if (hold != null) {
        SoftCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
            Row(Modifier.fillMaxWidth().clickable(onClickLabel = "까닭 보기") { holdOpen = true }.heightIn(min = 48.dp).padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(CommunityCare.holdLine(hold), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { holdOpen = true }) { Text("자세히") }
            }
        }
    } else if (standing.limitAppeal == "none") {
        // 1.36 신고 누적 쓰기 제한도 해명할 수 있어요(같은 길).
        SoftCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("✍️ 신고가 쌓여 글쓰기가 잠시 줄었어요", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { appealFor = "limit" }) { Text("해명하기") }
            }
        }
    }
    if (holdOpen && hold != null) {
        AlertDialog(
            onDismissRequest = { holdOpen = false },
            title = { Text(if (hold.scope == "community") "마을 쓰기를 쉬어 가요" else "의견 쓰기를 쉬어 가요") },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) { Text(CommunityCare.holdBody(hold, standing.answerHours)) } },
            confirmButton = { TextButton(onClick = { holdOpen = false }) { Text("닫기") } },
            dismissButton = {
                Row {
                    TextButton(onClick = { com.bamnat.app.ui.components.PolicyPage.open(context, PlazaRules.URL, "광장 운영 규칙") }) { Text("운영 원칙") }
                    if (hold.appeal == null) TextButton(onClick = { holdOpen = false; appealFor = hold.id }) { Text("해명하기") }
                }
            },
        )
    }
    appealFor?.let { target ->
        AppealDialog(c, target, standing.answerHours, onDone = { sent ->
            appealFor = null
            if (sent != null) {
                said = sent
                looks++
            }
        })
    }
    said?.let { text ->
        AlertDialog(onDismissRequest = { said = null }, text = { Text(text) }, confirmButton = { TextButton(onClick = { said = null }) { Text("확인") } })
    }
}

/**
 * 1.39 [CARE-10] 해명 쓰기(또는 가려진 내 글 '다시 보여 주세요', [target] "post:<id>"). 나만 보는 문의가 되고, 답은 내 문의에 와요.
 * [onDone]은 보냈으면 보낸 뒤의 말, 그만두면 null.
 */
@Composable
fun AppealDialog(c: AppContainer, target: String, answerHours: Int = CommunityCare.ANSWER_HOURS, onDone: (String?) -> Unit) {
    val scope = rememberCoroutineScope()
    var text by rememberSaveable(target) { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val repost = target.startsWith("post:")
    AlertDialog(
        onDismissRequest = { if (!busy) onDone(null) },
        title = { Text(if (repost) "다시 보여 주세요" else "해명하기") },
        text = {
            Column {
                Text(if (repost) "가려진 글을 왜 다시 보여 주면 좋을지 적어 주세요. 운영자가 다시 읽고 ${answerHours}시간 안에 내 문의로 답해요."
                    else "어떤 뜻이었는지, 무엇을 잘못 보았는지 적어 주세요. 운영자가 읽고 ${answerHours}시간 안에 내 문의로 답해요. 받아들이면 바로 풀려요.",
                    style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(value = text, onValueChange = { if (it.length <= CommunityCare.APPEAL_MAX) text = it; error = null },
                    minLines = 3, maxLines = 8, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    supportingText = { Text(error ?: "${text.trim().length}/${CommunityCare.APPEAL_MAX} · 이름·연락처는 쓰지 않아요") },
                    isError = error != null)
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                CommunityCare.appealProblem(text)?.let { error = it; return@TextButton }
                busy = true
                scope.launch {
                    runCatching { c.companyApi.careAppeal(c.companyStore.device, target, text) }
                        .onSuccess { onDone(CommunityCare.appealSent(answerHours)) }
                        .onFailure { e -> error = CommunityCare.appealError((e as? CompanyException)?.code ?: "offline") }
                    busy = false
                }
            }) { Text("보내기") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = { onDone(null) }) { Text("그만두기") } },
    )
}

/** 1.39 조사 ⑦ 쓰기 전 한마디(다정하게, 막지 않아요). */
@Composable
fun NudgeDialog(onEdit: () -> Unit, onSend: () -> Unit) {
    AlertDialog(
        onDismissRequest = onEdit,
        title = { Text(com.bamnat.core.plaza.PostNudge.TITLE) },
        text = { Text(com.bamnat.core.plaza.PostNudge.TEXT) },
        confirmButton = { TextButton(onClick = onEdit) { Text(com.bamnat.core.plaza.PostNudge.EDIT) } },
        dismissButton = { TextButton(onClick = onSend) { Text(com.bamnat.core.plaza.PostNudge.SEND) } },
    )
}
