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
import com.bamnat.app.ui.theme.color
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import com.bamnat.app.ui.components.TimePickerDialog
import com.bamnat.core.boss.StaffMember
import com.bamnat.core.hours.HoursFormat
import com.bamnat.core.work.StaffShift
import com.bamnat.core.time.Today
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/**
 * 1.38 가져오기 (사용자 2026-11-05 "어떤 형식이든"): the staff list read from a file or photo, one line a person with what was read
 * (형태·금액·요일·시간) and what to look at. Names already in the ledger are left out unless ticked. [room] is how many
 * more the 직원 요금제 lets in now; past it the lines say so and [onApply] gets only the ones that fit.
 * 2.0.0 [RVJ-40] 검토 PC-102: a person whose sum was not read comes with an empty 금액 field to fill here (no 최저시급 guess), and
 * the list cannot be added until each ticked one has a sum. The columns the list has but the reader does not ([unread]) are named
 * at the top; what to look at is a [ToneLine], the 소득세·외국인 read are in the line. It is a [FormSheet] now (a list with fields).
 * 2.0.0 [RVJ-97] 검토 U05c-02: a line opens where it is (one at a time) to change 시급·일급·월급, the sum, the usual week and
 * 소득세 떼는 법, so a wrong read is fixed here and not one by one after adding. The 4대보험 the law puts each in is said under the
 * line and worked out again from what is changed ([StaffImport.edit]); [on] is the day the month's 최저시급 is read on, as the lines.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StaffImportDialog(
    lines: List<StaffImport.Line>, fromPhoto: Boolean, room: Int, unread: List<String> = emptyList(),
    on: LocalDate = Today.date(), legalBreaks: Boolean = true,
    onDismiss: () -> Unit, onApply: (List<StaffImport.Line>, Boolean) -> Unit,
) {
    val ticked = remember(lines) { mutableStateListOf<Boolean>().apply { addAll(lines.map { !it.already }) } }
    // [RVJ-40][RVJ-97] What is typed or changed on each line ("" when nothing), kept through a rotation (조건 10).
    var edits by rememberSaveable(lines) { mutableStateOf(Array(lines.size) { "" }) }
    var open by rememberSaveable(lines) { mutableStateOf(-1) }
    var picking by remember { mutableStateOf<Pair<DayOfWeek, Boolean>?>(null) }
    val cur = remember(lines, edits, on, legalBreaks) {
        lines.mapIndexed { i, l -> StaffImport.Edit.decode(edits[i])?.let { StaffImport.edit(l, it, on, legalBreaks) } ?: l }
    }
    fun change(i: Int, e: StaffImport.Edit) {
        edits = edits.copyOf().also { a -> a[i] = if (e == StaffImport.Edit.of(lines[i].member)) "" else e.encode() }
    }
    val chosen = lines.indices.filter { ticked[it] }
    val fits = chosen.take(room.coerceAtLeast(0))
    val missing = fits.filter { cur[it].noAmount }
    // [RVJ-40] What every line has to look at (a list with no 요일 칸 said '근무 요일·시간이 없어요' five times) is said once at the top;
    // a missing sum stays on its own line, by its field.
    val common = remember(cur) {
        if (cur.size < 2) emptySet() else cur.map { l -> l.check.filterNot { it.endsWith("적어 주세요") }.toSet() }.reduce { a, b -> a intersect b }
    }
    // [RVJ-40] A list with fields to type in is a sheet, not a dialog in the middle (공통 부품 5절); back or X asks once a sum is typed.
    FormSheet(onDismissRequest = onDismiss, unsaved = edits.any { it.isNotEmpty() },
        title = { Text("직원 명단 가져오기") }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${lines.size}명을 읽었어요. 연락처·주민번호·계좌 칸은 읽지 않아요." + if (fromPhoto) " 사진에서 읽은 것이라 ‘확인’ 줄은 꼭 봐 주세요." else "",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (unread.isNotEmpty()) ToneLine("읽지 않은 칸: ${unread.joinToString(", ")} · 더한 뒤 직원 정보에서 넣어요", tone = Tone.Plain)
                if (common.isNotEmpty()) ToneLine("확인: 모두 ${common.joinToString(", ")}")
                lines.forEachIndexed { i, read ->
                    val l = cur[i]
                    val m = l.member
                    val form = formWord(m.form)
                    val opened = open == i
                    Row(verticalAlignment = Alignment.Top) {
                        Checkbox(checked = ticked[i], onCheckedChange = { ticked[i] = it })
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            // [RVJ-97] The line is the way in: a tap opens it to change what was read (조건 8: 고치기 한 번).
                            Column(Modifier.fillMaxWidth().clickable(onClickLabel = if (opened) "접기" else "고치기") { open = if (opened) -1 else i },
                                verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(m.name + if (read.already) " · 이미 있어요" else "", style = MaterialTheme.typography.bodyMedium)
                                val week = m.weekly.entries.sortedBy { it.key }.let { w ->
                                    if (w.isEmpty()) "" else " · " + w.joinToString("") { Fmt.dayOfWeekShort(it.key) } + " " + w.first().value.let { "${Fmt.time(it.start)}~${Fmt.time(it.end)}" } +
                                        (if (w.map { it.value }.distinct().size > 1) " 등" else "") +
                                        " · 주 " + HoursFormat.korean(w.sumOf { it.value.paidMinutes(legalBreaks) })
                                }
                                val tax = when (m.insurance.tax) { StaffTax.BIZ -> " · 3.3%"; StaffTax.DAILY -> " · 일용"; StaffTax.NONE -> " · 소득세 안 뗌"; StaffTax.WAGE -> "" }
                                Text(form + (if (l.noAmount) "" else " ${Fmt.won(m.base)}") + week + (m.job.takeIf { it.isNotBlank() }?.let { " · $it" } ?: "") + tax +
                                    (if (m.insurance.foreign) " · 외국인" else ""), style = MaterialTheme.typography.bodySmall)
                                if (m.insurance.tax != StaffTax.NONE) Text(StaffImport.insuranceSay(m, legalBreaks), style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (read.noAmount && ticked[i] && !opened) MoneyField("$form 금액", m.base.takeIf { it > 0 }, { v ->
                                change(i, StaffImport.Edit.of(m).copy(base = v ?: 0L))
                            })
                            if (opened) LineEditor(m, legalBreaks, edited = edits[i].isNotEmpty(), onChange = { change(i, it) },
                                onPick = { day, start -> picking = day to start }, onReset = { change(i, StaffImport.Edit.of(read.member)) })
                            val look = l.check.filterNot { it in common || (it.endsWith("적어 주세요") && (opened || read.noAmount && ticked[i])) }
                            if (look.isNotEmpty()) ToneLine("확인: " + look.joinToString(", "))
                            if (ticked[i] && i !in fits) ToneLine("직원 요금제가 있어야 더할 수 있어요")
                        }
                        IconButton(onClick = { open = if (opened) -1 else i }) {
                            Icon(if (opened) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, if (opened) "${m.name} 접기" else "${m.name} 고치기",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (missing.isNotEmpty()) ToneLine("금액을 적으면 더할 수 있어요: ${missing.joinToString(", ") { lines[it].member.name }} · 빼려면 체크를 풀어요", tone = Tone.Plain)
                Text("줄을 누르면 시급·요일·소득세를 여기서 고쳐요. 4대보험은 고친 대로 법에 맞춰 채워요.",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }, confirmButton = {
            TextButton(enabled = fits.isNotEmpty() && missing.isEmpty(), onClick = { onApply(fits.map { cur[it] }, chosen.size > fits.size) }) { Text("${fits.size}명 더하기") }
        }, dismissButton = { TextButton(onClick = onDismiss) { Text("그만두기") } })
    picking?.let { (day, isStart) ->
        val i = open
        val m = cur.getOrNull(i)?.member
        val sh = m?.weekly?.get(day)
        if (m != null && sh != null) TimePickerDialog(title = "${Fmt.dayOfWeekShort(day)}요일 ${if (isStart) "시작" else "끝"}", initial = if (isStart) sh.start else sh.end,
            onDismiss = { picking = null }) { t ->
            val next = if (isStart) sh.copy(start = t) else sh.copy(end = t)
            if (next.start != next.end) change(i, StaffImport.Edit.of(m).copy(weekly = m.weekly + (day to next)))
            picking = null
        }
    }
}

private fun formWord(f: WageForm) = when (f) { WageForm.HOUR -> "시급"; WageForm.DAY -> "일급"; WageForm.MONTH -> "월급" }

/**
 * [RVJ-97] One read line opened: the same chips and fields as 직원 정보 (형태, 금액, 요일과 시간, 소득세), in the order a 사장님
 * reads a list. '읽은 대로' puts back what the file said.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LineEditor(m: StaffMember, legalBreaks: Boolean, edited: Boolean, onChange: (StaffImport.Edit) -> Unit,
                       onPick: (DayOfWeek, Boolean) -> Unit, onReset: () -> Unit) {
    val e = StaffImport.Edit.of(m)
    Column(Modifier.padding(top = 4.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            WageForm.entries.forEach { f -> FilterChip(selected = m.form == f, onClick = { onChange(e.copy(form = f)) }, label = { Text(formWord(f)) }) }
        }
        MoneyField("${formWord(m.form)} 금액", m.base.takeIf { it > 0 }, { v -> onChange(e.copy(base = v ?: 0L)) })
        Text("일하는 요일", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DayOfWeek.entries.forEach { d ->
                FilterChip(selected = d in m.weekly, label = { Text(Fmt.dayOfWeekShort(d)) }, onClick = {
                    onChange(e.copy(weekly = if (d in m.weekly) m.weekly - d else m.weekly + (d to (m.weekly.values.lastOrNull() ?: StaffShift(LocalTime.of(9, 0), LocalTime.of(18, 0))))))
                })
            }
        }
        DayOfWeek.entries.filter { it in m.weekly }.forEach { d ->
            val sh = m.weekly.getValue(d)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(Fmt.dayOfWeekShort(d), modifier = Modifier.width(28.dp))
                TextButton(onClick = { onPick(d, true) }) { Text(Fmt.time(sh.start)) }
                Text("~")
                TextButton(onClick = { onPick(d, false) }) { Text(Fmt.time(sh.end)) }
                Text(HoursFormat.korean(sh.paidMinutes(legalBreaks)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text("소득세 떼는 법", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StaffTax.entries.forEach { t -> FilterChip(selected = m.insurance.tax == t, onClick = { onChange(e.copy(tax = t)) }, label = { Text(t.label) }) }
        }
        if (edited) TextButton(onClick = onReset) { Text("읽은 대로 되돌리기") }
    }
}
