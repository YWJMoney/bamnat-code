package com.bamnat.core

import com.bamnat.core.hours.StatsRange
import com.bamnat.core.hours.StatsRange.Mode
import com.bamnat.core.model.WorkPattern
import com.bamnat.core.pay.PayPeriod
import com.bamnat.core.schedule.ScheduleResolver
import com.bamnat.core.work.CalendarFocus
import com.bamnat.core.work.MonthOverview
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 1.32 [CALX-06] (계획/1.32-조사-대조표.md C04~C08): the days 통계 counts, the cut lines and the line that says when the period ends. */
class StatsRangeTest {
    private val oct = YearMonth.of(2026, 10)
    private val today = LocalDate.of(2026, 10, 15) // 목요일
    private val p20 = PayPeriod(endDay = 20, payDay = 25)

    @Test fun eachChipCountsItsDays() {
        assertEquals("10/1~10/31", StatsRange.of(Mode.MONTH, oct, today).label())
        assertEquals("9/21~10/20", StatsRange.of(Mode.PAY, oct, today, p20).label())
        // No period of its own: the month.
        assertEquals("10/1~10/31", StatsRange.of(Mode.PAY, oct, today, PayPeriod()).label())
        val week = StatsRange.of(Mode.WEEK, oct, today)
        assertEquals(LocalDate.of(2026, 10, 12), week.from)
        assertEquals(7L, week.days)
        assertEquals(LocalDate.of(2026, 10, 5), StatsRange.of(Mode.WEEK, oct, today, weekShift = -1).from)
        // A 주휴 week from Sunday.
        assertEquals(LocalDate.of(2026, 10, 11), StatsRange.of(Mode.WEEK, oct, today, weekStart = DayOfWeek.SUNDAY).from)
        // Another month: its last week.
        assertTrue(StatsRange.of(Mode.WEEK, YearMonth.of(2026, 8), today).let { it.from <= LocalDate.of(2026, 8, 31) && it.to >= LocalDate.of(2026, 8, 31) })
        // 직접, turned round and cut to a year.
        val c = StatsRange.of(Mode.CUSTOM, oct, today, custom = StatsRange.Range(LocalDate.of(2026, 10, 10), LocalDate.of(2026, 9, 1)))
        assertEquals(LocalDate.of(2026, 9, 1), c.from)
        assertEquals(StatsRange.MAX_DAYS, StatsRange.of(Mode.CUSTOM, oct, today, custom = StatsRange.Range(LocalDate.of(2020, 1, 1), LocalDate.of(2026, 1, 1))).days)
        // Another year says its year.
        assertEquals("2025.12/1~1/31", StatsRange.Range(LocalDate.of(2025, 12, 1), LocalDate.of(2026, 1, 31)).label(2026))
    }

    @Test fun theLineSaysWhenThisPeriodEnds() {
        assertEquals("이번 급여 기간은 10월 20일에 끝나요 · 5일 남았어요, 급여일 10월 25일", StatsRange.endsLine(p20, today))
        assertEquals("이번 급여 기간은 11월 20일에 끝나요 · 30일 남았어요, 급여일 11월 25일", StatsRange.endsLine(p20, LocalDate.of(2026, 10, 21)))
        assertNull(StatsRange.endsLine(PayPeriod(payDay = 10), today))
        val weekly = StatsRange.endsLine(PayPeriod(payWeekday = DayOfWeek.FRIDAY), today)!!
        assertTrue(weekly, weekly.contains("일요일(10/18)") && weekly.contains("3일 남았어요") && weekly.endsWith("금요일에 들어와요"))
    }

    @Test fun theCutLinesAreTheDaysAfterEachClose() {
        val cuts = StatsRange.cutDays(p20, oct.atDay(1).minusDays(7), oct.atEndOfMonth().plusDays(14))
        assertEquals(setOf(LocalDate.of(2026, 10, 21)), cuts)
        assertEquals(emptySet<LocalDate>(), StatsRange.cutDays(PayPeriod(), oct.atDay(1), oct.atEndOfMonth()))
    }

    @Test fun theCalendarSummaryCountsTheSameDays() {
        val weekdays = WorkPattern.Weekly.fromSteps(LocalDate.of(2026, 1, 1), listOf(1L, 1L, 1L, 1L, 1L, null, null))
        val resolver = ScheduleResolver(Fixtures.all, weekdays, emptyList())
        val month = MonthOverview.of(oct, CalendarFocus.SHIFTS, resolver)
        val pay = MonthOverview.of(oct, CalendarFocus.SHIFTS, resolver, range = StatsRange.of(Mode.PAY, oct, today, p20))
        assertTrue(month.workDays > 0 && pay.workDays > 0)
        val direct = resolver.resolveRange(LocalDate.of(2026, 9, 21), LocalDate.of(2026, 10, 20)).count { it.totalMinutes > 0 }
        assertEquals(direct, pay.workDays)
    }
}
