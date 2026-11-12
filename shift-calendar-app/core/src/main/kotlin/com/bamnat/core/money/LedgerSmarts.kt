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
     *
     * 2.0.0 [RVK-12] (검토 MONEY-60): with the user's own names known ([myNames], 결제 알림 › 더 정확하게 적기), two lines that
     * each name someone, neither of them the user, are two people's money (a friend's 50만 원 in, the rent out) and stay apart.
     */
    fun ownTransfer(new: MoneyEntry, recent: List<MoneyEntry>, myNames: Collection<String> = emptyList()): MoneyEntry? {
        val from = new.accountId ?: return null
        val wantKind = when (new.kind) {
            MoneyKind.INCOME -> MoneyKind.TRANSFER
            MoneyKind.TRANSFER -> MoneyKind.INCOME
            else -> return null
        }
        return recent.filter { e ->
            e.id != new.id && e.kind == wantKind && abs(e.amount) == abs(new.amount) && e.accountId != null && e.accountId != from &&
                abs(ChronoUnit.DAYS.between(e.date, new.date)) <= TRANSFER_DAYS && e.toAccountId == null &&
                e.shareOf(MoneyCategories.SALARY) == 0L && !othersApart(e, new, myNames)
        }.minByOrNull { abs(ChronoUnit.MINUTES.between(it.date.atTime(it.time ?: java.time.LocalTime.NOON), new.date.atTime(new.time ?: java.time.LocalTime.NOON))) }
    }

    /** [RVK-12] Both lines name someone, the names differ and neither is the user: money of two other people. */
    private fun othersApart(a: MoneyEntry, b: MoneyEntry, myNames: Collection<String>): Boolean {
        if (myNames.isEmpty() || a.merchant.isBlank() || b.merchant.isBlank()) return false
        if (LedgerChecks.isMine(a.merchant, myNames) || LedgerChecks.isMine(b.merchant, myNames)) return false
        return !placeSame(a.merchant, b.merchant)
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
        // 2.0.0 [RVK-39] (검토 MONEY-42) A bank 출금 now read as spending, beside the 체크카드's approval of the same payment.
        val bankSpend = e.kind == MoneyKind.EXPENSE && new.kind == MoneyKind.EXPENSE && (fromWithdraw(e) != fromWithdraw(new)) &&
            listOf(e, new).first { fromWithdraw(it) }.let { w ->
                val card = listOf(e, new).first { !fromWithdraw(it) }
                "체크" in w.merchant || "카드" in w.merchant || placeSame(w.merchant, card.merchant) || (w.cardLast4.isNotEmpty() && w.cardLast4 == card.cardLast4)
            }
        if (bankSpend) return@firstOrNull true
        val payAndCard = e.kind == MoneyKind.EXPENSE && new.kind == MoneyKind.EXPENSE &&
            (isPayApp(e) != isPayApp(new)) && (placeSame(e.merchant, new.merchant) || isPayApp(e) && e.merchant.isBlank() || isPayApp(new) && new.merchant.isBlank() ||
                PAY_APPS.any { it in e.merchant || it in new.merchant })
        cardAndBank || payAndCard
    }

    /** Of two lines of one payment, the one to keep: the card's (it names the place), else the earlier. */
    fun keepOf(a: MoneyEntry, b: MoneyEntry): MoneyEntry = when {
        a.kind == MoneyKind.EXPENSE && b.kind != MoneyKind.EXPENSE -> a
        b.kind == MoneyKind.EXPENSE && a.kind != MoneyKind.EXPENSE -> b
        fromWithdraw(a) && !fromWithdraw(b) -> b
        fromWithdraw(b) && !fromWithdraw(a) -> a
        isPayApp(a) && !isPayApp(b) -> b
        isPayApp(b) && !isPayApp(a) -> a
        else -> a
    }

    /** 2.0.0 [RVK-39] A line written from a bank 출금 read as spending ([PaymentParser.WITHDRAW_NOTE]). */
    fun fromWithdraw(e: MoneyEntry): Boolean = e.note.split(" · ").contains(PaymentParser.WITHDRAW_NOTE)

    /**
     * 2.0.0 [RVK-39] (검토 MONEY-42) A bank 출금 to someone else the app could not tell (not one of the user's own [myNames], no rule
     * for the receiver yet): it may be 월세 to a landlord or a 송금 to a friend as much as the user's own money moving, so 확인 대기함
     * and 새로 들어온 기록 ask '쓴 돈 / 내 계좌로 옮김' once, and the answer is kept as the receiver's rule.
     */
    fun spentOrMoved(e: MoneyEntry, myNames: Collection<String>, rules: Map<String, String>): Boolean =
        e.kind == MoneyKind.TRANSFER && e.source == MoneySource.NOTIFICATION && e.toAccountId == null && e.category == MOVE_CATEGORY &&
            e.merchant.isNotBlank() && "체크" !in e.merchant && "카드" !in e.merchant && !LedgerChecks.isMine(e.merchant, myNames) &&
            PaymentParser.ruleFor(e.merchant, rules) == null

    /** What a bank 출금 is when the user says it was spending: 지출 in [category], marked as read from the bank ([fromWithdraw]). */
    fun asSpending(e: MoneyEntry, category: String): MoneyEntry = e.copy(
        kind = MoneyKind.EXPENSE, category = category, toAccountId = null,
        note = (e.note.split(" · ").filter { it.isNotBlank() && it != PaymentParser.WITHDRAW_NOTE } + PaymentParser.WITHDRAW_NOTE).joinToString(" · "),
    )

    /** The category a bank 출금 read as a move gets ([PaymentParser]), and the rule a receiver told to be a move keeps. */
    const val MOVE_CATEGORY = "내 계좌"

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

    /**
     * [LEDX-03] "부분취소 12,000원 (원래 35,000원, 3/12)" or "결제 취소 (3/12 결제)". 1.32 [LEDX-16] With what is left of
     * the payment once this and [earlier] refunds of it are taken off: "… · 남은 23,000원".
     */
    fun refundNote(amount: Long, target: MoneyEntry, earlier: Long = 0): String {
        val left = target.amount - earlier - amount
        return if (amount < target.amount && left > 0) "부분취소 ${won(amount)} (원래 ${won(target.amount)}, ${target.date.monthValue}/${target.date.dayOfMonth}) · 남은 ${won(left)}"
        else if (amount < target.amount) "부분취소 ${won(amount)} (원래 ${won(target.amount)}, ${target.date.monthValue}/${target.date.dayOfMonth}) · 모두 돌려받았어요"
        else "결제 취소 (${target.date.monthValue}/${target.date.dayOfMonth} 결제)"
    }

    // ---- [LEDX-04] 584: 할부 ----

    /**
     * 2.0.0 [RVK-37] (검토 MONEY-48) The month [e]'s first 회차 is billed: by its card's 결제일 when the card is known
     * ([CardCycle.billMonth]), else the month after the payment, the same as 청구 기준 보기 ([LedgerChecks.installmentPlan]).
     */
    fun installmentFirst(e: MoneyEntry, accounts: List<MoneyAccount> = emptyList()): YearMonth {
        val card = accounts.firstOrNull { it.id == MoneyAccounts.of(e, accounts) }?.takeIf { it.hasBill }
        return card?.let { CardCycle.billMonth(it, e.date) } ?: YearMonth.from(e.date).plusMonths(1)
    }

    /** [LEDX-04] The 회차 of an [e] paid over months that falls in [month] (1-based, by the bill), or null when none does. */
    fun installmentOf(e: MoneyEntry, month: YearMonth, accounts: List<MoneyAccount> = emptyList()): Int? {
        if (e.installments <= 1 || e.kind != MoneyKind.EXPENSE) return null
        val n = ChronoUnit.MONTHS.between(installmentFirst(e, accounts), month).toInt() + 1
        return n.takeIf { it in 1..e.installments }
    }

    /** What of [e] is billed from [month] on, by the bill (the first 회차 keeps the rest of the division, as [CardCycle.portion]). */
    private fun installmentFrom(e: MoneyEntry, month: YearMonth, accounts: List<MoneyAccount>): Long {
        val first = installmentFirst(e, accounts)
        return (0 until e.installments.coerceIn(1, 60)).map { first.plusMonths(it.toLong()) }.filter { it >= month }
            .sumOf { CardCycle.portion(e, first, it) }
    }

    /**
     * [LEDX-04] "3개월 할부 · 2/3회 · 남은 2회 24,000원" for [today]. 2.0.0 [RVK-37] (검토 MONEY-48) The 회차 by the bill, the same
     * as 청구 기준 보기: "다 갚았어요" only once the last 회차's bill came, and before the first "12월부터 청구".
     */
    fun installmentText(e: MoneyEntry, today: LocalDate, accounts: List<MoneyAccount> = emptyList()): String? {
        if (e.installments <= 1) return null
        val month = YearMonth.from(today)
        val first = installmentFirst(e, accounts)
        if (month < first) return "${e.installments}개월 할부 · ${first.monthValue}월부터 청구 · ${won(e.amount)}"
        val at = installmentOf(e, month, accounts) ?: e.installments
        val left = e.installments - at
        return "${e.installments}개월 할부 · $at/${e.installments}회" +
            if (left > 0) " · 남은 ${left}회 ${won(installmentFrom(e, month.plusMonths(1), accounts))}" else " · 다 갚았어요"
    }

    /** [LEDX-04] What 할부 still owes in all, from [today]'s 회차 on (this month's bill included, and those not billed yet). */
    fun installmentsLeft(entries: List<MoneyEntry>, today: LocalDate, accounts: List<MoneyAccount> = emptyList()): Long =
        entries.filter { it.installments > 1 && it.kind == MoneyKind.EXPENSE && it.amount > 0 }.sumOf { installmentFrom(it, YearMonth.from(today), accounts) }

    // ---- [LEDX-05] 585: categories learned on the phone ----

    /**
     * [LEDX-05] A naive Bayes over the words of each place's name (and its card, and the hour band), trained on the lines the
     * user kept or fixed. Small: a few hundred lines teach it; nothing leaves the phone. [guess] gives the best category and how
     * sure it is; under [SURE] the app shows the two best as chips instead of picking.
     */
    class Learner private constructor(
        private val counts: Map<String, Map<String, Int>>, private val totals: Map<String, Int>, private val docs: Map<String, Int>,
        private val dayLabel: (LocalDate) -> String? = { null },
    ) {
        data class Guess(val category: String, val share: Double)

        fun guess(e: MoneyEntry, top: Int = 2): List<Guess> {
            if (docs.isEmpty()) return emptyList()
            val words = features(e, dayLabel)
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
            /** Lines the learner needs before it says anything: 1.32 [LEDX-21] 30, as Copilot waits (조사 2-5장). */
            const val MIN_LINES = 30

            /**
             * [dayLabel] gives what the day was on the calendar (주간, 야간, 비번, 쉬는 날: 1.32 [LEDX-21], the feature only
             * 밤낮 has); null where the calendar is not known.
             */
            fun of(entries: List<MoneyEntry>, dayLabel: (LocalDate) -> String? = { null }): Learner {
                val taught = entries.filter { it.kind == MoneyKind.EXPENSE && it.amount > 0 && it.reviewed && it.category.isNotBlank() && it.category != "기타" && it.parts.isEmpty() }
                if (taught.size < MIN_LINES) return Learner(emptyMap(), emptyMap(), emptyMap(), dayLabel)
                val counts = HashMap<String, HashMap<String, Int>>()
                val totals = HashMap<String, Int>()
                val docs = HashMap<String, Int>()
                for (e in taught) {
                    docs[e.category] = (docs[e.category] ?: 0) + 1
                    val m = counts.getOrPut(e.category) { HashMap() }
                    for (w in features(e, dayLabel)) { m[w] = (m[w] ?: 0) + 1; totals[e.category] = (totals[e.category] ?: 0) + 1 }
                }
                return Learner(counts, totals, docs, dayLabel)
            }

            /**
             * The place's name as 2- and 3-letter pieces (Korean names have no spaces to split on), its card, its hour band and
             * amount band; 1.32 [LEDX-21] also the pay app it went through, the weekday and what the day was on the calendar.
             */
            fun features(e: MoneyEntry, dayLabel: (LocalDate) -> String? = { null }): List<String> {
                val key = PaymentParser.placeKey(e.merchant)
                val grams = if (key.length < 2) listOfNotNull(key.takeIf { it.isNotEmpty() }) else (0..key.length - 2).map { key.substring(it, it + 2) }
                val tri = if (key.length < 3) emptyList() else (0..key.length - 3).map { "3:" + key.substring(it, it + 3) }
                return grams + tri + listOfNotNull(
                    e.cardLast4.takeIf { it.isNotEmpty() }?.let { "#card$it" },
                    e.time?.let { "#h${it.hour / 3}" },
                    "#amt${amountBand(e.amount)}",
                    LedgerChecks.viaOf(e)?.let { "#via$it" },
                    "#wd${e.date.dayOfWeek.value}",
                    runCatching { dayLabel(e.date) }.getOrNull()?.let { "#day$it" },
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
        val lines = entries.filter { it.kind == MoneyKind.EXPENSE && YearMonth.from(it.date) == month && !it.date.isAfter(today) }
        val spent = lines.sumOf { it.amount }
        val day = today.dayOfMonth
        val days = month.lengthOfMonth()
        // PC 확인 (덩어리 경비 한 번에 과한 경고): repeating lines and big ones ([Budgets.LUMP]) count once; only the rest is a pace.
        val once = lines.filter { it.source == MoneySource.FIXED || it.amount >= Budgets.LUMP }.sumOf { it.amount }
        val projected = once + ((spent - once).coerceAtLeast(0).toDouble() / day * days).roundToLong() + fixedToCome
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

    /**
     * [LEDX-08] [amount] of [currency] in won with the fee, or null for a currency the table does not know. 1.32 [LEDX-18]
     * [rates] are the user's own (set by hand or learned from a line fixed to the bill) and win over the table; [fee] is the
     * user's 해외 이용 수수료 (0.0135 for 1.35%).
     */
    fun abroad(currency: String, amount: Double, rates: Map<String, Double> = emptyMap(), fee: Double = ABROAD_FEE): Abroad? {
        val cur = currency.uppercase()
        val own = rates[cur]?.takeIf { it > 0 }
        val rate = own ?: ROUGH_WON[cur] ?: return null
        val won = (amount * rate).roundToLong()
        val charge = (won * fee.coerceIn(0.0, MAX_ABROAD_FEE)).roundToLong()
        val how = if (own != null) "내 환율 ${"%,.2f".format(java.util.Locale.ROOT, own)}원" else "대략 환율"
        return Abroad(won + charge, charge, "임시 원화 · $currency ${"%,.2f".format(java.util.Locale.ROOT, amount)} · $how · 수수료 약 ${won(charge)} 포함. 카드 청구서 금액으로 고쳐 주세요")
    }

    /** [LEDX-18] No Korean card charges 5% for using it abroad; more is a slip. */
    const val MAX_ABROAD_FEE = 0.05

    /** [LEDX-18] What marks a line written down at a rough rate, so fixing its amount teaches the rate. */
    const val ABROAD_MARK = "임시 원화 · "

    /**
     * [LEDX-18] The rate a line fixed from the card's bill teaches: [won] billed for [amount] of the currency, the fee
     * [fee] taken off. Null when the numbers cannot be a rate.
     */
    fun learnedRate(amount: Double, won: Long, fee: Double = ABROAD_FEE): Double? {
        if (amount <= 0 || won <= 0) return null
        val r = won / (1 + fee.coerceIn(0.0, MAX_ABROAD_FEE)) / amount
        return r.takeIf { it.isFinite() && it > 0 }?.let { kotlin.math.round(it * 100) / 100 }
    }

    /** [LEDX-18] The currency and amount of a line written down at a rough rate ("임시 원화 · USD 12.30 · …"), or null. */
    fun abroadOf(e: MoneyEntry): Pair<String, Double>? {
        // 2.0.0 [RVK-33] (검토 MONEY-55) A note written on a phone in another language says '12,30' or '1.234,56'.
        val m = Regex("임시 원화 · ([A-Z]{3}) (\\d[\\d.,]*\\d|\\d)").find(e.note) ?: return null
        return m.groupValues[1] to (com.bamnat.core.text.Numbers.readWritten(m.groupValues[2]) ?: return null)
    }

    /**
     * [LEDX-18] The note of a line fixed to the card's bill: "임시 원화 · USD 12.30 · … 고쳐 주세요" becomes
     * "USD 12.30 · 청구서 금액", so it no longer asks to be fixed.
     */
    fun settledAbroad(note: String): String =
        note.replace(Regex("임시 원화 · ([A-Z]{3} \\d[\\d.,]*\\d?) · [^·]*· 수수료 약 [\\d.,]+원 포함\\. 카드 청구서 금액으로 고쳐 주세요"), "$1 · 청구서 금액")

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
            // 1.32 [LEDX-19] A card the user has not added goes by what its texts said (체크 / 신용).
            when (byId[MoneyAccounts.of(e, accounts) ?: -1L]?.type ?: LedgerChecks.cardKind(e, accounts)) {
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

    /** 1.37 [LEDH-32] Days since the last line written by hand, and the lines that came in by themselves meanwhile. */
    data class Away(val days: Int, val auto: Int)

    /**
     * 1.37 [LEDH-32] 복귀 반김: the user wrote nothing by hand for [minDays] days or more, after using the book (ten lines or more by
     * hand); null otherwise.
     */
    fun away(entries: List<MoneyEntry>, today: LocalDate, minDays: Int = 7): Away? {
        val byHand = entries.filter { it.source == MoneySource.MANUAL && !it.date.isAfter(today) }
        if (byHand.size < 10) return null
        val last = byHand.maxOf { it.date }
        val days = ChronoUnit.DAYS.between(last, today).toInt()
        if (days < minDays) return null
        return Away(days, entries.count { it.source != MoneySource.MANUAL && it.date.isAfter(last) && !it.date.isAfter(today) })
    }

    /**
     * 1.37 [LEDH-30] 근무에서 바로 적기: what is spent again and again on [dates] (the past days of one kind of shift), as lines to
     * start from — the same place (or category) and amount on two days or more, most often first.
     */
    fun dayHabits(entries: List<MoneyEntry>, dates: Set<LocalDate>, limit: Int = 3): List<MoneyEntry> =
        entries.filter { it.kind == MoneyKind.EXPENSE && it.amount > 0 && it.source != MoneySource.FIXED && it.date in dates }
            .groupBy { (it.merchant.ifBlank { it.category }.trim()) to it.amount }
            .filterValues { l -> l.map { it.date }.distinct().size >= 2 }
            .entries.sortedWith(compareByDescending<Map.Entry<Pair<String, Long>, List<MoneyEntry>>> { it.value.size }.thenByDescending { it.value.maxOf { e -> e.date } })
            .take(limit).map { (_, l) -> l.maxBy { it.date } }

    /**
     * 1.37 [LEDH-36] 평소보다 큰 지출: the spending lines of the last [days] days at least [times] the usual (the median of the same
     * category over the half year before today, from five lines or more) and 3만 원 or more. Repeating lines are left out; the list only
     * marks them quietly.
     */
    fun unusual(entries: List<MoneyEntry>, today: java.time.LocalDate, days: Long = 60, times: Int = 3): Set<Long> {
        val from = today.minusDays(180)
        val spend = entries.filter { it.kind == MoneyKind.EXPENSE && it.amount > 0 && it.source != MoneySource.FIXED && !it.date.isBefore(from) && !it.date.isAfter(today) }
        val usual = spend.groupBy { it.category }.filterValues { it.size >= 5 }.mapValues { (_, l) ->
            val sorted = l.map { it.amount }.sorted()
            sorted[sorted.size / 2]
        }
        val since = today.minusDays(days - 1)
        return spend.filter { e -> !e.date.isBefore(since) && e.amount >= 30_000 && usual[e.category]?.let { e.amount >= it * times } == true }
            .map { it.id }.toSet()
    }

    private fun won(v: Long) = "%,d원".format(java.util.Locale.ROOT, v)
}
