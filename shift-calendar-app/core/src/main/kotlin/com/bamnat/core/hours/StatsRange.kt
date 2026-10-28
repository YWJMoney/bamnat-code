package com.bamnat.core.hours

import com.bamnat.core.pay.PayPeriod
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * 1.32 [CALX-06] (근무유형 조사 공통 4장 "기간 칩 [달력 월] [급여 기간] [주] [직접]"): the days 통계 counts, said under every
 * card the same way ("9/21~10/20"), and the one line that says when this 급여 기간 (or 주급 주) ends.
 */
object StatsRange {
    enum class Mode(val label: String) { MONTH("1일~말일"), PAY("급여 기간"), WEEK("주"), CUSTOM("직접") }

    data class Range(val from: LocalDate, val to: LocalDate) {
        val days: Long get() = ChronoUnit.DAYS.between(from, to) + 1
        /** "9/21~10/20", with the year when it is not [thisYear]'s. */
        fun label(thisYear: Int = from.year): String {
            fun d(x: LocalDate) = (if (x.year != thisYear) "${x.year}." else "") + "${x.monthValue}/${x.dayOfMonth}"
            return "${d(from)}~${d(to)}"
        }
    }

    /** At most a year in a 직접 range, so a slip of the finger never counts ten years of days. */
    const val MAX_DAYS = 366L

    /**
     * The range of [mode] for [month]: [PAY] needs a [period] that does not end on 말일, [WEEK] is the week ([weekStart]
     * first) that holds [today] when [month] is this month, else the month's last week, moved by [weekShift] weeks;
     * [CUSTOM] is [custom] cut to [MAX_DAYS]. Null falls back to 1일~말일.
     */
    fun of(
        mode: Mode, month: YearMonth, today: LocalDate, period: PayPeriod? = null, weekStart: DayOfWeek = DayOfWeek.MONDAY,
        weekShift: Int = 0, custom: Range? = null,
    ): Range = when (mode) {
        Mode.MONTH -> Range(month.atDay(1), month.atEndOfMonth())
        Mode.PAY -> period?.takeIf { it.endDay != null }?.let { Range(it.start(month), it.end(month)) } ?: Range(month.atDay(1), month.atEndOfMonth())
        Mode.WEEK -> {
            val anchor = if (YearMonth.from(today) == month) today else month.atEndOfMonth()
            val start = anchor.with(TemporalAdjusters.previousOrSame(weekStart)).plusWeeks(weekShift.toLong())
            Range(start, start.plusDays(6))
        }
        Mode.CUSTOM -> custom?.let { c ->
            val (a, b) = if (c.to.isBefore(c.from)) c.to to c.from else c.from to c.to
            Range(a, minOf(b, a.plusDays(MAX_DAYS - 1)))
        } ?: Range(month.atDay(1), month.atEndOfMonth())
    }

    /**
     * "이번 급여 기간은 10월 20일에 끝나요 · 5일 남았어요, 급여일 10월 25일", or for 주급 "이번 주급 주는 일요일에 끝나요 · 금요일에 들어와요";
     * null when the pay has no period of its own or [today] is not in one shown.
     */
    fun endsLine(period: PayPeriod?, today: LocalDate, weekStart: DayOfWeek = DayOfWeek.MONDAY): String? {
        val p = period ?: return null
        p.payWeekday?.let { pay ->
            val end = today.with(TemporalAdjusters.previousOrSame(weekStart)).plusDays(6)
            val left = ChronoUnit.DAYS.between(today, end)
            return "이번 주급 주는 ${dayName(end.dayOfWeek)}(${end.monthValue}/${end.dayOfMonth})에 끝나요" +
                (if (left > 0) " · ${left}일 남았어요" else " · 오늘까지예요") + ", ${dayName(pay)}에 들어와요"
        }
        if (p.endDay == null) return null
        val m = p.monthOf(today)
        val end = p.end(m)
        val left = ChronoUnit.DAYS.between(today, end)
        val pay = p.payDate(m)?.let { ", 급여일 ${it.monthValue}월 ${it.dayOfMonth}일" } ?: ""
        return "이번 급여 기간은 ${end.monthValue}월 ${end.dayOfMonth}일에 끝나요" + (if (left > 0) " · ${left}일 남았어요" else " · 오늘까지예요") + pay
    }

    /** The first day of each 급여 기간 seen in [month]'s grid ([from]..[to]): where the calendar draws its cut line. */
    fun cutDays(period: PayPeriod?, from: LocalDate, to: LocalDate): Set<LocalDate> {
        val p = period?.takeIf { it.endDay != null } ?: return emptySet()
        return generateSequence(YearMonth.from(from).minusMonths(1)) { it.plusMonths(1) }
            .takeWhile { !it.atDay(1).isAfter(to.plusMonths(1)) }
            .map { p.end(it).plusDays(1) }
            .filter { !it.isBefore(from) && !it.isAfter(to) }
            .toSet()
    }

    fun dayName(d: DayOfWeek): String = when (d) {
        DayOfWeek.MONDAY -> "월요일"; DayOfWeek.TUESDAY -> "화요일"; DayOfWeek.WEDNESDAY -> "수요일"; DayOfWeek.THURSDAY -> "목요일"
        DayOfWeek.FRIDAY -> "금요일"; DayOfWeek.SATURDAY -> "토요일"; DayOfWeek.SUNDAY -> "일요일"
    }
}
