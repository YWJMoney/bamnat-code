package com.bamnat.app.ui.memo

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.bamnat.core.memo.Memo
import com.bamnat.core.memo.MemoProps
import com.bamnat.core.memo.MemoTemplate

/**
 * [TAB-03] 1.36 이 메모를 틀로 저장 (PC 확인 bad7d860: "눌러도 반응이 없어요", 사용성 제안 "저장 알림을 띄우고 틀 이름을 물어 주세요"): the
 * name, and each "칸: 값" line of the memo as a box — ticked, starting from the 틀 asks it fresh each time (금액·날짜·별점… as that kind of
 * 입력 칸); unticked, the line is kept as written.
 */
@Composable
internal fun SaveTemplateDialog(memo: Memo, onSave: (String, Set<String>, Map<String, List<String>>) -> Unit, onDismiss: () -> Unit) {
    val props = remember(memo.body) { if (memo.isChecklist) emptyList() else MemoProps.of(memo).take(MemoTemplate.MAX_ASKS) }
    var name by remember { mutableStateOf(memo.title.take(30)) }
    var asked by remember { mutableStateOf(props.map { it.label }.toSet()) }
    // [TAB-01] 누락 점검 N9 고르기: a 글 칸 asked with words to pick from ("할 일, 하는 중, 끝") asks as 고르기 in the 틀.
    var picks by remember { mutableStateOf(props.filter { it.type == MemoProps.Type.TEXT || it.type == MemoProps.Type.PICK }.associate { it.label to "" }) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("틀로 저장") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it.take(30) }, label = { Text("틀 이름") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (props.isNotEmpty()) {
                    Text("새 메모를 만들 때마다 물어볼 칸", style = MaterialTheme.typography.titleSmall)
                    for (p in props) {
                        val on = p.label in asked
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                .clickable(role = Role.Checkbox) { asked = if (on) asked - p.label else asked + p.label },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = on, onCheckedChange = null)
                            Text("${p.label} (${p.type.label})", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Text(if (on) "물어봐요" else "‘${shown(p)}’ 그대로", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                        if (on && p.label in picks) OutlinedTextField(
                            picks[p.label].orEmpty(), { v -> picks = picks + (p.label to v.take(120)) },
                            label = { Text("고를 것 (쉼표로, 비우면 글로 써요)") },
                            placeholder = { Text("예: ${p.raw}, 하는 중, 끝") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(start = 40.dp),
                        )
                    }
                } else {
                    Text("메모에 ‘매출: 32000’, ‘날짜: 11월 1일’처럼 ‘칸: 값’ 줄을 쓰면, 그 칸을 새 메모마다 물어보는 틀이 돼요.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("날짜와 달력은 틀에 담지 않아요. 저장한 틀은 새 메모의 ‘틀에서 시작’에 있어요.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(name, asked, picks.filterKeys { it in asked }.mapValues { (_, v) -> v.split(',', '·').map { it.trim() }.filter { it.isNotEmpty() } }
                    .filterValues { it.size >= 2 })
            }) { Text("저장") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("그만두기") } },
    )
}
