package com.bamnat.core

import com.bamnat.core.boss.BossCover
import com.bamnat.core.boss.StaffMember
import com.bamnat.core.boss.StaffWork
import com.bamnat.core.boss.WorkKind
import com.bamnat.core.boss.Workplace
import com.bamnat.core.work.StaffShift
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/** 1.38 [P3-X-01] 사장님 대타 찾기: who could come, what each costs by the ledger itself. */
class BossCoverTest {
    private val sat = LocalDate.of(2026, 11, 7) // 토요일
    private val shift = StaffShift(LocalTime.of(10, 0), LocalTime.of(18, 0))
    private val place = Workplace("p", "우리 카페", fivePlus = false, minuteUnit = 1)
    private val hired = LocalDate.of(2026, 1, 1)
    private val absent = StaffMember("a", "가", base = 10_320, hiredOn = hired, weekly = mapOf(DayOfWeek.SATURDAY to shift))
    // 평일 이틀 5시간씩(휴게 빼고 4.5시간) = 주 9시간.
    private val small = StaffMember("b", "나", base = 10_320, hiredOn = hired,
        weekly = mapOf(DayOfWeek.MONDAY to StaffShift(LocalTime.of(10, 0), LocalTime.of(15, 0)), DayOfWeek.TUESDAY to StaffShift(LocalTime.of(10, 0), LocalTime.of(15, 0))))
    // 평일 닷새 8시간 = 이미 주휴 받는 사람: 더 드는 건 그날 시간만.
    private val full = StaffMember("c", "다", base = 10_320, hiredOn = hired,
        weekly = DayOfWeek.entries.take(5).associateWith { StaffShift(LocalTime.of(9, 0), LocalTime.of(18, 0)) })
    private val busy = StaffMember("d", "라", base = 10_320, hiredOn = hired, weekly = mapOf(DayOfWeek.SATURDAY to shift))

    @Test fun ranksByWhatItCostsAndSaysWhy() {
        val list = BossCover.candidates(place, listOf(absent, small, full, busy), listOf(StaffWork("a", sat, WorkKind.ABSENT)), "a", sat)
        assertEquals("그날 일하는 라는 빼요, 값이 같으면 그 주 덜 일한 사람 먼저", listOf("나", "다"), list.map { it.staff.name })
        val c = list.first { it.staff.id == "c" }
        val b = list.first { it.staff.id == "b" }
        assertTrue(c.ok && b.ok)
        // 8시간 − 휴게 30분 = 7.5시간 × 10,320원. 대근은 소정근로가 아니라 주휴수당을 새로 만들지 않아요(제18조 제3항).
        assertEquals(77_400L, b.extra)
        assertEquals(b.extra, c.extra)
    }

    @Test fun overFiftyTwoHoursIsNotOkAtFiveOrMore() {
        val long = full.copy(weekly = DayOfWeek.entries.take(5).associateWith { StaffShift(LocalTime.of(8, 0), LocalTime.of(19, 0)) })
        val list = BossCover.candidates(place.copy(fivePlus = true), listOf(absent, long), emptyList(), "a", sat)
        assertFalse(list.single().ok)
        assertTrue(list.single().why.single().contains("52시간"))
        assertTrue("5인 미만은 52시간 제한이 없어요", BossCover.candidates(place, listOf(absent, long), emptyList(), "a", sat).single().ok)
    }

    @Test fun theCoverRowNeverPaysTwice() {
        assertEquals(WorkKind.COVER, BossCover.cover(listOf(absent, small), emptyList(), "a", sat, "b")?.kind)
        val offThatDay = busy.copy(id = "e")
        assertEquals("평소 근무 요일이면 그날 시간 바꿈으로", WorkKind.NORMAL, BossCover.cover(listOf(absent, offThatDay), emptyList(), "a", sat, "e")?.kind)
        assertTrue("결근·연차인 사람은 묻지 않아요", BossCover.candidates(place, listOf(absent, small), listOf(StaffWork("b", sat, WorkKind.LEAVE)), "a", sat).isEmpty())
    }

    /**
     * 2.0.0 [RVJ-85] 검토 BOSS-64: a day whose hours were changed (15~23) or a 대근 on a day off, then turned to 결근, asks the 대타
     * for that day's hours: not the usual week's 10~18, and not '평소 근무 시간이 없어요'. 지각 hours are when they came, so the
     * usual week holds that day; a row that is the usual week keeps no hours.
     */
    @Test fun aDayWithItsOwnHoursKeepsThemWhenItTurnsToAbsent() {
        val late = StaffShift(LocalTime.of(15, 0), LocalTime.of(23, 0))
        val changed = StaffWork("a", sat, WorkKind.NORMAL, late.start, late.end)
        val absentRow = BossCover.keep(absent, sat, WorkKind.ABSENT, changed)
        assertEquals(WorkKind.ABSENT, absentRow.kind)
        assertEquals(late, BossCover.gapShift(listOf(absent, small), listOf(absentRow), "a", sat))
        assertEquals(late.start, BossCover.cover(listOf(absent, small), listOf(absentRow), "a", sat, "b")?.start)
        assertEquals("the ledger still reads a 결근", BossCover.candidates(place, listOf(absent, small), listOf(absentRow), "a", sat).single().staff.id, "b")

        // A 대근 on a Sunday (no usual hours) turned to 결근: still that day's hours.
        val sun = sat.plusDays(1)
        val cover = StaffWork("a", sun, WorkKind.COVER, shift.start, shift.end)
        assertEquals(shift, BossCover.gapShift(listOf(absent), listOf(BossCover.keep(absent, sun, WorkKind.ABSENT, cover)), "a", sun))
        assertEquals("before: no row hours, no usual week", null, BossCover.gapShift(listOf(absent), listOf(StaffWork("a", sun, WorkKind.ABSENT)), "a", sun))

        // 지각 (came at 11) turned to 결근: the usual 10~18, and nothing kept.
        val came = StaffWork("a", sat, WorkKind.LATE, LocalTime.of(11, 0), shift.end)
        val fromLate = BossCover.keep(absent, sat, WorkKind.ABSENT, came)
        assertEquals(null, fromLate.start)
        assertEquals(shift, BossCover.gapShift(listOf(absent), listOf(fromLate), "a", sat))
        assertEquals("the usual week keeps nothing", null, BossCover.keep(absent, sat, WorkKind.LEAVE, StaffWork("a", sat, WorkKind.NORMAL, shift.start, shift.end)).start)
        assertEquals(null, BossCover.keep(absent, sat, WorkKind.OFF, null).start)
    }

    /** 2.0.0 [RVJ-101] 검토 U05c-08: the hours the 사장님 sets before asking win over the day's own, for who could come and for the 대근 row. */
    @Test fun theHoursSetBeforeAskingAreTheOnesUsed() {
        val late = StaffShift(LocalTime.of(14, 0), LocalTime.of(22, 0))
        assertEquals("the day's own, without any set", shift.start, BossCover.cover(listOf(absent, small), emptyList(), "a", sat, "b")?.start)
        val row = BossCover.cover(listOf(absent, small), emptyList(), "a", sat, "b", late)!!
        assertEquals(late.start, row.start)
        assertEquals(late.end, row.end)
        // No usual week that day (the day has no hours): hours set by hand still find who could come, and none set finds nobody.
        val noWeek = absent.copy(weekly = emptyMap())
        assertTrue(BossCover.candidates(place, listOf(noWeek, small), emptyList(), "a", sat).isEmpty())
        assertEquals("b", BossCover.candidates(place, listOf(noWeek, small), emptyList(), "a", sat, late).single().staff.id)
        assertEquals(late.start, BossCover.cover(listOf(noWeek, small), emptyList(), "a", sat, "b", late)?.start)
    }
}
