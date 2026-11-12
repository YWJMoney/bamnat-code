package com.bamnat.core.imports

/**
 * [READ-19] 1.27 순번 447: a roster cell the user corrected on "사진과 같은가요?", and what it teaches the calendar's code
 * book ([RosterRow.raw]: the word the photo gave for that day).
 *
 * Only a word the reader was not sure of teaches: a code it did not know, or one it matched by a guess (a common code,
 * a word of the other alphabet). A word it knew exactly (D for 데이) that the user changed is a misread letter, not a new
 * meaning, and teaches nothing. And a word teaches only when the user gave it the same meaning [TIMES] times (two days,
 * or two months): one slip of the finger never changes how next month's roster reads.
 */
object RosterLearn {
    const val TIMES = 2

    /** The word [row] read on [day], when changing that day from what was read to [to] says what the word means. */
    fun word(row: RosterRow, day: Int, to: DutyCell?): String? {
        if (to == null || to is DutyCell.Unknown) return null
        val was = row.cells[day] ?: return null
        if (was == to) return null
        if (was !is DutyCell.Unknown && day !in row.guessed) return null
        val raw = row.raw[day]?.trim().orEmpty()
        return DutyCodes.norm(raw).takeIf { it.isNotEmpty() && it.length <= 6 }?.let { raw }
    }

    /**
     * 2.0.0 [RVL-80] (검토 I07bc-02) '네, 같아요' on [row]: the words read by a guess (a common code, a stroke off) that stay as read in
     * [cells], each with what it means and on how many days the user saw it so. A day changed by hand is [word]'s, not this; a word
     * that came out with two meanings teaches nothing.
     */
    fun confirmed(row: RosterRow, cells: Map<Int, DutyCell>): Map<String, Pair<DutyCell, Int>> {
        val seen = LinkedHashMap<String, MutableList<Pair<String, DutyCell>>>()
        row.guessed.forEach { d ->
            val was = row.cells[d] ?: return@forEach
            val now = cells[d] ?: return@forEach
            if (now != was || now is DutyCell.Unknown || now is DutyCell.Hours || now is DutyCell.Units) return@forEach
            val raw = row.raw[d]?.trim().orEmpty()
            val key = DutyCodes.norm(raw)
            if (key.isEmpty() || key.length > 6) return@forEach
            seen.getOrPut(key) { mutableListOf() } += raw to now
        }
        return seen.values.mapNotNull { said ->
            said.map { it.second }.distinct().singleOrNull()?.let { cell -> said.first().first to (cell to said.size) }
        }.toMap()
    }
}
