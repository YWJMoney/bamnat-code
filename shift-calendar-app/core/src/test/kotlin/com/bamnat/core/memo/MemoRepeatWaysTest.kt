package com.bamnat.core.memo

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2.0.0 [RVI-73] (검토 U07a-07·I07aa-05·T07a-09·T07a-10) The repeat window's new ways: several weekdays (가게의 납품 요일), 격주 (격주 휴무),
 * N일마다 (현장 점검 주기), 매달 몇째 요일 (정기 휴무) and an end, in the same stored text (the database keeps its version).
 * 2.0.0 [RVI-79][RVI-80][RVI-74] The schedule's first free time, old to-dos and the card's circle.
 */
class MemoRepeatWaysTest {
    private val wed = LocalDate.of(2026, 10, 14)
    private fun on(repeat: String, start: LocalDate, from: LocalDate, to: LocalDate) =
        MemoRepeat.datesIn(Memo(date = start, repeat = repeat), from, to)

    @Test fun severalWeekdaysEveryOtherWeekEveryTenDaysAndTheNthWeekday() {
        val mwf = MemoRepeat.daysOfWeek(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY))!!
        assertEquals("K135", mwf)
        assertEquals(listOf(wed, wed.plusDays(2), wed.plusDays(5)), on(mwf, wed, wed, wed.plusDays(6)))
        // One weekday is the old 매주; all seven is 매일.
        assertEquals("W3", MemoRepeat.daysOfWeek(setOf(DayOfWeek.WEDNESDAY)))
        assertEquals(MemoRepeat.code(MemoRepeat.Kind.DAY, wed), MemoRepeat.daysOfWeek(DayOfWeek.entries.toSet()))

        val other = MemoRepeat.everyWeeks(2, setOf(DayOfWeek.WEDNESDAY))!!
        assertEquals(listOf(wed, wed.plusWeeks(2), wed.plusWeeks(4)), on(other, wed, wed, wed.plusWeeks(5)))

        assertEquals(listOf(wed, wed.plusDays(10), wed.plusDays(20)), on(MemoRepeat.everyDays(10)!!, wed, wed, wed.plusDays(25)))

        // 둘째 목요일: 10/8, 11/12; 마지막 목요일: 10/29, 11/26.
        val second = MemoRepeat.nthWeekday(2, DayOfWeek.THURSDAY)!!
        assertEquals(listOf(LocalDate.of(2026, 10, 8), LocalDate.of(2026, 11, 12)), on(second, LocalDate.of(2026, 10, 8), LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 30)))
        val last = MemoRepeat.nthWeekday(MemoRepeat.LAST, DayOfWeek.THURSDAY)!!
        assertEquals(listOf(LocalDate.of(2026, 10, 29), LocalDate.of(2026, 11, 26)), on(last, LocalDate.of(2026, 10, 29), LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 30)))
    }

    @Test fun aRepeatEndsAfterItsLastDay() {
        val r = MemoRepeat.withUntil("K135", LocalDate.of(2026, 10, 19))
        assertEquals("K135~20261019", r)
        assertEquals(MemoRepeat.Kind.DAYS_OF_WEEK, MemoRepeat.kindOf(r))
        assertEquals(listOf(wed, wed.plusDays(2), wed.plusDays(5)), on(r, wed, wed, wed.plusDays(20)))
        assertEquals("매주 월·수·금 · 10월 19일까지", MemoRepeat.label(r))
        // No end, or no repeat, keeps the text as it was.
        assertEquals("W3", MemoRepeat.withUntil("W3~20261019", null))
        assertEquals("", MemoRepeat.withUntil("", LocalDate.of(2026, 10, 19)))
    }

    @Test fun theLabelsSayEachWay() {
        assertEquals("격주 수요일", MemoRepeat.label("E2W3"))
        assertEquals("3주마다 월·금요일", MemoRepeat.label("E3W15"))
        assertEquals("10일마다", MemoRepeat.label("N10"))
        assertEquals("매달 둘째 목요일", MemoRepeat.label("Q24"))
        assertEquals("매달 마지막 목요일", MemoRepeat.label("Q54"))
        assertEquals("매주 수요일 · 12월 31일까지", MemoRepeat.label("W3~20261231"))
    }

    @Test fun textThatIsNotOneOfTheWaysIsNoRepeat() {
        for (bad in listOf("K11", "K18", "E5W3", "E2W", "N1", "N366", "Q63", "Q20", "~20261231")) {
            assertEquals(bad, MemoRepeat.Kind.NONE, MemoRepeat.kindOf(bad))
        }
        assertNull(MemoRepeat.everyWeeks(5, setOf(DayOfWeek.MONDAY)))
        assertNull(MemoRepeat.everyDays(1))
        assertNull(MemoRepeat.daysOfWeek(emptySet()))
    }

    @Test fun movingTheMemoMovesWhatIsReadFromItsDate() {
        val fri = wed.plusDays(2)
        assertEquals("W5~20261231", MemoRepeat.moved("W3~20261231", fri))
        assertEquals("K135", MemoRepeat.moved("K135", fri))
        assertEquals("N10", MemoRepeat.moved("N10", fri))
        assertEquals("Q34", MemoRepeat.moved("Q24", LocalDate.of(2026, 11, 19)))
        // An end before the new date goes: the repeat goes on from there.
        assertEquals("W3", MemoRepeat.moved("W3~20261019", wed.plusWeeks(1)))
    }

    @Test fun theFirstDayFromADateIsTheRepeatsOwn() {
        val sat = LocalDate.of(2026, 11, 7)
        assertEquals(LocalDate.of(2026, 11, 9), MemoRepeat.firstFrom("K135", sat))
        assertEquals(LocalDate.of(2026, 11, 12), MemoRepeat.firstFrom("Q24", wed))
        assertEquals(sat, MemoRepeat.firstFrom("N10", sat))
        assertNull(MemoRepeat.firstFrom("", sat))
        // A 틀 that repeats with an end starts on its weekday (the end crashed the old read).
        val m = MemoTemplate(title = "납품", repeat = "W3~20261231").start(null, sat)
        assertEquals(LocalDate.of(2026, 11, 11), m.date)
        assertEquals("W3~20261231", m.repeat)
        assertEquals(LocalDate.of(2026, 11, 9), MemoTemplate(title = "납품", repeat = "K135").start(null, sat).date)
    }

    @Test fun theScheduleStartsAfterTheShiftOrTheSleepAfterANight() {
        val day = ShiftMemoTime.Day(wed, "주간", true, LocalTime.of(9, 0), LocalTime.of(18, 10))
        val night = ShiftMemoTime.Day(wed.plusDays(1), "야간", true, LocalTime.of(22, 0), LocalTime.of(7, 0))
        val days = listOf(day, night)
        // Inside 주간: after it, on the half hour.
        assertEquals(wed.atTime(18, 30), ShiftClash.free(wed.atTime(10, 0), days))
        // Inside the night: after the sleep that follows it (07:00 + 7시간).
        assertEquals(wed.plusDays(2).atTime(14, 0), ShiftClash.free(wed.plusDays(2).atTime(3, 0), days))
        // A free time stays as it is.
        assertEquals(wed.atTime(19, 5), ShiftClash.free(wed.atTime(19, 5), days))
        assertEquals(wed.atTime(10, 0), ShiftClash.free(wed.atTime(10, 0), emptyList()))
    }

    @Test fun oldToDosAreCountedNotLost() {
        val today = LocalDate.of(2026, 11, 7)
        fun todo(id: Long, d: LocalDate, done: Long? = null) =
            Memo(id = id, date = d, title = "할 일 $id", reminder = MemoReminder(d.atTime(9, 0), ring = false, notify = true), doneAtMillis = done)
        val memos = listOf(
            todo(1, LocalDate.of(2026, 10, 20)),
            todo(2, LocalDate.of(2026, 10, 25)),
            todo(3, today.minusDays(2)), // still on the list (늦은 일)
            todo(4, today.minusDays(TaskList.OLD_DAYS + 1)), // let go of
            todo(5, LocalDate.of(2026, 10, 22), done = 1),
            Memo(id = 6, date = LocalDate.of(2026, 10, 21), title = "회식"), // a diary line, not a to-do
            todo(7, LocalDate.of(2026, 10, 23)).copy(repeat = "W5"),
        )
        val old = TaskList.old(memos, today)
        assertEquals(listOf(1L, 2L), old.map { it.id })
        assertEquals("오래된 할 일 2개 · 10/20부터", TaskList.oldLine(old))
        assertNull(TaskList.oldLine(emptyList()))
    }

    @Test fun onlyAToDoGetsTheCardsCircle() {
        assertFalse(TaskList.finishable(Memo(title = "지난달 회식 장소", body = "OO갈비")))
        assertTrue(TaskList.finishable(Memo(title = "서류", date = wed)))
        assertTrue(TaskList.finishable(Memo(title = "장보기", pinned = true)))
        assertTrue(TaskList.finishable(Memo(title = "준비물", isChecklist = true, checklist = listOf(ChecklistItem("출입증")))))
        // Ticking every line of a finished memo off again unfinishes it.
        val done = Memo(title = "준비물", date = wed, isChecklist = true, checklist = listOf(ChecklistItem("출입증", true))).withDone(true, null, 1)
        assertTrue(done.isDone)
        assertFalse(done.withItemChecked(0, false, null, 2).isDone)
    }

    @Test fun theLastSameShiftsUntickedLinesCarryOver() {
        val last = listOf(
            Memo(title = "인수인계", isChecklist = true, checklist = listOf(ChecklistItem("3번 라인 센서", false), ChecklistItem("출입증", true))),
            Memo(title = "메모", body = "☐ 2번 설비 소음 확인\n☑ 정비팀 전달\n그냥 글"),
        )
        val now = Memo(title = "오늘 야간", isChecklist = true, checklist = listOf(ChecklistItem("2번 설비 소음 확인")))
        // What this memo already has is not brought twice.
        assertEquals(listOf("3번 라인 센서"), TaskList.carried(last, now))
        assertEquals(listOf("2번 설비 소음 확인", "3번 라인 센서"), TaskList.withCarried(now, listOf("3번 라인 센서")).checklist.map { it.text })
        // A note gets them as lines to tick.
        val note = TaskList.withCarried(Memo(title = "야간", body = "특이사항 없음"), listOf("3번 라인 센서"))
        assertEquals("특이사항 없음\n☐ 3번 라인 센서", note.body)
        assertEquals(0 to 1, TaskList.boxes(note))
    }
}
