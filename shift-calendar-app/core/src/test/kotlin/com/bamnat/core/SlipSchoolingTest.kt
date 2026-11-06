package com.bamnat.core

import com.bamnat.core.model.DayEntry
import com.bamnat.core.model.ExtraEntry
import com.bamnat.core.model.ExtraKind
import com.bamnat.core.model.ExtraKinds
import com.bamnat.core.model.ExtraTiming
import com.bamnat.core.model.ShiftType
import com.bamnat.core.model.WorkPattern
import com.bamnat.core.pay.ExtraRule
import com.bamnat.core.pay.PayCalculator
import com.bamnat.core.pay.PayslipRead
import com.bamnat.core.pay.SlipLine
import com.bamnat.core.pay.SlipRole
import com.bamnat.core.pay.SlipRules
import com.bamnat.core.pay.WorkForm
import com.bamnat.core.pay.WorkForms
import com.bamnat.core.schedule.ScheduleResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

/**
 * [READ-41] 1.38 교육 시수 (PR #245): a payslip's 안전교육/LMS line and the month's 교육 on the calendar offer the 시수 the company
 * pays 교육 on (사용자: 다른 근무 209·180시수, 교육 243시수), as a hint only.
 */
class SlipSchoolingTest {
    private val oct = YearMonth.of(2026, 10)
    private val day = ShiftType(11, "주간", "주", 0xFF1E88E5, LocalTime.of(8, 0), LocalTime.of(20, 0), 60, LocalTime.of(12, 0))
    private val off = ShiftType(19, "휴무", "휴", 0xFF9E9E9E, null, null, isOff = true)
    private val school = ExtraKind(50, "교육", "교", 0xFF7048E8, ExtraTiming.AfterShift(480))
    private fun resolver(withSchool: Boolean) = ScheduleResolver(listOf(day, off),
        WorkPattern.Rotation(LocalDate.of(2026, 9, 1), listOf(11L, 11L, 19L, 19L)),
        if (withSchool) listOf(7, 21).map { DayEntry(LocalDate.of(2026, 10, it), extras = listOf(ExtraEntry(50, LocalTime.of(9, 0), LocalTime.of(13, 0)))) } else emptyList(),
        ExtraKinds(ExtraKinds.DEFAULTS + school))
    private val profile = WorkForms.apply(WorkForm.FOUR_TEAM, 2_430_000, LocalDate.of(2026, 10, 1))
    private fun days(r: ScheduleResolver) = (1..31).map { r.resolve(oct.atDay(it)) }

    @Test fun aSchoolingLineOffersTheHoursItWasPaidOn() {
        val ordinary = PayCalculator.ordinaryForHours(profile, oct)!!
        val amount = Math.round(ordinary / 243 * 8)
        val read = PayslipRead(listOf(SlipLine("안전교육/LMS", SlipRole.ALLOWANCE, amount)), oct)
        val hints = SlipRules.schoolingHours(profile, oct, days(resolver(true)), read)
        assertEquals(hints.toString(), 1, hints.size)
        assertTrue(hints[0], "243시간" in hints[0] && "급여와 가산" in hints[0])
        // Already set: nothing to say.
        val set = profile.copy(rules = profile.rules.copy(extra = profile.rules.extra + (50L to ExtraRule(monthlyHours = 243))))
        assertTrue(SlipRules.schoolingHours(set, oct, days(resolver(true)), read).isEmpty())
    }

    @Test fun aSchoolingLineWithNoSchoolingOnTheCalendarSaysToPutItIn() {
        val read = PayslipRead(listOf(SlipLine("교육수당", SlipRole.ALLOWANCE, 80_000)), oct)
        val hints = SlipRules.schoolingHours(profile, oct, days(resolver(false)), read)
        assertTrue(hints.toString(), hints.single().contains("달력에 추가 근무 ‘교육’"))
    }

    @Test fun anAmountNoUsualHoursExplainSaysNothing() {
        val read = PayslipRead(listOf(SlipLine("안전교육", SlipRole.ALLOWANCE, 12_345)), oct)
        assertTrue(SlipRules.schoolingHours(profile, oct, days(resolver(true)), read).isEmpty())
    }
}
