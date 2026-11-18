package com.bamnat.core.invest

import java.time.LocalDate

/**
 * 2.0.0 [RVK-28] (검토 U06b-04·I06ba-04) 종목 한 장: one holding's buys, sales, 수량 바뀜, dividends and notes in the order they
 * happened, with what was held and its 평균단가 after each, so '왜 샀더라' is read again in one place (before, only a calendar
 * day showed it, and a holding sold out did nothing when pressed).
 */
object HoldingStory {
    data class Step(
        val date: LocalDate,
        /** A trade (with its memo, reason and feelings), or [cash] for a dividend. */
        val trade: Trade? = null,
        val cash: CashFlow? = null,
        /** Held after this step, in every account shown. */
        val quantity: Double,
        /** 평균단가 after this step in the holding's currency, fees included; 0 once nothing is held. */
        val average: Double,
        /** What a sale made (moving average, as the summary counts it); null for anything else. */
        val realizedWon: Double? = null,
    )

    /** Oldest first; [accountId] null for every account together. */
    fun of(book: InvestBook, key: String, accountId: Long? = null, result: PortfolioResult = Portfolio.build(book)): List<Step> {
        val trades = book.trades.filter { it.key == key && (accountId == null || it.accountId == accountId) }.sortedWith(Portfolio.ORDER)
        val cash = book.cash.filter { it.kind == CashKind.DIVIDEND && it.symbol.isNotBlank() && holdingKey(it.symbol, "") == key &&
            (accountId == null || it.accountId == accountId) }
        val realized = result.realizations.associate { it.tradeId to it.pnlWon }
        // Each account keeps its own average, as the summary does; the line shows them together.
        class Held(var qty: Double = 0.0, var cost: Double = 0.0)
        val held = HashMap<Long, Held>()
        val steps = ArrayList<Step>()
        fun total() = held.values.sumOf { it.qty }.let { q -> q to if (q > EPS) held.values.sumOf { it.cost } / q else 0.0 }
        for (t in trades) {
            val h = held.getOrPut(t.accountId) { Held() }
            if (t.quantity > 0) when (t.side) {
                Side.BUY, Side.IN -> { h.qty += t.quantity; h.cost += t.gross + t.fee + t.tax }
                Side.SELL, Side.OUT -> {
                    val out = minOf(t.quantity, h.qty)
                    if (h.qty > EPS) h.cost -= h.cost * out / h.qty
                    h.qty -= out
                    if (h.qty <= EPS) { h.qty = 0.0; h.cost = 0.0 }
                }
                Side.SPLIT -> h.qty *= t.quantity
            }
            val (q, avg) = total()
            steps += Step(t.date, trade = t, quantity = q, average = avg, realizedWon = if (t.side == Side.SELL) realized[t.id] else null)
        }
        // A dividend goes after that day's trades, holding what was held then.
        for (c in cash) {
            val before = steps.lastOrNull { !it.date.isAfter(c.date) }
            steps += Step(c.date, cash = c, quantity = before?.quantity ?: 0.0, average = before?.average ?: 0.0)
        }
        return steps.sortedWith(compareBy<Step>({ it.date }, { if (it.cash != null) 1 else 0 }))
    }
}
