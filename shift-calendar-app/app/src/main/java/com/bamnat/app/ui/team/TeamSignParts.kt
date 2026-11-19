package com.bamnat.app.ui.team

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bamnat.app.ui.components.AlertDialog
import com.bamnat.app.ui.components.ToneLine
import com.bamnat.app.ui.components.boldNumbers
import com.bamnat.app.ui.theme.Tone
import com.bamnat.core.team.TeamCheck

/**
 * 2.0.0 [RVJ-68] (총괄 HQ-107, 디자인 품질 조건 6) What a sign of the 팀 근무표 is told with, the same on the 확인할 것 card, a cell's
 * window, the window after a 맞바꾸기 and a 바꾸기 요청: the mark's colour says how much it matters and the words stay quiet.
 * 위험 (red) is only 주 52시간, which the law sets (근로기준법 제53조, a 5인 이상 사업장: [com.bamnat.core.schedule.RestRules.weekLimit]);
 * the team's own 기준 (역순·휴식·연속·야간 뒤 쉬는 날) and 인원 부족 are 주의 (amber, as the board's short days); 책임자 없음 is an 안내.
 */
internal fun TeamCheck.Kind.tone(): Tone = when (this) {
    TeamCheck.Kind.WEEK_HOURS -> Tone.Danger
    TeamCheck.Kind.RULE, TeamCheck.Kind.SHORT -> Tone.Caution
    TeamCheck.Kind.NO_CHARGE -> Tone.Plain
}

/** A sign in a few words: "준호 · N-D · 근무 사이 11시간을 못 쉼 · 나이트 뒤 데이 8시간만 쉬어요" ([who] left out where the name is already said). */
internal fun TeamCheck.Sign.words(who: Boolean = true): String =
    if (topic.isEmpty()) text else listOf(if (who) this.who else "", topic, detail).filter { it.isNotEmpty() }.joinToString(" · ")

/** [signs] as quiet lines, each with its mark; equal sorts one after another as [TeamCheck.summary] puts them. */
@Composable
internal fun SignLines(signs: List<TeamCheck.Sign>, who: Boolean = true) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        signs.sortedBy { TeamCheck.ORDER.indexOf(it.kind) }.forEach { s -> ToneLine(boldNumbers(s.words(who)), tone = s.kind.tone()) }
    }
}

/** The window a cell, a short day or a 맞바꾸기 opens: [title] and its signs, with the same marks as the 확인할 것 card. */
@Composable
internal fun SignsDialog(title: String, signs: List<TeamCheck.Sign>, who: Boolean = true, note: String? = null, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SignLines(signs, who)
            note?.let { Text(it) }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("알겠어요") } })
}
