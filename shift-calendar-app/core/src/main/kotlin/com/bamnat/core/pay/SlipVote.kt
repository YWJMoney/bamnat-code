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
        val sure: Boolean get() = unsure.isEmpty() && adds(read) && stated(read)
    }

    /** [PHOTO-23] 지급, 공제 or a total: a line's side of the payslip. */
    private fun side(l: SlipLine): Int = when {
        l.role in TOTALS && l.role != SlipRole.DEDUCTION -> 2
        l.role == SlipRole.DEDUCTION -> 1
        else -> 0
    }

    private fun key(l: SlipLine): String =
        l.role.name + ":" + PayslipReader.ocrFix(l.name.filter { it.isLetter() }).take(3)

    fun merge(texts: List<String>): Result =
        mergeResults(texts.filter { it.isNotBlank() }.map { Result(PayslipReader.read(it), emptySet(), it) })

    /**
     * [PHOTO-03] 1.29: the readings as each was read (by [SlipTable] or [PayslipReader]) with the lines each was unsure of
     * itself (a name matched to the nearest printed one, a letter read as a digit). Such a line stays to look at unless the
     * payslip's sums vouch for it.
     */
    fun mergeResults(results: List<Result>): Result {
        val reads = results.filter { it.read.lines.isNotEmpty() || it.text.isNotBlank() }
        if (reads.isEmpty()) return Result(PayslipRead(emptyList()), emptySet(), "")
        if (reads.size == 1) return Result(reads[0].read, checked(reads[0].read, reads[0].unsure), reads[0].text)
        // [PHOTO-23] 1.30: on a tie the photo as taken (the first reading) is the anchor, and it stays so unless another reads two
        // more amounts: a watermark number read once more no longer moved the anchor, and with it the lines' order and names
        // (사용자 2026-10-27 "사진마다 읽는 부분이 다르다", R3 A-1 #4).
        val first = reads.first()
        val anchorResult = reads.maxWith(compareBy<Result> { adds(it.read) }.thenBy { it.read.consistent }
            .thenBy { r -> r.read.lines.count { it.amount != null } + if (r === first) 1 else 0 })
        val anchor = anchorResult.read
        val anchorText = anchorResult.text
        val others = reads.filter { it !== anchorResult }.map { it.read }
        val unsure = HashSet<Int>(anchorResult.unsure)
        val lines = anchor.lines.toMutableList()
        val anchorKeys = anchor.lines.map(::key).toSet()
        val renamed = HashMap<Int, SlipLine>()
        anchor.lines.forEachIndexed { i, line ->
            if (line.amount == null) return@forEachIndexed
            val k = key(line)
            // [PHOTO-23] A line another reading named otherwise (its name's letters read another way, so the keys differ) but
            // read with the same amount on the same side is the same line: it backs the amount up.
            val theirs = others.mapNotNull { r -> r.lines.firstOrNull { key(it) == k && it.amount != null }?.amount }
                .ifEmpty { others.mapNotNull { r -> r.lines.firstOrNull { it.amount == line.amount && side(it) == side(line) && key(it) !in anchorKeys }?.amount } }
            // [PHOTO-12] Hours too: one reading that lost a line's hours (a box read short) takes what the others read.
            val hours = (others.mapNotNull { r -> r.lines.firstOrNull { key(it) == k && it.amount != null }?.minutes } + listOfNotNull(line.minutes))
                .groupingBy { it }.eachCount()
            if (hours.isNotEmpty()) {
                val topH = hours.values.max()
                val m = line.minutes?.takeIf { hours[it] == topH } ?: hours.filterValues { it == topH }.keys.first()
                if (m != line.minutes) lines[i] = lines[i].copy(minutes = m)
            }
            // [PHOTO-27] The name as another reading read it whole: one reading kept a piece of a word the reader cut ("분(지급)"
            // for 소급분(지급), "리비" for 남부_사택관리비); the same amount on the same side read with a longer name ending or
            // starting the same is the line's name.
            val mine = line.name.filter { it.isLetter() }
            if (mine.isNotEmpty()) {
                val fuller = others.mapNotNull { r ->
                    r.lines.firstOrNull { o ->
                        val theirs = o.name.filter { it.isLetter() }
                        o.amount == line.amount && side(o) == side(line) && theirs.length > mine.length && (theirs.endsWith(mine) || theirs.startsWith(mine))
                    }
                }.distinctBy { it.name.filter(Char::isLetter) }
                // Two other names it could be the piece of (볼링회(남부공장)·한맥산악회(남부공장) for "(남부공장"): looked at.
                if (fuller.size > 1) unsure += i
                if (fuller.size == 1) renamed[i] = fuller.single()
                else if (line.name.trim().contains(' ')) others.firstNotNullOfOrNull { r ->
                    r.lines.firstOrNull { o -> o.amount == line.amount && !o.name.trim().contains(' ') && o.name.filter(Char::isLetter) == mine }
                }?.let { renamed[i] = it }
                // The kind as the whole name says (야간수 read as a 수당, 야간수당 is 야간).
                renamed[i]?.let { lines[i] = lines[i].copy(name = it.name, role = it.role) }
            }
            // Read by one way only: nothing backs it up but the payslip's own sums (checked below).
            if (theirs.isEmpty()) { unsure += i; return@forEachIndexed }
            val counts = (theirs + line.amount).groupingBy { it }.eachCount()
            val top = counts.values.max()
            val pick = line.amount.takeIf { counts[it] == top } ?: counts.filterValues { it == top }.keys.first()
            if (pick != line.amount) lines[i] = lines[i].copy(amount = pick)
            // [READ-16] Any reading saying otherwise is looked at, unless the sums vouch for the line (checked below): 3 to 1
            // can be the same misread three times (a last digit lost the same way).
            if (counts.size > 1) unsure += i
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
        if (adds(anchor) && !adds(read)) {
            val named = anchor.copy(lines = anchor.lines.mapIndexed { i, l -> renamed[i]?.let { l.copy(name = it.name, role = it.role) } ?: l })
            return Result(named, checked(named, disagreed), anchorText)
        }
        return Result(read, checked(read, unsure), anchorText)
    }

    /**
     * [unsure] less the lines the payslip's own sums vouch for ([SlipSums.vouched]): the 지급 lines when they add up to the
     * 지급 총액 it states, the 공제 lines when they add up to its 공제 합계, and 실수령액 when it is 지급 − 공제.
     */
    private fun checked(read: PayslipRead, unsure: Set<Int>): Set<Int> = unsure - SlipSums.vouched(read)

    /**
     * The 지급 lines add up to the 지급 총액 to the won. [PayslipRead.consistent] allows a little (rounding, for matching the
     * app's own sums), which would let a last 0 read as 6 vouch for itself (1.27 stress #35: 472,006 for 472,000).
     */
    fun adds(read: PayslipRead): Boolean = read.earnings.isNotEmpty() && read.statedGross == read.earningsSum

    /** The payslip states its 지급 총액 (or a 합계), so the lines can be checked against it. */
    fun stated(read: PayslipRead): Boolean = read.lines.any { (it.role == SlipRole.GROSS || it.role == SlipRole.TOTAL) && it.amount != null }

    private val TOTALS = setOf(SlipRole.GROSS, SlipRole.TOTAL, SlipRole.DEDUCTION, SlipRole.DEDUCTION_TOTAL, SlipRole.NET)
}
