package com.bamnat.app.logic

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import com.bamnat.app.appContainer
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import com.bamnat.app.data.ScheduleSnapshot
import com.bamnat.core.formula.DayFactsBook
import com.bamnat.core.holiday.KoreanHolidays
import com.bamnat.core.memo.Memo
import com.bamnat.core.money.MoneyBook
import com.bamnat.core.money.MoneyKind
import com.bamnat.core.recipe.ShiftRole
import com.bamnat.core.work.WorkStyle
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * [RUL-05] 1.36 885 탭 사이 흐름 on the phone: the days on screen as formulas read them (근무 from the calendar, 꼬리표·체크 from the memos,
 * 지출·수입 from 가계부). The calendar provides it ([LocalDayFacts]); a part drawn with a blueprint asks `scope(date)` for its `when`
 * lines and formula values (갈래 A의 그리기 엔진), and buttons and rules ask the same.
 */
val LocalDayFacts = staticCompositionLocalOf<DayFactsBook?> { null }

private val HM = DateTimeFormatter.ofPattern("HH:mm")

/** The short word formulas use for a kind of work (교대·일근·알바·현장·자영업·투자). */
fun WorkStyle.formulaWord(): String = when (this) {
    WorkStyle.SHIFT -> "교대"; WorkStyle.DAY -> "일근"; WorkStyle.HOURLY -> "알바"
    WorkStyle.FIELD -> "현장"; WorkStyle.BUSINESS -> "자영업"; WorkStyle.INVEST -> "투자"
}

/** A book of day values over [snap], [memos] and [money]; built again only when one of them changes. */
fun dayFactsOf(
    snap: ScheduleSnapshot,
    memos: List<Memo>,
    money: MoneyBook,
    style: WorkStyle?,
    today: LocalDate,
    monthPay: (java.time.YearMonth) -> Long? = { null },
): DayFactsBook {
    val byDate = memos.filter { it.date != null && !it.inTrash }.groupBy { it.date!! }
    val cash = money.entries.groupBy { it.date }.mapValues { (_, es) ->
        DayFactsBook.DayCash(
            income = es.filter { it.kind == MoneyKind.INCOME }.sumOf { it.amount },
            expense = es.filter { it.kind == MoneyKind.EXPENSE }.sumOf { it.amount },
            kinds = es.filter { it.kind == MoneyKind.EXPENSE }.map { it.category },
        )
    }
    val shifts = HashMap<LocalDate, DayFactsBook.DayShift?>()
    return DayFactsBook(
        today = today,
        style = style?.formulaWord().orEmpty(),
        shifts = { d ->
            shifts.getOrPut(d) {
                val r = runCatching { snap.resolver.resolve(d) }.getOrNull() ?: return@getOrPut null
                val t = r.shiftType ?: r.coveredShift ?: return@getOrPut null
                val minutes = if (t.isOff) 0 else r.scheduledMinutes
                DayFactsBook.DayShift(
                    name = t.name,
                    role = ShiftRole.guess(t.name, t.isOff, r.start?.hour ?: t.start?.hour, minutes).key,
                    start = r.start?.format(HM),
                    end = r.end?.format(HM),
                    minutes = minutes,
                    off = t.isOff || r.leave?.isFullDay == true,
                    leave = r.leave?.kind?.label,
                )
            }
        },
        memos = { byDate[it].orEmpty() },
        money = { cash[it] },
        holiday = { KoreanHolidays.nameOf(it) },
        monthPay = monthPay,
        now = { com.bamnat.core.time.Today.time().format(HM) },
    )
}

@Composable
fun rememberDayFacts(snap: ScheduleSnapshot?, memos: List<Memo>, money: MoneyBook, style: WorkStyle?, today: LocalDate): DayFactsBook? =
    remember(snap, memos, money, style, today) { snap?.let { dayFactsOf(it, memos, money, style, today) } }

/** The phone's own day values for screens away from the calendar (스튜디오 미리 보기·규칙 편집), read once in the background. */
@Composable
fun rememberAppDayFacts(): DayFactsBook? {
    val c = androidx.compose.ui.platform.LocalContext.current.appContainer
    val v by androidx.compose.runtime.produceState<DayFactsBook?>(null, c) { value = runCatching { LogicRunner.facts(c) }.getOrNull() }
    return v
}
