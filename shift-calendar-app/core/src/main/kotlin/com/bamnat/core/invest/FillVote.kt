package com.bamnat.core.invest

import kotlin.math.abs

/**
 * [READ-13][INV-40] 1.27 순번 446: a 체결 내역 capture read several ways ([com.bamnat.core.imports.PhotoVariants]: as taken, its
 * light evened out, black and white, twice the size, a dark screen turned light) and the fills put together.
 *
 * The anchor is the reading with the most sure fills. A fill stays sure only when no other reading of the same fill (same
 * name or code, same side) gives another 수량 or 단가; a field read two ways is asked ([FillSheetReader.Fill.unsure]). A fill
 * only other readings found is added for a look (its name asked), never filled quietly. Which fills to ask about after
 * that, and not asking again, is the capture screen's (묶음 AK, 순번 450).
 */
object FillVote {
    private fun same(a: Double, b: Double) = abs(a - b) <= maxOf(0.0001, abs(b) * 1e-9)

    private fun matches(a: FillSheetReader.Fill, b: FillSheetReader.Fill): Boolean =
        a.side == b.side && (a.name.isNotBlank() && a.name == b.name || a.symbol.isNotBlank() && a.symbol == b.symbol ||
            // The same numbers under a name read another way (삼성건자 for 삼성전자, 1.27 bench f03): the same fill.
            a.quantity > 0 && same(a.quantity, b.quantity) && a.price > 0 && same(a.price, b.price))

    /**
     * The fill of reading [r] that is [anchor]'s [i]-th. Fills come in screen order, so a reading with as many fills gives the
     * one in the same place (same side), whatever its name was read as; otherwise the k-th fill with the same name or code
     * when the anchor has the same name k times (two 마이크로소프트 buys on one screen are two fills, 1.27 stress).
     */
    private fun counterpart(anchor: List<FillSheetReader.Fill>, i: Int, r: List<FillSheetReader.Fill>): FillSheetReader.Fill? {
        val f = anchor[i]
        if (r.size == anchor.size && r[i].side == f.side) return r[i]
        val k = anchor.take(i).count { matches(f, it) }
        val alike = r.filter { matches(f, it) }
        return alike.getOrNull(k) ?: alike.firstOrNull()
    }

    /** The value more than half of [values] give, else null. */
    private fun majority(values: List<Double>): Double? {
        if (values.isEmpty()) return null
        val top = values.groupBy { v -> values.first { same(it, v) } }.maxByOrNull { it.value.size } ?: return null
        return if (top.value.size * 2 > values.size) top.key else null
    }

    fun merge(reads: List<List<FillSheetReader.Fill>>): List<FillSheetReader.Fill> {
        val usable = reads.filter { it.isNotEmpty() }
        if (usable.size <= 1) return usable.firstOrNull().orEmpty()
        val anchor = usable.maxWith(compareBy<List<FillSheetReader.Fill>> { r -> r.count { it.sure } }.thenBy { it.size })
        val others = usable.filter { it !== anchor }
        val out = anchor.mapIndexed { i, f0 ->
            val theirs = others.mapNotNull { r -> counterpart(anchor, i, r) }
            var f = f0
            val ask = f.unsure.toMutableSet()
            val split = HashSet<FillSheetReader.Field>()
            if (theirs.any { !same(it.quantity, f.quantity) }) split += FillSheetReader.Field.QUANTITY
            if (theirs.any { !same(it.price, f.price) }) split += FillSheetReader.Field.PRICE
            if (theirs.any { it.amount > 0 && f.amount > 0 && !same(it.amount, f.amount) }) split += FillSheetReader.Field.AMOUNT
            if (split.isNotEmpty()) {
                // [INVA-02] 1.29 AR: readings that differ are put to a vote. The 수량 and 단가 more than half of them give stand
                // only when they and an amount read then agree to the last won (FillMath.exact) and no other reading's numbers also do;
                // anything else is asked, as before.
                val all = theirs + f0
                val q = majority(all.map { it.quantity })
                val p = majority(all.map { it.price })
                // The amount is a check only (a trade keeps 수량 and 단가): the reading of it nearest 수량 × 단가.
                val a = if (q != null && p != null) all.filter { it.amount > 0 }.minByOrNull { abs(it.amount - q * p) }?.amount else null
                val settles = q != null && p != null && a != null && FillMath.exact(q, p, a, f0.market) &&
                    all.none { o -> (!same(o.quantity, q) || !same(o.price, p)) && FillMath.agrees(o.quantity, o.price, if (o.amount > 0) o.amount else a) }
                // An amount read several ways is not kept as any one of them: 수량 × 단가 to the won (cent) stands for it.
                val unit = if (f0.market.foreign) 100.0 else 1.0
                val amount = if (FillSheetReader.Field.AMOUNT in split && q != null && p != null) Math.round(q * p * unit) / unit else a
                if (settles) f = f0.copy(quantity = q!!, price = p!!, amount = amount!!) else ask += split
            }
            // The name most readings give; asked unless clearly so.
            val names = (theirs + f).filter { it.name.isNotBlank() }.groupingBy { it.name }.eachCount()
            val name = names.maxByOrNull { it.value }?.let { top -> if (names[f.name] == top.value) f.name else top.key } ?: f.name
            if (names.size > 1 && !com.bamnat.core.imports.RosterVote.clear(names.values.sortedDescending())) ask += FillSheetReader.Field.NAME
            f.copy(name = name, unsure = ask)
        }
        val extra = others.flatten().filter { o -> out.none { matches(it, o) } }
            .distinctBy { it.side to (it.name.ifBlank { it.symbol }) }
            .map { it.copy(unsure = it.unsure + FillSheetReader.Field.NAME) }
        return out + extra
    }
}
