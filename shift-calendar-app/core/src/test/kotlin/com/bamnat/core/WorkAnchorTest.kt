package com.bamnat.core

import com.bamnat.core.memo.Memo
import com.bamnat.core.memo.MemoReminder
import com.bamnat.core.memo.MemoRepeat
import com.bamnat.core.memo.MemoSchedules
import com.bamnat.core.memo.ShiftMemoTime
import com.bamnat.core.memo.TaskList
import com.bamnat.core.memo.WorkAnchor
import com.bamnat.core.memo.WorkAnchor.From
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2.0.0 [RVIB-04] (검토 I07ac-01) 근무에 붙는 알림: "출근 1시간 전마다", "퇴근 30분 뒤", "야간 끝나고 일어나면" read from each day's work, so a
 * changed shift needs nothing changed and a night past midnight is its own night's.
 */
class WorkAnchorTest {
    private fun t(h: Int, m: Int = 0) = LocalTime.of(h, m)
    private val mon = LocalDate.of(2026, 11, 9)

    /** 일근 09–18 on weekdays; on 11/16 the day moved to 07–15; 11/18 a 야간 22–07; 11/19 a night that starts at 00:30. */
    private val shifts = object : MemoRepeat.ShiftOf {
        override fun on(date: LocalDate): Long? = hours(date)?.let { 1L }
        override fun hours(date: LocalDate): Pair<LocalTime, LocalTime>? = when {
            date == LocalDate.of(2026, 11, 16) -> t(7) to t(15)
            date == LocalDate.of(2026, 11, 18) -> t(22) to t(7)
            date == LocalDate.of(2026, 11, 19) -> t(0, 30) to t(9)
            date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY -> null
            else -> t(9) to t(18)
        }
    }

    private fun memo(a: WorkAnchor, repeat: String = "D") =
        Memo(id = 7, title = "약 챙기기", date = mon, repeat = repeat, reminder = MemoReminder(mon.atTime(8, 0), ring = false, notify = true, work = a))

    @Test fun anAnchorSaysAndKeepsItsPlace() {
        val a = WorkAnchor(From.START, -60)
        assertEquals("S-60", a.code)
        assertEquals(a, WorkAnchor.parse("S-60"))
        assertEquals(WorkAnchor(From.WAKE, 420), WorkAnchor.parse("W420"))
        assertNull(WorkAnchor.parse("X5"))
        assertNull(WorkAnchor.parse("S"))
        assertEquals("출근 1시간 전", a.label)
        assertEquals("출근 30분 전", WorkAnchor(From.START, -30).label)
        assertEquals("퇴근할 때", WorkAnchor(From.END, 0).label)
        assertEquals("퇴근 1시간 30분 뒤", WorkAnchor(From.END, 90).label)
        assertEquals("야간 끝나고 일어나면", WorkAnchor(From.WAKE, 420).label)
    }

    @Test fun theTimeComesFromThatDaysWork() {
        val day = LocalDate.of(2026, 11, 10)
        assertEquals(day.atTime(8, 0), WorkAnchor(From.START, -60).on(day, t(9) to t(18)))
        // A night's end is the next morning's.
        assertEquals(day.plusDays(1).atTime(7, 30), WorkAnchor(From.END, 30).on(day, t(22) to t(7)))
        assertEquals(day.plusDays(1).atTime(14, 0), WorkAnchor(From.WAKE, 420).on(day, t(22) to t(7)))
        // 일어나면 is after a night only.
        assertNull(WorkAnchor(From.WAKE, 420).on(day, t(9) to t(18)))
        assertNull(WorkAnchor(From.START, -60).on(day, null))
    }

    @Test fun everyDayWithWorkAndNotTheOthers() {
        val m = memo(WorkAnchor(From.START, -60))
        // Friday after work: Saturday and Sunday have none, Monday was moved to 07:00, so 06:00.
        assertEquals(LocalDateTime.of(2026, 11, 16, 6, 0), MemoSchedules.nextOf(m, LocalDateTime.of(2026, 11, 13, 19, 0), shifts))
        assertFalse(MemoRepeat.occursOn(m, LocalDate.of(2026, 11, 14), shifts))
        assertTrue(MemoRepeat.occursOn(m, LocalDate.of(2026, 11, 13), shifts))
        // A work that starts after midnight is told the evening before, for its own day.
        val at = LocalDateTime.of(2026, 11, 18, 23, 30)
        assertEquals(at, MemoSchedules.nextOf(m, LocalDateTime.of(2026, 11, 18, 22, 0), shifts))
        assertEquals(listOf(LocalDate.of(2026, 11, 19)), MemoSchedules.dueOn(listOf(m), at, { shifts }).map { it.day })
        // Finished for its day, it waits for the next.
        val done = m.copy(doneDays = setOf(LocalDate.of(2026, 11, 16)))
        assertEquals(LocalDateTime.of(2026, 11, 17, 8, 0), MemoSchedules.nextOf(done, LocalDateTime.of(2026, 11, 13, 19, 0), shifts))
        // 오늘 할 일 shows today's work time.
        assertEquals(t(6), TaskList.of(listOf(m), LocalDate.of(2026, 11, 16)) { shifts }.single().time)
    }

    @Test fun aNightsEndAndTheWakeAfterIt() {
        val wake = memo(WorkAnchor(From.WAKE, 420))
        val at = LocalDateTime.of(2026, 11, 19, 14, 0)
        assertEquals(at, MemoSchedules.nextOf(wake, LocalDateTime.of(2026, 11, 13, 19, 0), shifts))
        assertEquals(listOf(LocalDate.of(2026, 11, 18)), MemoSchedules.dueOn(listOf(wake), at, { shifts }).map { it.day })
        // Once, on its own day's work; without the schedule read, the time it was saved for.
        val once = memo(WorkAnchor(From.END, 30), repeat = "").copy(date = LocalDate.of(2026, 11, 10))
        assertEquals(LocalDateTime.of(2026, 11, 10, 18, 30), MemoSchedules.nextOf(once, LocalDateTime.of(2026, 11, 10, 9, 0), shifts))
        assertEquals(mon.atTime(8, 0), MemoSchedules.nextOf(once, LocalDateTime.of(2026, 11, 9, 7, 0), null))
        // The dialog's preview: the next work day's.
        val days = (0L..10L).map { mon.plusDays(it) }.map { d ->
            val h = shifts.hours(d)
            ShiftMemoTime.Day(d, if (h == null) "휴무" else "근무", h != null, h?.first, h?.second)
        }
        assertEquals(LocalDateTime.of(2026, 11, 16, 6, 0), ShiftMemoTime.workNext(days, WorkAnchor(From.START, -60), LocalDateTime.of(2026, 11, 13, 19, 0)))
    }
}
