package com.bamnat.core.memo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** 1.39 [CAL2-20] 약속 겹침 경고, [TASK-04][TASK-20] 알림에서 미루기. */
class ShiftClashSnoozeTest {
    private val today = LocalDate.of(2026, 11, 9)
    private fun night(d: LocalDate) = ShiftMemoTime.Day(d, "야간", true, LocalTime.of(22, 0), LocalTime.of(7, 0))
    private fun dayShift(d: LocalDate) = ShiftMemoTime.Day(d, "주간", true, LocalTime.of(9, 0), LocalTime.of(18, 0))
    private fun off(d: LocalDate) = ShiftMemoTime.Day(d, "휴무", false, null, null)

    @Test fun aTimeInsideAShiftIsSaid() {
        val days = listOf(dayShift(today), night(today.plusDays(1)))
        assertEquals("주간(09:00~18:00)과 겹쳐요", ShiftClash.of(today.atTime(15, 0), days)?.text)
        // Over midnight, on the next day's early hours.
        assertEquals("야간(22:00~07:00)과 겹쳐요", ShiftClash.of(today.plusDays(2).atTime(3, 0), days)?.text)
        // The end itself is free.
        assertNull(ShiftClash.of(today.atTime(18, 0), days))
    }

    @Test fun theMorningAfterANightIsSleep() {
        val days = listOf(night(today.minusDays(1)), off(today))
        val c = ShiftClash.of(today.atTime(10, 0), days)!!
        assertTrue(c.sleep)
        assertEquals("야간 퇴근(07:00) 뒤 3시간이에요 · 잘 시간일 수 있어요", c.text)
        assertEquals("야간 퇴근(07:00) 뒤 30분이에요 · 잘 시간일 수 있어요", ShiftClash.of(today.atTime(7, 30), days)?.text)
        // Six hours on, the afternoon is free.
        assertNull(ShiftClash.of(today.atTime(13, 0), days))
        // A day shift's evening is not sleep.
        assertNull(ShiftClash.of(today.atTime(19, 0), listOf(dayShift(today))))
    }

    @Test fun noShiftsNoHoursAndDaysOffWarnOfNothing() {
        assertNull(ShiftClash.of(today.atTime(10, 0), emptyList()))
        assertNull(ShiftClash.of(today.atTime(10, 0), listOf(off(today))))
        assertNull(ShiftClash.of(today.atTime(10, 0), listOf(ShiftMemoTime.Day(today, "일근", true, null, null))))
    }

    @Test fun snoozeGoesToTheNextDayOffMorningOrTomorrow() {
        val now = LocalDateTime.of(2026, 11, 9, 14, 3, 20)
        val was = LocalDateTime.of(2026, 11, 9, 14, 0)
        val rests = setOf(today.plusDays(3), today.plusDays(4))
        val rest = MemoSnooze.nextRest(today) { it in rests }
        assertEquals(today.plusDays(3), rest)
        assertEquals(MemoSnooze.Way.REST, MemoSnooze.later(rest))
        assertEquals("쉬는 날(11/12)", MemoSnooze.label(MemoSnooze.Way.REST, rest))
        assertEquals(LocalDateTime.of(2026, 11, 12, 9, 0), MemoSnooze.at(MemoSnooze.Way.REST, now, was, rest))
        assertEquals(LocalDateTime.of(2026, 11, 9, 14, 13), MemoSnooze.at(MemoSnooze.Way.MIN10, now, was, rest))
        assertEquals(LocalDateTime.of(2026, 11, 9, 15, 3), MemoSnooze.at(MemoSnooze.Way.HOUR1, now, was, rest))
        // No day off ahead (투자, nothing scheduled): tomorrow at the same time.
        assertNull(MemoSnooze.nextRest(today) { false })
        assertEquals(MemoSnooze.Way.TOMORROW, MemoSnooze.later(null))
        // 1.39 [TASK-39] 다른 때…: the day off first when there is one, then the times.
        assertEquals(listOf(MemoSnooze.Way.REST, MemoSnooze.Way.MIN10, MemoSnooze.Way.MIN30, MemoSnooze.Way.HOUR1, MemoSnooze.Way.HOUR3,
            MemoSnooze.Way.TOMORROW), MemoSnooze.choices(rest))
        assertEquals(MemoSnooze.Way.MIN10, MemoSnooze.choices(null).first())
        assertEquals(LocalDateTime.of(2026, 11, 9, 14, 33), MemoSnooze.at(MemoSnooze.Way.MIN30, now, was, rest))
        assertEquals(LocalDateTime.of(2026, 11, 9, 17, 3), MemoSnooze.at(MemoSnooze.Way.HOUR3, now, was, rest))
        assertEquals(LocalDateTime.of(2026, 11, 10, 14, 0), MemoSnooze.at(MemoSnooze.Way.TOMORROW, now, was, null))
        assertEquals(MemoSnooze.Way.REST, MemoSnooze.Way.of("rest"))
    }
}
