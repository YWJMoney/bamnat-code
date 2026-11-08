package com.bamnat.core.money

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 1.37 묶음 H "근무가 보이는 가계부" (조사 `조사/1.36-가계부-혁신-조사.md` 3장 A).
 *
 * [LEDH-01] 미래 잔액: from today, each day's money in the accounts that hold spendable money, with what the
 * calendar already knows is coming: pay worked out by the pay engine from the shifts still ahead (the app
 * passes it in as [PayDay]s, so there is no second pay formula here), jobs' promised payments, money owed
 * with a promised day, repeating lines and card bills, and the everyday spending of the last 90 days spread
 * over each day. Everything stays on the phone.
 */
enum class FlowKind(val label: String, val incoming: Boolean) {
    PAY("월급", true),
    JOB("일감 입금", true),
    OWED("받을 돈", true),
    CARD_SALES("카드 매출 입금", true),
    FIXED_IN("들어올 돈", true),
    FIXED_OUT("고정 지출", false),
    CARD_BILL("카드값", false),
    /** 1.38 [LEDH-25] A loan's monthly payment, from the account it is paid from. */
    LOAN("대출 갚기", false),
}

/** One known movement of money on [date]; [amount] is positive coming in and negative going out. */
data class MoneyFlow(val date: LocalDate, val amount: Long, val kind: FlowKind, val label: String)

/** [LEDH-01] A pay arriving on [date], take-home ([net]) as the pay engine worked it out for [label] (e.g. "11월 급여"). */
data class PayDay(val date: LocalDate, val net: Long, val label: String, val dateGuessed: Boolean = false)

/** [LEDH-01] One day of the forecast: the balance at the day's end and what was known to move. */
data class AheadDay(val date: LocalDate, val balance: Long, val flows: List<MoneyFlow>, val everyday: Long)

data class MoneyForecast(
    val today: LocalDate,
    /** Spendable money now (통장·현금·페이), or 0 with [hasBalance] false when no account keeps one. */
    val start: Long,
    val hasBalance: Boolean,
    val days: List<AheadDay>,
    /** The everyday spending counted for each day ahead. */
    val everydayPerDay: Long,
    /** True when fewer than two weeks of lines were there to learn the everyday spending from. */
    val everydayGuessed: Boolean,
    /** Jobs and money owed already past their promised day: not counted, said apart. */
    val late: Long,
    /** A repeating 급여 line was left out because the pay engine's pay stands for it. */
    val fixedPayReplaced: Boolean,
) {
    val flows: List<MoneyFlow> get() = days.flatMap { it.flows }
    val end: AheadDay? get() = days.lastOrNull()
    val lowest: AheadDay? get() = days.minByOrNull { it.balance }
    /** The first day the money runs below zero, if it does. */
    val firstBelowZero: AheadDay? get() = if (!hasBalance) null else days.firstOrNull { it.balance < 0 }
    val incoming: Long get() = flows.filter { it.amount > 0 }.sumOf { it.amount }
    val outgoing: Long get() = -flows.filter { it.amount < 0 }.sumOf { it.amount } + days.sumOf { it.everyday }

    /**
     * [LEDH-05] A card bill that leaves before the pay that would cover it: the bill's day ends below zero
     * and pay arrives within a week after it. "카드값이 월급보다 이틀 먼저예요".
     */
    val billsBeforePay: List<Pair<MoneyFlow, MoneyFlow>> get() {
        if (!hasBalance) return emptyList()
        val pays = flows.filter { it.kind == FlowKind.PAY }
        return days.filter { it.balance < 0 }.flatMap { d ->
            d.flows.filter { it.kind == FlowKind.CARD_BILL }.mapNotNull { bill ->
                pays.firstOrNull { p -> p.date.isAfter(bill.date) && ChronoUnit.DAYS.between(bill.date, p.date) <= 7 }?.let { bill to it }
            }
        }
    }

    /** The day [n] days from today, or the last one. */
    fun on(date: LocalDate): AheadDay? = days.firstOrNull { it.date == date }

    /**
     * [LEDH-02] 다음 월급날(or next money in)까지 하루 쓸 돈: the money now, less what must leave before then and
     * plus what else arrives before then, spread over the days left including today. Null without account balances.
     */
    /**
     * [todaySpent] is all spent today (taken off today's share); [fromStart] the part of it that already came out of [start] (lines on a
     * spendable account), which is put back to count the morning's money. PC 확인 (통장 잔액이 기록과 안 이어졌는데 '오늘 아침 통장
     * 555,000원'으로 거꾸로 셈): a line on no account never left the balance, so it is not added back.
     */
    fun untilNext(kinds: Set<FlowKind> = setOf(FlowKind.PAY), todaySpent: Long = 0, fromStart: Long = todaySpent): UntilNext? {
        val next = flows.firstOrNull { it.kind in kinds && it.date.isAfter(today) } ?: return null
        val daysLeft = ChronoUnit.DAYS.between(today, next.date).toInt().coerceAtLeast(1)
        val before = flows.filter { it.date.isBefore(next.date) }
        val reserved = -before.filter { it.amount < 0 }.sumOf { it.amount }
        val arriving = before.filter { it.amount > 0 }.sumOf { it.amount }
        val perDay = if (!hasBalance) null else ((start + fromStart + arriving - reserved) / daysLeft).coerceAtLeast(0)
        return UntilNext(next, daysLeft, perDay, reserved, arriving, perDay?.let { it - todaySpent })
    }
}

/** [LEDH-02] How long until [next] money arrives and what each day may take until then. */
data class UntilNext(
    val next: MoneyFlow,
    /** Days from today up to the day before [next] arrives. */
    val daysLeft: Int,
    val perDay: Long?,
    /** What leaves before then (fixed costs, card bills), kept aside. */
    val reserved: Long,
    val arriving: Long,
    /** What is left for today itself, after what was already spent today. */
    val todayLeft: Long?,
) {
    /** "월급까지 9일", "다음 입금까지 3일" */
    val words: String get() = (if (next.kind == FlowKind.PAY) "월급" else "다음 입금") + "까지 ${daysLeft}일"
}

object MoneyAhead {
    /** 1.37 결정 5(추천): 30 days free; 90 with the 7-day pass or 프로. */
    const val FREE_DAYS = 30
    const val FULL_DAYS = 90
    const val LOOKBACK_DAYS = 90
    private const val MIN_LEARN_DAYS = 14

    /** Account types whose money can be spent this month: not 적금·상품권 (기타) and not cards. */
    fun spendable(a: MoneyAccount): Boolean = !a.hidden && a.keepsBalance && a.type != AccountType.OTHER && !a.isLoan

    /** [LEDH-01] The money in spendable accounts now, or null when there is none to count. */
    /**
     * What left the accounts [startBalance] counts on [today], net: the morning's balance less now's. PC 확인 46200ae4 (오늘 아침 통장이
     * 카드·현금 지출을 더하고 통장에서 나간 이체는 빼먹음): worked out from the balances themselves, so only what really moved counts.
     */
    fun spentFromStart(accounts: List<MoneyAccount>, entries: List<MoneyEntry>, today: LocalDate): Long {
        val now = startBalance(accounts, entries) ?: return 0
        val morning = startBalance(accounts, entries.filter { it.date != today }) ?: return 0
        return morning - now
    }

    fun startBalance(accounts: List<MoneyAccount>, entries: List<MoneyEntry>): Long? {
        val ids = accounts.filter(::spendable).map { it.id }.toSet()
        if (ids.isEmpty()) return null
        return MoneyAccounts.balances(accounts, entries).filterKeys { it in ids }.values.sum()
    }

    /**
     * [LEDH-01] Everyday spending per day over the last [LOOKBACK_DAYS] days before today: expenses that are not
     * repeating lines, over the days since the first line (at least one day). The bool is true when fewer than two
     * weeks were there to learn from.
     */
    fun everydayPerDay(entries: List<MoneyEntry>, today: LocalDate): Pair<Long, Boolean> {
        val from = today.minusDays(LOOKBACK_DAYS.toLong())
        val lines = entries.filter {
            it.kind == MoneyKind.EXPENSE && it.source != MoneySource.FIXED && it.fixedId == null && it.amount > 0 &&
                !it.date.isBefore(from) && it.date.isBefore(today)
        }
        if (lines.isEmpty()) return 0L to true
        val first = lines.minOf { it.date }
        val span = ChronoUnit.DAYS.between(first, today).toInt().coerceAtLeast(1)
        return lines.sumOf { it.amount } / span to (span < MIN_LEARN_DAYS)
    }

    private fun isPayLine(c: FixedCost) = c.kind == MoneyKind.INCOME && (c.category == MoneyCategories.SALARY || c.name.contains("월급") || c.name.contains("급여"))

    /**
     * [LEDH-01] Every known movement from [today] to [until]. A pay day already written down (an income line on
     * that day of at least half the pay) is not counted again; pay days before today are gone.
     */
    fun flows(book: MoneyBook, today: LocalDate, until: LocalDate, pay: List<PayDay>, extra: List<MoneyFlow> = emptyList()): Pair<List<MoneyFlow>, Boolean> {
        val payFlows = pay.filter { p -> !p.date.isBefore(today) && !p.date.isAfter(until) && p.net > 0 }
            .filterNot { p -> p.date == today && book.entries.any { it.kind == MoneyKind.INCOME && it.date == today && it.amount * 2 >= p.net } }
            .map { MoneyFlow(it.date, it.net, FlowKind.PAY, it.label) }
        val replacePay = pay.isNotEmpty()
        val days = ChronoUnit.DAYS.between(today, until).toInt()
        val fixed = UpcomingPayments.within(book.fixed.filterNot { replacePay && isPayLine(it) }, book.entries, today, days, book.accounts)
            .mapNotNull { u ->
                val c = u.cost
                when {
                    u.cardId != null || (c.kind == MoneyKind.TRANSFER && c.category == UpcomingPayments.CARD_BILL) ->
                        MoneyFlow(u.date, -c.amount, FlowKind.CARD_BILL, "${c.name} ${UpcomingPayments.CARD_BILL}".trim())
                    c.kind == MoneyKind.INCOME -> MoneyFlow(u.date, c.amount, FlowKind.FIXED_IN, c.name)
                    c.kind == MoneyKind.EXPENSE -> MoneyFlow(u.date, -c.amount, FlowKind.FIXED_OUT, c.name)
                    // Between the user's own accounts nothing leaves; to savings, a loan or the owner it does.
                    c.category == "내 계좌" -> null
                    else -> MoneyFlow(u.date, -c.amount, FlowKind.FIXED_OUT, c.name)
                }
            }
        // 1.37 PC 6차: 가계약은 아직 정해진 돈이 아니라 앞으로 들어올 돈에 넣지 않아요.
        val jobs = book.jobs.filter { it.unpaid > 0 && it.status != JobStatus.TENTATIVE }.mapNotNull { j ->
            val due = j.due ?: j.lastDate
            if (due.isBefore(today) || due.isAfter(until)) null else MoneyFlow(due, j.unpaid, FlowKind.JOB, j.title.ifBlank { j.client })
        }
        val owed = book.receivables.filter { it.left > 0 && it.due != null }.mapNotNull { r ->
            val due = r.due!!
            if (due.isBefore(today) || due.isAfter(until)) null else MoneyFlow(due, r.left, FlowKind.OWED, r.name)
        }
        // 1.38 [LEDH-25] Each loan's monthly payment on its day, while something is owed, unless a repeating line already pays it.
        val balances = MoneyAccounts.balances(book.accounts, book.entries)
        val loans = book.accounts.filter { it.isLoan && !it.hidden && it.monthlyPay > 0 && it.payDay != null }.flatMap { loan ->
            if (book.fixed.any { f -> f.name.contains(loan.name) }) return@flatMap emptyList()
            var owedLeft = Debts.left(loan, balances)
            var m = Debts.firstMonth(loan, today)
            val out = mutableListOf<MoneyFlow>()
            while (owedLeft > 0) {
                val d = m.atDay(loan.payDay!!.coerceAtMost(m.lengthOfMonth()))
                if (d.isAfter(until)) break
                val pay = minOf(loan.monthlyPay, owedLeft + Debts.split(owedLeft, loan.rate, loan.monthlyPay).first)
                out += MoneyFlow(d, -pay, FlowKind.LOAN, loan.name)
                owedLeft -= Debts.split(owedLeft, loan.rate, pay).second
                m = m.plusMonths(1)
            }
            out
        }
        val all = (payFlows + fixed + jobs + owed + loans + extra.filter { !it.date.isBefore(today) && !it.date.isAfter(until) })
            .sortedWith(compareBy<MoneyFlow>({ it.date }, { it.amount < 0 }))
        return all to (replacePay && book.fixed.any(::isPayLine))
    }

    /** [LEDH-01] Late money: jobs and money owed past their promised day. */
    fun late(book: MoneyBook, today: LocalDate): Long =
        book.jobs.filter { it.unpaid > 0 && it.status != JobStatus.TENTATIVE && (it.due ?: it.lastDate).isBefore(today) }.sumOf { it.unpaid } +
            book.receivables.filter { it.left > 0 && it.due?.isBefore(today) == true }.sumOf { it.left }

    /**
     * [LEDH-01] The forecast for [days] days from [today]. [everyday] overrides the learned everyday spending
     * (0 leaves it out). Today counts only what of the day's share is not spent yet.
     */
    fun forecast(
        book: MoneyBook, today: LocalDate, days: Int, pay: List<PayDay>,
        extra: List<MoneyFlow> = emptyList(), everyday: Long? = null,
    ): MoneyForecast {
        val n = days.coerceIn(1, 366)
        val until = today.plusDays((n - 1).toLong())
        val (flows, replaced) = flows(book, today, until, pay, extra)
        val byDate = flows.groupBy { it.date }
        val (learned, guessed) = everydayPerDay(book.entries, today)
        val perDay = (everyday ?: learned).coerceAtLeast(0)
        val todaySpent = book.entries.filter { it.date == today && it.kind == MoneyKind.EXPENSE && it.source != MoneySource.FIXED }.sumOf { it.amount }
        val start = startBalance(book.accounts, book.entries)
        var balance = start ?: 0
        val out = (0 until n).map { i ->
            val d = today.plusDays(i.toLong())
            val f = byDate[d].orEmpty()
            val ev = if (i == 0) (perDay - todaySpent).coerceAtLeast(0) else perDay
            balance += f.sumOf { it.amount } - ev
            AheadDay(d, balance, f, ev)
        }
        return MoneyForecast(today, start ?: 0, start != null, out, perDay, everyday == null && guessed, late(book, today), replaced)
    }
}

/**
 * [LEDH-06] 급여일 주기: the days from the last pay day up to the day before the next, the way shift and part-time
 * workers live, given the pay days around [today] (the app gives them from [com.bamnat.core.pay.PayPeriod.payDate]).
 */
object PayCycle {
    data class Range(val from: LocalDate, val to: LocalDate) {
        val days: Int get() = ChronoUnit.DAYS.between(from, to).toInt() + 1
        fun contains(d: LocalDate) = !d.isBefore(from) && !d.isAfter(to)
        /** "10/25~11/24" */
        val label: String get() = "${from.monthValue}/${from.dayOfMonth}~${to.monthValue}/${to.dayOfMonth}"
    }

    /** The cycle holding [today], or null when there is no pay day on or before it and one after it. */
    fun around(today: LocalDate, payDays: List<LocalDate>): Range? {
        val sorted = payDays.distinct().sorted()
        val last = sorted.lastOrNull { !it.isAfter(today) } ?: return null
        val next = sorted.firstOrNull { it.isAfter(today) } ?: return null
        return Range(last, next.minusDays(1))
    }

    /** The pay days of the months around [today] from a monthly pay day (1..31, past the month's end its last day). */
    fun monthlyPayDays(today: LocalDate, payDay: Int, shift: (LocalDate) -> LocalDate = { it }): List<LocalDate> =
        (-2L..2L).map { java.time.YearMonth.from(today).plusMonths(it) }.map { m -> shift(m.atDay(payDay.coerceIn(1, m.lengthOfMonth()))) }

    /** [LEDH-06] A pay day on a weekend comes the Friday before, as most Korean payrolls do. */
    fun weekdayBefore(d: LocalDate): LocalDate = when (d.dayOfWeek) {
        java.time.DayOfWeek.SATURDAY -> d.minusDays(1)
        java.time.DayOfWeek.SUNDAY -> d.minusDays(2)
        else -> d
    }
}

/**
 * [LEDH-07] 근무 시간 환산: money as the work it took, from the user's own take-home per hour (the pay engine's
 * net pay over the minutes it counted). 결정 4(추천): only for spending of [DEFAULT_FROM] and more, and it can be
 * turned off. 자영업 reads it as a share of a day's sales, 현장 as 공수.
 */
object WorkTime {
    const val DEFAULT_FROM = 50_000L
    /** Fewer than ten hours of work in the month is too little to know an hourly take-home from. */
    private const val MIN_MINUTES = 600

    /** Take-home per hour, or null when it cannot be known. */
    fun hourly(net: Long, workedMinutes: Int): Long? =
        if (net <= 0 || workedMinutes < MIN_MINUTES) null else net * 60 / workedMinutes

    /** "근무 40분", "근무 1.5시간", "근무 12시간" for [amount] at [hourly] won an hour. */
    fun words(amount: Long, hourly: Long): String? {
        if (hourly <= 0 || amount <= 0) return null
        val minutes = amount * 60.0 / hourly
        return when {
            minutes < 60 -> "근무 ${(Math.round(minutes / 5.0) * 5).coerceAtLeast(5)}분"
            minutes < 600 -> "근무 ${trim(Math.round(minutes / 6.0) / 10.0)}시간"
            else -> "근무 ${Math.round(minutes / 60.0)}시간"
        }
    }

    /** 현장: "공수 0.3" for [amount] at a day's pay of [dayRate]. */
    fun units(amount: Long, dayRate: Long): String? =
        if (dayRate <= 0 || amount <= 0) null else "공수 ${trim(Math.round(amount * 10.0 / dayRate) / 10.0).let { if (it == "0") "0.1" else it }}"

    /** 자영업: "하루 매출의 12%" for [amount] against an average day's sales of [daySales]. */
    fun salesShare(amount: Long, daySales: Long): String? =
        if (daySales <= 0 || amount <= 0) null else "하루 매출의 ${Math.round(amount * 100.0 / daySales).coerceAtLeast(1)}%"

    /** Whether [amount] is worth saying in work at the threshold [from] (0 = off). */
    fun shows(amount: Long, from: Long): Boolean = from > 0 && amount >= from

    private fun trim(v: Double): String = if (v == Math.floor(v)) v.toLong().toString() else v.toString()
}
