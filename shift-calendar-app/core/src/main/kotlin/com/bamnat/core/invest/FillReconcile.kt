package com.bamnat.core.invest

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.abs

/**
 * [INV-42] 1.27 AK 순번 450: what a 체결 내역 capture adds to what is already written. Each read fill is put beside the
 * investor calendar's trades and its waiting alerts and becomes one of three (계획/1.27-AK-개발계획.md, 결정 AK-D1·D2):
 *  - [Kind.SAME] it is written already (skipped, counted as '이미 있음 n건');
 *  - [Kind.DIFFERENT] a trade (or a waiting alert with a price) of the same holding, side and day disagrees: asked as a fix,
 *    never changed quietly;
 *  - [Kind.NEW] not written yet: the only rows that are checked before they go in.
 * Reading the capture stays [FillSheetReader]'s (AJ's 몫); this only compares numbers already read.
 */
object FillReconcile {
    enum class Kind { SAME, DIFFERENT, NEW }

    enum class Part(val label: String) { QUANTITY("수량"), PRICE("단가"), TIME("시각") }

    data class Item(
        val kind: Kind,
        /** One row of the capture, or two to four rows of one written trade. */
        val fills: List<FillSheetReader.Fill>,
        /** The written trade it is (SAME) or disagrees with (DIFFERENT). */
        val trade: Trade? = null,
        /** The waiting alert it fills (NEW) or disagrees with (DIFFERENT). */
        val alert: TradeAlerts.Pending? = null,
        /** What differs, for DIFFERENT. */
        val parts: Set<Part> = emptySet(),
    ) {
        val fill: FillSheetReader.Fill get() = fills.first()
        /** The read numbers have a yellow field: never put in without asking (결정 AK-D4). */
        val unsure: Boolean get() = fills.any { !it.sure || it.price <= 0 || it.checked == false }
    }

    data class Result(val items: List<Item>) {
        val same: List<Item> get() = items.filter { it.kind == Kind.SAME }
        val different: List<Item> get() = items.filter { it.kind == Kind.DIFFERENT }
        val new: List<Item> get() = items.filter { it.kind == Kind.NEW }
        /** New rows that may go in without a question once 묻지 않기 is on. */
        val sureNew: List<Item> get() = new.filter { !it.unsure }
        /** What still has to be asked with 묻지 않기 on: every difference, and new rows with a yellow field. */
        val toAsk: List<Item> get() = different + new.filter { it.unsure }
    }

    /** A capture's row this far from a written trade's time is another trade. */
    const val MINUTES = FillMatcher.MINUTES
    /** A row without a date is today's, and is the same as a trade written up to this many days ago (결정 AK-D5). */
    const val DAYS_BACK = 7L

    fun sameHolding(t: Trade, f: FillSheetReader.Fill): Boolean {
        if (t.symbol.isNotBlank() && f.symbol.isNotBlank()) return t.symbol.equals(f.symbol, ignoreCase = true)
        val tn = FillMatcher.key(t.name.ifBlank { t.symbol })
        val fn = FillMatcher.key(f.name.ifBlank { f.symbol })
        return tn.isNotEmpty() && fn.isNotEmpty() && (tn.contains(fn) || fn.contains(tn))
    }

    private fun sameDay(t: Trade, f: FillSheetReader.Fill, today: LocalDate): Boolean =
        f.date?.let { it == t.date } ?: (!t.date.isAfter(today) && !t.date.isBefore(today.minusDays(DAYS_BACK)))

    /** Both times known and more than [MINUTES] apart: two trades. */
    private fun apart(t: Trade, f: FillSheetReader.Fill): Boolean {
        val m = t.minute ?: return false
        val ft = f.time ?: return false
        return abs(m - (ft.hour * 60 + ft.minute)) > MINUTES
    }

    private fun priceAgrees(quantity: Double, a: Double, b: Double): Boolean =
        abs(quantity * a - quantity * b) <= FillMath.tolerance(quantity * maxOf(a, b), quantity)

    private fun same(t: Trade, f: FillSheetReader.Fill, today: LocalDate): Boolean =
        t.side == f.side && sameHolding(t, f) && sameDay(t, f, today) && !apart(t, f) &&
            abs(t.quantity - f.quantity) < 1e-9 && f.price > 0 && priceAgrees(t.quantity, t.price, f.price)

    private fun related(t: Trade, f: FillSheetReader.Fill, today: LocalDate): Boolean =
        t.side == f.side && sameHolding(t, f) && sameDay(t, f, today) && !apart(t, f)

    fun reconcile(
        fills: List<FillSheetReader.Fill>,
        trades: List<Trade>,
        alerts: List<TradeAlerts.Pending>,
        today: LocalDate,
        zone: ZoneId,
    ): Result {
        val left = fills.toMutableList()
        val free = trades.toMutableList()
        val items = ArrayList<Item>()
        // ① The same as a written trade, exact pairs first.
        for (f in fills) {
            val t = free.filter { same(it, f, today) }.minByOrNull { t -> closeness(t, f) } ?: continue
            items += Item(Kind.SAME, listOf(f), trade = t)
            left -= f; free -= t
        }
        // ① A trade written once that the screen splits into two to four rows.
        for (t in free.toList()) {
            val rows = left.filter { related(t, it, today) && it.price > 0 }
            val pick = parts(rows, t.quantity) ?: continue
            val avg = pick.sumOf { it.quantity * it.price } / t.quantity
            if (!priceAgrees(t.quantity, t.price, avg)) continue
            items += Item(Kind.SAME, pick, trade = t)
            left -= pick.toSet(); free -= t
        }
        // Waiting alerts: a row that fills one is new (it goes in with that alert); a price already said that disagrees is asked.
        val waiting = FillMatcher.match(alerts, left.filter { it.price > 0 }, zone)
        for (m in waiting.matches) {
            val a = m.alert
            val differs = a.price > 0 && !priceAgrees(a.quantity, a.price, m.price)
            items += if (differs) Item(Kind.DIFFERENT, m.fills, alert = a, parts = setOf(Part.PRICE))
            else Item(Kind.NEW, m.fills, alert = a)
            left -= m.fills.toSet()
        }
        // ② A trade of the same holding, side and day that is left disagrees with the row.
        for (f in left.toList()) {
            val t = free.filter { related(it, f, today) }.minByOrNull { t -> closeness(t, f) } ?: continue
            val parts = buildSet {
                if (abs(t.quantity - f.quantity) >= 1e-9) add(Part.QUANTITY)
                if (f.price > 0 && !priceAgrees(f.quantity, t.price, f.price)) add(Part.PRICE)
                val tm = t.minute
                val ft = f.time
                if (tm != null && ft != null && tm != ft.hour * 60 + ft.minute) add(Part.TIME)
            }
            if (parts.isEmpty() || parts == setOf(Part.TIME)) {
                // Only the minute differs: the same trade, written at another minute.
                items += Item(Kind.SAME, listOf(f), trade = t)
            } else {
                items += Item(Kind.DIFFERENT, listOf(f), trade = t, parts = parts)
            }
            left -= f; free -= t
        }
        // ③ The rest is new.
        left.forEach { items += Item(Kind.NEW, listOf(it)) }
        // In the capture's order.
        return Result(items.sortedBy { item -> fills.indexOfFirst { it === item.fill }.let { if (it < 0) Int.MAX_VALUE else it } })
    }

    /** How far apart in time (minutes; 0 when either is unknown), then in quantity. */
    private fun closeness(t: Trade, f: FillSheetReader.Fill): Double {
        val m = t.minute
        val ft = f.time
        val minutes = if (m != null && ft != null) abs(m - (ft.hour * 60 + ft.minute)).toDouble() else 0.0
        return minutes * 1_000_000 + abs(t.quantity - f.quantity)
    }

    private fun parts(rows: List<FillSheetReader.Fill>, quantity: Double): List<FillSheetReader.Fill>? {
        val c = rows.take(8)
        for (mask in 1 until (1 shl c.size)) {
            val n = Integer.bitCount(mask)
            if (n < 2 || n > 4) continue
            val pick = c.indices.filter { mask and (1 shl it) != 0 }.map { c[it] }
            if (abs(pick.sumOf { it.quantity } - quantity) < 1e-9) return pick
        }
        return null
    }

    /** The waiting line a new row becomes: its alert with the read price, or a new one at the row's date and time. */
    fun pending(item: Item, zone: ZoneId, today: LocalDate): TradeAlerts.Pending {
        val amount = item.fills.sumOf { f -> if (f.amount > 0 && FillMath.agrees(f.quantity, f.price, f.amount)) f.amount else f.quantity * f.price }
        item.alert?.let { a ->
            return a.copy(price = FillMath.round(amount / a.quantity), amount = amount, filledBy = TradeAlerts.BY_CAPTURE)
        }
        val f = item.fill
        val at = (f.date ?: today).atTime(f.time ?: LocalTime.NOON).atZone(zone).toInstant().toEpochMilli()
        return TradeAlerts.Pending(at, CAPTURE_APP, f.side, f.symbol, f.name, f.quantity, f.price, f.market, f.amount, filledBy = TradeAlerts.BY_CAPTURE)
    }

    /** A written trade changed to what the capture says: quantity, price and minute only (결정 AK-D2). */
    fun fixed(item: Item, zone: ZoneId): Trade? {
        val t = item.trade ?: return null
        val qty = item.fills.sumOf { it.quantity }
        val money = item.fills.sumOf { it.quantity * it.price }
        val f = item.fill
        return t.copy(
            quantity = qty,
            price = if (money > 0) FillMath.round(money / qty) else t.price,
            minute = f.time?.let { it.hour * 60 + it.minute } ?: t.minute,
        )
    }

    /** The app name a capture's own line carries. */
    const val CAPTURE_APP = "체결 내역 캡처"

    /** The minute of day of a waiting alert, for showing it beside the capture. */
    fun minuteOf(a: TradeAlerts.Pending, zone: ZoneId): Int = Instant.ofEpochMilli(a.at).atZone(zone).let { it.hour * 60 + it.minute }
}
