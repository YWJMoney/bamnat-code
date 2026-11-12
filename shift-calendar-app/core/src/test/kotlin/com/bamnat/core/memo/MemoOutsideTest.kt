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

    /** 2.0.0 [RVI-32] (검토 MEMO-12) On an open fold the list beside the editor changed 고정·색·칸, then a letter was typed. */
    @Test fun theListsPinColourAndCellsStayOverALetterTyped() {
        val open = base.copy(body = "| 품목 | 수량 |\n| 장갑 | 2 |", isChecklist = false)
        val stored = open.copy(pinned = true, colorIndex = 3, body = "| 품목 | 수량 |\n| 장갑 | 5 |")
        val kept = MemoOutside.keep(open.copy(title = "준비물"), open, stored)
        assertEquals("준비물", kept.title)
        assertEquals(true, kept.pinned)
        assertEquals(3, kept.colorIndex)
        assertEquals(stored.body, kept.body)
        // The editor's own body wins over a cell changed outside.
        val mine = open.copy(body = "새 글")
        assertEquals("새 글", MemoOutside.keep(mine, open, stored).body)
    }

    @Test fun aChecklistIsTakenItemByItem() {
        val list = base.copy(isChecklist = true, checklist = listOf(ChecklistItem("장갑"), ChecklistItem("물"), ChecklistItem("열쇠")))
        // The card checked 물; the editor renamed 열쇠.
        val stored = list.copy(checklist = list.checklist.mapIndexed { i, c -> if (i == 1) c.copy(checked = true) else c })
        val mine = list.copy(checklist = list.checklist.mapIndexed { i, c -> if (i == 2) c.copy(text = "차 열쇠") else c })
        val kept = MemoOutside.keep(mine, list, stored).checklist
        assertEquals(listOf(ChecklistItem("장갑"), ChecklistItem("물", true), ChecklistItem("차 열쇠")), kept)
    }

    @Test fun aMemoTrashedBesideTheEditorIsNotBroughtBack() {
        val stored = base.copy(deletedAtMillis = 99L)
        val kept = MemoOutside.keep(base.copy(title = "약 먹기"), base, stored)
        assertEquals(99L, kept.deletedAtMillis)
        assertEquals(true, MemoOutside.trashedOutside(base, stored))
        assertEquals(false, MemoOutside.trashedOutside(stored, stored))
    }
}
