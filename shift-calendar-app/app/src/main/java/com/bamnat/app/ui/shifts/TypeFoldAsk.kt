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
    data class Done(
        val calendar: String, val ids: List<Long>, val message: String,
        val patterns: List<com.bamnat.core.model.WorkPattern>? = null,
        /** The kind of work whose schedule was stopped. */
        val from: WorkStyle? = null,
    )

    /**
     * [SETUP-16] 1.37 PC 122d18a0 (현대제철 교대 → 현장, 접어 두기: 달력이 12월까지 주·야·휴를 계속 칠함, 근무 알람 안내도 남음):
     * work written day by day (현장·자영업·투자) has no repeating schedule, so putting the old 근무 away also stops the cycle after
     * today. Past days stay; 그대로 두기 keeps the cycle as it was.
     */
    fun stopsCycle(to: WorkStyle): Boolean = to == WorkStyle.FIELD || to == WorkStyle.BUSINESS || to == WorkStyle.INVEST

    /**
     * 1.37 PC deca4f1f (교대에서 알바로 바로 가며 접어 두면 교대 주기가 계속 칠해짐): putting the old 근무 away stops its cycle whatever the
     * new kind of work is; the new one sets its own days (알바 요일, 일근 월~금, 교대 순서) and going back brings the old cycle back.
     */
    fun stopsCycle(from: WorkStyle, to: WorkStyle): Boolean = from != to

    /**
     * Whether [pattern], the one in use today, is [style]'s own: an order for 교대, days of the week for the others (투자 has none).
     * 1.37 PC 70710298 (교대 → 알바 → 교대에서 처음 주기가 안 돌아옴): back in 교대, putting the 알바 kinds away stopped the 교대 cycle
     * that had just come back; only the old work's own schedule is stopped.
     */
    /**
     * The schedule that goes on after today: the one in use, or one saved from tomorrow (1.37 PC 67d13335: 일근 saved from 11/6 while
     * today was still the stopped 교대 order, so 일근 → 알바's 접어 두기 did not stop the 월~금).
     */
    fun going(patterns: List<com.bamnat.core.model.WorkPattern>, today: java.time.LocalDate): com.bamnat.core.model.WorkPattern? =
        patterns.filter { it.endDate?.isAfter(today) != false }.maxByOrNull { it.startDate }

    fun ownSchedule(style: WorkStyle, pattern: com.bamnat.core.model.WorkPattern?): Boolean = when (style) {
        WorkStyle.SHIFT -> pattern is com.bamnat.core.model.WorkPattern.Rotation
        WorkStyle.INVEST -> false
        else -> pattern is com.bamnat.core.model.WorkPattern.Weekly
    }

    /** 되돌리기 of a [Done]: the 근무 out again, and the cycle as it was. */
    fun undo(c: AppContainer, d: Done) {
        c.foldedTypes.unfold(d.calendar, d.ids)
        val was = d.patterns ?: return
        d.from?.let { c.foldedTypes.stop(d.calendar, it, null) }
        c.appScope.launch { c.calendars.repository(d.calendar).restorePatterns(was) }
    }

    /** Stops [calendar]'s cycle after today, keeping it as it was for [resume]. */
    suspend fun stopCycle(c: AppContainer, calendar: String, was: List<com.bamnat.core.model.WorkPattern>, from: WorkStyle) {
        val today = com.bamnat.core.time.Today.date()
        // Stopped twice from the same kind of work: the first one, the schedule before any of it, is the one kept.
        if (c.foldedTypes.stopped(calendar, from) == null) c.foldedTypes.stop(calendar, from, com.bamnat.app.data.FoldedTypes.StoppedCycle(today, was, from))
        c.calendars.repository(calendar).endPatternsAfter(today)
    }

    /**
     * [SETUP-16] 1.37 PC b98f9d1e: back to work with a cycle (교대·일근·알바), the one [stopCycle] stopped comes back as it was, when
     * nothing was changed in it since. The days between went on being the other work's (written by hand, they stay as written).
     */
    suspend fun resume(c: AppContainer, calendar: String, from: WorkStyle?, to: WorkStyle) {
        // 1.37 PC 0068727b: only the kind of work it was stopped from takes it back (교대's 주간·야간 are not an 알바's days).
        val stopped = c.foldedTypes.stopped(calendar, to) ?: return
        c.foldedTypes.stop(calendar, to, null)
        val repo = c.calendars.repository(calendar)
        val now = repo.patterns.first()
        val today = com.bamnat.core.time.Today.date()
        val fromOwn = from != null && from != to && ownSchedule(from, going(now, today))
        // 1.37 PC 978d10f1 MED-2 (일근 → 알바 → 일근에서 월~금이 안 돌아옴): the 알바's own days saved meanwhile are days of the week
        // too, but the 알바's, not set again by hand for 일근; only a schedule of another kind than the work left counts as by hand.
        val sameKind = fromOwn && (from == WorkStyle.SHIFT) == (to == WorkStyle.SHIFT)
        if (!sameKind && madeByHand(now, stopped)) return
        // 1.37 PC 67d13335: the schedule it replaces is kept for its own kind of work too (일근's 월~금 back when 일근 comes back).
        if (fromOwn && c.foldedTypes.stopped(calendar, from!!) == null) {
            c.foldedTypes.stop(calendar, from, com.bamnat.app.data.FoldedTypes.StoppedCycle(today, now, from))
        }
        repo.restorePatterns(stopped.was)
    }

    /**
     * Whether a cycle was set again by hand after [stopped] (that one is the user's, kept as it is). A 자영업's 영업일 or a 현장's
     * weekly schedule saved meanwhile is the other work's and goes with it (1.37 PC 0068727b: 현장 → 자영업 → 교대 stayed 영업).
     */
    internal fun madeByHand(now: List<com.bamnat.core.model.WorkPattern>, stopped: com.bamnat.app.data.FoldedTypes.StoppedCycle): Boolean {
        val kept = com.bamnat.core.work.QuitCheck.endPatterns(stopped.was, stopped.on)
        // The stopped work's own kind of schedule: an order for 교대, days of the week for 일근·알바.
        val rotation = stopped.from?.let { it == WorkStyle.SHIFT } ?: stopped.was.any { it is com.bamnat.core.model.WorkPattern.Rotation }
        return now.filter { (it is com.bamnat.core.model.WorkPattern.Rotation) == rotation }.any { p ->
            kept.none { e -> e.javaClass == p.javaClass && e.withRange(e.startDate, p.endDate) == p }
        }
    }

    val done = MutableStateFlow<Done?>(null)

    /** Asks when the change from [from] to [to] left kinds of day the old one started with. */
    suspend fun after(c: AppContainer, calendar: String, from: WorkStyle?, to: WorkStyle) {
        if (from == to) return
        runCatching { resume(c, calendar, from, to) }
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
    val stops = TypeFoldAsk.stopsCycle(a.from, a.to) &&
        TypeFoldAsk.ownSchedule(a.from, TypeFoldAsk.going(patterns, com.bamnat.core.time.Today.date()))
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
                if (was != null) c.appScope.launch { TypeFoldAsk.stopCycle(c, a.calendar, was, a.from) }
                if (a.adds.isNotEmpty()) c.appScope.launch {
                    val repo = c.calendars.repository(a.calendar)
                    val have = repo.shiftTypes.first()
                    val next = (have.maxOfOrNull { it.sortOrder } ?: -1) + 1
                    a.adds.filter { t -> have.none { it.name == t.name } }.forEachIndexed { i, t -> repo.saveShiftType(t.copy(id = 0, sortOrder = next + i)) }
                }
                // Said before the question closes, so the kind-of-work line waiting for it says both (AppRoot).
                TypeFoldAsk.done.value = TypeFoldAsk.Done(a.calendar, ids,
                    "${a.from.label} 근무 종류 ${ids.size}개를 접었어요" + if (was != null) " · 근무 주기는 오늘까지예요" else "", was, a.from)
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
