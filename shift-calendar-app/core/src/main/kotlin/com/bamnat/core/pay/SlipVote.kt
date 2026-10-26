package com.bamnat.core.pay

/**
 * [READ-13] 1.27 순번 446: a payslip (or 회사 계산 방법) photo read several ways ([com.bamnat.core.imports.PhotoVariants]) and the
 * readings put together line by line.
 *
 * The anchor is the reading whose lines add up to its 지급 총액 ([PayslipRead.consistent]), else the one with the most amounts.
 * Each line's amount is the one most readings give for that line (same kind, same name); a line read with two amounts is
 * kept for a look ([Result.unsure]). A line the anchor missed but half the readings or more have is put back, also for a
 * look unless every reading but the anchor has it alike. Nothing here guesses a number no reading gave.
 */
object SlipVote {
    /** The put-together payslip, its lines to look at (by index in [read]'s lines), and the text it was read from. */
    data class Result(val read: PayslipRead, val unsure: Set<Int>, val text: String) {
        /**
         * Every line agreed by the readings and the lines add up to a 지급 총액 the payslip states: nothing to ask. Without a
         * stated total the lines "add up" to their own sum, which says nothing (1.27 bench p07, a blurred payslip).
         */
        val sure: Boolean get() = unsure.isEmpty() && read.consistent && stated(read)
    }

    private fun key(l: SlipLine): String =
        l.role.name + ":" + PayslipReader.ocrFix(l.name.filter { it.isLetter() }).take(3)

    fun merge(texts: List<String>): Result {
        val reads = texts.filter { it.isNotBlank() }.map { it to PayslipReader.read(it) }
        if (reads.isEmpty()) return Result(PayslipRead(emptyList()), emptySet(), "")
        if (reads.size == 1) return Result(reads[0].second, emptySet(), reads[0].first)
        val (anchorText, anchor) = reads.maxWith(compareBy<Pair<String, PayslipRead>> { it.second.consistent }
            .thenBy { r -> r.second.lines.count { it.amount != null } })
        val others = reads.map { it.second }.filter { it !== anchor }
        val unsure = HashSet<Int>()
        val lines = anchor.lines.toMutableList()
        anchor.lines.forEachIndexed { i, line ->
            if (line.amount == null) return@forEachIndexed
            val k = key(line)
            val theirs = others.mapNotNull { r -> r.lines.firstOrNull { key(it) == k && it.amount != null }?.amount }
            if (theirs.isEmpty()) return@forEachIndexed
            val counts = (theirs + line.amount).groupingBy { it }.eachCount()
            val top = counts.values.max()
            val pick = line.amount.takeIf { counts[it] == top } ?: counts.filterValues { it == top }.keys.first()
            if (pick != line.amount) lines[i] = line.copy(amount = pick)
            if (!com.bamnat.core.imports.RosterVote.clear(counts.values.sortedDescending())) unsure += i
        }
        val disagreed = unsure.toSet()
        // Lines the anchor did not have.
        val have = lines.map(::key).toMutableSet()
        others.flatMap { r -> r.lines.filter { it.amount != null } }.groupBy(::key).forEach { (k, found) ->
            if (k in have || found.size * 2 < reads.size) return@forEach
            val counts = found.groupingBy { it.amount }.eachCount()
            val top = counts.maxBy { it.value }
            val line = found.first { it.amount == top.key }
            // Before the totals, where it was on the payslip; the totals come last in every layout read.
            val at = lines.indexOfFirst { it.role in TOTALS }.takeIf { it >= 0 } ?: lines.size
            val shifted = unsure.map { if (it >= at) it + 1 else it }
            unsure.clear(); unsure.addAll(shifted)
            lines.add(at, line)
            have += k
            if (counts.size > 1 || found.size < others.size) unsure += at
        }
        val read = anchor.copy(lines = lines)
        // A put-together that no longer adds up is worse than the anchor as read.
        if (anchor.consistent && !read.consistent) return Result(anchor, checked(anchor, disagreed), anchorText)
        return Result(read, checked(read, unsure), anchorText)
    }

    /**
     * [unsure] less the lines the payslip's own sums vouch for: the 지급 lines when they add up to the 지급 총액 it states,
     * the 공제 lines when they add up to its 공제 합계, and 실수령액 when it is 지급 − 공제. Two readings may put a row's
     * amount beside another name (a side-by-side payslip read black and white); the sums settle which one is right.
     */
    private fun checked(read: PayslipRead, unsure: Set<Int>): Set<Int> {
        val ok = HashSet<Int>()
        val lines = read.lines
        if (stated(read) && read.consistent) lines.forEachIndexed { i, l -> if (l.isEarning || l.role == SlipRole.GROSS || l.role == SlipRole.TOTAL) ok += i }
        val dedTotal = lines.firstOrNull { it.role == SlipRole.DEDUCTION_TOTAL && it.amount != null }?.amount
        val dedSum = lines.filter { it.role == SlipRole.DEDUCTION && it.amount != null }.sumOf { it.amount!! }
        val dedOk = dedTotal != null && dedTotal == dedSum
        if (dedOk) lines.forEachIndexed { i, l -> if (l.role == SlipRole.DEDUCTION || l.role == SlipRole.DEDUCTION_TOTAL) ok += i }
        val g = read.gross
        if (dedOk && g != null && stated(read) && read.consistent) lines.forEachIndexed { i, l -> if (l.role == SlipRole.NET && l.amount == g - dedTotal!!) ok += i }
        return unsure - ok
    }

    /** The payslip states its 지급 총액 (or a 합계), so the lines can be checked against it. */
    fun stated(read: PayslipRead): Boolean = read.lines.any { (it.role == SlipRole.GROSS || it.role == SlipRole.TOTAL) && it.amount != null }

    private val TOTALS = setOf(SlipRole.GROSS, SlipRole.TOTAL, SlipRole.DEDUCTION, SlipRole.DEDUCTION_TOTAL, SlipRole.NET)
}
