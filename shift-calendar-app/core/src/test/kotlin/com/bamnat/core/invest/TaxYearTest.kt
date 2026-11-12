package com.bamnat.core.invest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 2.0.0 [RVK-32] (검토 MONEY-67·MONEY-68) Two ways the year's 세금 미리 보기 counted what it should not:
 * - 금융소득 2,000만 원 added the 배당·이자 of ISA·연금 accounts, which are not 금융소득 of the year (MONEY-67);
 * - a 해외 주식 sold on the year's last trading day was taxed in that year, though its 결제일 is in the next (MONEY-68).
 * Amounts are made up.
 */
class TaxYearTest {
    private val general = InvestAccount(id = 1, name = "주식 계좌")
    private val isa = InvestAccount(id = 2, name = "ISA", type = AccountType.ISA)
    private val pension = InvestAccount(id = 3, name = "연금저축", type = AccountType.PENSION)

    @Test fun onlyTheGeneralAccountsIncomeIsFinancialIncome() {
        val d = LocalDate.of(2026, 6, 1)
        val book = InvestBook(
            accounts = listOf(general, isa, pension),
            cash = listOf(
                CashFlow(accountId = 1, date = d, kind = CashKind.DIVIDEND, amount = 10_000_000, tax = 1_540_000, symbol = "005930"),
                CashFlow(accountId = 2, date = d, kind = CashKind.DIVIDEND, amount = 9_000_000, symbol = "005930"),
                CashFlow(accountId = 3, date = d, kind = CashKind.INTEREST, amount = 8_000_000),
                CashFlow(accountId = 1, date = d, kind = CashKind.INTEREST, amount = 1_000_000, tax = 154_000),
            ),
        )
        val t = InvestTaxes.estimate(book, Portfolio.build(book), 2026)
        assertEquals(11_000_000L, t.financialIncome)
        assertEquals(10_000_000L, t.dividendsGross)
        assertEquals(1_000_000L, t.interestGross)
        assertEquals(1_694_000L, t.dividendTax)
        // 1,100만 is not near the 2,000만 line; counted with ISA·연금 it was 2,800만 and said '넘었어요'.
        assertFalse(t.financialNear)
        assertFalse(t.financialOver)
        // The ISA's dividend is still there, in the ISA's own line.
        assertEquals(9_000_000L, t.isaGain)
    }

    @Test fun aForeignSaleCountsInTheYearItSettles() {
        assertEquals(LocalDate.of(2027, 1, 4), InvestTaxes.settleDay(LocalDate.of(2026, 12, 31), Market.US_STOCK))
        assertEquals(LocalDate.of(2026, 12, 31), InvestTaxes.settleDay(LocalDate.of(2026, 12, 30), Market.US_STOCK))
        assertEquals(LocalDate.of(2026, 12, 28), InvestTaxes.settleDay(LocalDate.of(2026, 12, 24), Market.US_STOCK))
        // T+2 before 2024-05-28.
        assertEquals(LocalDate.of(2024, 1, 3), InvestTaxes.settleDay(LocalDate.of(2023, 12, 29), Market.US_STOCK))
        // 국내 is taxed (or not) by the day it was sold.
        assertEquals(2026, InvestTaxes.taxYear(LocalDate.of(2026, 12, 31), Market.KR_STOCK))

        fun us(id: Long, side: Side, date: LocalDate, p: Double) =
            Trade(id, 1, date, symbol = "TSLA", market = Market.US_STOCK, side = side, quantity = 10.0, price = p, fxRate = 1_000.0)
        val book = InvestBook(
            accounts = listOf(general),
            trades = listOf(us(1, Side.BUY, LocalDate.of(2026, 3, 2), 100.0), us(2, Side.SELL, LocalDate.of(2026, 12, 31), 500.0)),
        )
        val r = Portfolio.build(book)
        assertEquals(0L, InvestTaxes.estimate(book, r, 2026).foreignGain)
        assertEquals(1, InvestTaxes.estimate(book, r, 2026).nextYearSales)
        assertEquals(4_000_000L, InvestTaxes.estimate(book, r, 2027).foreignGain)
        assertTrue(InvestCsv.taxReport(book, r, 2027).contains("2026-12-31"))
        assertFalse(InvestCsv.taxReport(book, r, 2026).contains("2026-12-31"))
        // The sell preview counts a last-day sale with the next year's gains too.
        val more = Trade(0, 1, LocalDate.of(2026, 12, 31), symbol = "TSLA", market = Market.US_STOCK, side = Side.SELL, quantity = 1.0, price = 500.0,
            fxRate = 1_000.0)
        val p = InvestTaxes.sellPreview(book.copy(trades = book.trades.take(1)), more)!!
        assertEquals(400_000L, p.yearGain)
    }
}
