package com.bamnat.core.imports

/**
 * [READ-36] 1.38 (사용자 2026-11-06 "모든 분석 기술을 넣어 최정상급으로"): a 조 roster repeats (주주야야비휴 every 6 days, 4조 2교대
 * every 8). When the cells read for sure in a line repeat with one period, a cell the photo lost or could not make out is the
 * one the period puts there: filled, and yellow (looked at, never quiet), between the line's first and last read day only. A
 * cell read is kept as read: [replaceDoubtful] (a yellow cell the period disagrees with) is off, since a real roster (Y9) broke
 * its repeat on the 31st. A line with no clear period (a 병동 듀티, a 알바 표) is left as it is. Photo bench: +9 cells, silent 0.
 */
object RosterPeriod {
    /** Shortest and longest period looked for. */
    private const val MIN = 2
    private const val MAX = 14
    /** Each day of the period seen read for sure at least this often. */
    private const val PER_CLASS = 2
    /** At most this share of the sure cells may disagree with the period (a 대근, a 연차 on the way). */
    private const val SLACK = 0.05

    data class Period(val length: Int, val at: Map<Int, DutyCell>)

    /** The line's period from its [sure] cells (day → cell), or null. */
    fun of(sure: Map<Int, DutyCell>, lastDay: Int): Period? {
        if (sure.size < 2 * MIN * PER_CLASS) return null
        for (p in MIN..MAX) {
            if (p * PER_CLASS * 2 > lastDay) break
            val classes = sure.entries.groupBy { (it.key - 1) % p }
            if (classes.size < p || classes.values.any { it.size < PER_CLASS }) continue
            val at = classes.mapValues { (_, cs) -> cs.groupingBy { it.value }.eachCount().maxBy { it.value }.key }
            val off = sure.count { (d, c) -> at.getValue((d - 1) % p) != c }
            // A period that only says "the same thing every day" says nothing; a longer one may (1.38 PC 8차: 1조's
            // 주야야비휴주 read as 야 on most odd and even days alike stopped the search at 2, and its blanks stayed blank).
            if (at.values.toSet().size < 2) continue
            if (off <= maxOf(1.0, sure.size * SLACK) && classes.values.all { it.size >= PER_CLASS + if (off > 0) 1 else 0 }) return Period(p, at)
        }
        return null
    }

    fun fill(rows: List<RosterRow>, replaceDoubtful: Boolean = false): List<RosterRow> {
        val last = rows.maxOfOrNull { it.cells.keys.maxOrNull() ?: 0 } ?: return rows
        if (last < 2 * MIN * PER_CLASS) return rows
        return rows.map { row ->
            val doubt = row.doubtful(last)
            // The cells read, a common code's guess (주 → 데이) among them: the period is in what was read, its meaning aside.
            val sure = row.cells.filter { (d, c) -> c !is DutyCell.Unknown && d !in row.unsure }
            // [READ-41] PC 11차: 1조 read two ways on 11 days had too few sure ones on some step of its 6 (5·18일 stayed
            // blank); then every cell read, the vote's pick on the doubtful ones, must keep the repeat as tightly.
            val period = of(sure, last) ?: of(row.cells.filterValues { it !is DutyCell.Unknown }, last) ?: return@map row
            val cells = row.cells.toMutableMap()
            val guessed = row.guessed.toMutableSet()
            val filled = row.filled.toMutableSet()
            val settled = HashSet<Int>()
            // Only between the first and last day the line was read on: past its ends a 조 roster may turn (Y9, a real
            // roster whose 31일 broke the repeat), and a guess there would only look like a reading.
            val span = row.cells.filterValues { it !is DutyCell.Unknown }.keys
            val first = span.minOrNull() ?: 1
            val end = span.maxOrNull() ?: 0
            for (d in first..end) {
                val want = period.at.getValue((d - 1) % period.length)
                val now = cells[d]
                // [READ-41] 1.38 PC 12차: 1조's 3일 read 야 and 비 alike and the anchor's 비 was kept, then 휴 by the sheet's own
                // letters. A cell the readings split on, inside the line (not its first or last two days, where Y9's repeat broke),
                // takes what the repeat says, yellow.
                val split = d in row.unsure && d in (first + 2)..(end - 2) && want !is DutyCell.Unknown
                val fill = now == null || now is DutyCell.Unknown || (replaceDoubtful && d in doubt && now != want) || split && now != want
                if (fill && now != want) { cells[d] = want; guessed += d; if (now == null || now is DutyCell.Unknown) filled += d }
                // [READ-41] 1.38 PC 12.2 (사용성): a clean 조 roster showed '노란 칸 15개' — cells only one or two readings saw. A cell
                // the readings split on that says what the line's repeat says, inside the line, is not one to look at.
                // 12.3: unless two readings gave two different codes there; then the photo may say the other one (the
                // repeat and most readings can be wrong together), so it stays yellow.
                else if (d in row.unsure && d !in row.contested && now == want && d in (first + 2)..(end - 2)) settled += d
            }
            if (cells == row.cells && settled.isEmpty()) row
            else row.copy(cells = cells.toSortedMap(), guessed = guessed, filled = filled, unsure = row.unsure - settled)
        }
    }
}
