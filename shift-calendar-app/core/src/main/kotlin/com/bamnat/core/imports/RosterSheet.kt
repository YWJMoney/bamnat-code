package com.bamnat.core.imports

import java.time.LocalDate
import java.time.YearMonth
import com.bamnat.core.time.Today

/*
 * [IMPORT-03] 불규칙 근무표 CSV·엑셀: wards and convenience stores hand out a roster that follows no cycle, as a
 * spreadsheet. Two shapes are read, from the rows [Sheets.read] gives:
 * - 세로형 (편의점·알바 앱 내보내기): a 날짜 column and a 근무 column, one day a row, maybe a 이름 column.
 * - 가로형 (병동 듀티표): a row of the month's days (1 2 3 … 31 or dates) and one row per person under it, the
 *   person's name on the left.
 * Codes are matched to the calendar's kinds of work with [DutyCodes]; a code that matches nothing stays "?" for the
 * user to pick before anything is written. The file never leaves the phone.
 */

/** One day of a roster as read: the code written and what it means on the calendar. */
data class RosterDay(val date: LocalDate, val raw: String, val cell: DutyCell, val note: String? = null)

/** One person's days. [name] is blank when the file has one person only. [READ-30] [role] is a job written by the name (책임, 주임). */
data class RosterPerson(val name: String, val days: List<RosterDay>, val role: String = "") {
    val unknown: List<String> get() = days.mapNotNull { (it.cell as? DutyCell.Unknown)?.raw }.distinct()
}

enum class RosterLayout(val label: String) {
    LONG("세로형 (날짜·근무 열)"), WIDE("가로형 (날짜 줄 아래 사람마다 한 줄)"),
    /** [READ-30] 날짜가 줄마다, 사람이 열마다 (편의점·알바 단톡 표). */
    COLUMNS("세로형 (날짜 줄마다 사람이 열마다)"),
}

data class RosterRead(val layout: RosterLayout, val people: List<RosterPerson>, val month: YearMonth? = null)

object RosterSheet {
    private val DATE_HEADS = listOf("날짜", "일자", "근무일", "DATE", "DAY")
    private val CODE_HEADS = listOf("근무", "듀티", "코드", "근무유형", "근무형태", "구분", "DUTY", "SHIFT", "CODE")
    private val NAME_HEADS = listOf("이름", "성명", "직원", "사원", "NAME")
    private val NOT_NAME = setOf("근무", "시간", "근무시간", "구분", "비고", "합계", "출근", "퇴근", "듀티", "DUTY", "TIME")
    private val WEEKDAYS = setOf("월", "화", "수", "목", "금", "토", "일", "MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")
    private val MONTH = Regex("""(\d{4})\s*[년.\-/]\s*(\d{1,2})\s*월?""")
    private val DAY = Regex("""^(\d{1,2})\s*일?(?:\s*\(?[월화수목금토일]\)?)?$""")

    /**
     * [IMPORT-03] Reads [rows] either way; [month] is the month a 가로형 roster with only day numbers is for
     * (from the file when it says, e.g. "2026년 11월"), [near] picks the year of dates written without one.
     */
    fun read(rows: List<List<String>>, codes: DutyCodes, month: YearMonth? = null, near: LocalDate = Today.date()): RosterRead? =
        readWide(rows, codes, month, near) ?: readColumns(rows, codes, near) ?: readLong(rows, codes, near)

    /**
     * [READ-30] 1.38: the sheet of a workbook that holds the roster (a cover note or a 합계 sheet may come first), read; the one
     * with the most days read wins, a sheet named after the month the user is on before others when they tie.
     */
    fun readBook(sheets: List<Sheets.Sheet>, codes: DutyCodes, month: YearMonth? = null, near: LocalDate = Today.date()): RosterRead? =
        sheets.mapNotNull { sh -> read(sh.rows, codes, month ?: monthIn(listOf(listOf(sh.name))), near)?.let { sh to it } }
            .maxWithOrNull(compareBy<Pair<Sheets.Sheet, RosterRead>> { (_, r) -> r.people.sumOf { p -> p.days.count { it.cell !is DutyCell.Unknown } } }
                .thenBy { (sh, _) -> if (month != null && monthIn(listOf(listOf(sh.name))) == month) 1 else 0 })?.second

    /**
     * [READ-30] The words by a name that are a job, not the name ("김민지(책임)", "이수진 주임", "박하늘RN"): the name and the job.
     */
    fun nameAndRole(raw: String, codes: DutyCodes): Pair<String, String> {
        val roles = mutableListOf<String>()
        var t = raw.trim()
        Regex("""[(\[]([^)\]]*)[)\]]""").findAll(t).forEach { m -> m.groupValues[1].trim().takeIf { it.isNotEmpty() }?.let(roles::add) }
        t = t.replace(Regex("""[(\[][^)\]]*[)\]]"""), " ").replace('★', ' ').replace(Regex("""\s+"""), " ").trim()
        val words = t.split(" ").filter { it.isNotEmpty() }
        val name = words.filterIndexed { i, w -> i == 0 || !isRole(w) }.toMutableList()
        roles += words.drop(1).filter(::isRole)
        if (name.size == 1) codes.splitName(name[0])?.let { (n, tail) -> if (tail == null) roles += name[0].removePrefix(n).trim(); name[0] = n }
        return name.joinToString(" ") to roles.joinToString(" ")
    }

    private fun isRole(w: String): Boolean = DutyCodes.norm(w) in DutyCodes.RANKS || w in com.bamnat.core.team.TeamPaste.ROLES || w.uppercase() in com.bamnat.core.team.TeamPaste.ROLES

    /**
     * [READ-30] The dates of a row of day numbers in the order they stand. Days that go down (… 30 31 1 2 …) carry on into
     * the next month: a 병동 4주 듀티 runs 10/26 ~ 11/25. The month the roster is "for" ([ym], from its title) is the one most
     * of its days are in, so 26~31 then 1~25 under "11월" is 10/26 ~ 11/25.
     */
    internal fun spanDates(days: List<Int>, ym: YearMonth): List<LocalDate?> {
        if (days.isEmpty()) return emptyList()
        val wraps = days.zipWithNext().withIndex().filter { (_, p) -> p.second < p.first && p.first - p.second >= 20 }.map { it.index + 1 }
        if (wraps.isEmpty()) return days.map { d -> d.takeIf { it in 1..ym.lengthOfMonth() }?.let(ym::atDay) }
        // Pieces: before the first wrap, between, after. The longest piece is [ym].
        val cuts = listOf(0) + wraps + days.size
        val pieces = cuts.zipWithNext()
        val longest = pieces.indices.maxBy { pieces[it].second - pieces[it].first }
        val out = arrayOfNulls<LocalDate>(days.size)
        pieces.forEachIndexed { k, (a, b) ->
            val m = ym.plusMonths((k - longest).toLong())
            for (i in a until b) out[i] = days[i].takeIf { it in 1..m.lengthOfMonth() }?.let(m::atDay)
        }
        return out.toList()
    }

    /**
     * [READ-30] A cell that reads as a roster code even when the calendar does not know it: short (4 letters at most), not a
     * time ("07-15", "9:00") nor a number, and written at least three times in the sheet (N1, 교, 휴가 …). Lets a line of a
     * ward's own codes count as a person's line; the codes stay "?" for the user to name once.
     */
    private fun codeLike(rows: List<List<String>>): (String) -> Boolean {
        val counts = rows.flatten().map { it.trim() }.filter { it.isNotEmpty() }.groupingBy { it }.eachCount()
        return { t ->
            t.length <= 4 && (counts[t] ?: 0) >= 3 && !t.all { it.isDigit() } && !t.contains(':') && !Regex("""\d+\s*[-~]\s*\d+""").containsMatchIn(t) &&
                DutyCodes.norm(t).removeSuffix("요일") !in WEEKDAYS
        }
    }

    /** The month a file names in its first rows, e.g. "2026년 11월 근무표". */
    fun monthIn(rows: List<List<String>>): YearMonth? = rows.take(6).flatten().firstNotNullOfOrNull { c ->
        MONTH.find(c)?.let { m -> runCatching { YearMonth.of(m.groupValues[1].toInt(), m.groupValues[2].toInt()) }.getOrNull() }
    }

    private fun readWide(rows: List<List<String>>, codes: DutyCodes, month: YearMonth?, near: LocalDate): RosterRead? {
        val ym = month ?: monthIn(rows) ?: YearMonth.from(near)
        // A day number ("3", "3일", "3(화)") or a full date in the header ("11/3", "2026-11-03"), not a lone month name.
        fun numberOf(cell: String): Int? = DAY.matchEntire(cell.trim())?.groupValues?.get(1)?.toInt()?.takeIf { it in 1..31 }
        fun fullDate(cell: String): LocalDate? {
            val t = cell.trim()
            if (t.isEmpty() || numberOf(t) != null) return null
            return if (t.any { it == '/' || it == '-' || it == '.' } || t.length >= 8) AttendanceImport.parseDate(t, ym.year, near) else null
        }
        // [READ-30] The row of days, each cell its date: day numbers in a row may cross into the next month (spanDates).
        fun datesOf(r: List<String>): Map<Int, LocalDate> {
            val numbered = r.mapIndexedNotNull { c, cell -> numberOf(cell)?.let { c to it } }.toMutableList()
            // A day number left out of the row (an empty cell between 2 and 4, a merged cell's lost half) is that day.
            numbered.zipWithNext().filter { (a, b) -> b.first - a.first == 2 && b.second - a.second == 2 && r.getOrNull(a.first + 1).isNullOrBlank() }
                .forEach { (a, _) -> numbered += (a.first + 1) to (a.second + 1) }
            numbered.sortBy { it.first }
            val full = r.mapIndexedNotNull { c, cell -> fullDate(cell)?.let { c to it } }
            if (full.size >= numbered.size) return full.toMap()
            return numbered.map { it.first }.zip(spanDates(numbered.map { it.second }, ym)).mapNotNull { (c, d) -> d?.let { c to it } }.toMap()
        }
        val header = rows.withIndex().map { (i, r) -> i to datesOf(r) }
            // A merged title filled across its cells repeats one date: a row of days has different ones.
            .filter { it.second.values.distinct().size >= DutyRoster.MIN_DAYS }
            .maxByOrNull { it.second.values.distinct().size } ?: return null
        val columns = header.second
        val firstDayCol = columns.keys.min()
        val isCode = codeLike(rows.drop(header.first + 1).map { r -> columns.keys.mapNotNull { r.getOrNull(it) } })
        val people = rows.drop(header.first + 1).mapNotNull { r ->
            // [ROST-04] A job title in its own column or after the name (김민지RN) is not part of the name.
            // [READ-30] "근무"·"시간" in a 구분 column (one person over two rows) is not part of the name.
            val left = r.take(firstDayCol).map { it.trim() }.filter { it.isNotEmpty() && it.none(Char::isDigit) && DutyCodes.norm(it) !in NOT_NAME }
            val named = left.filter { !isRole(it) }.map { nameAndRole(it, codes) }
            val name = named.map { it.first }.filter { it.isNotEmpty() }.distinct().joinToString(" ")
            val role = (left.filter(::isRole) + named.map { it.second }).filter { it.isNotEmpty() }.distinct().joinToString(" ")
            val days = columns.mapNotNull { (c, date) ->
                val raw = r.getOrNull(c)?.trim().orEmpty()
                // [ROST-03] "D*", "N-O", "11D" as on the photo: the code, and the rest kept as the day's note.
                val read = codes.read(raw)
                if (raw.isEmpty()) null else RosterDay(date, raw, read?.first ?: DutyCell.Unknown(raw), read?.second)
            }.distinctBy { it.date }.sortedBy { it.date }
            // A row of weekdays, hours or totals is no one's duties; a line of the ward's own codes is.
            val known = days.count { it.cell !is DutyCell.Unknown || isCode(it.raw) }
            if (days.size < DutyRoster.MIN_CELLS || known * 2 < days.size) null else RosterPerson(name, days, role)
        }
        if (people.isEmpty()) return null
        return RosterRead(RosterLayout.WIDE, people, mostDays(columns.values))
    }

    /** The month most of [dates] are in. */
    private fun mostDays(dates: Collection<LocalDate>): YearMonth = dates.groupingBy { YearMonth.from(it) }.eachCount().maxBy { it.value }.key

    /**
     * [READ-30] 날짜가 줄마다, 사람이 열마다: a header row naming people over columns of codes and a column of dates. Each named column
     * whose cells are mostly codes is a person.
     */
    private fun readColumns(rows: List<List<String>>, codes: DutyCodes, near: LocalDate): RosterRead? {
        val width = rows.maxOfOrNull { it.size } ?: return null
        val dateCol = (0 until width).maxByOrNull { c -> rows.count { AttendanceImport.parseDate(it.getOrNull(c).orEmpty(), near.year, near) != null } } ?: return null
        val dated = rows.withIndex().filter { (_, r) -> AttendanceImport.parseDate(r.getOrNull(dateCol).orEmpty(), near.year, near) != null }
        if (dated.size < DutyRoster.MIN_CELLS) return null
        val headAt = dated.first().index - 1
        if (headAt < 0) return null
        val head = rows[headAt]
        val isCode = codeLike(dated.map { it.value })
        val people = (0 until width).filter { it != dateCol }.mapNotNull { c ->
            val label = head.getOrNull(c)?.trim().orEmpty()
            if (label.isEmpty() || label.any(Char::isDigit) || DutyCodes.norm(label) in CODE_HEADS || DutyCodes.norm(label) in DATE_HEADS || label in listOf("요일", "비고", "메모")) return@mapNotNull null
            val days = dated.mapNotNull { (_, r) ->
                val raw = r.getOrNull(c)?.trim().orEmpty()
                val date = AttendanceImport.parseDate(r[dateCol], near.year, near) ?: return@mapNotNull null
                if (raw.isEmpty()) null else codes.read(raw).let { read -> RosterDay(date, raw, read?.first ?: DutyCell.Unknown(raw), read?.second) }
            }.distinctBy { it.date }.sortedBy { it.date }
            val known = days.count { it.cell !is DutyCell.Unknown || isCode(it.raw) }
            if (days.size < DutyRoster.MIN_CELLS || known * 10 < days.size * 7) return@mapNotNull null
            val (name, role) = nameAndRole(label, codes)
            RosterPerson(name, days, role)
        }
        if (people.size < 2) return null
        return RosterRead(RosterLayout.COLUMNS, people, mostDays(people.flatMap { p -> p.days.map { it.date } }))
    }

    private fun readLong(rows: List<List<String>>, codes: DutyCodes, near: LocalDate): RosterRead? {
        fun norm(s: String) = s.replace(Regex("""[\s_()\[\]./·-]"""), "").uppercase()
        val headerAt = rows.take(10).indexOfFirst { r -> r.any { norm(it) in DATE_HEADS } && r.any { c -> CODE_HEADS.any { norm(c) == it || norm(c).startsWith(it) } } }
        val (dateCol, codeCol, nameCol, start) = if (headerAt >= 0) {
            val h = rows[headerAt].map(::norm)
            listOf(h.indexOfFirst { it in DATE_HEADS }, h.indexOfFirst { c -> CODE_HEADS.any { c == it || c.startsWith(it) } },
                h.indexOfFirst { it in NAME_HEADS }, headerAt + 1)
        } else {
            // No header: the column of dates, and the next one with codes.
            val width = rows.maxOfOrNull { it.size } ?: return null
            val d = (0 until width).maxByOrNull { c -> rows.count { AttendanceImport.parseDate(it.getOrNull(c).orEmpty(), near.year, near) != null } } ?: return null
            val c = (0 until width).filter { it != d }.maxByOrNull { c -> rows.count { r -> r.getOrNull(c)?.let { codes.match(it) } != null } } ?: return null
            listOf(d, c, -1, 0)
        }
        if (dateCol < 0 || codeCol < 0) return null
        val read = rows.drop(start).mapNotNull { r ->
            val date = AttendanceImport.parseDate(r.getOrNull(dateCol).orEmpty(), near.year, near) ?: return@mapNotNull null
            val raw = r.getOrNull(codeCol)?.trim().orEmpty()
            if (raw.isEmpty()) return@mapNotNull null
            val name = if (nameCol >= 0) r.getOrNull(nameCol)?.trim().orEmpty() else ""
            val read = codes.read(raw)
            name to RosterDay(date, raw, read?.first ?: DutyCell.Unknown(raw), read?.second)
        }
        if (read.size < DutyRoster.MIN_CELLS) return null
        val people = read.groupBy({ it.first }, { it.second }).map { (n, d) -> RosterPerson(n, d.distinctBy { it.date }.sortedBy { it.date }) }
        return RosterRead(RosterLayout.LONG, people)
    }

    /** [IMPORT-03] [person]'s days with each unknown code given the meaning the user picked in [picked]. */
    fun resolve(person: RosterPerson, picked: Map<String, DutyCell>): Map<LocalDate, DutyCell> =
        person.days.associate { d -> d.date to ((d.cell as? DutyCell.Unknown)?.let { picked[it.raw] } ?: d.cell) }
            .filterValues { it !is DutyCell.Unknown }
}
