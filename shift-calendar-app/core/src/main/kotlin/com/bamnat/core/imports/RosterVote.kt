package com.bamnat.core.imports

/**
 * [READ-13] 1.27 순번 446: one roster photo read several ways ([PhotoVariants]) and the readings put together cell by cell.
 *
 * The reading with the most cells is the anchor (its lines, names and places). Each of its lines is found again in the
 * other readings, by name, else by the line that agrees with it most. Then, day by day:
 * - two readings or more agree and none says otherwise → that cell, as sure as the anchor had it;
 * - only one reading has the day (the others lost it) → that cell, in yellow ([RosterRow.unsure]): likely, but looked at;
 * - readings disagree → the most read one (the anchor's on a tie), in yellow.
 * So a cell one reading lost comes back already filled, and a cell read two ways never goes in without a look
 * (사용자 "모든 사진 인식 부분에 오류나 버그 인식 부정확이 일어나지 않게"). A single reading is returned as it is.
 */
object RosterVote {
    /** Fewest days two lines share before they are taken for the same person's by their cells alone. */
    private const val MIN_SHARED = 5

    fun merge(reads: List<RosterPhotoRead>): RosterPhotoRead {
        val usable = reads.filter { it.rows.isNotEmpty() }
        if (usable.isEmpty()) return reads.firstOrNull() ?: RosterPhotoRead(emptyList(), 0.0, emptyList(), RosterPhotoRead.Problem.NO_TEXT)
        if (usable.size == 1) return usable[0]
        val anchor = usable.maxBy { r -> r.rows.sumOf { it.cells.size - it.unknown } }
        val others = usable.filter { it !== anchor }
        val rows = anchor.rows.map { a -> vote(a, others.mapNotNull { match(a, it.rows) }) }
        return anchor.copy(rows = rows)
    }

    private fun key(label: String) = DutyCodes.norm(label)

    /** [a]'s line in another reading: the same name, else the line whose cells agree with it most (at least 60%). */
    fun match(a: RosterRow, rows: List<RosterRow>): RosterRow? {
        if (a.label.isNotBlank()) rows.firstOrNull { it.label.isNotBlank() && key(it.label) == key(a.label) }?.let { return it }
        return rows.mapNotNull { r ->
            val shared = a.cells.keys.intersect(r.cells.keys)
            if (shared.size < MIN_SHARED) return@mapNotNull null
            val same = shared.count { a.cells[it] == r.cells[it] }
            (r to same.toDouble() / shared.size).takeIf { it.second >= 0.6 }
        }.maxByOrNull { it.second }?.first
    }

    /**
     * Sure enough to go in without a look: read alike at least twice and by far the most (3 to 1, 4 to 1 …); 2 to 1 or 1
     * alone is looked at.
     */
    fun clear(sorted: List<Int>): Boolean {
        val top = sorted.firstOrNull() ?: return false
        val second = sorted.getOrNull(1) ?: 0
        return top >= 2 && (second == 0 || top >= 3 * second)
    }

    /** [a] with each day decided by all the readings of that line. */
    fun vote(a: RosterRow, others: List<RosterRow>): RosterRow {
        if (others.isEmpty()) return a
        val all = listOf(a) + others
        val days = all.flatMap { it.cells.keys }.toSortedSet()
        val cells = HashMap<Int, DutyCell>()
        val unsure = HashSet<Int>()
        val guessed = HashSet<Int>()
        for (d in days) {
            val votes = all.mapNotNull { r -> r.cells[d]?.let { r to it } }
            val known = votes.filter { it.second !is DutyCell.Unknown }
            if (known.isEmpty()) {
                // Only letters no reading knew: the anchor's (or the first) unknown, still asked.
                cells[d] = (a.cells[d] ?: votes.first().second)
                continue
            }
            val counts = known.groupingBy { it.second }.eachCount()
            val top = counts.values.max()
            val tied = counts.filterValues { it == top }.keys
            val pick = a.cells[d]?.takeIf { it in tied } ?: tied.first()
            cells[d] = pick
            if (!clear(counts.values.sortedDescending())) unsure += d
            // The anchor's own doubt (a cell off its column's middle) stays when the cell is the anchor's.
            if (d in a.unsure && a.cells[d] == pick) unsure += d
            // A doubt any reading had about the word it read there (a word of the other alphabet, a lone 0) stays.
            if (known.any { (r, c) -> c == pick && d in r.guessed }) guessed += d
        }
        return a.copy(cells = cells, unsure = unsure, guessed = guessed, notes = a.notes.filterKeys { cells[it] == a.cells[it] })
    }
}
