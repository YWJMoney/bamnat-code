package com.bamnat.core.boss

import com.bamnat.core.money.DailySales
import java.time.LocalDate
import java.time.YearMonth

/**
 * 1.38 [P3-X-07] 인건비율 (사용자 2026-11-06 혁신안 X7): the ledger's 인건비 (급여 + 4대보험 사업주 몫) over the month's 매출 from the
 * 가계부, and, while the month runs, the same over the 매출 the days so far point to. 참고: 매출 is what the boss wrote.
 */
object LaborRatio {
    data class Ratio(
        val month: YearMonth,
        val labor: Long,
        val sales: Long,
        /** Days of [month] with 매출 written. */
        val salesDays: Int,
        /** 인건비 / 매출 in percent (one decimal), null with no 매출. */
        val percent: Double?,
        /** This month only: 매출 at the pace of the days so far ([sales] / days passed × days in month). */
        val paceSales: Long?,
        val pacePercent: Double?,
        /** Last month's, when it had 매출. */
        val lastPercent: Double?,
    )

    private fun pct(labor: Long, sales: Long): Double? = if (sales <= 0) null else Math.round(labor * 1000.0 / sales) / 10.0

    fun of(month: YearMonth, labor: Long, sales: List<DailySales>, today: LocalDate, lastLabor: Long? = null): Ratio {
        val mine = sales.filter { YearMonth.from(it.date) == month }
        val total = mine.sumOf { it.sales }
        val running = YearMonth.from(today) == month
        // The days so far: up to today, or to the last day written when that is later (a day closed ahead).
        val passed = if (!running) month.lengthOfMonth() else maxOf(today.dayOfMonth, mine.maxOfOrNull { it.date.dayOfMonth } ?: 0)
        val pace = if (running && total > 0 && passed in 3 until month.lengthOfMonth()) total * month.lengthOfMonth() / passed else null
        val last = month.minusMonths(1)
        val lastSales = sales.filter { YearMonth.from(it.date) == last }.sumOf { it.sales }
        return Ratio(month, labor, total, mine.count { it.sales > 0 }, pct(labor, total), pace, pace?.let { pct(labor, it) },
            lastLabor?.let { pct(it, lastSales) })
    }

    /** "27.4%" */
    fun label(p: Double): String = if (p == Math.floor(p)) "${p.toLong()}%" else "$p%"
}
