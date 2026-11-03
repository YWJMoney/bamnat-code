package com.bamnat.core.formula

import com.bamnat.core.memo.Memo
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

/**
 * [RUL-05] 1.36 885 탭 사이 흐름: one day's values as formulas read them ([FormulaName]), gathered from the calendar (근무), the memos
 * (꼬리표·체크), 가계부 (지출·수입·분류) and 급여. The app hands what it already has for the days on screen; each day's scope is worked out
 * once and kept, so a month of cells and their rules cost a lookup each.
 */
class DayFactsBook(
    private val today: LocalDate,
    /** 교대·일근·알바·현장·자영업·투자. */
    private val style: String,
    private val shifts: (LocalDate) -> DayShift?,
    private val memos: (LocalDate) -> List<Memo> = { emptyList() },
    private val money: (LocalDate) -> DayCash? = { null },
    private val holiday: (LocalDate) -> String? = { null },
    private val monthPay: (YearMonth) -> Long? = { null },
    private val dayPay: (LocalDate) -> Long? = { null },
    private val now: () -> String = { "" },
    private val inputs: Map<String, Any?> = emptyMap(),
) {
    /** A day's work as the calendar resolved it. [role] is [com.bamnat.core.recipe.ShiftRole]'s key. */
    data class DayShift(
        val name: String,
        val role: String,
        val start: String? = null,
        val end: String? = null,
        val minutes: Int = 0,
        val off: Boolean = false,
        val leave: String? = null,
    )

    /** A day's money: in, out and the kinds of what went out. */
    data class DayCash(val income: Long = 0, val expense: Long = 0, val kinds: List<String> = emptyList())

    private val cache = HashMap<LocalDate, FormulaScope>()
    private val monthSums = HashMap<YearMonth, Map<LocalDate, Triple<Int, Double, Pair<Long, Long>>>>()

    fun scope(date: LocalDate): FormulaScope = cache.getOrPut(date) { FormulaScope.of(values(date), inputs) }

    private fun isNight(s: DayShift?) = s != null && !s.off && s.role == "night"
    private fun hoursOf(s: DayShift?) = if (s == null || s.off) 0.0 else s.minutes / 60.0

    /** For each day of [m] up to it: nights so far, hours so far, (spend, income) so far. */
    private fun sums(m: YearMonth) = monthSums.getOrPut(m) {
        var n = 0
        var h = 0.0
        var out = 0L
        var inc = 0L
        (1..m.lengthOfMonth()).associate { d ->
            val day = m.atDay(d)
            val s = shifts(day)
            if (isNight(s)) n++
            h += hoursOf(s)
            money(day)?.let { out += it.expense; inc += it.income }
            day to Triple(n, h, out to inc)
        }
    }

    fun values(date: LocalDate): Map<String, Any?> {
        val s = shifts(date)
        val dayMemos = memos(date)
        val cash = money(date)
        val hol = holiday(date)
        val weekFrom = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val week = (0..6).map { shifts(weekFrom.plusDays(it.toLong())) }
        val month = sums(YearMonth.from(date))
        val m = month[date]
        val endOfMonth = month[YearMonth.from(date).atEndOfMonth()]
        var streak = 0
        var d = date
        while (streak < 60) {
            val x = shifts(d)
            if (x == null || x.off) break
            streak++
            d = d.minusDays(1)
        }
        val checks = dayMemos.flatMap { it.checklist }.filter { it.text.isNotBlank() }
        return mapOf(
            FormulaName.DATE.ko to date.toString(),
            FormulaName.MONTH.ko to date.monthValue,
            FormulaName.DAY.ko to date.dayOfMonth,
            FormulaName.WEEKDAY.ko to "월화수목금토일"[date.dayOfWeek.value - 1].toString(),
            FormulaName.WEEKEND.ko to (date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY),
            FormulaName.HOLIDAY.ko to (hol != null),
            FormulaName.HOLIDAY_NAME.ko to hol.orEmpty(),
            FormulaName.TODAY.ko to (date == today),
            FormulaName.PAST.ko to date.isBefore(today),
            FormulaName.DAYS_AWAY.ko to java.time.temporal.ChronoUnit.DAYS.between(today, date),
            FormulaName.STYLE.ko to style,
            FormulaName.SHIFT.ko to s?.name.orEmpty(),
            FormulaName.ROLE.ko to (s?.let { ROLE_WORDS[it.role] } ?: ""),
            FormulaName.START.ko to s?.start.orEmpty(),
            FormulaName.END.ko to s?.end.orEmpty(),
            FormulaName.HOURS.ko to hoursOf(s),
            FormulaName.OFF.ko to (s == null || s.off),
            FormulaName.LEAVE.ko to s?.leave.orEmpty(),
            FormulaName.WEEK_NIGHTS.ko to week.count(::isNight),
            FormulaName.WEEK_HOURS.ko to week.sumOf(::hoursOf),
            FormulaName.MONTH_NIGHTS.ko to (endOfMonth?.first ?: 0),
            FormulaName.MONTH_HOURS.ko to (endOfMonth?.second ?: 0.0),
            FormulaName.STREAK.ko to streak,
            FormulaName.MEMO_COUNT.ko to dayMemos.size,
            FormulaName.MEMO.ko to dayMemos.joinToString("\n") { listOf(it.title, it.body).filter { t -> t.isNotBlank() }.joinToString(" ") }.take(Formula.MAX_TEXT),
            FormulaName.TAGS.ko to dayMemos.flatMap { it.tags }.distinct().take(Formula.MAX_LIST),
            FormulaName.CHECK_RATE.ko to (if (checks.isEmpty()) 0 else checks.count { it.checked } * 100 / checks.size),
            FormulaName.CHECKS_LEFT.ko to checks.count { !it.checked },
            FormulaName.SPEND.ko to (cash?.expense ?: 0L),
            FormulaName.INCOME.ko to (cash?.income ?: 0L),
            FormulaName.SPEND_KINDS.ko to cash?.kinds.orEmpty().distinct().take(Formula.MAX_LIST),
            FormulaName.MONTH_SPEND.ko to (m?.third?.first ?: 0L),
            FormulaName.MONTH_INCOME.ko to (m?.third?.second ?: 0L),
            FormulaName.MONTH_PAY.ko to monthPay(YearMonth.from(date)),
            FormulaName.HOUR_PAY.ko to dayPay(date),
            FormulaName.NOW.ko to now(),
        )
    }

    companion object {
        /** A kind of shift as formulas say it (주간·저녁·야간·당직·휴무·기타). */
        val ROLE_WORDS = mapOf("day" to "주간", "eve" to "저녁", "night" to "야간", "long" to "당직", "off" to "휴무", "other" to "기타")
    }
}
