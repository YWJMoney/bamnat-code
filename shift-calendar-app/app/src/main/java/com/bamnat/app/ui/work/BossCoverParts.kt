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
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch
import java.time.LocalDate

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
    val shift = remember(staff, work, absentId, date) { BossCover.gapShift(staff, work, absentId, date) }
    val list = remember(place, staff, work, absentId, date) { BossCover.candidates(place, staff, work, absentId, date) }
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
            if (shift == null) Text("이날 평소 근무 시간이 없어요. 직원 정보에서 평소 근무를 넣으면 찾을 수 있어요.", style = MaterialTheme.typography.bodyMedium)
            else if (list.isEmpty()) Text("이날 쉬는 직원이 없어요.", style = MaterialTheme.typography.bodyMedium)
            list.forEach { cand ->
                val on = cand.staff.id in picked
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(on, role = Role.Checkbox) { v ->
                    picked = if (v) picked + cand.staff.id else picked - cand.staff.id
                }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = on, onCheckedChange = null)
                    Column(Modifier.weight(1f)) {
                        Text(cand.staff.name.ifBlank { "직원" } + (if (cand.ok) "" else " ⚠") + " · +${Fmt.won(cand.extra)}", fontWeight = FontWeight.Medium,
                            color = if (cand.ok) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error)
                        Text((cand.why + cand.notes).ifEmpty { listOf("괜찮아요") }.joinToString(" · ") + " · 그 주 ${cand.weekMinutes / 60}시간",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(enabled = !busy, onClick = {
                        BossCover.cover(staff, work, absentId, date, cand.staff.id)?.let { row -> app.appScope.launch { app.phaseThree.setWork(row.staffId, date, row) } }
                        onDismiss()
                    }) { Text("넣기") }
                }
            }
            failed?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
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
}

/** 1.38 [P3-X-01] 대타 요청 ②: who has seen it, who said yes, and the first yes one press from the 대근 row. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BossCallCard(place: Workplace, call: TeamCall, staff: List<StaffMember>, work: List<StaffWork>) {
    val context = LocalContext.current
    val app = context.appContainer
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    val names = staff.associateBy { it.id }
    fun name(id: String) = names[id]?.name?.ifBlank { "직원" } ?: "?"
    val shift = BossCover.gapShift(staff, work, call.gap.memberId, call.gap.date)
    SectionCard(title = "대타 ${Fmt.monthDay(call.gap.date)} · ${name(call.gap.memberId)}") {
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
                Text("${name(id)}: " + when { a == true -> "들어갈 수 있어요"; a == false -> "어려워요"; id in call.seen -> "봤어요, 아직 답 없음"; else -> "아직 안 봤어요" },
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f),
                    color = if (a == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                if (a == true) Button(enabled = !busy, onClick = {
                    BossCover.cover(staff, work, call.gap.memberId, call.gap.date, id)?.let { row -> app.appScope.launch { app.phaseThree.setWork(row.staffId, row.date, row) } }
                    app.appScope.launch { BossCallSync.take(app, place.id, call.id, id) }
                }) { Text("${name(id)}님 넣기") }
            }
        }
        note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        if (call.token == null) Text("이 휴대폰에 요청 링크 열쇠가 없어요. 다시 보내 주세요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
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
