package com.bamnat.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * 2.0.0 [RVN-20] 검토 I10aa-02 '넣기 전 요약 + 되돌리기' 공통 부품: what goes in, before it does. 회사 설정·공개 근무주기·나눈 급여 방식이
 * all change my calendar or my pay, and each said it its own way (or not at all): now one look, [title] (바뀌는 것, or the one line
 * that matters most, as 이번 달 예상 세전 a → b), a row for each line that moves (앞 → 뒤), and when it can be undone, where:
 * the app's snackbar 되돌리기 that follows ([com.bamnat.app.ui.PutOnNotice.say] with its undo), which takes back only what this put in.
 *
 * Nothing when there is no line: a first setup has nothing to compare with.
 */
@Composable
fun ChangeSummary(
    lines: List<String>,
    modifier: Modifier = Modifier,
    title: String = "바뀌는 것",
    titleColor: Color = Color.Unspecified,
    /** Whether 되돌리기 follows once it went in (said under the lines). */
    undo: Boolean = false,
    /** At most this many rows; the rest as "그 밖에 n개". */
    max: Int = MAX_LINES,
) {
    if (lines.isEmpty() && title == TITLE) return
    Column(modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = titleColor, modifier = Modifier.semantics { heading() })
        lines.take(max).forEach { Text("· $it", style = MaterialTheme.typography.bodyMedium) }
        if (lines.size > max) Text("그 밖에 ${lines.size - max}개", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (undo) Text(UNDO_NOTE, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** "a → b", the one way a line that moves is written ([before] null: new). */
fun changeLine(label: String, before: String?, after: String): String =
    if (before == null) "새 $label · $after" else if (before == after) "$label · $after (같아요)" else "$label · $before → $after"

private const val TITLE = "바뀌는 것"
private const val MAX_LINES = 6
const val UNDO_NOTE = "넣은 뒤 화면 아래 ‘되돌리기’로 넣기 전으로 돌릴 수 있어요."
