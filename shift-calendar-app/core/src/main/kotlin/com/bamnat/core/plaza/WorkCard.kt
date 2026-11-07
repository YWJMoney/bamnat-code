package com.bamnat.core.plaza

/**
 * 1.39 [PLZC-20] 근무 카드: this month so far by the writer's own calendar, numbers only (no dates, no shift names), so a 하소연 or a
 * 자랑 has its ground. [days] days of the month gone (today included), [work] days at work, [nights] of them night shifts,
 * [streak] the longest run of days at work, [off] days off. 처리방침 3.12 ④.
 */
data class WorkCard(val days: Int, val work: Int, val nights: Int, val streak: Int, val off: Int) {
    /** The keys the server takes (d, w, n, s, o). */
    fun fields(): Map<String, Int> = mapOf("d" to days, "w" to work, "n" to nights, "s" to streak, "o" to off)

    /** The card as one line: '이번 달 근무 12일 · 야간 6 · 최장 연속 5일 · 쉬는 날 4'. */
    fun line(): String = buildList {
        add("이번 달 근무 ${work}일")
        if (nights > 0) add("야간 $nights")
        if (streak >= 2) add("최장 연속 ${streak}일")
        add("쉬는 날 $off")
    }.joinToString(" · ")

    /** Said to a screen reader, with how far into the month. */
    fun spoken(): String = "근무 카드, 이번 달 ${days}일 동안 " + line().removePrefix("이번 달 ")

    companion object {
        /**
         * The card of the days from the 1st to today, each as (at work, its shift's start hour or null). A day with neither work nor
         * a rest (no schedule) counts as neither. Null before any day at work.
         */
        fun of(month: List<Pair<Boolean?, Int?>>): WorkCard? {
            if (month.isEmpty() || month.none { it.first == true }) return null
            var run = 0
            var best = 0
            for ((work, _) in month) {
                run = if (work == true) run + 1 else 0
                best = maxOf(best, run)
            }
            val work = month.count { it.first == true }
            val nights = month.count { (w, h) -> w == true && h != null && PlazaTags.shiftOf(h) == PlazaTag.Now.NIGHT }
            return WorkCard(month.size.coerceAtMost(31), work, nights, best, month.count { it.first == false })
        }

        fun of(get: (String) -> Any?): WorkCard? = runCatching {
            fun n(k: String) = (get(k) as? Number)?.toInt()?.takeIf { it in 0..31 } ?: error(k)
            WorkCard(n("d"), n("w"), n("n"), n("s"), n("o")).takeIf { it.days >= 1 && it.work + it.off <= it.days && it.nights <= it.work }
        }.getOrNull()
    }
}
