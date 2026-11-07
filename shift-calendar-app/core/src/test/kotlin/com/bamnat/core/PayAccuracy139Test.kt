package com.bamnat.core

import com.bamnat.core.boss.AccidentRates
import com.bamnat.core.boss.Insurance
import com.bamnat.core.boss.PayrollLedger
import com.bamnat.core.model.ShiftType
import com.bamnat.core.model.WorkPattern
import com.bamnat.core.pay.Cadence
import com.bamnat.core.pay.CustomRule
import com.bamnat.core.pay.DatedValue
import com.bamnat.core.pay.HolidayWeek
import com.bamnat.core.pay.WeekHoursNudge
import com.bamnat.core.pay.InsuranceLine
import com.bamnat.core.pay.PayCalculator
import com.bamnat.core.pay.PayItem
import com.bamnat.core.pay.RulePay
import com.bamnat.core.pay.RuleWhen
import com.bamnat.core.pay.TakeHome
import com.bamnat.core.pay.TaxMode
import com.bamnat.core.pay.ValueTimeline
import com.bamnat.core.pay.WonCut
import com.bamnat.core.pay.WorkForm
import com.bamnat.core.pay.WorkForms
import com.bamnat.core.schedule.ScheduleResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

/**
 * 1.39 G3 급여·수당 정확도 (1.38에서 넘긴 '소급분·달마다 다른 공제 끝까지', '기타 비과세가 실수령에 주는 변화', 공식 대조 G2~G6).
 * [PAYX-56] 세후 공제, [PAYX-57] 이번 달만(소급분)과 규칙 비과세, [PAYX-58] 비과세 종류별 한도, [PAYX-59] 건보 하한·떼지 않는 보험, [BOSS-12] 산재.
 */
class PayAccuracy139Test {
    private val nov = YearMonth.of(2026, 11)
    private val work = ShiftType(1, "일근", "일", 0xFF1E88E5, LocalTime.of(9, 0), LocalTime.of(18, 0), 60, LocalTime.of(12, 0))
    private val resolver = ScheduleResolver(listOf(work), WorkPattern.Rotation(LocalDate.of(2026, 11, 1), listOf(1L)), emptyList())
    private val office = WorkForms.apply(WorkForm.OFFICE, 3_000_000, LocalDate.of(2026, 1, 1)).copy(taxMode = TaxMode.INSURANCE_4, taxChosen = true)
    private fun pay(p: com.bamnat.core.pay.PayProfile, m: YearMonth = nov) = PayCalculator().calculate(p, m, resolver::resolve, checks = false)
    private fun monthly(name: String, amount: Long, deduct: Boolean = false) = CustomRule("r-$name", name, RuleWhen(), RulePay.Monthly(amount), deduct = deduct)

    @Test fun namesSayWhichSideADeductionIsOn() {
        assertTrue(CustomRule.looksAfterTax("사택관리비"))
        assertTrue(CustomRule.looksAfterTax("남부_사택관리비"))
        assertTrue(CustomRule.looksAfterTax("노동조합비"))
        assertTrue(CustomRule.looksAfterTax("친목회비"))
        assertFalse(CustomRule.looksAfterTax("지각 공제"))
        assertFalse(CustomRule.looksAfterTax("결근 차감"))
        assertFalse(CustomRule.looksAfterTax("유니폼"))
        assertTrue(CustomRule.looksOneMonth("소급분(지급)"))
        assertFalse(CustomRule.looksOneMonth("자격수당"))
    }

    /** 사택관리비 is on the payslip's 공제 side: 지급 총액 and the tax on it stay, and 실수령 is less by its month's amount. */
    @Test fun anAfterTaxDeductionLeavesGrossAndTaxAlone() {
        val plain = pay(office)
        val house = monthly("사택관리비", 50_000, deduct = true).copy(byMonth = mapOf(nov to 83_000), afterTax = true)
        val p = office.copy(customRules = listOf(house))
        val r = pay(p)
        assertEquals("지급 총액은 그대로", plain.total, r.total)
        assertEquals(listOf(83_000L), r.afterTax.map { it.amount })
        val before = TakeHome.forProfile(office, nov, plain.total)
        val after = TakeHome.forProfile(p, nov, r.total, pay = r)
        assertEquals("세금은 그대로", before.deductions.filter { it.first == TakeHome.TAX_LABEL }, after.deductions.filter { it.first == TakeHome.TAX_LABEL })
        assertEquals(before.net - 83_000, after.net)
        // The same without the month's pay: from the profile alone.
        assertEquals(after.net, TakeHome.forProfile(p, nov, r.total).net)
        // Other months: 보통 50,000원.
        assertEquals(before.net - 50_000, TakeHome.forProfile(p, nov.plusMonths(1), plain.total).net)
        // Taken off the pay instead (the 1.38 way): false, and null for a rule from before 1.39, which the pay screen offers to move.
        for (way in listOf(false, null)) {
            val pre = p.copy(customRules = listOf(house.copy(afterTax = way)))
            assertEquals(plain.total - 83_000, pay(pre).total)
            assertTrue(pay(pre).afterTax.isEmpty())
        }
        assertTrue(house.copy(afterTax = null).mayBeAfterTax)
        assertFalse(house.mayBeAfterTax)
        assertFalse(monthly("지각 공제", 30_000, deduct = true).mayBeAfterTax)
    }

    /** 지각 공제 stays on the 지급 side as before 1.39. */
    @Test fun aDeductionFromThePayStaysInTheTotal() {
        val p = office.copy(customRules = listOf(monthly("지각 공제", 30_000, deduct = true)))
        assertEquals(pay(office).total - 30_000, pay(p).total)
    }

    /** 소급분 (지급) once: that month only, taxed with the month's pay. */
    @Test fun aOneMonthAllowanceIsPaidInItsMonthOnly() {
        val back = monthly("소급분", 120_000).copy(from = nov, until = nov)
        assertTrue(back.oneMonth)
        assertEquals("2026년 11월 한 번 120,000원", back.describe())
        val p = office.copy(customRules = listOf(back))
        assertEquals(pay(office).total + 120_000, pay(p).total)
        assertEquals(pay(office, nov.plusMonths(1)).total, pay(p, nov.plusMonths(1)).total)
        assertEquals(pay(office, nov.minusMonths(1)).total, pay(p, nov.minusMonths(1)).total)
    }

    /** 식대 두 줄은 한도 하나(월 20만 원), 사용자가 켠 다른 비과세(교통비)는 따로 20만 원까지. */
    @Test fun nonTaxableLimitIsPerKind() {
        fun item(name: String, amount: Long) = PayItem("i-$name", name, ValueTimeline(listOf(DatedValue(LocalDate.of(2026, 1, 1), amount))), Cadence.MONTHLY, taxable = false)
        val p = office.copy(items = listOf(item("중식대", 100_000), item("석식대", 150_000), item("교통비", 100_000)))
        assertEquals(300_000L, TakeHome.nonTaxable(p, nov))
        val parts = TakeHome.nonTaxableParts(p, nov).associateBy { it.kind }
        assertEquals(250_000L, parts.getValue("식대").paid)
        assertEquals(200_000L, parts.getValue("식대").amount)
        assertTrue(parts.getValue("식대").over)
        assertEquals(100_000L, parts.getValue("교통비").amount)
        // 비과세를 켜면 실수령 어림이 늘어요 (교통비 10만 원: 세금·4대보험 기준이 줄어서).
        val taxed = p.copy(items = p.items.map { if (it.name == "교통비") it.copy(taxable = true) else it })
        val gross = pay(p).total + 350_000
        assertTrue(TakeHome.forProfile(p, nov, gross).net > TakeHome.forProfile(taxed, nov, gross).net)
    }

    /** A 매달 수당 kept as a rule (기간·이번 달만) keeps its 비과세. */
    @Test fun aRuleAllowanceCanBeNonTaxable() {
        val meal = monthly("식대", 200_000).copy(from = nov, until = nov, taxable = false)
        val p = office.copy(customRules = listOf(meal))
        assertEquals(200_000L, TakeHome.nonTaxable(p, nov))
        assertEquals(0L, TakeHome.nonTaxable(p, nov.plusMonths(1)))
    }

    /** G4 건강보험 보수월액 하한 28만 원 (국민건강보험법 시행령 제32조). */
    @Test fun healthInsuranceHasAFloor() {
        val r = TakeHome.estimate(200_000, TaxMode.INSURANCE_4, on = nov.atDay(1))
        val sh = TakeHome.sharesOn(nov.atDay(1))
        val health = WonCut.cut10(280_000, sh.health)
        assertEquals(health, r.deductions.toMap()["건강보험"])
        assertEquals(WonCut.cut10(health, sh.careRatio), r.deductions.toMap()["장기요양"])
        assertTrue(r.notes.single(), "280,000원" in r.notes.single())
        // Above it, as before.
        assertEquals(WonCut.cut10(3_000_000, sh.health), TakeHome.estimate(3_000_000, TaxMode.INSURANCE_4, on = nov.atDay(1)).deductions.toMap()["건강보험"])
    }

    /** G2·G3·G6: lines the worker is out of are not taken, and the note says why. */
    @Test fun insuranceTheWorkerIsOutOfIsNotTaken() {
        val p = office.copy(insuranceOff = setOf(InsuranceLine.PENSION, InsuranceLine.EMPLOYMENT))
        val all = TakeHome.forProfile(office, nov, 3_000_000).deductions.toMap()
        val r = TakeHome.forProfile(p, nov, 3_000_000)
        val d = r.deductions.toMap()
        assertNull(d["국민연금"]); assertNull(d["고용보험"])
        assertEquals(all["건강보험"], d["건강보험"])
        assertEquals(all[TakeHome.TAX_LABEL], d[TakeHome.TAX_LABEL])
        assertEquals(2, r.notes.size)
        assertEquals(TakeHome.forProfile(office, nov, 3_000_000).net + all.getValue("국민연금") + all.getValue("고용보험"), r.net)
        // A 실수령 worked back leaves at least that 실수령, with the same lines left out.
        val back = TakeHome.grossFromNet(r.net, TaxMode.INSURANCE_4, on = nov.atDay(1), off = p.insuranceOff)
        assertTrue(back in 2_980_000..3_000_000)
        assertTrue(TakeHome.estimate(back, TaxMode.INSURANCE_4, on = nov.atDay(1), off = p.insuranceOff).net >= r.net)
    }

    /** G5 산재보험: 2026 고시 업종 요율 + 출퇴근재해 0.6‰, 사업주만. */
    @Test fun accidentInsuranceFollowsTheIndustry() {
        assertEquals(8.0, AccidentRates.kind("retail_food")!!.permille, 0.0)
        assertEquals(35.0, AccidentRates.kind("construction")!!.permille, 0.0)
        // 1.39 B6: 2,000,000 × (8 + 출퇴근 0.6 + 임금채권 0.9 + 석면 0.06 = 9.56‰) = 19,120원.
        assertEquals(19_120L, AccidentRates.premium(2_000_000, "retail_food"))
        assertEquals(0L, AccidentRates.premium(2_000_000, null))
        assertEquals(15_120L, AccidentRates.premium(2_000_000, "custom:6"))
        // 국가·지자체 사업은 임금채권부담금이 없어요: 9 + 0.6 + 0.06 = 9.66‰.
        assertEquals(19_320L, AccidentRates.premium(2_000_000, "public"))
        assertTrue("임금채권부담금 0.9‰" in AccidentRates.describe(AccidentRates.kind("retail_food")!!))
        assertNull(AccidentRates.kind("custom:abc"))
        // Every 2026 고시 사업종류 is there once.
        assertEquals(28, AccidentRates.KINDS.size)
        assertEquals(AccidentRates.KINDS.size, AccidentRates.KINDS.map { it.code }.toSet().size)
    }

    /** 1.39 [PAYX-61] 발굴 G3-B7: 건강보험료 월 상한(보건복지부고시 제2025-222호 보수월액보험료 9,183,480원, 근로자·사업주 반씩 4,591,740원). */
    @Test fun healthHasAMonthlyCeiling() {
        val on = nov.atDay(1)
        assertEquals(4_591_740L, TakeHome.healthCap(on))
        assertNull(TakeHome.healthCap(LocalDate.of(2025, 12, 1)))
        val r = TakeHome.estimate(200_000_000, TaxMode.INSURANCE_4, on = on)
        assertEquals(4_591_740L, r.deductions.toMap()["건강보험"])
        assertTrue(r.notes.any { "월 상한" in it })
        // An ordinary pay is not touched.
        val sh = TakeHome.sharesOn(on)
        assertEquals(WonCut.cut10(3_000_000, sh.health), TakeHome.estimate(3_000_000, TaxMode.INSURANCE_4, on = on).deductions.toMap()["건강보험"])
        // 사업주 몫도 같은 상한.
        val healthOnly = PayrollLedger.employerInsurance(200_000_000, Insurance(health = true), on)
        assertEquals(4_591_740L + WonCut.cut10(4_591_740L, sh.careRatio), healthOnly)
    }

    /** 1.39 [PAYX-63] 발굴 G3-B8: 소급분이 있는 달은 '연말정산 때 맞춰져요' 한 줄. */
    @Test fun backPaySaysTheYearSettlesItsTax() {
        val back = monthly("임금인상 소급분", 120_000).copy(from = nov, until = nov)
        val p = office.copy(customRules = listOf(back))
        assertTrue(TakeHome.BACK_PAY_NOTE in TakeHome.forProfile(p, nov, pay(p).total).notes)
        assertFalse(TakeHome.BACK_PAY_NOTE in TakeHome.forProfile(p, nov.plusMonths(1), pay(p, nov.plusMonths(1)).total).notes)
        // 이름에 '소급'이 없는 한 번 수당(격려금)은 말하지 않아요.
        val bonus = office.copy(customRules = listOf(monthly("격려금", 100_000).copy(from = nov, until = nov)))
        assertFalse(TakeHome.BACK_PAY_NOTE in TakeHome.forProfile(bonus, nov, pay(bonus).total).notes)
    }

    /** G4 for the 사업주 half too: a 직원 on a small month pays on 28만 원, and so does the 사장님. */
    @Test fun employerHealthHasTheSameFloor() {
        val on = nov.atDay(1)
        val small = PayrollLedger.employerInsurance(200_000, Insurance(health = true), on)
        val sh = TakeHome.sharesOn(on)
        val h = WonCut.cut10(280_000, sh.health)
        assertEquals(h + WonCut.cut10(h, sh.careRatio), small)
        val big = PayrollLedger.employerInsurance(3_000_000, Insurance(health = true), on)
        val hb = WonCut.cut10(3_000_000, sh.health)
        assertEquals(hb + WonCut.cut10(hb, sh.careRatio), big)
    }

    /** 1.39 [PAYX-62] 발굴 G3-M3: 주 15시간 경계 알림. */
    @Test fun weekJustUnderFifteenHoursSaysHowMuchMore() {
        fun wk(from: String, minutes: Int, absent: Boolean = false): HolidayWeek {
            val d = LocalDate.parse(from)
            return HolidayWeek(d, d.plusDays(6), minutes, if (minutes >= 900) minOf(minutes, 2400) / 5 else 0, absent)
        }
        val today = LocalDate.of(2026, 11, 11)
        val under = WeekHoursNudge.lines(listOf(wk("2026-11-09", 14 * 60 + 30)), today)
        assertEquals(1, under.size)
        assertTrue(under[0], under[0].contains("14시간 30분") && under[0].contains("30분 더 일하면"))
        val over = WeekHoursNudge.lines(listOf(wk("2026-11-09", 15 * 60 + 30)), today)
        assertTrue(over[0], over[0].contains("주휴수당을 받아요") && over[0].contains("30분 넘게 줄면"))
        assertTrue(WeekHoursNudge.lines(listOf(wk("2026-11-09", 15 * 60)), today)[0].contains("조금이라도"))
        // Far from the line, another week, or a week with 결근: nothing.
        assertTrue(WeekHoursNudge.lines(listOf(wk("2026-11-09", 8 * 60)), today).isEmpty())
        assertTrue(WeekHoursNudge.lines(listOf(wk("2026-11-09", 20 * 60)), today).isEmpty())
        assertTrue(WeekHoursNudge.lines(listOf(wk("2026-11-02", 14 * 60)), today).isEmpty())
        assertTrue(WeekHoursNudge.lines(listOf(wk("2026-11-09", 14 * 60, absent = true)), today).isEmpty())
        // Two weeks of the month kept just under 15 hours: said once.
        val kept = WeekHoursNudge.lines(listOf(wk("2026-11-02", 14 * 60), wk("2026-11-09", 14 * 60 + 30), wk("2026-11-16", 10 * 60)), today)
        assertEquals(2, kept.size)
        assertTrue(kept[1], kept[1].contains("2주") && kept[1].contains("바로 아래로 맞춰진"))
    }
}
