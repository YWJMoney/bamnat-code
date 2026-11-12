package com.bamnat.core.imports

import com.bamnat.core.model.DayEntry
import com.bamnat.core.model.LeaveKind
import com.bamnat.core.model.ShiftOverride
import java.text.Normalizer
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import kotlin.math.abs
import kotlin.math.roundToInt

/*
 * [SHFT-06] A month's duty roster (듀티표) read from a photo on the phone, or from text pasted from
 * another app: a row of day numbers (1 2 3 … 31) and, under it, a row of codes for each person
 * (D E N O, 주 야 비 휴, or the calendar's own names). Codes are matched to the calendar's kinds of
 * work; what cannot be matched stays as "?" for the user to fix before anything is written.
 */

/** A word recognised in a photo with its box in pixels; text pasted by hand gets boxes by position. */
data class RosterWord(
    val text: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    /** [ROST-03] Split off a name it was stuck to ("김민지D*"): its day is one to look at. */
    val doubt: Boolean = false,
    /** [READ-02] The slope of the OCR's line this word was on, in degrees (ML Kit `Text.Line.angle`): 0 when unknown. */
    val angle: Float = 0f,
) {
    val centerX: Int get() = (left + right) / 2
    val centerY: Int get() = (top + bottom) / 2
    val height: Int get() = (bottom - top).coerceAtLeast(1)
}

/** What one cell of a roster means on the calendar. */
sealed interface DutyCell {
    /** A kind of work (or a day-off kind such as 휴무) of the calendar. */
    data class Shift(val shiftTypeId: Long) : DutyCell
    /** A day off the calendar has no kind for. */
    data object Off : DutyCell
    /**
     * 연차·휴가. [ROST-05] With its kind and part of the day (상-3): 반차 is half a day of 연차, 생휴 is 무급, never a
     * whole day of 연차 for every leave word as before.
     */
    data class Leave(val kind: LeaveKind = LeaveKind.ANNUAL, val quarters: Int = 4, val fromStart: Boolean = false) : DutyCell {
        val leave: com.bamnat.core.model.Leave get() = com.bamnat.core.model.Leave(kind, quarters, fromStart && quarters < 4)
    }
    /**
     * Read, but matches nothing: the user picks. [ROST-06] [leave] is a leave word whose kind each company sets
     * (병가·경조·공가): the user is asked which kind once, and the calendar remembers the answer.
     */
    data class Unknown(val raw: String, val leave: Boolean = false) : DutyCell
}

/** One kind of day of the calendar, for matching the roster's codes. */
data class DutyType(
    val id: Long,
    val name: String,
    val label: String,
    val isOff: Boolean,
    val start: LocalTime? = null,
    val night: Boolean = false,
)

/**
 * One person's line of the roster: the name at its left (may be blank) and the cell of each day of the month.
 * [guessed] are the days read from a common code (주, N, 휴) rather than the calendar's own name or short name
 * of a kind of day, so they may be the wrong one of the user's kinds [NURS-04].
 */
data class RosterRow(
    val label: String,
    val cells: Map<Int, DutyCell>,
    val guessed: Set<Int> = emptySet(),
    /**
     * [ROST-03] What a cell said besides its code, by day: a mark after it ("D*" → "*"), the next code joined to it
     * ("N-O") or a time before it ("11D" → "11"). Kept as the day's memo so nothing written on the roster is lost.
     */
    val notes: Map<Int, String> = emptyMap(),
    /** [READ-03] Days whose cell stood off its column's middle in the photo: maybe the next day's, so looked at. */
    val unsure: Set<Int> = emptySet(),
    /**
     * [READ-41] 1.38 PC 12.3: days the readings gave two different codes for (not merely one reading losing it). Such a cell
     * stays yellow even when the line's repeat agrees with the pick: the photo may say the other one.
     */
    val contested: Set<Int> = emptySet(),
    /** [READ-04] The line of the table it was read from, top to bottom: the order people are listed, and where a tap lands. */
    val line: Int = 0,
    /**
     * [READ-19] 1.27: the words read for each day as the photo gave them ("OF", "0", "Ｄ"), so a cell the user corrects
     * teaches the code book what that word means on their rosters ([RosterLearn]). In memory only, never saved.
     */
    val raw: Map<Int, String> = emptyMap(),
    /**
     * [READ-30] 1.38 PC: a 4주 듀티 file's days outside [cells]' month (10/26~31 of a "11월" roster that runs 10/26~11/25), by
     * date, so they go in too instead of being dropped.
     */
    val outside: Map<java.time.LocalDate, DutyCell> = emptyMap(),
    /**
     * [READ-41] Where the line stands in the photo, top to bottom (the middle of its words, level), 0 when not known: the
     * readings of one photo share the photo's pixels, so a line is matched by place when people share a rota.
     */
    val y: Int = 0,
    /** [READ-41] Days the photo had nothing on that the line's repeat filled ([RosterPeriod.fill]): yellow, and said so. */
    val filled: Set<Int> = emptySet(),
    /**
     * 2.0.0 [RVL-24] (검토 SCAN-10·T07b-05) A 4주 듀티 photo's days before the 1st (26 27 … 31 | 1 2 …), counted back from it: 0 is
     * the last day of the month before. In memory only, until the month is known ([placed]) and they join [outside].
     */
    val before: Map<Int, DutyCell> = emptyMap(),
) {
    val unknown: Int get() = cells.values.count { it is DutyCell.Unknown }

    /** 2.0.0 [RVL-24] [before] as dates of the month before [month], in [outside] with the file's. */
    fun placed(month: java.time.YearMonth): RosterRow = if (before.isEmpty()) this
        else copy(outside = before.mapKeys { (d, _) -> month.atDay(1).plusDays(d - 1L) } + outside, before = emptyMap())

    /**
     * [NURS-04] The days to look at before saving (순번 275): cells read but not matched, cells guessed from a
     * common code, and days missing between the first and last cell read (a cell the photo lost).
     */
    fun doubtful(lastDay: Int): Set<Int> {
        val days = cells.keys.filter { it <= lastDay }
        if (days.isEmpty()) return emptySet()
        val missing = (days.min()..days.max()).filter { it !in cells }
        return (cells.filter { it.value is DutyCell.Unknown }.keys + guessed + unsure + missing).filter { it <= lastDay }.toSortedSet()
    }
}

/**
 * [READ-04] Where the user pointed on the photo, in its pixels: the row of days and their own line. Given when the photo
 * read nothing (or the wrong thing) by itself, so any table can be read with two taps.
 */
data class RosterHint(val dayRow: Pair<Int, Int>? = null, val myRow: Pair<Int, Int>? = null)

/**
 * [READ-05] What the reader made of a photo: the lines, the tilt it took out, each line of the photo and what it was taken
 * for (shown under "무엇을 읽었는지 보기"), and why nothing came out.
 */
data class RosterPhotoRead(val rows: List<RosterRow>, val tilt: Double, val lines: List<Line>, val problem: Problem?) {
    enum class Kind { DAYS, WEEKDAYS, DUTIES, OTHER }
    enum class Problem { NO_TEXT, NO_DAYS, NO_DUTIES }
    data class Line(val text: String, val kind: Kind, val left: Int, val top: Int, val right: Int, val bottom: Int)
}

/**
 * [SHFT-06] The roster's codes, matched to the calendar's kinds: their own names and short names first,
 * then the codes hospitals and factories use (D/E/N/O, 데이·이브닝·나이트, 주·야·비·휴, 연차).
 */
class DutyCodes(
    private val types: List<DutyType>,
    /**
     * [ROST-06] The calendar's own code book: what the user said a code means once (병가 → 유급 휴가, M → 미들),
     * by [norm]ed code. It comes before the common codes, after the calendar's own names.
     */
    private val learned: Map<String, DutyCell> = emptyMap(),
) {
    private enum class Group(val words: Set<String>) {
        // [IMPORT-06] 경찰 (주·야·비·휴, 일근) and 소방 (당·비·휴) words, from no real roster yet (표본 없음, 확인 필요). A lone
        // "일" is left out: it is also 일요일 in a weekday row.
        DAY(setOf("D", "DAY", "DY", "데", "데이", "주", "주간", "낮", "일근", "주간근무", "상시", "상시근무")),
        EVENING(setOf("E", "EV", "EVE", "이", "이브", "이브닝", "오후", "저녁")),
        NIGHT(setOf("N", "NT", "NIGHT", "나", "나이트", "야", "야간", "밤", "야간근무")),
        // [NURS-03] S is a middle shift (09~18) on the rosters seen, not an evening.
        MID(setOf("M", "MD", "MID", "미", "미드", "S", "중", "중간")),
        DUTY(setOf("당", "당직", "당번", "당무", "당직근무", "24H")),
        OFF(setOf("O", "OF", "OFF", "0", "휴", "휴무", "휴일", "X", "-", "/", "비", "비번", "오프", "OH", "공", "공휴", "PH", "주휴", "비번일", "휴무일")),
    }

    private val exact: Map<String, Long> = buildMap {
        // Short names win over names, and earlier kinds over later ones.
        types.reversed().forEach { t -> norm(t.name).takeIf { it.isNotEmpty() }?.let { put(it, t.id) } }
        types.reversed().forEach { t -> norm(t.label).takeIf { it.isNotEmpty() }?.let { put(it, t.id) } }
    }

    private fun typeFor(g: Group): DutyCell? {
        types.firstOrNull { !it.isOff && (norm(it.name) in g.words || norm(it.label) in g.words) }?.let { return DutyCell.Shift(it.id) }
        val work = types.filter { !it.isOff && it.start != null }
        val pick = when (g) {
            Group.DAY -> work.filter { !it.night && it.start!!.hour in 5..10 }.minByOrNull { it.start!! }
            Group.EVENING -> work.filter { !it.night || it.start!!.hour in 12..17 }.filter { it.start!!.hour in 11..17 }.minByOrNull { it.start!! }
            Group.NIGHT -> work.filter { it.night && (it.start!!.hour >= 18 || it.start.hour < 3) }.minByOrNull { (it.start!!.hour + 12) % 24 }
            else -> null
        }
        if (pick != null) return DutyCell.Shift(pick.id)
        return when (g) {
            Group.OFF -> types.firstOrNull { it.isOff && (norm(it.name) in g.words || norm(it.label) in g.words) }
                ?.let { DutyCell.Shift(it.id) } ?: types.firstOrNull { it.isOff }?.let { DutyCell.Shift(it.id) } ?: DutyCell.Off
            else -> null
        }
    }

    private val aliases: Map<String, DutyCell?> = Group.entries.flatMap { g -> g.words.map { it to g } }
        .associate { (w, g) -> w to typeFor(g) } + LEAVE_WORDS

    /** What [token] means, or null when it is no code at all (a name, a number). */
    fun match(token: String): DutyCell? {
        val n = norm(token)
        if (n.isEmpty()) return null
        exact[n]?.let { return DutyCell.Shift(it) }
        learned[n]?.let { return it }
        if (aliases.containsKey(n)) return aliases[n] ?: DutyCell.Unknown(token.trim())
        // [READ-36] 1.38 PC 8차: a one-letter code read a stroke off ("아" for 야, "추" for 주, "취" for 휴): the code it is
        // nearly, looked at (yellow) like any common code.
        LOOKALIKE[n]?.let { t -> if (aliases.containsKey(t)) return aliases[t] ?: DutyCell.Unknown(token.trim()) }
        return null
    }

    /**
     * [ROST-03] [token] as it stands on a real roster (상-1·2): a code with a mark after it ("D*", "E/", "주'"), two
     * codes joined ("N-O", "N/O", "N→O": the first is the day's), or a time with the code ("11D", "9E", "D11").
     * The code's cell and what else was written, or null when the token holds no code.
     */
    fun read(token: String): Pair<DutyCell, String?>? {
        match(token)?.let { return it to null }
        val t = fold(token).trim()
        MARKED.matchEntire(t)?.let { m -> match(m.groupValues[1])?.let { return it to m.groupValues[2] } }
        JOINED.matchEntire(t)?.let { m ->
            val first = match(m.groupValues[1])
            if (first != null && match(m.groupValues[2]) != null) return first to t
        }
        TIMED.matchEntire(t)?.let { m ->
            val code = m.groupValues[2].ifEmpty { m.groupValues[3] }
            val hour = m.groupValues[1].ifEmpty { m.groupValues[4] }
            if (code.any { it.isLetter() }) match(code)?.let { return it to hour }
        }
        return null
    }

    /**
     * [NURS-04] True when [cell] is a kind of work picked from a common code in [token] (주, N, 이브닝) rather than
     * the calendar's own name or short name: the right group, maybe not the user's own kind. Days off never are.
     */
    fun guessedWork(token: String, cell: DutyCell): Boolean = codeOf(token).let { c ->
        c in LOOKALIKE && !exact.containsKey(c) && !learned.containsKey(c) ||
            cell is DutyCell.Shift && types.firstOrNull { it.id == cell.shiftTypeId }?.isOff == false && !exact.containsKey(c) && !learned.containsKey(c)
    }

    /** [ROST-11] The code a token stands for, marks and times taken off: what the code book keeps an answer under. */
    fun codeOf(token: String): String {
        val t = fold(token).trim()
        MARKED.matchEntire(t)?.let { return norm(it.groupValues[1]) }
        JOINED.matchEntire(t)?.let { return norm(it.groupValues[1]) }
        TIMED.matchEntire(t)?.let { m -> return norm(m.groupValues[2].ifEmpty { m.groupValues[3] }) }
        return norm(t)
    }

    /** True for a token that stands in a code's place but matches nothing: short and not a number. */
    fun looksLikeCode(token: String): Boolean {
        val n = norm(token)
        if (n.isEmpty() || n.any { it.isDigit() } || n.none { it.isLetter() }) return false
        return n.length <= 2 || (n.length <= 4 && n.all { it in 'A'..'Z' })
    }

    /** True for letters A–Z only that stand for several codes ("DDEN"): never a person's name. */
    fun mergedLatin(token: String): Boolean = norm(token).let { n -> n.isNotEmpty() && n.all { it in 'A'..'Z' } } && split(token) != null

    /** [token] as single-letter codes ("DDEN" → D D E N), or null when it is not one. */
    fun split(token: String): List<String>? {
        val n = norm(token)
        // Letters only: "N/" is a code with a mark and "N-O" two codes joined, never N and a day off.
        if (n.length < 2 || match(token) != null || n in RANKS || !n.all { it.isLetter() }) return null
        val chars = n.map { it.toString() }
        if (chars.all { match(it) != null }) return chars
        // Latin letters only, so a Korean name in a row is never cut into 주·야·휴.
        return if (n.all { it in 'A'..'Z' }) segments(n) else null
    }

    /**
     * 1.36 PC Y1: two cells read as one word ("NOFF" → N + OFF, "DOFFN" → D OFF N): [n] cut into known codes of up to
     * [MAX_PART] letters, the fewest pieces; null when it cannot be cut whole.
     */
    private fun segments(n: String): List<String>? {
        val best = arrayOfNulls<List<String>>(n.length + 1)
        best[0] = emptyList()
        for (end in 1..n.length) {
            for (len in 1..minOf(MAX_PART, end)) {
                val before = best[end - len] ?: continue
                val part = n.substring(end - len, end)
                if (match(part) == null) continue
                val next = before + part
                if (best[end] == null || next.size < best[end]!!.size) best[end] = next
            }
        }
        return best[n.length]?.takeIf { it.size >= 2 }
    }

    /**
     * [ROST-03][ROST-04] A name with something stuck to it (하): a job title ("김민지RN" → 김민지) or the first day's
     * code ("김민지D*" → 김민지 + D*). The name and the code token, or null when [token] is a name as it is.
     */
    fun splitName(token: String): Pair<String, String?>? {
        val t = fold(token).trim()
        val m = NAME_TAIL.matchEntire(t) ?: return null
        val name = m.groupValues[1]
        val tail = m.groupValues[2]
        if (norm(tail) in RANKS) return name to null
        if (tail.none { it in 'A'..'Z' || it in 'a'..'z' } && MARKS.none { it in tail }) return null
        return if (read(tail) != null) name to tail else null
    }

    companion object {
        /** The longest code [segments] cuts out of a stuck-together word. */
        private const val MAX_PART = 4
        private val STRIP = Regex("""[\s.,·:;|()\[\]{}'"`‘’“”]""")
        /** [ROST-04] Full-width letters and digits from a 한글 document (Ｄ, １) read as plain ones (중-7). */
        fun fold(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFKC)
        fun norm(s: String): String = fold(s).replace(STRIP, "").uppercase()

        /** [READ-36] One-letter codes as a photo misreads them, and the code each nearly is. */
        internal val LOOKALIKE = mapOf("아" to "야", "추" to "주", "츄" to "주", "쥬" to "주", "취" to "휴", "휘" to "휴")

        /** [ROST-03] Marks written after a code on rosters: 교육 *, 반 근무 /, 표시 ', 추가 +, △ ○ ※. */
        private const val MARKS = "*/'+△▲○●※#!~`^"
        private val MARKED = Regex("""^([^*/'+△▲○●※#!~`^\s]+)([*/'+△▲○●※#!~`^]+)$""")
        private val JOINED = Regex("""^([^\s\-/→>]+)\s*(?:-|/|→|>|->)\s*([^\s\-/→>]+)$""")
        private val TIMED = Regex("""^(?:(\d{1,2})([A-Za-z가-힣]{1,3})|([A-Za-z가-힣]{1,3})(\d{1,2}))$""")
        private val NAME_TAIL = Regex("""^([가-힣]{2,4})([A-Za-z]{1,3}[*/'+△▲○●※#!~`^]*|[가-힣]{1,2}[*/'+△▲○●※#!~`^]+)$""")

        /** [ROST-04] Job titles written with a name: never part of it, never a code. */
        val RANKS = setOf("RN", "CN", "HN", "PN", "UM", "NA", "AN", "LPN", "수간호사", "책임", "책임간호사", "주임", "간호사", "조무사", "간호조무사", "선임", "팀장", "조장", "반장")

        /**
         * 2.0.0 [RVL-21] (검토 SCAN-06·T07b-04) Short leave words that mean something else on many rosters: AM·PM are 오전·오후
         * 근무 in many 병동 and 가게, V·MS·HD·AL and one-letter 반·생·년 are a 메모 or a team's own mark as often as a leave. Read
         * as a leave without asking they took 연차 off quietly; now each is asked once ([DutyCell.Unknown], answered into the code
         * book), with the leave it most often is offered first: its name on the chip and the cell it puts in.
         */
        private val GUESSES: Map<String, Pair<String, DutyCell>> = buildMap {
            listOf("V", "AL", "년").forEach { put(it, "연차" to DutyCell.Leave()) }
            put("AM", "오전 반차" to DutyCell.Leave(quarters = 2, fromStart = true))
            put("PM", "오후 반차" to DutyCell.Leave(quarters = 2))
            listOf("반", "HD").forEach { put(it, "반차" to DutyCell.Leave(quarters = 2)) }
            listOf("생", "MS").forEach { put(it, "생리휴가" to DutyCell.Leave(LeaveKind.UNPAID)) }
        }

        /** 2.0.0 [RVL-21] The leave a short word asked about most often is ([GUESSES]): its name and cell, or null. */
        fun guessOf(raw: String): Pair<String, DutyCell>? = GUESSES[norm(raw)]

        /**
         * [ROST-05] Leave words (상-3). 연차 is a whole day; 반차 half a day of it (오전반 off the start); 반반차 a quarter.
         * 생리휴가 is 무급: 근로기준법 제73조 "사용자는 여성 근로자가 청구하면 월 1일의 생리휴가를 주어야 한다."
         * says nothing of pay (2004년 개정으로 유급 문구가 빠짐; 출처 위키문헌 근로기준법 조문, 현행 원문 대조 확인 필요 —
         * 계획/1.24-P-개발계획.md 조사). 병가·공가·경조·특별휴가 are set by each company's rules, not the law, so the
         * user is asked once ([DutyCell.Unknown.leave]).
         */
        private val LEAVE_WORDS: Map<String, DutyCell> = buildMap {
            listOf("연", "연차", "V", "VA", "VAC", "AL", "휴가", "년", "년차", "연가").forEach { put(it, DutyCell.Leave()) }
            listOf("반", "반차", "HD", "HALF", "반일").forEach { put(it, DutyCell.Leave(quarters = 2)) }
            listOf("오전반", "오전반차", "AM", "AM반", "전반").forEach { put(it, DutyCell.Leave(quarters = 2, fromStart = true)) }
            listOf("오후반", "오후반차", "PM", "PM반", "후반").forEach { put(it, DutyCell.Leave(quarters = 2)) }
            listOf("반반", "반반차", "QD").forEach { put(it, DutyCell.Leave(quarters = 1)) }
            listOf("생", "생휴", "생리", "생리휴가", "MS").forEach { put(it, DutyCell.Leave(LeaveKind.UNPAID)) }
            listOf("병", "병가", "SL", "공가", "경조", "경", "경조사", "특휴", "특별휴가", "특", "보", "보건", "보건휴가", "청원", "포상", "위로", "하계", "하휴", "교육휴가")
                .forEach { put(it, DutyCell.Unknown(it, leave = true)) }
            // 2.0.0 [RVL-21] A short word that is a leave on one roster and a shift on another is asked once instead.
            GUESSES.keys.forEach { put(it, DutyCell.Unknown(it)) }
        }
    }
}

object DutyRoster {
    /** [READ-32] A 2~5 letter 한글 name and the Latin codes OCR stuck to its end ("최유리ED"). */
    private val NAME_CODES = Regex("""^([가-힣]{2,5})[A-Za-z]{1,4}$""")
    // [NURS-03] "1", "1일", "01", and a day with its weekday in one cell: "1(월)". "1월" is a month, never a day.
    // [ROST-04] A date in the row of days too, as a spreadsheet copies it (중-7): "10/1", "10.1", "10월1일", "10-1".
    private val DAY_TOKEN = Regex("""^(?:(\d{1,2})\s*[/.\-월]\s*)?(\d{1,2})일?(?:\((?:월|화|수|목|금|토|일)\))?$""")
    private val WEEKDAYS = setOf("월", "화", "수", "목", "금", "토", "일", "MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")
    /** Marks a code token split off a name in [readText] until it becomes a [RosterWord]. */
    private const val STUCK = "\u0000"
    /** Fewest days in a row of numbers for it to be the roster's row of days. */
    const val MIN_DAYS = 5
    /** Fewest cells for a line to be someone's duties. */
    const val MIN_CELLS = 3

    /**
     * [SHFT-06] Reads a roster from recognised words with their boxes: each person's line under the
     * nearest row of day numbers, each code under the day it sits beneath. Most cells first.
     * [READ-02] The photo's tilt is taken out first and lines are followed word to word ([RosterGeometry]); [hint] is
     * where the user pointed at the row of days and at their own line when nothing was read without it.
     */
    fun read(words: List<RosterWord>, codes: DutyCodes, hint: RosterHint? = null): List<RosterRow> = analyse(words, codes, hint).rows

    /** [READ-05] [read], with what was seen on the way: the tilt, each line and what it was taken for, and why nothing came out. */
    fun analyse(words: List<RosterWord>, codes: DutyCodes, hint: RosterHint? = null): RosterPhotoRead {
        val clean = words.filter { it.text.isNotBlank() }
        if (clean.isEmpty()) return RosterPhotoRead(emptyList(), 0.0, emptyList(), RosterPhotoRead.Problem.NO_TEXT)
        val tilt = RosterGeometry.tilt(clean)
        val lines = RosterGeometry.lines(RosterGeometry.level(clean, tilt)).map { line -> line.flatMapIndexed { i, w -> explode(w, codes, first = i == 0) } }
        val nearest = { at: Pair<Int, Int> ->
            val (_, y) = RosterGeometry.level(at.first, at.second, tilt)
            lines.indices.minByOrNull { i -> abs(lines[i].sumOf { it.y } / lines[i].size - y) }
        }
        val fits = HashMap<Int, DayFit>()
        val tapped = hint?.dayRow?.let(nearest)
        if (tapped != null) {
            // [READ-04] The line the user said is the row of days: its numbers, or its words one day each from the first.
            val line = lines[tapped]
            (RosterGeometry.fit(RosterGeometry.days(line, ::dayOf), 2) ?: sequence(line))?.let { fits[tapped] = extend(it, line, lines) }
        } else {
            lines.forEachIndexed { i, line -> dayFit(line)?.let { fits[i] = extend(it, line, lines) } }
        }
        val read = if (fits.isEmpty()) emptyList() else gridRows(lines, fits, codes)
        val columns = if (read.isEmpty()) columnRows(lines, fits.keys, codes) else emptyList()
        val list = listRow(lines, codes)?.takeIf { l -> columns.none { it.cells.size >= l.cells.size } }
        var rows = (merge(read) + columns + listOfNotNull(list)).filter { it.cells.size >= MIN_CELLS }
            .sortedWith(compareByDescending<RosterRow> { it.cells.size - it.unknown }.thenBy { it.unknown })
            .distinctBy { it.label to it.cells }
        rows = tidy(rows)
        // [READ-37] 1.38 PC: a ward's row of weekdays under its dates, misread ("으" for 일), and the row of dates with
        // "이름" beside it came out as two more people ("으 · 16일", "이름|의 · 22일"; 14명 for 12).
        rows.filterNot(::headerLike).takeIf { it.isNotEmpty() }?.let { rows = it }
        // [READ-36] 1.38 PC 8차 (조 근무표 사진, "3번째 줄 · 18일"): on a sheet whose lines are named (1조, 2조, 김민지), a short
        // line with no name that says what a named line says on the same days is a piece of that one read twice, never one
        // more person. (A nameless line of its own, a 손글씨 name lost, stays.)
        if (rows.count { it.label.isNotBlank() } >= 2) {
            val most = rows.maxOf { it.cells.size }
            fun twice(r: RosterRow) = rows.any { o ->
                o !== r && o.label.isNotBlank() && r.cells.keys.intersect(o.cells.keys).let { shared ->
                    shared.size >= 5 && shared.count { r.cells[it] == o.cells[it] } * 10 >= shared.size * 8
                }
            }
            rows.filterNot { it.label.isBlank() && it.cells.size * 10 < most * 7 && twice(it) }.takeIf { it.isNotEmpty() }?.let { rows = it }
        }
        // [GROW-05] 1.38: a month calendar's picture (다른 앱 화면 캡처) is one line of the user's own; its weeks are not
        // several people's lines.
        if (hint == null) CalendarShot.read(clean, codes)?.let { shot -> if (rows.none { it.cells.size > shot.cells.size }) rows = listOf(shot) }
        hint?.myRow?.let(nearest)?.let { mine ->
            // [READ-04] The user's own line: that one only, read even when short, in order from the 1st when no row of days is known.
            rows = rows.filter { it.line == mine }.ifEmpty {
                listOfNotNull(if (fits.isNotEmpty()) gridRows(lines, fits, codes, only = mine, min = 1).firstOrNull() else sequenceRow(lines[mine], codes, mine))
            }
        }
        val kinds = lines.mapIndexed { i, line ->
            val kind = when {
                i in fits -> RosterPhotoRead.Kind.DAYS
                isWeekdayRow(line) -> RosterPhotoRead.Kind.WEEKDAYS
                rows.any { it.line == i } -> RosterPhotoRead.Kind.DUTIES
                else -> RosterPhotoRead.Kind.OTHER
            }
            RosterPhotoRead.Line(line.joinToString(" ") { it.text }, kind, line.minOf { it.word.left }, line.minOf { it.word.top },
                line.maxOf { it.word.right }, line.maxOf { it.word.bottom })
        }
        val problem = when {
            rows.isNotEmpty() -> null
            fits.isEmpty() && columns.isEmpty() -> RosterPhotoRead.Problem.NO_DAYS
            else -> RosterPhotoRead.Problem.NO_DUTIES
        }
        return RosterPhotoRead(rows, tilt, kinds, problem)
    }

    /**
     * [SHFT-06] Reads text pasted by hand (from Google Lens or a messenger): one line of the table per line,
     * cells apart by spaces or tabs. A person's name at the start of a line is kept as the line's name.
     */
    /**
     * 2.0.0 [RVL-16] (검토 SCAN-08) The lines of one roster read page by page (a scanned PDF of 1~15일 and 16~31일): a name on two pages is
     * one line, its days joined, as [TeamSheet.pages] does for a team. A day two pages read differently keeps the first page's and is
     * looked at ([RosterRow.contested]); a line without a name stays a line of its own. Before, the second page's days were dropped.
     */
    fun joinPages(rows: List<RosterRow>): List<RosterRow> {
        val out = LinkedHashMap<String, RosterRow>()
        for (r in rows) {
            var key = DutyCodes.norm(r.label).ifEmpty { "#${out.size}" }
            // 2.0.0 [RVL-22] (검토 SCAN-07) Another person of the same name (their days meet this one's): a line of its own.
            var n = 1
            while (out[key]?.let { had -> had.cells.keys.count(r.cells::containsKey) > SEAM } == true) {
                n++
                key = DutyCodes.norm(twin(r.label, n))
            }
            val had = out[key]
            out[key] = if (had == null) (if (n == 1) r else r.copy(label = twin(r.label, n))) else {
                fun Set<Int>.fresh() = filter { it !in had.cells }
                val clash = r.cells.filter { (d, c) -> had.cells[d]?.let { it != c } == true }.keys
                had.copy(
                    cells = had.cells + r.cells.filterKeys { it !in had.cells },
                    guessed = had.guessed + r.guessed.fresh(),
                    notes = r.notes.filterKeys { it !in had.cells } + had.notes,
                    unsure = had.unsure + r.unsure.fresh(),
                    contested = had.contested + r.contested.fresh() + clash,
                    raw = r.raw.filterKeys { it !in had.cells } + had.raw,
                    outside = r.outside + had.outside,
                    filled = had.filled + r.filled.fresh(),
                    before = r.before + had.before,
                )
            }
        }
        return out.values.toList()
    }

    fun readText(text: String, codes: DutyCodes): List<RosterRow> {
        val words = mutableListOf<RosterWord>()
        text.lines().forEachIndexed { i, line ->
            if ('\t' in line) {
                // Copied from a spreadsheet: one cell per tab, an empty cell keeping its day's place, so each
                // cell sits under its own column (a name's columns to the left of the days).
                line.trimEnd().split('\t').forEachIndexed { j, cell ->
                    if (cell.isNotBlank()) words += RosterWord(cell.trim(), j * 100, i * 100, j * 100 + 60, i * 100 + 40)
                }
                return@forEachIndexed
            }
            val raw = line.trim().split(Regex("""[\s,]+""")).filter { it.isNotBlank() }
                // [ROST-03] A name with the first day's code stuck to it ("김민지D*") is the name and that code.
                .flatMapIndexed { j, t -> if (j > 1) listOf(t) else codes.splitName(t)?.let { (n, c) -> listOfNotNull(n, c?.let { STUCK + it }) } ?: listOf(t) }
                // [ROST-04] A job title beside the name (김민지 RN) is neither.
                .filter { DutyCodes.norm(it) !in DutyCodes.RANKS }
            // The name first, as written: a two-letter name made of code letters (주연, 이나, 연주) is not codes.
            val lead = raw.takeWhile { t -> !t.startsWith(STUCK) && dayOf(t) == null && codes.read(t) == null && DutyCodes.norm(t) !in WEEKDAYS && !codes.mergedLatin(t) }
            // Days and codes are placed by their order, the name before them to the left of the first column.
            val tokens = raw.drop(lead.size).flatMap { t -> if (t.startsWith(STUCK)) listOf(t) else codes.split(t) ?: listOf(t) }
            lead.forEachIndexed { j, t -> words += RosterWord(t, -1000 + j * 10, i * 100, -1000 + j * 10 + 8, i * 100 + 40) }
            tokens.forEachIndexed { j, t ->
                words += RosterWord(t.removePrefix(STUCK), j * 100, i * 100, j * 100 + 60, i * 100 + 40, doubt = t.startsWith(STUCK))
            }
        }
        return read(words, codes)
    }

    /** What each day of [row] does to that day of the calendar, for the days the month has. */
    fun apply(entry: DayEntry, cell: DutyCell): DayEntry = when (cell) {
        is DutyCell.Shift -> entry.copy(override = ShiftOverride.Shift(cell.shiftTypeId), leave = null)
        DutyCell.Off -> entry.copy(override = ShiftOverride.Off, leave = null)
        // [ROST-05] 반차 keeps the day's work (half of it is taken off); a whole day's leave keeps it too, as before.
        is DutyCell.Leave -> entry.copy(leave = cell.leave)
        is DutyCell.Unknown -> entry
    }

    /**
     * [READ-30] 1.38 PC (x03): the kind of work a roster line has most of. A day the roster gives 연차 is a working day taken
     * off, whatever the user's own order says of it, so a leave on a day the calendar has as a rest day is put on this shift
     * (the leave then counts and is paid, as the roster means). Null when the line has no work.
     */
    fun mainShift(cells: Collection<DutyCell>): Long? =
        cells.filterIsInstance<DutyCell.Shift>().groupingBy { it.shiftTypeId }.eachCount().maxByOrNull { it.value }?.key

    /** [READ-30] [cell]'s leave on [entry], a rest day on the calendar, made a day of [shiftTypeId] work taken off. */
    fun leaveOnRest(entry: DayEntry, cell: DutyCell.Leave, shiftTypeId: Long): DayEntry =
        entry.copy(override = ShiftOverride.Shift(shiftTypeId), leave = cell.leave)

    /**
     * [ROST-02] What [cells] would overwrite on a calendar whose days are [entries]: days with leave, or with a
     * kind of work the user changed by hand, that the roster puts something else on (하 '덮어쓰기 안내').
     */
    fun overwrites(cells: Map<LocalDate, DutyCell>, entries: Map<LocalDate, DayEntry>): Set<LocalDate> =
        cells.filter { (d, c) -> c !is DutyCell.Unknown }.keys.filter { d ->
            val e = entries[d] ?: return@filter false
            val after = apply(e, cells.getValue(d))
            (e.leave != null && after.leave != e.leave) || (e.override != ShiftOverride.None && after.override != e.override)
        }.toSet()

    /**
     * [ROST-04] The month a pasted roster names (중-6): "10월 근무표" / "10월 듀티" first (its year beside it, or the one nearest
     * [near]), "2026년 10월", "2026-10", "2026.10", a span "2026.10.26 ~ 11.22", or the month of the dates in its row of days
     * ("10/1 10/2 …"). A date with its day (작성일) names no month. Null when it names none.
     */
    fun monthIn(text: String, near: LocalDate): YearMonth? {
        val t = DutyCodes.fold(text)
        fun nearest(month: Int): YearMonth = listOf(near.year - 1, near.year, near.year + 1).map { YearMonth.of(it, month) }
            .minBy { abs(it.atDay(1).toEpochDay() - near.toEpochDay()) }
        // 2.0.0 [RVL-25] (검토 SCAN-11) A date with its day (작성일 2026.10.25, 2026년 10월 25일) is when it was written, never the
        // roster's month: "11월 근무표" comes first, then a year and month alone ("2026년 11월", "2026.11"), then a span of dates.
        val yearMonth = Regex("""(20\d{2})\s*년\s*(\d{1,2})\s*월(?!\s*\d{1,2}\s*일)""")
        val dotted = Regex("""(?<!\d)(20\d{2})\s*[.\-/]\s*(\d{1,2})(?!\d)(?!\s*[.\-/]\s*\d)""")
        fun pairs(r: Regex) = r.findAll(t).mapNotNull { m -> runCatching { YearMonth.of(m.groupValues[1].toInt(), m.groupValues[2].toInt()) }.getOrNull() }
        Regex("""(?<!\d)(\d{1,2})\s*월\s*(?:근무|듀티|근무표|DUTY|스케줄|일정|당직)""", RegexOption.IGNORE_CASE).find(t)?.groupValues?.get(1)?.toInt()
            ?.takeIf { it in 1..12 }?.let { named ->
                return (pairs(yearMonth) + pairs(dotted)).firstOrNull { it.monthValue == named } ?: nearest(named)
            }
        // [READ-37] 1.38 PC: "7병동 2026년 11월" read as July. The words of a title come in reading order only roughly (a
        // tilted photo puts "2026년" before "7병동"), so "2026년 7…" is never a month unless 월 follows it; "11월" is.
        pairs(yearMonth).firstOrNull()?.let { return it }
        pairs(dotted).firstOrNull()?.let { return it }
        // 2.0.0 [RVL-25] "2026.10.26 ~ 2026.11.22" (a 4주 듀티): the month most of its days are in.
        Regex("""(20\d{2})\s*[.\-/년]\s*(\d{1,2})\s*[.\-/월]\s*(\d{1,2})\s*일?\s*[~∼～]\s*(?:(20\d{2})\s*[.\-/년]\s*)?(\d{1,2})\s*[.\-/월]\s*(\d{1,2})""")
            .find(t)?.let { m ->
                val g = m.groupValues
                runCatching {
                    val from = LocalDate.of(g[1].toInt(), g[2].toInt(), g[3].toInt())
                    val to = LocalDate.of(g[4].ifEmpty { g[1] }.toInt(), g[5].toInt(), g[6].toInt()).let { if (it < from) it.plusYears(1) else it }
                    generateSequence(from) { it.plusDays(1) }.takeWhile { it <= to }.take(62).groupingBy { YearMonth.from(it) }.eachCount().maxBy { it.value }.key
                }.getOrNull()?.let { return it }
            }
        // A year and, apart from it, a month ("2026년" … "11월"): the first "n월" that is not a day's date ("11월 3일").
        Regex("""(20\d{2})\s*년""").find(t)?.let { y ->
            Regex("""(?<![\d.])(\d{1,2})\s*월(?!\s*\d{1,2}\s*일)""").findAll(t).map { it.groupValues[1].toInt() }.firstOrNull { it in 1..12 }
                ?.let { return YearMonth.of(y.groupValues[1].toInt(), it) }
        }
        val month = t.lines().firstNotNullOfOrNull { line ->
                val months = line.split(Regex("""\s+""")).mapNotNull { DAY_TOKEN.matchEntire(it.trim())?.groupValues?.get(1)?.toIntOrNull() }
                months.takeIf { it.size >= MIN_DAYS }?.groupingBy { it }?.eachCount()?.maxByOrNull { it.value }?.key
            }
            ?: return null
        if (month !in 1..12) return null
        // The year that puts the month closest to [near]: a December roster read in January is last year's.
        return nearest(month)
    }

    /** [ROST-04] Days [row] has that [month] does not (31일 on a 30-day month): said before they are left out. */
    fun beyond(row: RosterRow, month: YearMonth): List<Int> = row.cells.keys.filter { it > month.lengthOfMonth() }.sorted()

    private fun dayOf(token: String): Int? =
        DAY_TOKEN.matchEntire(DutyCodes.fold(token).trim().trimEnd('.', ','))?.groupValues?.get(2)?.toIntOrNull()?.takeIf { it in 1..31 }

    /**
     * A word that holds several cells ("1 2 3", "DDEN") becomes one word per cell across its box. The
     * [first] word of a line may be a name, so there only letters A–Z are taken apart (주연 stays a name).
     */
    private fun explode(p: Placed, codes: DutyCodes, first: Boolean = false): List<Placed> {
        val parts = p.text.trim().split(Regex("""\s+""")).filter { it.isNotEmpty() }
            .flatMap { s -> (if (first && !codes.mergedLatin(s)) null else codes.split(s)) ?: listOf(s) }
        if (parts.size <= 1) return listOf(p)
        val step = p.w / parts.size
        return parts.mapIndexed { i, s ->
            val w = p.word
            val l = w.left + ((w.right - w.left) * i / parts.size)
            val r = w.left + ((w.right - w.left) * (i + 1) / parts.size)
            Placed(RosterWord(s, l, w.top, r, w.bottom, w.doubt, w.angle), p.left + step * (i + 0.5), p.y, step, p.h)
        }
    }

    /** [READ-03] The columns of a row of days: mostly day numbers, at least [MIN_DAYS] of them on one straight line. */
    private fun dayFit(line: List<Placed>): DayFit? {
        val numbers = line.count { dayOf(it.text) != null || RosterGeometry.days(listOf(it), ::dayOf).isNotEmpty() }
        if (numbers < MIN_DAYS || numbers * 10 < line.size * 6) return null
        return RosterGeometry.fit(RosterGeometry.days(line, ::dayOf), MIN_DAYS)
    }

    /** [READ-04] A line the user called the row of days with no numbers on it: its words one day each, from the 1st. */
    private fun sequence(line: List<Placed>): DayFit? {
        if (line.size < 2) return null
        val (a, b) = RosterGeometry.leastSquares(line.sortedBy { it.x }.mapIndexed { i, p -> (i + 1) to p.x }) ?: return null
        return DayFit(a, b, 1, line.size.coerceAtMost(31))
    }

    /**
     * [READ-03] The first and last days of a row of days whose numbers the photo lost (31 cut at the edge, 1 under a
     * thumb): a column more on either side while words stand under it and no other number is there (the next month's).
     */
    private fun extend(fit: DayFit, header: List<Placed>, lines: List<List<Placed>>): DayFit {
        val others = lines.filter { it !== header }.flatten()
        val numbers = RosterGeometry.days(header, ::dayOf)
        fun free(d: Int): Boolean {
            val x = fit.x(d)
            return numbers.none { (n, nx) -> n != d && abs(nx - x) < 0.45 * fit.b } && others.any { abs(it.x - x) < 0.4 * fit.b }
        }
        var first = fit.first
        var last = fit.last
        while (first > 1 && fit.first - first < 3 && free(first - 1)) first--
        while (last < 31 && last - fit.last < 3 && free(last + 1)) last++
        return fit.copy(first = first, last = last)
    }

    /** [READ-37] Words over a table's names column: a line beginning with one is the table's head, not a person. */
    private val HEAD_WORDS = listOf("이름", "성명", "요일", "날짜", "일자", "NAME", "DATE", "DAY")

    /**
     * [READ-37] A line read as a person's that is the table's head: hardly any of its cells a duty, and most of them a
     * weekday, a misread one (one or two Hangul letters no code knows) or a number; or "이름"·"요일" at its start and
     * fewer than half its cells duties. A line of 주·야·비·휴 is all duties and stays.
     */
    internal fun headerLike(row: RosterRow): Boolean {
        val n = row.cells.size
        if (n == 0) return false
        fun text(d: Int, c: DutyCell) = row.raw[d] ?: (c as? DutyCell.Unknown)?.raw.orEmpty()
        val duties = row.cells.count { (d, c) -> c !is DutyCell.Unknown && DutyCodes.norm(text(d, c)) !in WEEKDAYS }
        val heads = row.cells.count { (d, c) ->
            val t = DutyCodes.norm(text(d, c))
            t in WEEKDAYS || c is DutyCell.Unknown && !c.leave && t.isNotEmpty() &&
                (t.length <= 2 && t.all { it in '가'..'힣' } || t.all(Char::isDigit))
        }
        val label = DutyCodes.norm(row.label)
        return duties * 4 < n && heads * 2 >= n || HEAD_WORDS.any { label.startsWith(it) } && duties * 2 < n
    }

    private fun isWeekdayRow(row: List<Placed>): Boolean =
        row.size >= MIN_DAYS && row.count { DutyCodes.norm(it.text) in WEEKDAYS } * 10 >= row.size * 7

    /**
     * Each person's line under its row of days. [READ-03] A line is read with the columns of the line read before it
     * under the same row of days, fitted again to where its own cells stand, so a table the camera saw at an angle (wider
     * at the bottom) keeps every cell on its day. [only] reads that line alone, with [min] cells enough.
     */
    private fun gridRows(lines: List<List<Placed>>, headers: Map<Int, DayFit>, codes: DutyCodes, only: Int? = null, min: Int = MIN_CELLS): List<RosterRow> {
        val out = mutableListOf<RosterRow>()
        val carried = HashMap<Int, DayFit>()
        lines.forEachIndexed { i, row ->
            if (i in headers || isWeekdayRow(row) || (only != null && i != only)) return@forEachIndexed
            // The row of days this line belongs under: the nearest above, or below for a table drawn upside down.
            val h = headers.keys.filter { it < i }.maxOrNull() ?: headers.keys.min()
            val start = carried[h] ?: headers.getValue(h)
            val first = readLine(row, start, codes)
            var read = first.first
            var fit = first.second
            // The columns again, from where this line's own cells stand.
            val own = RosterGeometry.leastSquares(read.positions)
            if (start.knots.isNotEmpty()) {
                // [READ-33] Uneven columns: the row of days' places moved and stretched to this line's cells (x = α + β·x(d)).
                val pairs = read.positions.map { (d, x) -> start.x(d) to x }
                if (pairs.size >= 5) {
                    val mx = pairs.sumOf { it.first } / pairs.size; val my = pairs.sumOf { it.second } / pairs.size
                    val sxx = pairs.sumOf { (p, _) -> (p - mx) * (p - mx) }
                    val beta = if (sxx > 0) pairs.sumOf { (p, q) -> (p - mx) * (q - my) } / sxx else 1.0
                    val alpha = my - beta * mx
                    if (beta in 0.8..1.25) {
                        val refit = start.copy(a = alpha + beta * start.a, b = beta * start.b, knots = start.knots.map { (d, x) -> d to alpha + beta * x })
                        readLine(row, refit, codes).first.let { again -> if (again.cells.size >= read.cells.size) { read = again; fit = refit } }
                    }
                }
            } else if (own != null && read.positions.size >= 5 && own.second in start.b * 0.8..start.b * 1.25) {
                val refit = start.copy(a = own.first, b = own.second, c = 0.0)
                readLine(row, refit, codes).first.let { again -> if (again.cells.size >= read.cells.size) { read = again; fit = refit } }
            }
            // [READ-13] 1.27: columns that narrow along the row (seen from one side): bent to where this line's cells stand.
            if (fit.knots.isEmpty()) RosterGeometry.bent(fit, read.positions)?.let { bent ->
                readLine(row, bent, codes).first.let { again -> if (again.cells.size >= read.cells.size && again.unsure.size <= read.unsure.size) { read = again; fit = bent } }
            }
            if (read.cells.size >= min) {
                carried[h] = fit
                val ys = row.map { it.y }.sorted()
                out += RosterRow(read.label, read.cells.toSortedMap(), read.guessed, read.notes, read.unsure, line = i, raw = read.raws,
                    y = ys[ys.size / 2].toInt(), before = read.before)
            }
        }
        return out
    }

    private class LineRead(
        val label: String, val cells: Map<Int, DutyCell>, val guessed: Set<Int>, val notes: Map<Int, String>, val unsure: Set<Int>,
        val positions: List<Pair<Int, Double>>, val raws: Map<Int, String> = emptyMap(), val before: Map<Int, DutyCell> = emptyMap(),
    )

    private fun readLine(row: List<Placed>, fit: DayFit, codes: DutyCodes): Pair<LineRead, DayFit> {
        val cells = LinkedHashMap<Int, DutyCell>()
        val off = HashMap<Int, Double>()
        val at = HashMap<Int, Double>()
        val guessed = HashSet<Int>()
        val notes = HashMap<Int, String>()
        val raws = HashMap<Int, String>()
        val before = HashMap<Int, DutyCell>()
        val label = StringBuilder()
        // 1.25 통합 직접 시험: a table with its names at the right end (after the last day) read every cell but no name.
        val rightLabel = StringBuilder()
        val firstX = fit.x(fit.first)
        val lastX = fit.x(fit.last)
        for (w in row) {
            val leftOfDays = w.x < firstX - fit.b / 2
            val rightOfDays = w.x > lastX + fit.b / 2
            // [ROST-03] A name with a code stuck to it, in a photo's one word: doubtful, so the day is looked at.
            val stuck = if (leftOfDays) codes.splitName(w.text) else null
            if (stuck != null) {
                label.append(stuck.first)
                val code = stuck.second?.let(codes::read)
                if (code != null && cells.putIfAbsent(fit.first, code.first) == null) {
                    guessed += fit.first
                    off[fit.first] = 1.0
                    code.second?.let { notes[fit.first] = it }
                }
                continue
            }
            val read = codes.read(w.text)
            // [READ-32] 1.38 PC (스캔 PDF "최유리ED", 7일부터): when the date row was read from a later day on, the first days'
            // codes stand left of it. Each is put on the day its place says, counted back from the first day read, and
            // looked at (yellow); never taken into the name.
            if (leftOfDays && read != null && fit.first > 1 && label.any { it in '가'..'힣' }) {
                val back = (firstX - w.x) / fit.step(fit.first)
                val d = fit.first - back.roundToInt()
                if (d in 1 until fit.first && abs(back - back.roundToInt()) <= 0.45 && d !in cells) {
                    cells[d] = read.first
                    guessed += d
                    off[d] = 1.0
                    raws[d] = w.text.trim()
                }
                continue
            }
            // 2.0.0 [RVL-24] (검토 SCAN-10·T07b-05) A 4주 듀티 that starts in the month before (26 27 … 31 | 1 2 …): a code left of the
            // 1st after the name is that month's day, counted back from the 1st ([RosterRow.before]); never part of the name.
            if (leftOfDays && read != null && fit.first == 1 && label.any { it.isLetter() }) {
                val back = (firstX - w.x) / fit.step(fit.first)
                val r = back.roundToInt()
                if (r >= 1 && abs(back - r) <= 0.45) before.putIfAbsent(1 - r, read.first)
                continue
            }
            val near = if (leftOfDays) null else fit.day(w.x)
            val aligned = near != null && near.second <= 0.45
            val cell = read?.first ?: if (codes.looksLikeCode(w.text) && aligned) DutyCell.Unknown(w.text.trim()) else null
            when {
                cell != null && aligned -> {
                    val (d, miss) = near!!
                    // Two words on one day: the one nearer its middle.
                    if (d !in cells || miss < off.getValue(d)) {
                        cells[d] = cell
                        off[d] = miss
                        at[d] = w.x
                        raws[d] = w.text.trim()
                        // 1.25 통합 직접 시험: a lone "0" is O to the reader, but OCR also makes it of D: looked at, never taken quietly.
                        if (w.word.doubt || codes.guessedWork(w.text, cell) || w.text.trim() == "0") guessed += d else guessed -= d
                        read?.second?.let { notes[d] = it } ?: notes.remove(d)
                    }
                }
                leftOfDays && dayOf(w.text) == null && DutyCodes.norm(w.text) !in DutyCodes.RANKS -> label.append(w.text.trim())
                rightOfDays && read == null && dayOf(w.text) == null && DutyCodes.norm(w.text) !in DutyCodes.RANKS &&
                    w.text.any { it.isLetter() } -> rightLabel.append(w.text.trim())
            }
        }
        if (label.isBlank()) label.append(rightLabel)
        // [READ-32] 1.38 PC: "이수진D", "정다은N": a 한글 name with Latin codes stuck at its end is the name alone.
        NAME_CODES.matchEntire(label.toString())?.groupValues?.get(1)?.let { name -> label.setLength(0); label.append(name) }
        // [READ-03] A cell far from its column's middle may be the next day's: one to look at.
        val unsure = off.filter { (d, miss) -> miss > 0.3 && d in cells }.keys
        // [READ-13] 1.27: a line written in 한글 codes (주·야·비·휴) with one Latin word ("OF" for 야, the bench's dim 조 표)
        // has a misread cell: the word matched a code of the other alphabet. Looked at, never quiet.
        guessed += mixedScript(raws, cells)
        // 2.0.0 [RVL-24] Days of the month before only as a run up to the 1st (two at least): one code beside a name is a 조 or a mark.
        val run = generateSequence(0) { it - 1 }.takeWhile { it in before }.count()
        val kept = if (run >= 2) before.filterKeys { it > -run } else emptyMap()
        return LineRead(label.toString(), cells, guessed, notes, unsure, at.map { (d, x) -> d to x }, raws, kept) to fit
    }

    /** [READ-13] Days whose word is in the other alphabet from most of the line's work and days off (leave words aside). */
    internal fun mixedScript(raws: Map<Int, String>, cells: Map<Int, DutyCell>): Set<Int> {
        fun script(t: String): Char? {
            val f = DutyCodes.norm(t).filter { it.isLetter() }
            return when {
                f.isEmpty() -> null
                f.all { it in '가'..'힣' } -> 'K'
                f.all { it in 'A'..'Z' } -> 'L'
                else -> null
            }
        }
        val judged = raws.filterKeys { d -> cells[d] is DutyCell.Shift || cells[d] == DutyCell.Off }.mapValues { (_, t) -> script(t) }.filterValues { it != null }
        if (judged.size < 5) return emptySet()
        val major = judged.values.groupingBy { it }.eachCount().maxBy { it.value }
        // Only a 한글 line: OCR turns a blurred 야 into "OF" or "O", never D·E·N into 한글, and a D·E·N roster with 휴·야간
        // written in is common (DutyRosterTypingTest.doubtfulCells).
        if (major.key != 'K' || major.value * 10 < judged.size * 6) return emptySet()
        return judged.filterValues { it != major.key }.keys
    }

    /**
     * [READ-04] The user's own line when no row of days is known: its codes one day each from the 1st, left to right.
     * Every day is one to look at.
     */
    private fun sequenceRow(line: List<Placed>, codes: DutyCodes, index: Int): RosterRow? {
        val read = line.sortedBy { it.x }.mapNotNull { w -> codes.read(w.text) }
        if (read.isEmpty()) return null
        val cells = read.take(31).mapIndexed { i, (c, _) -> (i + 1) to c }.toMap().toSortedMap()
        val label = line.sortedBy { it.x }.takeWhile { codes.read(it.text) == null }.joinToString("") { it.text.trim() }
        return RosterRow(label, cells, unsure = cells.keys, line = index)
    }

    /** [READ-06] The day a line of a table with the days down its side starts with: "1(월)", or "1(" "5원" as a photo reads them. */
    private fun leadDay(token: String): Int? = dayOf(token)
        ?: Regex("""^(\d{1,2})\s*(?:\(.{0,2}\)?|[^\d\s월]{1,2})$""").matchEntire(DutyCodes.fold(token).trim())?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 1..31 }

    /**
     * [READ-06] A table with the days down its side (1(월) D E N … a line per day) and the people across: each column of
     * codes is a person, named by the word over it. Lines starting with a day are the table's lines; the columns are
     * followed down the page, so a table seen at an angle stays in its columns.
     */
    private fun columnRows(lines: List<List<Placed>>, headers: Set<Int>, codes: DutyCodes): List<RosterRow> {
        fun codesOf(words: List<Placed>) = words.filter { DutyCodes.norm(it.text) !in WEEKDAYS && !it.text.trim().matches(Regex("""\([^)]*\)""")) }
            .filter { codes.read(it.text) != null || codes.looksLikeCode(it.text) }
        val dated = lines.withIndex().mapNotNull { (i, line) ->
            val first = line.firstOrNull() ?: return@mapNotNull null
            val day = leadDay(first.text) ?: return@mapNotNull null
            Triple(i, day, codesOf(line.drop(1)))
        }
        // The longest run of lines whose days go up one by one with the lines (a line whose day the photo lost counts
        // as its place says): a title's "10월" or a stray number is left out.
        val chains = dated.map { start -> dated.filter { it.first >= start.first && it.second - start.second == it.first - start.first } }
        val chain = chains.maxByOrNull { it.size } ?: return emptyList()
        if (chain.size < MIN_DAYS) return emptyList()
        val (top, firstDay) = chain.first().first to chain.first().second
        val led = (top..chain.last().first).mapNotNull { i ->
            val day = firstDay + (i - top)
            chain.firstOrNull { it.first == i } ?: lines[i].let { line ->
                // Its day lost: the line's own codes, when it starts with one.
                line.takeIf { it.isNotEmpty() && codes.read(it.first().text) != null }?.let { Triple(i, day, codesOf(it)) }
            }
        }.filter { it.second in 1..31 }
        if (led.count { it.third.isNotEmpty() } < MIN_DAYS) return emptyList()
        val h = RosterGeometry.median(led.flatMap { it.third }.map { it.h }).coerceAtLeast(1.0)
        // The people's names: the line over the first day, when it is no row of days itself.
        val head = lines.getOrNull(led.first().first - 1)?.takeIf { led.first().first - 1 !in headers && it.none { w -> dayOf(w.text) != null } }
        val template = led.maxBy { it.third.size }.third
        val columns = (head?.takeIf { it.size >= template.size }?.map { it.x } ?: template.map { it.x }).toMutableList()
        val labels = columns.map { x -> head?.minByOrNull { abs(it.x - x) }?.takeIf { abs(it.x - x) < 2 * h }?.text?.trim().orEmpty() }
        val spacing = columns.sorted().zipWithNext { a, b -> b - a }.minOrNull() ?: (h * 8)
        val cells = List(columns.size) { sortedMapOf<Int, DutyCell>() }
        val guessed = List(columns.size) { HashSet<Int>() }
        val notes = List(columns.size) { HashMap<Int, String>() }
        for ((_, day, words) in led) {
            for (w in words) {
                val c = columns.indices.minBy { abs(columns[it] - w.x) }
                if (abs(columns[c] - w.x) > spacing * 0.5) continue
                val (cell, note) = codes.read(w.text) ?: (DutyCell.Unknown(w.text.trim()) to null)
                if (cells[c].putIfAbsent(day, cell) == null) {
                    if (codes.guessedWork(w.text, cell)) guessed[c] += day
                    note?.let { notes[c][day] = it }
                    // Followed down the page: a column drifts with the camera's angle.
                    columns[c] = columns[c] * 0.7 + w.x * 0.3
                }
            }
        }
        return columns.indices.map { c -> RosterRow(labels[c], cells[c], guessed[c], notes[c], line = top) }
    }

    /** Lines of the same person under the 1–15 and 16–31 rows of days become one. */
    /**
     * 1.36 PC Y9 (듀티표 9줄: 같은 사람 줄이 이름 줄과 이름 없는 줄로 겹치고 '3일' 쓰레기 줄): a full nameless reading of a named row's own line
     * that agrees with it is that person's, taken whole (the one with more days first, the other filling gaps); and once some
     * row holds most of a month, nameless rows of fewer than [SHORT_ROW] days are dropped as bits of the page.
     */
    internal fun tidy(rows: List<RosterRow>): List<RosterRow> {
        val out = rows.toMutableList()
        for (u in rows.filter { it.label.isBlank() && it.cells.size >= FULL_ROW }) {
            // The same line of the photo read a second time (against another row of days), with its name on one reading only:
            // it reads like the named one (a day pushed by each stuck cell still reads in the same order). A neighbour's line,
            // even with almost the same rota, is never taken.
            val n = out.firstOrNull { it.label.isNotBlank() && it.line == u.line && same(it, u) } ?: continue
            val (first, second) = if (u.cells.size >= n.cells.size) u to n else n to u
            val cells = (second.cells + first.cells).toSortedMap()
            val merged = RosterRow(n.label, cells,
                guessed = first.guessed + second.guessed.filter { it !in first.cells },
                notes = second.notes + first.notes,
                unsure = first.unsure + second.unsure.filter { it !in first.cells },
                line = n.line, raw = second.raw + first.raw, y = n.y)
            out[out.indexOf(n)] = merged
            out.remove(u)
        }
        val most = out.maxOfOrNull { it.cells.size } ?: 0
        // A short row with a name stays (handwriting reads few cells); one with no name is a bit of the page.
        return if (most >= FULL_ROW) out.filter { it.cells.size >= SHORT_ROW || it.label.isNotBlank() } else out
    }

    /**
     * The share of the days both rows read where they read the same code, the best of the two lined up as they are or one day
     * apart (a row pushed by a stuck cell, "NOFF"); 0 with fewer than 3 such days.
     */
    private fun agreement(a: RosterRow, b: RosterRow): Double = (-1..1).maxOf { shift ->
        val both = a.cells.keys.filter { (it + shift) in b.cells }
        if (both.size < 3) 0.0 else both.count { a.cells[it] == b.cells[it + shift] }.toDouble() / both.size
    }

    /** Fewer days than this, nameless, beside a full row: a bit of the page ('3일 · ‖z'), never a handwritten line (7+). */
    /** Two lines are one person's: their cells agree day by day, or their codes in order do (LCS ≥ 85% of the shorter). */
    private fun same(a: RosterRow, b: RosterRow): Boolean = agreement(a, b) >= 0.6 || inOrder(a, b) >= 0.85

    private fun inOrder(a: RosterRow, b: RosterRow): Double {
        val x = a.cells.toSortedMap().values.toList()
        val y = b.cells.toSortedMap().values.toList()
        if (minOf(x.size, y.size) < SHORT_ROW) return 0.0
        val dp = Array(x.size + 1) { IntArray(y.size + 1) }
        for (i in 1..x.size) for (j in 1..y.size) {
            dp[i][j] = if (x[i - 1] == y[j - 1]) dp[i - 1][j - 1] + 1 else maxOf(dp[i - 1][j], dp[i][j - 1])
        }
        return dp[x.size][y.size].toDouble() / minOf(x.size, y.size)
    }

    private const val SHORT_ROW = 6
    private const val FULL_ROW = 20
    /** 2.0.0 [RVL-22] Days two halves of one line may share where a photo's halves meet; more and they are two people. */
    private const val SEAM = 1
    private val TWIN = Regex("""^(.*) \((\d+)\)$""")

    /** 2.0.0 [RVL-22] (검토 SCAN-07) The [n]th person of one name on a roster: "김민지 (2)". */
    fun twin(label: String, n: Int): String = "$label ($n)"

    /** 2.0.0 [RVL-22] [a] and [b] are one name on the roster, "(2)" aside. */
    fun sameName(a: String, b: String): Boolean = (TWIN.matchEntire(a)?.groupValues?.get(1) ?: a) == (TWIN.matchEntire(b)?.groupValues?.get(1) ?: b)

    /** 2.0.0 [RVL-22] True when [rows] hold two people named [label]: my line is then asked, never taken by its name. */
    fun twins(rows: List<RosterRow>, label: String): Boolean = rows.count { sameName(it.label, label) } > 1

    private fun merge(rows: List<RosterRow>): List<RosterRow> {
        val named = rows.filter { it.label.isNotBlank() }.groupBy { it.label }.flatMap { (label, lines) ->
            // 2.0.0 [RVL-22] (검토 SCAN-07) Lines of one name are one person's halves only when their days do not meet (a day read
            // twice at the seam aside); two people of one name (동명이인, two lines of one 조) stay two: "김민지", "김민지 (2)".
            val people = mutableListOf<MutableList<RosterRow>>()
            for (l in lines) {
                val home = people.firstOrNull { ps -> ps.sumOf { p -> p.cells.keys.count(l.cells::containsKey) } <= SEAM }
                if (home != null) home += l else people += mutableListOf(l)
            }
            people.mapIndexed { i, parts ->
                RosterRow(if (i == 0) label else twin(label, i + 1),
                    parts.fold(sortedMapOf<Int, DutyCell>()) { acc, r -> acc.apply { r.cells.forEach { (d, c) -> putIfAbsent(d, c) } } },
                    parts.flatMap { it.guessed }.toSet(), parts.reversed().fold(emptyMap()) { acc, r -> acc + r.notes },
                    parts.flatMap { it.unsure }.toSet(), line = parts.first().line, y = parts.first().y,
                    // [READ-19] 1.27: the words as read, for 고친 것 배우기 (two halves of one person kept them apart).
                    raw = parts.reversed().fold(emptyMap()) { acc, r -> acc + r.raw },
                    before = parts.reversed().fold(emptyMap()) { acc, r -> acc + r.before })
            }
        }
        val blank = rows.filter { it.label.isBlank() }
        // Two blank lines whose days do not overlap are one person's two halves.
        val joined = if (blank.size == 2 && blank[0].cells.keys.intersect(blank[1].cells.keys).isEmpty()) {
            listOf(RosterRow("", (blank[0].cells + blank[1].cells).toSortedMap(), blank[0].guessed + blank[1].guessed, blank[0].notes + blank[1].notes,
                blank[0].unsure + blank[1].unsure, line = blank[0].line, raw = blank[0].raw + blank[1].raw, y = blank[0].y))
        } else blank
        return named + joined
    }

    /** A roster written as a list, one day per line: "1 D", "10/3 N", "3일 주간". */
    private fun listRow(rows: List<List<Placed>>, codes: DutyCodes): RosterRow? {
        val cells = sortedMapOf<Int, DutyCell>()
        val guessed = HashSet<Int>()
        val notes = HashMap<Int, String>()
        val dated = Regex("""^(?:\d{1,2}[/.월-])?(\d{1,2})일?(?:\([^)]*\))?$""")
        var first: Int? = null
        rows.forEachIndexed { i, row ->
            if (row.size < 2 || row.size > 4) return@forEachIndexed
            val day = dated.matchEntire(row[0].text.trim())?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 1..31 } ?: return@forEachIndexed
            val rest = row.drop(1).filter { DutyCodes.norm(it.text) !in WEEKDAYS && !it.text.trim().matches(Regex("""\([^)]*\)""")) }
            val word = rest.firstOrNull { codes.read(it.text) != null } ?: return@forEachIndexed
            val (cell, note) = codes.read(word.text)!!
            if (cells.putIfAbsent(day, cell) == null) {
                if (first == null) first = i
                if (codes.guessedWork(word.text, cell)) guessed += day
                note?.let { notes[day] = it }
            }
        }
        return RosterRow("", cells, guessed, notes, line = first ?: 0).takeIf { cells.size >= MIN_CELLS }
    }
}
