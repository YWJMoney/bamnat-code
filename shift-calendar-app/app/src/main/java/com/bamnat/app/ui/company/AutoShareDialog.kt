package com.bamnat.app.ui.company

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bamnat.app.AppContainer
import com.bamnat.app.company.AutoShare

/**
 * [PLAZA-07] 결정 D79 and [PLAZA-34] 1.27 AI: the ask when a cycle or pay way of a known company changed, with what changed;
 * the first time yes opens the company's 광장. With 저절로 나누기 on, one line ([snackbar]) says it went, with 되돌리기.
 * 설정 › 개인정보·앱 정보 changes the way later.
 */
@Composable
fun AutoShareHost(c: AppContainer, onOpen: (String) -> Unit, snackbar: SnackbarHostState? = null) {
    val ask by c.autoShare.ask.collectAsStateWithLifecycle()
    val opened by c.autoShare.opened.collectAsStateWithLifecycle()
    val shared by c.autoShare.shared.collectAsStateWithLifecycle()
    LaunchedEffect(opened) {
        val id = opened ?: return@LaunchedEffect
        c.autoShare.openedDone()
        onOpen(id)
    }
    LaunchedEffect(shared) {
        val s = shared ?: return@LaunchedEffect
        val bar = snackbar ?: return@LaunchedEffect
        val r = bar.showSnackbar(sharedLine(s), actionLabel = "되돌리기", duration = SnackbarDuration.Long)
        if (r == SnackbarResult.ActionPerformed) {
            c.autoShare.undo()
            bar.showSnackbar(if (s.queued) "나누지 않을게요" else "광장에서 거뒀어요")
        } else c.autoShare.sharedDone()
    }
    ask?.let { AutoShareDialog(it, onYes = c.autoShare::approve, onNo = c.autoShare::decline, onNever = c.autoShare::never) }
}

/** [PLAZA-34] "나눴어요 · 야간 50% → 60%", or the day's wait. */
internal fun sharedLine(s: AutoShare.Shared): String =
    (if (s.queued) "${s.company} 광장에 내일 나눠요(하루 한 번)" else "${s.company} 광장에 나눴어요") + " · ${s.line}"

@Composable
fun AutoShareDialog(ask: AutoShare.Ask, onYes: (Boolean) -> Unit, onNo: () -> Unit, onNever: () -> Unit = onNo) {
    var always by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onNo,
        title = { Text(if (ask.first) "회사 광장에 나눌까요?" else "바뀐 내용을 회사 광장에 나눌까요?") },
        text = {
            // At 200% text the words scroll inside the dialog rather than push its buttons away.
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (ask.first) {
                    Text("같은 회사 사람들이 쓰게 근무 순서·계산 방식(금액·이름 빼고)을 나눌까요?", style = MaterialTheme.typography.bodyLarge)
                    Text("${ask.company} 광장에 나눠요.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text("${ask.company} 광장에 이렇게 고쳐 올려요.", style = MaterialTheme.typography.bodyMedium)
                    ask.lines.forEach { Text("• $it", style = MaterialTheme.typography.bodyLarge) }
                    if (ask.more > 0) Text("그 밖에 ${ask.more}가지", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (ask.rewardMonths > 0) {
                    Text("처음 알려 주는 급여 방식이면 프로 ${ask.rewardMonths}개월을 드려요.", style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary)
                }
                Text("금액, 이름, 연락처, 입사일, 명세서와 사진은 보내지 않아요.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(
                    Modifier.fillMaxWidth().clickable(role = Role.Checkbox) { always = !always }.padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = always, onCheckedChange = null)
                    Text("다음부터 묻지 않고 저절로 나누기 (하루 한 번)", style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 8.dp))
                }
                TextButton(onClick = onNever, modifier = Modifier.align(Alignment.End)) { Text("다시 묻지 않기") }
            }
        },
        confirmButton = { TextButton(onClick = { onYes(always) }) { Text("나눌게요") } },
        dismissButton = { TextButton(onClick = onNo) { Text("이번엔 안 할게요") } },
    )
}
