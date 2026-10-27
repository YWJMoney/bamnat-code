package com.bamnat.core

import com.bamnat.core.flags.FlagCatalog
import com.bamnat.core.flags.ProFeatures
import com.bamnat.core.leave.HourlyLeave
import com.bamnat.core.model.ShiftType
import com.bamnat.core.model.WorkPattern
import com.bamnat.core.pay.AnnualPay
import com.bamnat.core.pay.BreakPay
import com.bamnat.core.pay.CompanyRules
import com.bamnat.core.pay.DatedValue
import com.bamnat.core.pay.LineKind
import com.bamnat.core.pay.MonthPay
import com.bamnat.core.pay.Occupation
import com.bamnat.core.pay.PayCalculator
import com.bamnat.core.pay.PayProfile
import com.bamnat.core.pay.ValueTimeline
import com.bamnat.core.pay.WageType
import com.bamnat.core.schedule.ScheduleResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

/**
 * 1.29 AU: the three places the pay engine marked 확인 필요 (1.28 인계 8장 9번), each settled with its source, as golden
 * cases (docs/pay-golden-cases.md 주제 14), and 시간 단위 연차 and the feature names tied to the flag book.
 */
class NxtPayLawTest {
    private fun t(h: Int) = LocalTime.of(h, 0)
    private fun wage(v: Long) = ValueTimeline(listOf(DatedValue(LocalDate.of(2026, 1, 1), v)))
    private val off = ShiftType(99, "휴무", "휴", 0xFF9E9E9E, null, null, isOff = true)
    private fun oneDay(shift: ShiftType, date: LocalDate) =
        ScheduleResolver(listOf(shift, off), WorkPattern.Rotation(date, listOf(shift.id) + List(60) { off.id }), emptyList())
    private fun hourly(v: Long, rules: CompanyRules = CompanyRules()) =
        PayProfile(occupation = Occupation.CUSTOM, wageType = WageType.HOURLY, base = wage(v), fiveOrMoreEmployees = true, rules = rules)
    private fun pay(p: PayProfile, r: ScheduleResolver, month: YearMonth) = PayCalculator().calculate(p, month, r::resolve)
    private fun MonthPay.sum(kind: LineKind) = lines.filter { it.kind == kind }.sumOf { it.amount }

    // ---- [NXT-04] 승인 받은 감시·단속 근로자의 근로자의 날 ----

    /** 24시간 격일제 09:00~익일 09:00, 휴게 8시간 18:00~02:00 (M11 T20과 같음): 실근로 16시간, 야간대 실근로 4시간. */
    private val guard = ShiftType(30, "당직", "당", 0xFF000000, t(9), t(9), 480, t(18))
    private val approved = CompanyRules(supervisoryApproved = true)

    @Test
    fun `NXT-04 Labour Day is a paid day for an approved guard, worked hours at 100 percent, other holidays are not`() {
        // 고용노동부 회신(2023-01-18): 근로자의 날은 제63조 적용제외 근로자에게도 적용, 연장·휴일근로 가산임금은 적용받지 않음.
        val mayDay = LocalDate.of(2026, 5, 1)
        val may = YearMonth.of(2026, 5)
        val yes = pay(hourly(10_320, approved), oneDay(guard, mayDay), may)
        // 유급휴일 8시간 × 10,320 = 82,560 + 근무 16시간 × 10,320 = 165,120 + 야간 가산 4시간 × 5,160 = 20,640.
        assertEquals(82_560, yes.lines.single { it.label == "유급휴일수당 (공휴일)" }.amount)
        assertTrue(yes.lines.filter { it.kind == LineKind.HOLIDAY }.all { it.multiplier == 1.0 })
        assertEquals(20_640, yes.sum(LineKind.NIGHT))
        assertEquals(268_320, yes.day(mayDay)!!.amount)
        // Without the approval the same day also earns the 휴일근로 premium, so it is more.
        val no = pay(hourly(10_320), oneDay(guard, mayDay), may)
        assertTrue(no.day(mayDay)!!.amount > yes.day(mayDay)!!.amount)
        assertTrue(no.lines.any { it.kind == LineKind.HOLIDAY && it.multiplier > 1.0 })
        // 어린이날 (5/5, 관공서 공휴일): 제55조 제2항 is in chapter 4, so an approved guard has no paid day for it.
        val childrensDay = LocalDate.of(2026, 5, 5)
        val other = pay(hourly(10_320, approved), oneDay(guard, childrensDay), may)
        assertTrue(other.lines.none { it.label == "유급휴일수당 (공휴일)" })
        assertEquals(165_120 + 20_640L, other.day(childrensDay)!!.amount)
    }

    // ---- [NXT-05] 22~06시 유급 휴게의 야간 가산 ----

    /** 야간 19~07 (휴게 1시간 02~03, 야간대 안): 실근로 11시간, 야간대 실근로 7시간. */
    private val night = ShiftType(2, "야간", "야", 0xFF5E35B1, t(19), t(7), 60, t(2))

    @Test
    fun `NXT-05 a paid break at 02 to 03 is paid at 100 percent without the night premium, a break spent waiting earns it`() {
        val d = LocalDate.of(2026, 11, 4)
        val nov = YearMonth.of(2026, 11)
        val unpaid = pay(hourly(12_000), oneDay(night, d), nov)
        val paid = pay(hourly(12_000, CompanyRules(breakPay = BreakPay.PAID)), oneDay(night, d), nov)
        val waiting = pay(hourly(12_000, CompanyRules(breakPay = BreakPay.AS_WORK)), oneDay(night, d), nov)
        // 유급 휴게: 1시간 × 12,000 more, the night premium (7시간 × 6,000 = 42,000) the same: a break is not 근로 (제56조 제3항).
        assertEquals(42_000, unpaid.sum(LineKind.NIGHT))
        assertEquals(unpaid.sum(LineKind.NIGHT), paid.sum(LineKind.NIGHT))
        assertEquals(unpaid.sum(LineKind.OVERTIME), paid.sum(LineKind.OVERTIME))
        assertEquals(12_000, paid.day(d)!!.amount - unpaid.day(d)!!.amount)
        val breakLine = paid.lines.single { it.label == "유급 휴게" }
        assertTrue(breakLine.basis, breakLine.basis.contains("제56조 제3항") && breakLine.basis.contains("일한 시간으로"))
        // Waiting at one's post is 근로시간 (제50조 제3항): the hour counts as night work and as 연장 (12시간 > 8시간).
        assertEquals(48_000, waiting.sum(LineKind.NIGHT))
        assertTrue(waiting.sum(LineKind.OVERTIME) > unpaid.sum(LineKind.OVERTIME))
    }

    // ---- [NXT-06] 연봉 ÷ 13 ----

    @Test
    fun `NXT-06 a 13-way annual salary keeps 연봉 over 13 and tells a 퇴직금 kept apart from one folded into pay`() {
        val b = AnnualPay(39_000_000, split = 13).breakdown()
        assertEquals(3_000_000, b.monthlyBase)
        assertEquals(listOf("연봉 39,000,000원 ÷ 13 = 기본급 3,000,000원"), b.lines)
        val w = b.warnings.single()
        assertTrue(w, "퇴직할 때 퇴직금으로 받는다면 맞는 계산" in w)
        assertTrue(w, "매달 월급에 나눠 받는다면" in w && "2007다90760" in w)
        assertTrue(w, "노무사" in w && "1350" in w)
        // 12 has no such warning.
        assertTrue(AnnualPay(36_000_000).breakdown().warnings.isEmpty())
    }

    // ---- [NXT-02] 시간 단위 연차 ----

    @Test
    fun `NXT-02 hourly leave opens with the leave hourly row and the screen says so`() {
        assertTrue(HourlyLeave.open { it == FlagCatalog.LEAVE_HOURLY })
        assertFalse(HourlyLeave.open { false })
        val open = HourlyLeave.note(open = true, today = LocalDate.of(2027, 6, 10))
        assertNotNull(open)
        assertTrue(open!!, open.contains("시간 단위 연차를 쓸 수 있어요") && open.contains("시행령"))
        // The month before: what is coming. Before that, and when the row is still off after the day (forced off): nothing.
        assertTrue(HourlyLeave.note(open = false, today = LocalDate.of(2027, 5, 11))!!.contains("2027년 6월 10일부터"))
        assertNull(HourlyLeave.note(open = false, today = LocalDate.of(2027, 5, 10)))
        assertNull(HourlyLeave.note(open = false, today = LocalDate.of(2027, 6, 10)))
    }

    // ---- [NXT-03] 기능 이름표 ----

    @Test
    fun `NXT-03 the feature table names every free row and keeps codes the free pattern takes`() {
        val pattern = Regex(FlagCatalog.FREE_FEATURE)
        assertTrue(ProFeatures.NAMES.keys.all { pattern.matches(it) })
        assertEquals(ProFeatures.NAMES.size, ProFeatures.NAMES.values.toSet().size)
        for ((code, name) in ProFeatures.NAMES) assertEquals("무료로 연 프로 기능: $name", FlagCatalog.of(FlagCatalog.FREE_PREFIX + code)!!.name)
    }
}
