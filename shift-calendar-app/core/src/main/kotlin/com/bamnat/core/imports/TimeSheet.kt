package com.bamnat.core.imports

import com.bamnat.core.time.Today
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs

/**
 * 2.0.0 [RVL-72] (검토 T07b-01·T07b-02·I07bb-02) The second way of reading a roster, beside [RosterSheet] and [DutyRoster]: a sheet
 * whose cells are **times** (알바 시간표: "09:00-15:00", "9-3", "6h") or **공수** (현장 공수표: 1, 0.5, 1.5), not codes. Those cells were
 * dropped as "no code" and the screen said it read nothing; 알바 and 현장 get their sheets this way. The same shapes are read as for
 * codes:
 * - 가로형: a row of days (1 2 3 … or dates) and one line a person under it; a 출근 line and a 퇴근 line under it are one person.
 * - 요일표 (주간 시간표): a row of 월 화 수 … and the times under it, every such weekday of the month ([RosterRow.weekly]).
 * - 세로형: one day a row (a date or a weekday, and a time range, or 출근·퇴근 columns, or 공수), maybe a name column.
 * Nothing leaves the phone; the user sees the month before anything is written.
 */
object TimeSheet {
    // ---- one cell ----

    private val RANGE = Regex("""^(\d{1,2})(?:\s*[:시]\s*(\d{2})?\s*분?)?\s*[-~–—]+\s*(\d{1,2})(?:\s*[:시]\s*(\d{2})?\s*분?)?$""")
    private val FOUR = Regex("""^(\d{2})(\d{2})\s*[-~–—]+\s*(\d{2})(\d{2})$""")
    private val LENGTH = Regex("""^(\d{1,2}(?:[.,]\d{1,2})?)\s*(?:h|H|hr|HR|시간)$""")
    private val UNITS = Regex("""^(\d(?:[.,]\d{1,3})?)\s*(공수|공)?$""")
    private val OFF = setOf("휴", "휴무", "휴일", "쉼", "쉬는날", "OFF", "오프", "휴무일")

    /**
     * A cell as a time ([DutyCell.Hours]), 공수 ([DutyCell.Units], a bare number only when [units]) or a day off; null when it is none
     * of these (a code, a name, blank).
     */
    fun cell(raw: String, units: Boolean = false): DutyCell? {
        val t = raw.trim().replace('：', ':').replace(Regex("""\s+"""), " ")
        if (t.isEmpty()) return null
        if (t.uppercase().replace(" ", "") in OFF) return DutyCell.Off
        range(t)?.let { return it }
        LENGTH.matchEntire(t)?.let { m ->
            val h = m.groupValues[1].replace(',', '.').toDouble()
            return if (h > 0 && h <= MAX_HOURS) DutyCell.Hours(null, Math.round(h * 60).toInt()) else null
        }
        if (t == "반공" || t == "반") return if (units) DutyCell.Units(0.5) else null
        UNITS.matchEntire(t)?.let { m ->
            // "1공" says itself; a bare "1" is 공수 only on a 공수표.
            if (!units && m.groupValues[2].isEmpty()) return null
            val u = m.groupValues[1].replace(',', '.').toDouble()
            return if (u > 0 && u <= MAX_UNITS) DutyCell.Units(Math.round(u * 1000) / 1000.0) else null
        }
        return null
    }

    /** "09:00-15:00", "9:30~18", "9-3" (오후 3시), "22-6" (밤새), "0900-1500", "9시-15시". */
    fun range(t: String): DutyCell.Hours? {
        val s = t.replace(" ", "")
        val m = RANGE.matchEntire(s)
        val parts = if (m != null) {
            val g = m.groupValues
            listOf(g[1].toInt(), g[2].toIntOrNull(), g[3].toInt(), g[4].toIntOrNull())
        } else FOUR.matchEntire(s)?.groupValues?.let { g -> listOf(g[1].toInt(), g[2].toInt(), g[3].toInt(), g[4].toInt()) } ?: return null
        val sh = parts[0]!!; val sm = parts[1] ?: 0; var eh = parts[2]!!; val em = parts[3] ?: 0
        if (sh > 24 || eh > 24 || sm > 59 || em > 59) return null
        // "9-3" written in hours only is 9시 to 오후 3시; "22-6" goes past midnight.
        val bare = m != null && parts[1] == null && parts[3] == null && !s.contains(':')
        if (bare && eh <= sh && eh + 12 > sh && eh <= 12) eh += 12
        val start = (sh * 60 + sm) % MINUTES_A_DAY
        var minutes = (eh * 60 + em) - (sh * 60 + sm)
        if (minutes <= 0) minutes += MINUTES_A_DAY
        return if (minutes in 1..MAX_HOURS * 60) DutyCell.Hours(start, minutes) else null
    }

    private fun timed(c: DutyCell?) = c is DutyCell.Hours || c is DutyCell.Units

    // ---- dates, days and weekdays ----

    private val DAY = Regex("""^(\d{1,2})\s*일?(?:\s*\(?[월화수목금토일]\)?)?$""")
    private val WEEKDAY = mapOf(
        "월" to DayOfWeek.MONDAY, "화" to DayOfWeek.TUESDAY, "수" to DayOfWeek.WEDNESDAY, "목" to DayOfWeek.THURSDAY,
        "금" to DayOfWeek.FRIDAY, "토" to DayOfWeek.SATURDAY, "일" to DayOfWeek.SUNDAY,
        "MON" to DayOfWeek.MONDAY, "TUE" to DayOfWeek.TUESDAY, "WED" to DayOfWeek.WEDNESDAY, "THU" to DayOfWeek.THURSDAY,
        "FRI" to DayOfWeek.FRIDAY, "SAT" to DayOfWeek.SATURDAY, "SUN" to DayOfWeek.SUNDAY,
    )

    private fun dayNumber(t: String): Int? = DAY.matchEntire(t.trim())?.groupValues?.get(1)?.toInt()?.takeIf { it in 1..31 }

    fun weekday(t: String): DayOfWeek? {
        val s = t.trim().uppercase().removeSuffix("요일").removeSuffix("DAY").removePrefix("(").removeSuffix(")")
        return WEEKDAY[s] ?: WEEKDAY[s.take(3)].takeIf { s.length in 3..9 && s.all(Char::isLetter) }
    }

    /** A written date ("11/3", "2026-11-03", "11월 3일"); never "9-3", which is a time on these sheets. */
    private fun date(t: String, ym: YearMonth, near: LocalDate): LocalDate? {
        val s = t.trim()
        if (s.isEmpty() || dayNumber(s) != null || range(s) != null) return null
        if (Regex("""^\d{1,2}\s*-\s*\d{1,2}$""").matches(s)) return null
        if (!(s.any { it == '/' || it == '-' || it == '.' || it == '월' } || s.length >= 8)) return null
        return AttendanceImport.parseDate(s, ym.year, near, ahead = true)
    }

    // ---- sheets (files and pasted text) ----

    private val NAME_HEADS = listOf("이름", "성명", "직원", "사원", "알바", "NAME")
    private val NOT_NAME = setOf("시간", "근무시간", "근무", "구분", "비고", "합계", "계", "공수", "출근", "퇴근", "출근시간", "퇴근시간", "시작", "종료", "TIME")
    private val IN_WORDS = listOf("출근", "시작", "IN", "START")
    private val OUT_WORDS = listOf("퇴근", "종료", "끝", "OUT", "END")

    /**
     * The lines of [rows] read as times or 공수, each with its days in the month most of them are in; [month] is the month a sheet of day
     * numbers or weekdays is for (its title, else the one on screen). Null when it holds no such line.
     */
    fun read(rows: List<List<String>>, month: YearMonth? = null, near: LocalDate = Today.date()): Read? {
        val clean = rows.map { r -> r.map { it.trim() } }
        val ym = RosterSheet.monthIn(clean, near) ?: month ?: YearMonth.from(near)
        return wide(clean, ym, near) ?: long(clean, ym, near)
    }

    /** Pasted text: each line split where it has spaces, tabs or commas (a range written "09:00 - 15:00" kept whole). */
    fun readText(text: String, month: YearMonth? = null, near: LocalDate = Today.date()): Read? {
        val rows = text.lines().map { line ->
            line.replace(Regex("""(\d)\s*([-~–])\s*(\d)"""), "$1$2$3").split(Regex("""[\t,|]+|\s{2,}|\s(?=\S)""")).map { it.trim() }.filter { it.isNotEmpty() }
        }.filter { it.isNotEmpty() }
        return read(rows, month, near)
    }

    /** What was read: the lines, and the month their days are in (null for a 요일표, which fits any month). */
    data class Read(val rows: List<RosterRow>, val month: YearMonth?)

    /** One line as read: its name, its days by date or by weekday, and where it stood. */
    private data class Line(val name: String, val dates: Map<LocalDate, DutyCell>, val weekly: Map<DayOfWeek, DutyCell>, val at: Int)

    private data class Header(val row: Int, val dates: Map<Int, LocalDate>, val weekdays: Map<Int, DayOfWeek>)

    private fun minutes(a: java.time.LocalTime, b: java.time.LocalTime): DutyCell.Hours {
        val from = a.hour * 60 + a.minute
        var mins = b.hour * 60 + b.minute - from
        if (mins <= 0) mins += MINUTES_A_DAY
        return DutyCell.Hours(from, mins)
    }

    private fun isIn(tag: String) = IN_WORDS.any { tag.contains(it) } && OUT_WORDS.none { tag.contains(it) }
    private fun isOut(tag: String) = OUT_WORDS.any { tag.contains(it) }

    private fun wide(rows: List<List<String>>, ym: YearMonth, near: LocalDate): Read? {
        // A row of day numbers or dates, or of weekdays.
        fun datesOf(r: List<String>): Map<Int, LocalDate> {
            val full = r.mapIndexedNotNull { c, cell -> date(cell, ym, near)?.let { c to it } }
            val numbered = r.mapIndexedNotNull { c, cell -> dayNumber(cell)?.let { c to it } }
            if (full.size >= numbered.size) return full.toMap()
            // Day numbers go up (and may cross into the next month): a row of 공수 (1 1 0.5 1) does not.
            if (!goesUp(numbered.map { it.second })) return emptyMap()
            return numbered.map { it.first }.zip(RosterSheet.spanDates(numbered.map { it.second }, ym)).mapNotNull { (c, d) -> d?.let { c to it } }.toMap()
        }
        fun weekdaysOf(r: List<String>): Map<Int, DayOfWeek> = r.mapIndexedNotNull { c, cell -> weekday(cell)?.let { c to it } }.toMap()
        val headers = rows.indices.mapNotNull { i ->
            val d = datesOf(rows[i])
            if (d.values.distinct().size >= DutyRoster.MIN_DAYS) Header(i, d, emptyMap())
            else weekdaysOf(rows[i]).takeIf { w -> w.values.distinct().size >= 5 }?.let { Header(i, emptyMap(), it) }
        }
        if (headers.isEmpty()) return null
        val read = mutableListOf<Line>()
        headers.forEachIndexed { k, h ->
            val end = headers.getOrNull(k + 1)?.row ?: rows.size
            val columns = h.dates.keys.ifEmpty { h.weekdays.keys }
            val first = columns.min()
            fun nameOf(r: List<String>) = r.take(first).filter { it.isNotEmpty() && it.none(Char::isDigit) }
            fun keyed(cells: Map<Int, DutyCell>, at: Int, name: String) = if (h.weekdays.isNotEmpty())
                Line(name, emptyMap(), cells.mapKeys { (c, _) -> h.weekdays.getValue(c) }, at)
                else Line(name, cells.mapKeys { (c, _) -> h.dates.getValue(c) }, emptyMap(), at)
            var i = h.row + 1
            while (i < end) {
                val r = rows[i]
                val left = nameOf(r)
                val tag = left.joinToString(" ").uppercase()
                val name = left.flatMap { it.split(' ') }.filter { w -> w.isNotEmpty() && w.uppercase() !in NOT_NAME && IN_WORDS.none { w.uppercase() == it } && OUT_WORDS.none { w.uppercase() == it } }.joinToString(" ")
                val next = rows.getOrNull(i + 1)?.takeIf { i + 1 < end }
                // A 출근 line and the 퇴근 line under it are one person's times.
                if (isIn(tag) && next != null && isOut(nameOf(next).joinToString(" ").uppercase())) {
                    val cells = columns.mapNotNull { c ->
                        val a = r.getOrNull(c)?.let(AttendanceImport::parseTime) ?: return@mapNotNull null
                        val b = next.getOrNull(c)?.let(AttendanceImport::parseTime) ?: return@mapNotNull null
                        c to minutes(a, b)
                    }.toMap()
                    if (cells.size >= MIN_TIMED) read += keyed(cells, i, name)
                    i += 2
                    continue
                }
                // A line whose numbers go up 1 2 3 … is another row of days, not 공수.
                val units = datesOf(r).size < DutyRoster.MIN_DAYS
                val cells = columns.mapNotNull { c -> cell(r.getOrNull(c).orEmpty(), units)?.let { c to it } }.toMap()
                if (cells.values.count(::timed) >= MIN_TIMED) read += keyed(cells, i, name)
                i++
            }
        }
        return finish(read)
    }

    private fun goesUp(n: List<Int>): Boolean = n.size >= 2 && n.zipWithNext().count { (a, b) -> b > a || a - b >= 20 } + 1 >= n.size * 0.8

    private fun long(rows: List<List<String>>, ym: YearMonth, near: LocalDate): Read? {
        // Columns named in a heading row, when there is one.
        val head = rows.take(5).indexOfFirst { r -> r.any { c -> c.uppercase().replace(" ", "") in listOf("날짜", "일자", "근무일", "요일", "DATE", "DAY") } }
        val heads = rows.getOrNull(head).orEmpty().map { it.uppercase().replace(" ", "") }
        fun col(words: List<String>) = heads.indexOfFirst { h -> words.any { h.contains(it) } }.takeIf { it >= 0 }
        val nameCol = col(NAME_HEADS)
        val inCol = col(IN_WORDS)
        val outCol = col(OUT_WORDS)
        val unitsSheet = heads.any { it.contains("공수") }
        val dates = linkedMapOf<String, MutableMap<LocalDate, DutyCell>>()
        val weekly = linkedMapOf<String, MutableMap<DayOfWeek, DutyCell>>()
        val firstAt = mutableMapOf<String, Int>()
        rows.forEachIndexed { i, r ->
            if (i == head) return@forEachIndexed
            val dateAt = r.indexOfFirst { date(it, ym, near) != null || (head >= 0 && dayNumber(it) != null) }
            val day = r.getOrNull(dateAt)?.let { date(it, ym, near) ?: dayNumber(it)?.takeIf { d -> d <= ym.lengthOfMonth() }?.let(ym::atDay) }
            val wdAt = if (day == null) r.indexOfFirst { weekday(it) != null } else -1
            val wd = r.getOrNull(wdAt)?.let(::weekday)
            if (day == null && wd == null) return@forEachIndexed
            val skip = setOfNotNull(dateAt.takeIf { it >= 0 }, wdAt.takeIf { it >= 0 }, nameCol)
            val a = inCol?.let { r.getOrNull(it) }?.let(AttendanceImport::parseTime)
            val b = outCol?.let { r.getOrNull(it) }?.let(AttendanceImport::parseTime)
            val value: DutyCell = if (a != null && b != null) minutes(a, b)
                else r.withIndex().firstNotNullOfOrNull { (c, v) -> if (c in skip) null else cell(v, unitsSheet) } ?: return@forEachIndexed
            val name = nameCol?.let { r.getOrNull(it) }.orEmpty().takeIf { it.none(Char::isDigit) }.orEmpty()
            firstAt.putIfAbsent(name, i)
            if (day != null) dates.getOrPut(name) { linkedMapOf() }[day] = value else weekly.getOrPut(name) { linkedMapOf() }[wd!!] = value
        }
        val names = (dates.keys + weekly.keys).distinct()
        return finish(names.map { n -> Line(n, dates[n].orEmpty(), weekly[n].orEmpty(), firstAt[n] ?: 0) })
    }

    /**
     * The lines with at least [MIN_TIMED] times or 공수, as [RosterRow]s of the month most of their days are in (the other days as
     * dates, [RosterRow.outside]); a 요일표's days stay by weekday ([RosterRow.weekly]). Lines of one name in two blocks are one.
     */
    private fun finish(lines: List<Line>): Read? {
        val joined = lines.groupBy { it.name.ifBlank { "#${it.at}" } }.values.map { same ->
            same.reduce { a, b -> a.copy(dates = a.dates + b.dates, weekly = a.weekly + b.weekly) }
        }.filter { (it.dates.values + it.weekly.values).count(::timed) >= MIN_TIMED }.sortedBy { it.at }
        if (joined.isEmpty()) return null
        val month = joined.flatMap { it.dates.keys }.groupingBy { YearMonth.from(it) }.eachCount().maxByOrNull { it.value }?.key
        val rows = joined.map { l ->
            val inMonth = l.dates.filterKeys { YearMonth.from(it) == month }
            RosterRow(l.name, inMonth.mapKeys { it.key.dayOfMonth }, line = l.at, outside = l.dates - inMonth.keys, weekly = l.weekly)
        }
        return Read(rows, month)
    }

    // ---- a photo's words ----

    /**
     * A photo's words read as a time or 공수 sheet: words put in lines, a range broken into pieces ("09:00", "-", "15:00") joined, each
     * cell placed under the day (or weekday) whose column it stands in. A photo of 세로형 (one day a line) is read as its lines.
     */
    fun readWords(words: List<RosterWord>, month: YearMonth? = null, near: LocalDate = Today.date()): Read? {
        if (words.isEmpty()) return null
        val lines = lines(words)
        val text = lines.joinToString("\n") { l -> l.joinToString(" ") { it.text } }
        val ym = DutyRoster.monthIn(text, near) ?: month ?: YearMonth.from(near)
        placed(lines, ym, near)?.let { return it }
        return read(lines.map { l -> l.map { it.text } }, ym, near)
    }

    private data class Piece(val text: String, val x: Int)

    /** The words in lines, top to bottom, each line left to right with the pieces of a range joined. */
    private fun lines(words: List<RosterWord>): List<List<Piece>> {
        val h = words.map { it.height }.sorted()[words.size / 2].coerceAtLeast(1)
        val out = mutableListOf<MutableList<RosterWord>>()
        for (w in words.sortedBy { it.centerY }) {
            val line = out.lastOrNull()
            if (line != null && abs(line.map { it.centerY }.average() - w.centerY) < h * 0.6) line += w else out += mutableListOf(w)
        }
        return out.map { l ->
            val ws = l.sortedBy { it.left }
            val pieces = mutableListOf<Piece>()
            var i = 0
            while (i < ws.size) {
                // "09:00" "-" "15:00" or "09:00-" "15:00": one range when they stand close.
                val joined = (3 downTo 2).firstNotNullOfOrNull { n ->
                    if (i + n > ws.size) return@firstNotNullOfOrNull null
                    val part = ws.subList(i, i + n)
                    val close = part.zipWithNext().all { (a, b) -> b.left - a.right < h * 1.5 }
                    val t = part.joinToString("") { it.text }
                    if (close && range(t) != null) Piece(t, (part.first().left + part.last().right) / 2) to n else null
                }
                if (joined != null) { pieces += joined.first; i += joined.second } else { pieces += Piece(ws[i].text, ws[i].centerX); i++ }
            }
            pieces
        }
    }

    /** A 가로형 photo: the line of days (or weekdays), and each line under it by the columns its pieces stand in. */
    private fun placed(lines: List<List<Piece>>, ym: YearMonth, near: LocalDate): Read? {
        val head = lines.indexOfFirst { l -> l.mapNotNull { dayNumber(it.text) }.let { n -> n.size >= DutyRoster.MIN_DAYS && goesUp(n) } }.takeIf { it >= 0 } ?: lines.indexOfFirst { l -> l.mapNotNull { weekday(it.text) }.distinct().size >= 5 }.takeIf { it >= 0 } ?: return null
        val headLine = lines[head]
        val numbered = headLine.mapNotNull { p -> dayNumber(p.text)?.let { p.x to it } }
        val columns: List<Pair<Int, Any>> = if (numbered.size >= DutyRoster.MIN_DAYS) {
            numbered.map { it.first }.zip(RosterSheet.spanDates(numbered.map { it.second }, ym)).mapNotNull { (x, d) -> d?.let { x to it } }
        } else headLine.mapNotNull { p -> weekday(p.text)?.let { p.x to it } }
        if (columns.size < 5) return null
        val gap = columns.map { it.first }.sorted().zipWithNext { a, b -> b - a }.sorted().let { it[it.size / 2] }.coerceAtLeast(1)
        val firstX = columns.minOf { it.first }
        val read = mutableListOf<Line>()
        lines.drop(head + 1).forEachIndexed { j, l ->
            val name = l.filter { it.x < firstX - gap / 2 && it.text.none(Char::isDigit) }.joinToString(" ") { it.text }
                .takeIf { it.uppercase() !in NOT_NAME }.orEmpty()
            val units = !goesUp(l.mapNotNull { dayNumber(it.text) }.takeIf { it.size >= DutyRoster.MIN_DAYS } ?: emptyList())
            val dates = mutableMapOf<LocalDate, DutyCell>()
            val weekly = mutableMapOf<DayOfWeek, DutyCell>()
            for (p in l) {
                if (p.x < firstX - gap / 2) continue
                val (x, key) = columns.minBy { abs(it.first - p.x) }
                if (abs(x - p.x) > gap * 0.6) continue
                val c = cell(p.text, units) ?: continue
                if (key is DayOfWeek) weekly[key] = c else dates[key as LocalDate] = c
            }
            read += Line(name, dates, weekly, head + 1 + j)
        }
        return finish(read)
    }

    /** 2.0.0 [RVL-72] A 요일표's weekdays as days of [month]. */
    fun expand(weekly: Map<DayOfWeek, DutyCell>, month: YearMonth): Map<Int, DutyCell> =
        (1..month.lengthOfMonth()).mapNotNull { d -> weekly[month.atDay(d).dayOfWeek]?.let { d to it } }.toMap()

    /** "9–15", "9:30–18", "6시간", "1.5공": a cell as the month and the list show it. */
    fun short(c: DutyCell): String? = when (c) {
        is DutyCell.Hours -> c.start?.let { s -> "${clock(s)}–${clock(s + c.minutes)}" } ?: "${hours(c.minutes)}시간"
        is DutyCell.Units -> com.bamnat.core.money.DayRate.label(c.units)
        else -> null
    }

    private fun clock(m: Int): String {
        val h = (m / 60) % 24
        val mm = m % 60
        return if (mm == 0) "$h" else "$h:%02d".format(mm)
    }

    private fun hours(m: Int): String = if (m % 60 == 0) "${m / 60}" else "%.1f".format(m / 60.0).trimEnd('0').trimEnd('.')

    /** A line of at least this many times or 공수 is someone's. */
    const val MIN_TIMED = 2
    const val MAX_HOURS = 16
    const val MAX_UNITS = 3.0
    private const val MINUTES_A_DAY = 24 * 60
}
