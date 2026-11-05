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

/** [NOTE-05] The chip of a dated memo that makes it come back every week, month or year. */
@Composable
internal fun MemoRepeatChip(date: LocalDate, repeat: String, onChange: (MemoRepeat.Kind) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        AssistChip(
            onClick = { open = true },
            label = { Text(MemoRepeat.label(repeat).ifEmpty { "반복 없음" }, maxLines = 1) },
            leadingIcon = { Icon(Icons.Filled.Repeat, null, Modifier.size(18.dp)) },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            MemoRepeat.Kind.entries.forEach { kind ->
                val text = if (kind == MemoRepeat.Kind.NONE) "반복 없음" else MemoRepeat.label(MemoRepeat.code(kind, date))
                DropdownMenuItem(text = { Text(text) }, onClick = { open = false; onChange(kind) })
            }
        }
    }
}
