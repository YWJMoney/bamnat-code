package com.bamnat.core

import com.bamnat.core.hours.WorkHoursCalculator
import com.bamnat.core.leave.LeaveSources
import com.bamnat.core.model.DayEntry
import com.bamnat.core.model.Leave
import com.bamnat.core.model.LeaveKind
import com.bamnat.core.model.ShiftOverride
import com.bamnat.core.model.ShiftType
import com.bamnat.core.model.WorkPattern
import com.bamnat.core.schedule.IcsWriter
import com.bamnat.core.schedule.ResolvedDay
import com.bamnat.core.schedule.ScheduleResolver
import com.bamnat.core.schedule.ShiftEvents
import com.bamnat.core.work.ShiftHealth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * 2.0.0 [RVH-44][RVH-45] 결정 19번 H-계산: a day of two parts and an hour's leave on the clock (설계
 * docs/plans/2.0.0/두-구간-근무-시간-연차-설계.md 2-3·2-5·2-6·2-7·3-3·4-5절). A day of one part reads as before (the tests of one part
 * pass unchanged: 총괄's line).
 */
class TwoPartDayCalcTest {
    private val day = LocalDate.of(2026, 11, 18) // a Wednesday
    private fun t(h: Int, m: Int = 0) = LocalTime.of(h, m)
    private fun at(h: Int, m: Int = 0, next: Boolean = false) = (if (next) day.plusDays(1) else day).atTime(h, m)

    private fun type(start: LocalTime, end: LocalTime, gapStart: LocalTime? = null, gap: Int = 0, rest: Int = 0, restAt: LocalTime? = null) =
        ShiftType(1, "주간", "주", 0xFF1565C0, start, end, breakMinutes = rest, breakStart = restAt, gapStart = gapStart, gapMinutes = gap)

    private fun resolve(type: ShiftType, entry: DayEntry? = null, legal: Boolean = false): ResolvedDay =
        ScheduleResolver(listOf(type), listOf(WorkPattern.Rotation(day, listOf(1L))), listOfNotNull(entry), legalBreaks = legal).resolve(day)

    private fun leave(l: Leave) = DayEntry(day, leave = l)
    private fun parts(d: ResolvedDay) = d.segments.map { it.start to it.endInclusive }

    // ---- 2-3절 parts on the clock and '근무 중' ----

    @Test fun aShopDayHasTwoPartsAndIsNotWorkingInTheGap() {
        val d = resolve(type(t(11), t(21), t(15), 120))
        assertEquals(t(11), d.start)
        assertEquals(t(21), d.end)
        assertEquals(480, d.scheduledMinutes)
        assertEquals(0, d.breakMinutes)
        assertEquals(at(15), d.gapFrom)
        assertEquals(at(17), d.gapTo)
        assertEquals(120, d.gapMinutes)
        assertEquals(listOf(at(11) to at(15), at(17) to at(21)), parts(d))
        assertTrue(d.split)
        assertTrue(d.workingAt(at(11)))
        assertTrue(d.workingAt(at(14, 59)))
        assertFalse("브레이크 타임", d.workingAt(at(15)))
        assertFalse(d.workingAt(at(16)))
        assertTrue(d.workingAt(at(17)))
        assertFalse("끝", d.workingAt(at(21)))
    }

    @Test fun aDayOfOnePartIsOnePartAsBefore() {
        val d = resolve(type(t(9), t(18), rest = 60, restAt = t(12)))
        assertEquals(listOf(at(9) to at(18)), parts(d))
        assertFalse(d.split)
        assertNull(d.gapFrom)
        assertEquals(480, d.scheduledMinutes)
        assertTrue("휴게는 근무 중(휴게 시각은 급여 쪽)", d.workingAt(at(12, 30)))
        assertEquals(480, type(t(9), t(18), rest = 60).workMinutes)
    }

    @Test fun theKindOfDaysWorkMinutesLeaveTheGapOut() {
        assertEquals(480, type(t(11), t(21), t(15), 120).workMinutes)
        assertEquals(420, type(t(6), t(23), t(9), 600).workMinutes)
        assertEquals("맞지 않는 구간 사이는 쓰지 않음", 540, type(t(9), t(18), t(17), 120).workMinutes)
    }

    // ---- 2-5절 past midnight ----

    @Test fun aGapPastMidnightIsOnTheDaysOwnLine() {
        // 20:00~23:00 and 01:00~05:00.
        val d = resolve(type(t(20), t(5), t(23), 120))
        assertEquals(420, d.scheduledMinutes)
        assertEquals(listOf(at(20) to at(23), at(1, next = true) to at(5, next = true)), parts(d))
        assertFalse(d.workingAt(at(0, 30, next = true)))
        assertTrue(d.workingAt(at(2, next = true)))
    }

    @Test fun aGapRightAtMidnightAndADayOfDawnAndNight() {
        val m = resolve(type(t(22), t(4), t(0), 60))
        assertEquals(listOf(at(22) to at(0, next = true), at(1, next = true) to at(4, next = true)), parts(m))
        assertEquals(300, m.scheduledMinutes)
        // 06:00~09:00 · 19:00~23:00: ten hours between.
        val dawn = resolve(type(t(6), t(23), t(9), 600))
        assertEquals(listOf(at(6) to at(9), at(19) to at(23)), parts(dawn))
        assertEquals(420, dawn.scheduledMinutes)
    }

    @Test fun theWeekSplitAtMidnightCountsOnlyThePartsAfterIt() {
        // A Sunday 20:00~23:00 · 01:00~05:00: 4 of its 7 hours are Monday's.
        val sunday = LocalDate.of(2026, 11, 22)
        val r = ScheduleResolver(listOf(type(t(20), t(5), t(23), 120)), listOf(WorkPattern.Weekly(sunday.minusDays(6),
            java.time.DayOfWeek.entries.associateWith { if (it == java.time.DayOfWeek.SUNDAY) 1L else null })), emptyList())
        val c = WorkHoursCalculator(r)
        assertEquals(420, c.week(sunday).totalMinutes)
        assertEquals(180, c.weekSplitAtMidnight(sunday))
    }

    @Test fun aSecondPartOverASundayIntoMondayStaysOnItsOwnDate() {
        val sunday = LocalDate.of(2026, 11, 22)
        val r = ScheduleResolver(listOf(type(t(18), t(3), t(21), 120)), listOf(WorkPattern.Rotation(sunday, listOf(1L))), emptyList())
        val d = r.resolve(sunday)
        assertEquals(sunday, d.date)
        assertEquals(listOf(sunday.atTime(18, 0) to sunday.atTime(21, 0), sunday.atTime(23, 0) to sunday.plusDays(1).atTime(3, 0)),
            d.segments.map { it.start to it.endInclusive })
    }

    // ---- 2-6절 a day's own times or gap ----

    @Test fun aDaysOwnGapOrNoneThatDay() {
        val shop = type(t(11), t(21), t(15), 120)
        val noGap = resolve(shop, DayEntry(day, customGapMinutes = 0))
        assertEquals("오늘은 이어서 일해요", 600, noGap.scheduledMinutes)
        assertFalse(noGap.split)
        val own = resolve(shop, DayEntry(day, customGapStart = t(14), customGapMinutes = 60))
        assertEquals(540, own.scheduledMinutes)
        assertEquals(at(14), own.gapFrom)
        // Times of the day's own that push the gap out: one part, never cut to fit.
        val moved = resolve(shop, DayEntry(day, customStart = t(16), customEnd = t(21)))
        assertFalse(moved.split)
        assertEquals(300, moved.scheduledMinutes)
        val tooShort = resolve(shop, DayEntry(day, customStart = t(11), customEnd = t(17, 20)))
        assertFalse("둘째 구간이 30분보다 짧음", tooShort.split)
    }

    // ---- 2-3절 법정 휴게 one rule ----

    @Test fun theLegalBreakIsTheOuterDaysLessTheGap() {
        assertEquals("11~15·17~21: 그대로 8시간", 480, resolve(type(t(11), t(21), t(15), 120), legal = true).scheduledMinutes)
        assertEquals("5시간 + 5시간, 사이 2시간", 600, resolve(type(t(9), t(21), t(14), 120), legal = true).scheduledMinutes)
        // 09:00~18:30 with 30 minutes between: 1 hour less 30 minutes.
        val nine = resolve(type(t(9), t(18, 30), t(13), 30), legal = true)
        assertEquals(510, nine.scheduledMinutes)
        assertEquals(30, nine.breakMinutes)
        assertEquals("한 구간은 그대로", 480, resolve(type(t(9), t(18)), legal = true).scheduledMinutes)
    }

    // ---- 2-7절 반차 along the parts ----

    @Test fun halfADayOffAShopDayComesOffAlongTheParts() {
        val shop = type(t(11), t(21), t(15), 120)
        val back = resolve(shop, leave(Leave(quarters = 2)))
        assertEquals(t(11) to t(15), back.start to back.end)
        assertEquals(240, back.scheduledMinutes)
        assertEquals(240, back.leaveMinutes)
        assertFalse(back.split)
        val front = resolve(shop, leave(Leave(quarters = 2, fromStart = true)))
        assertEquals("알람은 남은 첫 구간 시작", t(17) to t(21), front.start to front.end)
        assertFalse(front.split)
    }

    @Test fun fivePlusThreeAndThreePlusFive() {
        // 10~15 · 17~20.
        val fiveThree = type(t(10), t(20), t(15), 120)
        val b = resolve(fiveThree, leave(Leave(quarters = 2)))
        assertEquals(t(10) to t(14), b.start to b.end)
        val f = resolve(fiveThree, leave(Leave(quarters = 2, fromStart = true)))
        assertEquals("앞 5시간 중 4시간", t(14) to t(20), f.start to f.end)
        assertTrue(f.split)
        assertEquals(listOf(at(14) to at(15), at(17) to at(20)), parts(f))
        // 10~13 · 15~20.
        val threeFive = type(t(10), t(20), t(13), 120)
        val b2 = resolve(threeFive, leave(Leave(quarters = 2)))
        assertEquals(t(10) to t(16), b2.start to b2.end)
        assertEquals(listOf(at(10) to at(13), at(15) to at(16)), parts(b2))
        val f2 = resolve(threeFive, leave(Leave(quarters = 2, fromStart = true)))
        assertEquals(t(16) to t(20), f2.start to f2.end)
        for (q in listOf(1, 2, 3)) for (s in listOf(true, false)) for (shop in listOf(fiveThree, threeFive, type(t(11), t(21), t(15), 120))) {
            val d = resolve(shop, leave(Leave(quarters = q, fromStart = s)))
            assertEquals("$q $s", 480 - Math.round(480 * q / 4.0).toInt(), d.scheduledMinutes)
            assertEquals("$q $s parts", d.scheduledMinutes, d.segmentMinutes - d.breakMinutes)
        }
    }

    @Test fun halfADayOfOnePartIsAsBefore() {
        val office = type(t(9), t(18), rest = 60)
        val b = resolve(office, leave(Leave(quarters = 2)))
        assertEquals(t(9) to t(13, 30), b.start to b.end)
        assertEquals(30, b.breakMinutes)
        val f = resolve(office, leave(Leave(quarters = 2, fromStart = true)))
        assertEquals(t(13, 30) to t(18), f.start to f.end)
    }

    // ---- 3-3절 an hour's leave ----

    @Test fun anHoursLeaveOffTheEndKeepsTheBreakWhereItIs() {
        val office = type(t(9), t(18), rest = 60, restAt = t(12))
        val d = resolve(office, leave(Leave.hours(LeaveKind.ANNUAL, 120)))
        assertEquals(t(9) to t(16), d.start to d.end)
        assertEquals(60, d.breakMinutes)
        assertEquals(t(12), d.breakStart)
        assertEquals(120, d.leaveMinutes)
        assertEquals(360, d.scheduledMinutes)
    }

    @Test fun anHoursLeaveOffTheStartTakesTheBreakItPasses() {
        val office = type(t(9), t(18), rest = 60, restAt = t(12))
        val d = resolve(office, leave(Leave.hours(LeaveKind.ANNUAL, 240, fromStart = true)))
        assertEquals("09~12 + 13~14", t(14) to t(18), d.start to d.end)
        assertEquals(0, d.breakMinutes)
        assertEquals(240, d.scheduledMinutes)
        val three = resolve(office, leave(Leave.hours(LeaveKind.ANNUAL, 180, fromStart = true)))
        assertEquals("휴게로 시작하지 않음", t(13), three.start)
        assertEquals(300, three.scheduledMinutes)
    }

    @Test fun anHoursLeaveInTheMiddleMakesTwoParts() {
        val office = type(t(9), t(18), rest = 60, restAt = t(12))
        val d = resolve(office, leave(Leave.hours(LeaveKind.ANNUAL, 120, at = t(10))))
        assertEquals(t(9) to t(18), d.start to d.end)
        assertEquals(at(10), d.leaveFrom)
        assertEquals(at(12), d.leaveTo)
        assertEquals(listOf(at(9) to at(10), at(12) to at(18)), parts(d))
        assertFalse("외출 중", d.workingAt(at(11)))
        assertEquals(60, d.breakMinutes)
        assertEquals(360, d.scheduledMinutes)
        // From 11:00 for 2 hours of work, the 12:00 break passed over on the clock.
        val lunch = resolve(office, leave(Leave.hours(LeaveKind.ANNUAL, 120, at = t(11))))
        assertEquals(at(14), lunch.leaveTo)
        assertEquals(0, lunch.breakMinutes)
        assertEquals(360, lunch.scheduledMinutes)
    }

    @Test fun anHoursLeaveWithTheBreakTimeUnknownTakesItsMinutesOfClock() {
        val d = resolve(type(t(9), t(18), rest = 60), leave(Leave.hours(LeaveKind.ANNUAL, 120)))
        assertEquals(t(16), d.end)
        assertEquals(60, d.breakMinutes)
        assertEquals(360, d.scheduledMinutes)
    }

    @Test fun anHoursLeaveAsLongAsTheDayIsTheWholeDayAndAMiddleOneOnAShopDayComesOffTheEnd() {
        val whole = resolve(type(t(9), t(13)), leave(Leave.hours(LeaveKind.ANNUAL, 300)))
        assertNull(whole.start)
        assertEquals(0, whole.scheduledMinutes)
        assertEquals(240, whole.leaveMinutes)
        val shop = resolve(type(t(11), t(21), t(15), 120), leave(Leave.hours(LeaveKind.ANNUAL, 60, at = t(12))))
        assertEquals("구간 사이가 있는 날은 가운데 연차 없이 뒤에서", t(11) to t(20), shop.start to shop.end)
        assertNull(shop.leaveFrom)
        assertEquals(420, shop.scheduledMinutes)
    }

    @Test fun anHoursLeaveIsItsMinutesOverTheCompanysDay() {
        val r = ScheduleResolver(listOf(type(t(9), t(18), rest = 60)), listOf(WorkPattern.Rotation(day, listOf(1L))),
            listOf(leave(Leave.hours(LeaveKind.ANNUAL, 120))))
        assertEquals(0.25, LeaveSources.uses(listOf(leave(Leave.hours(LeaveKind.ANNUAL, 120))), r::resolve, null).single().days, 1e-9)
    }

    // ---- 4-5절 the phone's calendar, night ----

    @Test fun aDayOfTwoPartsIsTwoEventsOfOneName() {
        val r = ScheduleResolver(listOf(type(t(11), t(21), t(15), 120)), listOf(WorkPattern.Rotation(day, listOf(1L))), emptyList())
        val events = ShiftEvents.of(listOf(r.resolve(day)))
        assertEquals(listOf("$day", "$day-p2"), events.map { it.key })
        assertEquals(listOf(at(11) to at(15), at(17) to at(21)), events.map { it.start to it.end })
        assertEquals(setOf("주간"), events.map { it.title }.toSet())
        assertNull("브레이크 타임에는 근무 중이 아님", ShiftEvents.current(listOf(r.resolve(day)), at(16)))
        assertEquals(at(17), ShiftEvents.nextChange(listOf(r.resolve(day)), at(16)))
        val ics = IcsWriter.write(listOf(r.resolve(day)), ZoneId.of("Asia/Seoul"), LocalDateTime.of(2026, 11, 1, 0, 0))
        assertTrue(ics.contains("UID:20261118@bamnat.app"))
        assertTrue(ics.contains("UID:20261118-p2@bamnat.app"))
        assertTrue(ics.contains("DTSTART;TZID=Asia/Seoul:20261118T170000"))
        assertEquals(2, Regex("BEGIN:VEVENT").findAll(ics).count())
    }

    @Test fun aNightIsAskedOfEachPart() {
        assertFalse("06~09 · 17~21", ShiftHealth.night(resolve(type(t(6), t(21), t(9), 480))))
        assertTrue("20~23 · 01~05", ShiftHealth.night(resolve(type(t(20), t(5), t(23), 120))))
        assertTrue("한 구간 22~07", ShiftHealth.night(resolve(type(t(22), t(7)))))
    }

    // ---- [RVH-58] extra work on a day of two parts ----

    /**
     * 2.0.0 [RVH-58] (갈래 H, 총괄 PC-109를 보다 함께): the gap is not the shift. 대타 15~17 in a 11~15·17~21 shop day was taken as
     * inside the shift (its first start to its last end) and added no hours.
     */
    @Test fun workInTheGapCountsAndOverAPartOnlyTheTimeOutsideIt() {
        val shop = type(t(11), t(21), t(15), 120)
        fun extra(from: Int, to: Int) = ScheduleResolver(listOf(shop), listOf(WorkPattern.Rotation(day, listOf(1L))),
            listOf(DayEntry(day, extras = listOf(com.bamnat.core.model.ExtraEntry(com.bamnat.core.model.ExtraKind.COVER_ID, t(from), t(to)))))).resolve(day).extras.single()
        val gap = extra(15, 17)
        assertEquals("브레이크 타임의 대타 2시간", 120, gap.minutes)
        assertEquals(0, gap.insideShiftMinutes)
        val over = extra(14, 18)
        assertEquals("14~15·17~18은 근무 안, 15~17만", 120, over.minutes)
        assertEquals(120, over.insideShiftMinutes)
        assertEquals("시각은 적은 대로", at(14), over.from)
        val inside = extra(12, 14)
        assertEquals(0, inside.minutes)
        assertEquals(120, inside.insideShiftMinutes)
        val after = extra(20, 23)
        assertEquals("21~23", 120, after.minutes)
        assertEquals("한 구간에만 걸치면 한 구간 날처럼 그 밖만", at(21), after.from)
        // A day of one part: as before (PAYX-54, SchoolingInShiftTest).
        val one = ScheduleResolver(listOf(type(t(9), t(18))), listOf(WorkPattern.Rotation(day, listOf(1L))),
            listOf(DayEntry(day, extras = listOf(com.bamnat.core.model.ExtraEntry(com.bamnat.core.model.ExtraKind.COVER_ID, t(16), t(20)))))).resolve(day).extras.single()
        assertEquals(120, one.minutes)
        assertEquals(at(18), one.from)
    }

    @Test fun aSwappedDayKeepsTheGapOfTheKindItTakes() {
        val other = ShiftType(2, "가게", "가", 0xFF2E7D32, t(11), t(21), gapStart = t(15), gapMinutes = 120)
        val r = ScheduleResolver(listOf(type(t(9), t(18)), other), listOf(WorkPattern.Rotation(day, listOf(1L))),
            listOf(DayEntry(day, override = ShiftOverride.Shift(2))))
        assertTrue(r.resolve(day).split)
    }
}
