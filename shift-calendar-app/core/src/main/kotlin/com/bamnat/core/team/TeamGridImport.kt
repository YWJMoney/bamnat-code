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
    // 2.0.0 [RVJ-27] 검토 T05b-13 (한글 약자를 쓰는 팀은 코드 짝짓기가 덜 됨): 데·이·나·오 as wards write them, 오전·오후, 조근·석근·야근.
    private val OFF_WORDS = setOf("O", "OFF", "휴", "휴무", "휴일", "비", "비번", "오", "오프", "-", "X", "/", "OF", "쉼", "연", "연차", "V", "VA")
    private val DAY_WORDS = mapOf(
        "D" to setOf("D", "DAY", "데", "데이", "주", "주간", "주근", "오전", "조근", "A"),
        "E" to setOf("E", "EVE", "EVENING", "이", "이브", "이브닝", "저녁", "오후", "석근", "B"),
        "N" to setOf("N", "NIGHT", "나", "나이트", "야", "야간", "야근", "밤", "심야", "C"),
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
                   /** [RVJ-27] 검토 PAYU-57: the new member's name as the leader fixed it before putting the file in, by row. */
                   names: Map<Int, String> = emptyMap(),
                   id: () -> String = { java.util.UUID.randomUUID().toString() }): Pair<List<TeamMember>, List<String?>> {
        var sort = (members.maxOfOrNull { it.sort } ?: -1) + 1
        var room = limit - members.size
        val made = ArrayList<TeamMember>()
        val ids = rows.indices.map { i ->
            memberOf.getOrNull(i) ?: if (i in skipped || room <= 0) null else {
                // "민지 책임" as the 팀 만들기 paste reads it: 책임 is the role and the 책임 skill.
                val p = TeamPaste.people((rows[i].name + " " + rows[i].role.orEmpty()).trim()).firstOrNull()
                val name = names[i]?.trim()?.take(TeamPaste.NAME_MAX)?.takeIf { it.isNotEmpty() } ?: p?.name ?: rows[i].name.trim()
                val m = TeamMember(id(), teamId, name, p?.role ?: rows[i].role.orEmpty(), p?.skill ?: Skill.REG, sort = sort++)
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
            // [RVJ-27] The team's own label or name can be any word of the same kind of day: a file's D on a team that writes 데
            // (or 주간), a file's 데 on a team that writes D. It was the letter D·E·N alone, so those teams paired every code by hand.
            else DAY_WORDS.entries.firstOrNull { k in it.value }?.let { (d, words) ->
                (shifts.firstOrNull { !it.isOff && key(it.label) == d } ?: shifts.firstOrNull { !it.isOff && (key(it.label) in words || key(it.name) in words) })?.id
            }
    }

    /**
     * 2.0.0 [RVJ-27] 검토 U05b-16: the codes [guessCodes] paired by the words rosters use, not by the team's own label or name (A·B·C
     * as D·E·N, 연·V as 휴무, a file's 주간 on a team that writes D): the list marks these '짐작' so the leader looks at them.
     */
    fun looselyGuessed(codes: Collection<String>, shifts: List<TeamShift>): Set<String> {
        val guessed = guessCodes(codes, shifts)
        return codes.filter { c -> guessed[c] != null && key(c).let { k -> shifts.none { key(it.label) == k || key(it.name) == k } } }.toSet()
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

    /** A name as it is compared: its role taken off, spaces and case aside. */
    private fun nameKey(s: String) = TeamPaste.people(s).firstOrNull()?.name.orEmpty().replace(" ", "").lowercase()

    /** The name a row's new member would get (the role taken off), for the leader to fix before it goes in. */
    fun newName(row: Row): String = TeamPaste.people((row.name + " " + row.role.orEmpty()).trim()).firstOrNull()?.name ?: row.name.trim()

    /** Which member each row is: the same name (spaces and case aside, roles taken off), else null to be added. */
    fun matchNames(rows: List<Row>, members: List<TeamMember>): List<String?> {
        val used = HashSet<String>()
        return rows.map { r ->
            val n = nameKey(r.name)
            members.firstOrNull { it.active && it.id !in used && nameKey(it.name) == n }?.also { used += it.id }?.id
        }
    }

    /**
     * 2.0.0 [RVJ-27] 검토 PAYU-57 (한 글자 틀리게 읽힌 이름은 '새 팀원으로 더하기'가 기본이라 팀원이 겹쳐 생김): for each row [matched] left
     * without a member, the member whose name is a letter off (a photo read 김민서 as 김민셔, or dropped a letter). The list
     * chooses that member first and asks the leader to check, instead of adding a second 김민서. Only when it is the one such
     * member for the row and the row the one such row for the member; a member the same name already took is not offered.
     */
    fun closeNames(rows: List<Row>, members: List<TeamMember>, matched: List<String?> = matchNames(rows, members)): List<String?> {
        val taken = matched.filterNotNull().toSet()
        val free = members.filter { it.active && it.id !in taken }.map { it.id to nameKey(it.name) }
        val near = rows.indices.associateWith { i ->
            val n = nameKey(rows[i].name)
            if (matched.getOrNull(i) != null || n.isEmpty()) emptyList() else free.filter { closeName(n, it.second) }.map { it.first }
        }
        return rows.indices.map { i -> near.getValue(i).singleOrNull()?.takeIf { id -> near.values.count { id in it } == 1 } }
    }

    /**
     * Two names a misread apart: one letter of three or more (김민지·김민셔, 이수·이수진), one stroke of a Hangul letter at any
     * length (민서·민셔), or two letters of five or more. Not the same name, and not two short names that only share a family name.
     */
    internal fun closeName(a: String, b: String): Boolean {
        if (a == b || a.isEmpty() || b.isEmpty()) return false
        val n = maxOf(a.length, b.length)
        val letters = editDistance(a, b)
        return n >= 3 && letters <= 1 || n >= 5 && letters <= 2 || n >= 2 && letters <= 1 && editDistance(jamo(a), jamo(b)) <= 1
    }

    /** Hangul syllables as their 초성·중성·종성, so 서 and 셔 are one stroke apart and 서 and 지 two. */
    private fun jamo(s: String): String = buildString {
        s.forEach { c ->
            if (c in '\uAC00'..'\uD7A3') {
                val i = c - '\uAC00'
                append((0x1100 + i / 588).toChar()); append((0x1161 + i % 588 / 28).toChar())
                if (i % 28 > 0) append((0x11A7 + i % 28).toChar())
            } else append(c)
        }
    }

    private fun editDistance(a: String, b: String): Int {
        var row = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val next = IntArray(b.length + 1)
            next[0] = i
            for (j in 1..b.length) next[j] = minOf(row[j] + 1, next[j - 1] + 1, row[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            row = next
        }
        return row[b.length]
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
