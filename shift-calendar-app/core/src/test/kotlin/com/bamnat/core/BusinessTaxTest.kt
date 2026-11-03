package com.bamnat.core

import com.bamnat.core.invest.AccountType
import com.bamnat.core.invest.InvestAccount
import com.bamnat.core.invest.InvestBook
import com.bamnat.core.invest.InvestFees
import com.bamnat.core.invest.InvestSettings
import com.bamnat.core.invest.InvestTaxes
import com.bamnat.core.invest.Market
import com.bamnat.core.invest.Side
import com.bamnat.core.invest.Trade
import com.bamnat.core.money.DailySales
import com.bamnat.core.money.Job
import com.bamnat.core.money.MoneyEntry
import com.bamnat.core.money.MoneyKind
import com.bamnat.core.tax.BusinessTax
import com.bamnat.core.tax.BusinessTax.Industry
import com.bamnat.core.tax.BusinessTax.Method
import com.bamnat.core.tax.BusinessTax.Profile
import com.bamnat.core.tax.BusinessTax.VatType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * [TAX-05][TAX-06] 1.25 W 382 골든 사례 (research/1.25-W-조사.md 2장, 금액은 손으로 계산), [INV-38][INV-39] 383.
 * 경비율 61.7·17.5 등은 사례용 숫자예요 (실제 값은 업종코드마다 홈택스, 결정 W-D3).
 */
class BusinessTaxTest {
    private val end = LocalDate.of(2026, 12, 31)

    private fun year(p: Profile, sales: Long, costs: Long, proved: Long, withheld: Long = 0) =
        BusinessTax.of(2026, end, p, sales, costs, proved, withheld)

    @Test fun simpleTaxpayerUnder48MillionPaysNoVat() {
        // 간이 음식점 매출 4,000만: 부가세 면제(법 제69조). 장부 소득 2,000만 − 기본공제 150만 = 1,850만
        // → 84만 + 450만×15% = 151.5만 − 표준세액공제 7만 = 144.5만 + 지방 14.45만.
        val y = year(Profile(VatType.SIMPLE, Industry.RESTAURANT), 40_000_000, 20_000_000, 10_000_000)
        assertEquals(0, y.vat)
        assertTrue(y.vatExempt)
        assertEquals(Method.BOOK, y.best.method)
        assertEquals(1_589_500, y.incomeTaxDue)
        assertEquals((1_589_500L / 12) / 100 * 100, y.perMonth)
    }

    @Test fun simpleTaxpayerOverTheLine() {
        // 간이 음식점 매출 8,000만: 8,000만 × 15% × 10% = 120만 − 증빙 매입 3,000만 × 0.5% = 15만 → 105만.
        val y = year(Profile(VatType.SIMPLE, Industry.RESTAURANT), 80_000_000, 50_000_000, 30_000_000)
        assertEquals(1_050_000, y.vat)
        assertFalse(y.vatExempt)
        // 소득 3,000만 − 150만 = 2,850만 → 84만 + 1,450만 × 15% = 301.5만 − 7만 = 294.5만 × 1.1.
        assertEquals(3_239_500, y.incomeTaxDue)
    }

    @Test fun generalTaxpayerVatAndIncomeWithoutVat() {
        // 일반 소매 매출 1.1억(부가세 포함): 매출세액 1,000만 − 증빙 매입 5,500만의 1/11 = 500만 → 500만.
        // 수입금액 1억, 경비 6,600만 − 매입세액 500만 = 6,100만 → 소득 3,900만 − 150만 = 3,750만
        // → 84만 + 2,350만 × 15% = 436.5만 − 7만 = 429.5만 × 1.1 = 472.45만.
        val y = year(Profile(VatType.GENERAL, Industry.RETAIL), 110_000_000, 66_000_000, 55_000_000)
        assertEquals(5_000_000, y.vat)
        assertEquals(4_724_500, y.incomeTaxDue)
        assertEquals(2, y.dues.count { it.name.contains("확정") && it.name.contains("부가세") })
    }

    @Test fun simpleExpenseRateWinsAndTheWithheldComesBack() {
        // 면세 강사, 매출 2,000만, 직전 연도 1,500만 (<2,400만): 단순경비율 61.7% → 소득 766만 − 150만 = 616만
        // × 6% = 36.96만 − 7만 = 29.96만 × 1.1 = 32.956만. 3.3%로 66만을 이미 냈으니 33.044만을 돌려받아요.
        val p = Profile(VatType.EXEMPT, Industry.EDUCATION, simpleRate = 61.7, lastYearIncome = 15_000_000)
        val y = year(p, 20_000_000, 1_000_000, 0, withheld = 660_000)
        assertEquals(Method.SIMPLE, y.best.method)
        assertEquals(329_560, y.best.tax)
        assertEquals(329_560 - 660_000, y.incomeTaxDue)
        assertEquals(0, y.total)
        assertEquals(1_424_500, y.options.first { it.method == Method.BOOK }.tax)
        // 1.25 통합: the 5월 확정신고 says what comes back instead of showing 0 alone.
        val may = y.dues.single { it.name == "종합소득세 확정신고" }
        assertEquals(0L, may.amount)
        assertTrue(may.note, may.note.contains("330,440원을 돌려받아요"))
    }

    @Test fun standardExpenseRateWhenSimpleIsNotAllowed() {
        // 직전 연도 3,000만 (C 묶음 2,400만 이상): 단순경비율 못 씀. 기준경비율 17.5% → 2,000만 − 350만 = 1,650만
        // − 150만 = 1,500만 → 84만 + 100만 × 15% = 99만 − 7만 = 92만 × 1.1 = 101.2만.
        val p = Profile(VatType.EXEMPT, Industry.EDUCATION, simpleRate = 61.7, standardRate = 17.5, lastYearIncome = 30_000_000)
        val y = year(p, 20_000_000, 1_000_000, 0)
        assertFalse(y.options.first { it.method == Method.SIMPLE }.allowed)
        assertEquals(Method.STANDARD, y.best.method)
        assertEquals(1_012_000, y.best.tax)
    }

    @Test fun standardIncomeIsCappedAtSimpleTimesTwoPointEight() {
        // 시행규칙 제67조: 기준경비율 소득 1,900만 > 단순경비율 소득 400만 × 2.8 = 1,120만 → 1,120만.
        // − 150만 = 970만 × 6% = 58.2만 − 7만 = 51.2만 × 1.1 = 56.32만.
        val p = Profile(VatType.EXEMPT, Industry.EDUCATION, simpleRate = 80.0, standardRate = 5.0, lastYearIncome = 30_000_000)
        val y = year(p, 20_000_000, 1_000_000, 0)
        assertEquals(11_200_000, y.options.first { it.method == Method.STANDARD }.income)
        assertEquals(563_200, y.best.tax)
    }

    @Test fun incomeTaxBracketsAsArticle55() {
        assertEquals(840_000, BusinessTax.rate(14_000_000))
        assertEquals(6_240_000, BusinessTax.rate(50_000_000))
        assertEquals(15_360_000, BusinessTax.rate(88_000_000))
        assertEquals(37_060_000, BusinessTax.rate(150_000_000))
        assertEquals(384_060_000, BusinessTax.rate(1_000_000_000))
    }

    @Test fun duesMoveOffWeekendsAndTakeOffTheNovemberPrepayment() {
        val y = year(Profile(VatType.GENERAL, Industry.RETAIL, lastYearTax = 2_000_000), 110_000_000, 66_000_000, 55_000_000)
        val dates = y.dues.map { it.date }
        // 2026-04-25·07-25 are Saturdays.
        assertTrue(LocalDate.of(2026, 4, 27) in dates)
        assertTrue(LocalDate.of(2026, 7, 27) in dates)
        assertTrue(LocalDate.of(2026, 11, 30) in dates)
        assertTrue(LocalDate.of(2027, 1, 25) in dates)
        val nov = y.dues.first { it.name == "종합소득세 중간예납" }
        assertEquals(1_000_000L, nov.amount)
        assertEquals(4_724_500L - 1_000_000L, y.dues.first { it.name == "종합소득세 확정신고" }.amount)
    }

    @Test fun theYearIsProjectedFromTheDaysGone() {
        val until = LocalDate.of(2026, 3, 31)
        val y = BusinessTax.of(2026, until, Profile(VatType.EXEMPT), 9_000_000, 0, 0, 0)
        assertEquals(Math.round(9_000_000 * 365.0 / 90), y.projectedSales)
    }

    /** 1.36 실사용 S16: a 3.3% job is a 인적용역's pay, free of 부가세, even with the default 일반과세. */
    @Test fun withheldJobsCarryNoVat() {
        val jobs = listOf(Job(date = LocalDate.of(2026, 11, 30), title = "도배", amount = 900_000, withholding = true))
        val y = BusinessTax.year(2026, LocalDate.of(2026, 12, 6), Profile(), jobs, emptyList(), emptyList())
        assertEquals(0, y.vat)
        assertTrue(y.dues.none { it.name.contains("부가세") || it.name.contains("사업장현황") })
        assertTrue(y.dues.any { it.name == "종합소득세 확정신고" })
        // A shop day beside it still carries its 부가세, the job's pay left out.
        val mixed = BusinessTax.year(2026, LocalDate.of(2026, 12, 31), Profile(), jobs, listOf(DailySales(LocalDate.of(2026, 5, 1), 11_000_000, 0)), emptyList())
        assertEquals(1_000_000, mixed.vat)
    }

    @Test fun fromTheLedgerShopDaysAndJobs() {
        val today = LocalDate.of(2026, 12, 31)
        val sales = listOf(DailySales(LocalDate.of(2026, 5, 1), 30_000_000, 5_000_000))
        val jobs = listOf(Job(date = LocalDate.of(2026, 6, 1), title = "시공", amount = 10_000_000, withholding = true, costs = 2_000_000))
        val entries = listOf(
            MoneyEntry(date = LocalDate.of(2026, 6, 3), kind = MoneyKind.EXPENSE, amount = 1_100_000, category = "재료", proof = "세금계산서"),
            MoneyEntry(date = LocalDate.of(2026, 6, 4), kind = MoneyKind.EXPENSE, amount = 50_000, category = "식비", proof = "간이영수증"),
        )
        val y = BusinessTax.year(2026, today, Profile(VatType.SIMPLE, Industry.CONSTRUCTION), jobs, sales, entries)
        assertEquals(40_000_000, y.sales)
        assertEquals(5_000_000 + 2_000_000 + 1_150_000, y.costs)
        assertEquals(1_100_000, y.proved)
        assertEquals(330_000, y.withheld)
        // 간이 4,800만 미만: 부가세 면제.
        assertTrue(y.vatExempt)
    }

    // --- 투자 (INV-38·INV-39) ---

    @Test fun calendarDefaultFeeIsPointZeroFiveAndAnAccountsOwnFeeWins() {
        val s = InvestSettings()
        assertEquals(0.05, s.buyFeeRate, 0.0)
        // 삼성전자 10주 × 70,000원 매도: 수수료 0.05% = 350원, 거래세 0.20% = 1,400원.
        val sell = InvestFees.suggest(Market.KR_STOCK, Side.SELL, 10.0, 70_000.0, null, s)
        assertEquals(350.0, sell.fee, 0.0)
        assertEquals(1_400.0, sell.tax, 0.0)
        val changed = s.copy(sellFeeRate = 0.015)
        assertEquals(105.0, InvestFees.suggest(Market.KR_STOCK, Side.SELL, 10.0, 70_000.0, null, changed).fee, 0.0)
        assertEquals(350.0, InvestFees.suggest(Market.KR_STOCK, Side.BUY, 10.0, 70_000.0, null, changed).fee, 0.0)
        val own = InvestAccount(id = 1, name = "증권", feeRate = 0.1)
        assertEquals(700.0, InvestFees.suggest(Market.KR_STOCK, Side.SELL, 10.0, 70_000.0, own, changed).fee, 0.0)
    }

    @Test fun aForeignSellShowsTheTaxItAdds() {
        val acc = InvestAccount(id = 1, name = "해외", type = AccountType.OVERSEAS)
        val buy = Trade(id = 1, accountId = 1, date = LocalDate.of(2026, 2, 1), symbol = "AAPL", market = Market.US_STOCK, side = Side.BUY,
            quantity = 10.0, price = 100.0, fxRate = 1_400.0)
        val book = InvestBook(accounts = listOf(acc), trades = listOf(buy))
        // 10주를 300달러에 팔면 (300−100)×10×1,400 = 280만 원 이익 → 250만 공제 뒤 30만 × 22% = 6.6만.
        val sell = Trade(accountId = 1, date = LocalDate.of(2026, 10, 1), symbol = "AAPL", market = Market.US_STOCK, side = Side.SELL,
            quantity = 10.0, price = 300.0, fxRate = 1_400.0)
        val p = InvestTaxes.sellPreview(book, sell)!!
        assertEquals(2_800_000, p.gain)
        assertEquals(0, p.taxBefore)
        assertEquals(66_000, p.extraTax)
        assertEquals(0, p.deductionLeft)
        assertNull(InvestTaxes.sellPreview(book, sell.copy(market = Market.KR_STOCK)))
        val isa = book.copy(accounts = listOf(acc.copy(type = AccountType.ISA)))
        assertNull(InvestTaxes.sellPreview(isa, sell))
    }

    @Test fun fixingATradeWorksTheCostsOutAgainOnceThePriceChanges() {
        val s = InvestSettings()
        // A 체결 알림 trade saved with no price and no costs: typing the price fills them (사용자 U17).
        val fromAlert = Trade(id = 7, accountId = 1, date = LocalDate.of(2026, 10, 1), symbol = "005930", market = Market.KR_STOCK, side = Side.SELL,
            quantity = 10.0, price = 0.0)
        val now = InvestFees.suggest(Market.KR_STOCK, Side.SELL, 10.0, 70_000.0, null, s)
        val shown = InvestFees.shown(fromAlert, null, Market.KR_STOCK, Side.SELL, 10.0, 70_000.0, 1, now)
        assertEquals(350.0, shown.fee, 0.0)
        assertEquals(1_400.0, shown.tax, 0.0)
        // A trade opened as it was keeps its own costs; changing the quantity works them out again.
        val saved = fromAlert.copy(price = 70_000.0, fee = 300.0, tax = 1_400.0)
        assertEquals(300.0, InvestFees.shown(saved, null, Market.KR_STOCK, Side.SELL, 10.0, 70_000.0, 1, now).fee, 0.0)
        val more = InvestFees.suggest(Market.KR_STOCK, Side.SELL, 20.0, 70_000.0, null, s)
        assertEquals(700.0, InvestFees.shown(saved, null, Market.KR_STOCK, Side.SELL, 20.0, 70_000.0, 1, more).fee, 0.0)
        // What the user typed in the sheet wins.
        val typed = InvestFees.Suggestion(1.0, 2.0)
        assertEquals(typed, InvestFees.shown(saved, typed, Market.KR_STOCK, Side.SELL, 20.0, 70_000.0, 1, more))
    }
}
