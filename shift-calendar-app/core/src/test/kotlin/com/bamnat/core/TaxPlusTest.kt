package com.bamnat.core

import com.bamnat.core.money.AccountType
import com.bamnat.core.money.DailySales
import com.bamnat.core.money.MoneyAccount
import com.bamnat.core.money.MoneyEntry
import com.bamnat.core.money.MoneyKind
import com.bamnat.core.pay.YearEndPreview
import com.bamnat.core.tax.BusinessTax
import com.bamnat.core.tax.PayObservation
import com.bamnat.core.tax.TaxPlus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** F 1.32 세금·리포트 [TAXP-01]..[TAXP-11]. Amounts are made up. */
class TaxPlusTest {
    private val oct = YearMonth.of(2026, 10)

    @Test fun theSummarySaysTheBestNetItHas() {
        val dep = PayObservation(payMonth = oct, net = 2_600_000, date = LocalDate.of(2026, 10, 25), source = PayObservation.Source.DEPOSIT)
        val slip = PayObservation(payMonth = oct, net = 2_610_000, gross = 3_000_000, date = LocalDate.of(2026, 10, 31), source = PayObservation.Source.PAYSLIP)
        assertEquals(TaxPlus.NetSource.PAYSLIP, TaxPlus.netLine(oct, listOf(dep, slip), 2_900_000, null)!!.source)
        val d = TaxPlus.netLine(oct, listOf(dep), 2_900_000, null)!!
        assertEquals(TaxPlus.NetSource.DEPOSIT, d.source)
        assertEquals(300_000L, d.deducted)
        assertTrue(TaxPlus.netText(d).contains("세후 2,600,000원 (입금)"))
        assertNull(TaxPlus.netLine(oct, emptyList(), 2_900_000, null))
    }

    @Test fun theTaxIsExplainedLineByLine() {
        val steps = TaxPlus.explain(3_000_000, 1, 1, LocalDate.of(2026, 10, 1))
        assertTrue(steps.any { it.label == "국민연금" && it.how.contains("%") })
        val tax = steps.first { it.how.contains("간이세액표") || it.how.contains("줄") }
        assertTrue(tax.how, tax.how.contains("가족 1명 칸") && tax.how.contains("자녀 1명"))
    }

    @Test fun yearEndTakesCardsMedicalRentAndPension() {
        val plain = YearEndPreview.estimate(2026, 30_000_000, 1_000_000, 1_100_000, 1_000_000, 10, 1, 0)
        val more = YearEndPreview.estimate(2026, 30_000_000, 1_000_000, 1_100_000, 1_000_000, 10, 1, 0,
            YearEndPreview.Extras(credit = 12_000_000, debit = 3_000_000, medical = 2_000_000, rent = 6_000_000, pensionSavings = 3_000_000))
        assertTrue(more.cardDeduction > 0)
        assertTrue(more.pensionCredit == 450_000L)
        assertTrue(more.refund > plain.refund)
        val tipped = YearEndPreview.estimate(2026, 30_000_000, 1_000_000, 1_100_000, 1_000_000, 10, 1, 0, YearEndPreview.Extras(credit = 5_000_000))
        assertTrue(tipped.tips.any { it.text.contains("연금저축") })
        // 문턱: 총급여 3,600만 원의 25% = 900만 원을 신용카드에서 먼저 써요.
        assertEquals((3_000_000 * 0.15 + 3_000_000 * 0.30).toLong(), YearEndPreview.cardDeduction(36_000_000, YearEndPreview.Extras(credit = 12_000_000, debit = 3_000_000)))
    }

    @Test fun ledgerFillsTheExtras() {
        val cards = listOf(MoneyAccount(1, "신용", AccountType.CREDIT), MoneyAccount(2, "체크", AccountType.CHECK))
        val e = listOf(
            MoneyEntry(date = LocalDate.of(2026, 3, 1), kind = MoneyKind.EXPENSE, amount = 100_000, category = "의료", accountId = 1),
            MoneyEntry(date = LocalDate.of(2026, 3, 2), kind = MoneyKind.EXPENSE, amount = 50_000, category = "식비", accountId = 2),
            MoneyEntry(date = LocalDate.of(2026, 3, 3), kind = MoneyKind.EXPENSE, amount = 500_000, category = "주거", merchant = "3월 월세", accountId = 2),
        )
        val x = YearEndPreview.extrasFrom(e, cards, 2026, LocalDate.of(2026, 12, 31), YearEndPreview.Extras(pensionSavings = 1))
        assertEquals(100_000L, x.medical)
        assertEquals(100_000L, x.credit)
        assertEquals(50_000L, x.debit)
        assertEquals(500_000L, x.rent)
        assertEquals(1L, x.pensionSavings)
    }

    @Test fun cardSalesCutTheVatAndADaysTaxIsPutAside() {
        val p = BusinessTax.Profile(BusinessTax.VatType.GENERAL, BusinessTax.Industry.RESTAURANT)
        val end = LocalDate.of(2026, 12, 31)
        val y = BusinessTax.of(2026, end, p, 110_000_000, 40_000_000, 33_000_000, 0, cardSales = 80_000_000)
        assertEquals(1_040_000L, y.cardCredit)
        val without = BusinessTax.of(2026, end, p, 110_000_000, 40_000_000, 33_000_000, 0)
        assertEquals(without.vat - 1_040_000, y.vat)
        val (vat, income) = y.setAside(300_000)
        assertTrue(vat > 0 && income >= 0)
        val simple = BusinessTax.of(2026, end, p.copy(vat = BusinessTax.VatType.SIMPLE), 105_000_000, 0, 0, 0)
        assertNotNull(simple.lineNote)
    }

    @Test fun nextCardBillIsForecast() {
        val card = MoneyAccount(1, "신용", AccountType.CREDIT, payDay = 14)
        val today = LocalDate.of(2026, 10, 10)
        val e = (1..10).map { MoneyEntry(date = LocalDate.of(2026, 10, it), kind = MoneyKind.EXPENSE, amount = 10_000, category = "식비", accountId = 1) }
        val f = TaxPlus.cardForecast(listOf(card), e, today)
        // 결제일 14일: 11월 14일 청구는 10월 1~31일 몫이에요. 10일까지 10만 원 → 31일이면 31만 원.
        val nov = f.first { it.payDate == LocalDate.of(2026, 11, 14) || it.payDate == LocalDate.of(2026, 10, 14) }
        assertTrue(nov.projected >= nov.now)
    }

    @Test fun spendingByShift() {
        val from = LocalDate.of(2026, 10, 1); val to = LocalDate.of(2026, 10, 12)
        val e = (1..12).map { MoneyEntry(date = LocalDate.of(2026, 10, it), kind = MoneyKind.EXPENSE, amount = if (it % 2 == 0) 30_000 else 10_000, category = "식비") }
        val rows = TaxPlus.byShift(e, from, to) { d -> if (d.dayOfMonth % 2 == 0) TaxPlus.AFTER_NIGHT else "쉬는 날" }
        assertEquals(TaxPlus.AFTER_NIGHT, rows.first().label)
        assertEquals("야간 다음 날은 쉬는 날보다 하루 20,000원 더 써요", TaxPlus.shiftWords(rows))
        assertEquals(30_000L to 10_000L, TaxPlus.overtimeMonths(
            mapOf(YearMonth.of(2026, 1) to 30_000L, YearMonth.of(2026, 2) to 30_000L, YearMonth.of(2026, 3) to 10_000L, YearMonth.of(2026, 4) to 10_000L),
            mapOf(YearMonth.of(2026, 1) to 900, YearMonth.of(2026, 2) to 800, YearMonth.of(2026, 3) to 0, YearMonth.of(2026, 4) to 60)))
    }

    @Test fun salesSplitAndCardDeposits() {
        val d = LocalDate.of(2026, 10, 5)
        val sales = listOf(DailySales(d, 500_000, card = 300_000, cash = 100_000, delivery = 50_000))
        val s = TaxPlus.salesSplit(sales, d, d)
        assertEquals(50_000L, s.unsplit)
        assertEquals(60, s.share(300_000))
        val dep = MoneyEntry(date = d.plusDays(2), kind = MoneyKind.INCOME, amount = 343_000, category = "판매", merchant = "신한카드 가맹점대금")
        val m = TaxPlus.cardDeposits(sales, listOf(dep), d.plusDays(3)).single()
        assertTrue(m.matched)
        assertEquals(7_000L, m.fee)
        assertTrue(TaxPlus.cardDeposits(sales, emptyList(), d.plusDays(15)).single().late)
    }

    @Test fun yearReportAndMayPack() {
        val e = listOf(MoneyEntry(date = LocalDate.of(2026, 1, 3), kind = MoneyKind.EXPENSE, amount = 9_000, category = "식비", merchant = "김밥"))
        val doc = TaxPlus.yearDoc(2026, e, emptyList())
        assertTrue(doc.text().contains("분류별 나간 돈"))
        assertTrue(TaxPlus.yearCsv(2026, e, emptyList()).lines()[1].startsWith("2026-01-03,지출"))
        val jobs = listOf(com.bamnat.core.money.Job(date = LocalDate.of(2026, 2, 1), title = "촬영", client = "A사", amount = 1_000_000, withholding = true))
        val f = TaxPlus.freelance(2026, jobs, emptyList(), emptyList(), null)
        assertEquals(33_000L, f.withheld)
        assertTrue(TaxPlus.mayPack(f).text().contains("A사"))
        assertTrue(TaxPlus.SOURCES.any { !it.checked && TaxPlus.sourceLine(it).endsWith("(확인 필요)") })
    }
}
