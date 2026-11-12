package com.bamnat.core

import com.bamnat.core.schedule.TurnSlip
import com.bamnat.core.schedule.TurnSlip.Kind
import com.bamnat.core.schedule.TurnSlip.Source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

/** 2.0.0 [RVH-51] PC-72 The day a tap meant in 1.36.1~1.39.0 (설계 docs/plans/2.0.0/PC-72-지난판-다른-날-확인-설계.md 3절). */
class TurnSlipTest {
    private val seoul = ZoneId.of("Asia/Seoul")
    private val sun = DayOfWeek.SUNDAY
    private val mon = DayOfWeek.MONDAY
    private val update = at(2026, 11, 20, 9, 0)

    private fun at(y: Int, m: Int, d: Int, h: Int = 12, min: Int = 0, zone: ZoneId = seoul): Instant =
        LocalDateTime.of(y, m, d, h, min).atZone(zone).toInstant()
    private fun day(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d)

    @Test fun novemberTwelfthOpenedDecemberTenthTwentyEightDaysOn() {
        // PC-28: November's grid starts on Sunday the 1st, December's on Sunday 29 November.
        val (viewed, meant) = TurnSlip.meant(day(2026, 12, 10), day(2026, 11, 12), sun)!!
        assertEquals(YearMonth.of(2026, 11), viewed)
        assertEquals(day(2026, 11, 12), meant)
        // PC 4절: the 연차 on 12/17 was meant for 11/19.
        assertEquals(day(2026, 11, 19), TurnSlip.meant(day(2026, 12, 17), day(2026, 11, 12), sun)!!.second)
    }

    @Test fun aMonthOfSixRowsSendsItThirtyFiveDaysOn() {
        // October 2026 starts on a Thursday: its grid from 27 September, November's from 1 November, 35 days.
        val line = TurnSlip.lines(listOf(Source(Kind.DAY, day(2026, 11, 24), at(2026, 10, 31))), sun, seoul, update).single()
        assertEquals(day(2026, 10, 20), line.meant)
        assertEquals(35, line.days)
    }

    @Test fun theWeekStartsAsTheCalendarSays() {
        // From Monday: November's grid starts 26 October, December's 30 November, 35 days.
        assertEquals(day(2026, 11, 12), TurnSlip.meant(day(2026, 12, 17), day(2026, 11, 12), mon)!!.second)
        assertEquals(day(2026, 11, 19), TurnSlip.meant(day(2026, 12, 17), day(2026, 11, 12), sun)!!.second)
    }

    @Test fun aRowTwoGridsShareIsTheEarlierMonths() {
        // 30 November is in November and in December's first row: from November in sight, a tap on 2 November.
        val (viewed, meant) = TurnSlip.meant(day(2026, 11, 30), day(2026, 11, 5), sun)!!
        assertEquals(YearMonth.of(2026, 11), viewed)
        assertEquals(day(2026, 11, 2), meant)
    }

    @Test fun aMonthTurnedToIsTheNextOneOut() {
        // 20 January is past December's grid (29 Nov ~ 9 Jan): December was in sight, January's grid from 27 December.
        val (viewed, meant) = TurnSlip.meant(day(2027, 1, 20), day(2026, 11, 12), sun)!!
        assertEquals(YearMonth.of(2026, 12), viewed)
        assertEquals(day(2026, 12, 23), meant)
    }

    @Test fun aDayBeforeTheNextMonthsGridIsNotALine() {
        assertNull(TurnSlip.meant(day(2026, 11, 20), day(2026, 11, 12), sun))
        assertNull(TurnSlip.meant(day(2026, 11, 28), day(2026, 11, 12), sun))
        assertEquals(0, TurnSlip.lines(listOf(Source(Kind.DAY, day(2026, 11, 20), at(2026, 11, 12))), sun, seoul, update).size)
    }

    @Test fun aDayChangedOnOrAfterItselfIsNotALine() {
        // 30 November is in December's first row, but changed on the 30th (출퇴근, 위젯, 오늘 카드) it is the day's own record.
        assertNull(TurnSlip.meant(day(2026, 11, 30), day(2026, 11, 30), sun))
        assertNull(TurnSlip.meant(day(2026, 11, 29), day(2026, 11, 30), sun))
        assertEquals(day(2026, 11, 2), TurnSlip.meant(day(2026, 11, 30), day(2026, 11, 29), sun)!!.second)
    }

    @Test fun manyDaysInOneWriteAreNotTaps() {
        // 근무표 넣기 wrote December in one go, 3 ms apart: not asked about. A drag over five days, and taps 400 ms apart, are.
        val import = (1..31).map { Source(Kind.DAY, day(2026, 12, it), at(2026, 11, 12, 10).plusMillis(it * 3L)) }
        val drag = (14..18).map { Source(Kind.DAY, day(2027, 1, it), at(2026, 11, 12, 11).plusMillis(it * 3L)) }
        val taps = (7..16).map { Source(Kind.DAY, day(2026, 12, it), at(2026, 11, 13, 9).plusMillis(it * 400L)) }
        assertEquals(0, TurnSlip.lines(import, sun, seoul, update).size)
        assertEquals(5, TurnSlip.lines(drag, sun, seoul, update).size)
        assertEquals(10, TurnSlip.lines(taps, sun, seoul, update).size)
        // A day changed by the import and then tapped is asked about by its tap.
        val tapped = Source(Kind.DAY, day(2026, 12, 3), at(2026, 11, 14))
        assertEquals(listOf(day(2026, 12, 3)), TurnSlip.lines(import + tapped, sun, seoul, update).map { it.date })
    }

    @Test fun theMonthInSightIsThePhonesOwnAroundMidnight() {
        // 00:30 on 1 November in Seoul is still 31 October in UTC: November was in sight, not October.
        val change = Source(Kind.DAY, day(2026, 12, 2), at(2026, 11, 1, 0, 30))
        assertEquals(day(2026, 11, 4), TurnSlip.lines(listOf(change), sun, seoul, update).single().meant)
        assertEquals(day(2026, 10, 28), TurnSlip.lines(listOf(change), sun, ZoneId.of("UTC"), update).single().meant)
    }

    @Test fun onlyChangesFromOneThirtySixOneUntilTheUpdate() {
        val before = Source(Kind.DAY, day(2026, 11, 28), at(2026, 10, 30, 23, 59))
        val first = Source(Kind.DAY, day(2026, 11, 27), at(2026, 10, 31, 0, 1))
        val after = Source(Kind.DAY, day(2026, 12, 24), at(2026, 11, 20, 9, 1))
        assertEquals(listOf(day(2026, 11, 27)), TurnSlip.lines(listOf(before, first, after), sun, seoul, update).map { it.date })
    }

    @Test fun aDayChangedTwiceIsOneLineByItsLastChange() {
        val lines = TurnSlip.lines(listOf(
            Source(Kind.DAY, day(2026, 12, 10), at(2026, 11, 12, 8)),
            Source(Kind.DAY, day(2026, 12, 10), at(2026, 11, 12, 9)),
            // A memo and a money line on the same day are lines of their own.
            Source(Kind.MEMO, day(2026, 12, 10), at(2026, 11, 12, 9), id = 7),
            Source(Kind.MONEY, day(2026, 12, 10), at(2026, 11, 12, 9), id = 7),
        ), sun, seoul, update)
        assertEquals(3, lines.size)
        assertEquals(at(2026, 11, 12, 9), lines.first { it.source.kind == Kind.DAY }.source.at)
    }

    @Test fun daysToComeFirstAnAlarmOffFirstAmongThemThenGoneByThenMemos() {
        val today = day(2026, 11, 20)
        val lines = TurnSlip.lines(listOf(
            Source(Kind.DAY, day(2026, 12, 10), at(2026, 11, 12)),
            Source(Kind.DAY, day(2026, 12, 17), at(2026, 11, 12)),
            Source(Kind.DAY, day(2026, 11, 2), at(2026, 10, 31)),
            Source(Kind.MEMO, day(2026, 12, 3), at(2026, 11, 5), id = 1),
        ), sun, seoul, update)
        val order = TurnSlip.ordered(lines, today) { it.date == day(2026, 12, 17) }.map { it.date }
        assertEquals(listOf(day(2026, 12, 17), day(2026, 12, 10), day(2026, 11, 2), day(2026, 12, 3)), order)
    }
}
