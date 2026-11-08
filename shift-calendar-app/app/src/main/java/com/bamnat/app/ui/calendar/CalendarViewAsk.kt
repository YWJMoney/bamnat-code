package com.bamnat.app.ui.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 1.39 [CALX-07] (사용자 2026-11-08 "달력만 보기가 기본값, 카드 보기가 선택값, 사용자가 처음 달력 칸을 볼 때 고르게"): once, over the
 * month of a new install, which way the calendar shows. It starts on 달력만; one tap keeps it or brings the cards (오늘의 흐름, 하루 카드,
 * 이번 달 요약) in. The ✕ keeps what is on screen. Either answer puts the line away for good; 달력 › 도구 and 설정 › 달력 change it later.
 */
@Composable
internal fun CalendarViewAsk(calendarOnly: Boolean, onAnswer: (calendarOnly: Boolean) -> Unit, modifier: Modifier = Modifier) {
    val words = calendarViewAskWords(calendarOnly)
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(words.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                IconButton(onClick = { onAnswer(calendarOnly) }) { Icon(Icons.Filled.Close, "이대로 두고 닫기") }
            }
            Text(words.body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 12.dp))
            Row(Modifier.fillMaxWidth().padding(top = 6.dp, end = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onAnswer(calendarOnly) }) { Text(words.keep) }
                FilledTonalButton(onClick = { onAnswer(!calendarOnly) }) { Text(words.change) }
            }
        }
    }
}

internal data class CalendarViewAskWords(val title: String, val body: String, val keep: String, val change: String)

/** [CALX-07] The line's words for the view on screen now. */
internal fun calendarViewAskWords(calendarOnly: Boolean): CalendarViewAskWords = if (calendarOnly) CalendarViewAskWords(
    title = "달력만 크게 보고 있어요",
    body = "오늘의 흐름·하루 카드·이번 달 요약을 달력 위아래에 함께 볼 수도 있어요. 나중에 달력 › 도구나 설정 › 달력에서 바꿔요.",
    keep = "달력만 볼게요",
    change = "카드도 함께 보기",
) else CalendarViewAskWords(
    title = "달력과 카드를 함께 보고 있어요",
    body = "달력만 크게 볼 수도 있어요. 나중에 달력 › 도구나 설정 › 달력에서 바꿔요.",
    keep = "이대로 볼게요",
    change = "달력만 보기",
)
