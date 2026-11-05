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
 * 1.38 PC 확인(9b028f4c): a repeating memo is one memo, so deleting it takes it off every day it repeats on. The confirm says
 * so ("‘야간마다’ 반복 메모라서 …") instead of reading like this day alone. [base] is what the dialog says otherwise.
 */
internal fun memoDeleteTitle(memo: com.bamnat.core.memo.Memo, plain: String): String =
    if (memo.repeat.isEmpty()) plain else "반복 메모를 지울까요?"

internal fun memoDeleteMessage(memo: com.bamnat.core.memo.Memo, base: String, shiftName: (Long) -> String?): String =
    if (memo.repeat.isEmpty()) base
    else "‘${com.bamnat.core.memo.MemoRepeat.label(memo.repeat, shiftName)}’ 반복 메모라서 이 날만이 아니라 반복되는 모든 날에서 사라져요. $base"
