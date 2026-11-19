package com.bamnat.core.pay

import com.bamnat.core.tax.TaxLearner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/**
 * 2.0.0 [RVJ-81] 결정 5 (처리방침 3.14 13번): a 생년 alone takes 국민연금 off from the year after the worker turns 60 (국민연금법 제6조·
 * 제12조 ①·제17조 ①) and 고용보험 off at a place first worked at 65 or later (고용보험법 제10조 ②, 보험료징수법 제13조 ③); a year it
 * cannot tell is said, not guessed; '그래도 내요' keeps a line.
 */
class InsuranceAgeTest {
    private val nov = YearMonth.of(2026, 11)

    private fun pay(born: Int?, hired: LocalDate? = null, keep: Set<InsuranceLine> = emptySet(), off: Set<InsuranceLine> = emptySet()) = PayProfile(
        occupation = Occupation.CUSTOM, wageType = WageType.MONTHLY,
        base = ValueTimeline(listOf(DatedValue(LocalDate.of(2026, 1, 1), 3_000_000))), taxMode = TaxMode.INSURANCE_4, taxChosen = true,
        birthYear = born, hireDate = hired, insuranceKeep = keep, insuranceOff = off,
    )

    @Test
    fun `no 생년, nothing changes`() {
        assertTrue(InsuranceAge.lines(pay(null), nov).isEmpty())
        assertEquals(TakeHome.forProfile(pay(null), nov, 3_000_000, free = 0), TakeHome.forProfile(pay(null).copy(birthYear = null), nov, 3_000_000, free = 0))
    }

    @Test
    fun `국민연금 stops the year after turning 60, and the year of turning 60 is said`() {
        assertTrue(InsuranceAge.lines(pay(1967), nov).isEmpty())
        val turning = InsuranceAge.lines(pay(1966), nov).single()
        assertEquals(InsuranceLine.PENSION, turning.line)
        assertFalse(turning.off)
        val r60 = TakeHome.forProfile(pay(1966), nov, 3_000_000, free = 0)
        assertTrue("60세가 되는 해는 떼요", r60.deductions.any { it.first == "국민연금" })
        assertTrue(r60.notes.any { it.startsWith("올해 만 60세가 돼요") })

        val after = TakeHome.forProfile(pay(1965), nov, 3_000_000, free = 0)
        assertFalse(after.deductions.any { it.first == "국민연금" })
        assertTrue(after.notes.contains("만 60세가 지나 국민연금을 떼지 않아요 (생년 1965년)"))
        assertEquals(TakeHome.forProfile(pay(1967, off = setOf(InsuranceLine.PENSION)), nov, 3_000_000, free = 0).net, after.net)
    }

    @Test
    fun `고용보험 is off at a place first worked at 65 or later, not at one kept from before 65`() {
        val late = pay(1958, hired = LocalDate.of(2025, 3, 2))
        assertFalse(TakeHome.forProfile(late, nov, 3_000_000, free = 0).deductions.any { it.first == "고용보험" })
        val kept = pay(1958, hired = LocalDate.of(2020, 3, 2))
        assertTrue(TakeHome.forProfile(kept, nov, 3_000_000, free = 0).deductions.any { it.first == "고용보험" })
        // 입사한 해에 65세가 된 분, 입사일이 없는 분: said, still taken.
        val edge = TakeHome.forProfile(pay(1958, hired = LocalDate.of(2023, 6, 1)), nov, 3_000_000, free = 0)
        assertTrue(edge.deductions.any { it.first == "고용보험" })
        assertTrue(edge.notes.any { it.startsWith("입사한 해에 만 65세가 됐어요") })
        val noDate = TakeHome.forProfile(pay(1958), nov, 3_000_000, free = 0)
        assertTrue(noDate.deductions.any { it.first == "고용보험" })
        assertTrue(noDate.notes.any { it.startsWith("입사일을 적으면") })
        // 현장 일용근로 takes 고용보험 too, so the same rule holds there.
        val daily = late.copy(taxMode = TaxMode.DAILY_WORKER)
        assertFalse(TakeHome.forProfile(daily, nov, 1_000_000, free = 0).deductions.any { it.first == "고용보험" })
    }

    @Test
    fun `그래도 내요 keeps the line, and the chip toggles between the three`() {
        val p = pay(1964)
        val kept = InsuranceAge.toggle(p, InsuranceLine.PENSION, nov)
        assertEquals(setOf(InsuranceLine.PENSION), kept.insuranceKeep)
        assertTrue(TakeHome.forProfile(kept, nov, 3_000_000, free = 0).deductions.any { it.first == "국민연금" })
        assertEquals(p, InsuranceAge.toggle(kept, InsuranceLine.PENSION, nov))
        // A line chosen by hand and by age: one press takes it back on.
        val both = pay(1964, off = setOf(InsuranceLine.PENSION))
        val on = InsuranceAge.toggle(both, InsuranceLine.PENSION, nov)
        assertTrue(InsuranceLine.PENSION !in on.insuranceOffOn(nov))
        // A line the age does not touch is the hand's.
        assertEquals(setOf(InsuranceLine.HEALTH), InsuranceAge.toggle(p, InsuranceLine.HEALTH, nov).insuranceOff)
    }

    @Test
    fun `the learner's table and the payslip check leave the same lines out`() {
        val p = pay(1964)
        val r = TakeHome.forProfile(p, nov, 3_000_000, free = 0)
        assertEquals(r.deductions.sumOf { it.second }, TaxLearner.Table.of(p).deductions(3_000_000, nov))
        assertEquals(setOf(InsuranceLine.PENSION), p.insuranceOffOn(nov))
        assertEquals(emptySet<InsuranceLine>(), p.insuranceOffOn(YearMonth.of(2024, 5)))
    }

    @Test
    fun `the field takes years 14 to 100 back`() {
        assertTrue(InsuranceAge.valid(1965, 2026))
        assertFalse(InsuranceAge.valid(2013, 2026))
        assertFalse(InsuranceAge.valid(1925, 2026))
    }
}
