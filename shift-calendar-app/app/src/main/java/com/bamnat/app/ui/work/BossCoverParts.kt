package com.bamnat.app.ui.work

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import com.bamnat.app.ui.components.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import com.bamnat.app.ui.components.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bamnat.app.appContainer
import com.bamnat.app.boss.BossCallSync
import com.bamnat.app.ui.Fmt
import com.bamnat.app.ui.components.AlertDialog
import com.bamnat.app.ui.components.SectionCard
import com.bamnat.core.boss.BossCover
import com.bamnat.core.boss.StaffMember
import com.bamnat.core.boss.StaffWork
import com.bamnat.core.boss.Workplace
import com.bamnat.core.team.TeamCall
import com.bamnat.core.team.TeamCover
import com.bamnat.core.time.Today
import com.bamnat.core.work.StaffShift
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import com.bamnat.app.ui.theme.bamnat

private fun share(context: android.content.Context, text: String) {
    val i = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
    runCatching { context.startActivity(Intent.createChooser(i, "대타 요청 보내기")) }
}

/**
 * 1.38 [P3-X-01] 사장님 대타 찾기 ①: who could come, cheapest first, each with what it costs by the ledger itself (인건비·4대보험
 * 사업주 몫까지). 넣기 writes the 대근 now (전화로 정했을 때); 요청 보내기 sends each ticked person a link.
 */
@Composable
internal fun BossCoverDialog(place: Workplace, staff: List<StaffMember>, work: List<StaffWork>, absentId: String, date: LocalDate, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val app = context.appContainer
    val scope = rememberCoroutineScope()
    // [RVJ-101] 검토 U05c-08: the hours are the day's own (RVJ-85), and the 사장님 can change them before asking or putting anyone in. Kept as
    // "HH:mm-HH:mm" so a turned screen keeps them.
    val own = remember(staff, work, absentId, date) { BossCover.gapShift(staff, work, absentId, date) }
    var set by rememberSaveable(absentId, date) { mutableStateOf("") }
    val shift = remember(set, own) { hoursOf(set) ?: own }
    var picking by remember { mutableStateOf<Boolean?>(null) }
    val list = remember(place, staff, work, absentId, date, shift) { BossCover.candidates(place, staff, work, absentId, date, shift) }
    var picked by remember(list) { mutableStateOf(list.filter { it.ok }.take(3).map { it.staff.id }.toSet()) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf<String?>(null) }
    val absent = staff.firstOrNull { it.id == absentId }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("대신 일할 사람") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("${Fmt.dayTitle(date)} " + (shift?.let { "${Fmt.time(it.start)}~${Fmt.time(it.end)}, " } ?: "") + "${absent?.name?.ifBlank { "직원" } ?: "?"}님 대신",
                style = MaterialTheme.typography.bodyMedium)
            Text("그날 쉬는 직원만, 급여 대장과 같은 계산으로 더 드는 돈이 적은 순이에요(4대보험 사장님 몫 포함).",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { picking = true }) { Text(if (shift == null) "시간 정하기" else "시간 고치기") }
                if (set.isNotEmpty() && own != null) TextButton(onClick = { set = "" }) { Text("원래 시간으로") }
            }
            if (shift == null) Text("이날 평소 근무 시간이 없어요. 시간을 정하면 찾을 수 있어요.", style = MaterialTheme.typography.bodyMedium)
            else if (list.isEmpty()) Text("이날 쉬는 직원이 없어요.", style = MaterialTheme.typography.bodyMedium)
            list.forEach { cand ->
                val on = cand.staff.id in picked
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(on, role = Role.Checkbox) { v ->
                    picked = if (v) picked + cand.staff.id else picked - cand.staff.id
                }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = on, onCheckedChange = null)
                    Column(Modifier.weight(1f)) {
                        Text(cand.staff.name.ifBlank { "직원" } + (if (cand.ok) "" else " ⚠") + " · +${Fmt.won(cand.extra)}", fontWeight = FontWeight.Medium,
                            color = if (cand.ok) MaterialTheme.colorScheme.onSurface else MaterialTheme.bamnat.caution) // 2.0.0 [RVJ-66] (HQ-103, 조건 6) 고르기 전의 걸림(⚠과 까닭 글): 주의
                        Text((cand.why + cand.notes).ifEmpty { listOf("괜찮아요") }.joinToString(" · ") + " · 그 주 ${cand.weekMinutes / 60}시간",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(enabled = !busy, onClick = {
                        BossCover.cover(staff, work, absentId, date, cand.staff.id, shift)?.let { row -> app.appScope.launch { app.phaseThree.setWork(row.staffId, date, row) } }
                        onDismiss()
                    }) { Text("넣기") }
                }
            }
            // 2.0.0 [RVJ-43] 검토 PC-105 같은 구조: a send that did not go is said on the one 알림 줄 (주의), not in red (조건 6).
            failed?.let { com.bamnat.app.ui.components.ToneLine(it) }
        }
    }, confirmButton = {
        TextButton(enabled = picked.isNotEmpty() && shift != null && !busy, onClick = {
            busy = true
            scope.launch {
                val gap = TeamCover.Gap(absentId, date, 0)
                runCatching { BossCallSync.open(app, place, gap, shift!!, list.map { it.staff }.filter { it.id in picked }) }
                    .onSuccess { (_, k) -> share(context, BossCallSync.message(gap, shift!!, k, staff)); onDismiss() }
                    .onFailure { e -> failed = com.bamnat.app.share.linkProblem(e) ?: "요청 링크를 못 만들었어요. 인터넷이 될 때 다시 눌러 주세요." }
                busy = false
            }
        }) { Text("${picked.size}명에게 요청 보내기") }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("닫기") } })
    picking?.let { isStart ->
        val now = shift ?: StaffShift(LocalTime.of(9, 0), LocalTime.of(18, 0))
        com.bamnat.app.ui.components.TimePickerDialog(title = if (isStart) "몇 시부터" else "몇 시까지", initial = if (isStart) now.start else now.end,
            onDismiss = { picking = null }) { t ->
            set = if (isStart) "$t-${now.end}" else "${now.start}-$t"
            picking = if (isStart) false else null
        }
    }
}

/** [RVJ-101] "HH:mm-HH:mm" as the hours it holds; blank or unreadable → none. */
private fun hoursOf(s: String): StaffShift? = s.takeIf { it.contains('-') }?.let { h ->
    runCatching { StaffShift(LocalTime.parse(h.substringBefore('-')), LocalTime.parse(h.substringAfter('-'))) }.getOrNull()
}

/** 1.38 [P3-X-01] 대타 요청 ②: who has seen it, who said yes, and the first yes one press from the 대근 row. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BossCallCard(place: Workplace, call: TeamCall, staff: List<StaffMember>, work: List<StaffWork>,
                          /** 2.0.0 [RVJ-95] The 관리 화면's 할 일 '대타: …님이 들어갈 수 있어요' brings the card into view with it. */ modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val app = context.appContainer
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    val names = staff.associateBy { it.id }
    fun name(id: String) = names[id]?.name?.ifBlank { "직원" } ?: "?"
    // [RVJ-101] The hours asked for are the call's own (set before sending); an older call has none and reads the day's.
    val shift = call.hours ?: BossCover.gapShift(staff, work, call.gap.memberId, call.gap.date)
    SectionCard(title = "대타 ${Fmt.monthDay(call.gap.date)} · ${name(call.gap.memberId)}", modifier = modifier) {
        call.taken?.let { t ->
            Text("${name(t)}님이 들어가요. 급여 대장에 대근으로 넣었어요.", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            TextButton(onClick = { scope.launch { BossCallSync.end(app, place.id, call.id) } }) { Text("이 카드 닫기") }
            return@SectionCard
        }
        val past = call.expiresAt in 1 until Today.millis()
        Text("물어본 ${call.people.size}명 · 본 사람 ${call.seen.size}명 · 된다 ${call.answers.count { it.value }}명" + if (past) " · 기한 지남" else "",
            style = MaterialTheme.typography.bodyMedium)
        call.people.forEach { id ->
            val a = call.answers[id]
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("${name(id)}: " + when { a == true -> "들어갈 수 있어요"; a == false -> "어려워요"; id in call.seen -> "열어 봤어요, 아직 답 없음"; else -> "아직 안 열어 봤어요" },
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f),
                    color = if (a == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                if (a == true) Button(enabled = !busy, onClick = {
                    BossCover.cover(staff, work, call.gap.memberId, call.gap.date, id, call.hours)?.let { row -> app.appScope.launch { app.phaseThree.setWork(row.staffId, row.date, row) } }
                    app.appScope.launch { BossCallSync.take(app, place.id, call.id, id) }
                }) { Text("${name(id)}님 넣기") }
            }
        }
        // [RVJ-43] 검토 PC-105 같은 구조: 알림 줄 한 모양 (조건 3·6).
        note?.let { com.bamnat.app.ui.components.ToneLine(it) }
        if (call.token == null) com.bamnat.app.ui.components.ToneLine("이 휴대폰에 요청 링크 열쇠가 없어요. 다시 보내 주세요.")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(enabled = !busy && call.token != null, onClick = {
                busy = true
                scope.launch {
                    note = runCatching { BossCallSync.pull(app, place.id, call.id) }.exceptionOrNull()?.let {
                        if (it.message == "not_found") "요청이 서버에 없어요. 기한이 지났거나 거둔 요청이에요." else "인터넷이 될 때 다시 가져와 주세요."
                    }
                    busy = false
                }
            }) { Text("답 가져오기") }
            if (shift != null) OutlinedButton(onClick = { share(context, BossCallSync.message(call.gap, shift, call, staff)) }) { Text("링크 다시 보내기") }
            TextButton(enabled = !busy, onClick = { scope.launch { BossCallSync.end(app, place.id, call.id) } }) { Text("요청 거두기") }
        }
    }
}
