package com.bamnat.core

import com.bamnat.core.pay.DatedValue
import com.bamnat.core.pay.OcrRows
import com.bamnat.core.pay.Occupation
import com.bamnat.core.pay.PayCalculator
import com.bamnat.core.pay.PayProfile
import com.bamnat.core.pay.PayslipRead
import com.bamnat.core.pay.PremiumTarget
import com.bamnat.core.pay.RuleChange
import com.bamnat.core.pay.RuleText
import com.bamnat.core.pay.SlipHourly
import com.bamnat.core.pay.SlipLine
import com.bamnat.core.pay.SlipReading
import com.bamnat.core.pay.SlipRole
import com.bamnat.core.pay.HourlySource
import com.bamnat.core.pay.ValueTimeline
import com.bamnat.core.pay.WageType
import com.bamnat.core.pay.WonRounding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import kotlin.random.Random

/**
 * [HOURLY-01][HOURLY-02][HOURLY-03] 1.30 AV (사용자 2026-10-27 "LG화학은 근로시수 180시간으로 통상임금이 적용된다. 명세서와 회사
 * 규칙을 사진으로 보내면 해당 내용의 근거로만 가지고도 월급을 정확하게 계산해 낼 수 있어야 한다"): the 기준 시간 worked back from the
 * payslip, a 시급 from one line, another 배율 than the law's, and where each value came from.
 */
class SlipHourlyHoursTest {
    private val plant = SlipReading.read(listOf(PlantSample.payslip(PlantSample.Noise.CLEAN, Random(1)))).read
    private val rules = RuleText.read(OcrRows.join(PlantSample.rules(PlantSample.Noise.CLEAN, Random(1))))

    @Test fun theChemicalPlantIs180Hours() {
        val fit = SlipHourly.of(plant)!!
        // With the rules' 통상임금 (기본급, 직책수당, 자격수당, 기술전수교육수당): 기본급I·II + 자격수당 + 상여 600%의 1/12.
        val items = rules.map { it.change }.filterIsInstance<RuleChange.OrdinaryItems>().single().names
        val h = SlipHourly.hoursOf(plant, fit.hourly, items)!!
        assertEquals(180, h.hours)
        assertEquals(600, h.bonusPercent)
        assertEquals(listOf("기본급I", "기본급II", "자격수당"), h.lines.map { it.name })
        assertTrue("${h.off}", h.off < 3)
        // The payslip alone (no rules): the usual 통상 수당 tried in and out come to the same.
        val alone = SlipHourly.hoursOf(plant, fit.hourly)!!
        assertEquals(180, alone.hours)
        assertEquals(600, alone.bonusPercent)
        println("기준 시간: ${h.hours}시간, 상여 ${h.bonusPercent}%, ${h.ordinary}원, ${h.off}원 차이")
    }

    @Test fun findingsOfferTheHoursWithTheHourly() {
        val found = SlipHourly.findings(plant, rules)
        val hourly = found.map { it.change }.filterIsInstance<RuleChange.OrdinaryHourly>().single()
        assertEquals(21_618.04, hourly.hourly, 1e-9)
        assertFalse(hourly.estimated)
        val hours = found.single { it.change is RuleChange.MonthlyHours }
        assertEquals(RuleChange.MonthlyHours(180, HourlySource.SLIP), hours.change)
        assertEquals("명세서로 보면 통상임금 기준 180시간이에요", hours.change.summary)
        assertTrue(hours.sourceLine, hours.sourceLine.contains("상여 연 600%의 1/12") && hours.sourceLine.endsWith("= 180시간"))
        assertTrue(hours.confidence >= 0.6)
        // No 배율 other than the rules' was needed.
        assertTrue(found.none { it.change is RuleChange.Premium })
        // The rules saying 180 already: not offered twice.
        val stated = rules + com.bamnat.core.pay.RuleFinding("통상임금 산정 기준시간 180시간", RuleChange.MonthlyHours(180), 0.85)
        assertTrue(SlipHourly.findings(plant, stated).none { it.change is RuleChange.MonthlyHours })
    }

    /** With the hours and the 통상임금 items in, next month's 통상시급 comes out of the 기본급 alone as the payslip's. */
    @Test fun theHoursRebuildTheHourly() {
        val base = PayProfile(Occupation.CUSTOM, WageType.MONTHLY, ValueTimeline(listOf(DatedValue(LocalDate.of(2020, 1, 1), 2_555_830))))
        val h = SlipHourly.hoursOf(plant, 21_618.04)!!
        val rebuilt = (2_555_830 + 57_500 + h.bonusShare).toDouble() / h.hours
        assertEquals(21_618.04, rebuilt, 0.02)
        val p = RuleText.apply(base, listOf(RuleChange.MonthlyHours(h.hours, HourlySource.SLIP)), YearMonth.of(2026, 11))
        assertEquals(180, p.monthlyHours)
        assertEquals(HourlySource.SLIP, p.hoursSource)
        val q = RuleText.apply(base, listOf(SlipHourly.finding(plant)!!.change), YearMonth.of(2026, 11))
        assertEquals(HourlySource.SLIP, q.ordinarySource)
    }

    /** [HOURLY-02] A payslip with one premium line (야간 only): a guess, unticked, the next payslip makes it sure. */
    @Test fun oneLineIsAGuess() {
        val read = PayslipRead(listOf(SlipLine("기본급", SlipRole.BASE, 2_555_830), SlipLine("야간근무수당", SlipRole.NIGHT, 691_778, 64 * 60)))
        val fit = SlipHourly.of(read)!!
        assertFalse(fit.sure)
        assertEquals(691_778, PayCalculator.lineWon(64 * 60, fit.hourly, 0.5, up = fit.cut == SlipHourly.Cut.UP, down = fit.cut == SlipHourly.Cut.DOWN))
        assertEquals(21_618.0, fit.hourly, 0.1)
        val f = SlipHourly.finding(read)!!
        assertEquals(0.5, f.confidence, 0.0)
        assertTrue(f.change.summary, f.change.summary.startsWith("명세서로 짐작한 통상시급 21,618원"))
    }

    /** [HOURLY-03] A company paying 휴일연장 2.5배: found by trying the usual 배율, and offered with the 시급. */
    @Test fun anotherMultiplierIsFound() {
        val hourly = 15_000.0
        val read = PayslipRead(listOf(
            SlipLine("기본급", SlipRole.BASE, 3_135_000),
            SlipLine("휴일근로수당", SlipRole.HOLIDAY, (16 * 1.5 * hourly).toLong(), 16 * 60),
            SlipLine("휴일연장수당", SlipRole.HOLIDAY, (8 * 2.5 * hourly).toLong(), 8 * 60),
            SlipLine("야간근로수당", SlipRole.NIGHT, (20 * 0.5 * hourly).toLong(), 20 * 60),
        ))
        val fit = SlipHourly.of(read)!!
        assertEquals(hourly, fit.hourly, 1e-9)
        assertEquals(2.5, fit.multipliers.holidayOver8h, 0.0)
        val found = SlipHourly.findings(read)
        val premium = found.single { it.change is RuleChange.Premium }
        assertEquals(RuleChange.Premium(PremiumTarget.HOLIDAY, 1.5, over8h = true), premium.change)
        assertTrue(premium.sourceLine, premium.sourceLine.startsWith("명세서로 보면 휴일연장 2.5배예요"))
        // 3,135,000 ÷ 15,000 = 209 hours.
        assertEquals(RuleChange.MonthlyHours(209, HourlySource.SLIP), found.single { it.change is RuleChange.MonthlyHours }.change)
    }

    /** [HOURLY-03] 원 미만 내림: its own way now (1.29 put it on 반올림). */
    @Test fun cutDownIsKept() {
        val hourly = 12_346.0
        fun down(min: Int, m: Double) = kotlin.math.floor(min / 60.0 * hourly * m + 1e-6).toLong()
        val read = PayslipRead(listOf(
            SlipLine("연장근로수당", SlipRole.OVERTIME, down(52 * 60 + 30, 1.5), 52 * 60 + 30),
            SlipLine("야간근로수당", SlipRole.NIGHT, down(64 * 60, 0.5), 64 * 60),
            SlipLine("휴일근로수당", SlipRole.HOLIDAY, down(81 * 60, 1.5), 81 * 60),
        ))
        val f = SlipHourly.finding(read)!!.change as RuleChange.OrdinaryHourly
        assertTrue("$f", f.roundDown)
        assertTrue(f.summary, f.summary.endsWith("(원 미만 내림)"))
        val base = PayProfile(Occupation.CUSTOM, WageType.MONTHLY, ValueTimeline(listOf(DatedValue(LocalDate.of(2020, 1, 1), 2_500_000))))
        assertEquals(WonRounding.AT_END_DOWN, RuleText.apply(base, listOf(f), YearMonth.of(2026, 11)).rules.wonRounding)
        assertEquals(down(37 * 60, 0.5), PayCalculator.lineWon(37 * 60, f.hourly, 0.5, up = false, down = true))
    }

    /** Hours no company is known to use, from a payslip that fits several: not offered. */
    @Test fun noBaseNoHours() {
        val read = PayslipRead(listOf(SlipLine("야간근로수당", SlipRole.NIGHT, 100_000, 20 * 60)))
        assertNull(SlipHourly.hoursOf(read, 10_000.0))
        assertNotNull(SlipHourly.of(read))
    }

    /** [HOURLY-01] The rules sentence for the hours, as companies write it; a 산출식 with 시수 in it is not one. */
    @Test fun theRulesSayTheHours() {
        for (s in listOf("통상임금 산정 기준시간 180시간", "통상임금 산정 기준시간: 월 180시간", "월 소정근로시간 180시간", "근로시수 180시간",
            "통상시급 산정 기준시간은 180시간으로 한다", "기준시간 180H", "시수 : 180시간")) {
            val found = RuleText.read(s).map { it.change }
            assertTrue("$s → $found", RuleChange.MonthlyHours(180) in found)
        }
        val formula = RuleText.read("휴일근로수당  해당시간 × 1.5 × ( 통상임금 / 시수 )").map { it.change }
        assertTrue("$formula", formula.none { it is RuleChange.MonthlyHours })
    }
}
