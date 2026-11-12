package com.bamnat.core

import com.bamnat.core.memo.Memo
import com.bamnat.core.memo.MemoBacklog
import com.bamnat.core.memo.MemoReminder
import com.bamnat.core.memo.ShiftMemoTime
import com.bamnat.core.memo.WorkAnchor
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 2.0.0 [RVIB-05] (검토 I07ac-02) 근무 중에 온 일정은 퇴근 때 한 장, 지난 할 일은 쉬는 날 아침 한 장. */
class MemoBacklogTest {
    private fun t(h: Int, m: Int = 0) = LocalTime.of(h, m)
    private val thu = LocalDate.of(2026, 11, 12)

    /** 주간 09–18 on weekdays, a 야간 22–07 on 11/12; 토·일 off. */
    private fun hours(d: LocalDate): Pair<LocalTime, LocalTime>? = when {
        d == thu -> t(22) to t(7)
        d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY -> null
        else -> t(9) to t(18)
    }

    @Test fun onlyAToldScheduleWaits() {
        val at = thu.atTime(15, 0)
        assertTrue(MemoBacklog.waits(Memo(id = 1, reminder = MemoReminder(at, ring = false, notify = true))))
        // A ringing one rings on time; one the work tells (출근 1시간 전) is the work's own time.
        assertFalse(MemoBacklog.waits(Memo(id = 2, reminder = MemoReminder(at, ring = true, notify = true))))
        assertFalse(MemoBacklog.waits(Memo(id = 3, reminder = MemoReminder(at, ring = false, notify = true, work = WorkAnchor(WorkAnchor.From.START, -60)))))
        assertFalse(MemoBacklog.waits(Memo(id = 4)))
    }

    @Test fun theEndOfTheWorkItFallsIn() {
        val wed = thu.minusDays(1)
        assertEquals(wed.atTime(18, 0), MemoBacklog.workEnd(wed.atTime(15, 0), ::hours))
        assertNull(MemoBacklog.workEnd(wed.atTime(18, 0), ::hours))
        assertNull(MemoBacklog.workEnd(wed.atTime(8, 59), ::hours))
        // A night past midnight is the night before's.
        assertEquals(thu.plusDays(1).atTime(7, 0), MemoBacklog.workEnd(thu.plusDays(1).atTime(3, 0), ::hours))
        assertNull(MemoBacklog.workEnd(thu.plusDays(2).atTime(12, 0), ::hours))
    }

    @Test fun theNextDayOffsMorning() {
        val days = { d: LocalDate -> hours(d).let { h -> ShiftMemoTime.Day(d, if (h == null) "휴무" else "근무", h != null, h?.first, h?.second) } }
        val rest = { d: LocalDate -> hours(d) == null }
        // Thursday's 야간 then Friday's 주간: Saturday at 9.
        assertEquals(LocalDate.of(2026, 11, 14).atTime(9, 0), MemoBacklog.restMorning(thu.atTime(10, 0), rest, days))
        // On Saturday at 10, Sunday's.
        assertEquals(LocalDate.of(2026, 11, 15).atTime(9, 0), MemoBacklog.restMorning(LocalDate.of(2026, 11, 14).atTime(10, 0), rest, days))
        // The day off after a 야간 is told in the afternoon.
        val afterNight = { d: LocalDate -> d == thu.plusDays(1) }
        assertEquals(thu.plusDays(1).atTime(14, 0), MemoBacklog.restMorning(thu.atTime(10, 0), afterNight, days))
        assertNull(MemoBacklog.restMorning(thu.atTime(10, 0), { false }, days))
    }

    @Test fun whatIsLeftAndHowItIsSaid() {
        val today = LocalDate.of(2026, 11, 14)
        val memos = listOf(
            Memo(id = 1, title = "약 받기", date = today.minusDays(2), reminder = MemoReminder(today.minusDays(2).atTime(15, 0), ring = false, notify = true)),
            Memo(id = 2, title = "장보기", date = today.minusDays(20), reminder = MemoReminder(today.minusDays(20).atTime(10, 0), ring = false, notify = true)),
            // Finished, a plain note, and today's: not left.
            Memo(id = 3, title = "택배", date = today.minusDays(1), reminder = MemoReminder(today.minusDays(1).atTime(9, 0), ring = false, notify = true), doneAtMillis = 1L),
            Memo(id = 4, title = "회식", date = today.minusDays(1)),
            Memo(id = 5, title = "세탁", date = today, reminder = MemoReminder(today.atTime(9, 0), ring = false, notify = true)),
        )
        val left = MemoBacklog.left(memos, today)
        assertEquals(listOf(2L, 1L), left.map { it.id })
        assertEquals("지난 할 일 2개" to "쉬는 날이에요. 천천히 해도 돼요 · 장보기 · 약 받기", MemoBacklog.restLines(left))
        assertEquals("가 · 나 · 다 외 2개", MemoBacklog.named(listOf("가", "나", "다", "라", "마")))
        assertEquals("근무 중에 온 일정 3개", MemoBacklog.workTitle(3))
    }
}
