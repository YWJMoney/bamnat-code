package com.bamnat.core

import com.bamnat.core.boss.PayrollLedger
import com.bamnat.core.boss.StaffMember
import com.bamnat.core.boss.Workplace
import com.bamnat.core.work.StaffShift
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 1.39 G2 PC 4bd5d745: 주 60시간이 빨강이 안 되고, 지난달 '22일 · 24시간'으로 보이던 것. */
class LedgerWeekHoursTest {
    private val weekdays = listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
    private fun staff(start: Int, end: Int) = StaffMember("m", "민수", base = 10_320, weekly = weekdays.associateWith { StaffShift(LocalTime.of(start, 0), LocalTime.of(end, 0)) })

    @Test fun aSixtyHourWeekIsRedAtFivePlus() {
        val l = PayrollLedger.line(Workplace("p", "식당", fivePlus = true), staff(9, 22), YearMonth.of(2026, 11), emptyList())
        assertTrue(l.weekMinutes.all { it == 60 * 60 })
        assertTrue(l.warnings.any { it.key == "week52" })
    }

    @Test fun fortyHoursIsNotRedAndUnderFiveIsNotTold() {
        assertFalse(PayrollLedger.line(Workplace("p", "식당", fivePlus = true), staff(9, 18), YearMonth.of(2026, 11), emptyList()).warnings.any { it.key == "week52" })
        assertFalse(PayrollLedger.line(Workplace("p", "식당", fivePlus = false), staff(9, 22), YearMonth.of(2026, 11), emptyList()).warnings.any { it.key == "week52" })
    }

    @Test fun hoursAreEveryHourWorkedEveryMonth() {
        val place = Workplace("p", "식당", fivePlus = true)
        // 12시간 × 일한 날: 연장도 일한 시간이고, 공휴일이 낀 10월도 같은 셈이에요.
        assertEquals(21 * 12 * 60, PayrollLedger.line(place, staff(9, 22), YearMonth.of(2026, 11), emptyList()).workMinutes)
        val oct = PayrollLedger.line(place, staff(9, 22), YearMonth.of(2026, 10), emptyList())
        assertEquals(oct.workDays * 12 * 60, oct.workMinutes)
        val oct8 = PayrollLedger.line(place, staff(9, 18), YearMonth.of(2026, 10), emptyList())
        assertEquals(oct8.workDays * 8 * 60, oct8.workMinutes)
    }
}
