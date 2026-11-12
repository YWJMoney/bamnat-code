package com.bamnat.core.invest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 2.0.0 [RVK-27] (검토 MONEY-64·T06b-14·I06ba-03) 수량 바뀜: a split, a merge, 무상증자·옮겨 온 주식·에어드랍 (입고) and shares moved
 * out (출고) change what is held without a buy or a sale. Before, there was no way to write them and the book stayed wrong for good.
 * Amounts are made up.
 */
class QuantityChangeTest {
    private val d = LocalDate.of(2026, 3, 2)
    private fun t(side: Side, q: Double, p: Double, day: Int, id: Long) =
        Trade(id = id, accountId = 1, date = d.plusDays(day.toLong()), symbol = "005930", name = "삼성전자", side = side, quantity = q, price = p)
    private fun book(vararg trades: Trade) = InvestBook(accounts = listOf(InvestAccount(id = 1, name = "주식 계좌")), trades = trades.toList(),
        cash = listOf(CashFlow(accountId = 1, date = d, kind = CashKind.DEPOSIT, amount = 10_000_000)))

    @Test fun aFiveForOneSplitMakesTheAverageAFifthAndKeepsWhatWasMade() {
        val before = Portfolio.build(book(t(Side.BUY, 10.0, 70_000.0, 0, 1), t(Side.SELL, 2.0, 80_000.0, 1, 2)))
        val after = Portfolio.build(book(t(Side.BUY, 10.0, 70_000.0, 0, 1), t(Side.SELL, 2.0, 80_000.0, 1, 2), t(Side.SPLIT, 5.0, 0.0, 2, 3)))
        val h0 = before.held.single()
        val h = after.held.single()
        assertEquals(40.0, h.quantity, 1e-9)
        assertEquals(h0.averagePrice / 5, h.averagePrice, 1e-6)
        assertEquals(h0.costWon, h.costWon, 1e-6)
        assertEquals(before.summary.realized, after.summary.realized)
        assertEquals(before.summary.cash, after.summary.cash)
        // A sale after the split is matched at the new cost: 10 of 40 shares cost a quarter.
        val sold = Portfolio.build(book(t(Side.BUY, 10.0, 70_000.0, 0, 1), t(Side.SPLIT, 5.0, 0.0, 2, 3), t(Side.SELL, 10.0, 15_000.0, 3, 4)))
        assertEquals(150_000.0 - 140_000.0, sold.realizations.single().pnlWon, 1e-6)
    }

    @Test fun aMergeIsAFraction() {
        val r = Portfolio.build(book(t(Side.BUY, 20.0, 5_000.0, 0, 1), t(Side.SPLIT, 0.1, 0.0, 1, 2)))
        assertEquals(2.0, r.held.single().quantity, 1e-9)
        assertEquals(50_000.0, r.held.single().averagePrice, 1e-6)
    }

    @Test fun sharesInForFreeLowerTheAverageAndMoveNoMoney() {
        // 무상증자 1주당 1주: 10 more at 0원.
        val r = Portfolio.build(book(t(Side.BUY, 10.0, 70_000.0, 0, 1), t(Side.IN, 10.0, 0.0, 1, 2)))
        val h = r.held.single()
        assertEquals(20.0, h.quantity, 1e-9)
        assertEquals(35_000.0, h.averagePrice, 1e-6)
        assertEquals(10_000_000L - 700_000L, r.summary.cash)
        // Moved in from another broker at what they cost there: the cost goes with them, the cash does not.
        val moved = Portfolio.build(book(t(Side.IN, 5.0, 60_000.0, 0, 1)))
        assertEquals(300_000L, moved.summary.cost)
        assertEquals(10_000_000L, moved.summary.cash)
        assertEquals(d, moved.held.single().openedOn)
    }

    @Test fun sharesOutLeaveAtTheirCostWithNothingMade() {
        val r = Portfolio.build(book(t(Side.BUY, 10.0, 70_000.0, 0, 1), t(Side.OUT, 4.0, 0.0, 1, 2)))
        val h = r.held.single()
        assertEquals(6.0, h.quantity, 1e-9)
        assertEquals(70_000.0, h.averagePrice, 1e-6)
        assertTrue(r.realizations.isEmpty())
        assertEquals(0L, r.summary.realized)
    }

    @Test fun theTaxMatchOldestFirstFollowsTheSplit() {
        val fifo = Portfolio.fifo(listOf(t(Side.BUY, 10.0, 70_000.0, 0, 1), t(Side.SPLIT, 5.0, 0.0, 1, 2), t(Side.IN, 10.0, 0.0, 2, 3),
            t(Side.SELL, 50.0, 15_000.0, 3, 4)))
        // The 50 split shares (700,000원) go first; nothing of the free 10.
        assertEquals(700_000.0, fifo.single().costWon, 1e-6)
    }

    @Test fun onlyBuysAndSellsAreTrades() {
        assertEquals(listOf(Side.BUY, Side.SELL), Side.TRADES)
        assertTrue(!Side.SPLIT.trade && !Side.IN.trade && !Side.OUT.trade)
        assertEquals(0.0, t(Side.IN, 10.0, 60_000.0, 0, 1).cashWon, 0.0)
        assertEquals(0.0, t(Side.SPLIT, 5.0, 0.0, 0, 1).cashWon, 0.0)
    }
}

/** 2.0.0 [RVK-28] (검토 U06b-04) 종목 한 장. */
class HoldingStoryTest {
    private val d = LocalDate.of(2026, 3, 2)

    @Test fun eachStepSaysWhatWasHeldAndItsAverage() {
        val book = InvestBook(
            accounts = listOf(InvestAccount(id = 1, name = "주식 계좌")),
            trades = listOf(
                Trade(id = 1, accountId = 1, date = d, symbol = "005930", name = "삼성전자", side = Side.BUY, quantity = 10.0, price = 70_000.0, memo = "실적 기대"),
                Trade(id = 2, accountId = 1, date = d.plusDays(10), symbol = "005930", name = "삼성전자", side = Side.BUY, quantity = 10.0, price = 80_000.0),
                Trade(id = 3, accountId = 1, date = d.plusDays(20), symbol = "005930", name = "삼성전자", side = Side.SPLIT, quantity = 5.0, price = 0.0),
                Trade(id = 4, accountId = 1, date = d.plusDays(30), symbol = "005930", name = "삼성전자", side = Side.SELL, quantity = 100.0, price = 16_000.0),
                Trade(id = 5, accountId = 1, date = d, symbol = "000660", name = "SK하이닉스", side = Side.BUY, quantity = 1.0, price = 180_000.0),
            ),
            cash = listOf(CashFlow(accountId = 1, date = d.plusDays(15), kind = CashKind.DIVIDEND, amount = 3_610, tax = 0, symbol = "005930")),
        )
        val s = HoldingStory.of(book, "005930")
        assertEquals(5, s.size)
        assertEquals("실적 기대", s[0].trade!!.memo)
        assertEquals(75_000.0, s[1].average, 1e-6)
        assertEquals(3_610L, s[2].cash!!.amount)
        assertEquals(20.0, s[2].quantity, 1e-9)
        assertEquals(100.0, s[3].quantity, 1e-9)
        assertEquals(15_000.0, s[3].average, 1e-6)
        assertEquals(0.0, s[4].quantity, 1e-9)
        assertEquals(1_600_000.0 - 1_500_000.0, s[4].realizedWon!!, 1e-6)
    }
}
