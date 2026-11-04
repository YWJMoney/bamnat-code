package com.bamnat.core.money

import com.bamnat.core.tax.TaxPlus
import java.time.LocalDate

/**
 * 1.37 [LEDH-08] 근무 리듬 예산: the money that may be spent until the next pay day, shared out by what each day is (야간,
 * 쉬는 날, 야간 다음 날 …) in the proportion the user has spent on such days over the last 90 days ([TaxPlus.byShift]).
 * "야간 날 3만 원 · 쉬는 날 4만 원 · 주간 1만 5천 원". A day kind seen too rarely to learn from takes the average.
 */
object ShiftRhythm {
    data class Share(val label: String, val perDay: Long, val days: Int)

    data class Plan(val todayLabel: String, val today: Long, val shares: List<Share>) {
        /** "야간 3만 원 · 쉬는 날 4만 원" (the kinds still ahead in the cycle, the dearest first). */
        fun words(won: (Long) -> String): String = shares.joinToString(" · ") { "${it.label} ${won(it.perDay)}" }
    }

    /**
     * [pool] shared over [from]..[until] (both counted; [from] is today) by [label]. Null when fewer than two kinds of
     * day were learned or the days ahead are all of one kind (nothing to share differently), or nothing is left.
     */
    fun plan(pool: Long, from: LocalDate, until: LocalDate, label: (LocalDate) -> String?, rows: List<TaxPlus.ShiftSpend>): Plan? {
        if (pool <= 0 || rows.size < 2 || until.isBefore(from)) return null
        val learned = rows.associate { it.label to it.perDay.coerceAtLeast(0) }
        val avg = (learned.values.sum() / learned.size).coerceAtLeast(1)
        val ahead = generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(until) }
            .map { d -> d to (label(d) ?: TaxPlus.OFF) }.toList()
        val counts = ahead.groupingBy { it.second }.eachCount()
        if (counts.size < 2) return null
        fun w(l: String) = (learned[l]?.takeIf { it > 0 } ?: avg).toDouble()
        val total = ahead.sumOf { w(it.second) }
        if (total <= 0.0) return null
        fun share(l: String) = (pool * w(l) / total).toLong() / 100 * 100
        val shares = counts.map { (l, n) -> Share(l, share(l), n) }.sortedByDescending { it.perDay }
        val todayLabel = ahead.first().second
        return Plan(todayLabel, share(todayLabel), shares)
    }
}
