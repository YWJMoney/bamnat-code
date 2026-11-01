package com.bamnat.app.ui.shifts

import com.bamnat.app.ui.components.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bamnat.app.AppContainer
import com.bamnat.core.model.ShiftType
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first

/**
 * 1.36 PC 재검토 (마을 스레드 추천안): after the kind of work changes, the kinds of day the old one brought and the new one does not use
 * are offered once to be put away ('안 쓰는 종류'). Nothing is deleted: past days, patterns and pay stay; 근무 종류와 시간 brings them back.
 */
object TypeFoldAsk {
    data class Ask(val calendar: String, val types: List<ShiftType>, val from: WorkStyle, val to: WorkStyle)

    val asking = MutableStateFlow<Ask?>(null)

    /** Asks when the change from [from] to [to] left kinds of day the old one started with. */
    suspend fun after(c: AppContainer, calendar: String, from: WorkStyle?, to: WorkStyle) {
        if (from == to) return
        val types = c.calendars.repository(calendar).shiftTypes.first()
        // Back to a kind of work: what it starts with comes out of '안 쓰는 종류' by itself.
        val own = com.bamnat.app.data.FoldedTypes.startNames(to)
        c.foldedTypes.unfold(calendar, types.filter { it.name in own }.map { it.id })
        if (from == null) return
        val old = com.bamnat.app.data.FoldedTypes.startNames(from) - own
        if (old.isEmpty()) return
        val folded = c.foldedTypes.of(calendar)
        val left = types.filter { !it.isOff && it.name in old && it.id !in folded }
        if (left.isNotEmpty()) asking.value = Ask(calendar, left, from, to)
    }
}

@Composable
fun TypeFoldHost(c: AppContainer) {
    val ask by TypeFoldAsk.asking.collectAsStateWithLifecycle()
    val a = ask ?: return
    val names = a.types.joinToString("·") { it.name }
    AlertDialog(
        onDismissRequest = { TypeFoldAsk.asking.value = null },
        title = { Text("${a.from.label} 근무 종류를 접어 둘까요?") },
        text = {
            Text("$names${com.bamnat.app.ui.Fmt.eun(names)} ${a.to.label}에서 잘 쓰지 않아요. 접어 두면 하루 시트에서 빠지고, 지난 날짜 기록과 급여는 그대로예요. " +
                "설정 › 근무 종류와 시간 › 안 쓰는 종류에서 언제든 다시 꺼낼 수 있어요.")
        },
        confirmButton = {
            TextButton(onClick = { c.foldedTypes.fold(a.calendar, a.types.map { it.id }); TypeFoldAsk.asking.value = null }) { Text("접어 두기") }
        },
        dismissButton = { TextButton(onClick = { TypeFoldAsk.asking.value = null }) { Text("그대로 두기") } },
    )
}
