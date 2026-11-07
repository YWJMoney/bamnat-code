package com.bamnat.app.ui.calendar

import com.bamnat.core.memo.ChecklistItem
import com.bamnat.core.memo.Memo
import com.bamnat.core.memo.MemoReminder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** [CELL-04] 1.39 발굴: a day's memos in its cell, schedules by their time first, each in its own colour, a done checklist struck. */
class CellMemosTest {
    private val day = LocalDate.of(2026, 11, 9)

    @Test fun schedulesComeFirstByTimeThenPinnedThenTheRest() {
        val memos = listOf(
            Memo(id = 1, date = day, title = "택배 오는 날"),
            Memo(id = 2, date = day, title = "치과", reminder = MemoReminder(day.atTime(14, 0), ring = false, notify = true)),
            Memo(id = 3, date = day, title = "월급날", pinned = true, colorIndex = 3),
            Memo(id = 4, date = day, title = "회의", reminder = MemoReminder(day.atTime(9, 30), ring = true, notify = false)),
        )
        val lines = cellMemos(memos, day)
        assertEquals(listOf("09:30 회의", "14:00 치과", "월급날", "택배 오는 날"), lines.map { it.text })
        assertEquals(3, lines[2].color)
    }

    @Test fun aRepeatingScheduleShowsItsTimeOnEachDay() {
        val m = Memo(id = 1, date = day, title = "헬스", repeat = "W1", reminder = MemoReminder(day.minusDays(7).atTime(19, 0), ring = false, notify = true))
        assertEquals("19:00 헬스", cellMemos(listOf(m), day).single().text)
        // A one-off schedule set for another day keeps its title alone.
        val other = Memo(id = 2, date = day, title = "병원", reminder = MemoReminder(day.plusDays(1).atTime(8, 0), ring = false, notify = true))
        assertEquals("병원", cellMemos(listOf(other), day).single().text)
    }

    @Test fun aChecklistIsDoneOnlyWhenEveryItemIs() {
        val done = Memo(id = 1, date = day, title = "장보기", isChecklist = true, checklist = listOf(ChecklistItem("우유", true), ChecklistItem("빵", true)))
        val half = done.copy(id = 2, checklist = listOf(ChecklistItem("우유", true), ChecklistItem("빵")))
        val lines = cellMemos(listOf(done, half), day)
        assertTrue(lines[0].done)
        assertFalse(lines[1].done)
    }

    /** [CELL-07] A schedule is checked against the calendar's shifts at its own time; a memo with no time is not. */
    @Test fun aScheduleThatRunsIntoAShiftSaysSo() {
        val night = com.bamnat.core.memo.ShiftMemoTime.Day(day.minusDays(1), "야간", true, java.time.LocalTime.of(19, 0), java.time.LocalTime.of(7, 0))
        val clashOf = { at: java.time.LocalDateTime -> com.bamnat.core.memo.ShiftClash.of(at, listOf(night))?.text }
        val memos = listOf(
            Memo(id = 1, date = day, title = "은행", reminder = MemoReminder(day.atTime(10, 0), ring = false, notify = true)),
            Memo(id = 2, date = day, title = "저녁 약속", reminder = MemoReminder(day.atTime(18, 0), ring = false, notify = true)),
            Memo(id = 3, date = day, title = "택배"),
        )
        val lines = cellMemos(memos, day, clashOf)
        assertTrue(lines[0].clash!!.contains("잘 시간"))
        assertEquals(null, lines[1].clash)
        assertEquals(null, lines[2].clash)
    }

    @Test fun blankMemosAreLeftOut() {
        assertTrue(cellMemos(listOf(Memo(id = 1, date = day)), day).isEmpty())
    }
}
