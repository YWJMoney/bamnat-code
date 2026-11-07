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

    /** The 인식 갈래's read ([com.bamnat.core.imports.RosterGrid]) as rows to put on a board. */
    fun rows(grid: com.bamnat.core.imports.RosterGrid): List<Row> = grid.rows.map { Row(it.name, it.role, it.cells, it.doubtful) }

    /** How many cells of the rows each code is on, most first: the 코드 짝짓기 list shows the big ones on top. */
    fun codeCounts(rows: List<Row>): List<Pair<String, Int>> =
        rows.flatMap { r -> r.cells.values.filter { it.isNotBlank() } }.groupingBy { it }.eachCount().entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key }).map { it.key to it.value }

    /**
     * The members a row asks for: each row with no member (null in [memberOf], and not [skipped]) a new one after [members],
     * at most [limit] people on the team. Returns the new members and [memberOf] with their ids in.
     */
    fun newMembers(rows: List<Row>, memberOf: List<String?>, skipped: Set<Int>, teamId: String, members: List<TeamMember>, limit: Int = 60,
                   id: () -> String = { java.util.UUID.randomUUID().toString() }): Pair<List<TeamMember>, List<String?>> {
        var sort = (members.maxOfOrNull { it.sort } ?: -1) + 1
        var room = limit - members.size
        val made = ArrayList<TeamMember>()
        val ids = rows.indices.map { i ->
            memberOf.getOrNull(i) ?: if (i in skipped || room <= 0) null else {
                // "민지 책임" as the 팀 만들기 paste reads it: 책임 is the role and the 책임 skill.
                val p = TeamPaste.people((rows[i].name + " " + rows[i].role.orEmpty()).trim()).firstOrNull()
                val m = TeamMember(id(), teamId, p?.name ?: rows[i].name.trim(), p?.role ?: rows[i].role.orEmpty(), p?.skill ?: Skill.REG, sort = sort++)
                room--; made += m; m.id
            }
        }
        return made to ids
    }

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

    // ---- 1.38 [P3-L-17] 종이 신청표: the ward's request sheet photographed, its cells read as 신청 instead of 근무 ----

    private val LEAVE_WORDS = setOf("연", "연차", "V", "VA", "AL", "휴가", "연가")
    private val HALF_WORDS = setOf("반", "반차", "H", "HD", "½")
    private val EDU_WORDS = setOf("교", "교육", "EDU", "연수", "학회", "세미나")
    private val NO_NIGHT_WORDS = setOf("N빼", "N빼기", "NX", "X N", "노나이트", "N제외", "N불가")
    private val WORK_WORDS = setOf("근", "근무", "W", "출")

    /**
     * Each code as a request ([WishKind.code], with the shift for [WishKind.SHIFT]); null for one not guessed. 휴·OFF are 쉬고 싶어요, 연·V
     * 연차, 교 교육, N빼 나이트 빼기, and the team's own labels "이 근무 원해요".
     */
    fun guessWishes(codes: Collection<String>, shifts: List<TeamShift>): Map<String, String?> = codes.distinct().associateWith { code ->
        val k = key(code)
        when {
            k.isEmpty() -> null
            k in LEAVE_WORDS -> WishKind.LEAVE.code(null)
            k in HALF_WORDS -> WishKind.HALF.code(null)
            k in EDU_WORDS -> WishKind.EDU.code(null)
            k in NO_NIGHT_WORDS -> WishKind.AVOID_NIGHT.code(null)
            k in WORK_WORDS -> WishKind.WORK.code(null)
            k in OFF_WORDS -> WishKind.OFF.code(null)
            else -> guessCodes(listOf(code), shifts)[code]?.let { id ->
                if (shifts.firstOrNull { it.id == id }?.isOff == true) WishKind.OFF.code(null) else WishKind.SHIFT.code(id)
            }
        }
    }

    /**
     * A request sheet rather than a 근무표: most rows have only a few days written (사람마다 한 달에 몇 칸), and most of what is
     * written reads as a request (휴·연·교 …). The dialog opens on 신청 then; the leader can switch.
     */
    fun looksLikeRequests(rows: List<Row>, days: Int, shifts: List<TeamShift>): Boolean {
        if (rows.isEmpty() || days <= 0) return false
        val sparse = rows.count { it.cells.size <= days * 0.4 } >= rows.size * 0.7
        val all = rows.flatMap { it.cells.values }
        if (all.isEmpty()) return false
        val asWish = guessWishes(all.toSet(), shifts)
        val requestish = all.count { asWish[it]?.substringBefore(':') in setOf("OFF", "LEAVE", "HALF", "EDU", "AVOID_NIGHT") }
        return sparse && requestish >= all.size * 0.6
    }

    /** The requests of the rows, each code by [codes] (null: not put in). */
    fun wishes(rows: List<Row>, memberOf: List<String?>, codes: Map<String, String?>, at: Long): List<TeamWish> = rows.flatMapIndexed { i, r ->
        val id = memberOf.getOrNull(i) ?: return@flatMapIndexed emptyList()
        r.cells.mapNotNull { (d, code) -> codes[code]?.let(WishKind::parse)?.let { (k, s) -> TeamWish(id, d, k, s, at) } }
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
