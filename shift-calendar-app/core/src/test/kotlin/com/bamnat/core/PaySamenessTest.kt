package com.bamnat.core

import com.bamnat.core.pay.BulkEditPlanner
import com.bamnat.core.pay.DatedValue
import com.bamnat.core.pay.EditScope
import com.bamnat.core.pay.PaySameness
import com.bamnat.core.pay.PayItem
import com.bamnat.core.pay.PayProfile
import com.bamnat.core.pay.Occupation
import com.bamnat.core.pay.ValueTimeline
import com.bamnat.core.pay.WageType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** A 1.32 [FIX132-03] every month on the same pay values unless the user wants it otherwise. Amounts are made up. */
class PaySamenessTest {
    private val oct = LocalDate.of(2026, 10, 1)
    private fun profile(base: ValueTimeline, items: List<PayItem> = emptyList()) =
        PayProfile(Occupation.entries.first(), WageType.MONTHLY, base, items)

    @Test fun aValueChangedFromThisMonthShowsInTheMonthsBefore() {
        val p = profile(ValueTimeline(listOf(DatedValue(LocalDate.of(2026, 1, 1), 2_500_000), DatedValue(oct, 2_600_000))))
        val sep = PaySameness.of(p, YearMonth.of(2026, 9))
        assertFalse(sep.same)
        assertEquals(2_500_000, sep.older.single().here)
        assertTrue(sep.line, sep.line.contains("2026년 10월부터는 2,600,000원"))
        assertTrue(PaySameness.of(p, YearMonth.of(2026, 11)).same)
        val one = PaySameness.unify(p)
        assertTrue(PaySameness.of(one, YearMonth.of(2026, 9)).same)
        assertEquals(2_600_000, one.base.on(YearMonth.of(2026, 3)).value)
    }

    @Test fun allMonthsReplacesEveryEntryAndKeepsTheRaise() {
        val t = ValueTimeline(listOf(DatedValue(LocalDate.of(2025, 3, 1), 10), DatedValue(oct, 20)))
        val all = BulkEditPlanner.apply(t, 30, EditScope.AllMonths)
        assertEquals(listOf(DatedValue(LocalDate.of(2025, 3, 1), 30)), all.entries)
        assertEquals(BulkEditPlanner.EARLIEST, BulkEditPlanner.apply(ValueTimeline(), 5, EditScope.AllMonths).entries.single().from)
    }

    @Test fun aClosedMonthSaysSo() {
        val p = profile(ValueTimeline(listOf(DatedValue(oct, 1))))
        val r = PaySameness.of(p, YearMonth.of(2026, 10), closed = true)
        assertFalse(r.same)
        assertTrue(r.line.startsWith("이 달은 마감해서"))
    }
}
