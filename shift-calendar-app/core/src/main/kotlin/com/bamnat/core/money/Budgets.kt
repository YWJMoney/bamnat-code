package com.bamnat.core.money

import java.time.LocalDate
import java.time.YearMonth

/** [LEDG-06] A monthly budget: for the whole month ([TOTAL]) or one spending category. Repeats every month. */
data class MoneyBudget(val category: String, val amount: Long) {
    val isTotal: Boolean get() = category == TOTAL

    companion object {
        const val TOTAL = ""
    }
}

enum class BudgetLevel { OK, NEAR, OVER }

/** [LEDG-06] One budget against what was spent, and where the month's pace is heading. */
data class BudgetLine(
    val category: String, val budget: Long, val spent: Long, val forecast: Long,
    /** 1.37 [LEDH-34] What came over from the stretch before (left over, or below zero when it went over); inside [budget]. */
    val carried: Long = 0,
) {
    val left: Long get() = budget - spent
    val fraction: Float get() = if (budget <= 0) 0f else spent.toFloat() / budget
    val level: BudgetLevel get() = when {
        spent > budget -> BudgetLevel.OVER
        fraction >= Budgets.NEAR_AT -> BudgetLevel.NEAR
        else -> BudgetLevel.OK
    }
    /** On course to go over by the month's end, though not over yet. */
    val headedOver: Boolean get() = level != BudgetLevel.OVER && forecast > budget
    val isTotal: Boolean get() = category == MoneyBudget.TOTAL
}

/** [LEDG-06] A month's budgets: the whole, each category with one, and what is left per day. */
data class BudgetStatus(
    val month: YearMonth,
    /** The whole month; the sum of the category budgets when no whole-month budget is set; null with no budget. */
    val total: BudgetLine?,
    /** Categories with a budget, the ones over or nearest to it first. */
    val categories: List<BudgetLine>,
    /** Days left including today for this month, every day for a later month, 0 for one gone by. */
    val daysLeft: Int,
    /** What may still be spent each day from today on, after the fixed costs still to leave; null without a budget or for a past month. */
    val perDay: Long?,
    val todaySpent: Long,
    /** Fixed costs still to leave this month, kept aside in [perDay]. */
    val fixedLeft: Long,
    /** The share of the month gone by, 0..1, to set the money against the days. */
    val elapsed: Float,
    /** 1.37 [LEDH-06] The first and last day counted: the month's, or a 급여일 주기's. */
    val from: LocalDate = month.atDay(1),
    val to: LocalDate = month.atEndOfMonth(),
) {
    /** 1.37 [LEDH-06] Counted over a 급여일 주기 rather than the calendar month. */
    val isCycle: Boolean get() = from != month.atDay(1) || to != month.atEndOfMonth()
    val hasBudget: Boolean get() = total != null
    val over: List<BudgetLine> get() = categories.filter { it.level == BudgetLevel.OVER }
    /** What is left for today itself; below zero once today went past its share. */
    val todayLeft: Long? get() = perDay?.let { it - todaySpent }
}

object Budgets {
    /** A budget 80% used is close to its end. */
    const val NEAR_AT = 0.8f

    /** One line this big is a lump (자재비, 가전, 병원비): it counts in what is spent, not in the daily pace. */
    const val LUMP = 100_000L

    /**
     * [LEDG-06] Budgets against the ledger's spending in [month] (job costs and transfers are not
     * spending here). A split payment counts under each of its categories [LEDG-11]. The forecast
     * adds the pace of everyday spending to what is spent, without repeating lines, which come
     * once ([fixedLeft] is added to the whole month's).
     */
    fun status(budgets: List<MoneyBudget>, entries: List<MoneyEntry>, month: YearMonth, today: LocalDate, fixedLeft: Long = 0,
               carry: Map<String, Long> = emptyMap()): BudgetStatus =
        statusIn(budgets, entries, month.atDay(1), month.atEndOfMonth(), today, fixedLeft, carry)

    /**
     * 1.37 [LEDH-34] 예산 넘김: for each category in [rolling] with a budget, what the stretch just before [from] left over (or went over,
     * below zero), at most one budget either way. Only one stretch back, so a long-forgotten month does not pile up.
     */
    fun carry(budgets: List<MoneyBudget>, entries: List<MoneyEntry>, from: LocalDate, to: LocalDate, rolling: Set<String>): Map<String, Long> {
        if (rolling.isEmpty()) return emptyMap()
        val length = java.time.temporal.ChronoUnit.DAYS.between(from, to) + 1
        val monthly = from.dayOfMonth == 1 && to == YearMonth.from(from).atEndOfMonth()
        val prevFrom = if (monthly) YearMonth.from(from).minusMonths(1).atDay(1) else from.minusDays(length)
        val prevTo = from.minusDays(1)
        val spent = HashMap<String, Long>()
        entries.filter { it.kind == MoneyKind.EXPENSE && !it.date.isBefore(prevFrom) && !it.date.isAfter(prevTo) }
            .forEach { e -> e.shares().forEach { (c, a) -> spent.merge(c, a, Long::plus) } }
        // Nothing written that stretch: nothing to carry (the app was not in use yet).
        if (entries.none { !it.date.isBefore(prevFrom) && !it.date.isAfter(prevTo) }) return emptyMap()
        return budgets.filter { !it.isTotal && it.amount > 0 && it.category in rolling }.associate { b ->
            b.category to (b.amount - (spent[b.category] ?: 0)).coerceIn(-b.amount, b.amount)
        }.filterValues { it != 0L }
    }

    /**
     * 1.37 [LEDH-06] The same budgets over any stretch of days, such as 급여일 주기 (25일~다음 달 24일): the monthly
     * amounts stand for the stretch, as a month's pay stands for the month it covers.
     */
    fun statusIn(budgets: List<MoneyBudget>, entries: List<MoneyEntry>, from: LocalDate, to: LocalDate, today: LocalDate, fixedLeft: Long = 0,
                 carry: Map<String, Long> = emptyMap()): BudgetStatus {
        val month = YearMonth.from(to)
        val spent = entries.filter { it.kind == MoneyKind.EXPENSE && !it.date.isBefore(from) && !it.date.isAfter(to) }
        val byCategory = HashMap<String, Long>()
        val everyday = HashMap<String, Long>()
        for (e in spent) for ((c, a) in e.shares()) {
            byCategory.merge(c, a, Long::plus)
            // PC 확인 (자재비 25만을 한 번 적었더니 '이대로면 약 125만원 넘을 것 같아요'): one big line ([LUMP] or more) is counted once,
            // not as a pace that repeats every day.
            if (e.source != MoneySource.FIXED && e.amount < LUMP) everyday.merge(c, a, Long::plus)
        }
        val length = (java.time.temporal.ChronoUnit.DAYS.between(from, to) + 1).toInt().coerceAtLeast(1)
        val current = !today.isBefore(from) && !today.isAfter(to)
        val dayIndex = (java.time.temporal.ChronoUnit.DAYS.between(from, today) + 1).toInt()
        val gone = when { current -> dayIndex; to.isBefore(today) -> length; else -> 0 }
        val daysLeft = when { current -> length - dayIndex + 1; from.isAfter(today) -> length; else -> 0 }
        val after = if (current) length - dayIndex else 0
        fun forecast(spentSoFar: Long, everydaySoFar: Long): Long =
            if (!current || gone == 0) spentSoFar else spentSoFar + (everydaySoFar.coerceAtLeast(0) * after / gone)
        val fixedAside = if (current) fixedLeft else 0
        val all = spent.sumOf { it.amount }
        val explicit = budgets.firstOrNull { it.isTotal && it.amount > 0 }?.amount
        val categoryBudgets = budgets.filter { !it.isTotal && it.amount > 0 }.map { b -> carry[b.category]?.let { b.copy(amount = (b.amount + it).coerceAtLeast(0)) } ?: b }
        val totalBudget = explicit ?: categoryBudgets.sumOf { it.amount }.takeIf { it > 0 }
        val total = totalBudget?.let { BudgetLine(MoneyBudget.TOTAL, it, all, forecast(all, everyday.values.sum()) + fixedAside) }
        val lines = categoryBudgets.map { b ->
            BudgetLine(b.category, b.amount, byCategory[b.category] ?: 0, forecast(byCategory[b.category] ?: 0, everyday[b.category] ?: 0), carry[b.category] ?: 0)
        }.sortedWith(compareBy<BudgetLine> { it.level != BudgetLevel.OVER }.thenByDescending { it.fraction }.thenBy { it.category })
        val todaySpent = if (current) spent.filter { it.date == today }.sumOf { it.amount } else 0
        val perDay = if (total == null || daysLeft == 0) null else
            ((total.budget - (all - todaySpent) - fixedAside) / daysLeft).coerceAtLeast(0)
        return BudgetStatus(month, total, lines, daysLeft, perDay, todaySpent, fixedAside, gone.toFloat() / length, from, to)
    }

    /**
     * [LEDG-06] The budgets a new line pushes over: categories (and the whole month) that were
     * within their budget before [entry] and are over it with it, so the app can say so once.
     */
    fun crossedBy(
        entry: MoneyEntry, budgets: List<MoneyBudget>, entries: List<MoneyEntry>, today: LocalDate,
        /** 2.0.0 [RVK-41] (검토 MONEY-53) The 급여일 주기 holding the line's day when the budget is seen by pay cycle. */
        cycle: PayCycle.Range? = null,
        /** The categories that carry the last stretch's leftover ([carry]), as the budget screen counts them. */
        rolling: Set<String> = emptySet(),
    ): List<BudgetLine> {
        if (entry.kind != MoneyKind.EXPENSE || entry.amount <= 0) return emptyList()
        val month = YearMonth.from(entry.date)
        // 2.0.0 [RVK-41] (검토 MONEY-53) Over the same days and with the same 넘김 as the budget screen, so '예산을 넘었어요' comes when
        // the screen says over, not by 1일~말일 and without the carry.
        val range = cycle?.takeIf { it.contains(entry.date) }
        val from = range?.from ?: month.atDay(1)
        val to = range?.to ?: month.atEndOfMonth()
        val carry = if (rolling.isEmpty()) emptyMap() else carry(budgets, entries, from, to, rolling)
        fun at(list: List<MoneyEntry>) = if (range != null) statusIn(budgets, list, from, to, today, carry = carry) else status(budgets, list, month, today, carry = carry)
        // An edited line replaces the one it was; a new one is added.
        val before = at(entries)
        val after = at(entries.filter { entry.id == 0L || it.id != entry.id } + entry)
        val was = (listOfNotNull(before.total) + before.categories).associateBy { it.category }
        return (listOfNotNull(after.total) + after.categories).filter { it.level == BudgetLevel.OVER && was[it.category]?.level != BudgetLevel.OVER }
    }
}
