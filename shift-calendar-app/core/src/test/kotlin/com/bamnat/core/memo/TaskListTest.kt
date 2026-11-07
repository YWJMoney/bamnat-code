package com.bamnat.core.memo

import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 1.39 [TASK-07] 할 일 위젯의 목록. */
class TaskListTest {
    private val today = LocalDate.of(2026, 11, 7)
    private fun memo(id: Long, title: String, date: LocalDate? = today, repeat: String = "", reminderAt: LocalDateTime? = null,
        checklist: List<ChecklistItem> = emptyList(), pinned: Boolean = false, doneAt: Long? = null, doneDays: Set<LocalDate> = emptySet()) =
        Memo(id = id, date = date, title = title, repeat = repeat, reminder = reminderAt?.let { MemoReminder(it, ring = false, notify = true) },
            checklist = checklist, isChecklist = checklist.isNotEmpty(), pinned = pinned, createdAtMillis = id, doneAtMillis = doneAt, doneDays = doneDays)

    @Test fun lateComesFirstThenTodayByTimeThenPinnedNotes() {
        val rows = TaskList.of(listOf(
            memo(1, "장보기", date = null, pinned = true),
            memo(2, "치과", reminderAt = today.atTime(15, 0)),
            memo(3, "택배 받기", reminderAt = today.atTime(9, 0)),
            memo(4, "서류 내기", date = today.minusDays(2), reminderAt = today.minusDays(2).atTime(10, 0)),
            memo(5, "약 먹기", date = today.minusDays(30), repeat = "D"),
        ), today)
        assertEquals(listOf("서류 내기", "택배 받기", "치과", "약 먹기", "장보기"), rows.map { it.text })
        assertEquals(listOf(TaskList.Kind.LATE, TaskList.Kind.TODAY, TaskList.Kind.TODAY, TaskList.Kind.TODAY, TaskList.Kind.PINNED), rows.map { it.kind })
        assertEquals("a repeating memo is finished on today", today, rows[3].day)
    }

    @Test fun aPastPlainNoteIsADiaryLineNotALateTask() {
        val rows = TaskList.of(listOf(
            memo(1, "회식", date = today.minusDays(1)),
            memo(2, "이삿짐", date = today.minusDays(1), checklist = listOf(ChecklistItem("상자", true), ChecklistItem("테이프"))),
            memo(3, "끝난 체크", date = today.minusDays(1), checklist = listOf(ChecklistItem("상자", true))),
            memo(4, "오래된 일", date = today.minusDays(TaskList.LATE_DAYS + 1), reminderAt = today.minusDays(9).atTime(9, 0)),
            memo(5, "끝낸 일", date = today.minusDays(1), reminderAt = today.minusDays(1).atTime(9, 0), doneAt = 1),
        ), today)
        assertEquals(listOf("이삿짐"), rows.map { it.text })
        assertEquals("1/2", rows.single().count)
    }

    @Test fun finishedTodayGoesLastAndTheHeadSaysWhatIsLeft() {
        val rows = TaskList.of(listOf(
            memo(1, "빨래", doneAt = 5),
            memo(2, "약 먹기", date = today.minusDays(3), repeat = "D", doneDays = setOf(today.minusDays(1))),
        ), today)
        assertEquals(listOf("약 먹기", "빨래"), rows.map { it.text })
        assertEquals("yesterday's 약 먹기 is still to do today", listOf(false, true), rows.map { it.done })
        assertEquals("할 일 1개 남았어요", TaskList.headline(rows))
        assertEquals("오늘 할 일을 다 끝냈어요", TaskList.headline(rows.map { it.copy(done = true) }))
        assertEquals("할 일", TaskList.headline(emptyList()))
    }

    @Test fun trashedBlankAndSkippedMemosStayOff() {
        val rows = TaskList.of(listOf(
            memo(1, "지운 것").copy(deletedAtMillis = 1),
            memo(2, ""),
            memo(3, "오늘만 뺌", date = today.minusDays(1), repeat = "D").copy(skips = setOf(today)),
            memo(4, "끝낸 고정", date = null, pinned = true, doneAt = 1),
        ), today)
        assertTrue(rows.isEmpty())
    }

    /** PC 2afb3a76: three things the 할 일 위젯 got wrong on the emulator. */
    @Test fun aMemoIsDueOnItsPlansDayAndBoxesInANoteCount() {
        val rows = TaskList.of(listOf(
            // 날짜 11/6, 일정 11/7 20:00: today's, not late.
            memo(1, "서류 내기", date = today.minusDays(1), reminderAt = today.atTime(20, 0)),
            // ☐ lines written in a note with the editor's '☐ 체크'.
            memo(2, "이삿짐", date = today.minusDays(1)).copy(body = "☐ 상자 사기\n☑ 테이프"),
            memo(3, "장보기", date = null, pinned = true),
            memo(4, "우유 사기", doneAt = 7),
            // A box with nothing after it is not a line to tick.
            memo(5, "빈 칸", date = today.minusDays(1)).copy(body = "☐ "),
        ), today)
        assertEquals(listOf("이삿짐", "서류 내기", "장보기", "우유 사기"), rows.map { it.text })
        assertEquals(listOf(TaskList.Kind.LATE, TaskList.Kind.TODAY, TaskList.Kind.PINNED, TaskList.Kind.TODAY), rows.map { it.kind })
        assertEquals("1/2", rows[0].count)
        assertEquals(today, rows[1].day)
    }
}
