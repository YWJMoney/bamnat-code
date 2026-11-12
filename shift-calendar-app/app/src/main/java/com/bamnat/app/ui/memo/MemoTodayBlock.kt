package com.bamnat.app.ui.memo

import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bamnat.app.memo.QuickDay
import com.bamnat.app.memo.quickLine
import com.bamnat.app.memo.quickPast
import com.bamnat.app.memo.quickSay
import com.bamnat.app.ui.theme.Tone
import com.bamnat.app.ui.theme.color
import com.bamnat.core.memo.Memo
import com.bamnat.core.memo.TaskList
import com.bamnat.core.time.Today
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 2.0.0 [RVI-77] (검토 U07a-02·U07a-03·I07ab-01, 대기열 TASK-01·03·40) The memo tab's '오늘' 묶음: the first thing the tab shows is what
 * is to do today — 늦음·오늘·고정, the same [TaskList] the 할 일 위젯 shows — each finished with its circle in one tap, and under them one
 * line to write the next one ("내일 오후 3시 치과", read as the widget's window reads it). Before, the first memo was some 330dp down,
 * under the tabs, the filters and the week strip, and there was no '오늘 할 일' at all.
 *
 * Three rows show; '…개 더' opens the rest. Nothing to do today: only the line, so the block takes one row.
 */
@Composable
internal fun MemoTodayBlock(
    rows: List<TaskList.Row>,
    onOpen: (Memo) -> Unit,
    onDone: (memo: Memo, day: LocalDate?, done: Boolean) -> Unit,
    onWrite: (text: String, written: (Boolean) -> Unit) -> Unit,
    shifts: List<com.bamnat.core.memo.ShiftMemoTime.Day> = emptyList(),
    /** 2.0.0 [RVI-80] To-dos left from before the 늦음 week, and what opens them. */
    old: List<Memo> = emptyList(),
    onOld: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val now = Today.dateTime()
    Column(modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        if (rows.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    TaskList.headline(rows, now),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                if (rows.size > SHOWN) TextButton(onClick = { open = !open }) {
                    Text(if (open) "접기" else "${rows.size - SHOWN}개 더", maxLines = 1)
                }
            }
            for (row in if (open) rows else rows.take(SHOWN)) TodayRow(row, now, onOpen, onDone)
        }
        // 2.0.0 [RVI-80] (검토 U07a-09) What fell off the list after a week, said in one line instead of nothing.
        TaskList.oldLine(old)?.let { line ->
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onOld).padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(line, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                TextButton(onClick = onOld) { Text("정리하기", maxLines = 1) }
            }
        }
        QuickLineField(onWrite, shifts)
    }
}

/** How many of today's rows show before '…개 더'. */
private const val SHOWN = 3

@Composable
private fun TodayRow(row: TaskList.Row, now: java.time.LocalDateTime, onOpen: (Memo) -> Unit, onDone: (Memo, LocalDate?, Boolean) -> Unit) {
    val late = TaskList.overdue(row, now)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { onOpen(row.memo) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = { onDone(row.memo, row.day, !row.done) },
            modifier = Modifier.semantics { stateDescription = if (row.done) "끝냄" else "안 끝냄" },
        ) {
            Icon(
                if (row.done) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                contentDescription = if (row.done) "‘${row.text}’ 끝냄 풀기" else "‘${row.text}’ 끝냄 표시",
                tint = if (row.done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            )
        }
        Text(
            row.text,
            style = MaterialTheme.typography.bodyLarge,
            textDecoration = if (row.done) TextDecoration.LineThrough else null,
            color = if (row.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        // The widget's small line ("11/5 10:00 · 늦음", "15:00 · 2/5", "고정"), 늦음 in the caution colour (디자인 품질 6: not red).
        com.bamnat.app.widget.note(row, late)?.let { note ->
            Text(
                note,
                style = MaterialTheme.typography.labelMedium,
                color = if (late) Tone.Caution.color() else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.padding(start = 8.dp, end = 12.dp),
            )
        }
    }
}

/**
 * The one line under today's rows: written, it says where it goes ("제목: 치과 · 11/13 (금) 15:00 알림") as the widget's window does,
 * and 적기 (the keyboard's 완료 too) puts it in; the line stays open for the next one.
 */
@Composable
private fun QuickLineField(onWrite: (String, (Boolean) -> Unit) -> Unit, shifts: List<com.bamnat.core.memo.ShiftMemoTime.Day>) {
    var text by rememberSaveable { mutableStateOf("") }
    val now = remember(text.isEmpty()) { Today.dateTime() }
    val line = remember(text, shifts) { quickLine(text, now, shifts = shifts) }
    val dayFmt = remember { DateTimeFormatter.ofPattern("M/d (E)", Locale.KOREAN) }
    fun write() {
        val was = text.trim()
        if (was.isEmpty()) return
        text = ""
        // A line that could not be saved comes back to the box.
        onWrite(was) { ok -> if (!ok && text.isEmpty()) text = was }
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.take(com.bamnat.app.memo.QuickTaskActivity.MAX_CHARS) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
            placeholder = { Text("한 줄로 적기 · 예: 내일 오후 3시 치과", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            trailingIcon = {
                if (text.isNotBlank()) IconButton(onClick = ::write) { Icon(Icons.AutoMirrored.Filled.Send, "적기") }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { write() }),
            shape = MaterialTheme.shapes.extraLarge,
            colors = com.bamnat.app.ui.theme.PhotoGround.fieldColors(),
        )
        if (text.isNotBlank()) {
            Text(
                quickSay(line, QuickDay.READ, ring = true, now, dayFmt),
                style = MaterialTheme.typography.bodySmall,
                color = if (quickPast(line, QuickDay.READ, now)) Tone.Caution.color() else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp),
            )
        }
    }
}

/** 2.0.0 [RVI-77] What the bar says once a line is in: "‘치과’ 적었어요 · 11/13 (금) 15:00 알림", "‘우유 사기’ 적었어요 · 오늘". */
internal fun quickWritten(memo: Memo, today: LocalDate = Today.date()): String {
    val where = when {
        memo.reminder != null -> scheduleText(memo.reminder!!.at) + " 알림"
        memo.date == null -> "날짜 없이 고정"
        memo.date == today -> "오늘"
        memo.date == today.plusDays(1) -> "내일"
        else -> com.bamnat.app.ui.Fmt.dayTitle(memo.date!!)
    }
    return "‘${memo.title.take(20)}’ 적었어요 · $where"
}

/**
 * 2.0.0 [RVI-80] (검토 U07a-09, 대기열 TASK-34·KEEP-12) The to-dos left from before the 늦음 week, which used to drop off without a word:
 * each one (its circle finishes it, the words open it) and the three things to do with all of them at once — 모두 끝내기 (the sheet's
 * one button), 모두 오늘로, 모두 휴지통으로 — each with 되돌리기 on the memo tab.
 */
@Composable
internal fun OldTasksSheet(
    old: List<Memo>,
    onOpen: (Memo) -> Unit,
    onDone: (Memo) -> Unit,
    onAll: (OldWay) -> Unit,
    onClose: () -> Unit,
) {
    com.bamnat.app.ui.components.FormSheet(
        onDismissRequest = onClose,
        title = { Text(TaskList.oldLine(old) ?: "오래된 할 일") },
        text = {
            Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                Text("일주일이 넘게 남은 할 일이에요. 하나씩 끝내거나 한 번에 정리해요.", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
                for (m in old) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { onOpen(m) }, verticalAlignment = Alignment.CenterVertically) {
                        val title = m.title.ifBlank { m.preview }.lineSequence().firstOrNull().orEmpty().take(TaskList.TEXT_CHARS)
                        IconButton(onClick = { onDone(m) }) {
                            Icon(Icons.Outlined.RadioButtonUnchecked, "‘$title’ 끝냄 표시", tint = MaterialTheme.colorScheme.outline)
                        }
                        Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        (m.reminder?.at?.toLocalDate() ?: m.date)?.let { d ->
                            Text(com.bamnat.app.ui.Fmt.shortDate(d), style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
                androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    TextButton(onClick = { onAll(OldWay.TODAY) }) { Text(OldWay.TODAY.label, maxLines = 1) }
                    TextButton(onClick = { onAll(OldWay.TRASH) }) { Text(OldWay.TRASH.label, maxLines = 1) }
                }
            }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("닫기") } },
        confirmButton = { androidx.compose.material3.Button(onClick = { onAll(OldWay.DONE) }) { Text(OldWay.DONE.label, maxLines = 1) } },
    )
}
