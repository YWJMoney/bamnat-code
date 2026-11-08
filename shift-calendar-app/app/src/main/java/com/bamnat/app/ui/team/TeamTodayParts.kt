package com.bamnat.app.ui.team

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.bamnat.app.ui.theme.LocalCalendarColors
import com.bamnat.core.team.TeamToday

/**
 * 1.39 [FLOW-23] 팀 오늘 판 머리 (흐름 디자인 3-11 '한 숫자 머리, 이야기 줄'): "오늘 출근 **12명** · 빈자리 1", and under it the day's
 * kinds in the order they start ("07:00 주간 6명 → 19:00 야간 6명"), only a kind short of 근무별 인원 in orange. Only on the leader's
 * phone, from the board it already holds; nothing about a member's sleep or mood (SLEEP-K).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TeamTodayHead(day: TeamToday.Day, modifier: Modifier = Modifier, word: String = "오늘") {
    val warn = LocalCalendarColors.current.warning
    val head = day.head(word)
    Column(modifier.semantics(mergeDescendants = true) { contentDescription = head + ". " + day.steps.joinToString(", ") { stepWords(it) } },
        verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(buildAnnotatedString {
            if (day.working == 0 && day.short == 0) { append(head); return@buildAnnotatedString }
            append("$word 출근 ")
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("${day.working}명") }
            if (day.short > 0) withStyle(SpanStyle(color = warn, fontWeight = FontWeight.Bold)) { append(" · 빈자리 ${day.short}") }
        }, style = MaterialTheme.typography.titleMedium)
        if (day.steps.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(2.dp),
            itemVerticalAlignment = Alignment.CenterVertically) {
            day.steps.forEachIndexed { i, s ->
                if (i > 0) Text("→", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stepWords(s), style = MaterialTheme.typography.bodySmall,
                    color = if (s.short > 0) warn else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (s.short > 0) FontWeight.Bold else null)
            }
        }
    }
}

private fun stepWords(s: TeamToday.Step): String =
    listOfNotNull(s.shift.start?.let { "%02d:%02d".format(it.hour, it.minute) }, s.shift.name).joinToString(" ") + " " +
        (if (s.short > 0) "${s.count}/${s.need}명" else "${s.count}명")
