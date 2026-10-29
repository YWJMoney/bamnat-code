package com.bamnat.core.money

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * 1.32 조사 대조 (계획/1.32-조사-대조표.md, 가계부 조사 2-4장 "보강"): the pieces of a self-writing ledger 1.32's first
 * pass left out or cut short. All on the phone; nothing here sends anything.
 */
object LedgerChecks {
    private fun won(v: Long) = "%,d원".format(v)

    // ---- [LEDX-13] 경로: the pay app a payment went through ----

    /** The pay app [e] went through: named in its note ("네이버페이 결제", the pay app's own text) or before its place. */
    fun viaOf(e: MoneyEntry): String? =
        MerchantNames.ROUTES.firstOrNull { r -> e.note.contains(r, ignoreCase = true) || e.merchant.startsWith(r, ignoreCase = true) }
            ?: "토스".takeIf { e.note.split(" · ").any { it == "토스" || it.startsWith("토스 ") } }

    /**
     * [LEDX-13] The card's line [kept] for a payment the pay app also told ([dropped]), with the app as its 경로: "카드 + 페이"
     * becomes one line that says both (경로 = 페이, 결제수단 = 카드), as 뱅크샐러드 fixed in 2025.1.
     */
    fun withRoute(kept: MoneyEntry, dropped: MoneyEntry): MoneyEntry {
        val via = viaOf(dropped) ?: return kept
        if (viaOf(kept) == via) return kept
        return kept.copy(note = listOf(kept.note, "$via 결제").filter { it.isNotBlank() }.joinToString(" · "))
    }

    // ---- [LEDX-14] 카드값 대조 ----

    /** Days a card's bill may leave before or after its 결제일 (a weekend or holiday moves it). */
    const val BILL_DAYS = 3L

    data class BillCheck(val card: MoneyAccount, val payMonth: YearMonth, val expected: Long, val actual: Long) {
        val diff: Long get() = actual - expected
        /** "예상 412,300원 · 실제 415,800원 (3,500원 더)" */
        val words: String get() = "예상 ${"%,d".format(expected)}원 · 실제 ${"%,d".format(actual)}원" + when {
            abs(diff) < 10 -> " (맞아요)"
            diff > 0 -> " (${"%,d".format(diff)}원 더)"
            else -> " (${"%,d".format(-diff)}원 덜)"
        }
    }

    /**
     * [LEDX-14] The credit card whose bill a 카드값 withdrawal [e] paid: the card it went to, or the one card whose 결제일 is
     * within [BILL_DAYS] and whose name or company the text named. With what the app expected that bill to be
     * ([CardCycle.bill], 할부 회차 and 수수료 included) against what left.
     */
    fun billCheck(e: MoneyEntry, accounts: List<MoneyAccount>, entries: List<MoneyEntry>): BillCheck? {
        if (e.kind != MoneyKind.TRANSFER || e.amount <= 0) return null
        val cards = accounts.filter { it.hasBill && !it.hidden }
        if (cards.isEmpty()) return null
        fun due(card: MoneyAccount): YearMonth? = listOf(YearMonth.from(e.date).minusMonths(1), YearMonth.from(e.date), YearMonth.from(e.date).plusMonths(1))
            .firstOrNull { m -> abs(ChronoUnit.DAYS.between(CardCycle.payDate(card, m), e.date)) <= BILL_DAYS }
        val text = (e.merchant + " " + e.note).replace(" ", "")
        val card = cards.firstOrNull { it.id == e.toAccountId }
            ?: cards.filter { due(it) != null }.let { near ->
                near.firstOrNull { c -> c.last4.isNotEmpty() && c.last4 == e.cardLast4 }
                    ?: near.firstOrNull { c -> c.name.replace(" ", "").take(2).let { it.length == 2 && text.contains(it) } }
                    ?: near.singleOrNull()?.takeIf { e.category == "카드값" }
            }
            ?: return null
        val month = due(card) ?: return null
        val expected = CardCycle.bill(card, month, entries.filter { it.id != e.id }, accounts).amount
        if (expected <= 0) return null
        return BillCheck(card, month, expected, e.amount)
    }

    /** [LEDX-14] The note a checked 카드값 line keeps: "신한 Deep 10월 카드값 · 예상 …". */
    fun billNote(b: BillCheck): String = "${b.card.name} ${b.payMonth.monthValue}월 카드값 · ${b.words}"

    // ---- [LEDX-15] 내 이름: a deposit from the user themself ----

    /**
     * [LEDX-15] A deposit whose sender is one of the user's own [names] (as a bank prints "홍길동" or "홍*동"): money moved
     * from another of the user's own accounts, not 수입. The names stay on the phone.
     */
    fun fromMyself(e: MoneyEntry, names: Collection<String>): Boolean {
        if (e.kind != MoneyKind.INCOME || e.merchant.isBlank() || names.isEmpty()) return false
        val sender = e.merchant.replace(Regex("\\s+"), "").removeSuffix("님")
        return names.map { it.replace(Regex("\\s+"), "") }.filter { it.length >= 2 }.any { n -> sameName(sender, n) }
    }

    /** "홍길동" against "홍길동", "홍*동", "홍길*" or "(홍길동)". */
    private fun sameName(sender: String, name: String): Boolean {
        val s = sender.trim('(', ')', '[', ']')
        if (s == name) return true
        if (s.length != name.length || '*' !in s) return false
        return s.indices.all { s[it] == '*' || s[it] == name[it] }
    }

    /** [LEDX-15] The line a deposit from the user themself becomes: a move into the account it reached. */
    fun asOwnMove(e: MoneyEntry): MoneyEntry = e.copy(kind = MoneyKind.TRANSFER, category = LedgerSmarts.TRANSFER_CATEGORY,
        toAccountId = e.accountId, accountId = null, note = listOf(e.note, "내 이름으로 들어온 돈(내 계좌끼리)").filter { it.isNotBlank() }.joinToString(" · "))

    // ---- [LEDX-16] 원 결제를 못 찾은 환불 ----

    /** What a refund the app could not tie to its payment says, until the user picks the payment. */
    const val UNMATCHED_REFUND = "환불 받은 돈 · 원래 결제를 골라 주세요"

    fun isUnmatchedRefund(e: MoneyEntry): Boolean = e.kind == MoneyKind.EXPENSE && e.amount < 0 && UNMATCHED_REFUND in e.note

    /** [LEDX-16] What earlier part refunds of [target] already gave back: lines whose note names it ("원래 35,000원, 3/12"). */
    fun refundedBefore(target: MoneyEntry, entries: List<MoneyEntry>, except: Long = -1): Long {
        val mark = "(원래 ${won(target.amount)}, ${target.date.monthValue}/${target.date.dayOfMonth})"
        return entries.filter { it.id != except && it.id != target.id && it.amount < 0 && mark in it.note && !it.date.isBefore(target.date) }
            .sumOf { -it.amount }
    }

    /**
     * [LEDX-16] Payments a refund of [refund] may undo, for the user to pick: spending before it within 90 days at the same
     * place or card first, then any of at least its amount; at most [limit].
     */
    fun refundCandidates(refund: MoneyEntry, entries: List<MoneyEntry>, limit: Int = 8): List<MoneyEntry> {
        val back = -refund.amount
        val pool = entries.filter { e ->
            e.kind == MoneyKind.EXPENSE && e.amount >= back && e.id != refund.id && !e.date.isAfter(refund.date) &&
                ChronoUnit.DAYS.between(e.date, refund.date) <= 90
        }
        fun near(e: MoneyEntry) = (refund.merchant.isNotBlank() && PaymentParser.samePlace(e.merchant, refund.merchant)) ||
            (refund.cardLast4.isNotEmpty() && e.cardLast4 == refund.cardLast4)
        return pool.sortedWith(compareBy<MoneyEntry> { if (near(it)) 0 else 1 }.thenByDescending { it.date }).take(limit)
    }

    /** [LEDX-16] [refund] tied to [target]: its category, and the note that says what is left. */
    fun linkRefund(refund: MoneyEntry, target: MoneyEntry, entries: List<MoneyEntry>): MoneyEntry {
        val note = LedgerSmarts.refundNote(-refund.amount, target, refundedBefore(target, entries, except = refund.id))
        val kept = refund.note.split(" · ").filter { it.isNotBlank() && it != "환불 받은 돈" && it != "원래 결제를 골라 주세요" && !it.startsWith("부분취소") && !it.startsWith("결제 취소") }
        return refund.copy(category = target.category, note = (kept + note).joinToString(" · "), reviewed = true)
    }

    // ---- [LEDX-17] 할부 청구 기준 보기 ----

    data class InstallmentRow(val entry: MoneyEntry, val month: YearMonth, val round: Int, val principal: Long, val fee: Long) {
        val total: Long get() = principal + fee
    }

    /**
     * [LEDX-17] The 할부 [e] paid in [payMonth]'s bill: 원금 1/N (the first takes the rest of the division, as
     * [CardCycle.portion]) and, for 유이자 ([MoneyEntry.installmentRate]), 수수료 on what was still owed before this 회차
     * (잔액 × 연 수수료율 ÷ 12, as Korean cards bill it). Null when nothing of it falls there.
     */
    fun installmentRow(e: MoneyEntry, first: YearMonth, payMonth: YearMonth): InstallmentRow? {
        val n = e.installments.coerceIn(1, 60)
        if (n <= 1 || e.amount <= 0 || e.kind != MoneyKind.EXPENSE) return null
        val k = ChronoUnit.MONTHS.between(first, payMonth).toInt()
        if (k < 0 || k >= n) return null
        val principal = CardCycle.portion(e, first, payMonth)
        val each = e.amount / n
        val owed = e.amount - (if (k == 0) 0 else (e.amount - each * (n - 1)) + each * (k - 1))
        val fee = (owed * e.installmentRate.coerceIn(0.0, MoneyEntry.MAX_INSTALLMENT_RATE) / 100 / 12).roundToLong()
        return InstallmentRow(e, payMonth, k + 1, principal, fee)
    }

    /** [LEDX-17] What all 할부 of [e]'s card bills add in 수수료, for the whole of it. */
    fun installmentFees(e: MoneyEntry, first: YearMonth = YearMonth.from(e.date)): Long =
        (0 until e.installments.coerceIn(1, 60)).sumOf { installmentRow(e, first, first.plusMonths(it.toLong()))?.fee ?: 0L }

    data class BillMonth(val month: YearMonth, val rows: List<InstallmentRow>) {
        val total: Long get() = rows.sumOf { it.total }
        val fees: Long get() = rows.sumOf { it.fee }
    }

    /**
     * [LEDX-17] 청구 기준 보기: the next [months] bills from [from] and the 할부 each carries, by the card's 결제일 when the
     * card is known ([CardCycle.billMonth]), else from the month after the payment.
     */
    fun installmentPlan(entries: List<MoneyEntry>, accounts: List<MoneyAccount>, from: YearMonth, months: Int = 6): List<BillMonth> {
        val byId = accounts.associateBy { it.id }
        val split = entries.filter { it.kind == MoneyKind.EXPENSE && it.installments > 1 && it.amount > 0 }
        return (0 until months).map { i ->
            val m = from.plusMonths(i.toLong())
            BillMonth(m, split.mapNotNull { e ->
                val card = byId[MoneyAccounts.of(e, accounts) ?: -1L]?.takeIf { it.hasBill }
                val first = card?.let { CardCycle.billMonth(it, e.date) } ?: YearMonth.from(e.date).plusMonths(1)
                installmentRow(e, first, m)
            }.sortedByDescending { it.total })
        }
    }

    /** [LEDX-17] 원금 and 수수료 still to pay from [today]'s month on, all 할부 together. */
    fun owedFrom(entries: List<MoneyEntry>, accounts: List<MoneyAccount>, today: LocalDate): Pair<Long, Long> {
        val plan = installmentPlan(entries, accounts, YearMonth.from(today), 60)
        return plan.sumOf { b -> b.rows.sumOf { it.principal } } to plan.sumOf { it.fees }
    }

    // ---- [LEDX-19] 체크 vs 신용 ----

    /**
     * [LEDX-19] What card [e] was paid with: the kind of the account it belongs to, else what its text said ("체크" in the
     * note the parser keeps, "체크카드" as the issuer's word); null when nothing says.
     */
    fun cardKind(e: MoneyEntry, accounts: List<MoneyAccount>): AccountType? {
        accounts.firstOrNull { it.id == MoneyAccounts.of(e, accounts) }?.type?.let { return it }
        if (e.cardLast4.isEmpty()) return null
        return when {
            "체크" in e.note -> AccountType.CHECK
            "신용" in e.note -> AccountType.CREDIT
            else -> null
        }
    }

    data class UnknownCard(val last4: String, val guess: AccountType?, val lines: Int, val spent: Long, val issuer: String?)

    /**
     * [LEDX-19] Cards the texts used that the user has not added, most used first, with what the texts suggest; the app asks
     * once ("체크카드 맞나요?") and adds the card, so the 25% 문턱 and the 연말정산 count it right.
     */
    fun unknownCards(entries: List<MoneyEntry>, accounts: List<MoneyAccount>, since: LocalDate): List<UnknownCard> {
        val known = accounts.map { it.last4 }.filter { it.isNotEmpty() }.toSet()
        return entries.filter { it.kind == MoneyKind.EXPENSE && it.cardLast4.length == 4 && it.cardLast4 !in known && !it.date.isBefore(since) }
            .groupBy { it.cardLast4 }
            .map { (l4, list) ->
                val check = list.count { "체크" in it.note }
                val credit = list.count { "신용" in it.note }
                val issuer = list.firstNotNullOfOrNull { e -> e.note.split(" · ").firstOrNull { it.endsWith(" $l4") }?.removeSuffix(" $l4") }
                UnknownCard(l4, when { check > credit -> AccountType.CHECK; credit > check -> AccountType.CREDIT; else -> null },
                    list.size, list.sumOf { it.amount }, issuer)
            }
            .filter { it.lines >= 2 }
            .sortedByDescending { it.lines }
    }

    // ---- [LEDX-23] 달력 칸 지출 히트맵 ----

    /**
     * [LEDX-23] Each day of [spent] as a shade 1~4 against the month's own days: quarters of the spending days by amount,
     * so a big month and a quiet one both show where it went. Days without spending have none.
     */
    fun heat(spent: Map<LocalDate, Long>): Map<LocalDate, Int> {
        val days = spent.filterValues { it > 0 }
        if (days.isEmpty()) return emptyMap()
        val sorted = days.values.sorted()
        fun at(q: Double) = sorted[((sorted.size - 1) * q).roundToLong().toInt()]
        val q1 = at(0.25); val q2 = at(0.5); val q3 = at(0.75)
        return days.mapValues { (_, v) -> when { v > q3 -> 4; v > q2 -> 3; v > q1 -> 2; else -> 1 } }
    }
}
