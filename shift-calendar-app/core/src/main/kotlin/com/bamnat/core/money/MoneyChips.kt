package com.bamnat.core.money

import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * 1.39 [FLOW-18] 잘 쓴 곳·많이 쓴 곳 (흐름·디자인 계획 3-9): the month's categories read for the user in two rows of chips above the
 * category list, green for the ones kept down and orange for the ones that went up, so the list needs no reading to see what moved.
 *
 * A category is judged against its own budget when it has one (넘음 → 많이, 지난 날만큼보다 덜 씀 → 잘), else against last month —
 * for the month in progress, last month cut to the same share of the month, so the 7th is not set against a whole month.
 * Only a clear change: at least [MIN_GAP] and [MIN_SHARE] of the base. A category new this month is not judged (nothing to set it
 * against), nor is 일감 경비 (work costs, not spending). At most [MAX] a row; nothing at all when there is no month before and no budget.
 */
object MonthChips {
    const val MIN_GAP = 10_000L
    const val MIN_SHARE = 0.2
    const val MAX = 3

    enum class Why { BUDGET_OVER, BUDGET_KEPT, LESS, MORE }

    data class Chip(val category: String, val why: Why, val gap: Long) {
        /** The words on the chip: "식비 3만 원 덜", "배달 예산 넘음". [won] writes an amount the screen's way. */
        fun words(won: (Long) -> String): String = when (why) {
            Why.BUDGET_OVER -> "$category 예산 ${won(gap)} 넘음"
            Why.BUDGET_KEPT -> "$category 예산 안"
            Why.LESS -> "$category ${won(gap)} 덜"
            Why.MORE -> "$category ${won(gap)} 더"
        }
    }

    data class Rows(val good: List<Chip>, val caution: List<Chip>) {
        val isEmpty: Boolean get() = good.isEmpty() && caution.isEmpty()
        /**
         * 2.0.0 [RVK-43] (검토 U06a-27) The first '많이 쓴 곳' with no budget of its own (a 'N원 더' chip; a budget's chips say 예산), for
         * '이 분류 예산 정하기' under the chips: having seen it, there was nothing to do next.
         */
        val toBudget: String? get() = caution.firstOrNull { it.why == Why.MORE }?.category
    }

    /**
     * [r]'s categories; [budgets] the month's budget lines (category to line, the whole month's left out); [today] tells how much of a
     * month in progress has gone by.
     */
    fun of(r: MonthReport, budgets: List<BudgetLine>, today: LocalDate): Rows {
        val now = YearMonth.from(today)
        val gone = when {
            r.month == now -> today.dayOfMonth.toDouble() / r.month.lengthOfMonth()
            r.month.isBefore(now) -> 1.0
            else -> return Rows(emptyList(), emptyList())
        }
        val byBudget = budgets.filter { it.category != MoneyBudget.TOTAL && it.budget > 0 }.associateBy { it.category }
        val good = ArrayList<Chip>()
        val caution = ArrayList<Chip>()
        for (c in r.categories) {
            if (c.category == Reports.JOB_COSTS) continue
            val b = byBudget[c.category]
            if (b != null) {
                when {
                    b.spent > b.budget -> caution += Chip(c.category, Why.BUDGET_OVER, b.spent - b.budget)
                    // Kept: under the share of the budget the days gone by allow (a month gone by: under the whole budget).
                    b.spent <= (b.budget * gone).roundToLong() && b.spent > 0 -> good += Chip(c.category, Why.BUDGET_KEPT, b.budget - b.spent)
                }
                continue
            }
            if (c.previous <= 0) continue
            val base = (c.previous * gone).roundToLong()
            val gap = c.amount - base
            if (abs(gap) < MIN_GAP || abs(gap) < base * MIN_SHARE) continue
            if (gap > 0) caution += Chip(c.category, Why.MORE, gap) else good += Chip(c.category, Why.LESS, -gap)
        }
        // A category spent last month and not at all this month (nothing in r.categories) is kept down too, once the month is over.
        if (gone >= 1.0) {
            val seen = r.categories.map { it.category }.toSet()
            budgets.filter { it.category != MoneyBudget.TOTAL && it.category !in seen && it.budget > 0 }.forEach {
                good += Chip(it.category, Why.BUDGET_KEPT, it.budget)
            }
        }
        fun List<Chip>.top() = sortedWith(compareBy<Chip> { if (it.why == Why.BUDGET_OVER) 0 else 1 }.thenByDescending { it.gap }).take(MAX)
        return Rows(good.top(), caution.top())
    }
}

/**
 * 1.39 [FLOW-20] 근무별 돈 (흐름·디자인 계획 3-9, 혁신): which categories go up on one kind of day — "야간 날 편의점 하루 6,200원 더" — in
 * the two chip rows of [MonthChips], read from the user's own 90 days on the phone only. A kind of day seen fewer than [MIN_TIMES]
 * days, or a category bought fewer than [MIN_TIMES] times on those days, says nothing (수면 원인 찾기 [com.bamnat.core.sleep.SleepCauses]
 * keeps the same five). Only a clear difference: [MIN_RATIO] times the other days or more (or that much less), and [MIN_GAP] a day.
 */
object ShiftMoneyChips {
    const val MIN_TIMES = 5
    const val MIN_RATIO = 1.5
    const val MIN_GAP = 2_000L
    const val MAX_CAUTION = 3
    const val MAX_GOOD = 2

    data class Chip(val dayKind: String, val category: String, val perDay: Long, val otherPerDay: Long, val times: Int) {
        val more: Boolean get() = perDay > otherPerDay
        val gap: Long get() = abs(perDay - otherPerDay)

        /** "야간 날 편의점 하루 6,200원 더" / "쉬는 날 택시 하루 4,000원 덜". */
        fun words(won: (Long) -> String): String = "${dayKind}${if (dayKind.endsWith("날")) "" else " 날"} $category 하루 ${won(gap)} ${if (more) "더" else "덜"}"
    }

    data class Rows(val good: List<Chip>, val caution: List<Chip>) {
        val isEmpty: Boolean get() = good.isEmpty() && caution.isEmpty()
    }

    /** Over [from]..[to], by [dayLabel] (e.g. [com.bamnat.core.tax.TaxPlus.dayLabel]): fixed costs (자동 고정비) are not choices, so left out. */
    fun of(
        entries: List<MoneyEntry>, from: LocalDate, to: LocalDate, dayLabel: (LocalDate) -> String?,
        /** 2.0.0 [RVK-43] (검토 T06b-08) Days whose shift runs past midnight, so what was bought before morning counts on them ([NightSpill]). */
        isNight: (LocalDate) -> Boolean = { false },
    ): Rows {
        val labels = LinkedHashMap<LocalDate, String>()
        var d = from
        while (!d.isAfter(to)) { dayLabel(d)?.let { labels[d] = it }; d = d.plusDays(1) }
        val daysOf = labels.values.groupingBy { it }.eachCount()
        if (daysOf.size < 2) return Rows(emptyList(), emptyList())
        // (label, category) -> amount, times
        val amount = HashMap<Pair<String, String>, Long>()
        val times = HashMap<Pair<String, String>, Int>()
        val categories = HashSet<String>()
        for (e in entries) {
            if (e.kind != MoneyKind.EXPENSE || e.source == MoneySource.FIXED) continue
            val l = labels[NightSpill.dayOf(e, isNight)] ?: continue
            for ((c, v) in e.shares()) {
                if (v <= 0) continue
                categories += c
                amount.merge(l to c, v, Long::plus)
                times.merge(l to c, 1, Int::plus)
            }
        }
        val good = ArrayList<Chip>()
        val caution = ArrayList<Chip>()
        val total = labels.size
        for ((l, n) in daysOf) {
            if (n < MIN_TIMES || total - n < MIN_TIMES) continue
            for (c in categories) {
                val mine = amount[l to c] ?: 0
                val others = daysOf.keys.filter { it != l }.sumOf { amount[it to c] ?: 0 }
                val perDay = mine / n
                val otherPerDay = others / (total - n)
                val chip = Chip(l, c, perDay, otherPerDay, times[l to c] ?: 0)
                if (chip.gap < MIN_GAP) continue
                if (chip.more) {
                    if ((times[l to c] ?: 0) >= MIN_TIMES && perDay >= otherPerDay * MIN_RATIO) caution += chip
                } else {
                    val otherTimes = daysOf.keys.filter { it != l }.sumOf { times[it to c] ?: 0 }
                    if (otherTimes >= MIN_TIMES && otherPerDay >= perDay * MIN_RATIO) good += chip
                }
            }
        }
        // With two kinds of day, "야간 날 편의점 더" and "쉬는 날 편의점 덜" say one thing: the 더 is kept.
        val said = caution.map { it.category }.toSet()
        return Rows(
            good.filter { it.category !in said }.sortedByDescending { it.gap }.distinctBy { it.category }.take(MAX_GOOD),
            caution.sortedByDescending { it.gap }.distinctBy { it.category }.take(MAX_CAUTION),
        )
    }
}

/**
 * 2.0.0 [RVK-43] (검토 T06b-08) The day whose work a spending line belongs to: bought before [UNTIL] the morning after a night shift
 * (02:00 편의점 during a 야간 that began the evening before), the night's day; otherwise its own date. A line without a time stays
 * on its date.
 */
object NightSpill {
    val UNTIL: java.time.LocalTime = java.time.LocalTime.of(9, 0)

    fun dayOf(e: MoneyEntry, isNight: (LocalDate) -> Boolean): LocalDate {
        val t = e.time ?: return e.date
        return if (t.isBefore(UNTIL) && isNight(e.date.minusDays(1))) e.date.minusDays(1) else e.date
    }
}
