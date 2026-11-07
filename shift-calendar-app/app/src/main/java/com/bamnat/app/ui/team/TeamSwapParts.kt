package com.bamnat.app.ui.team

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bamnat.app.ui.Fmt
import com.bamnat.app.ui.components.SectionCard
import com.bamnat.core.schedule.RestRules
import com.bamnat.core.team.SwapRound
import com.bamnat.core.team.Team
import com.bamnat.core.team.TeamBoard
import com.bamnat.core.team.TeamCheck
import com.bamnat.core.team.TeamMember
import com.bamnat.core.team.TeamSwap

/**
 * 1.38 [P3-X-02] 바꾸기 요청 카드: the trades both sides agreed to on top, each with what the board would say after it (checked again
 * here, since the board may have moved since the list went up), then the ones still waiting on the other person.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SwapCard(
    team: Team, round: SwapRound, board: TeamBoard, rules: RestRules, busy: Boolean,
    onDecide: (TeamSwap.Ask, Boolean) -> Unit, onPull: () -> Unit, onSend: () -> Unit, onRefresh: () -> Unit, onEnd: () -> Unit,
) {
    val names = board.members.associate { it.id to it.name }
    val labels = team.shifts.associate { it.id to it.label }
    fun label(id: String, a: TeamSwap.Ask) = board.cell(id, a.date)?.shiftId?.let(labels::get) ?: "·"
    val agreed = round.asks.filter { it.state == TeamSwap.State.YES }
    val waiting = round.asks.filter { it.state == TeamSwap.State.ASKED }
    val settled = round.asks.filter { it.state == TeamSwap.State.DONE || it.state == TeamSwap.State.DENIED }
    SectionCard(title = "${round.month.monthValue}월 바꾸기 요청" + if (agreed.isNotEmpty()) " · 승인할 것 ${agreed.size}" else "") {
        Text("요청한 사람 ${round.asks.map { it.from }.toSet().size}명 · 기다리는 것 ${waiting.size} · 끝난 것 ${settled.size}", style = MaterialTheme.typography.bodyMedium)
        agreed.forEach { a ->
            val signs = TeamCheck.swapCheck(team, board, a.from, a.to, a.date, rules).filter { it.kind == TeamCheck.Kind.RULE || it.kind == TeamCheck.Kind.WEEK_HOURS }
            val locked = board.cell(a.from, a.date)?.locked == true || board.cell(a.to, a.date)?.locked == true
            Text("${Fmt.monthDay(a.date)} ${names[a.from] ?: "?"} ${label(a.from, a)} ↔ ${names[a.to] ?: "?"} ${label(a.to, a)} · 둘 다 좋대요",
                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            signs.forEach { Text("⚠ " + it.text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            if (locked) Text("잠긴 칸이 있어요. 잠금을 풀어야 바꿀 수 있어요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Button(onClick = { onDecide(a, true) }, enabled = !busy && !locked) { Text("승인") }
                TextButton(onClick = { onDecide(a, false) }, enabled = !busy) { Text("거절") }
            }
        }
        if (waiting.isNotEmpty()) {
            Text("상대 답 기다림", style = MaterialTheme.typography.labelLarge)
            waiting.forEach { a -> Text("${Fmt.monthDay(a.date)} ${names[a.from] ?: "?"} → ${names[a.to] ?: "?"}", style = MaterialTheme.typography.bodySmall) }
        }
        if (round.token == null) Text("이 휴대폰에 바꾸기 링크 열쇠가 없어요(암호 없는 백업에서 되살림). 새로 열어 주세요.", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error)
        Text("승인하면 표가 바로 바뀌고 팀원 링크의 바꿀 수 있는 날도 새로 맞춰요. 근무표 링크는 확정을 다시 누르면 고쳐져요.",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = onPull, enabled = !busy && round.token != null) { Text("요청 가져오기") }
            OutlinedButton(onClick = onSend) { Text("링크 다시 보내기") }
            OutlinedButton(onClick = onRefresh, enabled = !busy && round.token != null) { Text("고친 표 올리기") }
            TextButton(onClick = onEnd, enabled = !busy) { Text("바꾸기 끝내기") }
        }
    }
}

/** Who to send the links to: the month's active members. */
internal fun swapPeople(board: TeamBoard): List<TeamMember> = board.members.filter { it.active }
