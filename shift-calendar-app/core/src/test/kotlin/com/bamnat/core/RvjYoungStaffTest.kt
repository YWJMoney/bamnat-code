package com.bamnat.core

import com.bamnat.core.boss.Payroll
import com.bamnat.core.boss.Workplace
import com.bamnat.core.boss.StaffMember
import com.bamnat.core.work.StaffShift
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

/** 2.0.0 갈래 J [RVJ-13] 검토 PAYC-19·T04-04: 18세 미만 직원은 하루 7시간·한 주 35시간을 넘는 시간이 연장이에요(근로기준법 제69조). */
class RvjYoungStaffTest {
    private val place = Workplace("p", "가나다카페", fivePlus = true, payday = 5)
    private val week = DayOfWeek.entries.take(5).associateWith { StaffShift(LocalTime.of(9, 0), LocalTime.of(18, 0)) }
    private fun staff(birth: LocalDate?) = StaffMember("a", "박마바", base = 10_320, birth = birth, weekly = week)

    @Test fun theMonthsFirstDayDecidesWhoIsUnderEighteen() {
        val month = YearMonth.of(2026, 11)
        assertTrue("2010년생은 18세 미만", Payroll.youngIn(staff(LocalDate.of(2010, 3, 1)), month))
        assertTrue("만 18세 생일이 달 가운데면 그 달 1일에는 18세 미만", Payroll.youngIn(staff(LocalDate.of(2008, 11, 15)), month))
        assertFalse("만 18세 생일이 1일이면 18세", Payroll.youngIn(staff(LocalDate.of(2008, 11, 1)), month))
        assertFalse("생년월일이 없으면 성인으로", Payroll.youngIn(staff(null), month))
        assertFalse("달을 모르면 성인으로", Payroll.youngIn(staff(LocalDate.of(2010, 3, 1)), null))
    }

    @Test fun theLimitsThePayIsCountedWithFollowTheAge() {
        val month = YearMonth.of(2026, 11)
        val young = Payroll.profile(place, staff(LocalDate.of(2010, 3, 1)), month)
        assertEquals(7 * 60, young.dailyLimitMinutes!!)
        assertEquals(35 * 60, young.weeklyLimitMinutes!!)
        val adult = Payroll.profile(place, staff(LocalDate.of(2000, 3, 1)), month)
        assertEquals(8 * 60, adult.dailyLimitMinutes!!)
        assertEquals(40 * 60, adult.weeklyLimitMinutes!!)
    }
}
