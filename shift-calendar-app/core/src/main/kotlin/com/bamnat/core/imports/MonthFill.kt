package com.bamnat.core.imports

/**
 * [ROST-08] Filling the rest of a month from what was typed (리뷰 개선 다): D D E E N N O O typed from the 1st, then
 * "이 순서 반복" carries the same order to the month's end. Works on day numbers, for any kind of cell.
 */
object MonthFill {
    /**
     * The order typed so far, carried from [from] to [last]: the days typed one after another, ending the day before
     * [from], are the order (at least two). Days already typed after [from] are kept as they are. Null when there is
     * no order to repeat.
     */
    fun <T> repeat(typed: Map<Int, T>, from: Int, last: Int): Map<Int, T>? {
        if (from !in 2..last) return null
        var start = from - 1
        if (start !in typed) return null
        while (start - 1 in typed) start--
        val cycle = (start until from).map { typed.getValue(it) }
        if (cycle.size < 2) return null
        return (from..last).filter { it !in typed }.associateWith { cycle[(it - start) % cycle.size] }.takeIf { it.isNotEmpty() }
    }

    /**
     * 2.0.0 [RVL-39] (검토 U07b-16) How 지난달과 같게 carries last month over: day for day as before ([DATES]), each day from the
     * same weekday four (or five) weeks back ([WEEKDAYS], for a week that repeats), or the user's own order carried on from where
     * last month ended ([ORDER], for D D E E N N O O that does not fit a week and slips a few days each month).
     */
    enum class CopyWay { DATES, WEEKDAYS, ORDER }

    /**
     * The order a run of days repeats: the shortest p in 2..[longest] that agrees nearly as well (to 5%) as the best one, when
     * the best has at least 80% of the days p apart the same (a few days traded still leave the order) and two turns of it were
     * seen. Null for a run with no order, or with one kind of day only. [days]: in order, null where nothing is known.
     */
    fun <T> period(days: List<T?>, longest: Int = 28): Int? {
        if (days.filterNotNull().distinct().size < 2) return null
        val agree = (2..minOf(longest, days.size / 2)).mapNotNull { p ->
            var pairs = 0
            var same = 0
            for (i in p until days.size) {
                val a = days[i] ?: continue
                val b = days[i - p] ?: continue
                pairs++
                if (a == b) same++
            }
            if (pairs < 2 * p) null else p to same.toDouble() / pairs
        }
        val best = agree.maxOfOrNull { it.second } ?: return null
        if (best < 0.8) return null
        return agree.first { it.second >= best - 0.05 }.first
    }

    /**
     * [days] carried on for [count] more days by an order of [p]: each new day what most days at its place in the order were,
     * the latest on a tie. Null where none of them is known.
     */
    fun <T> carry(days: List<T?>, p: Int, count: Int): List<T?> {
        require(p > 0)
        return (0 until count).map { k ->
            val place = (days.size + k) % p
            days.indices.reversed().filter { it % p == place }.mapNotNull { days[it] }
                .groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
        }
    }
}
