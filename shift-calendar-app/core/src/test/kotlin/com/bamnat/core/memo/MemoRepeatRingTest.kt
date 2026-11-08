package com.bamnat.core.memo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 1.39 [TASK-44] PC 6차 메모 매일 반복: '반복: 안 함·매일·평일·쉬는 날', the schedule rings on every day the memo comes back on, and the 한 줄
 * 창 reads '매일' as the repeat instead of leaving it in the title.
 */
class MemoRepeatRingTest {
    private val sun = LocalDate.of(2026, 11, 8)

    @Test fun weekdaysAndDaysOffRepeat() {
        assertEquals("B", MemoRepeat.code(MemoRepeat.Kind.WEEKDAYS, sun))
        assertEquals(MemoRepeat.Kind.WEEKDAYS, MemoRepeat.kindOf("B"))
        assertEquals("평일(월~금)마다", MemoRepeat.label("B"))
        val weekdays = Memo(id = 1, date = sun.plusDays(1), title = "출근 도장", repeat = "B")
        assertEquals((1L..5L).map { sun.plusDays(it) }, MemoRepeat.datesIn(weekdays, sun, sun.plusDays(7)))
        assertFalse(MemoRepeat.followsSchedule("B"))

        assertEquals("R", MemoRepeat.code(MemoRepeat.Kind.REST, sun))
        assertEquals("쉬는 날마다", MemoRepeat.label("R"))
        assertTrue(MemoRepeat.followsSchedule("R"))
        val offs = setOf(sun.plusDays(2), sun.plusDays(6))
        val shifts = object : MemoRepeat.ShiftOf {
            override fun on(date: LocalDate): Long? = null
            override fun rest(date: LocalDate) = date in offs
        }
        val rest = Memo(id = 2, date = sun, title = "장보기", repeat = "R")
        assertEquals(offs.sorted(), MemoRepeat.datesIn(rest, sun, sun.plusDays(7), shifts))
        // Without the schedule: its own date only, as 근무마다 does.
        assertEquals(listOf(sun), MemoRepeat.datesIn(rest, sun, sun.plusDays(7)))
        assertEquals(offs.sorted(), MemoRepeat.expand(listOf(rest), sun, sun.plusDays(7)) { shifts }.map { it.date })
    }

    @Test fun aRepeatingScheduleRingsEveryDayItComesBack() {
        val daily = Memo(id = 3, date = sun, title = "약 먹기", repeat = "D",
            reminder = MemoReminder(sun.atTime(9, 24), ring = false, notify = true))
        val now = sun.atTime(10, 0)
        // Today's 9:24 is gone: tomorrow's, then each day after.
        assertEquals(sun.plusDays(1).atTime(9, 24), MemoSchedules.next(listOf(daily), now))
        assertEquals(sun.plusDays(2).atTime(9, 24), MemoSchedules.next(listOf(daily), sun.plusDays(1).atTime(9, 24)))
        assertEquals(listOf(daily), MemoSchedules.dueAt(listOf(daily), sun.plusDays(3).atTime(9, 24, 30)))
        assertTrue(MemoSchedules.dueAt(listOf(daily), sun.plusDays(3).atTime(9, 25)).isEmpty())
        // Not before its own day; a day finished (끝내기) does not ring; a day taken off neither.
        assertTrue(MemoSchedules.dueAt(listOf(daily), sun.minusDays(1).atTime(9, 24)).isEmpty())
        val done = daily.withDone(true, sun.plusDays(1), 1L)
        assertTrue(MemoSchedules.dueAt(listOf(done), sun.plusDays(1).atTime(9, 24)).isEmpty())
        assertEquals(sun.plusDays(2).atTime(9, 24), MemoSchedules.next(listOf(done), now))
        // A one-off 미루기 rings once, and the time of day stays.
        val put = mapOf(3L to sun.atTime(10, 10))
        assertEquals(sun.atTime(10, 10), MemoSchedules.next(listOf(daily), now, extra = put))
        assertEquals(listOf(daily), MemoSchedules.dueAt(listOf(daily), sun.atTime(10, 10), extra = put))
        assertEquals(sun.plusDays(1).atTime(9, 24), MemoSchedules.next(listOf(daily), sun.atTime(10, 10), extra = put))
        // A memo that does not repeat rings once, as before.
        val once = daily.copy(id = 4, repeat = "")
        assertNull(MemoSchedules.next(listOf(once), now))
        assertEquals(listOf(once), MemoSchedules.dueAt(listOf(once), sun.atTime(9, 24)))
        // 평일: Friday 9:24, then Monday's.
        val weekdays = daily.copy(id = 5, repeat = "B")
        assertEquals(LocalDate.of(2026, 11, 16).atTime(9, 24), MemoSchedules.next(listOf(weekdays), LocalDate.of(2026, 11, 13).atTime(10, 0)))
        // 쉬는 날마다, from the schedule.
        val shifts = object : MemoRepeat.ShiftOf {
            override fun on(date: LocalDate): Long? = null
            override fun rest(date: LocalDate) = date.dayOfWeek == java.time.DayOfWeek.SATURDAY
        }
        val rest = daily.copy(id = 6, repeat = "R")
        assertEquals(LocalDate.of(2026, 11, 14).atTime(9, 24), MemoSchedules.next(listOf(rest), now, { shifts }))
    }

    @Test fun theLineReadsTheRepeat() {
        val now = LocalDateTime.of(2026, 11, 8, 9, 0)
        val line = MemoTime.line("매일 오전 9시 24분 약 먹기", now)
        assertEquals("약 먹기", line.title)
        assertEquals(MemoRepeat.Kind.DAY, line.repeat)
        assertEquals(now.toLocalDate().atTime(9, 24), line.at)
        assertEquals(MemoRepeat.Kind.WEEKDAYS, MemoTime.line("평일마다 8시 출근 도장", now).repeat)
        assertEquals("출근 도장", MemoTime.line("평일마다 8시 출근 도장", now).title)
        assertEquals(MemoRepeat.Kind.REST, MemoTime.line("쉬는 날마다 빨래", now).repeat)
        assertEquals("빨래", MemoTime.line("쉬는 날마다 빨래", now).title)
        assertEquals(MemoRepeat.Kind.NONE, MemoTime.line("내일 9시 치과", now).repeat)
    }

    /** PC 7차 c92cec3e: '평일마다 8시 출근 도장' written at 13:10 read 20:00; a repeat's bare 8시 is the morning one, 오후 flips it. */
    @Test fun aRepeatsBareHourIsTheMorningUnlessAfternoonIsPicked() {
        val at1310 = java.time.LocalDateTime.of(2026, 11, 9, 13, 10)
        val line = MemoTime.line("평일마다 8시 출근 도장", at1310)
        assertEquals(java.time.LocalDateTime.of(2026, 11, 10, 8, 0), line.at)
        assertTrue(line.unmarked)
        assertEquals(java.time.LocalDateTime.of(2026, 11, 9, 20, 0), MemoTime.line("평일마다 8시 출근 도장", at1310, MemoTime.Half.PM).at)
        assertEquals(java.time.LocalDateTime.of(2026, 11, 10, 8, 0), MemoTime.line("평일마다 8시 출근 도장", at1310, MemoTime.Half.AM).at)
        // A one-off bare 8시 at 19시 is still tonight; 오전 written is not 'unmarked'.
        assertEquals(java.time.LocalDateTime.of(2026, 11, 9, 20, 0), MemoTime.line("8시 약 먹기", at1310.withHour(19)).at)
        assertFalse(MemoTime.line("평일마다 오전 8시 출근", at1310).unmarked)
    }
}
