package com.bamnat.core

import com.bamnat.core.boss.Insurance
import com.bamnat.core.boss.InsuranceRules
import com.bamnat.core.boss.StaffMember
import com.bamnat.core.boss.StaffTax
import com.bamnat.core.boss.WageForm
import com.bamnat.core.work.StaffShift
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalTime

/** 1.39 G2 (PC f7f61818 '월급 직원을 넣어도 4대보험이 비어 사장님 4대보험 0원'): a new staff member's 4대보험 follow the law until ticked. */
class InsuranceDefaultTest {
    private fun week(days: Int, from: Int, to: Int) =
        DayOfWeek.entries.take(days).associateWith { StaffShift(LocalTime.of(from, 0), LocalTime.of(to, 0)) }

    @Test fun `monthly pay is in all four, the tax way kept`() {
        val s = StaffMember("a", "민수", form = WageForm.MONTH, base = 2_500_000, insurance = Insurance(tax = StaffTax.NONE))
        assertEquals(Insurance.ALL.copy(tax = StaffTax.NONE), InsuranceRules.defaultFor(s, legalBreaks = true))
        assertTrue(InsuranceRules.unticked(s, true))
    }

    @Test fun `an hourly week of 15 hours or more is in, a shorter one is not`() {
        // 3일 × 9시간(휴게 1시간 빼고 8시간) = 24시간
        assertTrue(InsuranceRules.defaultFor(StaffMember("b", "유나", weekly = week(3, 9, 18)), true).any)
        // 2일 × 4시간 = 8시간
        assertFalse(InsuranceRules.defaultFor(StaffMember("c", "지호", weekly = week(2, 18, 22)), true).any)
        assertFalse(InsuranceRules.unticked(StaffMember("c", "지호", weekly = week(2, 18, 22)), true))
    }

    @Test fun `a 3_3 worker or one already ticked is not told`() {
        val biz = StaffMember("d", "프리", form = WageForm.MONTH, insurance = Insurance(tax = StaffTax.BIZ))
        assertFalse(InsuranceRules.unticked(biz, true))
        assertFalse(InsuranceRules.unticked(StaffMember("e", "민수", form = WageForm.MONTH, insurance = Insurance(health = true)), true))
    }
}
