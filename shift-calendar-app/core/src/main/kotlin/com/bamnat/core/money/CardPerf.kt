package com.bamnat.core.money

import java.time.LocalDate
import java.time.YearMonth

/**
 * 1.38 [LEDH-27] 카드 실적 게이지 (조사 C5): 카드마다 사용자가 적은 전월 실적 구간(예 30만·50만)에 이번 달 쓴 돈이 어디까지 왔는지,
 * "4만 2천 원 더 쓰면 50만 원 구간". 실적은 그달 1일~말일에 그 카드로 쓴 돈(취소는 뺌)이고, 사용자가 고른 분류(세금·상품권 등)는
 * 빼요. 카드사마다 빠지는 업종·할부 셈이 달라 "카드사 기준과 다를 수 있어요"를 함께 보여 줘요.
 */
object CardPerf {
    data class Gauge(
        val card: MoneyAccount,
        val month: YearMonth,
        /** What counts this month so far. */
        val counted: Long,
        /** The tier reached (null below the first). */
        val reached: Long?,
        /** The next tier up (null when the top one is reached). */
        val next: Long?,
        /** What last month counted, which sets this month's benefits at most card companies. */
        val lastMonth: Long,
        val lastReached: Long?,
        /** Spending left out because of its category. */
        val skipped: Long,
    ) {
        val toNext: Long? get() = next?.let { (it - counted).coerceAtLeast(0) }
        /** 0..1 toward the top tier, for the bar. */
        val fraction: Float get() = card.perfTiers.lastOrNull()?.takeIf { it > 0 }?.let { (counted.toFloat() / it).coerceIn(0f, 1f) } ?: 0f
    }

    /** What [card] counted in [month]: its spending there, cancellations taken off, the skipped categories left out. */
    fun counted(card: MoneyAccount, accounts: List<MoneyAccount>, entries: List<MoneyEntry>, month: YearMonth): Pair<Long, Long> {
        var sum = 0L
        var skipped = 0L
        for (e in entries) {
            if (e.kind != MoneyKind.EXPENSE || YearMonth.from(e.date) != month) continue
            if (MoneyAccounts.of(e, accounts) != card.id) continue
            if (e.category in card.perfSkip) skipped += e.amount else sum += e.amount
        }
        return sum.coerceAtLeast(0) to skipped.coerceAtLeast(0)
    }

    fun gauge(card: MoneyAccount, accounts: List<MoneyAccount>, entries: List<MoneyEntry>, today: LocalDate): Gauge? {
        if (!card.isCard || card.perfTiers.isEmpty()) return null
        val month = YearMonth.from(today)
        val (now, skipped) = counted(card, accounts, entries, month)
        val (last, _) = counted(card, accounts, entries, month.minusMonths(1))
        val tiers = card.perfTiers.sorted()
        return Gauge(card, month, now, tiers.lastOrNull { now >= it }, tiers.firstOrNull { now < it }, last, tiers.lastOrNull { last >= it }, skipped)
    }

    /** Every card with tiers, the one closest to its next tier first. */
    fun all(accounts: List<MoneyAccount>, entries: List<MoneyEntry>, today: LocalDate): List<Gauge> =
        accounts.filter { !it.hidden }.mapNotNull { gauge(it, accounts, entries, today) }
            .sortedWith(compareBy<Gauge>({ it.toNext == null }, { it.toNext ?: 0 }))

    /** "30만, 50만" or "300000 500000" as tiers in 원; 만 is 10,000. Up to five, each above zero. */
    fun parseTiers(text: String): List<Long> = text.split(',', ' ', '/', '·').mapNotNull { raw ->
        val t = raw.trim().replace("원", "")
        when {
            t.isEmpty() -> null
            t.endsWith("만") -> t.dropLast(1).toDoubleOrNull()?.let { (it * 10_000).toLong() }
            else -> t.toLongOrNull()
        }
    }.filter { it > 0 }.distinct().sorted().take(5)
}
