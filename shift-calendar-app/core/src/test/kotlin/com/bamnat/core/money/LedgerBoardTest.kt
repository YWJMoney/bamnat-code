package com.bamnat.core.money

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** [TAB-06] 1.36 가계부 판. */
class LedgerBoardTest {
    private val d = LocalDate.of(2026, 11, 3)
    private val lines = listOf(
        MoneyEntry(1, d, MoneyKind.EXPENSE, 8000, "식비", merchant = "김밥"),
        MoneyEntry(2, d, MoneyKind.EXPENSE, 30000, "교통"),
        MoneyEntry(3, d.plusDays(1), MoneyKind.EXPENSE, 12000, "식비"),
        MoneyEntry(4, d.plusDays(1), MoneyKind.INCOME, 100000, "급여"),
        MoneyEntry(5, d, MoneyKind.TRANSFER, 50000, "이체"),
    )

    @Test fun boardColumnsBiggestFirstWithoutTransfers() {
        val cols = LedgerBoard.columns(lines)
        assertEquals(listOf("급여", "교통", "식비"), cols.map { it.category })
        assertEquals(20000, cols.first { it.category == "식비" }.expense)
    }

    @Test fun daysAndTableSort() {
        val days = LedgerBoard.days(lines, YearMonth.of(2026, 11))
        assertEquals(38000, days[d]!!.expense)
        assertEquals(100000, days[d.plusDays(1)]!!.income)
        assertEquals(listOf(2L, 3L, 1L, 5L, 4L), LedgerBoard.sortTable(lines, "amount", down = false).map { it.id })
        assertEquals("김밥", LedgerBoard.what(lines[0]))
    }
}
