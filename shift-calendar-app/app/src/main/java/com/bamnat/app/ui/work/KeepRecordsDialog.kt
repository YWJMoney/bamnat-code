package com.bamnat.app.ui.work

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.bamnat.app.ui.components.AlertDialog
import com.bamnat.app.ui.components.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bamnat.app.data.RecordsTrash
import com.bamnat.app.ui.Fmt
import com.bamnat.core.work.WorkStyle

/**
 * [UNDO-01] Asked when a calendar's kind of work changes and it already has records: keep them and
 * change only the kind of work, or clear them and start the new kind afresh. Either can be taken back
 * for 7 days, which the question says, so neither answer feels final.
 */
@Composable
fun KeepRecordsDialog(
    calendarName: String,
    from: WorkStyle,
    to: WorkStyle,
    records: RecordsTrash.Records,
    onKeep: (fold: Boolean) -> Unit,
    onClear: (fold: Boolean) -> Unit,
    onDismiss: () -> Unit,
    /**
     * 1.37 PC MED-2 사용성 제안 (확인 창 두 번): the old kind of work's 근무 that would be offered to put away ("주간·야간"), asked
     * here with a check instead of in a second dialog; null when there are none.
     */
    foldNames: String? = null,
) {
    var fold by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("기존 기록을 유지할까요?") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("‘$calendarName’ 달력을 ${from.label}에서 ${to.label}${Fmt.ro(to.label)} 바꿔요.")
                Text("이 달력의 기록: ${records.text}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                Text(
                    "유지하고 바꾸기: 적어 둔 기록은 그대로 두고 근무 형태만 바뀌어요.\n" +
                        "기록을 지우고 새로 시작: 근무 기록·근무 순서·급여 설정·일감·매출·받을 돈과 이 달력에만 있는 메모를 지우고 처음부터 시작해요. " +
                        "근무 종류(이름·시간·알람)와 가계부는 그대로예요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (foldNames != null) {
                    androidx.compose.foundation.layout.Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .toggleable(value = fold, role = androidx.compose.ui.semantics.Role.Checkbox) { fold = it },
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.Checkbox(checked = fold, onCheckedChange = null)
                        Text("${from.label} 근무 $foldNames${Fmt.eun(foldNames)} 접어 두기 · 하루 시트에서만 빠지고 기록은 그대로예요",
                            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    }
                }
                Text(
                    "어느 쪽이든 7일 안에는 되돌릴 수 있어요. 설정 › 데이터 › 최근 지운 기록",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(onClick = { onKeep(foldNames != null && fold) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("유지하고 바꾸기") }
                OutlinedButton(
                    onClick = { onClear(foldNames != null && fold) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("기록을 지우고 새로 시작") }
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("취소") }
            }
        },
    )
}
