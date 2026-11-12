package com.bamnat.core.imports

import com.bamnat.core.boss.StaffTax
import com.bamnat.core.boss.WageForm
import com.bamnat.core.time.Today
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

/*
 * [READ-31] 1.38 (사용자 2026-11-05 "사장님·관리자들이 종이 사진이나 엑셀표·PDF 등 어떠한 형식으로든 제공하더라도 완벽하게 인식을 해서
 * 틀에 관계없이 직원이나 동료·후배들을 관리할 수 있게"): what a 리더 (팀 근무표, P3 M24) or a 사장님 (급여 대장, M25) gets out of any
 * file or photo — the whole team's roster with each code as written, and a staff list — for the 관리 화면 to match names,
 * pair unknown codes once and check the doubtful cells first. Everything is read on the phone; the file stays there.
 */

/** One person's line: the code written on each date as it was written ("D", "휴", "N1"), and the dates to look at first. */
data class GridRow(val name: String, val role: String?, val cells: Map<LocalDate, String>, val doubtful: Set<LocalDate> = emptySet())

/**
 * A team's roster read from a file or a photo. [days] are the roster's dates in order (a 4주 듀티 crosses into the next month);
 * [month] is the month most of them are in; [codes] every code written, most used first.
 */
data class RosterGrid(val month: YearMonth, val days: List<LocalDate>, val rows: List<GridRow>, val codes: List<String>) {
    val doubtfulCount: Int get() = rows.sumOf { it.doubtful.size }
}

/** [GridRow] as the calendar's [RosterRow] for [month] (days in it only), its codes matched by [codes]. */
fun GridRow.toRosterRow(month: YearMonth, codes: DutyCodes): RosterRow {
    val inMonth = cells.filterKeys { YearMonth.from(it) == month }
    val read = inMonth.mapKeys { it.key.dayOfMonth }.mapValues { (_, raw) -> codes.read(raw) }
    return RosterRow(
        name, read.mapValues { (d, r) -> r?.first ?: DutyCell.Unknown(inMonth.getValue(month.atDay(d))) }.toSortedMap(),
        notes = read.mapNotNull { (d, r) -> r?.second?.let { d to it } }.toMap(),
        unsure = doubtful.filter { YearMonth.from(it) == month }.map { it.dayOfMonth }.toSet(),
        raw = inMonth.mapKeys { it.key.dayOfMonth },
    )
}

object TeamSheet {
    /** The common codes only (D/E/N/O, 주·야·비·휴, 연차 …): a team's file is read before its own kinds are known. */
    private val common = DutyCodes(emptyList())

    /** [READ-31] The team roster in a workbook (any of its sheets) or a CSV. */
    @JvmName("rosterOfBook")
    fun roster(sheets: List<Sheets.Sheet>, month: YearMonth? = null, near: LocalDate = Today.date()): RosterGrid? =
        RosterSheet.readBook(sheets, common, month, near)?.let(::grid)

    fun roster(rows: List<List<String>>, month: YearMonth? = null, near: LocalDate = Today.date()): RosterGrid? = roster(listOf(Sheets.Sheet("", rows)), month, near)

    fun grid(read: RosterRead): RosterGrid {
        val rows = read.people.mapIndexed { i, p ->
            GridRow(p.name.ifBlank { "이름 없음 ${i + 1}" }, p.role.ifBlank { null }, p.days.associate { it.date to it.raw })
        }
        return RosterGrid(read.month ?: YearMonth.from(rows.flatMap { it.cells.keys }.minOrNull() ?: Today.date()), rows.flatMap { it.cells.keys }.distinct().sorted(), rows, codesOf(rows))
    }

    /**
     * [READ-31] A photo (or a page of a PDF) read for [month]: each line its code as the photo gave it, doubtful days (read two
     * ways, lost by some readings, off their column) marked. [labels] names the calendar's kinds for cells another reading filled.
     */
    fun roster(read: RosterPhotoRead, month: YearMonth, labels: Map<Long, String> = emptyMap()): RosterGrid? {
        val last = month.lengthOfMonth()
        val rows = read.rows.mapIndexedNotNull { i, r ->
            val cells = r.cells.filterKeys { it in 1..last }.mapNotNull { (d, c) ->
                // The team's own name of a kind first; the word as read for days off and leave (휴, OFF, 연); "?" words as they are.
                val raw = when (c) {
                    is DutyCell.Unknown -> c.raw
                    is DutyCell.Shift -> labels[c.shiftTypeId] ?: r.raw[d] ?: return@mapNotNull null
                    DutyCell.Off -> r.raw[d]?.takeIf { common.read(it)?.first == DutyCell.Off } ?: "O"
                    is DutyCell.Leave -> r.raw[d]?.takeIf { common.read(it)?.first is DutyCell.Leave } ?: if (c.quarters < 4) "반차" else "연차"
                }
                month.atDay(d) to raw
            }.toMap()
            if (cells.isEmpty()) return@mapIndexedNotNull null
            val (name, role) = RosterSheet.nameAndRole(r.label, common)
            GridRow(name.ifBlank { "이름 없음 ${i + 1}" }, role.ifBlank { null }, cells, r.doubtful(last).filter { it in 1..last }.map(month::atDay).toSet())
        }
        if (rows.isEmpty()) return null
        return RosterGrid(month, rows.flatMap { it.cells.keys }.distinct().sorted(), rows, codesOf(rows))
    }

    /**
     * [READ-31] The pages of one roster (a PDF of two pages, two photos of a long table) as one: a name on two pages is one
     * line (its days joined, a page's doubt kept), new names come after in their order.
     */
    fun pages(grids: List<RosterGrid>): RosterGrid? {
        if (grids.isEmpty()) return null
        if (grids.size == 1) return grids[0]
        val rows = LinkedHashMap<String, GridRow>()
        for (g in grids) for (r in g.rows) {
            var key = DutyCodes.norm(r.name)
            // 2.0.0 [RVL-22] (검토 SCAN-07) Another member of the same name (their days meet): a line of their own, "김민지 (2)".
            var n = 1
            while (rows[key]?.let { had -> had.cells.keys.count(r.cells::containsKey) > 1 } == true) {
                n++
                key = DutyCodes.norm(DutyRoster.twin(r.name, n))
            }
            val had = rows[key]
            rows[key] = if (had == null) (if (n == 1) r else r.copy(name = DutyRoster.twin(r.name, n))) else had.copy(role = had.role ?: r.role, cells = had.cells + r.cells.filterKeys { it !in had.cells }, doubtful = had.doubtful + r.doubtful)
        }
        val all = rows.values.toList()
        val days = all.flatMap { it.cells.keys }.distinct().sorted()
        return RosterGrid(days.groupingBy { YearMonth.from(it) }.eachCount().maxBy { it.value }.key, days, all, codesOf(all))
    }

    private fun codesOf(rows: List<GridRow>): List<String> =
        rows.flatMap { it.cells.values }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key }
}

/** [READ-31] One staff member as a list gives them. [doubtful] names the fields read but not sure ("base", "weekly"). */
data class StaffRow(
    val name: String,
    val form: WageForm? = null,
    val base: Long? = null,
    val weekly: Map<DayOfWeek, Pair<LocalTime, LocalTime>> = emptyMap(),
    val job: String? = null,
    val hiredOn: LocalDate? = null,
    val doubtful: Set<String> = emptySet(),
    /** 2.0.0 [RVJ-40] 검토 PC-102: 소득세 칸 (3.3%·일용·근로), null when the list has none or it was not read. */
    val tax: StaffTax? = null,
    /** [RVJ-40] 외국인·국적 칸, null when the list has none. */
    val foreign: Boolean? = null,
    /** [RVJ-40] Cells that held something the reader could not read, as "일급 칸 ‘15만원?’", said as they are. */
    val unreadCells: List<String> = emptyList(),
)

/** [rows], and [unread]: the list's own column names that are not read (빈 칸 이름 빼고, 연락처·주민번호처럼 일부러 안 읽는 칸 빼고). */
data class StaffSheet(val rows: List<StaffRow>, val unread: List<String> = emptyList()) {
    companion object {
        fun read(rows: List<List<String>>, near: LocalDate = Today.date()): StaffSheet? = StaffSheets.read(rows, near)
        @JvmName("readBook")
        fun read(sheets: List<Sheets.Sheet>, near: LocalDate = Today.date()): StaffSheet? = StaffSheets.read(sheets, near)
    }
}

/**
 * [READ-31] A 사장님's staff list (직원 명단·근로계약 정리표) in any sheet: 이름, 급여 형태, 시급·월급, 근무 요일, 출근·퇴근, 담당,
 * 입사일. 연락처·주민번호·계좌·주소 columns are not read at all (개인정보 보호법 제16조 최소 수집): the ledger does not need them.
 */
object StaffSheets {
    private val NAME = listOf("이름", "성명", "직원", "직원명", "사원명", "NAME")
    private val FORM = listOf("급여형태", "임금형태", "형태", "급여구분", "구분")
    // 2.0.0 [RVJ-40] 검토 PC-102: '이름,시급,일급,월급,…' lists keep each person's pay in the column of its kind. Only the first of
    // them was read, so 일급·월급 people came in as 시급 at the 최저시급. Each kind has its own column now; [BASE] is a column of any kind.
    private val HOURLY = listOf("시급", "시간급", "시간당", "시간당급여")
    private val DAILY = listOf("일급", "일당", "하루급여")
    private val MONTHLY = listOf("월급", "월급여", "월급액", "월기본급", "월임금")
    private val BASE = listOf("급여", "임금", "기본급", "시급월급", "시급/월급", "금액", "통상임금", "급여액")
    private val TAX = listOf("소득세", "세금", "원천징수", "원천세", "소득구분", "세금구분", "신고구분", "소득종류", "세금처리", "과세구분")
    private val FOREIGN = listOf("외국인", "국적", "내외국인", "외국인여부", "내국인외국인")
    private val DAYS = listOf("근무요일", "요일", "근무일")
    private val START = listOf("출근", "시작", "출근시간", "근무시작", "시작시간")
    private val END = listOf("퇴근", "끝", "종료", "퇴근시간", "근무종료", "종료시간")
    private val HOURS = listOf("근무시간", "시간")
    private val JOB = listOf("담당", "업무", "직무", "파트", "포지션", "담당업무", "하는일", "직책", "역할")
    private val HIRED = listOf("입사일", "입사", "근로시작일", "계약시작")
    /** Never read (최소 수집). */
    private val PRIVATE = listOf("연락처", "전화", "휴대폰", "핸드폰", "주민", "주민번호", "주민등록번호", "계좌", "은행", "주소", "이메일")

    private fun norm(s: String) = s.replace(Regex("""[\s_()\[\]./·-]"""), "").uppercase()

    @JvmName("readBook")
    fun read(sheets: List<Sheets.Sheet>, near: LocalDate = Today.date()): StaffSheet? =
        sheets.mapNotNull { read(it.rows, near) }.maxByOrNull { it.rows.size }

    fun read(rows: List<List<String>>, near: LocalDate = Today.date()): StaffSheet? {
        val known = BASE + HOURLY + DAILY + MONTHLY + FORM + DAYS + START + JOB + HOURS + TAX
        val headAt = rows.take(10).indexOfFirst { r -> r.any { norm(it) in NAME } && r.count { c -> known.any { norm(c) == norm(it) } } >= 1 }
        if (headAt < 0) return null
        val head = rows[headAt].map(::norm)
        fun col(names: List<String>, not: Set<Int> = emptySet()) = head.withIndex().firstOrNull { (i, h) -> i !in not && names.any { norm(it) == h } }?.index ?: -1
        val nameCol = col(NAME)
        val privateCols = head.withIndex().filter { (_, h) -> PRIVATE.any { h.contains(norm(it)) } }.map { it.index }.toSet()
        val formCol = col(FORM, privateCols)
        val hourlyCol = col(HOURLY, privateCols + formCol); val dailyCol = col(DAILY, privateCols + formCol); val monthlyCol = col(MONTHLY, privateCols + formCol)
        val baseCol = col(BASE, privateCols + formCol + hourlyCol + dailyCol + monthlyCol); val daysCol = col(DAYS, privateCols)
        val startCol = col(START, privateCols); val endCol = col(END, privateCols); val hoursCol = col(HOURS, privateCols + startCol + endCol)
        val jobCol = col(JOB, privateCols); val hiredCol = col(HIRED, privateCols)
        val taxCol = col(TAX, privateCols); val foreignCol = col(FOREIGN, privateCols)
        val used = setOf(nameCol, formCol, hourlyCol, dailyCol, monthlyCol, baseCol, daysCol, startCol, endCol, hoursCol, jobCol, hiredCol, taxCol, foreignCol)
        // [RVJ-40] Said as it is: the list's columns that are not read (a 비고, a 휴게) are named in the preview.
        val unread = rows[headAt].withIndex().filter { (i, h) -> i !in used && i !in privateCols && h.isNotBlank() }.map { it.value.trim() }
        val typedCols = listOf(Triple(hourlyCol, WageForm.HOUR, "시급"), Triple(dailyCol, WageForm.DAY, "일급"), Triple(monthlyCol, WageForm.MONTH, "월급"))
            .filter { it.first >= 0 }
        val out = rows.drop(headAt + 1).mapNotNull { r ->
            fun at(c: Int) = if (c < 0 || c in privateCols) "" else r.getOrNull(c)?.trim().orEmpty()
            val name = RosterSheet.nameAndRole(at(nameCol), DutyCodes(emptyList())).first
            if (name.isEmpty() || name.any(Char::isDigit) || norm(name) in listOf("합계", "계", "소계", "TOTAL")) return@mapNotNull null
            val doubt = mutableSetOf<String>()
            val unreadCells = mutableListOf<String>()
            // [RVJ-40] The 시급·일급·월급 columns first: the one that holds a sum is the person's form and pay.
            val typed = typedCols.mapNotNull { (c, f, label) -> at(c).takeUnless(::blankish)?.let { t -> Triple(f, moneyOf(t), label to t) } }
            typed.filter { it.second == null }.forEach { (_, _, lt) -> unreadCells += "${lt.first} 칸 ‘${lt.second}’"; doubt += "base" }
            val readTyped = typed.filter { it.second != null }
            val saidForm = formOf(at(formCol))
            val pick = readTyped.firstOrNull { it.first == saidForm } ?: readTyped.firstOrNull()
            if (readTyped.size > 1 && saidForm == null) doubt += "form"
            val formText = at(formCol) + " " + head.getOrNull(baseCol).orEmpty()
            val generic = at(baseCol).takeUnless(::blankish).orEmpty()
            val money = pick?.second ?: moneyOf(generic)
            if (pick == null && generic.isNotEmpty() && money == null) { doubt += "base"; unreadCells += "${rows[headAt].getOrNull(baseCol)?.trim().orEmpty().ifBlank { "금액" }} 칸 ‘$generic’" }
            // The read column's kind; else the one typed column that held something (an 일급 칸 not read is still an 일급); else the 형태 칸.
            val form = pick?.first ?: typed.singleOrNull()?.first ?: saidForm ?: when {
                formText.contains("시급") && !formText.contains("월급") || at(formCol).contains("시급") -> WageForm.HOUR
                at(formCol).contains("월급") || at(formCol).contains("월") && !at(formCol).contains("일") -> WageForm.MONTH
                at(formCol).contains("일급") || at(formCol).contains("일당") -> WageForm.DAY
                money != null && money >= 1_000_000 -> WageForm.MONTH.also { doubt += "form" }
                money != null && money in 50_000 until 1_000_000 -> WageForm.DAY.also { doubt += "form" }
                money != null -> WageForm.HOUR.also { if (at(formCol).isEmpty()) doubt += "form" }
                else -> null
            }
            // [RVJ-40] 소득세 (3.3%·일용·근로) and 외국인 칸.
            val taxText = at(taxCol)
            val tax = taxOf(taxText) ?: if (at(formCol).contains("일용")) StaffTax.DAILY else null
            if (taxText.isNotBlank() && taxOf(taxText) == null) { doubt += "tax"; unreadCells += "${rows[headAt].getOrNull(taxCol)?.trim()} 칸 ‘$taxText’" }
            val foreignText = at(foreignCol)
            val foreign = foreignOf(foreignText, byCountry = foreignCol >= 0 && norm(rows[headAt][foreignCol]) == "국적")
            if (foreignText.isNotBlank() && foreign == null) { doubt += "foreign"; unreadCells += "${rows[headAt].getOrNull(foreignCol)?.trim()} 칸 ‘$foreignText’" }
            val times = hoursOf(at(startCol), at(endCol), at(hoursCol))
            if ((at(startCol) + at(endCol) + at(hoursCol)).isNotBlank() && times == null) doubt += "weekly"
            val days = daysOf(at(daysCol))
            if (at(daysCol).isNotBlank() && days.isEmpty()) doubt += "weekly"
            StaffRow(
                name, form, money,
                if (times != null) (days.ifEmpty { emptySet() }).associateWith { times } else emptyMap(),
                at(jobCol).ifBlank { null }, AttendanceImport.parseDate(at(hiredCol), near.year, near), doubt, tax, foreign, unreadCells,
            )
        }
        return out.takeIf { it.isNotEmpty() }?.let { StaffSheet(it, unread) }
    }

    /**
     * [RVJ-40] 검토 PC-102: the column names read, said in one line before the file is picked ([READS]), and a file that has them all
     * ([EXAMPLE_CSV], 예시 파일). The example is read back by a test, so the two do not drift apart.
     */
    const val READS = "이름·시급·일급·월급·근무 요일·출근·퇴근·입사일·하는 일·소득세·외국인"
    val EXAMPLE_CSV = """
        이름,급여형태,시급,일급,월급,근무요일,출근,퇴근,입사일,하는일,소득세,외국인
        김알바,시급,10320,,,월~금,09:00,15:00,2026-03-02,홀,근로소득,
        이일용,일급,,150000,,토일,10:00,19:00,2026-10-01,주방,일용,
        박매니저,월급,,,2500000,월~금,09:00,18:00,2025-01-06,매니저,근로소득,
        최디자인,시급,12000,,,수,13:00,18:00,2026-09-01,디자인,3.3%,
        응우옌,시급,10320,,,월수금,10:00,16:00,2026-05-04,주방,근로소득,예
    """.trimIndent() + "\n"

    /** "10,320", "10320원", "250만", "2,500,000" as a sum; null for anything else. */
    internal fun moneyOf(text: String): Long? = text.replace(Regex("""[,\s원₩]"""), "").let { t ->
        Regex("""^(\d+(?:\.\d+)?)(만)?$""").matchEntire(t)?.let { m -> (m.groupValues[1].toDouble() * if (m.groupValues[2].isNotEmpty()) 10_000 else 1).toLong() }
    }

    /** A cell that says nothing: empty, a dash, 0 or 없음 (the other kinds' columns of a person paid by one kind). */
    private fun blankish(t: String): Boolean = t.isBlank() || t.trim() in setOf("-", "–", "—", "0", "없음", "x", "X", "/", "해당없음")

    /** The 급여 형태 column said in words. */
    private fun formOf(t: String): WageForm? = when {
        t.contains("시급") -> WageForm.HOUR
        t.contains("일급") || t.contains("일당") || t.contains("일용") -> WageForm.DAY
        t.contains("월급") || t.contains("연봉") -> WageForm.MONTH
        else -> null
    }

    /** [RVJ-40] 소득세 칸: '3.3%'·'사업소득' → 3.3%, '일용' → 일용, '근로'·'간이'·'4대보험' → 근로소득, '없음'·'안 뗌' → 떼지 않음. */
    internal fun taxOf(t: String): StaffTax? {
        val s = t.replace(" ", "")
        return when {
            s.isEmpty() -> null
            s.contains("3.3") || s.contains("사업") || s.contains("프리") -> StaffTax.BIZ
            s.contains("일용") -> StaffTax.DAILY
            s.contains("근로") || s.contains("간이") || s.contains("4대") || s.contains("갑근") -> StaffTax.WAGE
            s.contains("없") || s.contains("안뗌") || s.contains("미공제") || s.contains("비과세") -> StaffTax.NONE
            else -> null
        }
    }

    /** [RVJ-40] 외국인 칸: 예·O·✓·외국인 → yes, 아니오·X·내국인 → no. A 국적 칸 says yes for any country but 한국. */
    internal fun foreignOf(t: String, byCountry: Boolean): Boolean? {
        val s = t.replace(" ", "").uppercase()
        if (s.isEmpty()) return null
        if (byCountry) return s !in setOf("한국", "대한민국", "내국인", "KOREA", "KR", "KOR", "한국인")
        return when (s) {
            "Y", "YES", "예", "네", "O", "○", "●", "✓", "✔", "V", "1", "TRUE", "외국인", "해당" -> true
            "N", "NO", "아니오", "아니요", "X", "×", "0", "FALSE", "내국인", "-", "해당없음" -> false
            else -> null
        }
    }

    /** "월화수", "월~금", "월,수,금", "주5일(월-금)", "평일", "주말" as days. */
    internal fun daysOf(t: String): Set<DayOfWeek> {
        val s = t.replace(" ", "")
        if (s.isEmpty()) return emptySet()
        val week = "월화수목금토일"
        if (s.contains("평일")) return (0..4).map { DayOfWeek.of(it + 1) }.toSet() + if (s.contains("주말")) setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) else emptySet()
        if (s == "주말") return setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
        val out = mutableSetOf<DayOfWeek>()
        Regex("""([월화수목금토일])[~\-]([월화수목금토일])""").findAll(s).forEach { m ->
            val a = week.indexOf(m.groupValues[1]); val b = week.indexOf(m.groupValues[2])
            var i = a
            while (true) { out += DayOfWeek.of(i + 1); if (i == b) break; i = (i + 1) % 7 }
        }
        s.forEach { c -> week.indexOf(c).takeIf { it >= 0 }?.let { out += DayOfWeek.of(it + 1) } }
        return out
    }

    /** 출근·퇴근 columns, or one "09:00~18:00" / "9-18" column. */
    private fun hoursOf(start: String, end: String, both: String): Pair<LocalTime, LocalTime>? {
        val a = AttendanceImport.parseTime(start); val b = AttendanceImport.parseTime(end)
        if (a != null && b != null) return a to b
        val m = Regex("""(\d{1,2})(?::(\d{2}))?\s*[~\-–]\s*(\d{1,2})(?::(\d{2}))?""").find(both.ifBlank { start }) ?: return null
        val h1 = m.groupValues[1].toInt(); val h2 = m.groupValues[3].toInt()
        if (h1 > 24 || h2 > 24) return null
        return LocalTime.of(h1 % 24, m.groupValues[2].ifEmpty { "0" }.toInt()) to LocalTime.of(h2 % 24, m.groupValues[4].ifEmpty { "0" }.toInt())
    }
}
