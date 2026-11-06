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
        val anchor = reads[anchor(reads)]
        val others = usable.filter { it !== anchor }
        val rows = anchor.rows.map { a -> vote(a, others.mapNotNull { match(a, it.rows) }) }
        // [READ-36] A 조 roster's repeat fills what every reading lost (yellow).
        return anchor.copy(rows = RosterPeriod.fill(folded(rows)))
    }

    /**
     * Which of [reads] the put-together keeps the lines, names and places of: the one with the most known cells under a
     * name, then the most known cells. Y9 (PC roster1.jpg): one reading broke each person's line into nameless pieces whose
     * cells added up to the most, and every piece came out as a person of its own; the readings that kept the names whole win.
     */
    fun anchor(reads: List<RosterPhotoRead>): Int {
        fun known(r: RosterRow) = r.cells.size - r.unknown
        val best = reads.indices.filter { reads[it].rows.isNotEmpty() }.maxWithOrNull(
            compareBy<Int> { i -> reads[i].rows.filter { it.label.isNotBlank() }.sumOf { known(it) } }
                .thenBy { i -> reads[i].rows.sumOf { known(it) } },
        )
        return best ?: 0
    }

    /** Fewest days a nameless line shares with a named one before it can be folded into it. */
    private const val FOLD_SHARED = 10

    /**
     * Y9: a nameless line that says what a named line says (90% of at least [FOLD_SHARED] shared days) is that person's
     * line read twice: its days fill the named line's gaps and it goes, so one person never comes out as two.
     */
    fun folded(rows: List<RosterRow>): List<RosterRow> {
        val out = rows.toMutableList()
        for (u in rows.filter { it.label.isBlank() }) {
            val best = out.filter { it.label.isNotBlank() }.mapNotNull { n ->
                val shared = n.cells.keys.intersect(u.cells.keys)
                if (shared.size < FOLD_SHARED) return@mapNotNull null
                (n to shared.count { n.cells[it] == u.cells[it] }.toDouble() / shared.size).takeIf { it.second >= 0.9 }
            }.maxByOrNull { it.second }?.first ?: continue
            val gaps = u.cells.keys - best.cells.keys
            val merged = best.copy(
                cells = (best.cells + gaps.associateWith { u.cells.getValue(it) }).toSortedMap(),
                unsure = best.unsure + gaps.filter { it in u.unsure },
            )
            out[out.indexOf(best)] = merged
            out.remove(u)
        }
        return out
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
