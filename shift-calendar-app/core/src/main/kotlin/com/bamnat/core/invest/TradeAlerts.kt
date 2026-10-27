package com.bamnat.core.invest

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import com.bamnat.core.time.Today

/**
 * [INV-30][INV-32] Trades found in notifications, waiting for the user's 확인 (결정 D37): only the app,
 * side, stock or coin, quantity, price, market and time are kept, never the rest of the text. They sit in
 * the calendar's investor settings as one line each (no new table, 결정 D40), so a backup carries them.
 */
object TradeAlerts {
    /** The investor settings key holding the waiting alerts. */
    const val KEY = "pendingAlerts"
    /** The key for 알림 오면 바로 적기; off means each one waits for 확인. */
    const val AUTO_KEY = "alertAuto"
    /** At most this many wait; the oldest goes first. */
    const val MAX = 30
    /** The same fill posted again (a stacked or repeated notification) within this is one trade. */
    const val SAME_WITHIN_MILLIS = 10L * 60 * 1000
    /** A tag on the saved trade naming the app it came from: "알림:업비트". */
    const val TAG_PREFIX = "알림:"

    data class Pending(
        /** The notification's time, which is also its id among the waiting ones. */
        val at: Long,
        val app: String,
        val side: Side,
        val symbol: String,
        val name: String,
        val quantity: Double,
        val price: Double,
        val market: Market,
        /** [INV-40] 체결 금액 when known (0 when not), in the price's currency. */
        val amount: Double = 0.0,
        /** [INV-40] How many notifications this is (409 B: split fills of one order put together). */
        val parts: Int = 1,
        /** [INV-40] Where the price came from when it was not said: [BY_AMOUNT], [BY_PARTS] or [BY_CAPTURE]. */
        val filledBy: String = "",
        /** [INVA-01] 1.29: false for a capture's row that showed no time (its trade then has none, not a made-up 12:00). Not stored. */
        val timeKnown: Boolean = true,
    ) {
        val title: String get() = name.ifBlank { symbol }
        val tag: String get() = TAG_PREFIX + app.take(20)
        /** [INV-40] 수량 × 단가 agrees with the amount; null without one. False holds the line for a look (no 바로 적기). */
        val checked: Boolean? get() = if (amount > 0 && price > 0) FillMath.agrees(quantity, price, amount) else null

        fun day(zone: ZoneId = Today.zone()): LocalDate = Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
        fun minute(zone: ZoneId = Today.zone()): Int = LocalDateTime.ofInstant(Instant.ofEpochMilli(at), zone).let { it.hour * 60 + it.minute }

        /** The trade it becomes once confirmed: fees left at zero (the sheet suggests them when opened). */
        fun toTrade(accountId: Long, fxRate: Double, zone: ZoneId = Today.zone()) = Trade(
            accountId = accountId, date = day(zone), minute = if (timeKnown) minute(zone) else null, symbol = symbol, name = name, market = market,
            side = side, quantity = quantity, price = price, fxRate = if (market.foreign) fxRate else 1.0, tags = listOf(tag),
        )

        fun toDraft(accountId: Long?, zone: ZoneId = Today.zone()) = TradeDraft(
            symbol = symbol, name = name, market = market, side = side, quantity = quantity, price = price.takeIf { it > 0 },
            accountId = accountId, date = day(zone), tags = listOf(tag), alertAt = at,
        )

        fun sameFill(o: Pending) = side == o.side && symbol == o.symbol && name == o.name && quantity == o.quantity &&
            price == o.price && kotlin.math.abs(at - o.at) <= SAME_WITHIN_MILLIS

        /** [INV-40] The same holding and side, close in time: one order's notifications. */
        fun sameOrder(o: Pending) = side == o.side && market == o.market && symbol == o.symbol && name == o.name &&
            kotlin.math.abs(at - o.at) <= SAME_WITHIN_MILLIS

        /** The money of the fill: the amount when it agrees, else 수량 × 단가. */
        val money: Double get() = if (checked == true) amount else quantity * price
    }

    /** [INV-40] What filled a price that the notification did not say. */
    const val BY_AMOUNT = "금액"
    const val BY_PARTS = "묶음"
    const val BY_CAPTURE = "캡처"

    fun of(a: TradeAlertParser.Alert, app: String, at: Long) =
        Pending(at, app, a.side, a.symbol, a.name, a.quantity, a.price, a.market, a.amount, filledBy = if (a.priceFromAmount) BY_AMOUNT else "")

    /**
     * The waiting list with [p] added, unless the same fill is there already; null when nothing changes. [INV-40] 409 B:
     * a notification of the same order a few minutes later is put with the one waiting: the same quantity without a
     * price (체결 → 정산·잔고 알림) gives the price to the one without, and a different quantity with a price (나눠
     * 체결) adds up into one line at the average price.
     */
    fun add(list: List<Pending>, p: Pending): List<Pending>? {
        if (list.any { it.sameFill(p) }) return null
        val order = list.lastOrNull { it.sameOrder(p) }
        val merged: Pending? = when {
            order == null -> null
            // The same fill told twice, one of them with the price.
            order.quantity == p.quantity && (order.price == 0.0 || p.price == 0.0) -> {
                if (p.price == 0.0) return null
                order.copy(price = p.price, amount = p.amount, filledBy = BY_PARTS)
            }
            // Split fills of one order, each with its price.
            order.price > 0 && p.price > 0 && order.parts < MAX_PARTS -> {
                val qty = order.quantity + p.quantity
                val money = order.money + p.money
                order.copy(quantity = qty, price = FillMath.round(money / qty), amount = money, parts = order.parts + p.parts, filledBy = BY_PARTS)
            }
            else -> null
        }
        val next = if (merged != null) list.map { if (it.at == order!!.at) merged else it } else list + p
        return next.sortedBy { it.at }.takeLast(MAX)
    }

    /**
     * [INVA-06] 1.29 AR: a notification for a fill that is written already — from a capture put in first, or a notification
     * posted again later — of the same side, holding and quantity, that day, within [FillMatcher.MINUTES] of a trade with
     * its time. It neither waits nor (with 알림 오면 바로 적기) goes in a second time. A trade written without a time is not
     * taken for it (two buys of one stock on one day stay two).
     */
    fun writtenAlready(p: Pending, trades: List<Trade>, zone: ZoneId = Today.zone()): Boolean = trades.any { t ->
        val m = t.minute ?: return@any false
        t.side == p.side && t.date == p.day(zone) && kotlin.math.abs(t.quantity - p.quantity) < 1e-9 &&
            kotlin.math.abs(m - p.minute(zone)) <= FillMatcher.MINUTES && sameHolding(t, p) &&
            (p.price <= 0 || kotlin.math.abs(t.price - p.price) * t.quantity <= FillMath.tolerance(t.quantity * t.price, t.quantity))
    }

    private fun sameHolding(t: Trade, p: Pending): Boolean {
        if (t.symbol.isNotBlank() && p.symbol.isNotBlank()) return t.symbol.equals(p.symbol, ignoreCase = true)
        val a = FillMatcher.key(t.name.ifBlank { t.symbol })
        val b = FillMatcher.key(p.name.ifBlank { p.symbol })
        return a.isNotEmpty() && b.isNotEmpty() && (a.contains(b) || b.contains(a))
    }

    /** One order is split into this many notifications at most before a new line starts. */
    const val MAX_PARTS = 20

    /** [INV-40] 409 C: a waiting line given the price read from a 체결 내역 capture; it still waits for 확인 (결정 D37). */
    fun fill(list: List<Pending>, at: Long, price: Double, amount: Double): List<Pending> =
        list.map { if (it.at == at) it.copy(price = price, amount = amount, filledBy = BY_CAPTURE) else it }

    private fun clean(s: String) = s.replace('\t', ' ').replace('\n', ' ')

    fun encode(list: List<Pending>): String = list.joinToString("\n") { p ->
        listOf(p.at.toString(), clean(p.app), p.side.name, clean(p.symbol), clean(p.name), p.quantity.toString(), p.price.toString(), p.market.name,
            // [INV-40] 1.25: older apps read the first eight and leave these.
            p.amount.toString(), p.parts.toString(), clean(p.filledBy))
            .joinToString("\t")
    }

    /** A line this version cannot read is left out, never an error. */
    fun decode(s: String?): List<Pending> = s.orEmpty().split('\n').mapNotNull { line ->
        val f = line.split('\t')
        if (f.size < 8) return@mapNotNull null
        Pending(
            at = f[0].toLongOrNull() ?: return@mapNotNull null,
            app = f[1],
            side = Side.entries.firstOrNull { it.name == f[2] } ?: return@mapNotNull null,
            symbol = f[3], name = f[4],
            quantity = f[5].toDoubleOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null,
            price = f[6].toDoubleOrNull() ?: 0.0,
            market = Market.entries.firstOrNull { it.name == f[7] } ?: Market.OTHER,
            amount = f.getOrNull(8)?.toDoubleOrNull()?.takeIf { it >= 0 } ?: 0.0,
            parts = f.getOrNull(9)?.toIntOrNull()?.coerceIn(1, MAX_PARTS) ?: 1,
            filledBy = f.getOrNull(10).orEmpty(),
        )
    }

    /** The app a saved trade came from, read back from its tag; null when typed by hand. */
    fun sourceOf(t: Trade): String? = t.tags.firstOrNull { it.startsWith(TAG_PREFIX) }?.removePrefix(TAG_PREFIX)

    /** A short word for the kind of holding, shown as a badge on each trade: 코인, 주식, ETF, 해외주식. */
    fun badge(m: Market): String? = when (m) {
        Market.CRYPTO -> "코인"
        Market.KR_STOCK -> "주식"
        Market.KR_ETF -> "ETF"
        Market.US_STOCK -> "해외주식"
        Market.OTHER -> null
    }
}
