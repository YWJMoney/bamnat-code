package com.bamnat.core

import com.bamnat.core.invest.AccountType
import com.bamnat.core.invest.InvestAccount
import com.bamnat.core.invest.InvestBook
import com.bamnat.core.invest.InvestSettings
import com.bamnat.core.invest.InvestTaxes
import com.bamnat.core.invest.Market
import com.bamnat.core.invest.Portfolio
import com.bamnat.core.invest.Quote
import com.bamnat.core.invest.Side
import com.bamnat.core.invest.Trade
import com.bamnat.core.notify.DateNotices
import com.bamnat.core.work.WishOff
import com.bamnat.core.work.WorkStyle
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 1.32 [WTYP-11][WTYP-29] 희망 오프와 마감 알림, 연말 손실 정리 미리 보기, 코인 12월 31일 알림. */
class DateNoticesTest {
    @Test fun wishOffDaysToggleAndListPerMonth() {
        val nov3 = LocalDate.of(2026, 11, 3)
        var w = WishOff(deadlineDay = 20).toggle(nov3).toggle(LocalDate.of(2026, 11, 7)).toggle(LocalDate.of(2026, 12, 1))
        assertEquals(listOf(nov3, LocalDate.of(2026, 11, 7)), w.picked(YearMonth.of(2026, 11)))
        w = w.toggle(nov3)
        assertFalse(w.has(nov3))
        assertEquals("11월 희망 오프: 7일(토)", WishOff.copyText(YearMonth.of(2026, 11), w.picked(YearMonth.of(2026, 11))))
        assertTrue(WishOff.offered(WorkStyle.SHIFT))
        assertFalse(WishOff.offered(WorkStyle.HOURLY))
        // Long past days are let go.
        assertTrue(w.prune(LocalDate.of(2027, 6, 1)).days.isEmpty())
    }

    @Test fun wishOffNoticesComeTwoDaysBeforeAndOnTheDeadline() {
        val now = LocalDateTime.of(2026, 10, 10, 12, 0)
        val n = DateNotices.wishOff("main", "", 20, { m -> if (m == YearMonth.of(2026, 11)) 3 else 0 }, now)
        assertEquals(2, n.size)
        assertEquals(LocalDateTime.of(2026, 10, 18, 9, 0), n[0].at)
        assertEquals("11월 희망 오프 마감이 모레예요", n[0].title)
        assertTrue(n[0].text.startsWith("고른 날 3일"))
        assertEquals(LocalDateTime.of(2026, 10, 20, 9, 0), n[1].at)
        // After this month's deadline: the next month's; a 31 in a short month is its last day.
        val later = DateNotices.wishOff("main", "병동", 31, { 0 }, LocalDateTime.of(2026, 11, 30, 10, 0))
        assertEquals(LocalDateTime.of(2026, 11, 30, 9, 0).plusDays(29), later[0].at)
        assertTrue(later[0].title.startsWith("병동 · "))
        assertTrue(DateNotices.wishOff("main", "", null, { 0 }, now).isEmpty())
        val all = n + DateNotices.coinYearEnd("inv", 2, now)!!
        assertEquals(listOf(n[0]), DateNotices.next(all, now))
    }

    @Test fun coinYearEndOnlyWithCoinsAndOnTheLastDay() {
        assertNull(DateNotices.coinYearEnd("inv", 0, LocalDateTime.of(2026, 10, 1, 0, 0)))
        assertEquals(LocalDateTime.of(2026, 12, 31, 9, 0), DateNotices.coinYearEnd("inv", 1, LocalDateTime.of(2026, 10, 1, 0, 0))!!.at)
        assertEquals(LocalDateTime.of(2027, 12, 31, 9, 0), DateNotices.coinYearEnd("inv", 1, LocalDateTime.of(2026, 12, 31, 10, 0))!!.at)
    }

    @Test fun lossHarvestShowsTheTaxASaleAtALossSaves() {
        val us = InvestAccount(id = 1, name = "해외", type = AccountType.OVERSEAS)
        val isa = InvestAccount(id = 2, name = "ISA", type = AccountType.ISA)
        fun t(id: Long, acc: Long, sym: String, side: Side, q: Double, p: Double, m: Market = Market.US_STOCK, d: LocalDate = LocalDate.of(2026, 3, 1)) =
            Trade(id = id, accountId = acc, date = d, symbol = sym, market = m, side = side, quantity = q, price = p, fxRate = 1_400.0)
        val book = InvestBook(
            accounts = listOf(us, isa),
            trades = listOf(
                t(1, 1, "AAPL", Side.BUY, 10.0, 100.0), t(2, 1, "AAPL", Side.SELL, 10.0, 300.0, d = LocalDate.of(2026, 6, 1)),
                t(3, 1, "TSLA", Side.BUY, 10.0, 200.0), t(4, 2, "MSFT", Side.BUY, 10.0, 200.0),
                t(5, 1, "BTC", Side.BUY, 0.1, 100_000_000.0, Market.CRYPTO).copy(fxRate = 1.0),
            ),
            quotes = listOf(Quote("TSLA", 150.0, LocalDate.of(2026, 12, 1)), Quote("MSFT", 100.0, LocalDate.of(2026, 12, 1))),
            settings = InvestSettings(usdKrw = 1_400.0),
        )
        val result = Portfolio.build(book)
        val h = InvestTaxes.lossHarvest(book, result, LocalDate.of(2026, 12, 1))
        // 280만 원 이익 → 세금 6.6만. TSLA를 150달러에 팔면 손실 70만 → 이익 210만은 공제 안이라 세금 0, 6.6만 원 줄어요. ISA의 MSFT는 빼요.
        assertEquals(1, h.size)
        assertEquals("TSLA", h[0].key)
        assertEquals(-700_000, h[0].loss)
        assertEquals(66_000, h[0].saves)
        assertEquals(1, InvestTaxes.coinsHeld(result))
        // A year under the 공제 saves nothing.
        val small = book.copy(trades = book.trades.filter { it.symbol != "AAPL" })
        assertTrue(InvestTaxes.lossHarvest(small, Portfolio.build(small), LocalDate.of(2026, 12, 1)).isEmpty())
    }
}
