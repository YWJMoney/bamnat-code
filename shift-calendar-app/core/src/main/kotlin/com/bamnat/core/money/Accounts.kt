package com.bamnat.core.money

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/** [LEDG-08] What kind of place money is kept or paid from. */
enum class AccountType(val label: String, val hint: String) {
    CASH("현금", "지갑에 있는 돈"),
    BANK("통장", "월급 통장, 생활비 통장"),
    CHECK("체크카드", "쓰면 바로 연결한 통장에서 나가요"),
    CREDIT("신용카드", "쓴 돈이 결제일에 한 번에 나가요"),
    PAY("페이·충전", "페이 머니, 교통카드 충전"),
    OTHER("기타", "적금, 상품권 등"),
    /** 1.38 [LEDH-25] 빚: its balance is below zero by what is still owed; a transfer to it pays it down. */
    LOAN("대출·빌린 돈", "남은 원금을 적으면 다 갚는 달과, 더 갚으면 줄어드는 이자를 보여 줘요"),
}

/**
 * [LEDG-08] Cash, a bank account, a card or a pay balance. A credit card's spending leaves on its
 * 결제일 ([payDay]) for the days up to its 마감일 ([closeDay]); a check card's leaves its linked
 * account ([payFromId]) at once. Accounts that hold money keep a balance from [opening].
 */
data class MoneyAccount(
    val id: Long = 0,
    val name: String,
    val type: AccountType,
    /** Last four digits of the card, so payment texts find it by themselves. */
    val last4: String = "",
    /** A credit card's 결제일, 1..31 (past the month's end is its last day). */
    val payDay: Int? = null,
    /** The last day a bill covers (마감일); null for the month before's last day, the usual 1일~말일. */
    val closeDay: Int? = null,
    /** The account a card's money leaves from: a check card's at once, a credit card's bill on its day. */
    val payFromId: Long? = null,
    /** What was in it when the user began counting here. */
    val opening: Long = 0,
    val color: Int = 0,
    val order: Int = 0,
    /** Put away: kept for its old lines but not offered any more. */
    val hidden: Boolean = false,
    /** A credit card's bill three days ahead and on the day, like the fixed costs [LEDG-05]. */
    val remind: Boolean = true,
    /** 1.38 [LEDH-25] 대출·빌린 돈: 연 이자율(%)과 매달 갚는 돈. 0이면 이자 없음·정하지 않음. */
    val rate: Double = 0.0,
    val monthlyPay: Long = 0,
    /** 1.38 [LEDH-27] 카드의 전월 실적 구간(원, 작은 것부터)과 실적에 들지 않는 분류. */
    val perfTiers: List<Long> = emptyList(),
    val perfSkip: List<String> = emptyList(),
) {
    val isCredit: Boolean get() = type == AccountType.CREDIT
    val isCard: Boolean get() = type == AccountType.CREDIT || type == AccountType.CHECK
    /** Money sits in it, so it has a balance; a card only moves money from elsewhere. */
    val keepsBalance: Boolean get() = !isCard
    val hasBill: Boolean get() = isCredit && payDay != null
    /** 1.38 [LEDH-25] A loan: kept as a balance below zero, never money to spend. */
    val isLoan: Boolean get() = type == AccountType.LOAN

    /** "신한 Deep (1234)" */
    val label: String get() = if (last4.isNotBlank() && !name.contains(last4)) "$name ($last4)" else name

    companion object {
        const val MAX_NAME = 20
    }
}

object MoneyAccounts {
    /**
     * [LEDG-08] The account a line belongs to: the one picked for it, or else the card whose last
     * four digits the payment text carried, so lines read before the card was added find it too.
     */
    fun of(e: MoneyEntry, accounts: List<MoneyAccount>): Long? {
        e.accountId?.let { id -> if (accounts.any { it.id == id }) return id }
        val l4 = e.cardLast4.takeIf { it.length == 4 } ?: return null
        val same = accounts.filter { it.last4 == l4 }
        return (same.firstOrNull { it.isCard } ?: same.firstOrNull())?.id
    }

    /**
     * 2.0.0 [RVK-18] (검토 U06a-09): '지금 잔액으로 맞추기' — the one line that brings [account]'s counted balance [counted] to the
     * [actual] amount in it, as a transfer (so no month's 수입·지출 changes): money that left without a line goes out of it, money
     * that came without a line comes into it. Null when they already agree or the account does not hold money.
     */
    fun adjustment(account: MoneyAccount, counted: Long, actual: Long, on: LocalDate): MoneyEntry? {
        if (!account.keepsBalance || account.isLoan || actual == counted) return null
        val diff = actual - counted
        return MoneyEntry(
            date = on, kind = MoneyKind.TRANSFER, amount = kotlin.math.abs(diff), category = LedgerSmarts.TRANSFER_CATEGORY,
            merchant = ADJUST_NAME, note = "실제 잔액에 맞춤", source = MoneySource.MANUAL, reviewed = true,
            accountId = account.id.takeIf { diff < 0 }, toAccountId = account.id.takeIf { diff > 0 },
        )
    }

    /** 2.0.0 [RVK-18] What a balance line is called in the list. */
    const val ADJUST_NAME = "잔액 맞춤"

    /** The account a payment text belongs to when it is saved, or null when no card matches. */
    fun match(cardLast4: String, accounts: List<MoneyAccount>): Long? =
        of(MoneyEntry(date = LocalDate.MIN, kind = MoneyKind.EXPENSE, amount = 0, category = "", cardLast4 = cardLast4), accounts)

    /**
     * [LEDG-08] What is in each account that holds money: its opening amount, plus money in, less
     * money out. A check card's spending leaves its linked account; a credit card's only leaves on
     * its bill, which is a transfer to the card. Transfers move money and are never spending.
     */
    fun balances(accounts: List<MoneyAccount>, entries: List<MoneyEntry>): Map<Long, Long> {
        val byId = accounts.associateBy { it.id }
        val out = accounts.filter { it.keepsBalance }.associate { it.id to it.opening }.toMutableMap()
        fun holder(id: Long?): Long? {
            val a = byId[id ?: return null] ?: return null
            return when {
                a.keepsBalance -> a.id
                a.type == AccountType.CHECK -> a.payFromId?.takeIf { byId[it]?.keepsBalance == true }
                else -> null
            }
        }
        for (e in entries) {
            val from = holder(of(e, accounts))
            when (e.kind) {
                MoneyKind.EXPENSE -> from?.let { out.merge(it, -e.amount, Long::plus) }
                MoneyKind.INCOME -> from?.let { out.merge(it, e.amount, Long::plus) }
                MoneyKind.TRANSFER -> {
                    from?.let { out.merge(it, -e.amount, Long::plus) }
                    holder(e.toAccountId)?.let { out.merge(it, e.amount, Long::plus) }
                }
            }
        }
        return out
    }

    /** [LEDG-13] The account a new line of [kind] starts on: the one the last such line used. */
    fun lastUsed(entries: List<MoneyEntry>, kind: MoneyKind, accounts: List<MoneyAccount>): Long? {
        val live = accounts.filter { !it.hidden }.map { it.id }.toSet()
        return entries.asSequence().filter { it.kind == kind && it.source == MoneySource.MANUAL }
            .sortedWith(compareByDescending<MoneyEntry> { it.date }.thenByDescending { it.id })
            .mapNotNull { it.accountId }.firstOrNull { it in live }
    }
}

/**
 * [LEDG-08] A credit card's bills. The bill paid in a month covers the days up to that cycle's
 * 마감일: in the month before when the 마감일 is on or after the 결제일 (결제일 14일, 1일~말일 쓴 것),
 * or in the same month when it comes earlier (결제일 25일, 11일~10일). Installments (할부) are
 * spread over that many bills, the remainder in the first.
 */
object CardCycle {
    data class Bill(val card: MoneyAccount, val payDate: LocalDate, val from: LocalDate, val to: LocalDate, val amount: Long, val count: Int) {
        val month: YearMonth get() = YearMonth.from(payDate)
    }

    private fun dayIn(m: YearMonth, day: Int): LocalDate = m.atDay(day.coerceIn(1, m.lengthOfMonth()))
    private fun close(card: MoneyAccount): Int = (card.closeDay ?: 31).coerceIn(1, 31)
    private fun pay(card: MoneyAccount): Int = (card.payDay ?: 1).coerceIn(1, 31)

    /** The days a bill paid in [payMonth] covers. */
    fun period(card: MoneyAccount, payMonth: YearMonth): Pair<LocalDate, LocalDate> {
        val end = if (close(card) < pay(card)) payMonth else payMonth.minusMonths(1)
        return dayIn(end.minusMonths(1), close(card)).plusDays(1) to dayIn(end, close(card))
    }

    /** The month whose bill a payment made on [date] lands on (its first bill, for installments). */
    fun billMonth(card: MoneyAccount, date: LocalDate): YearMonth {
        val m = YearMonth.from(date)
        val end = if (!date.isAfter(dayIn(m, close(card)))) m else m.plusMonths(1)
        return if (close(card) < pay(card)) end else end.plusMonths(1)
    }

    fun payDate(card: MoneyAccount, payMonth: YearMonth): LocalDate = dayIn(payMonth, pay(card))

    /** What of [e] is paid in [payMonth]. */
    fun portion(e: MoneyEntry, first: YearMonth, payMonth: YearMonth): Long {
        val n = e.installments.coerceIn(1, 60)
        val k = ChronoUnit.MONTHS.between(first, payMonth)
        if (k < 0 || k >= n) return 0
        if (n == 1 || e.amount <= 0) return if (k == 0L) e.amount else 0
        val each = e.amount / n
        return if (k == 0L) e.amount - each * (n - 1) else each
    }

    fun bill(card: MoneyAccount, payMonth: YearMonth, entries: List<MoneyEntry>, accounts: List<MoneyAccount>): Bill {
        var amount = 0L
        var count = 0
        for (e in entries) {
            if (e.kind != MoneyKind.EXPENSE || MoneyAccounts.of(e, accounts) != card.id) continue
            val first = billMonth(card, e.date)
            val p = portion(e, first, payMonth)
            // 1.32 [LEDX-17] 유이자 할부 adds the 회차's 수수료 to the bill.
            if (p != 0L) { amount += p + (if (e.installmentRate > 0) LedgerChecks.installmentRow(e, first, payMonth)?.fee ?: 0L else 0L); count++ }
        }
        val (from, to) = period(card, payMonth)
        return Bill(card, payDate(card, payMonth), from, to, amount, count)
    }

    /** The first bill on or after [today]. */
    fun next(card: MoneyAccount, today: LocalDate, entries: List<MoneyEntry>, accounts: List<MoneyAccount>): Bill {
        val m = YearMonth.from(today)
        return bill(card, if (payDate(card, m).isBefore(today)) m.plusMonths(1) else m, entries, accounts)
    }

    /** The bill being built now: the one today's spending will be on. */
    fun current(card: MoneyAccount, today: LocalDate, entries: List<MoneyEntry>, accounts: List<MoneyAccount>): Bill =
        bill(card, billMonth(card, today), entries, accounts)

    /**
     * fc374a23 PC 확인 (카드값을 갚은 뒤에도 '다음 카드값'이 그대로): what is still owed on each bill of [months] after what was paid
     * to the card (a transfer to it, or its 카드값 line) up to [today].
     *
     * 2.0.0 [RVK-37] (검토 MONEY-45) A payment within [LedgerChecks.BILL_DAYS] of a bill's day is that bill, the card company's own
     * figure: it closes the bill when it is at least half of it (more or less than the app's sum, from a 연회비 or a line not written),
     * and nothing of it carries to the next one. Any other payment is an early or part payment: it goes to the oldest bill not yet paid
     * off whose days have begun, the rest to the next one, but never to a bill whose day is more than two weeks gone, which the card
     * company took on its day even when that was not written down.
     */
    fun left(card: MoneyAccount, months: List<YearMonth>, entries: List<MoneyEntry>, accounts: List<MoneyAccount>, today: LocalDate): Map<YearMonth, Long> {
        val sorted = months.distinct().sorted()
        val owed = sorted.associateWith { bill(card, it, entries, accounts).amount }.toMutableMap()
        for (p in paymentsTo(card, entries, today)) {
            val due = sorted.firstOrNull { m -> kotlin.math.abs(ChronoUnit.DAYS.between(payDate(card, m), p.date)) <= LedgerChecks.BILL_DAYS }
            if (due != null) {
                val was = owed.getValue(due)
                owed[due] = if (p.amount * 2 >= was) 0 else was - p.amount
                continue
            }
            var money = p.amount
            for (m in sorted) {
                if (money <= 0) break
                if (period(card, m).first.isAfter(p.date)) break
                if (payDate(card, m).plusDays(PAID_WINDOW_DAYS).isBefore(p.date)) continue
                val left = owed.getValue(m)
                if (left <= 0) continue
                val take = minOf(left, money)
                owed[m] = left - take
                money -= take
            }
        }
        return owed
    }

    /** What went to [card] up to [until]: a transfer to it, or its 카드값 repeating line, oldest first. */
    fun paymentsTo(card: MoneyAccount, entries: List<MoneyEntry>, until: LocalDate): List<MoneyEntry> = entries.filter { e ->
        e.kind == MoneyKind.TRANSFER && !e.date.isAfter(until) && e.amount > 0 && (e.toAccountId == card.id || (e.source == MoneySource.FIXED && e.merchant == card.name))
    }.sortedBy { it.date }

    /**
     * Whether the bill of [payMonth] is paid. 2.0.0 [RVK-37] (검토 MONEY-44) By the money, not by one transfer of any amount near the
     * day: what went to the card up to two weeks after the day covers it ([left], the three bills before first). A bill with nothing
     * written on it counts as paid once anything went to the card within two weeks of the day, as before.
     */
    fun paid(card: MoneyAccount, payMonth: YearMonth, entries: List<MoneyEntry>, accounts: List<MoneyAccount>): Boolean {
        val day = payDate(card, payMonth)
        if (bill(card, payMonth, entries, accounts).amount <= 0) {
            return paymentsTo(card, entries, day.plusDays(PAID_WINDOW_DAYS)).any { kotlin.math.abs(ChronoUnit.DAYS.between(day, it.date)) <= PAID_WINDOW_DAYS }
        }
        val months = (3L downTo 0L).map { payMonth.minusMonths(it) }
        return left(card, months, entries, accounts, day.plusDays(PAID_WINDOW_DAYS)).getValue(payMonth) <= 0
    }

    private const val PAID_WINDOW_DAYS = 14L
}

/**
 * 1.37 [LEDH-26] 순자산 한 장: what the accounts that hold money have, less what the credit cards still owe (spent on the card and not
 * yet paid to it), on any day; and the last twelve month-ends, for one line over the year.
 */
object NetWorth {
    /**
     * [unrecorded] 2.0.0 [RVK-37] (검토 MONEY-46): the cards' bills gone past their day that no payment to the card was written for,
     * counted as paid on their day (from the account the card pays from, when the book keeps it).
     */
    data class Point(val date: LocalDate, val have: Long, val owe: Long, val unrecorded: Int = 0) {
        val net: Long get() = have - owe
    }

    fun on(accounts: List<MoneyAccount>, entries: List<MoneyEntry>, date: LocalDate): Point {
        val until = entries.filter { !it.date.isAfter(date) }
        val balances = MoneyAccounts.balances(accounts, until).toMutableMap()
        // 1.38 [LEDH-25] A loan is owed, not had.
        val loans = accounts.filter { it.isLoan }.map { it.id }.toSet()
        var owe = 0L
        var unrecorded = 0
        for (card in accounts.filter { it.isCredit }) {
            val on = until.filter { MoneyAccounts.of(it, accounts) == card.id }
            if (!card.hasBill) {
                // Without a 결제일 the app cannot tell which bill is gone: what was spent on the card less what was paid to it.
                owe += (on.sumOf { if (it.kind == MoneyKind.EXPENSE) it.amount else if (it.kind == MoneyKind.INCOME) -it.amount else 0L } -
                    until.filter { it.kind == MoneyKind.TRANSFER && it.toAccountId == card.id }.sumOf { it.amount }).coerceAtLeast(0)
                continue
            }
            val c = cardOwed(card, on, until, accounts, date)
            owe += c.owe
            unrecorded += c.months.size
            // The bill left the account the card pays from on its day; after the last 잔액 맞춤 there, which already counted it before.
            val from = card.payFromId?.takeIf { id -> accounts.any { it.id == id && it.keepsBalance } }
            if (from != null) {
                val fixed = until.filter { MoneyAccounts.of(it, accounts) == from && it.merchant == MoneyAccounts.ADJUST_NAME }.maxOfOrNull { it.date }
                val gone = c.months.filter { (day, _) -> fixed == null || day.isAfter(fixed) }.sumOf { it.second }
                balances.merge(from, -gone, Long::plus)
            }
        }
        val have = balances.filterKeys { it !in loans }.values.sum()
        val loanOwed = balances.filterKeys { it in loans }.values.sumOf { (-it).coerceAtLeast(0) }
        return Point(date, have, owe + loanOwed, unrecorded)
    }

    /** What a card still owes on [date], and each bill gone past its day with no payment written for it (its day and what was not). */
    private data class CardOwed(val owe: Long, val months: List<Pair<LocalDate, Long>>)

    /**
     * 2.0.0 [RVK-37] (검토 MONEY-46) A card with a 결제일 owes only the bills whose day has not come (this period's spending and the
     * 할부 회차 still ahead, 유이자 수수료 too): a bill whose day is gone was taken by the card company on its day, written down or not.
     * So the 갚을 돈 no longer grows month after month for someone who never writes the 카드값. Payments go to the oldest bills first.
     */
    private fun cardOwed(card: MoneyAccount, on: List<MoneyEntry>, until: List<MoneyEntry>, accounts: List<MoneyAccount>, date: LocalDate): CardOwed {
        val byMonth = sortedMapOf<YearMonth, Long>()
        var credited = 0L
        for (e in on) {
            when (e.kind) {
                MoneyKind.EXPENSE -> {
                    val first = CardCycle.billMonth(card, e.date)
                    for (k in 0 until e.installments.coerceIn(1, 60)) {
                        val m = first.plusMonths(k.toLong())
                        val part = CardCycle.portion(e, first, m) +
                            (if (e.installmentRate > 0) LedgerChecks.installmentRow(e, first, m)?.fee ?: 0L else 0L)
                        if (part != 0L) byMonth.merge(m, part, Long::plus)
                    }
                }
                MoneyKind.INCOME -> credited += e.amount
                else -> {}
            }
        }
        var paid = CardCycle.paymentsTo(card, until, date).sumOf { it.amount } + credited
        val missed = mutableListOf<Pair<LocalDate, Long>>()
        var ahead = 0L
        for ((m, amount) in byMonth) {
            val day = CardCycle.payDate(card, m)
            if (day.isBefore(date)) {
                val covered = minOf(amount.coerceAtLeast(0), paid.coerceAtLeast(0))
                paid -= covered
                if (amount - covered > 0) missed += day to (amount - covered)
            } else ahead += amount
        }
        return CardOwed((ahead - paid.coerceAtLeast(0)).coerceAtLeast(0), missed)
    }

    /** Today and the last [months] month-ends before it, oldest first; empty with no account that holds money. */
    fun year(accounts: List<MoneyAccount>, entries: List<MoneyEntry>, today: LocalDate, months: Int = 12): List<Point> {
        if (accounts.none { it.keepsBalance && !it.hidden }) return emptyList()
        val ends = (months downTo 1).map { java.time.YearMonth.from(today).minusMonths(it.toLong()).atEndOfMonth() }
        return (ends + today).map { on(accounts, entries, it) }
    }
}

