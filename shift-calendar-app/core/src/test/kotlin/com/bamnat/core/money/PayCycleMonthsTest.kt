package com.bamnat.core.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/**
 * 2.0.0 [RVK-20] (검토 MONEY-18·U06a-07·T06a-08·I06aa-04): with 급여일 주기 on, 요약·내역·통계 count the pay cycle, not only
 * the budget. Before, '11월 수입 − 지출' summed 11/1~11/30 while the budget beside it ran 10/25~11/24. Amounts are made up.
 */
class PayCycleMonthsTest {
    private val today = LocalDate.of(2026, 11, 12)
    /** Pay on the 25th, a weekend's moved to the Friday before (10/25/2026 is a Sunday: paid 10/23). */
    private val payDate = { m: YearMonth -> PayCycle.weekdayBefore(m.atDay(25)) }

    @Test fun eachMonthIsOneCycleNoneTwiceNoneSkipped() {
        val cycles = (-6..6).map { PayCycle.forMonth(today, YearMonth.from(today).plusMonths(it.toLong()), payDate)!! }
        assertEquals(PayCycle.Range(LocalDate.of(2026, 10, 23), LocalDate.of(2026, 11, 24)), cycles[6])
        cycles.zipWithNext().forEach { (a, b) -> assertEquals("$a → $b", a.to.plusDays(1), b.from) }
        assertEquals(PayCycle.Range(LocalDate.of(2026, 9, 25), LocalDate.of(2026, 10, 22)), cycles[5])
        // The same day in each month would show one cycle twice once a pay day moved: on 11/24, October's 10/24 and November's
        // 11/24 are both in 10/23~11/24. One cycle a month away never is.
        val late = LocalDate.of(2026, 11, 24)
        assertEquals(PayCycle.Range(LocalDate.of(2026, 9, 25), LocalDate.of(2026, 10, 22)), PayCycle.forMonth(late, YearMonth.of(2026, 10), payDate))
        assertEquals(PayCycle.Range(LocalDate.of(2026, 10, 23), LocalDate.of(2026, 11, 24)), PayCycle.forMonth(late, YearMonth.of(2026, 11), payDate))
    }

    @Test fun noPayDayNoCycle() {
        assertNull(PayCycle.forMonth(today, YearMonth.of(2026, 11)) { null })
    }

    @Test fun theSumsAreTheCycles() {
        val book = MoneyBook(entries = listOf(
            MoneyEntry(date = LocalDate.of(2026, 10, 24), kind = MoneyKind.EXPENSE, amount = 30_000, category = "식비"),
            MoneyEntry(date = LocalDate.of(2026, 11, 20), kind = MoneyKind.EXPENSE, amount = 12_000, category = "식비"),
            MoneyEntry(date = LocalDate.of(2026, 11, 25), kind = MoneyKind.EXPENSE, amount = 50_000, category = "쇼핑"),
            MoneyEntry(date = LocalDate.of(2026, 10, 23), kind = MoneyKind.INCOME, amount = 2_000_000, category = MoneyCategories.SALARY),
        ))
        val nov = YearMonth.of(2026, 11)
        val range = PayCycle.forMonth(today, nov, payDate)!!
        val m = book.cycle(nov, range, today)
        // Before: the calendar month, 11/20 and 11/25 (62,000원) and no pay.
        assertEquals(62_000L, book.month(nov, today = today).spent)
        assertEquals(42_000L, m.spent)
        assertEquals(2_000_000L, m.received)
        assertEquals("10/23~11/24", m.label)
        assertEquals(LocalDate.of(2026, 10, 23), m.from)
    }

    @Test fun theReportSetsCycleAgainstCycle() {
        val book = MoneyBook(entries = listOf(
            MoneyEntry(date = LocalDate.of(2026, 10, 1), kind = MoneyKind.EXPENSE, amount = 5_000, category = "식비"),
            MoneyEntry(date = LocalDate.of(2026, 10, 22), kind = MoneyKind.EXPENSE, amount = 7_000, category = "식비"),
            MoneyEntry(date = LocalDate.of(2026, 10, 24), kind = MoneyKind.EXPENSE, amount = 30_000, category = "식비"),
            MoneyEntry(date = LocalDate.of(2026, 11, 20), kind = MoneyKind.EXPENSE, amount = 12_000, category = "식비"),
        ))
        val nov = YearMonth.of(2026, 11)
        val now = PayCycle.forMonth(today, nov, payDate)!!
        val before = PayCycle.forMonth(today, nov.minusMonths(1), payDate)!!
        val r = Reports.month(book.cycle(nov, now, today), book.cycle(nov.minusMonths(1), before, today), book, today)
        assertEquals(42_000L, r.spent)
        // The cycle before is 9/25~10/22: 10/1 and 10/22, not 10/24 (that is this cycle's).
        assertEquals(12_000L, r.previousSpent)
        assertEquals(now.days, r.length)
        assertEquals(before.days, r.previousCumulative.size)
        // Counted from the cycle's first day to today (10/23..11/12, 21 days): by today only 10/24's 30,000원.
        assertEquals(21, r.cumulative.size)
        assertEquals(30_000L, r.cumulative.last())
        assertEquals("지난 주기", r.earlier)
        assertEquals("지난 주기와", r.earlierWith)
        assertTrue(r.holds(today))
        assertTrue(!r.holds(LocalDate.of(2026, 11, 25)))
        // Without a cycle the words stay the month's.
        val plain = Reports.month(book.month(nov, today = today), book.month(nov.minusMonths(1), today = today), book, today)
        assertEquals("지난달과", plain.earlierWith)
        assertEquals(30, plain.length)
    }
}
