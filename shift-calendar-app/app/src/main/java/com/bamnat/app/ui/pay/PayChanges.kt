package com.bamnat.app.ui.pay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bamnat.app.pay.PayStore
import com.bamnat.app.ui.components.AlertDialog
import com.bamnat.core.pay.PayProfile
import com.bamnat.core.time.Today

/**
 * [PAYIN-05] 1.37 B5: what a save just did, under the screen: "회사 규칙 2개를 넣었어요 · 11월 2,980,000원 → 3,046,000원" with
 * 되돌리기 and 닫기. A save that moved nothing in the month says so, so a save never seems to have done nothing. It stays 12 seconds
 * (사용자 1.36 "되돌리기 띠가 너무 빨리 사라져요") or until closed.
 */
@Composable
internal fun ChangeNoteBar(note: PayViewModel.ChangeNote, onUndo: () -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    LaunchedEffect(note) { kotlinx.coroutines.delay(12_000); onClose() }
    Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = MaterialTheme.shapes.medium, shadowElevation = 6.dp,
        modifier = modifier.fillMaxWidth().padding(12.dp)) {
        Column(Modifier.padding(start = 14.dp, top = 10.dp, end = 4.dp, bottom = 2.dp)) {
            Text(note.what, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.inverseOnSurface)
            Text(changeLine(note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.inverseOnSurface)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onUndo, modifier = Modifier.heightIn(min = 48.dp)) { Text("되돌리기", color = MaterialTheme.colorScheme.inversePrimary) }
                TextButton(onClick = onClose, modifier = Modifier.heightIn(min = 48.dp)) { Text("닫기", color = MaterialTheme.colorScheme.inverseOnSurface) }
            }
        }
    }
}

/** "11월 급여 2,980,000원 → 3,046,000원 (+66,000원)", or that the month stayed as it was. */
internal fun changeLine(note: PayViewModel.ChangeNote): String {
    val m = "${note.month.monthValue}월 급여"
    val b = note.before
    val a = note.after
    return when {
        b == null && a == null -> "$m 계산은 아직 없어요"
        b == null -> "$m ${won(a!!)}로 계산해요"
        a == null -> "$m 계산을 비웠어요 (전에는 ${won(b)})"
        a == b -> "$m ${won(a)} 그대로예요 (이 달에는 영향이 없어요)"
        else -> "$m ${won(b)} → ${won(a)} (${if (a > b) "+" else "−"}${won(kotlin.math.abs(a - b))})"
    }
}

/**
 * [PAYIN-06] 1.37 B7 (사용자 "정확하지 않은 급여정보를 수정하는 부분이 너무 불편해"): the last 7 days of changes, newest first: when, what,
 * and what the setup was before (기본급 and how many 수당·규칙), each with '이때 전으로 되돌리기'. Putting one back is itself a change
 * in the list, so it can be undone too.
 */
@Composable
internal fun PayHistoryDialog(history: List<PayStore.Change>, current: PayProfile?, onRestore: (PayStore.Change) -> Unit, onDismiss: () -> Unit) {
    var asking by remember { mutableStateOf<PayStore.Change?>(null) }
    asking?.let { c ->
        AlertDialog(
            onDismissRequest = { asking = null },
            title = { Text("${PayStore.whenText(c.at)} 전으로 되돌릴까요?") },
            text = { Text("지금 설정은 기록에 남아서, 7일 안에는 다시 이리로 돌아올 수 있어요.\n\n되돌릴 설정: ${sketch(c.before)}") },
            confirmButton = { TextButton(onClick = { asking = null; onRestore(c); onDismiss() }) { Text("되돌리기") } },
            dismissButton = { TextButton(onClick = { asking = null }) { Text("취소") } },
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("고친 기록 (7일)") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("지금: ${sketch(current)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (history.isEmpty()) Text("최근 7일 동안 바꾼 것이 없어요.", style = MaterialTheme.typography.bodyMedium)
                history.forEach { c ->
                    HorizontalDivider()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(c.why, style = MaterialTheme.typography.bodyMedium)
                            Text(PayStore.whenText(c.at) + " · 전: " + sketch(c.before), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { asking = c }, modifier = Modifier.heightIn(min = 48.dp)) { Text("전으로") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
    )
}

/** A setup in a few words: "월급 기본급 2,500,000원 · 수당 3개 · 직접 만든 항목 1개", or that there was none. */
internal fun sketch(p: PayProfile?): String {
    if (p == null) return "급여 설정 없음"
    val base = p.base.on(Today.month()).value.takeIf { it > 0 }
    return listOfNotNull(
        "${p.wageType.baseName()} ${base?.let(::won) ?: "-"}",
        p.items.size.takeIf { it > 0 }?.let { "수당 ${it}개" },
        p.customRules.size.takeIf { it > 0 }?.let { "직접 만든 항목 ${it}개" },
    ).joinToString(" · ")
}

/** [PAYIN-06] 1.37 B6: before emptying the setup, what happens and that it can come back for 7 days. */
@Composable
internal fun RestartDialog(onRestart: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("급여 설정을 처음부터 다시 만들까요?") },
        text = {
            Text("지금 설정(기본급·수당·회사 규칙)을 비우고 첫 단계부터 다시 해요. 달력의 근무 기록은 그대로예요.\n\n" +
                "지금 설정은 7일 동안 보관해서, 급여 화면 위 ‘지운 설정 되돌리기’나 ⋮ › 고친 기록에서 언제든 되돌릴 수 있어요.")
        },
        confirmButton = { TextButton(onClick = { onDismiss(); onRestart() }) { Text("처음부터 다시") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

/** [PAYIN-06] The setup emptied by '처음부터 다시' in the last 7 days, on top of the first step, to bring back. */
@Composable
internal fun RestoreBand(change: PayStore.Change, onRestore: () -> Unit, modifier: Modifier = Modifier) {
    val left = (PayStore.KEEP_DAYS - (Today.millis() - change.at) / (24L * 60 * 60 * 1000)).coerceAtLeast(1)
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("지운 급여 설정이 있어요 (${left}일 동안 보관)", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer)
                Text(sketch(change.before), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            TextButton(onClick = onRestore, modifier = Modifier.heightIn(min = 48.dp)) { Text("되돌리기") }
        }
    }
}
