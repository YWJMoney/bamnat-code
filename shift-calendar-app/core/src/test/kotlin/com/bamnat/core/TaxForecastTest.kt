package com.bamnat.core

import com.bamnat.core.money.MoneyEntry
import com.bamnat.core.money.MoneyKind
import com.bamnat.core.pay.TakeHome
import com.bamnat.core.pay.TaxMode
import com.bamnat.core.tax.IncomeKind
import com.bamnat.core.tax.PayObservation
import com.bamnat.core.tax.SalaryDeposits
import com.bamnat.core.tax.TaxLearner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.roundToLong

/** [TAX-01][TAX-02][TAX-04] 1.25 W 380·381: 월급 입금을 관측으로, 실수령 학습 (research/1.25-W-조사.md 1장). */
class TaxForecastTest {
    private val table = TaxLearner.Table(TaxMode.INSURANCE_4)

    private fun obs(month: YearMonth, gross: Long, net: Long, kind: IncomeKind = IncomeKind.SALARY) =
        PayObservation(payMonth = month, gross = gross, net = net, date = month.atEndOfMonth(), kind = kind)

    /** A record whose deductions are the table's × [k]. */
    private fun scaled(month: YearMonth, gross: Long, k: Double) =
        obs(month, gross, gross - (table.deductions(gross, month) * k).roundToLong())

    @Test fun withoutRecordsTheTableItself() {
        val fit = TaxLearner.fit(emptyList(), table)
        assertEquals(TaxLearner.Basis.TABLE, fit.basis)
        val m = YearMonth.of(2026, 11)
        val f = TaxLearner.forecast(m, 3_000_000, fit, table)
        assertEquals(TakeHome.estimate(3_000_000, TaxMode.INSURANCE_4, on = m.atDay(1)).net, f.net)
        assertEquals(f.tableNet, f.net)
    }

    @Test fun learnsThePayslipsOwnDeductionsAndFollowsTheTableUp() {
        // The employer takes 10% more than the table (120% 원천징수, 노조비 등) for three months.
        val records = listOf(
            scaled(YearMonth.of(2026, 7), 3_000_000, 1.10),
            scaled(YearMonth.of(2026, 8), 3_200_000, 1.10),
            scaled(YearMonth.of(2026, 9), 2_900_000, 1.10),
        )
        val fit = TaxLearner.fit(records, table)
        assertEquals(TaxLearner.Basis.LEARNED, fit.basis)
        assertEquals(1.10, fit.k, 0.01)
        assertEquals(3, fit.used.size)
        assertTrue(fit.words, fit.words.contains("표보다 10% 더 떼요"))
        // A month with more 연장·야간: the table's 누진 deductions × k, not a flat rate.
        val m = YearMonth.of(2026, 10)
        val f = TaxLearner.forecast(m, 4_500_000, fit, table)
        val expected = 4_500_000 - (table.deductions(4_500_000, m) * fit.k).roundToLong() / 10 * 10
        assertEquals(expected, f.net)
        assertTrue("배운 공제가 표보다 커요", f.net < f.tableNet)
    }

    @Test fun yearEndSettlementMonthIsLeftOut() {
        val records = listOf(
            scaled(YearMonth.of(2026, 1), 3_000_000, 1.0),
            // 2월 연말정산 추징: twice the usual.
            scaled(YearMonth.of(2026, 2), 3_000_000, 2.2),
            scaled(YearMonth.of(2026, 3), 3_000_000, 1.02),
            scaled(YearMonth.of(2026, 4), 3_000_000, 0.98),
        )
        val fit = TaxLearner.fit(records, table)
        assertEquals(1, fit.left.size)
        assertEquals(YearMonth.of(2026, 2), fit.left.single().observation.payMonth)
        assertEquals(1.0, fit.k, 0.03)
        assertTrue(fit.words.contains("1달은 뺐어요"))
    }

    @Test fun aDepositWithoutGrossUsesThePayEnginesGross() {
        val m = YearMonth.of(2026, 9)
        val gross = 3_100_000L
        val deposit = PayObservation(payMonth = m, net = gross - (table.deductions(gross, m) * 1.2).roundToLong(), date = LocalDate.of(2026, 10, 10),
            source = PayObservation.Source.DEPOSIT)
        val fit = TaxLearner.fit(listOf(deposit), table) { if (it == m) gross else null }
        assertEquals(1.2, fit.k, 0.01)
        // Without the engine's gross the record is shown but not used.
        val blind = TaxLearner.fit(listOf(deposit), table)
        assertEquals(TaxLearner.Basis.TABLE, blind.basis)
        assertEquals("세전을 몰라요", blind.samples.single().why)
    }

    @Test fun theRecordsTellTheModeWhenThePaySetupSaysNone() {
        val none = TaxLearner.Table(TaxMode.NONE)
        val m = YearMonth.of(2026, 9)
        val freelance = TaxLearner.fit(listOf(obs(m, 2_000_000, TaxLearner.freelanceNet(2_000_000))), none)
        assertEquals(TaxMode.WITHHOLDING_3_3, freelance.mode)
        assertTrue(freelance.guessedMode)
        assertEquals(1_934_000, TaxLearner.forecast(m, 2_000_000, freelance, none).net)
        val insured = TaxLearner.fit(listOf(scaled(m, 3_000_000, 1.0)), none)
        assertEquals(TaxMode.INSURANCE_4, insured.mode)
        assertEquals(TaxLearner.Basis.LEARNED, insured.basis)
        val untaxed = TaxLearner.fit(listOf(obs(m, 1_200_000, 1_200_000)), none)
        assertEquals(TaxMode.NONE, untaxed.mode)
        assertEquals(1_200_000, TaxLearner.forecast(m, 1_200_000, untaxed, none).net)
    }

    @Test fun dailyWorkersWithholdingByLaw() {
        // (일당 − 15만) × 6% × 45%, 1,000원 미만은 안 떼요, 지방소득세 10%.
        assertEquals(0, TaxLearner.dailyWithheld(150_000))
        assertEquals(0, TaxLearner.dailyWithheld(180_000))
        assertEquals(1_080 + 100, TaxLearner.dailyWithheld(190_000))
        assertEquals(1_350 + 130, TaxLearner.dailyWithheld(200_000))
    }

    @Test fun yearSoFarSplitsTaxAndInsurance() {
        val records = (7..9).map { scaled(YearMonth.of(2026, it), 3_000_000, 1.0) }
        val fit = TaxLearner.fit(records, table)
        val y = TaxLearner.yearSoFar(2026, fit, table)
        assertEquals(3, y.months)
        assertEquals(y.deducted, y.tax + y.insurance)
        assertTrue("4대 보험이 세금보다 커요 (300만 원 월급)", y.insurance > y.tax)
    }

    // --- 월급 입금 알아보기 (TAX-01) ---

    private val payDay = LocalDate.of(2026, 11, 10)
    private val expected = listOf(SalaryDeposits.Expected(YearMonth.of(2026, 10), payDay, 2_980_000))

    private fun deposit(amount: Long, date: LocalDate = payDay, merchant: String = "(주)밤낮병원", category: String = "부수입") =
        MoneyEntry(date = date, kind = MoneyKind.INCOME, amount = amount, category = category, merchant = merchant)

    @Test fun aBankDepositNearThePayDayForAboutTheTakeHomeIsPay() {
        val seen = SalaryDeposits.seen(deposit(3_012_400), expected, emptySet())!!
        assertEquals(SalaryDeposits.Why.LOOKS, seen.why)
        assertEquals(YearMonth.of(2026, 10), seen.payMonth)
        // Small money, far from the pay day, or far from the amount: not pay.
        assertNull(SalaryDeposits.seen(deposit(120_000), expected, emptySet()))
        assertNull(SalaryDeposits.seen(deposit(3_000_000, payDay.plusDays(12)), expected, emptySet()))
        assertNull(SalaryDeposits.seen(deposit(5_000_000), expected, emptySet()))
    }

    @Test fun aConfirmedEmployerOrTheSalaryCategoryIsAlwaysPay() {
        assertEquals(SalaryDeposits.Why.PAYER, SalaryDeposits.seen(deposit(1_000_000, payDay.plusDays(12), "밤낮 병원"), expected, setOf("㈜밤낮병원"))!!.why)
        assertEquals(SalaryDeposits.Why.FILED, SalaryDeposits.seen(deposit(1_000_000, category = "급여"), emptyList(), emptySet())!!.why)
        assertFalse(SalaryDeposits.seen(deposit(3_000_000).copy(kind = MoneyKind.EXPENSE), expected, emptySet()) != null)
        assertEquals("밤낮병원", SalaryDeposits.key("(주) 밤낮 병원"))
    }
}
