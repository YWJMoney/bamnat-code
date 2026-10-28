package com.bamnat.core.pay

import java.time.YearMonth
import kotlin.math.abs

/**
 * D 1.32 [PAYA-03] 577 (프로): several months' payslips at once. One month can be matched by settings that are wrong
 * for the next (a month with no 휴일 says nothing of how 휴일 is paid); the settings that fit every month kept are the
 * company's. Each kept month ([PayProfile.checks], 지급 총액 only) is matched on its own from the user's settings
 * ([PayslipMatcher]), then every one of those settings, and the user's own, is tried on all the months: the one with the
 * fewest months off, then the least won off in all, wins. At most [MONTHS] months, newest first.
 */
object MultiMonthFit {
    const val MONTHS = 6

    data class MonthRow(val month: YearMonth, val gross: Long, val total: Long) {
        val gap: Long get() = gross - total
        val matched: Boolean get() = abs(gap) <= PayslipMatch.tolerance(gross)
    }

    data class Result(
        /** The settings that fit the months best; the user's own when nothing fits better. */
        val profile: PayProfile,
        val rows: List<MonthRow>,
        /** The user's settings on the same months, to show what changes. */
        val before: List<MonthRow>,
        /** What the winning settings changed, against the user's. */
        val changes: List<MatchChange>,
    ) {
        val matchedMonths: Int get() = rows.count { it.matched }
        val improved: Boolean get() = score(rows) < score(before)
    }

    private fun score(rows: List<MonthRow>): Pair<Int, Long> = rows.count { !it.matched } to rows.sumOf { abs(it.gap) }
    private operator fun Pair<Int, Long>.compareTo(o: Pair<Int, Long>): Int = compareValuesBy(this, o, { it.first }, { it.second })

    /** The months [p] keeps that can be fitted: two or more, a month once, newest first. */
    fun months(p: PayProfile): List<PayslipCheck> =
        (listOfNotNull(p.verified) + p.checks).filter { it.gross > 0 }.distinctBy { it.month }.sortedByDescending { it.month }.take(MONTHS)

    /**
     * [total] works out a month with given settings (the pay screen's own calculation); [match] is [PayslipMatcher.match]
     * for one month from given settings. Null with fewer than two months.
     */
    fun fit(
        profile: PayProfile,
        total: (PayProfile, YearMonth) -> Long,
        match: (PayProfile, YearMonth, Payslip) -> PayslipMatch,
    ): Result? {
        val months = months(profile)
        if (months.size < 2) return null
        fun rows(p: PayProfile) = months.map { MonthRow(it.month, it.gross, total(p, it.month)) }
        val before = rows(profile)
        val tried = months.map { match(profile, it.month, Payslip(gross = it.gross)) }
        var best = profile to before
        var bestChanges = emptyList<MatchChange>()
        for (m in tried) {
            val r = rows(m.profile)
            if (score(r) < score(best.second)) { best = m.profile to r; bestChanges = m.changes }
        }
        return Result(best.first, best.second, before, bestChanges)
    }
}
