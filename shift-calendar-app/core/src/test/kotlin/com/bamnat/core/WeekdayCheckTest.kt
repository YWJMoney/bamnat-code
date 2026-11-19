package com.bamnat.core

import com.bamnat.core.imports.RosterWord
import com.bamnat.core.imports.WeekdayCheck
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.YearMonth

/** 2.0.0 [RVL-75] (검토 I07ba-02) A roster's row of weekdays tells its month and whether its columns line up. */
class WeekdayCheckTest {
    private val nov = YearMonth.of(2026, 11) // 11/1 is a Sunday
    private val names = listOf("일", "월", "화", "수", "목", "금", "토")
    private fun weekdays(m: YearMonth, days: IntRange, shift: Int = 0) = days.map { d -> names[m.atDay(1).plusDays(d - 1L + shift).dayOfWeek.value % 7] }

    @Test fun `weekdays that fit say nothing`() {
        val rows = listOf(listOf("이름") + (1..14).map { "$it" }, listOf("") + weekdays(nov, 1..14), listOf("김민지") + List(14) { "D" })
        val pairs = WeekdayCheck.ofRows(rows)
        assertEquals(14, pairs.size)
        assertEquals(DayOfWeek.SUNDAY, pairs[1])
        assertNull(WeekdayCheck.check(pairs, nov))
    }

    @Test fun `weekdays of the next month say that month`() {
        val dec = nov.plusMonths(1)
        val rows = listOf(listOf("") + (1..10).map { "$it" }, listOf("") + weekdays(dec, 1..10))
        assertEquals(WeekdayCheck.Verdict.OtherMonth(dec), WeekdayCheck.check(WeekdayCheck.ofRows(rows), nov))
    }

    @Test fun `columns a day off are said`() {
        // A column was lost: the weekday under each date is the day before's.
        val rows = listOf(listOf("") + (2..12).map { "$it" }, listOf("") + weekdays(nov, 2..12, shift = -1))
        assertEquals(WeekdayCheck.Verdict.Shifted(-1), WeekdayCheck.check(WeekdayCheck.ofRows(rows), nov))
    }

    @Test fun `a date and its weekday in one cell, and a 4주 듀티 across two months`() {
        val oct = nov.minusMonths(1)
        val cells = (26..31).map { d -> "$d(${names[oct.atDay(d).dayOfWeek.value % 7]})" } + (1..8).map { d -> "$d(${names[nov.atDay(d).dayOfWeek.value % 7]})" }
        val pairs = WeekdayCheck.ofRows(listOf(listOf("이름") + cells))
        assertEquals(14, pairs.size)
        assertNull(WeekdayCheck.check(pairs, nov))
    }

    @Test fun `too few weekdays say nothing`() {
        assertNull(WeekdayCheck.check(mapOf(1 to DayOfWeek.MONDAY, 2 to DayOfWeek.TUESDAY), nov))
    }

    @Test fun `a photo's words, each weekday under its date`() {
        fun w(t: String, x: Int, y: Int) = RosterWord(t, x, y, x + 20, y + 24)
        val dec = nov.plusMonths(1)
        val words = (1..10).map { w("$it", 100 + it * 40, 0) } + weekdays(dec, 1..10).mapIndexed { i, t -> w(t, 100 + (i + 1) * 40 + 2, 30) } +
            listOf(w("김민지", 0, 60)) + (1..10).map { w("D", 100 + it * 40, 60) }
        val pairs = WeekdayCheck.ofWords(words)
        assertEquals(10, pairs.size)
        assertEquals(WeekdayCheck.Verdict.OtherMonth(dec), WeekdayCheck.check(pairs, nov))
    }
}
