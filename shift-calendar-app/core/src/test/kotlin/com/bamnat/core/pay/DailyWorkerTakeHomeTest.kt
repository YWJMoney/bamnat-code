package com.bamnat.core.pay

import com.bamnat.core.money.DailyWorkerTax
import com.bamnat.core.profile.FactKey
import com.bamnat.core.profile.FactText
import com.bamnat.core.profile.Questions
import com.bamnat.core.profile.Answer
import com.bamnat.core.tax.TaxLearner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/**
 * 2.0.0 [RVJ-80] 검토 T04-05·T05b-11 (현장 일당): 급여의 세금 방식에 '일용근로'. 한 달 급여를 일당으로 나눈 날마다 하루 15만 원을 빼고
 * 소득세를 매겨 한 번에 받는 것으로 셈하고(국세청 '일용근로자 원천징수' 예시와 같은 값), 고용보험을 떼요.
 */
class DailyWorkerTakeHomeTest {
    private val oct = YearMonth.of(2026, 10)
    private val day = oct.atDay(1)

    private fun daily(rate: Long, mode: TaxMode = TaxMode.DAILY_WORKER, off: Set<InsuranceLine> = emptySet()) = PayProfile(
        occupation = Occupation.CUSTOM, wageType = WageType.DAILY,
        base = ValueTimeline(listOf(DatedValue(LocalDate.of(2026, 1, 1), rate))), taxMode = mode, taxChosen = true, insuranceOff = off,
    )

    @Test
    fun `five days of 20만 원 owe what the 국세청 example says, with 고용보험`() {
        val r = TakeHome.estimate(1_000_000, TaxMode.DAILY_WORKER, on = day, dayRate = 200_000)
        val lines = r.deductions.toMap()
        // 국세청 안내: 일당 20만 원 5일 한 번에 → 소득세 6,750원, 지방소득세 670원.
        assertEquals(6_750L, lines[TakeHome.DAILY_TAX_LABEL])
        assertEquals(670L, lines[TakeHome.LOCAL_TAX_LABEL])
        assertEquals(WonCut.cut10(1_000_000, TakeHome.sharesOn(day).employment), lines["고용보험"])
        assertEquals(1_000_000 - lines.values.sum(), r.net)
        assertFalse("국민연금·건강보험은 떼지 않아요", lines.keys.any { it.contains("국민연금") || it.contains("건강보험") })
        assertTrue(TakeHome.DAILY_NOTE in r.notes)
    }

    @Test
    fun `under 15만 원 a day there is no 소득세, only 고용보험`() {
        val r = TakeHome.estimate(1_500_000, TaxMode.DAILY_WORKER, on = day, dayRate = 150_000)
        assertEquals(listOf("고용보험"), r.deductions.map { it.first })
    }

    @Test
    fun `the same month read as one day owes more, so the 일당 is used`() {
        val days = TakeHome.estimate(1_000_000, TaxMode.DAILY_WORKER, on = day, dayRate = 200_000)
        val one = TakeHome.estimate(1_000_000, TaxMode.DAILY_WORKER, on = day)
        assertTrue(one.net < days.net)
        // The profile's 일당 is the one forProfile and the learner's table use.
        assertEquals(days.net, TakeHome.forProfile(daily(200_000), oct, 1_000_000, free = 0).net)
        assertEquals(days.deductions.sumOf { it.second }, TaxLearner.Table.of(daily(200_000)).deductions(1_000_000, oct))
        assertEquals(200_000L, TakeHome.dayRateOf(daily(200_000), oct))
        assertNull(TakeHome.dayRateOf(daily(200_000).copy(wageType = WageType.HOURLY), oct))
    }

    @Test
    fun `the tax is DailyWorkerTax's, as 현장 도구 says it`() {
        val r = TakeHome.estimate(1_000_000, TaxMode.DAILY_WORKER, on = day, dayRate = 250_000)
        val t = DailyWorkerTax.paidTogether(List(4) { 250_000L })
        assertEquals(t.income, r.deductions.toMap()[TakeHome.DAILY_TAX_LABEL])
        assertEquals(t.local, r.deductions.toMap()[TakeHome.LOCAL_TAX_LABEL])
    }

    @Test
    fun `65세 이후 새로 일한 곳은 고용보험을 빼요`() {
        val r = TakeHome.forProfile(daily(200_000, off = setOf(InsuranceLine.EMPLOYMENT)), oct, 1_000_000, free = 0)
        assertFalse(r.deductions.any { it.first == "고용보험" })
        assertTrue(r.notes.any { it.startsWith(InsuranceLine.EMPLOYMENT.label) })
    }

    @Test
    fun `the tax question offers 일용근로 to a 일당 only`() {
        fun picks(daily: Boolean) = (Questions.of(FactKey.TAX_MODE, hasPay = true, daily = daily).answer as Answer.Chips).options.map { it.second }
        assertEquals(TaxMode.DAILY_WORKER.name, picks(true).first())
        assertFalse(TaxMode.DAILY_WORKER.name in picks(false))
        val fact = com.bamnat.core.profile.Fact(FactKey.TAX_MODE, TaxMode.DAILY_WORKER, com.bamnat.core.profile.Source.TYPED, 1.0, null, null)
        assertEquals("일용근로", FactText.of(fact))
    }

    @Test
    fun `a 일용근로 profile survives the day what-if`() {
        val p = daily(200_000)
        val before = TakeHome.estimate(1_000_000, p.taxMode, p.taxFamily, day, p.taxChildren, dayRate = 200_000).net
        val after = TakeHome.estimate(1_200_000, p.taxMode, p.taxFamily, day, p.taxChildren, dayRate = 200_000).net
        // One more 20만 원 day: 20만 원 less its own 소득세 1,350원·고용보험, not the month's tax read as one day.
        assertTrue(after - before in 195_000L..200_000L)
    }
}
