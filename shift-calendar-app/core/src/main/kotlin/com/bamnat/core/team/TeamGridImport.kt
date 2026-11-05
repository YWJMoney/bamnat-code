package com.bamnat.core.team

import java.time.LocalDate

/**
 * 1.38 가져오기 (사용자 2026-11-05 "종이 사진이나 엑셀 표, PDF 등 어떤 형식이든 완벽하게 인식해서 틀에 관계없이 관리"): a roster read
 * from any file (the 인식 갈래's `RosterGrid`, whose rows come here as [Row]) onto a team's board, in the leader's own steps:
 * ① each name matched to a member, or added; ② each code of the file paired once with a kind of day ([guessCodes] guesses, the
 * leader fixes); ③ cells the reader was unsure of, and codes left unpaired, kept for the keypad to look at first; ④ all of it
 * on the board as one change, so one 되돌리기 takes it back. A locked cell is never written over.
 */
object TeamGridImport {
    /** One person's line as read: original codes by date, and the dates the reader was unsure of. */
    data class Row(val name: String, val role: String? = null, val cells: Map<LocalDate, String>, val doubtful: Set<LocalDate> = emptySet())

    /** Words that mean a day off in rosters people make, beside the team's own off label. */
    private val OFF_WORDS = setOf("O", "OFF", "휴", "휴무", "비", "비번", "오프", "-", "X", "/", "OF", "쉼", "연", "연차", "V", "VA")
    private val DAY_WORDS = mapOf(
        "D" to setOf("D", "DAY", "데이", "주", "주간", "A"), "E" to setOf("E", "EVE", "EVENING", "이브", "이브닝", "저녁", "B"),
        "N" to setOf("N", "NIGHT", "나이트", "야", "야간", "밤", "C"),
    )

    private fun key(s: String) = s.trim().replace(" ", "").uppercase()

    /**
     * The kind of day each code most likely is: the team's own label or name first, then the words rosters use (휴·OFF·데이·나이트 …).
     * A code not guessed is null: the leader pairs it, or leaves it for the keypad.
     */
    fun guessCodes(codes: Collection<String>, shifts: List<TeamShift>): Map<String, Long?> = codes.distinct().associateWith { code ->
        val k = key(code)
        if (k.isEmpty()) return@associateWith null
        shifts.firstOrNull { key(it.label) == k }?.id
            ?: shifts.firstOrNull { key(it.name) == k }?.id
            ?: if (k in OFF_WORDS) shifts.firstOrNull { it.isOff }?.id
            else DAY_WORDS.entries.firstOrNull { k in it.value }?.key?.let { d -> shifts.firstOrNull { !it.isOff && key(it.label) == d }?.id }
    }

    /** Which member each row is: the same name (spaces and case aside, roles taken off), else null to be added. */
    fun matchNames(rows: List<Row>, members: List<TeamMember>): List<String?> {
        val used = HashSet<String>()
        fun norm(s: String) = TeamPaste.people(s).firstOrNull()?.name.orEmpty().replace(" ", "").lowercase()
        return rows.map { r ->
            val n = norm(r.name)
            members.firstOrNull { it.active && it.id !in used && norm(it.name) == n }?.also { used += it.id }?.id
        }
    }

    /** What putting the rows on the board did, for the line after it and the keypad. */
    data class Applied(
        val board: TeamBoard,
        /** Cells written. */
        val written: Int,
        /** Cells the reader was unsure of, or whose code has no kind of day yet: the keypad goes to these first. */
        val toCheck: Set<Pair<String, LocalDate>>,
        /** Days outside the board's month (a 26일~25일 roster) left for that month. */
        val outside: Int,
        /** Locked cells kept as they were. */
        val keptLocked: Int,
    )

    /**
     * [rows] onto [board]: row i goes to member [memberOf] i (null: the row is skipped), each code through [codes]. A code with
     * no kind of day leaves the cell as it is and marks it to check.
     */
    fun apply(board: TeamBoard, rows: List<Row>, memberOf: List<String?>, codes: Map<String, Long?>): Applied {
        var b = board
        var written = 0
        var outside = 0
        var locked = 0
        val check = HashSet<Pair<String, LocalDate>>()
        rows.forEachIndexed { i, r ->
            val id = memberOf.getOrNull(i) ?: return@forEachIndexed
            r.cells.forEach { (date, code) ->
                if (java.time.YearMonth.from(date) != board.month) { outside++; return@forEach }
                if (board.cell(id, date)?.locked == true) { locked++; return@forEach }
                val shift = codes[code]
                if (shift == null) { if (code.isNotBlank()) check += id to date; return@forEach }
                b = b.paint(id, date, shift, CellSource.PHOTO)
                written++
                if (date in r.doubtful) check += id to date
            }
            r.doubtful.filter { java.time.YearMonth.from(it) == board.month && it !in r.cells }.forEach { check += id to it }
        }
        return Applied(b, written, check, outside, locked)
    }
}
