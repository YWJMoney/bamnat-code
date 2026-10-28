package com.bamnat.core.money

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.roundToLong

/**
 * E 1.32 가계부 기본기 (무료, 조사/1.32/가계부-하이엔드-조사.md): what a ledger that writes itself must get right before
 * anything else, all on the phone. Each piece reads lines already kept; none of them sends anything anywhere.
 */
object LedgerSmarts {
    // ---- [LEDX-01] 581: money moved between the user's own accounts ----

    /** Days apart a withdrawal and its deposit may be and still be one move (a transfer at 23:58 lands the next day). */
    const val TRANSFER_DAYS = 1L

    /**
     * [LEDX-01] The other half of [new] when it and a recent line are one move between two of the user's own accounts:
     * a deposit into one account and a withdrawal (or transfer) of the same amount from another, within [TRANSFER_DAYS].
     * Both must be known accounts and differ. 1.31 counted the deposit as 수입, so moving 100만 원 to the 생활비 통장
     * read as 100만 원 earned.
     */
    fun ownTransfer(new: MoneyEntry, recent: List<MoneyEntry>): MoneyEntry? {
        val from = new.accountId ?: return null
        val wantKind = when (new.kind) {
            MoneyKind.INCOME -> MoneyKind.TRANSFER
            MoneyKind.TRANSFER -> MoneyKind.INCOME
            else -> return null
        }
        return recent.filter { e ->
            e.id != new.id && e.kind == wantKind && abs(e.amount) == abs(new.amount) && e.accountId != null && e.accountId != from &&
                abs(ChronoUnit.DAYS.between(e.date, new.date)) <= TRANSFER_DAYS && e.toAccountId == null &&
                e.shareOf(MoneyCategories.SALARY) == 0L
        }.minByOrNull { abs(ChronoUnit.MINUTES.between(it.date.atTime(it.time ?: java.time.LocalTime.NOON), new.date.atTime(new.time ?: java.time.LocalTime.NOON))) }
    }

    /** [LEDX-01] The one line a pair becomes: the withdrawal, now a transfer to the deposit's account. */
    fun joinTransfer(a: MoneyEntry, b: MoneyEntry): MoneyEntry {
        val out = if (a.kind == MoneyKind.TRANSFER) a else b
        val into = if (out === a) b else a
        return out.copy(kind = MoneyKind.TRANSFER, toAccountId = into.accountId, category = TRANSFER_CATEGORY,
            note = listOf(out.note, "내 계좌끼리 이체").filter { it.isNotBlank() }.joinToString(" · "))
    }

    const val TRANSFER_CATEGORY = "이체"

    // ---- [LEDX-02] 582: one payment told twice ----

    private val PAY_APPS = listOf("카카오페이", "네이버페이", "토스페이", "토스", "페이코", "삼성페이", "애플페이", "SSG페이", "스마일페이", "L.pay", "PAYCO")

    /** Minutes apart two texts of one payment arrive (the card's text and the bank's, or the pay app's). */
    const val SAME_PAYMENT_MINUTES = 10L

    /**
     * [LEDX-02] A recent line that is [new] told by someone else: a check card's approval and its bank's 출금 (the
     * withdrawal names the card or the same place), or a pay app's payment and the card behind it. Same amount, same
     * day, within [SAME_PAYMENT_MINUTES]. The card's line is kept (it knows the place); the other is the duplicate.
     */
    fun crossDuplicate(new: MoneyEntry, recent: List<MoneyEntry>): MoneyEntry? = recent.firstOrNull { e ->
        if (e.id == new.id || abs(e.amount) != abs(new.amount) || e.date != new.date) return@firstOrNull false
        val t1 = e.time; val t2 = new.time
        if (t1 == null || t2 == null || abs(ChronoUnit.MINUTES.between(t1, t2)) > SAME_PAYMENT_MINUTES) return@firstOrNull false
        val kinds = setOf(e.kind, new.kind)
        val cardAndBank = kinds == setOf(MoneyKind.EXPENSE, MoneyKind.TRANSFER) &&
            listOf(e, new).first { it.kind == MoneyKind.TRANSFER }.let { w ->
                val card = listOf(e, new).first { it.kind == MoneyKind.EXPENSE }
                "체크" in w.merchant || "카드" in w.merchant || placeSame(w.merchant, card.merchant) || (w.cardLast4.isNotEmpty() && w.cardLast4 == card.cardLast4)
            }
        val payAndCard = e.kind == MoneyKind.EXPENSE && new.kind == MoneyKind.EXPENSE &&
            (isPayApp(e) != isPayApp(new)) && (placeSame(e.merchant, new.merchant) || isPayApp(e) && e.merchant.isBlank() || isPayApp(new) && new.merchant.isBlank() ||
                PAY_APPS.any { it in e.merchant || it in new.merchant })
        cardAndBank || payAndCard
    }

    /** Of two lines of one payment, the one to keep: the card's (it names the place), else the earlier. */
    fun keepOf(a: MoneyEntry, b: MoneyEntry): MoneyEntry = when {
        a.kind == MoneyKind.EXPENSE && b.kind != MoneyKind.EXPENSE -> a
        b.kind == MoneyKind.EXPENSE && a.kind != MoneyKind.EXPENSE -> b
        isPayApp(a) && !isPayApp(b) -> b
        isPayApp(b) && !isPayApp(a) -> a
        else -> a
    }

    fun isPayApp(e: MoneyEntry): Boolean = PAY_APPS.any { e.note.contains(it) || e.merchant.startsWith(it) }

    private fun placeSame(a: String, b: String): Boolean {
        if (a.isBlank() || b.isBlank()) return false
        val ka = PaymentParser.placeKey(a); val kb = PaymentParser.placeKey(b)
        return ka.isNotEmpty() && (ka == kb || ka.contains(kb) || kb.contains(ka))
    }

    // ---- [LEDX-03] 583: refunds and part refunds ----

    /** A card abroad charges in won at its own rate: a refund may come back a little off. */
    const val FOREIGN_SHARE = 0.03

    /**
     * [LEDX-03] The payment a refund of [amount] (positive) at [merchant] / card [last4] on [date] undoes: the whole of
     * it or a part (부분취소), within 60 days before; a foreign one within [FOREIGN_SHARE]. The newest that fits.
     */
    fun refundTarget(amount: Long, merchant: String, last4: String?, date: LocalDate, recent: List<MoneyEntry>, foreign: Boolean = false): MoneyEntry? =
        recent.filter { e ->
            e.kind == MoneyKind.EXPENSE && e.amount > 0 && !e.date.isAfter(date) && ChronoUnit.DAYS.between(e.date, date) <= 60 &&
                ((merchant.isNotBlank() && placeSame(e.merchant, merchant)) || (last4 != null && last4.isNotEmpty() && e.cardLast4 == last4)) &&
                (amount <= e.amount || foreign && amount <= e.amount * (1 + FOREIGN_SHARE))
        }.sortedWith(compareBy<MoneyEntry> { if (abs(it.amount - amount) <= (if (foreign) it.amount * FOREIGN_SHARE else 0.0)) 0 else 1 }.thenByDescending { it.date })
            .firstOrNull()

    /** [LEDX-03] "부분취소 12,000원 (원래 35,000원, 3/12)" or "결제 취소 (3/12 결제)". */
    fun refundNote(amount: Long, target: MoneyEntry): String =
        if (amount < target.amount) "부분취소 ${won(amount)} (원래 ${won(target.amount)}, ${target.date.monthValue}/${target.date.dayOfMonth})"
        else "결제 취소 (${target.date.monthValue}/${target.date.dayOfMonth} 결제)"

    // ---- [LEDX-04] 584: 할부 ----

    /** [LEDX-04] The 회차 of an [e] paid over months that falls in [month] (1-based), or null when none does. */
    fun installmentOf(e: MoneyEntry, month: YearMonth): Int? {
        if (e.installments <= 1 || e.kind != MoneyKind.EXPENSE) return null
        val n = ChronoUnit.MONTHS.between(YearMonth.from(e.date), month).toInt() + 1
        return n.takeIf { it in 1..e.installments }
    }

    /** [LEDX-04] "3개월 할부 · 2/3회 · 남은 2회 24,000원" for [today]. */
    fun installmentText(e: MoneyEntry, today: LocalDate): String? {
        if (e.installments <= 1) return null
        val at = (installmentOf(e, YearMonth.from(today)) ?: if (YearMonth.from(today) > YearMonth.from(e.date)) e.installments else 1)
        val each = e.amount / e.installments
        val left = e.installments - at
        return "${e.installments}개월 할부 · $at/${e.installments}회" + if (left > 0) " · 남은 ${left}회 ${won(each * left)}" else " · 다 갚았어요"
    }

    /** [LEDX-04] What 할부 still owes in all, from [today]'s 회차 on (this month's included). */
    fun installmentsLeft(entries: List<MoneyEntry>, today: LocalDate): Long = entries.filter { it.installments > 1 && it.kind == MoneyKind.EXPENSE }.sumOf { e ->
        val at = installmentOf(e, YearMonth.from(today)) ?: return@sumOf 0L
        e.amount / e.installments * (e.installments - at + 1)
    }

    // ---- [LEDX-05] 585: categories learned on the phone ----

    /**
     * [LEDX-05] A naive Bayes over the words of each place's name (and its card, and the hour band), trained on the lines the
     * user kept or fixed. Small: a few hundred lines teach it; nothing leaves the phone. [guess] gives the best category and how
     * sure it is; under [SURE] the app shows the two best as chips instead of picking.
     */
    class Learner private constructor(private val counts: Map<String, Map<String, Int>>, private val totals: Map<String, Int>, private val docs: Map<String, Int>) {
        data class Guess(val category: String, val share: Double)

        fun guess(e: MoneyEntry, top: Int = 2): List<Guess> {
            if (docs.isEmpty()) return emptyList()
            val words = features(e)
            if (words.isEmpty()) return emptyList()
            val vocab = counts.values.flatMap { it.keys }.toSet().size.coerceAtLeast(1)
            val all = docs.values.sum().toDouble()
            val scores = docs.keys.associateWith { c ->
                val n = totals[c] ?: 0
                ln(docs.getValue(c) / all) + words.sumOf { w -> ln(((counts[c]?.get(w) ?: 0) + 1.0) / (n + vocab)) }
            }
            val max = scores.values.max()
            val exp = scores.mapValues { kotlin.math.exp(it.value - max) }
            val sum = exp.values.sum()
            return exp.entries.sortedByDescending { it.value }.take(top).map { Guess(it.key, it.value / sum) }
        }

        companion object {
            const val SURE = 0.6
            /** Lines the learner needs before it says anything. */
            const val MIN_LINES = 20

            fun of(entries: List<MoneyEntry>): Learner {
                val taught = entries.filter { it.kind == MoneyKind.EXPENSE && it.amount > 0 && it.reviewed && it.category.isNotBlank() && it.category != "기타" && it.parts.isEmpty() }
                if (taught.size < MIN_LINES) return Learner(emptyMap(), emptyMap(), emptyMap())
                val counts = HashMap<String, HashMap<String, Int>>()
                val totals = HashMap<String, Int>()
                val docs = HashMap<String, Int>()
                for (e in taught) {
                    docs[e.category] = (docs[e.category] ?: 0) + 1
                    val m = counts.getOrPut(e.category) { HashMap() }
                    for (w in features(e)) { m[w] = (m[w] ?: 0) + 1; totals[e.category] = (totals[e.category] ?: 0) + 1 }
                }
                return Learner(counts, totals, docs)
            }

            /** The place's name as 2-letter pieces (Korean names have no spaces to split on), its card and its hour band. */
            fun features(e: MoneyEntry): List<String> {
                val key = PaymentParser.placeKey(e.merchant)
                val grams = if (key.length < 2) listOfNotNull(key.takeIf { it.isNotEmpty() }) else (0..key.length - 2).map { key.substring(it, it + 2) }
                return grams + listOfNotNull(
                    e.cardLast4.takeIf { it.isNotEmpty() }?.let { "#card$it" },
                    e.time?.let { "#h${it.hour / 3}" },
                    "#amt${amountBand(e.amount)}",
                )
            }

            private fun amountBand(v: Long): Int = when { v < 5_000 -> 0; v < 15_000 -> 1; v < 50_000 -> 2; v < 200_000 -> 3; else -> 4 }
        }
    }

    // ---- [LEDX-06] 586: what a rule would change ----

    /**
     * [LEDX-06] The past lines a rule ([key] as the rules page keeps it: a place's name, or [PaymentParser.CONTAINS] + a word)
     * would move to [category]: spending at places it matches, now under another category, newest first.
     */
    fun ruleWouldChange(entries: List<MoneyEntry>, key: String, category: String): List<MoneyEntry> {
        if (key.removePrefix(PaymentParser.CONTAINS).isBlank()) return emptyList()
        val rule = mapOf(key to category)
        return entries.filter { it.kind == MoneyKind.EXPENSE && it.parts.isEmpty() && it.category != category && it.merchant.isNotBlank() &&
            PaymentParser.ruleFor(it.merchant, rule) != null }.sortedByDescending { it.date }
    }

    // ---- [LEDX-07] 587: the day's spending and what is left for today ----

    /** [LEDX-07] What each day of [month] spent (지출 less 취소), for the calendar's cells. */
    fun spentByDay(entries: List<MoneyEntry>, month: YearMonth): Map<LocalDate, Long> =
        entries.filter { it.kind == MoneyKind.EXPENSE && YearMonth.from(it.date) == month }.groupBy { it.date }
            .mapValues { (_, l) -> l.sumOf { it.amount } }.filterValues { it > 0 }

    data class Pace(val spent: Long, val projected: Long, val budget: Long?, val todayLeft: Long?, val daysLeft: Int)

    /**
     * [LEDX-07] This month so far, where it ends at this pace (fixed costs still to come included), and, with a budget,
     * what may be spent each day from today so the month stays within it.
     */
    fun pace(entries: List<MoneyEntry>, today: LocalDate, budget: Long?, fixedToCome: Long = 0): Pace {
        val month = YearMonth.from(today)
        val spent = entries.filter { it.kind == MoneyKind.EXPENSE && YearMonth.from(it.date) == month && !it.date.isAfter(today) }.sumOf { it.amount }
        val day = today.dayOfMonth
        val days = month.lengthOfMonth()
        val projected = (spent.coerceAtLeast(0).toDouble() / day * days).roundToLong() + fixedToCome
        val daysLeft = days - day + 1
        val todayLeft = budget?.let { ((it - spent - fixedToCome).coerceAtLeast(0) / daysLeft) }
        return Pace(spent, projected, budget, todayLeft, daysLeft)
    }

    // ---- [LEDX-08] 588: paid abroad ----

    /**
     * [LEDX-08] Rough won per unit, to write a foreign payment down at once (marked 임시) until the card's bill gives the
     * real figure. Plain constants of 2026-10 (한국은행 매매기준율 근처), never fetched; the user corrects the line from the bill.
     */
    val ROUGH_WON: Map<String, Double> = mapOf(
        "USD" to 1_390.0, "EUR" to 1_610.0, "JPY" to 9.3, "CNY" to 195.0, "GBP" to 1_860.0, "HKD" to 179.0, "TWD" to 45.0,
        "THB" to 41.0, "VND" to 0.055, "SGD" to 1_070.0, "AUD" to 910.0, "CAD" to 1_000.0, "PHP" to 24.0, "CHF" to 1_740.0,
    )

    /** 해외 이용 수수료 most Korean cards add: 브랜드 1% + 해외서비스 0.18~0.25%. */
    const val ABROAD_FEE = 0.0125

    data class Abroad(val won: Long, val fee: Long, val note: String)

    /** [LEDX-08] [amount] of [currency] in won with the usual fee, or null for a currency the table does not know. */
    fun abroad(currency: String, amount: Double): Abroad? {
        val rate = ROUGH_WON[currency.uppercase()] ?: return null
        val won = (amount * rate).roundToLong()
        val fee = (won * ABROAD_FEE).roundToLong()
        return Abroad(won + fee, fee, "임시 원화 · $currency ${"%,.2f".format(amount)} · 수수료 약 ${won(fee)} 포함. 카드 청구서 금액으로 고쳐 주세요")
    }

    /**
     * [LEDX-08] 원화 결제(DCC): a payment abroad charged in won costs 3~8% more than in the local money. Said when a text
     * from abroad (해외, a foreign place) carries a won amount.
     */
    fun looksDcc(text: String): Boolean = ("해외" in text || "국외" in text) && Regex("KRW|원화").containsMatchIn(text)

    const val DCC_NOTE = "해외에서 원화로 결제했어요(DCC). 현지 돈으로 결제하면 보통 3~8% 덜 나와요. 다음엔 ‘현지 통화로’를 골라 주세요"

    // ---- [LEDX-09] 589: subscriptions ----

    data class Subscription(val place: String, val monthly: Long, val months: Int, val last: LocalDate, val raised: Long?, val stale: Boolean) {
        val yearly: Long get() = monthly * 12
    }

    /**
     * [LEDX-09] The payments that come back every month at the same place (구독 or not): what each costs a month and a year,
     * whether its price went up, and [Subscription.stale] when it was paid three months or more in a row and the user has
     * not looked at it in [lookedAt] (asked "아직 쓰나요?").
     */
    fun subscriptions(entries: List<MoneyEntry>, today: LocalDate, lookedAt: Map<String, LocalDate> = emptyMap()): List<Subscription> {
        val from = YearMonth.from(today).minusMonths(6)
        return entries.filter { it.kind == MoneyKind.EXPENSE && it.amount in 1_000..300_000 && !YearMonth.from(it.date).isBefore(from) && it.merchant.isNotBlank() }
            .groupBy { PaymentParser.placeKey(it.merchant) }.filterKeys { it.isNotEmpty() }
            .mapNotNull { (key, list) ->
                val byMonth = list.groupBy { YearMonth.from(it.date) }
                if (byMonth.size < 3 || byMonth.values.any { it.size > 2 }) return@mapNotNull null
                val sorted = list.sortedBy { it.date }
                val amounts = sorted.map { it.amount }
                // Within 20% of each other: a subscription, not a café visited every month.
                if (amounts.max() > amounts.min() * 1.2 && list.none { it.category == "구독" }) return@mapNotNull null
                val last = sorted.last()
                if (ChronoUnit.DAYS.between(last.date, today) > 45) return@mapNotNull null
                val raised = (amounts.last() - amounts.first()).takeIf { it > 0 }
                val stale = byMonth.size >= 3 && (lookedAt[key]?.let { ChronoUnit.DAYS.between(it, today) > 90 } ?: true)
                Subscription(last.merchant, amounts.last(), byMonth.size, last.date, raised, stale)
            }.sortedByDescending { it.monthly }
    }

    // ---- [LEDX-10] 590: 신용카드 등 소득공제 25% 문턱 ----

    data class CardGauge(
        val threshold: Long, val credit: Long, val debitAndCash: Long, val over: Boolean,
        /** What is left before the 문턱, when not over. */
        val toThreshold: Long,
        val tip: String,
    ) {
        val spent: Long get() = credit + debitAndCash
        val share: Double get() = if (threshold == 0L) 0.0 else spent.toDouble() / threshold
    }

    /**
     * [LEDX-10] 조세특례제한법 제126조의2: card spending counts only past 25% of the year's 총급여; past it, 신용카드 15% and
     * 체크카드·현금영수증 30% come off income. So until the line spend on the card that gives more (혜택), after it on the 체크카드.
     * [yearGross] is the year's 총급여 (the pay estimate for the year), [accounts] say which card is which.
     */
    fun cardGauge(entries: List<MoneyEntry>, accounts: List<MoneyAccount>, year: Int, yearGross: Long): CardGauge? {
        if (yearGross <= 0) return null
        val byId = accounts.associateBy { it.id }
        val spending = entries.filter { it.kind == MoneyKind.EXPENSE && it.date.year == year && it.amount > 0 }
        var credit = 0L; var other = 0L
        for (e in spending) {
            when (byId[MoneyAccounts.of(e, accounts) ?: -1L]?.type) {
                AccountType.CREDIT -> credit += e.amount
                AccountType.CHECK, AccountType.CASH, AccountType.PAY -> other += e.amount
                else -> if (e.cardLast4.isNotEmpty()) credit += e.amount else other += e.amount
            }
        }
        val threshold = (yearGross * 0.25).roundToLong()
        val over = credit + other >= threshold
        val tip = if (over) "문턱을 넘었어요. 이제부터는 체크카드·현금영수증이 공제가 두 배(30%)예요"
        else "문턱까지 ${won(threshold - credit - other)} 남았어요. 그 전에는 혜택 많은 신용카드, 넘은 뒤엔 체크카드가 유리해요"
        return CardGauge(threshold, credit, other, over, (threshold - credit - other).coerceAtLeast(0), tip)
    }

    private fun won(v: Long) = "%,d원".format(v)
}
