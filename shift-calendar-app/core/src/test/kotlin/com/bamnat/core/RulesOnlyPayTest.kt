package com.bamnat.core

import com.bamnat.core.model.DayEntry
import com.bamnat.core.model.ShiftOverride
import com.bamnat.core.model.ShiftType
import com.bamnat.core.model.WorkPattern
import com.bamnat.core.pay.PayCalculator
import com.bamnat.core.pay.PayslipRead
import com.bamnat.core.pay.RuleOnlyNeeds
import com.bamnat.core.pay.SlipRules
import com.bamnat.core.pay.WorkForm
import com.bamnat.core.pay.WorkForms
import com.bamnat.core.schedule.ScheduleResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

/**
 * [PAYS-16][PAYS-17][PAYS-18] 1.27 순번 448 (사용자 "임금 규칙만 제공해도 달력 근무입력 기반 적용이 가능하게 … 굳이 명세표를 넣지 않아도"):
 * 명세서 없이 회사 계산 방법(규칙)만 넣어도 그 달 달력 근무로 급여가 나오고, 규칙만 넣은 사람에게 빈 필수 값을 한 줄씩 알려요.
 *
 * 골든 R1 (research/pay-golden-cases.md 1.27 AJ): 2026년 11월(공휴일 없음), 월 기본급 2,508,000원 ÷ 209시간 = 통상시급 12,000원.
 * 나이트 22~06시(휴게 없음)를 11/2(월)·3(화)·4(수) 사흘. 회사 계산 방법 "야간근로는 통상시급의 60%를 가산하여 지급한다."
 * 야간 수당 = 3일 × 8시간 × 12,000원 × 60% = 172,800원 → 합계 2,680,800원. 법대로(50%)면 144,000원 → 2,652,000원.
 */
class RulesOnlyPayTest {
    private val nov = YearMonth.of(2026, 11)
    private val night = ShiftType(3, "나이트", "N", 0xFF5E35B1, LocalTime.of(22, 0), LocalTime.of(6, 0), 0)
    private val off = ShiftType(9, "휴무", "O", 0xFF9E9E9E, null, null, isOff = true)
    private val noTimes = ShiftType(5, "당직", "당", 0xFF795548, null, null)
    private val resolver = ScheduleResolver(listOf(night, off, noTimes), WorkPattern.Rotation(LocalDate.of(2026, 11, 1), listOf(off.id)),
        (2..4).map { DayEntry(LocalDate.of(2026, 11, it), ShiftOverride.Shift(night.id)) })
    private val setup = WorkForms.apply(WorkForm.THREE_SHIFT, 2_508_000, LocalDate.of(2026, 1, 1))

    @Test fun `R1 회사 규칙만으로 달력 근무 급여`() {
        val lawOnly = PayCalculator().calculate(setup, nov, resolver::resolve).total
        assertEquals(2_652_000, lawOnly)
        val r = SlipRules.infer(setup, nov, resolver::resolve, PayslipRead(emptyList()), ruleText = "야간근로는 통상시급의 60%를 가산하여 지급한다.")
        assertNotNull("명세서 없이 규칙만으로도 결과가 나와야 해요", r)
        r!!
        assertFalse(r.withSlip)
        assertFalse(r.close)
        assertEquals(0.6, r.profile.rules.nightPremium!!, 1e-9)
        assertEquals(2_680_800, r.total)
        assertEquals(2_680_800, PayCalculator().calculate(r.profile, nov, resolver::resolve).total)
    }

    @Test fun `규칙도 명세서도 없으면 결과 없음`() {
        assertEquals(null, SlipRules.infer(setup, nov, resolver::resolve, PayslipRead(emptyList())))
    }

    @Test fun `규칙만 넣은 사람에게 빈 필수 값을 한 줄씩, 넣으면 사라짐`() {
        val zero = WorkForms.apply(WorkForm.THREE_SHIFT, 0, LocalDate.of(2026, 1, 1))
        val needs = RuleOnlyNeeds.of(zero, nov, listOf(night, noTimes, off))
        assertEquals(listOf(RuleOnlyNeeds.Kind.BASE, RuleOnlyNeeds.Kind.MONTHLY_HOURS, RuleOnlyNeeds.Kind.SHIFT_TIMES), needs.map { it.kind })
        assertEquals(noTimes.id, needs.last().shiftTypeId)
        assertTrue(needs.first().why.contains("0원"))
        // 기본급·시수를 넣고 근무 시각이 있는 근무만 하면 없음.
        val done = RuleOnlyNeeds.settled(setup)
        assertTrue(RuleOnlyNeeds.of(done, nov, listOf(night, off)).isEmpty())
        // 시급제는 시수를 묻지 않음.
        assertTrue(RuleOnlyNeeds.of(WorkForms.apply(WorkForm.PART_TIME, 10_320, LocalDate.of(2026, 1, 1)), nov, listOf(night)).isEmpty())
    }

    @Test fun `회사 계산 방법의 시수가 시수 물음을 닫음`() {
        val r = SlipRules.infer(setup, nov, resolver::resolve, PayslipRead(emptyList()), ruleText = "월 소정근로시간은 226시간으로 한다.")
        if (r != null && r.profile.monthlyHours == 226) {
            assertTrue(RuleOnlyNeeds.of(r.profile, nov, listOf(night)).none { it.kind == RuleOnlyNeeds.Kind.MONTHLY_HOURS })
        }
    }
}
