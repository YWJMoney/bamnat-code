package com.bamnat.core.memo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** 1.39 (DB 33판) [TASK-02] 메모 끝냄. */
class MemoDoneTest {
    private val day = LocalDate.of(2026, 11, 7)

    @Test fun aMemoThatDoesNotRepeatIsFinishedOnceAndKeepsWhen() {
        val m = Memo(id = 1, date = day, title = "택배")
        assertFalse(m.isDone)
        val done = m.withDone(true, day.plusDays(3), now = 100)
        assertTrue(done.isDone)
        assertTrue("any day: it is finished", done.isDoneOn(day.plusDays(9)))
        assertEquals(100L, done.doneAtMillis)
        // Finished again keeps the first time.
        assertEquals(100L, done.withDone(true, day, now = 200).doneAtMillis)
        assertNull(done.withDone(false, day, now = 300).doneAtMillis)
    }

    @Test fun aRepeatingMemoIsFinishedDayByDay() {
        val m = Memo(id = 1, date = day, title = "약 먹기", repeat = "D")
        val done = m.withDone(true, day.plusDays(1), now = 100)
        assertNull(done.doneAtMillis)
        assertTrue(done.isDoneOn(day.plusDays(1)))
        assertFalse(done.isDoneOn(day.plusDays(2)))
        // Laid on a day (a copy with that date), it reads that day.
        assertTrue(done.copy(date = day.plusDays(1)).isDone)
        assertFalse(done.copy(date = day.plusDays(2)).isDone)
        assertFalse(done.withDone(false, day.plusDays(1), now = 0).isDoneOn(day.plusDays(1)))
    }

    @Test fun aFinishedMemoStepsDownUnderPinnedAndToDo() {
        val pinned = Memo(id = 1, title = "고정", pinned = true, updatedAtMillis = 1)
        val doneNew = Memo(id = 2, title = "끝낸 것", updatedAtMillis = 9, doneAtMillis = 9)
        val todo = Memo(id = 3, title = "할 것", updatedAtMillis = 5)
        assertEquals(listOf(1L, 3L, 2L), listOf(doneNew, todo, pinned).sortedWith(MemoOrdering).map { it.id })
    }
}
