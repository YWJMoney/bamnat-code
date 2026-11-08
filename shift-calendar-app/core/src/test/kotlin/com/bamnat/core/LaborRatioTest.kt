package com.bamnat.core

import com.bamnat.core.boss.LaborRatio
import com.bamnat.core.money.DailySales
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** 1.38 [P3-X-07] 인건비율: the ledger's 인건비 over the 가계부's 매출, and over the month's pace while it runs. */
class LaborRatioTest {
    private val nov = YearMonth.of(2026, 11)
    private fun day(m: YearMonth, d: Int, v: Long) = DailySales(m.atDay(d), v)

    @Test fun overTheMonthAndItsPace() {
        // 10 days of 500,000원 by 11/10, 인건비 4,500,000원.
        val sales = (1..10).map { day(nov, it, 500_000) } + (1..30).map { day(nov.minusMonths(1), it, 400_000) }
        val r = LaborRatio.of(nov, 4_500_000, sales, LocalDate.of(2026, 11, 10), lastLabor = 3_000_000)
        assertEquals(5_000_000L, r.sales)
        assertEquals(10, r.salesDays)
        assertEquals(90.0, r.percent!!, 0.0)
        assertEquals(15_000_000L, r.paceSales)
        assertEquals(30.0, r.pacePercent!!, 0.0)
        assertEquals("10월 12,000,000원 중 3,000,000원", 25.0, r.lastPercent!!, 0.0)
        assertEquals("30%", LaborRatio.label(30.0))
        assertEquals("27.4%", LaborRatio.label(27.4))
    }

    @Test fun noSalesNoRatioAndAClosedMonthHasNoPace() {
        assertNull(LaborRatio.of(nov, 1_000_000, emptyList(), LocalDate.of(2026, 11, 10)).percent)
        val r = LaborRatio.of(nov, 1_000_000, listOf(day(nov, 3, 3_000_000)), LocalDate.of(2026, 12, 2))
        assertEquals(33.3, r.percent!!, 0.0)
        assertNull(r.paceSales)
        // Two days in is too early to say.
        assertNull(LaborRatio.of(nov, 1_000_000, listOf(day(nov, 1, 100)), LocalDate.of(2026, 11, 2)).paceSales)
    }
}
