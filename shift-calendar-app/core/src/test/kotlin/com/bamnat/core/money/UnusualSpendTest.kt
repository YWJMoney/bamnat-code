package com.bamnat.core.money

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** 1.37 [LEDH-36] 평소보다 큰 지출. Amounts are made up. */
class UnusualSpendTest {
    private val today = LocalDate.of(2026, 11, 5)
    private fun e(id: Long, days: Long, amount: Long, cat: String = "식비", source: MoneySource = MoneySource.MANUAL) =
        MoneyEntry(id = id, date = today.minusDays(days), kind = MoneyKind.EXPENSE, amount = amount, category = cat, source = source)

    @Test fun threeTimesTheUsualAndThirtyThousandOrMore() {
        val usual = (1L..6L).map { e(it, it * 10, 10_000) }
        val big = e(100, 2, 45_000)
        val small = e(101, 3, 25_000) // under 3만
        val fixed = e(102, 4, 90_000, source = MoneySource.FIXED)
        val old = e(103, 90, 60_000) // before the last 60 days
        assertEquals(setOf(100L), LedgerSmarts.unusual(usual + big + small + fixed + old, today))
    }

    @Test fun aCategoryWithFewLinesIsNotJudged() {
        assertEquals(emptySet<Long>(), LedgerSmarts.unusual(listOf(e(1, 1, 10_000, "병원"), e(2, 2, 200_000, "병원")), today))
    }
}

/** 1.37 [LEDH-30] 근무에서 바로 적기. */
class DayHabitsTest {
    private val d = LocalDate.of(2026, 11, 5)
    private fun e(days: Long, amount: Long, place: String) =
        MoneyEntry(id = days * 100 + amount % 97, date = d.minusDays(days), kind = MoneyKind.EXPENSE, amount = amount, category = "식비", merchant = place)

    @Test fun twiceOnNightsIsAHabit() {
        val nights = setOf(d.minusDays(1), d.minusDays(4), d.minusDays(7))
        val list = listOf(e(1, 8_000, "야식"), e(4, 8_000, "야식"), e(7, 8_000, "야식"), e(4, 3_000, "커피"), e(2, 8_000, "야식"))
        val picks = LedgerSmarts.dayHabits(list, nights)
        assertEquals(listOf("야식" to 8_000L), picks.map { it.merchant to it.amount })
    }
}

/** 1.37 [LEDH-32] 복귀 반김. */
class AwayTest {
    private val today = LocalDate.of(2026, 11, 20)
    private fun e(days: Long, source: MoneySource = MoneySource.MANUAL) =
        MoneyEntry(date = today.minusDays(days), kind = MoneyKind.EXPENSE, amount = 1_000, category = "식비", source = source)

    @Test fun aWeekAwayAfterRealUseIsSaid() {
        val used = (10L..19L).map { e(it) } + e(3, MoneySource.NOTIFICATION) + e(2, MoneySource.FIXED)
        assertEquals(LedgerSmarts.Away(10, 2), LedgerSmarts.away(used, today))
        assertEquals(null, LedgerSmarts.away(used + e(1), today))
        assertEquals(null, LedgerSmarts.away((10L..12L).map { e(it) }, today))
    }
}
