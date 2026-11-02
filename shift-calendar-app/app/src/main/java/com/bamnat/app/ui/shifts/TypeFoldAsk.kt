package com.bamnat.app.ui.shifts

import com.bamnat.app.ui.components.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bamnat.app.AppContainer
import com.bamnat.app.appContainer
import com.bamnat.core.model.ShiftType
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 1.36 PC 재검토 (마을 스레드 추천안): after the kind of work changes, the kinds of day the old one brought and the new one does not use
 * are offered once to be put away ('안 쓰는 종류'). Nothing is deleted: past days, patterns and pay stay; 근무 종류와 시간 brings them back.
 */
object TypeFoldAsk {
    /**
     * [adds]: the new kind of work's own 근무 put in with 접어 두기 when nothing else would be left to pick (알바 → 교대: 주간·오후·야간);
     * [empty]: nothing would be left and the new kind brings none (알바 → 현장), so the dialog says where to add one.
     */
    data class Ask(
        val calendar: String, val types: List<ShiftType>, val from: WorkStyle, val to: WorkStyle,
        val adds: List<ShiftType> = emptyList(), val empty: Boolean = false,
    )

    /** 1.36 PC 재검토 (접었는데 남는 근무가 없으면 다 보여서 접힌 모습을 못 봐요): the 근무 a kind of work starts with, to put in. */
    fun startTypes(style: WorkStyle): List<ShiftType> = when (style) {
        WorkStyle.SHIFT -> com.bamnat.app.data.DefaultShiftTypes.all().filter { !it.isOff }
        WorkStyle.DAY -> com.bamnat.core.schedule.ShiftBundles.weekday.map { it.type }.filter { !it.isOff }
        else -> emptyList()
    }

    val asking = MutableStateFlow<Ask?>(null)

    /** Said after 접어 두기, with 되돌리기 (the AppRoot snackbar shows it). */
    data class Done(val calendar: String, val ids: List<Long>, val message: String)

    val done = MutableStateFlow<Done?>(null)

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
        if (left.isEmpty()) return
        val rest = types.filter { !it.isOff && it.id !in folded && it !in left }
        val adds = if (rest.isNotEmpty()) emptyList() else startTypes(to).filter { t -> types.none { it.name == t.name } }
        asking.value = Ask(calendar, left, from, to, adds, empty = rest.isEmpty() && adds.isEmpty())
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
            val adds = a.adds.joinToString("·") { it.name }
            Text("$names${com.bamnat.app.ui.Fmt.eun(names)} ${a.to.label}에서 잘 쓰지 않아요. 접어 두면 하루 시트에서 빠지고, 지난 날짜 기록과 급여는 그대로예요. " +
                (if (adds.isNotEmpty()) "대신 ${a.to.label} 기본 근무 $adds${com.bamnat.app.ui.Fmt.obj(adds)} 넣어 둘게요(시간은 근무 종류와 시간에서 고쳐요). " else "") +
                (if (a.empty) "접으면 쓰는 근무가 없어 하루 시트엔 쉬는 날만 남아요. 필요하면 근무 종류와 시간 › 근무 추가로 넣어요. " else "") +
                "설정 › 근무 종류와 시간 › 안 쓰는 종류에서 언제든 다시 꺼낼 수 있어요.")
        },
        confirmButton = {
            TextButton(onClick = {
                val ids = a.types.map { it.id }
                c.foldedTypes.fold(a.calendar, ids)
                if (a.adds.isNotEmpty()) c.appScope.launch {
                    val repo = c.calendars.repository(a.calendar)
                    val have = repo.shiftTypes.first()
                    val next = (have.maxOfOrNull { it.sortOrder } ?: -1) + 1
                    a.adds.filter { t -> have.none { it.name == t.name } }.forEachIndexed { i, t -> repo.saveShiftType(t.copy(id = 0, sortOrder = next + i)) }
                }
                TypeFoldAsk.asking.value = null
                TypeFoldAsk.done.value = TypeFoldAsk.Done(a.calendar, ids, "${a.from.label} 근무 종류 ${ids.size}개를 접었어요")
            }) { Text("접어 두기") }
        },
        dismissButton = { TextButton(onClick = { TypeFoldAsk.asking.value = null }) { Text("그대로 두기") } },
    )
}

/** 1.36 PC 재검토: the kinds put away in the calendar shown now, for the places that offer 근무 (대근 chips too). */
@Composable
fun foldedHere(): Set<Long> {
    val c = androidx.compose.ui.platform.LocalContext.current.appContainer
    val calendar by c.calendars.active.collectAsStateWithLifecycle()
    return c.foldedTypes.state.collectAsStateWithLifecycle().value[calendar].orEmpty()
}
