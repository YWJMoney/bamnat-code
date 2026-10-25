package com.bamnat.core

import com.bamnat.core.model.ShiftType
import com.bamnat.core.model.WorkPattern
import com.bamnat.core.pay.Cadence
import com.bamnat.core.pay.CompanyRules
import com.bamnat.core.pay.DatedValue
import com.bamnat.core.pay.HolidayWork
import com.bamnat.core.pay.Occupation
import com.bamnat.core.pay.PayItem
import com.bamnat.core.pay.PayProfile
import com.bamnat.core.pay.PayStory
import com.bamnat.core.pay.Unknowns
import com.bamnat.core.pay.ValueTimeline
import com.bamnat.core.pay.WageType
import com.bamnat.core.pay.WorkForm
import com.bamnat.core.pay.WorkForms
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

/** [PAYS-12] 급여 간편 설정: 근무 형태 추측, 형태로 만든 급여, 기존 값 보존(D70), "이렇게 계산해요" 문장. */
class WorkFormTest {
    private fun t(h: Int) = LocalTime.of(h, 0)
    private fun type(id: Long, name: String, label: String, s: Int?, e: Int?) =
        ShiftType(id, name, label, 0xFF000000, s?.let(::t), e?.let(::t), isOff = s == null)
    private val off = type(9, "휴무", "O", null, null)
    private val oct = YearMonth.of(2026, 10)
    private val since = LocalDate.of(2026, 10, 1)

    @Test
    fun `the calendar's shifts point to the form`() {
        assertEquals(WorkForm.NURSE, WorkForms.guess(listOf(type(1, "데이", "D", 7, 15), type(2, "이브닝", "E", 15, 23), type(3, "나이트", "N", 23, 7), off)))
        assertEquals(WorkForm.THREE_SHIFT, WorkForms.guess(listOf(type(1, "모닝", "M", 6, 14), type(2, "오후", "A", 14, 22), type(3, "야간", "Y", 22, 6), off)))
        val day12 = type(1, "주간", "주", 8, 20); val night12 = type(2, "야간", "야", 20, 8)
        assertEquals(WorkForm.TWO_SHIFT, WorkForms.guess(listOf(day12, night12, off)))
        val four = WorkPattern.Rotation(since, listOf(1L, 1L, 2L, 2L, 9L, 9L, 9L, 9L))
        assertEquals(WorkForm.FOUR_TEAM, WorkForms.guess(listOf(day12, night12, off), four))
        // An 오후 kind put in once by hand is not in the order: still 2교대.
        val fiveDays = WorkPattern.Rotation(since, listOf(1L, 1L, 2L, 2L, 9L))
        assertEquals(WorkForm.TWO_SHIFT, WorkForms.guess(listOf(day12, night12, type(3, "오후", "오", 13, 22), off), fiveDays))
        assertEquals(WorkForm.DAY_ON_DAY_OFF, WorkForms.guess(listOf(type(1, "당번", "당", 9, 9), off)))
        assertEquals(WorkForm.ON_CALL, WorkForms.guess(listOf(type(1, "근무", "근", 9, 18), type(2, "당직", "당", 18, 9), off)))
        assertEquals(WorkForm.OFFICE, WorkForms.guess(listOf(type(1, "사무", "사", 9, 18), off)))
        assertNull(WorkForms.guess(listOf(off)))
        // A pay setup made before says it by its job.
        val nurse = PayProfile(occupation = Occupation.NURSE, wageType = WageType.MONTHLY, base = ValueTimeline(listOf(DatedValue(since, 1))))
        assertEquals(WorkForm.NURSE, WorkForms.guess(listOf(type(1, "사무", "사", 9, 18)), profile = nurse))
        assertEquals(WorkForm.PART_TIME, WorkForms.guess(emptyList(), profile = nurse.copy(occupation = Occupation.CUSTOM, wageType = WageType.HOURLY)))
    }

    @Test
    fun `a new setup from the form`() {
        val p = WorkForms.apply(WorkForm.PART_TIME, 10_320, since)
        assertEquals(WageType.HOURLY, p.wageType)
        assertEquals(Occupation.PART_TIME, p.occupation)
        assertEquals(10_320, p.base.on(oct).value)
        val shift = WorkForms.apply(WorkForm.THREE_SHIFT, 2_508_000, since, holiday = HolidayWork.AS_EXTRA)
        assertEquals(HolidayWork.AS_EXTRA, shift.rules.holidayWork)
        assertTrue(Unknowns.PUBLIC_HOLIDAYS !in shift.estimatedFields)
        // 공무원 gets its own first-setup rules.
        assertEquals(0.0, WorkForms.apply(WorkForm.PUBLIC, 2_500_000, since).rules.nightPremium)
    }

    @Test
    fun `an existing setup keeps every value it holds`() {
        val item = PayItem("n", "나이트 수당", ValueTimeline(listOf(DatedValue(since, 30_000))), Cadence.PER_NIGHT_SHIFT)
        val before = PayProfile(occupation = Occupation.CUSTOM, wageType = WageType.MONTHLY, base = ValueTimeline(listOf(DatedValue(LocalDate.of(2026, 1, 1), 2_400_000))),
            items = listOf(item), monthlyHours = 226, rules = CompanyRules(nightPremium = 0.6))
        val after = WorkForms.apply(WorkForm.NURSE, 2_508_000, since, existing = before)
        assertEquals(listOf(item), after.items)
        assertEquals(226, after.monthlyHours)
        assertEquals(0.6, after.rules.nightPremium)
        assertEquals(Occupation.NURSE, after.occupation)
        // The earlier months keep their pay.
        assertEquals(2_400_000, after.base.on(YearMonth.of(2026, 9)).value)
        assertEquals(2_508_000, after.base.on(oct).value)
    }

    @Test
    fun `the one page says how the month is worked out`() {
        val p = WorkForms.apply(WorkForm.THREE_SHIFT, 2_508_000, since).let {
            it.copy(rules = it.rules.withHolidayWork(HolidayWork.AS_EXTRA).withHolidayShiftPay(3, 100_000))
        }
        val s = PayStory.sentences(p, oct, mapOf(3L to "나이트"))
        assertTrue(s.joinToString("\n"), s[0].contains("통상시급 12,000원"))
        assertTrue(s.any { it.startsWith("야간(22~06시)") && it.contains("50%") })
        assertTrue(s.any { it.contains("특근처럼") && it.contains("나이트 100,000원") })
        val under5 = PayStory.sentences(p.copy(fiveOrMoreEmployees = false), oct)
        assertTrue(under5.any { it.contains("5명 미만") })
        assertTrue(PayStory.sentences(WorkForms.apply(WorkForm.PART_TIME, 10_320, since), oct)[0].contains("주휴수당"))
    }
}
