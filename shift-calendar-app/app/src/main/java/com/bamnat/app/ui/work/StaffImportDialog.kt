package com.bamnat.app.ui.work

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bamnat.app.ui.Fmt
import com.bamnat.app.ui.components.AlertDialog
import com.bamnat.app.ui.theme.LocalCalendarColors
import com.bamnat.core.boss.StaffImport
import com.bamnat.core.boss.WageForm

/**
 * 1.38 가져오기 (사용자 2026-11-05 "어떤 형식이든"): the staff list read from a file or photo, one line a person with what was read
 * (형태·금액·요일·시간) and what to look at in yellow. Names already in the ledger are left out unless ticked. [room] is how many
 * more the 직원 요금제 lets in now; past it the lines say so and [onApply] gets only the ones that fit.
 */
@Composable
internal fun StaffImportDialog(lines: List<StaffImport.Line>, fromPhoto: Boolean, room: Int, onDismiss: () -> Unit, onApply: (List<StaffImport.Line>, Boolean) -> Unit) {
    val on = remember(lines) { mutableStateListOf<Boolean>().apply { addAll(lines.map { !it.already }) } }
    val chosen = lines.indices.filter { on[it] }
    val fits = chosen.take(room.coerceAtLeast(0))
    AlertDialog(onDismissRequest = {}, properties = androidx.compose.ui.window.DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text("직원 명단 가져오기") }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${lines.size}명을 읽었어요. 연락처·주민번호·계좌 칸은 읽지 않아요." + if (fromPhoto) " 사진에서 읽은 것이라 노란 글씨는 꼭 봐 주세요." else "",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                lines.forEachIndexed { i, l ->
                    val m = l.member
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = on[i], onCheckedChange = { on[i] = it })
                        Column(Modifier.weight(1f)) {
                            Text(m.name + if (l.already) " · 이미 있어요" else "", style = MaterialTheme.typography.bodyMedium)
                            val form = when (m.form) { WageForm.HOUR -> "시급"; WageForm.DAY -> "일급"; WageForm.MONTH -> "월급" }
                            val week = m.weekly.entries.sortedBy { it.key }.let { w ->
                                if (w.isEmpty()) "" else " · " + w.joinToString("") { Fmt.dayOfWeekShort(it.key) } + " " + w.first().value.let { "${it.start}~${it.end}" }
                            }
                            Text("$form ${Fmt.won(m.base)}$week" + (m.job.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                                style = MaterialTheme.typography.bodySmall)
                            if (l.check.isNotEmpty()) Text("확인: " + l.check.joinToString(", "), style = MaterialTheme.typography.labelSmall, color = LocalCalendarColors.current.warning)
                            if (on[i] && i !in fits) Text("직원 요금제가 있어야 더할 수 있어요", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                Text("더한 뒤 이름을 누르면 하나씩 고칠 수 있어요. 요일마다 시간이 다르면 거기서 바꿔 주세요.",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }, confirmButton = {
            TextButton(enabled = fits.isNotEmpty(), onClick = { onApply(fits.map { lines[it] }, chosen.size > fits.size) }) { Text("${fits.size}명 더하기") }
        }, dismissButton = { TextButton(onClick = onDismiss) { Text("그만두기") } })
}
