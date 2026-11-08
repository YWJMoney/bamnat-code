package com.bamnat.core.setup

import com.bamnat.core.model.ShiftType
import com.bamnat.core.model.WorkPattern
import com.bamnat.core.schedule.ScheduleResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** 1.39 [FLOW-03] The one line over the calendar as it fills after the first setup. */
class FillMomentTest {
    private val day = ShiftType(11, "주간", "주", 0xFF1E88E5, LocalTime.of(7, 0), LocalTime.of(19, 0))
    private val night = ShiftType(12, "야간", "야", 0xFF5E35B1, LocalTime.of(19, 0), LocalTime.of(7, 0))
    private val off = ShiftType(13, "휴무", "휴", 0xFF9E9E9E, null, null, isOff = true)

    private fun days(pattern: WorkPattern, types: List<ShiftType>, from: LocalDate) =
        ScheduleResolver(types, pattern, emptyList()).resolveRange(from, from.plusDays(70))

    @Test fun nightsAreNamedFirstWithTheRestDaysAndTheFirstAlarm() {
        val today = LocalDate.of(2026, 11, 7)
        // 주 야 야 휴 휴 from today: 11/7..11/30 is 24 days.
        val r = days(WorkPattern.Rotation(today, listOf(day.id, night.id, night.id, off.id, off.id)), listOf(day, night, off), today)
        val line = FillMoment.of(r, today, LocalDateTime.of(2026, 11, 8, 5, 30))!!
        assertEquals("이번 달 남은 야간 10번", line.head)
        assertEquals("쉬는 날 9일 · 첫 알람 내일 05:30", line.rest)
        // 1.39 G6 발굴: once the first alarm has rung, the line no longer names it.
        assertEquals("쉬는 날 9일 · 첫 알람 내일 05:30", line.restAt(LocalDateTime.of(2026, 11, 8, 5, 0)))
        assertEquals("쉬는 날 9일", line.restAt(LocalDateTime.of(2026, 11, 8, 5, 30)))
    }

    @Test fun oneKindOfWorkCountsWorkingDays() {
        val today = LocalDate.of(2026, 11, 2) // a Monday
        // From the 1st, the whole month: no 남은.
        val first = LocalDate.of(2026, 12, 1)
        val dec = WorkPattern.Weekly.fromSteps(first, listOf(day.id, day.id, day.id, day.id, day.id, null, null), holidaysOff = false)
        assertEquals("이번 달 일하는 날 23일", FillMoment.of(days(dec, listOf(day), first), first)!!.head)
        val weekly = WorkPattern.Weekly.fromSteps(today, listOf(day.id, day.id, day.id, day.id, day.id, null, null), holidaysOff = false)
        val line = FillMoment.of(days(weekly, listOf(day), today), today)!!
        assertEquals("이번 달 남은 일하는 날 21일", line.head)
        assertEquals("쉬는 날 8일", line.rest)
    }

    @Test fun theLastDaysOfAMonthCountTheNextToo() {
        val today = LocalDate.of(2026, 11, 28)
        val r = days(WorkPattern.Rotation(today, listOf(day.id, off.id)), listOf(day, off), today)
        assertEquals("다음 달까지 일하는 날 17일", FillMoment.of(r, today)!!.head)
    }

    @Test fun nothingWhenNoDayWorks() {
        val today = LocalDate.of(2026, 11, 7)
        assertNull(FillMoment.of(days(WorkPattern.Rotation(today, listOf(off.id)), listOf(off), today), today))
    }
}
