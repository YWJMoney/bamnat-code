package com.bamnat.core.money

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** 1.37 [LEDH-34] 예산 넘김. Amounts are made up. */
class BudgetCarryTest {
    private val nov = YearMonth.of(2026, 11)
    private fun e(day: LocalDate, amount: Long, cat: String) = MoneyEntry(date = day, kind = MoneyKind.EXPENSE, amount = amount, category = cat)
    private val budgets = listOf(MoneyBudget("식비", 300_000), MoneyBudget("카페", 50_000))

    @Test fun leftOverAndOverspendCarryForChosenCategoriesOnly() {
        val oct = listOf(e(LocalDate.of(2026, 10, 3), 250_000, "식비"), e(LocalDate.of(2026, 10, 9), 80_000, "카페"))
        val carry = Budgets.carry(budgets, oct, nov.atDay(1), nov.atEndOfMonth(), setOf("식비", "카페"))
        assertEquals(mapOf("식비" to 50_000L, "카페" to -30_000L), carry)
        val s = Budgets.status(budgets, oct, nov, LocalDate.of(2026, 11, 5), carry = carry)
        assertEquals(350_000L, s.categories.first { it.category == "식비" }.budget)
        assertEquals(50_000L, s.categories.first { it.category == "식비" }.carried)
        assertEquals(emptyMap<String, Long>(), Budgets.carry(budgets, oct, nov.atDay(1), nov.atEndOfMonth(), emptySet()))
    }

    @Test fun nothingWrittenBeforeCarriesNothing() {
        assertEquals(emptyMap<String, Long>(), Budgets.carry(budgets, emptyList(), nov.atDay(1), nov.atEndOfMonth(), setOf("식비")))
    }
}
