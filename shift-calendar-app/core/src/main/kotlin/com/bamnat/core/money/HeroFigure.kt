package com.bamnat.core.money

/**
 * 2.0.0 [RVK-19] (검토 U06a-06·MONEY-19) The big figure at the top of 가계부 › 요약. For someone who writes only what they spend, a
 * big red '수입 − 지출' minus is the first thing they meet; while nothing came in this month (no pay written and none expected)
 * the month's 나간 돈 is the figure instead. A tap swaps it, and the swap is kept.
 */
enum class HeroFigure(val key: String) {
    /** 수입 − 지출: what is left of the money that came in. */
    NET("NET"),
    /** 나간 돈: what went out this month. */
    SPENT("SPENT");

    companion object {
        /** [choice] is the kept swap ("" when never swapped). */
        fun of(choice: String, m: MonthMoney): HeroFigure = entries.firstOrNull { it.key == choice }
            ?: if (m.received == 0L && m.payExpected == null) SPENT else NET

        /** The figure a tap swaps to. */
        fun swapped(now: HeroFigure): HeroFigure = if (now == NET) SPENT else NET
    }
}
