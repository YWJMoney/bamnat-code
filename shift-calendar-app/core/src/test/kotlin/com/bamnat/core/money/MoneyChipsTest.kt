package com.bamnat.core.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** 1.39 [FLOW-18][FLOW-20] 잘 쓴 곳·많이 쓴 곳, 근무별 돈. Amounts are made up. */
class MoneyChipsTest {
    private val won = { v: Long -> "%,d원".format(v) }
    private fun share(c: String, now: Long, before: Long) = CategoryShare(c, now, 1, 0f, before)
    private fun report(month: YearMonth, vararg c: CategoryShare) = MonthReport(month, 0, c.sumOf { it.amount }, 0, c.sumOf { it.previous },
        c.toList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), List(7) { 0L }, 0, 0)

    @Test fun aMonthGoneByIsSetAgainstTheWholeMonthBefore() {
        val r = report(YearMonth.of(2026, 10), share("식비", 300_000, 400_000), share("배달", 180_000, 90_000), share("카페", 52_000, 50_000),
            share("새 분류", 90_000, 0), share(Reports.JOB_COSTS, 1, 900_000))
        val rows = MonthChips.of(r, emptyList(), LocalDate.of(2026, 11, 7))
        assertEquals(listOf("식비 100,000원 덜"), rows.good.map { it.words(won) })
        // 카페 moved by 2,000원 only; a category new this month and 일감 경비 are not judged.
        assertEquals(listOf("배달 90,000원 더"), rows.caution.map { it.words(won) })
    }

    @Test fun theMonthInProgressIsSetAgainstTheSameShareOfTheMonthBefore() {
        // The 10th of a 30-day month: a third of last month's 300,000 is 100,000; 160,000 by now is 60,000 more.
        val r = report(YearMonth.of(2026, 11), share("식비", 160_000, 300_000))
        val rows = MonthChips.of(r, emptyList(), LocalDate.of(2026, 11, 10))
        assertEquals(listOf("식비 60,000원 더"), rows.caution.map { it.words(won) })
    }

    @Test fun aBudgetWinsOverLastMonth() {
        val r = report(YearMonth.of(2026, 11), share("식비", 260_000, 900_000), share("배달", 20_000, 300_000))
        val budgets = listOf(BudgetLine("식비", 250_000, 260_000, 0), BudgetLine("배달", 100_000, 20_000, 0), BudgetLine(MoneyBudget.TOTAL, 1, 999, 0))
        val rows = MonthChips.of(r, budgets, LocalDate.of(2026, 11, 15))
        assertEquals(listOf("식비 예산 10,000원 넘음"), rows.caution.map { it.words(won) })
        assertEquals(listOf("배달 예산 안"), rows.good.map { it.words(won) })
    }

    @Test fun nothingToSayWithNoMonthBeforeAndNoBudget() {
        assertTrue(MonthChips.of(report(YearMonth.of(2026, 11), share("식비", 90_000, 0)), emptyList(), LocalDate.of(2026, 11, 20)).isEmpty)
        // A month still ahead says nothing either.
        assertTrue(MonthChips.of(report(YearMonth.of(2026, 12), share("식비", 1, 900_000)), emptyList(), LocalDate.of(2026, 11, 20)).isEmpty)
    }

    private val from = LocalDate.of(2026, 9, 1)
    private val to = from.plusDays(29)
    /** Every third day a night, the rest days off. */
    private val label = { d: LocalDate -> if (d.dayOfMonth % 3 == 0) "야간" else "쉬는 날" }
    private fun e(d: LocalDate, cat: String, v: Long, fixed: Boolean = false) =
        MoneyEntry(date = d, kind = MoneyKind.EXPENSE, amount = v, category = cat, source = if (fixed) MoneySource.FIXED else MoneySource.MANUAL)

    @Test fun nightsThatBuyMoreAtTheShopAreSaid() {
        val days = generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.toList()
        val entries = days.filter { label(it) == "야간" }.map { e(it, "편의점", 9_000) } +
            days.filter { label(it) != "야간" }.take(6).map { e(it, "편의점", 3_000) } +
            // 택시 more on days off, from 5 times; a fixed cost is not a choice.
            days.filter { label(it) != "야간" }.take(8).map { e(it, "택시", 12_000) } +
            days.map { e(it, "통신", 50_000, fixed = true) }
        val rows = ShiftMoneyChips.of(entries, from, to, label)
        assertEquals(listOf("야간 날 편의점 하루 8,100원 더"), rows.caution.filter { it.dayKind == "야간" }.map { it.words(won) })
        assertTrue(rows.caution.any { it.dayKind == "쉬는 날" && it.category == "택시" })
        assertTrue(rows.good.none { it.category == "통신" } && rows.caution.none { it.category == "통신" })
    }

    @Test fun underFiveTimesNothingIsSaid() {
        // Four nights only.
        val few = { d: LocalDate -> if (d.dayOfMonth in listOf(3, 6, 9, 12)) "야간" else "쉬는 날" }
        val entries = listOf(3, 6, 9, 12).map { e(from.withDayOfMonth(it), "편의점", 20_000) }
        assertTrue(ShiftMoneyChips.of(entries, from, to, few).isEmpty)
    }
}
