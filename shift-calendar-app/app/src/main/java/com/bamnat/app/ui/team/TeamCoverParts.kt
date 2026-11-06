package com.bamnat.app.ui.team

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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bamnat.app.ui.Fmt
import com.bamnat.app.ui.components.AlertDialog
import com.bamnat.app.ui.components.SectionCard
import com.bamnat.core.team.Team
import com.bamnat.core.team.TeamCall
import com.bamnat.core.team.TeamCover
import com.bamnat.core.team.TeamMember
import com.bamnat.core.time.Today

/**
 * 1.38 [P3-X-01] 결원 메우기 ①: who could come instead, best first, with what weighs on each. The ones who break nothing are
 * ticked (up to three); the leader may put someone in right away (전화로 이미 정했을 때) or send them all one ask.
 */
@Composable
internal fun CoverDialog(
    team: Team, gap: TeamCover.Gap, absent: TeamMember?, list: List<TeamCover.Candidate>, busy: Boolean,
    onDismiss: () -> Unit, onPut: (TeamMember) -> Unit, onAsk: (List<TeamMember>) -> Unit,
) {
    val shift = team.shifts.firstOrNull { it.id == gap.shiftId }
    var picked by remember(list) { mutableStateOf(list.filter { it.ok }.take(3).map { it.member.id }.toSet()) }
    var showAll by remember(list) { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("결원 메우기") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("${Fmt.dayTitle(gap.date)} ${shift?.name ?: ""}, ${absent?.name ?: "?"}님 대신 들어갈 사람", style = MaterialTheme.typography.bodyMedium)
            Text("규칙(설정 › 근무표 점검 기준)·지난달 끝 근무·주 52시간·신청한 날을 다 보고, 이번 달 덜 일한 사람을 앞에 뒀어요.",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (list.isEmpty()) Text("이날 쉬는 사람이 없어요.", style = MaterialTheme.typography.bodyMedium)
            val shown = if (showAll) list else list.take(8)
            shown.forEach { cand ->
                val on = cand.member.id in picked
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(on, role = Role.Checkbox) { v ->
                    picked = if (v) picked + cand.member.id else picked - cand.member.id
                }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = on, onCheckedChange = null)
                    Column(Modifier.weight(1f)) {
                        Text(cand.member.name + if (cand.ok) "" else " ⚠", fontWeight = FontWeight.Medium,
                            color = if (cand.ok) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error)
                        Text((if (cand.ok) "괜찮아요" else cand.why.joinToString(" · ")) + " · 이번 달 ${cand.minutes / 60}시간" +
                            (if (cand.sameKind > 0) " · ${shift?.label ?: ""} ${cand.sameKind}번" else ""),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { onPut(cand.member) }, enabled = !busy) { Text("넣기") }
                }
            }
            if (list.size > 8 && !showAll) TextButton(onClick = { showAll = true }) { Text("${list.size - 8}명 더 보기") }
            Text("요청을 보내면 고른 사람마다 링크가 가요. '들어갈 수 있어요'가 오면 여기서 한 번에 넣어요.",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }, confirmButton = {
        TextButton(enabled = picked.isNotEmpty() && !busy, onClick = { onAsk(list.map { it.member }.filter { it.id in picked }) }) {
            Text("${picked.size}명에게 요청 보내기")
        }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("닫기") } })
}

/**
 * 1.38 [P3-X-01] 결원 메우기 ②: an ask on its way, who has seen it and who said yes. The first yes sits on top with its button, so
 * putting them in is one press.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CallCard(
    team: Team, call: TeamCall, members: List<TeamMember>, busy: Boolean,
    onPull: () -> Unit, onSend: () -> Unit, onTake: (TeamMember) -> Unit, onEnd: () -> Unit,
) {
    val names = members.associateBy { it.id }
    val shift = team.shifts.firstOrNull { it.id == call.gap.shiftId }
    val absent = names[call.gap.memberId]?.name ?: "?"
    val past = call.expiresAt in 1 until Today.millis()
    SectionCard(title = "결원 ${Fmt.monthDay(call.gap.date)} ${shift?.label ?: ""} · $absent") {
        val taken = call.taken?.let(names::get)
        if (taken != null) {
            Text("${taken.name}님이 들어가요. 표에 넣었어요.", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text("확정을 다시 누르면 두 사람 링크도 바뀐 근무로 고쳐져요.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onEnd) { Text("이 카드 닫기") }
            return@SectionCard
        }
        val yes = call.people.filter { call.answers[it] == true }
        Text("물어본 ${call.people.size}명 · 본 사람 ${call.seen.size}명 · 된다 ${yes.size}명" + if (past) " · 기한 지남" else "", style = MaterialTheme.typography.bodyMedium)
        call.people.forEach { id ->
            val a = call.answers[id]
            val word = when { a == true -> "들어갈 수 있어요"; a == false -> "어려워요"; id in call.seen -> "봤어요, 아직 답 없음"; else -> "아직 안 봤어요" }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("${names[id]?.name ?: "?"}: $word", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f),
                    color = if (a == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (a == true) FontWeight.Medium else null)
                if (a == true) names[id]?.let { m -> Button(onClick = { onTake(m) }, enabled = !busy) { Text("${m.name}님 넣기") } }
            }
        }
        if (call.token == null) Text("이 휴대폰에 요청 링크 열쇠가 없어요(암호 없는 백업에서 되살림). 다시 보내 주세요.", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = onPull, enabled = !busy && call.token != null) { Text("답 가져오기") }
            OutlinedButton(onClick = onSend, enabled = call.urls.isNotEmpty()) { Text("링크 다시 보내기") }
            TextButton(onClick = onEnd, enabled = !busy) { Text("요청 거두기") }
        }
    }
}
