package com.bamnat.core

import com.bamnat.core.model.DayEntry
import com.bamnat.core.model.Leave
import com.bamnat.core.model.WorkPattern
import com.bamnat.core.money.DailySales
import com.bamnat.core.money.Job
import com.bamnat.core.schedule.ScheduleResolver
import com.bamnat.core.sleep.SleepEntry
import com.bamnat.core.work.CalendarFocus
import com.bamnat.core.work.YearRecap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

/** 1.40 [SPREAD-37] 연말 근무 결산. */
class YearRecapTest {
    private fun d(m: Int, day: Int, y: Int = 2026) = LocalDate.of(y, m, day)

    @Test fun windowRunsFromDecember20ToJanuary10() {
        assertNull(YearRecap.yearOf(d(12, 19)))
        assertEquals(2026, YearRecap.yearOf(d(12, 20)))
        assertEquals(2026, YearRecap.yearOf(d(1, 10, 2027)))
        assertNull(YearRecap.yearOf(d(1, 11, 2027)))
        // Closed in December, it comes once more in January with the year finished; closed then, not again.
        assertEquals(2026, YearRecap.due(d(12, 22), 0))
        assertNull(YearRecap.due(d(12, 28), YearRecap.key(d(12, 22))))
        assertEquals(2026, YearRecap.due(d(1, 2, 2027), YearRecap.key(d(12, 22))))
        assertNull(YearRecap.due(d(1, 5, 2027), YearRecap.key(d(1, 2, 2027))))
        assertNull(YearRecap.due(d(11, 12), 0))
    }

    @Test fun rangeStartsWhenTheCalendarDid() {
        assertEquals(d(1, 1) to d(12, 22), YearRecap.range(2026, d(12, 22), startDay = null))
        assertEquals(d(3, 4) to d(12, 31), YearRecap.range(2026, d(1, 3, 2027), startDay = d(3, 4)))
        // A day written by hand before the install (a restored backup) counts from there.
        assertEquals(d(2, 1) to d(12, 31), YearRecap.range(2026, d(1, 3, 2027), startDay = d(3, 4), firstWritten = d(2, 1)))
        // Installed last year: the whole year.
        assertEquals(d(1, 1) to d(12, 31), YearRecap.range(2026, d(1, 3, 2027), startDay = d(6, 1, 2025)))
    }

    @Test fun shiftYearSaysNightsHolidaysAndLongestRest() {
        // 주주야야휴휴 from 1월 1일, a week of leave in August.
        val leave = (10..14).map { DayEntry(d(8, it), leave = Leave()) }
        val r = ScheduleResolver(Fixtures.all, WorkPattern.Rotation(d(1, 1), listOf(1L, 1L, 3L, 3L, null, null)), leave)
        val from = d(1, 1); val to = d(12, 31)
        val y = YearRecap.of(2026, from, to, CalendarFocus.SHIFTS, r.resolveRange(from, to), restCounts = true,
            money = 52_340_000, moneyLabel = "급여 (세전 예상)")
        assertTrue(y.nights in 115..125)
        assertEquals("야간", y.hero.label)
        assertTrue(y.holidayWorkDays > 0)
        // Leave put on a day the pattern already gives off is not taken (the resolver drops it).
        assertEquals(4.0, y.leaveDays, 0.0)
        // The leave joined to the days off around it.
        assertTrue(y.longestRest!!.days >= 5)
        assertTrue(y.tiles.any { it.label == "공휴일 근무" })
        val money = y.tiles.last()
        assertTrue(money.money)
        assertEquals("5,234만 원", money.value)
        // The card's line never says the money.
        assertFalse("만 원" in y.line)
        assertTrue(y.line.startsWith("야간 "))
        assertEquals("1월 1일~12월 31일", y.period)
        assertTrue(y.worthShowing)
    }

    @Test fun dayWorkerSeesNoNights() {
        val r = ScheduleResolver(Fixtures.all, WorkPattern.Weekly(d(1, 1), DayOfWeek.entries.associateWith { if (it.value <= 5) 1L else null }), emptyList())
        val from = d(1, 1); val to = d(12, 22)
        val y = YearRecap.of(2026, from, to, CalendarFocus.SHIFTS, r.resolveRange(from, to), restCounts = true)
        assertEquals(0, y.nights)
        assertEquals("일한 날", y.hero.label)
        assertTrue(y.tiles.none { it.label == "야간" })
        // A pattern of 평일 keeps 공휴일 as work unless changed: they are counted as the calendar says.
        assertTrue(y.unfinished)
        assertEquals("1월 1일부터 12월 22일까지", y.period)
        assertTrue(y.tiles.none { it.money })
    }

    @Test fun partTimerWithoutPatternHasNoRestTile() {
        val entries = (1..60).map { DayEntry(d(3, 1).plusDays(it * 3L), override = com.bamnat.core.model.ShiftOverride.Shift(2L)) }
        val r = ScheduleResolver(Fixtures.all, emptyList(), entries)
        val from = d(3, 1); val to = d(12, 31)
        val y = YearRecap.of(2026, from, to, CalendarFocus.SHIFTS, r.resolveRange(from, to), restCounts = false)
        assertNull(y.longestRest)
        assertTrue(y.tiles.none { it.label == "가장 길게 쉰 날" })
        assertEquals(60, y.workDays)
    }

    @Test fun fieldAndShopCountTheirOwnRecords() {
        val jobs = listOf(
            Job(date = d(5, 5), title = "시공", amount = 300_000),
            Job(date = d(6, 2), endDate = d(6, 4), title = "도배", amount = 900_000),
            Job(date = d(6, 10), title = "취소", amount = 100_000, status = com.bamnat.core.money.JobStatus.CANCELLED),
        )
        val field = YearRecap.of(2026, d(1, 1), d(12, 31), CalendarFocus.JOBS, emptyList(), restCounts = false, jobs = jobs,
            money = 1_200_000, moneyLabel = "일감 수입")
        assertEquals(2, field.jobs)
        assertEquals(4, field.jobDays)
        assertEquals(2, field.jobHolidayDays) // 5월 5일 어린이날, 6월 3일 지방선거
        assertEquals(YearMonth.of(2026, 6), field.busiestMonth!!.first)

        val sales = listOf(DailySales(d(3, 1), 500_000), DailySales(d(7, 7), 900_000), DailySales(d(7, 8), 0))
        val shop = YearRecap.of(2026, d(1, 1), d(12, 31), CalendarFocus.SALES, emptyList(), restCounts = false, sales = sales)
        assertEquals(2, shop.openDays)
        assertEquals(1, shop.holidayOpenDays) // 3월 1일 삼일절
        assertEquals(d(7, 7), shop.bestDay)
        // The best day says its date, never what it sold.
        assertTrue(shop.tiles.none { "원" in it.value })
        assertTrue(YearRecap.of(2026, d(1, 1), d(12, 31), CalendarFocus.INVEST, emptyList(), restCounts = false).isEmpty)
    }

    @Test fun sleepAverageNeedsAWeekOfNights() {
        fun night(m: Int, day: Int, h: Long) = SleepEntry(start = LocalDateTime.of(2026, m, day, 23, 0), end = LocalDateTime.of(2026, m, day, 23, 0).plusHours(h))
        val few = (1..6).map { night(10, it, 7) }
        assertTrue(YearRecap.of(2026, d(1, 1), d(12, 31), CalendarFocus.SALES, emptyList(), false, sleep = few,
            sales = listOf(DailySales(d(3, 2), 1))).tiles.none { it.label == "평균 잠" })
        val week = (1..8).map { night(10, it, if (it % 2 == 0) 6 else 8) } + SleepEntry(start = LocalDateTime.of(2026, 10, 20, 13, 0), end = LocalDateTime.of(2026, 10, 20, 13, 5))
        val y = YearRecap.of(2026, d(1, 1), d(12, 31), CalendarFocus.SALES, emptyList(), false, sleep = week, sales = listOf(DailySales(d(3, 2), 1)))
        assertEquals(8, y.sleepDays)
        assertEquals(7 * 60L, y.sleepAverageMinutes)
    }

    @Test fun extraWorkIsOneTile() {
        val y = YearRecap(2026, d(1, 1), d(12, 31), CalendarFocus.SHIFTS, workDays = 100, extras = listOf("특근" to 12, "대근" to 3))
        val t = y.tiles.single { it.label == "추가 근무" }
        assertEquals("15번", t.value)
        assertEquals("특근 12 · 대근 3", t.note)
        assertEquals("특근", YearRecap(2026, d(1, 1), d(12, 31), CalendarFocus.SHIFTS, workDays = 1, extras = listOf("특근" to 2)).tiles.single { it.value == "2번" }.label)
    }

    @Test fun shortRecordMakesNoCard() {
        val r = ScheduleResolver(Fixtures.all, WorkPattern.Rotation(d(1, 1), listOf(1L, 3L, null)), emptyList())
        val y = YearRecap.of(2026, d(12, 5), d(12, 22), CalendarFocus.SHIFTS, r.resolveRange(d(12, 5), d(12, 22)), true)
        assertFalse(y.worthShowing)
        assertEquals("8,000원", YearRecap.manWon(8_000))
        assertEquals("−12만 원", YearRecap.manWon(-120_000))
    }
}
