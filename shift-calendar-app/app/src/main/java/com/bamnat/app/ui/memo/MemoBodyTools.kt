package com.bamnat.app.ui.memo

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AssistChip
import com.bamnat.app.ui.components.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bamnat.core.memo.MemoRepeat
import com.bamnat.core.memo.MemoText
import java.time.LocalDate

/**
 * [NOTE-07] What the bar above the keyboard can do to the memo's body. The body field says while it
 * is being typed in ([editing]) and takes the bar's taps through [onAction].
 */
@Stable
internal class MemoBodyTools {
    sealed interface Action {
        data class Mark(val mark: MemoText.Mark) : Action
        data object Today : Action
    }

    var editing by mutableStateOf(false)
    var onAction: ((Action) -> Unit)? = null

    /** [MPH-01] 1.38 The body field typed in last (a memo with photos has one field between each two): it owns the bar. */
    var owner: Any? = null

    /** [MPH-01] Puts a photo in where the caret of the field typed in last is; null when no field was typed in (it goes at the end). */
    var insertPhoto: ((String) -> Unit)? = null

    /** [MPH-07] Puts a new 자유 판 in where the caret of the field typed in last is. */
    var insertBoard: (() -> Unit)? = null

    /** [MPH-01] The bar's 사진 button: opens the editor's 사진 넣기 menu. */
    var onPhoto: (() -> Unit)? = null
}

/** [NOTE-07] The bar above the keyboard while the body is typed in: 목록, 번호, 체크 칸, 오늘 날짜. */
@Composable
internal fun MemoBodyToolbar(tools: MemoBodyTools) {
    if (!tools.editing) return
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Box {
            HorizontalDivider()
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TextButton(onClick = { tools.onAction?.invoke(MemoBodyTools.Action.Mark(MemoText.Mark.BULLET)) }) { Text("• 목록") }
                TextButton(onClick = { tools.onAction?.invoke(MemoBodyTools.Action.Mark(MemoText.Mark.NUMBER)) }) { Text("1. 번호") }
                TextButton(onClick = { tools.onAction?.invoke(MemoBodyTools.Action.Mark(MemoText.Mark.CHECK)) }) { Text("☐ 체크") }
                TextButton(onClick = { tools.onAction?.invoke(MemoBodyTools.Action.Today) }) { Text("오늘 날짜") }
                // [MPH-01] 1.38 사진: put in where the caret is.
                if (tools.onPhoto != null) TextButton(onClick = { tools.onPhoto?.invoke() }) { Text("🖼 사진") }
            }
        }
    }
}

/**
 * [NOTE-05] The chip that makes a memo come back every week, month or year. [EASE-22] 1.38: or on every day of a kind of
 * shift ("야간마다"), the memo's own day's kind first, following the shift when it moves. [EASE-23] Shown on a memo with no date
 * too: picking a repeat gives it the day to start from, rather than asking for a date first.
 */
@Composable
internal fun MemoRepeatChip(
    date: LocalDate?,
    repeat: String,
    onChange: (MemoRepeat.Kind) -> Unit,
    shiftTypes: List<com.bamnat.core.model.ShiftType> = emptyList(),
    dayShiftId: Long? = null,
    onShift: (Long) -> Unit = {},
) {
    var open by remember { mutableStateOf(false) }
    val names = remember(shiftTypes) { shiftTypes.associate { it.id to it.name } }
    Box {
        AssistChip(
            onClick = { open = true },
            label = { Text(MemoRepeat.label(repeat) { names[it] }.ifEmpty { "반복 없음" }, maxLines = 1) },
            leadingIcon = { Icon(Icons.Filled.Repeat, null, Modifier.size(18.dp)) },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            val from = date ?: com.bamnat.core.time.Today.date()
            listOf(MemoRepeat.Kind.NONE, MemoRepeat.Kind.WEEK, MemoRepeat.Kind.MONTH, MemoRepeat.Kind.YEAR).forEach { kind ->
                val text = if (kind == MemoRepeat.Kind.NONE) "반복 없음" else MemoRepeat.label(MemoRepeat.code(kind, from))
                DropdownMenuItem(text = { Text(text) }, onClick = { open = false; onChange(kind) })
            }
            // [EASE-22] 근무마다: the day's own kind first, then the calendar's order.
            val ordered = shiftTypes.sortedBy { if (it.id == dayShiftId) -1 else 0 }
            if (ordered.isNotEmpty()) androidx.compose.material3.HorizontalDivider()
            ordered.forEach { t ->
                DropdownMenuItem(
                    text = { Text("${t.name}마다") },
                    leadingIcon = { com.bamnat.app.ui.components.ShiftBadge(t, size = 20.dp, outlined = t.isOff) },
                    trailingIcon = { if (MemoRepeat.shiftIdOf(repeat) == t.id) Icon(Icons.Filled.Check, null) },
                    onClick = { open = false; onShift(t.id) },
                )
            }
        }
    }
}

/**
 * 1.38 (DB 28판) Deleting a memo. A repeating one is one memo on many days, so it asks which: '[day]만 빼기' (it stays on every
 * other day it comes back on) or '반복 전부 지우기' (to the trash, every day). Opened from the memo tab ([day] null) only the
 * second is offered, with where to go for one day. A memo that does not repeat gets the plain confirm ([plainTitle], [plainMessage]).
 */
@Composable
internal fun MemoDeleteDialog(
    memo: com.bamnat.core.memo.Memo,
    day: LocalDate?,
    shiftName: (Long) -> String?,
    plainTitle: String,
    plainMessage: String,
    onDismiss: () -> Unit,
    onDeleteAll: () -> Unit,
    onSkipDay: (LocalDate) -> Unit,
) {
    val start = memo.date
    if (memo.repeat.isEmpty() || start == null) {
        com.bamnat.app.ui.components.ConfirmDialog(plainTitle, plainMessage, "지우기", onDismiss, onDeleteAll, destructive = true)
        return
    }
    val label = MemoRepeat.label(memo.repeat, shiftName)
    val skip = day?.takeIf { !it.isBefore(start) && it !in memo.skips }
    com.bamnat.app.ui.components.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("반복 메모를 지울까요?") },
        text = {
            androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("‘$label’ 반복 메모예요.", style = MaterialTheme.typography.bodyMedium)
                if (skip != null) DeleteChoice(
                    title = "${com.bamnat.app.ui.Fmt.dayTitle(skip)}만 빼기",
                    detail = "다른 날에는 그대로 보여요",
                    onClick = { onSkipDay(skip) },
                )
                DeleteChoice(
                    title = "반복 전부 지우기",
                    detail = "반복되는 모든 날에서 사라지고 휴지통으로 가요. 30일 동안 되살릴 수 있어요.",
                    danger = true,
                    onClick = onDeleteAll,
                )
                if (skip == null) Text(
                    "하루만 빼려면 달력에서 그 날을 눌러 이 메모를 열고 지우세요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

@Composable
private fun DeleteChoice(title: String, detail: String, onClick: () -> Unit, danger: Boolean = false) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        androidx.compose.foundation.layout.Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall,
                color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
