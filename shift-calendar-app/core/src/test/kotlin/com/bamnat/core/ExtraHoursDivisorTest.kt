package com.bamnat.core

import com.bamnat.core.model.DayEntry
import com.bamnat.core.model.ExtraEntry
import com.bamnat.core.model.ExtraKind
import com.bamnat.core.model.ExtraKinds
import com.bamnat.core.model.ExtraTiming
import com.bamnat.core.model.ShiftType
import com.bamnat.core.model.WorkPattern
import com.bamnat.core.pay.CompanyRules
import com.bamnat.core.pay.DatedValue
import com.bamnat.core.pay.ExtraBasis
import com.bamnat.core.pay.ExtraRule
import com.bamnat.core.pay.LineKind
import com.bamnat.core.pay.Occupation
import com.bamnat.core.pay.PayCalculator
import com.bamnat.core.pay.PayProfile
import com.bamnat.core.pay.Premium
import com.bamnat.core.pay.ValueTimeline
import com.bamnat.core.pay.WageType
import com.bamnat.core.schedule.ScheduleResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

/**
 * 1.38 [PAYX-49] 이 일만의 시수, worked out by hand. 사용자 2026-11-06: "휴일근무·연장근무는 180시수를 적용받는데 교육은 243시수를
 * 적용받아 일반 연장보다 적게 받는 경우". 통상임금 2,430,000원 so the numbers stay whole: ÷ 180 = 13,500원 (연장·휴일), ÷ 243 =
 * 10,000원 (교육). 5인 이상, 4조2교대 주주휴휴야야휴휴 from 11/1 (일). A case's amount is what the work adds to the month.
 */
class ExtraHoursDivisorTest {
    private val nov = YearMonth.of(2026, 11)
    private fun t(h: Int, m: Int = 0) = LocalTime.of(h, m)

    private val day = ShiftType(1, "주간", "주", 0xFF1E88E5, t(7), t(19), 60, t(12))
    private val night = ShiftType(2, "야간", "야", 0xFF5E35B1, t(19), t(7), 60, t(2))
    private val off = ShiftType(3, "휴무", "휴", 0xFF9E9E9E, null, null, isOff = true)
    private val start = LocalDate.of(2026, 11, 1)
    private val four = listOf(day.id, day.id, off.id, off.id, night.id, night.id, off.id, off.id)

    private val eduId = ExtraKind.FIRST_CUSTOM_ID
    private val kinds = ExtraKinds(ExtraKinds.DEFAULTS + ExtraKind(eduId, "교육", "교", 0xFF3949AB,
        ExtraTiming.AfterShift(120), countsAsShift = false))
    private fun roster(vararg e: DayEntry) = ScheduleResolver(listOf(day, night, off), listOf(WorkPattern.Rotation(start, four)), e.toList(), kinds)

    private val salary = PayProfile(
        occupation = Occupation.MANUFACTURING, wageType = WageType.MONTHLY,
        base = ValueTimeline(listOf(DatedValue(LocalDate.of(2026, 1, 1), 2_430_000))),
        monthlyHours = 180,
    )
    private val law = ExtraRule.lawUnit(overtime = 0.5, night = 0.5, holiday = 0.5, restAsHoliday = false)
    private val edu243 = law.copy(monthlyHours = 243)

    private fun withEdu(p: PayProfile, rule: ExtraRule) = p.copy(rules = p.rules.copy(extra = mapOf(eduId to rule)))
    private fun pay(p: PayProfile, vararg e: DayEntry) = PayCalculator().calculate(p, nov, roster(*e)::resolve)
    private fun added(p: PayProfile, vararg e: DayEntry) = pay(p, *e).total - pay(p).total

    /** 11/1 주간 07~19 (11시간 근무) then 교육 2시간: all of it 연장. */
    private val afterShift = DayEntry(start, extras = listOf(ExtraEntry(eduId, minutes = 120)))

    /** 11/3 휴무 09~13 교육 4시간: 그 주 22시간 + 4시간이라 8·40시간 안 (소정 외 100%). */
    private val nov3 = LocalDate.of(2026, 11, 3)
    private val dayOff = DayEntry(nov3, extras = listOf(ExtraEntry(eduId, t(9), t(13))))

    @Test
    fun `E1 교육 after the shift is 연장 at 13,500 by the law, 10,000 on its own 243 hours`() {
        // 법대로: 2 × 13,500 × 150% = 40,500. 교육 243시간: 2 × 10,000 × 150% = 30,000.
        assertEquals(40_500, added(salary, afterShift))
        assertEquals(40_500, added(withEdu(salary, law), afterShift))
        assertEquals(30_000, added(withEdu(salary, edu243), afterShift))
    }

    @Test
    fun `E2 교육 on a day off is 100 percent of the hour, on its own hours`() {
        // 법대로 4 × 13,500 = 54,000; 243시간 4 × 10,000 = 40,000.
        assertEquals(54_000, added(salary, dayOff))
        assertEquals(40_000, added(withEdu(salary, edu243), dayOff))
    }

    @Test
    fun `E3 other work keeps the profile's 180 hours`() {
        // 잔업 2시간 after the same shift is still 2 × 13,500 × 150%, with 교육 on 243.
        val overtime = DayEntry(start, extras = listOf(ExtraEntry(ExtraKind.OVERTIME_ID, minutes = 120)))
        assertEquals(40_500, added(withEdu(salary, edu243), overtime))
        assertEquals(13_500, pay(withEdu(salary, edu243)).ordinaryHourly)
    }

    @Test
    fun `E4 no premium for 교육 pays the hours only`() {
        val plain = edu243.copy(premiums = edu243.premiums.map { it.copy(on = false) })
        assertEquals(20_000, added(withEdu(salary, plain), afterShift))
    }

    @Test
    fun `E5 a 통상시급 set from the payslip is turned back into the month first`() {
        // 13,500 × 180 = 2,430,000 ÷ 243 = 10,000.
        val set = salary.copy(ordinaryHourly = 13_500.0)
        assertEquals(30_000, added(withEdu(set, edu243), afterShift))
        assertEquals(2_430_000.0, PayCalculator.ordinaryForHours(set, nov)!!, 0.001)
    }

    @Test
    fun `E6 시급 has no 시수, so the hours of a kind change nothing`() {
        val hourly = salary.copy(wageType = WageType.HOURLY, base = ValueTimeline(listOf(DatedValue(LocalDate.of(2026, 1, 1), 13_500))))
        assertEquals(added(withEdu(hourly, law), afterShift), added(withEdu(hourly, edu243), afterShift))
        assertNull(PayCalculator.ordinaryForHours(hourly, nov))
    }

    @Test
    fun `E7 the line and its formula say the hours`() {
        val p = pay(withEdu(salary, edu243), afterShift)
        val part = p.day(start)!!.parts.single { it.kind == LineKind.EXTRA }
        assertTrue(part.formula, part.formula.startsWith("2시간 × 10,000원(÷243시간) = 20,000원"))
        val line = p.lines.single { it.kind == LineKind.EXTRA }
        // [PAYX-55] One time in the month: the line says its numbers, not the profile's 180시간 통상시급.
        assertTrue(line.formula, line.formula.startsWith("2시간 × 10,000원(÷243시간) = 20,000원") && "180시간" !in line.formula)
        assertTrue(line.basis, "통상임금 ÷ 243시간 10,000원" in line.basis)
        assertTrue(line.basis, "다른 근무는 180시간" in line.basis)
    }

    @Test
    fun `E8 the older ways paid on 통상시급 take the hours too`() {
        // 통상시급 × 150%: 2 × 10,000 × 1.5.
        val rate = ExtraRule(basis = ExtraBasis.HOURLY_RATE, multiplier = 1.5, monthlyHours = 243)
        assertEquals(30_000, added(withEdu(salary, rate), afterShift))
        assertTrue(rate.describe(), rate.describe().startsWith("통상시급 (243시간 기준) × 150%"))
        // A fixed amount an hour has no 통상시급 in it.
        assertEquals(false, ExtraRule(basis = ExtraBasis.FIXED_PER_HOUR, amount = 9_000, monthlyHours = 243).usesHourly)
        assertEquals(true, edu243.usesHourly)
        assertEquals(Premium.OVERTIME, edu243.ticked.first().key)
    }
}
