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

    fun merge(reads: List<List<FillSheetReader.Fill>>): List<FillSheetReader.Fill> {
        val usable = reads.filter { it.isNotEmpty() }
        if (usable.size <= 1) return usable.firstOrNull().orEmpty()
        val anchor = usable.maxWith(compareBy<List<FillSheetReader.Fill>> { r -> r.count { it.sure } }.thenBy { it.size })
        val others = usable.filter { it !== anchor }
        val out = anchor.mapIndexed { i, f ->
            val theirs = others.mapNotNull { r -> counterpart(anchor, i, r) }
            val ask = f.unsure.toMutableSet()
            if (theirs.any { !same(it.quantity, f.quantity) }) ask += FillSheetReader.Field.QUANTITY
            if (theirs.any { !same(it.price, f.price) }) ask += FillSheetReader.Field.PRICE
            if (theirs.any { it.amount > 0 && f.amount > 0 && !same(it.amount, f.amount) }) ask += FillSheetReader.Field.AMOUNT
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
