package com.bamnat.core.money

import java.time.LocalDate

/**
 * 1.37 [LEDH-17] 찾아 준 돈 한 판: what 밤낮 caught this year, in one place, so the user feels it paid back — 결제 취소·부분취소
 * tied to the payment they undo (LEDX-03), subscriptions not looked at in three months (LEDX-09) and prices that went up.
 * Only money that really came back counts in the big number; a subscription to look at is what it would save, said apart.
 */
data class FoundMoney(
    val year: Int,
    /** Refunds this year that the ledger tied to their payment: (how many, won). */
    val refunds: Int,
    val refunded: Long,
    /** Subscriptions paid three months or more and not looked at in 90 days: a year of them. */
    val staleSubs: List<LedgerSmarts.Subscription>,
    /** Subscriptions whose price went up, with how much a month. */
    val raisedSubs: List<LedgerSmarts.Subscription>,
) {
    val staleYearly: Long get() = staleSubs.sumOf { it.yearly }
    val isEmpty: Boolean get() = refunds == 0 && staleSubs.isEmpty() && raisedSubs.isEmpty()

    companion object {
        /** The words the ledger writes on a refund it tied ([LedgerSmarts.refundNote]). */
        private fun isTiedRefund(e: MoneyEntry) = e.kind == MoneyKind.EXPENSE && e.amount < 0 &&
            (e.note.startsWith("결제 취소") || e.note.startsWith("부분취소"))

        fun of(entries: List<MoneyEntry>, today: LocalDate, lookedAt: Map<String, LocalDate> = emptyMap()): FoundMoney {
            val refunds = entries.filter { it.date.year == today.year && !it.date.isAfter(today) && isTiedRefund(it) }
            val subs = LedgerSmarts.subscriptions(entries, today, lookedAt)
            return FoundMoney(
                today.year, refunds.size, -refunds.sumOf { it.amount },
                staleSubs = subs.filter { it.stale },
                raisedSubs = subs.filter { (it.raised ?: 0) > 0 },
            )
        }
    }
}
