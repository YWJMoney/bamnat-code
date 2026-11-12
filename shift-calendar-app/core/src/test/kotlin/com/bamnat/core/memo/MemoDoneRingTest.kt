package com.bamnat.core.memo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/** 1.39.1 [R391-06] 검토 MEMO-01: "오후 3시 은행", marked 끝냄 at 1시, still rang at 3시. */
class MemoDoneRingTest {
    private val day = LocalDate.of(2026, 11, 12)
    private val three = day.atTime(15, 0)

    @Test fun aFinishedOneOffMemoDoesNotRing() {
        val bank = Memo(id = 1, date = day, title = "은행", reminder = MemoReminder(three, ring = true, notify = true))
        val one = day.atTime(13, 0)
        assertEquals(three, MemoSchedules.nextOf(bank, one))
        val done = bank.copy(doneAtMillis = 1L)
        assertNull(MemoSchedules.nextOf(done, one))
        assertNull(MemoSchedules.next(listOf(done), one))
        assertEquals(emptyList<Memo>(), MemoSchedules.dueAt(listOf(done), three))
        // Nor its 미룬 time.
        assertNull(MemoSchedules.next(listOf(done), one, extra = mapOf(1L to day.atTime(15, 10))))
        assertEquals(emptyList<Memo>(), MemoSchedules.dueAt(listOf(done), day.atTime(15, 10), extra = mapOf(1L to day.atTime(15, 10))))
        // Brought back, it rings again.
        assertEquals(listOf(bank), MemoSchedules.dueAt(listOf(done.copy(doneAtMillis = null)), three))
    }

    @Test fun aRepeatingMemoFinishedTodayStillRingsTomorrow() {
        val pill = Memo(id = 2, date = day, title = "약", repeat = "D", reminder = MemoReminder(day.atTime(9, 0), ring = false, notify = true))
        val done = pill.withDone(true, day, 1L)
        assertEquals(LocalDateTime.of(day.plusDays(1), java.time.LocalTime.of(9, 0)), MemoSchedules.nextOf(done, day.atTime(8, 0)))
    }
}
