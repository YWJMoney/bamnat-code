package com.bamnat.core.money

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** 2.0.0 [RVK-19] (MONEY-19): the big figure on 요약 for someone who writes only spending. Amounts are made up. */
class HeroFigureTest {
    private val month = YearMonth.of(2026, 11)
    private fun line(kind: MoneyKind, amount: Long, category: String) =
        MoneyEntry(date = LocalDate.of(2026, 11, 3), kind = kind, amount = amount, category = category)

    @Test fun onlySpendingShowsWhatWentOut() {
        val m = MoneyCalculator.month(month, listOf(line(MoneyKind.EXPENSE, 12_000, "식비")), emptyList(), emptyList())
        // Before: '수입 − 지출 −12,000원' in red as the first thing seen.
        assertEquals(-12_000L, m.leftNow)
        assertEquals(HeroFigure.SPENT, HeroFigure.of("", m))
    }

    @Test fun moneyInOrPayAheadKeepsNet() {
        val withPay = MoneyCalculator.month(month, listOf(line(MoneyKind.EXPENSE, 12_000, "식비"), line(MoneyKind.INCOME, 2_000_000, MoneyCategories.SALARY)),
            emptyList(), emptyList())
        assertEquals(HeroFigure.NET, HeroFigure.of("", withPay))
        // The pay still to come counts as money for the month: the estimate's 들어올 돈 sits beside 수입 − 지출.
        val expected = MoneyCalculator.month(month, listOf(line(MoneyKind.EXPENSE, 12_000, "식비")), emptyList(), emptyList(), payEstimate = 1_800_000)
        assertEquals(HeroFigure.NET, HeroFigure.of("", expected))
    }

    @Test fun theSwapIsKept() {
        val spendOnly = MoneyCalculator.month(month, listOf(line(MoneyKind.EXPENSE, 12_000, "식비")), emptyList(), emptyList())
        assertEquals(HeroFigure.NET, HeroFigure.of("NET", spendOnly))
        assertEquals(HeroFigure.SPENT, HeroFigure.of(HeroFigure.swapped(HeroFigure.NET).key, spendOnly))
        // An unknown word from a newer backup falls back to 저절로.
        assertEquals(HeroFigure.SPENT, HeroFigure.of("SOMETHING", spendOnly))
    }
}
