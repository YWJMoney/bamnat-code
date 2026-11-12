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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bamnat.app.ui.Fmt
import com.bamnat.app.ui.components.FormSheet
import com.bamnat.app.ui.components.ToneLine
import com.bamnat.app.ui.pay.MoneyField
import com.bamnat.app.ui.theme.Tone
import com.bamnat.core.boss.StaffImport
import com.bamnat.core.boss.StaffTax
import com.bamnat.core.boss.WageForm
import com.bamnat.app.ui.theme.Tone
import com.bamnat.app.ui.theme.color

/**
 * 1.38 가져오기 (사용자 2026-11-05 "어떤 형식이든"): the staff list read from a file or photo, one line a person with what was read
 * (형태·금액·요일·시간) and what to look at. Names already in the ledger are left out unless ticked. [room] is how many
 * more the 직원 요금제 lets in now; past it the lines say so and [onApply] gets only the ones that fit.
 * 2.0.0 [RVJ-40] 검토 PC-102: a person whose sum was not read comes with an empty 금액 field to fill here (no 최저시급 guess), and
 * the list cannot be added until each ticked one has a sum. The columns the list has but the reader does not ([unread]) are named
 * at the top; what to look at is a [ToneLine], the 소득세·외국인 read are in the line. It is a [FormSheet] now (a list with fields).
 */
@Composable
internal fun StaffImportDialog(
    lines: List<StaffImport.Line>, fromPhoto: Boolean, room: Int, unread: List<String> = emptyList(),
    onDismiss: () -> Unit, onApply: (List<StaffImport.Line>, Boolean) -> Unit,
) {
    val on = remember(lines) { mutableStateListOf<Boolean>().apply { addAll(lines.map { !it.already }) } }
    // [RVJ-40] Sums typed in for the lines with none, kept through a rotation (조건 10).
    var typed by rememberSaveable(lines) { mutableStateOf(LongArray(lines.size) { 0L }) }
    val chosen = lines.indices.filter { on[it] }
    val fits = chosen.take(room.coerceAtLeast(0))
    val missing = fits.filter { lines[it].noAmount && typed[it] <= 0 }
    // [RVJ-40] What every line has to look at (a list with no 요일 칸 said '근무 요일·시간이 없어요' five times) is said once at the top;
    // a missing sum stays on its own line, by its field.
    val common = remember(lines) {
        if (lines.size < 2) emptySet() else lines.map { l -> l.check.filterNot { it.endsWith("적어 주세요") }.toSet() }.reduce { a, b -> a intersect b }
    }
    // [RVJ-40] A list with fields to type in is a sheet, not a dialog in the middle (공통 부품 5절); back or X asks once a sum is typed.
    FormSheet(onDismissRequest = onDismiss, unsaved = typed.any { it > 0 },
        title = { Text("직원 명단 가져오기") }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${lines.size}명을 읽었어요. 연락처·주민번호·계좌 칸은 읽지 않아요." + if (fromPhoto) " 사진에서 읽은 것이라 ‘확인’ 줄은 꼭 봐 주세요." else "",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (unread.isNotEmpty()) ToneLine("읽지 않은 칸: ${unread.joinToString(", ")} · 더한 뒤 직원 정보에서 넣어요", tone = Tone.Plain)
                if (common.isNotEmpty()) ToneLine("확인: 모두 ${common.joinToString(", ")}")
                lines.forEachIndexed { i, l ->
                    val m = l.member
                    Row(verticalAlignment = Alignment.Top) {
                        Checkbox(checked = on[i], onCheckedChange = { on[i] = it })
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(m.name + if (l.already) " · 이미 있어요" else "", style = MaterialTheme.typography.bodyMedium)
                            val form = when (m.form) { WageForm.HOUR -> "시급"; WageForm.DAY -> "일급"; WageForm.MONTH -> "월급" }
                            val week = m.weekly.entries.sortedBy { it.key }.let { w ->
                                if (w.isEmpty()) "" else " · " + w.joinToString("") { Fmt.dayOfWeekShort(it.key) } + " " + w.first().value.let { "${it.start}~${it.end}" }
                            }
                            val tax = when (m.insurance.tax) { StaffTax.BIZ -> " · 3.3%"; StaffTax.DAILY -> " · 일용"; StaffTax.NONE -> " · 소득세 안 뗌"; StaffTax.WAGE -> "" }
                            Text(form + (if (l.noAmount) "" else " ${Fmt.won(m.base)}") + week + (m.job.takeIf { it.isNotBlank() }?.let { " · $it" } ?: "") + tax +
                                (if (m.insurance.foreign) " · 외국인" else ""), style = MaterialTheme.typography.bodySmall)
                            if (l.noAmount && on[i]) MoneyField("$form 금액", typed[i].takeIf { it > 0 }, { v -> typed = typed.copyOf().also { a -> a[i] = v ?: 0L } })
                            val look = l.check.filterNot { it in common || (l.noAmount && typed[i] > 0 && it.endsWith("적어 주세요")) }
                            if (look.isNotEmpty()) ToneLine("확인: " + look.joinToString(", "))
                            if (on[i] && i !in fits) ToneLine("직원 요금제가 있어야 더할 수 있어요")
                        }
                    }
                }
                if (missing.isNotEmpty()) ToneLine("금액을 적으면 더할 수 있어요: ${missing.joinToString(", ") { lines[it].member.name }} · 빼려면 체크를 풀어요", tone = Tone.Plain)
                Text("더한 뒤 이름을 누르면 하나씩 고칠 수 있어요. 요일마다 시간이 다르면 거기서 바꿔 주세요.",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }, confirmButton = {
            TextButton(enabled = fits.isNotEmpty() && missing.isEmpty(), onClick = {
                onApply(fits.map { i ->
                    lines[i].let { l -> if (!l.noAmount) l else l.copy(member = l.member.copy(base = typed[i]), noAmount = false, check = l.check.filterNot { it.endsWith("적어 주세요") }) }
                }, chosen.size > fits.size)
            }) { Text("${fits.size}명 더하기") }
        }, dismissButton = { TextButton(onClick = onDismiss) { Text("그만두기") } })
}
