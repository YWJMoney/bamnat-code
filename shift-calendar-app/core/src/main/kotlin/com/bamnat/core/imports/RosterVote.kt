package com.bamnat.core.imports

import kotlin.math.abs

/**
 * [READ-13] 1.27 순번 446: one roster photo read several ways ([PhotoVariants]) and the readings put together cell by cell.
 *
 * The reading with the most cells is the anchor (its lines, names and places). Each of its lines is found again in the
 * other readings, by name, else by the line that agrees with it most. Then, day by day:
 * - two readings or more agree and none says otherwise → that cell, as sure as the anchor had it;
 * - only one reading has the day (the others lost it) → that cell, in yellow ([RosterRow.unsure]): likely, but looked at;
 * - readings disagree → the most read one (the anchor's on a tie), in yellow.
 * So a cell one reading lost comes back already filled, and a cell read two ways never goes in without a look
 * (사용자 "모든 사진 인식 부분에 오류나 버그 인식 부정확이 일어나지 않게"). A single reading is returned as it is, its 조 repeat filled.
 */
object RosterVote {
    /** Fewest days two lines share before they are taken for the same person's by their cells alone. */
    private const val MIN_SHARED = 5

    fun merge(reads: List<RosterPhotoRead>): RosterPhotoRead {
        val usable = reads.filter { it.rows.isNotEmpty() }
        if (usable.isEmpty()) return reads.firstOrNull() ?: RosterPhotoRead(emptyList(), 0.0, emptyList(), RosterPhotoRead.Problem.NO_TEXT)
        // [READ-36] One reading (a clean photo read once) gets the repeat too.
        if (usable.size == 1) return usable[0].let { it.copy(rows = RosterPeriod.fill(it.rows)) }
        val anchor = reads[anchor(reads)]
        val others = usable.filter { it !== anchor }
        val gap = gap(anchor.rows)
        val taken = HashSet<RosterRow>()
        val rows = anchor.rows.map { a -> vote(a, others.mapNotNull { o -> match(a, o.rows, gap)?.also { taken += it } }) }.toMutableList()
        // A nameless line named by the others' readings keeps no name another line has (1조 and 4조 share a rota).
        anchor.rows.forEachIndexed { i, a ->
            if (a.label.isBlank() && rows[i].label.isNotBlank() && rows.indices.any { j -> j != i && key(rows[j].label) == key(rows[i].label) }) rows[i] = rows[i].copy(label = "")
        }
        rows += missed(anchor.rows, others.map { o -> o.rows.filter { it !in taken } }, gap)
        rows.sortBy { if (it.y > 0) it.y else Int.MAX_VALUE }
        // [READ-36] A 조 roster's repeat fills what every reading lost (yellow).
        return anchor.copy(rows = RosterPeriod.fill(pieces(folded(rows))))
    }

    /**
     * [READ-41] 1.38 PC 11차 (조-근무표-빈칸6.png on ML Kit): one reading broke 1조's line in two, and the nameless piece
     * matched a different line in each other reading, so it came out as '3번째 줄 · 18일' whose days the readings mostly
     * disagree on. Among named lines, a nameless one with fewer than a third of its days read alike, or under half the days
     * a named line has, is no one's line.
     */
    fun pieces(rows: List<RosterRow>): List<RosterRow> {
        val named = rows.filter { it.label.isNotBlank() }
        if (named.size < 2) return rows
        val usual = named.map { it.cells.size }.sorted()[named.size / 2]
        return rows.filterNot { r ->
            val known = r.cells.filterValues { it !is DutyCell.Unknown }.keys
            r.label.isBlank() && ((known - r.unsure).size * 3 < r.cells.size || known.size * 2 < usual)
        }
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

    /** [READ-41] The usual step between the lines in the photo (pixels), 0 when the lines' places are not known. */
    fun gap(rows: List<RosterRow>): Int {
        val ys = rows.map { it.y }.filter { it > 0 }.sorted()
        val steps = ys.zipWithNext { a, b -> b - a }.filter { it > 0 }.sorted()
        return steps.getOrNull(steps.size / 2) ?: 0
    }

    /** Whether [r] stands where [a] does (within half a line), or either place is unknown. */
    private fun near(a: RosterRow, r: RosterRow, gap: Int) = gap <= 0 || a.y <= 0 || r.y <= 0 || abs(a.y - r.y) * 2 <= gap

    /**
     * [a]'s line in another reading: the same name, else the line whose cells agree with it most (at least 60%). [READ-41]
     * PC 11차 (a 병동 듀티 where three people share each rota): the line in the same place of the photo, not another
     * person's with the same days.
     */
    fun match(a: RosterRow, rows: List<RosterRow>, gap: Int = 0): RosterRow? {
        if (a.label.isNotBlank()) rows.firstOrNull { it.label.isNotBlank() && key(it.label) == key(a.label) && near(a, it, gap) }?.let { return it }
        return rows.filter { near(a, it, gap) }.mapNotNull { r ->
            val shared = a.cells.keys.intersect(r.cells.keys)
            if (shared.size < MIN_SHARED) return@mapNotNull null
            val same = shared.count { a.cells[it] == r.cells[it] }
            (r to same.toDouble() / shared.size).takeIf { it.second >= 0.6 }
        }.maxWithOrNull(compareBy<Pair<RosterRow, Double>> { it.second }.thenBy { -abs(a.y - it.first.y) })?.first
    }

    /**
     * [READ-41] PC 11차: lines the anchor reading lost (12 people came out as 10) that other readings kept, named and in a
     * place no anchor line stands, seen in at least two readings: put together like the others.
     */
    private fun missed(anchor: List<RosterRow>, left: List<List<RosterRow>>, gap: Int): List<RosterRow> {
        if (gap <= 0) return emptyList()
        val out = mutableListOf<RosterRow>()
        val names = anchor.map { key(it.label) }.filter { it.isNotEmpty() }.toSet()
        val pool = left.mapIndexed { i, rs -> rs.filter { r -> r.y > 0 && r.label.isNotBlank() && key(r.label) !in names && anchor.none { near(it, r, gap) } }.map { i to it } }.flatten()
        val used = HashSet<RosterRow>()
        for ((i, r) in pool.sortedByDescending { it.second.cells.size }) {
            if (r in used || out.any { near(it, r, gap) }) continue
            val same = pool.filter { (j, o) -> j != i && o !in used && near(r, o, gap) }.groupBy { it.first }.map { (_, os) -> os.minBy { abs(it.second.y - r.y) }.second }
            if (same.isEmpty()) continue
            used += r; used += same
            out += vote(r, same)
        }
        return out
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

    /**
     * [READ-41] PC 11차: the line's name as most readings read it, a whole name (2~4 글자) before a piece of one (정 for
     * 정하늘); the anchor's own name on a tie.
     */
    fun label(all: List<RosterRow>): String {
        val names = all.map { it.label.trim() }.filter { it.isNotBlank() }
        if (names.isEmpty()) return all.first().label
        fun whole(n: String) = n.length in 2..4 && n.all { it in '가'..'힣' }
        val counts = names.groupingBy { key(it) }.eachCount()
        val best = names.maxWith(compareBy<String> { whole(it) }.thenBy { counts.getValue(key(it)) }.thenBy { it == all.first().label.trim() })
        return best
    }

    /** [a] with each day decided by all the readings of that line. */
    fun vote(a: RosterRow, others: List<RosterRow>): RosterRow {
        if (others.isEmpty()) return a
        val all = listOf(a) + others
        val days = all.flatMap { it.cells.keys }.toSortedSet()
        val cells = HashMap<Int, DutyCell>()
        val unsure = HashSet<Int>()
        val guessed = HashSet<Int>()
        val contested = HashSet<Int>()
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
            if (counts.size > 1) contested += d
            // The anchor's own doubt (a cell off its column's middle) stays when the cell is the anchor's.
            if (d in a.unsure && a.cells[d] == pick) unsure += d
            // A doubt any reading had about the word it read there (a word of the other alphabet, a lone 0) stays.
            if (known.any { (r, c) -> c == pick && d in r.guessed }) guessed += d
        }
        return a.copy(label = label(all), cells = cells, unsure = unsure, contested = a.contested + contested, guessed = guessed, notes = a.notes.filterKeys { cells[it] == a.cells[it] })
    }
}
