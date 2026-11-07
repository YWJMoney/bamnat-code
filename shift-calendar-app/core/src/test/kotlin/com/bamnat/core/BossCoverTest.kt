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
}
