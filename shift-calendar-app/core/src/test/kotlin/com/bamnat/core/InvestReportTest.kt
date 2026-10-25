package com.bamnat.core

import com.bamnat.core.invest.AccountType
import com.bamnat.core.invest.InvestAccount
import com.bamnat.core.invest.InvestBook
import com.bamnat.core.invest.InvestTaxes
import com.bamnat.core.invest.Market
import com.bamnat.core.invest.Side
import com.bamnat.core.invest.Trade
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** [INV-41] 해외 주식 양도소득세 신고 reminders: only with tax to report, for the coming May, never for ISA·연금. */
class InvestReportTest {
    private val general = InvestAccount(id = 1, name = "일반", type = AccountType.OVERSEAS)
    private val isa = InvestAccount(id = 2, name = "ISA", type = AccountType.ISA)

    private fun round(account: Long, year: Int, sellPrice: Double) = listOf(
        Trade(accountId = account, date = LocalDate.of(year, 3, 2), symbol = "MSFT", market = Market.US_STOCK, side = Side.BUY,
            quantity = 10.0, price = 100.0, fxRate = 1_000.0),
        Trade(accountId = account, date = LocalDate.of(year, 9, 1), symbol = "MSFT", market = Market.US_STOCK, side = Side.SELL,
            quantity = 10.0, price = sellPrice, fxRate = 1_000.0),
    )

    private fun book(vararg trades: List<Trade>) = InvestBook(accounts = listOf(general, isa), trades = trades.flatMap { it })

    @Test fun overTheDeductionRemindsOnMayFirstAndAWeekBefore() {
        // 2025: 10 × 400 USD × 1,000 = 4,000,000원 → (4,000,000 − 2,500,000) × 22% = 330,000원.
        val r = InvestTaxes.reportReminders(book(round(1, 2025, 500.0)), LocalDate.of(2026, 1, 15))
        // 2026-05-31 is a Sunday: 마감 6월 1일, a week before 5월 25일.
        assertEquals(listOf(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 25)), r.map { it.date })
        assertTrue(r.first().memo, "330,000" in r.first().memo && "6월 1일" in r.first().memo)
    }

    @Test fun underTheDeductionOrInAnIsaThereIsNothingToReport() {
        assertTrue(InvestTaxes.reportReminders(book(round(1, 2025, 300.0)), LocalDate.of(2026, 1, 15)).isEmpty()) // 2,000,000원
        assertTrue(InvestTaxes.reportReminders(book(round(2, 2025, 900.0)), LocalDate.of(2026, 1, 15)).isEmpty())
    }

    @Test fun afterTheDeadlineItIsNextMaysForThisYearsGain() {
        val trades = book(round(1, 2025, 500.0), round(1, 2026, 600.0))
        assertEquals(listOf(LocalDate.of(2027, 5, 1), LocalDate.of(2027, 5, 24)), InvestTaxes.reportReminders(trades, LocalDate.of(2026, 6, 2)).map { it.date })
        // Past 5월 1일 only the week-before one is left.
        assertEquals(listOf(LocalDate.of(2026, 5, 25)), InvestTaxes.reportReminders(trades, LocalDate.of(2026, 5, 10)).map { it.date })
    }
}
