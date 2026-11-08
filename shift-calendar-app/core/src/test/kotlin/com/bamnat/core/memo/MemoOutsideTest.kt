package com.bamnat.core.memo

import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

/** 1.39 [TASK-39] PC 6차: a 10분 뒤 pressed on the notice while the editor was open was undone by the next letter typed. */
class MemoOutsideTest {
    private val day = LocalDate.of(2026, 11, 8)
    private val was = MemoReminder(LocalDateTime.of(2026, 11, 8, 9, 35), ring = false, notify = true)
    private val base = Memo(id = 7, date = day, title = "약", reminder = was)

    @Test fun theNoticesNewTimeAndDoneStayOverALetterTyped() {
        val stored = base.copy(reminder = was.copy(at = was.at.plusMinutes(10)), doneAtMillis = 5L)
        val typed = base.copy(title = "약 먹기")
        val kept = MemoOutside.keep(typed, base, stored)
        assertEquals("약 먹기", kept.title)
        assertEquals(LocalDateTime.of(2026, 11, 8, 9, 45), kept.reminder!!.at)
        assertEquals(5L, kept.doneAtMillis)
    }

    @Test fun whatTheEditorChangedItselfWins() {
        val stored = base.copy(reminder = was.copy(at = was.at.plusMinutes(10)))
        val mine = base.copy(reminder = was.copy(at = LocalDateTime.of(2026, 11, 8, 18, 0)))
        assertEquals(LocalDateTime.of(2026, 11, 8, 18, 0), MemoOutside.keep(mine, base, stored).reminder!!.at)
        // Nothing changed outside: the edit as it is.
        assertEquals(mine, MemoOutside.keep(mine, base, base))
        // A repeating memo finished today on the widget.
        val daily = base.copy(repeat = "D")
        assertEquals(setOf(day), MemoOutside.keep(daily.copy(title = "물"), daily, daily.copy(doneDays = setOf(day))).doneDays)
        assertEquals(setOf(day), MemoOutside.seen(daily, daily.copy(doneDays = setOf(day))).doneDays)
    }
}
