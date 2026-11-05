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
import com.bamnat.core.work.words
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

    /**
     * Said after 접어 두기, with 되돌리기 (the AppRoot snackbar shows it). [patterns]: the repeating schedule as it was, when 접어 두기
     * also stopped it ([stopsCycle]); 되돌리기 puts it back ([undo]).
     */
    data class Done(val calendar: String, val ids: List<Long>, val message: String, val patterns: List<com.bamnat.core.model.WorkPattern>? = null)

    /**
     * [SETUP-16] 1.37 PC 122d18a0 (현대제철 교대 → 현장, 접어 두기: 달력이 12월까지 주·야·휴를 계속 칠함, 근무 알람 안내도 남음):
     * work written day by day (현장·자영업·투자) has no repeating schedule, so putting the old 근무 away also stops the cycle after
     * today. Past days stay; 그대로 두기 keeps the cycle as it was.
     */
    fun stopsCycle(to: WorkStyle): Boolean = to == WorkStyle.FIELD || to == WorkStyle.BUSINESS || to == WorkStyle.INVEST

    /** 되돌리기 of a [Done]: the 근무 out again, and the cycle as it was. */
    fun undo(c: AppContainer, d: Done) {
        c.foldedTypes.unfold(d.calendar, d.ids)
        val was = d.patterns ?: return
        c.appScope.launch { c.calendars.repository(d.calendar).restorePatterns(was) }
    }

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
    val patterns by androidx.compose.runtime.produceState<List<com.bamnat.core.model.WorkPattern>>(emptyList(), a) {
        value = c.calendars.repository(a.calendar).patterns.first()
    }
    val stops = TypeFoldAsk.stopsCycle(a.to) && patterns.isNotEmpty()
    val names = a.types.joinToString("·") { it.name }
    AlertDialog(
        onDismissRequest = { TypeFoldAsk.asking.value = null },
        title = { Text("${a.from.label} 근무 종류를 접어 둘까요?") },
        text = {
            val adds = a.adds.joinToString("·") { it.name }
            Text("$names${com.bamnat.app.ui.Fmt.eun(names)} ${a.to.label}에서 잘 쓰지 않아요. 접어 두면 하루 시트에서 빠지고, 지난 날짜 기록과 급여는 그대로예요. " +
                (if (adds.isNotEmpty()) "대신 ${a.to.label} 기본 근무 $adds${com.bamnat.app.ui.Fmt.obj(adds)} 넣어 둘게요(시간은 근무 종류와 시간에서 고쳐요). " else "") +
                (if (stops) "${a.from.label} 근무 주기도 오늘까지만 칠하고 멈춰요. " else "") +
                (if (a.empty) "접으면 쓰는 ${dayKindSubject(a.to)} 없어 하루 시트엔 쉬는 날만 남아요. 필요하면 ${dayKindsPlace(a.to)} › ${dayKindNoun(a.to)} 추가로 넣어요. " else "") +
                "${dayKindsPlace(a.to)} › 안 쓰는 종류에서 언제든 다시 꺼낼 수 있어요.")
        },
        confirmButton = {
            TextButton(onClick = {
                val ids = a.types.map { it.id }
                c.foldedTypes.fold(a.calendar, ids)
                val was = patterns.takeIf { stops }
                if (was != null) c.appScope.launch {
                    c.calendars.repository(a.calendar).endPatternsAfter(com.bamnat.core.time.Today.date())
                }
                if (a.adds.isNotEmpty()) c.appScope.launch {
                    val repo = c.calendars.repository(a.calendar)
                    val have = repo.shiftTypes.first()
                    val next = (have.maxOfOrNull { it.sortOrder } ?: -1) + 1
                    a.adds.filter { t -> have.none { it.name == t.name } }.forEachIndexed { i, t -> repo.saveShiftType(t.copy(id = 0, sortOrder = next + i)) }
                }
                // Said before the question closes, so the kind-of-work line waiting for it says both (AppRoot).
                TypeFoldAsk.done.value = TypeFoldAsk.Done(a.calendar, ids,
                    "${a.from.label} 근무 종류 ${ids.size}개를 접었어요" + if (was != null) " · 근무 주기는 오늘까지예요" else "", was)
                TypeFoldAsk.asking.value = null
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

/** 1.36 PC 재검토 (현장은 제목만 '일정'이고 안은 '근무'): what a kind of day is called in [style]'s own words. */
fun dayKindNoun(style: WorkStyle?): String = if (style == WorkStyle.FIELD) "일정" else "근무"

/** "일정이" / "근무가". */
fun dayKindSubject(style: WorkStyle?): String = if (style == WorkStyle.FIELD) "일정이" else "근무가"

/** Where the kinds of day are set, as the settings row says it ("설정 › 일정 종류"). */
fun dayKindsPlace(style: WorkStyle?): String = "설정 › " + style.words().types
