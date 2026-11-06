package com.bamnat.core

import com.bamnat.core.boss.LedgerLine
import com.bamnat.core.boss.PayrollLedger
import com.bamnat.core.boss.StaffLeave
import com.bamnat.core.boss.StaffMember
import com.bamnat.core.boss.WageForm
import com.bamnat.core.boss.Workplace
import com.bamnat.core.work.StaffShift
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

/** 1.38 퇴사·휴직 (사용자 2026-11-06 "퇴사자 휴직자 처리"): 쉬는 날은 임금 0, 월급은 일한 날만큼, 재직은 이어져요. */
class StaffLeaveTest {
    private val place = Workplace("w", "가게", fivePlus = false)
    private val month = YearMonth.of(2026, 11)
    private val week = DayOfWeek.entries.filter { it.value <= 5 }.associateWith { StaffShift(LocalTime.of(9, 0), LocalTime.of(13, 0)) }

    @Test fun hourlyPaysNothingOnLeaveDays() {
        val s = StaffMember("s", "가", base = 10_320, hiredOn = LocalDate.of(2026, 1, 1), weekly = week)
        val all = PayrollLedger.line(place, s, month, emptyList())
        val rested = PayrollLedger.line(place, s.copy(leaves = listOf(StaffLeave(month.atDay(1), month.atDay(15)))), month, emptyList())
        assertTrue(rested.workDays < all.workDays)
        assertTrue(rested.pay!!.total < all.pay!!.total)
        val whole = PayrollLedger.line(place, s.copy(leaves = listOf(StaffLeave(LocalDate.of(2026, 10, 1)))), month, emptyList())
        assertEquals(0, whole.workDays)
        assertEquals(0L, whole.pay?.total ?: 0L)
    }

    @Test fun monthlyIsCutForLeaveAndForLeaving() {
        val s = StaffMember("s", "가", form = WageForm.MONTH, base = 3_000_000, hiredOn = LocalDate.of(2026, 1, 1), weekly = week)
        val base = { l: LedgerLine -> l.pay!!.lines.first { it.kind == com.bamnat.core.pay.LineKind.BASE }.amount }
        assertEquals(3_000_000L, base(PayrollLedger.line(place, s, month, emptyList())))
        assertEquals("15일 휴직이면 반", 1_500_000L, base(PayrollLedger.line(place, s.copy(leaves = listOf(StaffLeave(month.atDay(16), month.atDay(30)))), month, emptyList())))
        assertEquals("15일 퇴사면 반", 1_500_000L, base(PayrollLedger.line(place, s.copy(leftOn = month.atDay(15)), month, emptyList())))
    }

    @Test fun workingCountsOnlyThoseNotLeftOrResting() {
        val s = StaffMember("s", "가", hiredOn = LocalDate.of(2026, 1, 1))
        assertTrue(s.workingIn(month))
        assertFalse(s.copy(leftOn = LocalDate.of(2026, 10, 31)).workingIn(month))
        assertFalse(s.copy(leaves = listOf(StaffLeave(LocalDate.of(2026, 10, 1)))).workingIn(month))
        assertTrue(s.copy(leaves = listOf(StaffLeave(LocalDate.of(2026, 10, 1), month.atDay(10)))).workingIn(month))
    }
}
