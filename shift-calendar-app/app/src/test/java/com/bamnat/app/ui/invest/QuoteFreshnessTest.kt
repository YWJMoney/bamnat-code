package com.bamnat.app.ui.invest

import com.bamnat.core.invest.Holding
import com.bamnat.core.invest.Market
import com.bamnat.core.invest.Quote
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * 2.0.0 [RVK-25] (검토 MONEY-69) Saving 현재가 dated every price today, the ones not touched too, so a price of last week read as
 * today's. Amounts are made up.
 */
class QuoteFreshnessTest {
    private val today = LocalDate.of(2026, 10, 14)
    private fun h(key: String, quote: Quote?) =
        Holding(key, key, key, Market.KR_STOCK, null, 10.0, 700_000.0, 700_000.0, quote, 1.0, 0.0, 0L, LocalDate.of(2026, 9, 1), 1)

    private val held = listOf(
        h("A", Quote("A", 73_400.0, today.minusDays(9))),
        h("B", Quote("B", 41_050.0, today.minusDays(9))),
        h("C", null),
    )
    private val first = mapOf("A" to "73400", "B" to "41050", "C" to "")

    @Test fun onlyAPriceTypedAnewIsToday() {
        val saved = quotesToSave(held, first + ("A" to "74000") + ("C" to "182300"), first, today, lookedToday = false)
        assertEquals(listOf("A", "C"), saved.map { it.key })
        assertEquals(listOf(today, today), saved.map { it.date })
    }

    @Test fun theOnesLookedAtTodayToo() {
        val saved = quotesToSave(held, first + ("A" to "74000"), first, today, lookedToday = true)
        assertEquals(listOf("A", "B"), saved.map { it.key })
        assertEquals(41_050.0, saved[1].price, 0.0)
    }

    @Test fun howOld() {
        assertEquals("오늘", InvestFmt.ago(today, today))
        assertEquals("어제", InvestFmt.ago(today.minusDays(1), today))
        assertEquals("9일 전", InvestFmt.ago(today.minusDays(9), today))
    }
}
