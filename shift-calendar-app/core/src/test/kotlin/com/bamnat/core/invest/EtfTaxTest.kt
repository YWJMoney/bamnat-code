package com.bamnat.core.invest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 2.0.0 [RVK-31] (검토 MONEY-66·T06b-12·U06b-11) A Korean-listed ETF that follows a foreign index, bonds or commodities was told
 * "국내 상장주식은 … 양도소득세가 없어요" when sold, and its gain stayed out of 금융소득. Only 국내주식형 ETF gains are untaxed; the
 * others lose 15.4% (배당소득세) at the broker and count toward the 2,000만 원 line. Each market's sale now says its own line.
 * Amounts are made up.
 */
class EtfTaxTest {
    private val d = LocalDate.of(2026, 3, 2)
    private fun t(id: Long, symbol: String, market: Market, side: Side, q: Double, p: Double, day: Int, account: Long = 1) =
        Trade(id = id, accountId = account, date = d.plusDays(day.toLong()), symbol = symbol, name = symbol, market = market, side = side,
            quantity = q, price = p)

    private val trades = listOf(
        t(1, "360750", Market.KR_ETF, Side.BUY, 100.0, 10_000.0, 0), t(2, "360750", Market.KR_ETF, Side.SELL, 100.0, 20_000.0, 10),
        t(3, "069500", Market.KR_ETF, Side.BUY, 10.0, 30_000.0, 0), t(4, "069500", Market.KR_ETF, Side.SELL, 10.0, 80_000.0, 10),
        t(5, "005930", Market.KR_STOCK, Side.BUY, 10.0, 60_000.0, 0), t(6, "005930", Market.KR_STOCK, Side.SELL, 10.0, 80_000.0, 10),
        t(7, "GOLD", Market.OTHER, Side.BUY, 1.0, 1_000_000.0, 0), t(8, "GOLD", Market.OTHER, Side.SELL, 1.0, 1_300_000.0, 10),
    )

    private fun book(kinds: Map<String, Boolean>) = InvestBook(
        accounts = listOf(InvestAccount(id = 1, name = "주식 계좌"), InvestAccount(id = 2, name = "ISA", type = AccountType.ISA)),
        trades = trades,
        cash = listOf(CashFlow(accountId = 1, date = d, kind = CashKind.DEPOSIT, amount = 10_000_000)),
        settings = InvestSettings(etfTaxed = kinds),
    )

    @Test fun aForeignIndexEtfSaleIsNotToldItOwesNothing() {
        val sale = trades[1]
        val unknown = InvestTaxes.sellLine(book(emptyMap()), sale, gainWon = 1_000_000)!!
        assertTrue(unknown, "국내주식형" in unknown && "15.4%" in unknown)
        assertFalse(unknown, "양도소득세가 없어요" in unknown)
        val other = InvestTaxes.sellLine(book(mapOf("360750" to true)), sale, gainWon = 1_000_000)!!
        assertTrue(other, "154,000원" in other && "15.4%" in other)
        val equity = InvestTaxes.sellLine(book(mapOf("360750" to false)), sale, gainWon = 1_000_000)!!
        assertTrue(equity, "세금이 없어요" in equity)
        assertFalse(equity, "15.4%" in equity)
    }

    @Test fun everyMarketSaysItsOwnLine() {
        val b = book(emptyMap())
        assertEquals(InvestTaxes.DOMESTIC_NOTE, InvestTaxes.sellLine(b, trades[5], 200_000))
        assertTrue(InvestTaxes.sellLine(b, trades[7], 300_000)!!.contains("증권사"))
        val coin = InvestTaxes.sellLine(b, t(9, "BTC", Market.CRYPTO, Side.SELL, 1.0, 1.0, 10), 0)!!
        assertTrue(coin, "${InvestTaxes.CRYPTO_FROM_YEAR}년부터" in coin)
        // 해외 주식 has its own preview of the year's 양도소득세.
        assertNull(InvestTaxes.sellLine(b, t(10, "TSLA", Market.US_STOCK, Side.SELL, 1.0, 1.0, 10), 0))
        // In an ISA the account decides, whatever the market.
        assertTrue(InvestTaxes.sellLine(b, trades[1].copy(accountId = 2), 1_000_000)!!.contains("ISA"))
    }

    @Test fun theYearsEstimateCountsTheTaxedEtfAsFinancialIncome() {
        val b = book(mapOf("360750" to true, "069500" to false))
        val t = InvestTaxes.estimate(b, Portfolio.build(b), 2026)
        assertEquals(1_000_000L, t.otherEtfGain)
        assertEquals(154_000L, t.otherEtfTax)
        assertEquals(1_000_000L, t.financialIncome)
        // 국내 주식 200,000 + 국내주식형 ETF 500,000; 기타 시장 is said apart.
        assertEquals(700_000L, t.domesticGain)
        assertEquals(300_000L, t.otherMarketGain)
        assertEquals(0, t.unknownEtfs)
        // Not told yet: counted as before (국내), and said so.
        val u = InvestTaxes.estimate(book(emptyMap()), Portfolio.build(book(emptyMap())), 2026)
        assertEquals(2, u.unknownEtfs)
        assertEquals(0L, u.otherEtfGain)
    }
}
