package com.bamnat.core.money

import com.bamnat.core.tax.TaxPlus
import java.time.LocalDate

/**
 * 1.37 [LEDH-08] 근무 리듬 예산: the money that may be spent until the next pay day, shared out by what each day is (야간,
 * 쉬는 날, 야간 다음 날 …) in the proportion the user has spent on such days over the last 90 days ([TaxPlus.byShift]).
 * "야간 날 3만 원 · 쉬는 날 4만 원 · 주간 1만 5천 원". A day kind seen too rarely to learn from takes the average.
 */
object ShiftRhythm {
    const val MIN_SPEND_DAYS = 14

    data class Share(val label: String, val perDay: Long, val days: Int)

    data class Plan(val todayLabel: String, val today: Long, val shares: List<Share>) {
        /** "야간 3만 원 · 쉬는 날 4만 원" (the kinds still ahead in the cycle, the dearest first). */
        fun words(won: (Long) -> String): String = shares.joinToString(" · ") { "${it.label} ${won(it.perDay)}" }
    }

    /**
     * [pool] shared over [from]..[until] (both counted; [from] is today) by [label]. Null when fewer than two kinds of
     * day were learned or the days ahead are all of one kind (nothing to share differently), or nothing is left.
     */
    fun plan(pool: Long, from: LocalDate, until: LocalDate, label: (LocalDate) -> String?, rows: List<TaxPlus.ShiftSpend>,
        /** Days with spending written in the window [rows] came from; under [MIN_SPEND_DAYS] there is too little to learn. */
        spendDays: Int = MIN_SPEND_DAYS): Plan? {
        // 1.37 PC 5차: 지출을 이틀만 적었는데 근무 리듬 줄이 나왔어요. 두 주는 적어야 날 종류마다 쓰는 돈을 배워요.
        if (spendDays < MIN_SPEND_DAYS) return null
        // PC 확인 37446021: 새로 깔아 지출이 없는데도 모든 날이 '2만'으로 나왔어요. 쓴 돈이 있는 날 종류가 둘은 있어야 배워요.
        val spent = rows.filter { it.perDay > 0 }
        if (pool <= 0 || spent.size < 2 || until.isBefore(from)) return null
        val learned = spent.associate { it.label to it.perDay }
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
        if (shares.map { it.perDay }.distinct().size < 2) return null
        val todayLabel = ahead.first().second
        return Plan(todayLabel, share(todayLabel), shares)
    }
}

/**
 * 1.37 [LEDH-23] 내 월급 만들기, for money that comes in unevenly (현장·가게·알바): pay yourself the same amount each month — the second
 * lowest of the last six months' income, so five months of six would have covered it — and how many months what is in the accounts
 * now would last at the usual spending.
 */
object SelfSalary {
    data class Plan(val salary: Long, val months: Int, val covered: Int, val lastFor: Double?, val spend: Long)

    /** [incomes] and [spends] of the last whole months, oldest first; null with fewer than three months of income. */
    fun of(incomes: List<Long>, spends: List<Long>, have: Long?): Plan? {
        val months = incomes.indices.filter { incomes[it] > 0 || spends.getOrElse(it) { 0 } > 0 }
        if (months.size < 3) return null
        val inc = months.map { incomes[it] }
        val sorted = inc.sorted()
        val salary = (sorted.getOrElse(1) { sorted.first() } / 10_000) * 10_000
        if (salary <= 0) return null
        val spend = months.map { spends.getOrElse(it) { 0 } }.average().toLong()
        val lastFor = if (have == null || spend <= 0) null else Math.round(have.toDouble() / spend * 10) / 10.0
        return Plan(salary, inc.size, inc.count { it >= salary }, lastFor, spend)
    }
}

