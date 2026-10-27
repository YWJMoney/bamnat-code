package com.bamnat.core.pay

import com.bamnat.core.imports.RosterGeometry
import com.bamnat.core.imports.RosterWord
import java.util.IdentityHashMap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * [PHOTO-02] 1.29 AQ: the names a Korean payslip prints, for a name read a letter or two off (사용자 2026-10-27 "급여 명세서를
 * 사진으로 보낼 때 인식을 못 하고 있다"). [PayslipReader.roleOf] reads a name by what it holds (연장 → 연장근로수당); a name
 * whose letters the phone misread past that (연잔근로수당, 국민연굼, 장기오양) is matched to the nearest name here by edit
 * distance, one letter off for a short name and two for a long one. The match is a doubt: its line is yellow until the
 * payslip's own sums vouch for it ([SlipSums]).
 */
object SlipLexicon {
    val NAMES: List<String> = listOf(
        "기본급", "기본급여", "기본시급", "통상시급", "연장근로수당", "연장수당", "시간외수당", "시간외근무수당", "야간근로수당", "야간수당",
        "심야수당", "휴일근로수당", "휴일수당", "주휴수당", "특근수당", "잔업수당", "대근수당", "식대", "식비", "중식대", "교통비",
        "차량유지비", "자가운전보조금", "직책수당", "직무수당", "직급수당", "상여금", "성과급", "명절상여", "근속수당", "자격수당", "면허수당",
        "위험수당", "교대수당", "가족수당", "자녀수당", "육아수당", "보육수당", "연차수당", "통신비", "간호수당", "기술수당", "생산장려금",
        "고정연장수당", "정액급식비", "직급보조비", "봉급", "국민연금", "건강보험", "장기요양보험", "고용보험", "산재보험", "소득세",
        "근로소득세", "지방소득세", "주민세", "노동조합비", "조합비", "연말정산", "기숙사비", "일반기여금", "지급합계", "지급총액",
        "급여총액", "총지급액", "공제합계", "공제총액", "실지급액", "실수령액", "차인지급액", "차감지급액",
    )

    /** A name matched: the name meant, its kind, and how many letters were off (0: read as printed). */
    data class Hit(val name: String, val role: SlipRole, val distance: Int)

    fun of(raw: String): Hit? {
        val c = PayslipReader.ocrFix(raw.filter { it.isLetter() }.lowercase())
        if (c.isEmpty() || c.length > 14) return null
        if (PayslipReader.isHeader(c)) return null
        // A lone letter is a 계/합 only.
        if (c.length == 1) return PayslipReader.roleOf(c)?.takeIf { it == SlipRole.TOTAL }?.let { Hit(c, it, 0) }
        val exact = PayslipReader.roleOf(c)
        if (c in NAMES) return exact?.let { Hit(c, it, 0) }
        // A name a stroke off a printed one (연잔근로수당, 교퉁비, 자걱수당) is that one, even when roleOf found something in it
        // (…수당 → 수당): the name matters for matching the user's own allowances.
        val near = if (c.length >= 2 && (exact == null || exact == SlipRole.ALLOWANCE)) nearest(c) else null
        if (near != null) return near
        return exact?.let { Hit(c, it, 0) }
    }

    /**
     * The printed name [c] is a stroke or two off, compared letter part by letter part (초성·중성·종성): 교퉁비 (ㅗ→ㅜ) is 교통비,
     * but 기능수당 (three parts off 기술수당) stays a name of its own.
     */
    private fun nearest(c: String): Hit? {
        val limit = if (c.length <= 4) 1 else 2
        val cj = jamo(c)
        var best: String? = null
        var bestD = Int.MAX_VALUE
        for (n in NAMES) {
            if (abs(n.length - c.length) > 1) continue
            val d = distance(jamo(n), cj)
            if (d in 1..limit && d < bestD) { best = n; bestD = d }
        }
        val name = best ?: return null
        return PayslipReader.roleOf(name)?.let { Hit(name, it, bestD) }
    }

    /** A 한글 word as its letter parts (conjoining 초성·중성·종성); anything else as it is. */
    fun jamo(s: String): String = buildString {
        for (ch in s) {
            if (ch in '가'..'힣') {
                val v = ch - '가'
                append((0x1100 + v / 588).toChar()).append((0x1161 + v % 588 / 28).toChar())
                if (v % 28 != 0) append((0x11A7 + v % 28).toChar())
            } else append(ch)
        }
    }

    /** Levenshtein distance, letters as they are (a 한글 syllable is one letter). */
    fun distance(a: String, b: String): Int {
        val prev = IntArray(b.length + 1) { it }
        val cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                cur[j] = min(min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            }
            System.arraycopy(cur, 0, prev, 0, cur.size)
        }
        return prev[b.length]
    }
}

/**
 * [PHOTO-02] A number on a payslip as a phone reads it: "2,508,000", "2.508.000", "2,508,000원", "2,508,000 윈", "₩2,508,000",
 * "(12,000)", "-12,000", "△12,000" (taken off), "2,5O8,000" (O for 0), "1l0,000" (l for 1), "12.5"·"209시간"·"16h" (hours),
 * "22일"·"3회" (counts, not money). [fixed] says a letter was read as a digit, so the line is looked at.
 */
object SlipNumber {
    data class Num(
        val value: Long, val decimal: Double, val unit: String, val money: Boolean, val fixed: Boolean,
        /** "2,856,0": digits cut at the edge of the photo. */
        val cut: Boolean = false,
    ) {
        val hours: Boolean get() = unit in HOUR_UNITS
        val count: Boolean get() = unit in COUNT_UNITS
    }

    val HOUR_UNITS = setOf("시간", "hr", "h", "H", "분")
    private val COUNT_UNITS = setOf("일", "회", "%", "배", "개월", "년", "월", "개", "명", "호봉")
    private val UNIT = Regex("""(원|윈|웜|시간|hr|h|H|분|일|회|%|배|개월|년|월|개|명|호봉)$""")
    private val OCR_DIGITS = mapOf('O' to '0', 'o' to '0', 'D' to '0', 'l' to '1', 'I' to '1', '|' to '1', '!' to '1')

    fun parse(token: String): Num? {
        var t = token.trim().replace("₩", "").replace("￦", "").replace(" ", "")
        if (t.isEmpty()) return null
        var negative = false
        if (t.startsWith("(") && t.endsWith(")")) { negative = true; t = t.substring(1, t.length - 1) }
        if (t.first() in "-−–△▲") { negative = true; t = t.substring(1) }
        t = t.trimEnd(')').trimStart('(')
        val unit = UNIT.find(t)?.value.orEmpty()
        var body = t.removeSuffix(unit)
        if (body.isEmpty() || !body.first().isDigit() && body.first() !in OCR_DIGITS.keys) return null
        if (body.count { it.isDigit() } == 0) return null
        var fixed = false
        body = body.map { c -> OCR_DIGITS[c]?.also { fixed = true } ?: c }.joinToString("")
        if (!body.all { it.isDigit() || it == ',' || it == '.' }) return null
        val won = unit == "원" || unit == "윈" || unit == "웜"
        val sign = if (negative) -1 else 1
        return when {
            Regex("""\d{1,3}([,.]\d{3})+""").matches(body) -> {
                val v = body.filter { it.isDigit() }.toLong() * sign
                Num(v, v.toDouble(), if (won) "원" else unit, money = unit.isEmpty() || won, fixed = fixed)
            }
            Regex("""\d+""").matches(body) -> {
                val v = body.toLong() * sign
                Num(v, v.toDouble(), if (won) "원" else unit, money = won || unit.isEmpty() && abs(v) >= 1_000, fixed = fixed)
            }
            Regex("""\d+\.\d{1,2}""").matches(body) && !won -> {
                val d = body.toDouble() * sign
                Num(d.toLong(), d, unit, money = false, fixed = fixed)
            }
            // "2,856,0": the last digits off the photo; worked out by the sums, never taken as it is.
            Regex("""\d{1,3}(,\d{3})*,\d{1,2}""").matches(body) -> {
                val v = body.filter { it.isDigit() }.toLong() * sign
                Num(v, v.toDouble(), unit, money = true, fixed = true, cut = true)
            }
            else -> null
        }
    }
}

/**
 * [PHOTO-01] 1.29 AQ: a payslip photo read as the table it is, from where each word stands (사용자 2026-10-27 "급여 쪽은 발전이
 * 없다 … 프로 기능이라 실패 없는 정확성"). [OcrRows] put the words of a row in one line of text and [PayslipReader] took the
 * amount after each name: a table whose names and amounts sit in different rows (가로 머리줄: names in one row, amounts under
 * them; a name above its amount in each cell) or a row holding two or three pairs with a column of hours or of 산출근거 in
 * between gave amounts to the wrong names or none.
 *
 * Here each word is placed (the photo's tilt taken out, [RosterGeometry]), words make rows, and in a row each name takes the
 * nearest amount to its right before the next name; a column of hours (시간·시수 head, or "12.5", "16시간") gives the name its
 * hours instead; a 산출근거 (× and =) gives only what follows "=". A name with nothing to its right takes the amount right
 * under it (the first thing under it that is a number, never past another name). A 계 under 공제 names is 공제 합계.
 * Each line says what it was unsure of ([Result.unsure]): a name matched to the nearest printed name, a letter read as a
 * digit, an amount found under rather than beside its name. Plain geometry on the phone, nothing sent (D68).
 */
object SlipTable {
    private enum class Kind { NUM, LABEL, HEAD, TEXT, PUNCT }
    private enum class Col { HOURS, DAYS, RATE, AMOUNT, NOTE }

    private class Cell(
        var text: String, var x: Double, var y: Double, var w: Double, val h: Double,
    ) {
        var kind = Kind.TEXT
        var num: SlipNumber.Num? = null
        var hit: SlipLexicon.Hit? = null
        var col: Col? = null
        var used = false
        val left: Double get() = x - w / 2
        val right: Double get() = x + w / 2
    }

    private class Found(val label: Cell, val row: Int) {
        var amount: Long? = null
        var minutes: Int? = null
        var doubt = false
        var under = false
    }

    /** The payslip read from [words] (ML Kit elements or lines with their boxes), and the indexes of its lines to look at. */
    fun read(words: List<OcrLine>): SlipVote.Result {
        val rows = rows(words)
        if (rows.isEmpty()) return SlipVote.Result(PayslipRead(emptyList()), emptySet(), "")
        val text = rows.joinToString("\n") { r -> r.joinToString("  ") { it.text } }
        classify(rows)
        val found = mutableListOf<Found>()
        val heads = mutableListOf<Cell>()
        for ((ri, row) in rows.withIndex()) {
            row.filter { it.kind == Kind.HEAD && it.col != null }.forEach { h -> heads.removeAll { abs(it.x - h.x) < max(it.w, h.w) / 2 }; heads += h }
            var open: Found? = null
            var formula = false
            var afterEquals = false
            var prev: Cell? = null
            for (cell in row) {
                // A wide gap is a new column: a 산출근거 does not run into the amount column after it.
                if (prev != null && cell.left - prev.right > 1.6 * cell.h) { formula = false; afterEquals = false }
                prev = cell
                when (cell.kind) {
                    Kind.LABEL -> { open = Found(cell, ri).also { found += it }; formula = false; afterEquals = false }
                    Kind.HEAD -> { open = null; formula = false }
                    Kind.TEXT -> {
                        val t = cell.text
                        if (Regex("[×xX*]|곱").containsMatchIn(t) && t.length <= 3) formula = true
                        if ('=' in t) { formula = true; afterEquals = true }
                    }
                    Kind.PUNCT -> Unit
                    Kind.NUM -> {
                        val n = cell.num!!
                        val o = open ?: continue
                        val col = columnOf(cell, heads)
                        val hoursLabel = o.label.text.contains("시간") || o.label.text.contains("시수")
                        val hoursLike = n.hours || col == Col.HOURS || hoursLabel && !n.money ||
                            !n.money && n.unit.isEmpty() && n.decimal != n.value.toDouble() && abs(n.decimal) < 400
                        when {
                            hoursLike -> {
                                val minutes = if (n.unit == "분") n.decimal.roundToInt() else (n.decimal * 60).roundToInt()
                                if (o.minutes == null && minutes in 1..(744 * 60)) { o.minutes = minutes; cell.used = true }
                            }
                            n.count || col == Col.DAYS || col == Col.RATE || col == Col.NOTE -> Unit
                            formula && !afterEquals -> Unit
                            o.amount == null && (n.money || abs(n.value) >= 100 && n.unit.isEmpty()) -> {
                                o.amount = n.value; cell.used = true
                                if (n.fixed || !n.money) o.doubt = true
                                afterEquals = false
                            }
                        }
                    }
                }
            }
        }
        under(rows, found)
        totalsByColumn(rows, found)
        val lines = mutableListOf<SlipLine>()
        val unsure = HashSet<Int>()
        for (f in found) {
            val hit = f.label.hit ?: continue
            if (f.amount == null && f.minutes == null) continue
            val name = if (hit.distance > 0) hit.name else f.label.text
            if (f.doubt || hit.distance > 0 || f.under && f.label.text.length <= 1) unsure += lines.size
            lines += SlipLine(name, hit.role, f.amount, f.minutes, rows[f.row].joinToString("  ") { it.text })
        }
        val (merged, keep) = mergeHours(lines, unsure)
        return SlipVote.Result(PayslipRead(merged, PayslipReader.monthOf(text)), keep, text)
    }

    /** Hours read on a line of their own ("연장근로시간 16") join the amount line of the same kind, as [PayslipReader] does. */
    private fun mergeHours(lines: List<SlipLine>, unsure: Set<Int>): Pair<List<SlipLine>, Set<Int>> {
        val out = lines.toMutableList()
        val flags = lines.indices.map { it in unsure }.toMutableList()
        for (h in lines.filter { it.amount == null && it.minutes != null }) {
            val target = out.indexOfFirst { it.role == h.role && it.amount != null && it.minutes == null }
            val at = out.indexOf(h)
            if (target >= 0 && at >= 0) {
                out[target] = out[target].copy(minutes = h.minutes)
                out.removeAt(at); flags.removeAt(at)
            }
        }
        return out to flags.indices.filter { flags[it] }.toSet()
    }

    /** A name with no amount beside it takes the first number right under it, never past another name or a heading. */
    private fun under(rows: List<List<Cell>>, found: List<Found>) {
        for (f in found.filter { it.amount == null && it.minutes == null }) {
            val l = f.label
            for (ri in f.row + 1..min(rows.lastIndex, f.row + 3)) {
                val over = rows[ri].filter { c -> c.kind != Kind.PUNCT && overlaps(l, c) }
                val first = over.minByOrNull { abs(it.x - l.x) } ?: continue
                if (first.kind != Kind.NUM) break
                val n = first.num!!
                if (first.used || !n.money && !(abs(n.value) >= 100 && n.unit.isEmpty())) break
                f.amount = n.value; f.under = true; first.used = true
                if (n.fixed) f.doubt = true
                if (ri > f.row + 1) f.doubt = true
                break
            }
        }
    }

    private fun overlaps(label: Cell, c: Cell): Boolean {
        val reach = max(label.w, c.w) * 0.75
        return abs(label.x - c.x) <= reach || c.left < label.right && c.right > label.left
    }

    /** A plain 계·합계 at the foot of a column of 공제 names is 공제 합계; under 지급 names it is 지급 총액. */
    private fun totalsByColumn(rows: List<List<Cell>>, found: List<Found>) {
        for (f in found) {
            val hit = f.label.hit ?: continue
            if (hit.role != SlipRole.TOTAL) continue
            val above = found.filter { it.row < f.row && it.label.hit != null && overlaps(f.label, it.label) }.mapNotNull { it.label.hit?.role }
            val ded = above.count { it == SlipRole.DEDUCTION }
            val earn = above.count { it in SlipLine.EARNINGS }
            if (ded > earn) f.label.hit = hit.copy(role = SlipRole.DEDUCTION_TOTAL)
            else if (earn > ded) f.label.hit = hit.copy(role = SlipRole.GROSS)
        }
    }

    private fun columnOf(cell: Cell, heads: List<Cell>): Col? =
        heads.filter { abs(it.x - cell.x) <= max(it.w, cell.w) * 0.75 }.minByOrNull { abs(it.x - cell.x) }?.col

    // ---- placing the words ----

    private val PIECE = Regex(
        """[(\-−–△▲]?[₩￦]?[\dOoIl|]?\d[\dOoIl|,.]*(?:\s?(?:원|윈|웜|시간|hr|h|H|분|일|회|%|배|개월|년|월|개|명|호봉))?\)?|[^\s\d]+""",
    )

    /** How wide a letter is in a line's box, to place the pieces of a line read as one. */
    private fun width(c: Char): Double = when {
        c in '가'..'힣' || c.code > 0x2E80 -> 1.0
        c == ',' || c == '.' -> 0.3
        c == ' ' -> 0.35
        c.isLetterOrDigit() -> 0.58
        else -> 0.5
    }

    private fun rows(words: List<OcrLine>): List<MutableList<Cell>> {
        val pieces = mutableListOf<RosterWord>()
        for (w in words) {
            val t = w.text.replace(' ', ' ').replace('：', ':').replace('，', ',')
            if (t.isBlank()) continue
            val weights = t.map(::width)
            val total = weights.sum().coerceAtLeast(0.01)
            val span = (w.right - w.left).toDouble()
            val acc = DoubleArray(t.length + 1)
            for (i in t.indices) acc[i + 1] = acc[i] + weights[i]
            for (m in PIECE.findAll(t)) {
                val s = m.value.trim()
                if (s.isEmpty()) continue
                val a = w.left + span * acc[m.range.first] / total
                val b = w.left + span * acc[m.range.last + 1] / total
                pieces += RosterWord(s, a.roundToInt(), w.top, b.roundToInt().coerceAtLeast(a.roundToInt() + 1), w.bottom, angle = w.angle)
            }
        }
        if (pieces.isEmpty()) return emptyList()
        val geo = RosterGeometry
        val slopes = words.filter { it.angle != 0f && it.right - it.left > 3 * it.height }.map { it.angle.toDouble() }
        val tilt = if (slopes.size >= 3) geo.median(slopes) else geo.tilt(pieces)
        val placed = geo.level(pieces, tilt)
        val cells = IdentityHashMap<RosterWord, Cell>()
        placed.forEach { p -> cells[p.word] = Cell(p.text, p.x, p.y, p.w, p.h) }
        return geo.lines(placed).map { line -> joinRow(line.map { cells.getValue(it.word) }) }
    }

    /** Words of one cell put back together ("연장 근로수당", "2 508 000"); two names side by side stay two. */
    private fun joinRow(row: List<Cell>): MutableList<Cell> {
        val out = mutableListOf<Cell>()
        for (c in row) {
            val last = out.lastOrNull()
            if (last != null) {
                val gap = c.left - last.right
                val h = min(last.h, c.h).coerceAtLeast(1.0)
                val lastNum = SlipNumber.parse(last.text)
                val cNum = SlipNumber.parse(c.text)
                val bothWords = lastNum == null && cNum == null && !punct(last.text) && !punct(c.text)
                val digitsGroup = lastNum != null && cNum != null && lastNum.unit.isEmpty() && Regex("""\d{3}(원|윈)?""").matches(c.text) &&
                    !last.text.contains(',') && gap < 0.8 * h
                val wonAfter = lastNum != null && c.text in setOf("원", "윈", "웜") && gap < 1.2 * h
                val unitAfter = lastNum != null && lastNum.unit.isEmpty() && c.text in setOf("시간", "h", "H", "분", "일", "회", "%") && gap < 0.8 * h
                if (bothWords && gap < 1.4 * h && keepTogether(last.text, c.text) || digitsGroup || wonAfter || unitAfter) {
                    val l = min(last.left, c.left)
                    val r = max(last.right, c.right)
                    last.text = last.text + if (bothWords) " " + c.text else c.text
                    last.x = (l + r) / 2; last.w = r - l
                    continue
                }
            }
            out += c
        }
        return out
    }

    /** Two words make one name unless each is a name by itself and together they are not ("기본급 연장수당" in a row of heads). */
    private fun keepTogether(a: String, b: String): Boolean {
        val together = SlipLexicon.of(a + b)
        val ha = SlipLexicon.of(a)
        val hb = SlipLexicon.of(b)
        if (together != null && together.distance == 0 && (ha == null || hb == null || together.name.length >= 4 && (a + b).filter { it.isLetter() } in SlipLexicon.NAMES)) return true
        if (ha != null && hb != null) return false
        if (PayslipReader.isHeader(a.filter { it.isLetter() }) || PayslipReader.isHeader(b.filter { it.isLetter() })) return false
        return true
    }

    private fun punct(t: String) = t.isNotEmpty() && t.all { !it.isLetterOrDigit() }

    private fun classify(rows: List<List<Cell>>) {
        for (c in rows.flatten()) {
            val t = c.text
            val compact = t.filter { it.isLetter() }
            val n = SlipNumber.parse(t)
            when {
                n != null -> { c.kind = Kind.NUM; c.num = n }
                punct(t) && !t.contains('=') -> c.kind = Kind.PUNCT
                PayslipReader.isHeader(PayslipReader.ocrFix(compact)) || compact in SECTIONS -> {
                    c.kind = Kind.HEAD
                    val f = PayslipReader.ocrFix(compact)
                    c.col = when {
                        f.contains("시간") || f.contains("시수") -> Col.HOURS
                        f.contains("일수") || f == "일" -> Col.DAYS
                        f.contains("단가") || f.contains("기준") -> Col.RATE
                        f.contains("금액") || f.endsWith("액") -> Col.AMOUNT
                        f.contains("산출") || f.contains("계산") || f.contains("근거") || f.contains("비고") -> Col.NOTE
                        else -> null
                    }
                }
                else -> {
                    val hit = SlipLexicon.of(t)
                    if (hit != null) { c.kind = Kind.LABEL; c.hit = hit } else c.kind = Kind.TEXT
                }
            }
        }
    }

    private val SECTIONS = setOf("지급", "공제", "근태", "지급액", "공제액", "시간", "시수", "산출근거", "계산방법", "단가")
}

/**
 * [PHOTO-03] 1.29 AQ: a payslip's own sums check its numbers. 지급 항목을 더하면 지급 총액, 공제 항목을 더하면 공제 합계, 지급 − 공제 =
 * 실지급액. When one of them is off, a number the phone read a digit wrong (3↔8, 1↔7, 0↔6·8·9, 5↔6, a 0 lost or added) is
 * looked for: if exactly one such change makes the sums meet, that is taken and its line stays yellow so the user sees it;
 * if none or more than one would, nothing is changed. A number no reading gave is never made up.
 */
object SlipSums {
    private val LIKE = mapOf(
        '0' to "689", '1' to "7", '2' to "7", '3' to "85", '4' to "1", '5' to "63", '6' to "085", '7' to "12",
        '8' to "3069", '9' to "08",
    )

    /** Numbers [v] may have been before the phone misread one digit of it. */
    fun alternatives(v: Long): Set<Long> {
        if (v <= 0) return emptySet()
        val s = v.toString()
        val out = HashSet<Long>()
        for (i in s.indices) for (to in LIKE[s[i]].orEmpty()) {
            if (i == 0 && to == '0') continue
            out += (s.substring(0, i) + to + s.substring(i + 1)).toLong()
        }
        out += v * 10
        if (v % 10 == 0L) out += v / 10
        out -= v
        return out
    }

    data class Fixed(val read: PayslipRead, val unsure: Set<Int>, val changed: Set<Int>)

    fun repair(read: PayslipRead, unsure: Set<Int>): Fixed {
        var lines = read.lines
        val doubt = unsure.toMutableSet()
        val changed = HashSet<Int>()
        fun set(i: Int, v: Long) { lines = lines.toMutableList().also { it[i] = it[i].copy(amount = v) }; doubt += i; changed += i }
        val earnIdx = lines.indices.filter { lines[it].isEarning }
        val grossIdx = lines.indexOfFirst { (it.role == SlipRole.GROSS || it.role == SlipRole.TOTAL) && it.amount != null }
        val dedIdx = lines.indices.filter { lines[it].role == SlipRole.DEDUCTION && lines[it].amount != null }
        val dedTotIdx = lines.indexOfFirst { it.role == SlipRole.DEDUCTION_TOTAL && it.amount != null }
        val netIdx = lines.indexOfFirst { it.role == SlipRole.NET && it.amount != null }
        fun amt(i: Int) = lines[i].amount ?: 0
        // 지급: items against the stated total.
        if (grossIdx >= 0 && earnIdx.isNotEmpty()) {
            val sum = earnIdx.sumOf(::amt)
            val g = amt(grossIdx)
            if (sum != g) {
                val fixes = mutableListOf<Pair<Int, Long>>()
                for (i in earnIdx) for (alt in alternatives(amt(i))) if (sum - amt(i) + alt == g) fixes += i to alt
                // The total itself misread: the items, and 실지급 + 공제 when both are there, agree on another number.
                val other = if (netIdx >= 0 && dedTotIdx >= 0) amt(netIdx) + amt(dedTotIdx) else null
                if (sum in alternatives(g) && (other == null || other == sum)) fixes += grossIdx to sum
                if (fixes.size == 1) set(fixes[0].first, fixes[0].second)
            }
        }
        // 공제: items against the stated 공제 합계.
        if (dedTotIdx >= 0 && dedIdx.size >= 2) {
            val sum = dedIdx.sumOf(::amt)
            val d = amt(dedTotIdx)
            if (sum != d) {
                val fixes = mutableListOf<Pair<Int, Long>>()
                for (i in dedIdx) for (alt in alternatives(amt(i))) if (sum - amt(i) + alt == d) fixes += i to alt
                val other = if (netIdx >= 0 && grossIdx >= 0) amt(grossIdx) - amt(netIdx) else null
                if (sum in alternatives(d) && (other == null || other == sum)) fixes += dedTotIdx to sum
                if (fixes.size == 1) set(fixes[0].first, fixes[0].second)
            }
        }
        // 실지급 = 지급 − 공제.
        if (netIdx >= 0 && grossIdx >= 0 && dedTotIdx >= 0) {
            val want = amt(grossIdx) - amt(dedTotIdx)
            if (amt(netIdx) != want && want in alternatives(amt(netIdx))) set(netIdx, want)
        }
        val fixed = read.copy(lines = lines)
        // Lines the sums vouch for are not asked about; a line changed here always is.
        val vouched = vouched(fixed)
        return Fixed(fixed, (doubt - vouched) + changed, changed)
    }

    /** Lines whose amounts the payslip's own sums confirm to the won. */
    fun vouched(read: PayslipRead): Set<Int> {
        val ok = HashSet<Int>()
        val lines = read.lines
        if (SlipVote.stated(read) && SlipVote.adds(read)) lines.forEachIndexed { i, l -> if (l.isEarning || l.role == SlipRole.GROSS || l.role == SlipRole.TOTAL) ok += i }
        val dedTotal = lines.firstOrNull { it.role == SlipRole.DEDUCTION_TOTAL && it.amount != null }?.amount
        val dedSum = lines.filter { it.role == SlipRole.DEDUCTION && it.amount != null }.sumOf { it.amount!! }
        val dedOk = dedTotal != null && dedTotal == dedSum
        if (dedOk) lines.forEachIndexed { i, l -> if (l.role == SlipRole.DEDUCTION || l.role == SlipRole.DEDUCTION_TOTAL) ok += i }
        val g = read.gross
        if (dedOk && g != null && SlipVote.stated(read) && SlipVote.adds(read)) lines.forEachIndexed { i, l -> if (l.role == SlipRole.NET && l.amount == g - dedTotal!!) ok += i }
        return ok
    }
}

/**
 * [PHOTO-03] 1.29 AQ: one payslip photo read several ways ([com.bamnat.core.imports.PhotoVariants]), each reading taken both as
 * a table ([SlipTable], from where the words stand) and as text ([PayslipReader], from the rows put back together), the better
 * of the two kept for that reading, the readings put together line by line ([SlipVote]) and the result checked against the
 * payslip's own sums ([SlipSums]). What the app shows and asks about comes from here.
 */
object SlipReading {
    /** [readings]: the words of each reading with their boxes (one list per made-over picture). */
    fun read(readings: List<List<OcrLine>>): SlipVote.Result {
        val each = readings.filter { it.isNotEmpty() }.map { best(it) }
        val voted = SlipVote.mergeResults(each)
        val fixed = SlipSums.repair(voted.read, voted.unsure)
        return SlipVote.Result(fixed.read, fixed.unsure, voted.text)
    }

    /** The better of the table and the text reading of one picture. */
    fun best(words: List<OcrLine>): SlipVote.Result {
        val table = SlipTable.read(words)
        val text = OcrRows.join(words)
        val asText = PayslipReader.read(text)
        // A name a stroke off (교퉁비) put right, as the table does.
        val named = asText.lines.map { l -> SlipLexicon.of(l.name)?.takeIf { it.distance > 0 && it.role == l.role }?.let { l.copy(name = it.name) } ?: l }
        val plain = SlipVote.Result(asText.copy(lines = named), emptySet(), text)
        val pick = if (score(plain.read) > score(table.read)) plain else table
        // The text the user sees and rules are read from is the rows put back together either way.
        return pick.copy(read = settleTotals(pick.read), text = text)
    }

    /**
     * A plain 계·합계 told by what it adds up: the 지급 items' sum is 지급 총액, the 공제 items' sum is 공제 합계, so readings of
     * one payslip name their totals alike and are put together line by line.
     */
    fun settleTotals(r: PayslipRead): PayslipRead {
        if (r.lines.none { it.role == SlipRole.TOTAL }) return r
        val earn = r.earningsSum
        val ded = r.lines.filter { it.role == SlipRole.DEDUCTION && it.amount != null }.sumOf { it.amount!! }
        var gross = r.lines.any { it.role == SlipRole.GROSS && it.amount != null }
        var dedTotal = r.lines.any { it.role == SlipRole.DEDUCTION_TOTAL && it.amount != null }
        val lines = r.lines.map { l ->
            val a = l.amount
            when {
                l.role != SlipRole.TOTAL || a == null -> l
                !gross && earn > 0 && a == earn -> { gross = true; l.copy(role = SlipRole.GROSS) }
                !dedTotal && ded > 0 && a == ded -> { dedTotal = true; l.copy(role = SlipRole.DEDUCTION_TOTAL) }
                !gross && earn > 0 && a >= earn -> { gross = true; l.copy(role = SlipRole.GROSS) }
                !dedTotal && ded > 0 && a < earn -> { dedTotal = true; l.copy(role = SlipRole.DEDUCTION_TOTAL) }
                else -> l
            }
        }
        return r.copy(lines = lines)
    }

    /** Lines that add up to a stated total first, then the stated total meeting the items within rounding, then amounts read. */
    fun score(r: PayslipRead): Int {
        val amounts = r.lines.count { it.amount != null && (it.isEarning || it.role in TOTALS || it.role == SlipRole.DEDUCTION) }
        val adds = if (SlipVote.adds(r) && SlipVote.stated(r)) 1000 else 0
        val near = if (r.consistent) 200 else 0
        val net = if (r.net != null && r.lines.any { it.role == SlipRole.NET && it.amount == r.net }) 50 else 0
        val ded = r.lines.firstOrNull { it.role == SlipRole.DEDUCTION_TOTAL }?.amount
        val dedOk = if (ded != null && ded == r.lines.filter { it.role == SlipRole.DEDUCTION && it.amount != null }.sumOf { it.amount!! }) 100 else 0
        return adds + near + net + dedOk + amounts
    }

    private val TOTALS = setOf(SlipRole.GROSS, SlipRole.TOTAL, SlipRole.DEDUCTION_TOTAL, SlipRole.NET)
}
