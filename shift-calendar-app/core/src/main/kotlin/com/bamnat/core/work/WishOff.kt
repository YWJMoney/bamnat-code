package com.bamnat.core.work

import java.time.LocalDate
import java.time.YearMonth

/**
 * 1.32 [WTYP-11] (근무유형 조사 3장 의료) 희망 오프: the days off a shift worker asks for in the next month's duty plan,
 * and the day of the month those requests close. Marked on the calendar (♡) and listed to send; the notices come two
 * days before the close and on the day ([com.bamnat.core.notify.DateNotices.wishOff]). One calendar's own, kept on the
 * phone and in the backup.
 */
data class WishOff(val deadlineDay: Int? = null, val days: Set<LocalDate> = emptySet()) {
    fun has(day: LocalDate): Boolean = day in days

    fun toggle(day: LocalDate): WishOff = copy(days = if (day in days) days - day else days + day)

    fun picked(month: YearMonth): List<LocalDate> = days.filter { YearMonth.from(it) == month }.sorted()

    /** Days long past are let go, so the list stays short. */
    fun prune(today: LocalDate): WishOff = copy(days = days.filterTo(sortedSetOf()) { !it.isBefore(today.minusMonths(KEEP_MONTHS)) })

    val isEmpty: Boolean get() = deadlineDay == null && days.isEmpty()

    companion object {
        const val NAME = "희망 오프"
        const val MARK = "♡"
        const val KEEP_MONTHS = 3L
        val STYLES = setOf(WorkStyle.SHIFT)

        /** Whether [style] gets the chip: 교대 근무, or a calendar not set yet. */
        fun offered(style: WorkStyle?): Boolean = style == null || style in STYLES

        /** What 고른 날 복사 puts on the clipboard, to send to whoever makes the duty plan. */
        fun copyText(month: YearMonth, days: List<LocalDate>): String =
            "${month.monthValue}월 희망 오프: " + days.joinToString(", ") { "${it.dayOfMonth}일(${com.bamnat.core.hours.StatsRange.dayName(it.dayOfWeek).take(1)})" }
    }
}
