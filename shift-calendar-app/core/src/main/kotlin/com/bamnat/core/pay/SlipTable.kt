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
        // [PHOTO-22] 1.30: names of the 화학 공장 명세서 (R3 A-2) a photo reads a stroke off.
        "향군수당", "사택관리비", "소급분", "휴일연장수당", "야간근무수당", "평일연장수당", "교육수당", "당직수당",
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
            // [PHOTO-12] "2397,500": a comma lost (화학 공장 명세서 사진의 모아레); money, looked at.
            Regex("""\d{4,6}([,.]\d{3})+""").matches(body) && (unit.isEmpty() || won) -> {
                val v = body.filter { it.isDigit() }.toLong() * sign
                Num(v, v.toDouble(), if (won) "원" else unit, money = true, fixed = true)
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
        /** [PHOTO-12] A name no list holds, taken as a line because it stands in the 지급 or 공제 column: always looked at. */
        var guessed = false
        val left: Double get() = x - w / 2
        val right: Double get() = x + w / 2
    }

    private class Found(val label: Cell, val row: Int) {
        var amount: Long? = null
        /** [PHOTO-24] The cell the amount was read from. */
        var at: Cell? = null
        var minutes: Int? = null
        var doubt = false
        var under = false
    }

    /**
     * [PHOTO-24] Where a line read stood on the photo (levelled pixels): the middle of its name, of its amount (null for a line
     * of hours only), and its letters' height.
     */
    data class Spot(val x: Double, val y: Double, val ax: Double?, val ay: Double?, val h: Double)

    /** [PHOTO-24] A reading and where each of its lines stood ([spots], one per line). */
    class Laid(val result: SlipVote.Result, val spots: List<Spot?>)

    /**
     * The payslip read from [words] (ML Kit elements or lines with their boxes), and the indexes of its lines to look at.
     * [PHOTO-24] 1.30: with the company's [template] (the payslip the user confirmed before), lines the photo lost are looked
     * for where the template says, and the lines come in its order.
     */
    fun read(words: List<OcrLine>, template: SlipTemplate? = null): SlipVote.Result = laid(words, template).result

    /** [read], with where each line stood. */
    fun laid(words: List<OcrLine>, template: SlipTemplate?): Laid {
        val rows = rows(words)
        if (rows.isEmpty()) return Laid(SlipVote.Result(PayslipRead(emptyList()), emptySet(), ""), emptyList())
        val text = rows.joinToString("\n") { r -> r.joinToString("  ") { it.text } }
        classify(rows)
        sections(rows)
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
                        // [PHOTO-12] Under a 시간 head of a table above (총근로시간 209), 2,200,000원 is still money.
                        val hoursLike = n.hours || col == Col.HOURS && !(n.money && abs(n.value) >= 1_000) || hoursLabel && !n.money ||
                            !n.money && n.unit.isEmpty() && n.decimal != n.value.toDouble() && abs(n.decimal) < 400
                        when {
                            hoursLike -> {
                                val minutes = if (n.unit == "분") n.decimal.roundToInt() else (n.decimal * 60).roundToInt()
                                if (o.minutes == null && minutes in 1..(744 * 60)) { o.minutes = minutes; cell.used = true }
                            }
                            n.count || col == Col.DAYS || col == Col.RATE || col == Col.NOTE -> Unit
                            formula && !afterEquals -> Unit
                            o.amount == null && (n.money || abs(n.value) >= 100 && n.unit.isEmpty()) -> {
                                o.amount = n.value; cell.used = true; o.at = cell
                                if (n.fixed || !n.money) o.doubt = true
                                afterEquals = false
                            }
                        }
                    }
                }
            }
        }
        under(rows, found)
        beside(rows, found)
        totalsByColumn(rows, found)
        if (template != null) fromTemplate(rows, found, template)
        val lines = mutableListOf<SlipLine>()
        val spots = mutableListOf<Spot?>()
        val unsure = HashSet<Int>()
        for (f in found) {
            val hit = f.label.hit ?: continue
            if (f.amount == null && f.minutes == null) continue
            val name = if (hit.distance > 0) hit.name else f.label.text
            if (f.doubt || hit.distance > 0 || f.label.guessed || f.under && f.label.text.length <= 1) unsure += lines.size
            lines += SlipLine(name, hit.role, f.amount, f.minutes, rows[f.row].joinToString("  ") { it.text })
            spots += Spot(f.label.x, f.label.y, f.at?.x, f.at?.y, f.label.h)
        }
        val (merged, keep, kept) = mergeHours(lines, unsure)
        var out = attendance(rows, merged)
        var flags = keep
        var places = kept.map { spots[it] }
        if (template != null) {
            val ordered = inOrder(out, template)
            flags = ordered.indices.filter { ordered[it] in flags }.toSet()
            places = ordered.map { places[it] }
            out = ordered.map { out[it] }
        }
        return Laid(SlipVote.Result(PayslipRead(out, PayslipReader.monthOf(text)), flags, text), places)
    }

    /**
     * [PHOTO-24] The lines' indexes in the template's order: a line the template has goes where the template has it; a line it
     * has not stays after the line it followed on the photo.
     */
    internal fun inOrder(lines: List<SlipLine>, template: SlipTemplate): List<Int> {
        val order = template.lines.map { SlipTemplate.key(it.name) }
        var last = -1.0
        val rank = lines.mapIndexed { i, l ->
            val at = order.indexOf(SlipTemplate.key(l.name))
            val r = if (at >= 0) at.toDouble() else last + 0.001 * (i + 1)
            if (at >= 0) last = at.toDouble()
            r
        }
        return lines.indices.sortedWith(compareBy<Int> { rank[it] }.thenBy { it })
    }

    /**
     * [PHOTO-24] 1.30 AV: a line the template has and this photo lost. Where the template's lines and this photo's agree, the
     * template is laid on the photo ([TemplateMap]); at the place of a lost line, a name read there that was no line (other
     * letters, no list holds it, no head over it) becomes that line, with the amount at the template's amount place (or
     * beside it as read); with no name read there at all, an unused amount at the template's amount place is that line's.
     * Always looked at ("지난달 명세서 틀로 찾았어요"): never silently.
     */
    private fun fromTemplate(rows: List<List<Cell>>, found: MutableList<Found>, template: SlipTemplate) {
        val have = found.filter { it.label.hit != null && (it.amount != null || it.minutes != null) }
        val byKey = have.associateBy { SlipTemplate.key(it.label.hit!!.let { h -> if (h.distance > 0) h.name else it.label.text }) }
        val pairs = template.lines.mapNotNull { t -> byKey[SlipTemplate.key(t.name)]?.let { t to (it.label.x to it.label.y) } }
        if (pairs.size < 3) return
        val map = TemplateMap(pairs)
        val all = rows.flatten()
        val h = RosterGeometry.median(all.map { it.h })
        for (t in template.lines) {
            if (SlipTemplate.key(t.name) in byKey) continue
            if (t.ax == null || t.ay == null) continue
            val (nx, ny) = map.at(t.x, t.y) ?: continue
            val (ax, ay) = map.at(t.ax, t.ay) ?: continue
            val near = 1.5 * h
            val amount = all.filter { c -> c.kind == Kind.NUM && !c.used && c.num?.money == true && c.num?.cut == false && abs(c.x - ax) < 3 * near && abs(c.y - ay) < near }
                .minByOrNull { abs(it.y - ay) * 3 + abs(it.x - ax) } ?: continue
            // The name's place: a cell read there that is no line yet (its letters off, or no list holds them).
            val name = all.firstOrNull { c -> c.kind != Kind.NUM && c.kind != Kind.HEAD && abs(c.x - nx) < max(near, c.w / 2) && abs(c.y - ny) < near &&
                found.none { it.label === c && (it.amount != null || it.minutes != null) } }
            // Another line's name nearer the amount than the template's place: the amount is that line's.
            if (have.any { o -> abs(o.label.y - amount.y) < abs(ny - amount.y) - 0.5 * h && abs(o.label.x - nx) < 3 * near }) continue
            val label = name ?: Cell(t.name, nx, ny, t.name.length * h, h)
            label.kind = Kind.LABEL; label.guessed = true
            label.text = t.name
            label.hit = SlipLexicon.Hit(t.name, t.role, 0)
            val row = rows.indexOfFirst { r -> r.any { it === amount } }.coerceAtLeast(0)
            found.removeAll { it.label === label }
            found += Found(label, row).also { f ->
                f.amount = amount.num!!.value; f.at = amount; f.doubt = true
            }
            amount.used = true
        }
    }

    /**
     * Hours read on a line of their own ("연장근로시간 16") join the amount line of the same kind, as [PayslipReader] does.
     * The lines, those to look at, and ([PHOTO-24]) the index each line kept had before.
     */
    private fun mergeHours(lines: List<SlipLine>, unsure: Set<Int>): Triple<List<SlipLine>, Set<Int>, List<Int>> {
        val out = lines.toMutableList()
        val from = lines.indices.toMutableList()
        val flags = lines.indices.map { it in unsure }.toMutableList()
        for (h in lines.filter { it.amount == null && it.minutes != null }) {
            val target = out.indexOfFirst { it.role == h.role && it.amount != null && it.minutes == null }
            val at = out.indexOf(h)
            if (target >= 0 && at >= 0) {
                out[target] = out[target].copy(minutes = h.minutes)
                out.removeAt(at); flags.removeAt(at); from.removeAt(at)
            }
        }
        return Triple(out, flags.indices.filter { flags[it] }.toSet(), from)
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
                f.amount = n.value; f.under = true; first.used = true; f.at = first
                if (n.fixed) f.doubt = true
                if (ri > f.row + 1) f.doubt = true
                break
            }
        }
    }

    /**
     * [PHOTO-12] 1.29 AQ: a name whose amount stands in a column of its own to the right, up to two rows lower (화학 공장 명세서
     * 사진, 사용자 2026-10-27: 금액 칸이 이름 칸 오른쪽에 반 줄 아래로 따로 있어 기본급I·총지급액이 빠짐). A name with no amount
     * yet takes the nearest unused amount to its right from its own height down to about two rows lower, when no other name
     * stands between them and the amount is not right under another name. Found more than a row lower, the line is looked at.
     */
    private fun beside(rows: List<List<Cell>>, found: List<Found>) {
        val all = rows.flatten()
        val labels = found.map { it.label }
        for (f in found.filter { it.amount == null && it.minutes == null }) {
            val l = f.label
            // A head of hours (야간근로시간) holds hours, never money.
            if (l.text.contains("시간") || l.text.contains("시수")) continue
            // [PHOTO-21] 1.30: how far lower the amount may sit is told by the table's own rows (the names' spacing in this
            // column), not by the letters' height: a photo taken at an angle spreads the far rows and the amount half a row
            // lower went past 2.2 letters (R3 A-1 #1).
            val gap = rowGap(l, labels)
            val reach = if (gap != null) max(1.2 * l.h, 0.9 * gap) else null
            val pick = all.filter { c ->
                val n = c.num
                if (c.kind != Kind.NUM || c.used || n == null || !n.money || n.cut || c.left <= l.right) return@filter false
                val h = max(l.h, c.h)
                val dy = c.y - l.y
                if (dy < -0.5 * h || dy > (reach ?: (2.2 * h))) return@filter false
                // Another name between them, at the name's height or the amount's.
                val between = labels.any { o -> o !== l && o.x > l.right && o.right < c.left && (abs(o.y - c.y) < h || abs(o.y - l.y) < 0.6 * h) }
                // The amount right under another name is that name's.
                val underOther = labels.any { o -> o !== l && overlaps(o, c) && c.y > o.y && c.y - o.y < 3 * h }
                // A row of names or heads between them is another table's.
                val across = all.any { o -> (o.kind == Kind.LABEL || o.kind == Kind.HEAD) && o !== l && o.y > l.y + 0.5 * h && o.y < c.y - 0.5 * h &&
                    o.right > l.left && o.left < c.right }
                // An amount with a name of its own to its left in its row is that name's.
                val ownRow = labels.any { o -> o !== l && abs(o.y - c.y) < 0.6 * h && o.right < c.left && abs(c.y - l.y) > 0.6 * h }
                !between && !underOther && !across && !ownRow
            }.minWithOrNull(compareBy<Cell> { abs(it.y - l.y) }.thenBy { it.left - l.right }) ?: continue
            val n = pick.num!!
            f.amount = n.value; pick.used = true; f.at = pick
            if (n.fixed || abs(pick.y - l.y) > 0.9 * max(l.h, pick.h)) f.doubt = true
        }
    }

    /**
     * [PHOTO-21] The spacing of the rows around name [l]: the middle step between the names one under another in its
     * column, near it (its own six neighbours, so a photo spread at the bottom measures the bottom). null with fewer than
     * two steps.
     */
    private fun rowGap(l: Cell, labels: List<Cell>): Double? {
        val column = labels.filter { o -> o.left < l.right + l.h && o.right > l.left - l.h }.sortedBy { it.y }
        val near = column.sortedBy { abs(it.y - l.y) }.take(7).sortedBy { it.y }
        val steps = near.zipWithNext { a, b -> b.y - a.y }.filter { it > 0.8 * l.h && it < 6 * l.h }
        if (steps.size < 2) return null
        return RosterGeometry.median(steps)
    }

    /**
     * [PHOTO-12] 1.29 AQ: the 지급 and 공제 columns say what a name is (화학 공장 명세서: 볼링회·축구회·노동조합·사택관리비·소급분이
     * 공제내역 아래, 위험예지훈련/LMS가 지급내역 아래). A name under a 공제 head is 공제 even when it reads like a 수당 (…관리비); a
     * name no list holds, under a 지급 or 공제 head, is a line of that side, looked at. A column ends at its 계.
     */
    private fun sections(rows: List<List<Cell>>) {
        val all = rows.flatten()
        val heads = all.filter { it.kind == Kind.HEAD }.mapNotNull { c ->
            val f = PayslipReader.ocrFix(c.text.filter { it.isLetter() })
            when {
                f.startsWith("공제") && !f.contains("합계") && !f.contains("총") -> c to true
                f.startsWith("지급") && !f.contains("일") && !f.contains("합계") && !f.contains("총") -> c to false
                else -> null
            }
        }
        val totals = setOf(SlipRole.GROSS, SlipRole.TOTAL, SlipRole.DEDUCTION_TOTAL, SlipRole.NET)
        fun inColumn(h: Cell, c: Cell) = c.left < h.right + h.h && c.right > h.left - h.h
        fun candidate(c: Cell) = c.kind == Kind.LABEL && c.hit?.role == SlipRole.ALLOWANCE || c.kind == Kind.TEXT && nameLike(c.text)
        fun take(c: Cell, deduction: Boolean) {
            when {
                c.kind == Kind.TEXT -> {
                    c.kind = Kind.LABEL; c.guessed = true
                    c.hit = SlipLexicon.Hit(c.text, if (deduction) SlipRole.DEDUCTION else SlipRole.ALLOWANCE, 0)
                }
                deduction -> { c.hit = c.hit!!.copy(role = SlipRole.DEDUCTION); c.guessed = true }
            }
        }
        val placed = HashSet<Cell>()
        for (c in all) {
            if (!candidate(c)) continue
            val (head, deduction) = heads.filter { (h, _) -> h.y < c.y && inColumn(h, c) }.maxByOrNull { it.first.y } ?: continue
            // Past the column's 계 it is another table.
            if (all.any { o -> o.kind == Kind.LABEL && o.hit?.role in totals && o.y > head.y && o.y < c.y - 0.5 * c.h && inColumn(head, o) }) continue
            take(c, deduction)
            placed += c
        }
        // [PHOTO-22] 1.30: no head over it (지급내역 not read, or read as other letters): the column of names it stands in says.
        for ((side, column) in columns(all)) for (c in all) {
            if (c in placed || !candidate(c) || !column.holds(c)) continue
            take(c, side)
        }
    }

    /**
     * [PHOTO-22] 1.30 AV: a column of names says what its names are, with no 지급내역·공제내역 head (사용자 2026-10-27 "사진마다 읽는
     * 부분이 다르다", R3 A-1 #5: the head read as 지금내 역 or not at all, and 위험예지훈련/LMS·볼링회·사택관리비 were gone). Names the
     * lists know (소득세·건강보험 … or 기본급·자격수당 …) that stand one under another, three or more, are a column; when most of
     * them are 공제 the column is 공제, when most are 지급 it is 지급 (true: 공제). A name no list holds in that column, between
     * its first name and its 계, is a line of that side, looked at ([Cell.guessed]).
     */
    private fun columns(all: List<Cell>): List<Pair<Boolean, Column>> {
        val totals = setOf(SlipRole.GROSS, SlipRole.TOTAL, SlipRole.DEDUCTION_TOTAL, SlipRole.NET)
        val known = all.filter { it.kind == Kind.LABEL && !it.guessed && it.hit?.role.let { r -> r != null && r !in totals && r != SlipRole.HOURLY } }
        if (known.size < 3) return emptyList()
        // Names one under another: their spans overlap (a letter's height of slack), joined by overlap.
        val groups = mutableListOf<MutableList<Cell>>()
        for (c in known.sortedBy { it.y }) {
            val g = groups.firstOrNull { g -> g.any { o -> c.left < o.right + o.h && c.right > o.left - o.h } }
            if (g != null) g += c else groups += mutableListOf(c)
        }
        // Groups that overlap each other after growing are one column.
        var merged = true
        while (merged) {
            merged = false
            loop@ for (i in groups.indices) for (j in i + 1 until groups.size) {
                if (groups[i].any { a -> groups[j].any { b -> a.left < b.right + b.h && a.right > b.left - b.h } }) {
                    groups[i] += groups[j]; groups.removeAt(j); merged = true; break@loop
                }
            }
        }
        val out = mutableListOf<Pair<Boolean, Column>>()
        for (g in groups) {
            if (g.size < 3) continue
            val ded = g.count { it.hit?.role == SlipRole.DEDUCTION }
            val earn = g.count { it.hit?.role in SlipLine.EARNINGS }
            val side = when {
                ded >= 2 && ded >= 2 * earn -> true
                earn >= 2 && earn >= 2 * ded -> false
                else -> continue
            }
            val ys = g.map { it.y }.sorted()
            val gap = ys.zipWithNext { a, b -> b - a }.filter { it > 0.8 * g.first().h }.let { if (it.isEmpty()) 2.0 * g.first().h else RosterGeometry.median(it) }
            val top = ys.first() - 1.5 * gap
            // The column ends at its 계 (지급계·공제계) under its first name, or a row and a half under its last.
            val foot = all.filter { o -> o.kind == Kind.LABEL && o.hit?.role in totals && o.y > ys.first() && g.any { it.left < o.right + o.h && it.right > o.left - o.h } }
                .minOfOrNull { it.y } ?: (ys.last() + 1.5 * gap)
            out += side to Column(g.minOf { it.left }, g.maxOf { it.right }, top, foot, g.first().h)
        }
        return out
    }

    /** [PHOTO-22] Where a column of names stands: its span across, from [top] down to (not past) its [foot]. */
    private class Column(val left: Double, val right: Double, val top: Double, val foot: Double, val h: Double) {
        fun holds(c: Cell): Boolean = c.left < right + h && c.right > left - h && c.y > top && c.y < foot - 0.5 * c.h
    }

    /** A cell that reads as a name of pay: a few words with two 한글 letters or more, no clock or sentence. */
    private fun nameLike(t: String): Boolean {
        val hangul = t.count { it in '가'..'힣' }
        if (hangul < 2 || t.length > 24 || t.count { it == ' ' } > 3) return false
        if (t.any { it.isDigit() } || ':' in t || t.endsWith("다") || t.endsWith("요")) return false
        return !PayslipReader.isHeader(PayslipReader.ocrFix(t.filter { it.isLetter() }))
    }

    /**
     * [PHOTO-12] 1.29 AQ: a 근태 table puts its heads on one row (휴일근로시간수, 야간근로시간수, 향군수당 시간수) and the hours on
     * the row under them (화학 공장 명세서). Each value goes to the line of pay its head names: the head and the line agree once
     * 시간수·근로·근무·수당 are taken off (휴일근로시간수 → 휴일 → 휴일근로수당, 휴일연장근로시간수 → 휴일연장 → 휴일연장수당).
     */
    private fun attendance(rows: List<List<Cell>>, lines: List<SlipLine>): List<SlipLine> {
        val hourHead = Regex("(시간수|시간|시수)$")
        val anyHead = Regex("(시간수|시간|시수|일수)$")
        fun letters(t: String) = PayslipReader.ocrFix(t.filter { it.isLetter() })
        fun stem(t: String) = letters(t).replace(hourHead, "").replace("수당", "").replace("근로", "").replace("근무", "")
        val all = rows.flatten()
        // A letter's height on this page: one box read short is not the page's.
        val line = all.map { it.h }.sorted().let { it[it.size / 2] }
        // A head of the table: it ends in 시간수, or its row holds two heads or more (휴일근로시간 · 야간근로시간).
        val tableRows = rows.filter { r -> r.count { it.kind != Kind.NUM && anyHead.containsMatchIn(letters(it.text)) && letters(it.text).length >= 3 } >= 2 }.flatten().toSet()
        var out = lines
        for (v in all) {
            val n = v.num ?: continue
            if (v.used || v.kind != Kind.NUM || n.unit.isNotEmpty() && !n.hours) continue
            if (n.money && n.decimal == n.value.toDouble() && abs(n.value) >= 1_000) continue
            // The cell right above it, in its column, within three rows.
            val above = all.filter { o -> o !== v && o.kind != Kind.PUNCT && o.y < v.y - 0.3 * v.h && v.y - o.y < 3.5 * maxOf(o.h, v.h, line) && abs(o.x - v.x) <= max(o.w, v.w) * 0.6 }
                .maxByOrNull { it.y } ?: continue
            val t = letters(above.text)
            if (above.kind == Kind.NUM || !hourHead.containsMatchIn(t) || !(t.endsWith("시간수") || above in tableRows)) continue
            val minutes = (n.decimal * 60).roundToInt()
            if (minutes !in 1..(744 * 60)) continue
            val s = stem(above.text)
            if (s.isEmpty()) continue
            val at = out.indexOfFirst { it.amount != null && stem(it.name) == s }
            if (at >= 0) out = out.toMutableList().also { it[at] = it[at].copy(minutes = minutes) }
        }
        return out
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

    // [PHOTO-27] 1.30: ",500" (an amount cut at its comma) is one piece, put back with the digits before it ([joinRow]); a
    // date ("2026-10-25") is one piece too, never 2026 and two minus amounts.
    private val PIECE = Regex(
        """\d{4}[./-]\d{1,2}[./-]\d{1,2}\.?|[(\-−–△▲]?[₩￦]?(?:[,.](?=\d{3}))?[\dOoIl|]?\d[\dOoIl|,.]*(?:\s?(?:원|윈|웜|시간|hr|h|H|분|일|회|%|배|개월|년|월|개|명|호봉))?\)?|[^\s\d]+""",
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
        for (w in OcrRows.unmarked(words)) {
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
        // [PHOTO-21] 1.30: each row levelled by its own slant (a page taken from one side), from the slopes the reader gave.
        val slant = geo.slant(words.map { RosterWord(it.text, it.left, it.top, it.right, it.bottom, angle = it.angle) })
        // [PHOTO-12] With no slopes given, the tilt is found from the names: an amount column sitting half a row lower to the
        // right stacks with the names as well at a slant as level, and the slant was taken (a level screenshot read 2° off).
        val placed = if (slant != null) geo.level(pieces, slant)
            else geo.level(pieces, geo.tilt(pieces.filter { SlipNumber.parse(it.text) == null }.takeIf { it.size >= 6 } ?: pieces))
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
                // [PHOTO-12] "볼링회 ( 남부공장 )": the brackets of a name read as words of their own.
                val bracket = gap < 1.4 * h && (c.text == "(" && lastNum == null && last.text.any { it.isLetter() } ||
                    c.text == ")" && last.text.contains('(') && !last.text.contains(')') ||
                    last.text.endsWith("(") && cNum == null && c.text.any { it.isLetter() })
                // [PHOTO-27] 1.30: an amount the reader cut at a comma ("2,397," "500", "2,397" ",500"), or read its comma as a
                // hair of space ("2,397" "500" touching): one amount again.
                // A 0 read as O ("2,397" ",50O") or a comma lost before the cut ("2397" ",500") is still a piece of the amount.
                val a = digitish(last.text)
                val b = digitish(c.text)
                val amountCut = gap < 0.6 * h && (Regex("""\d{1,7}([,.]\d{3})*[,.]""").matches(a) && Regex("""\d{3}([,.]\d{3})*(원|윈)?""").matches(b) ||
                    Regex("""\d{1,7}([,.]\d{3})*""").matches(a) && Regex("""[,.]\d{3}([,.]\d{3})*(원|윈)?""").matches(b) ||
                    gap < 0.35 * h && Regex("""\d{1,3}([,.]\d{3})+""").matches(a) && Regex("""\d{3}(원|윈)?""").matches(b))
                // [PHOTO-27] One word the reader cut in two that touch ("소급" "분(지급)", "남부_사택관" "리비"): one word, even when a
                // piece looks like a name of its own; two names printed side by side have a space between them.
                val touching = bothWords && gap <= 0.15 * h && !(SlipLexicon.of(last.text)?.distance == 0 && SlipLexicon.of(c.text)?.distance == 0 &&
                    SlipLexicon.of(last.text + c.text) == null)
                // [PHOTO-27] A piece no name ends like ("소급" "분(지급)", "남부_사택" "관리비" a little apart): the rest of it follows.
                if (touching || bothWords && gap < 0.6 * h && fragment(last.text) && hangul(c.text)) {
                    val l = min(last.left, c.left)
                    val r = max(last.right, c.right)
                    last.text = last.text + c.text
                    last.x = (l + r) / 2; last.w = r - l
                    continue
                }
                if (bracket || amountCut) {
                    val l = min(last.left, c.left)
                    val r = max(last.right, c.right)
                    val glue = if (amountCut && last.text.last().isDigit() && c.text.first().isDigit()) "," else ""
                    last.text = last.text + glue + c.text
                    last.x = (l + r) / 2; last.w = r - l
                    continue
                }
                if (bothWords && gap < 1.4 * h && keepTogether(last.text, c.text) || digitsGroup || wonAfter || unitAfter) {
                    val l = min(last.left, c.left)
                    val r = max(last.right, c.right)
                    // [PHOTO-27] Pieces of one word the reader cut ("기본급" "I", "위험예지훈련" "/LMS") touch: no space between.
                    last.text = last.text + if (bothWords && gap > 0.15 * h) " " + c.text else c.text
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

    private fun hangul(t: String) = t.any { it in '가'..'힣' }

    /**
     * [PHOTO-27] The front of a word the reader cut: 한글, not a name of its own, and not ending as names of three letters or
     * more end (…수당, …급, …비): "소급", "남부_사택", "국민연" are; "기본급", "식대", "연장수당" are not.
     */
    private fun fragment(t: String): Boolean {
        val c = t.filter { it.isLetter() }
        if (!hangul(c) || c in SlipLexicon.NAMES || PayslipReader.isHeader(c)) return false
        return !(c.length >= 3 && NAME_ENDS.any { c.endsWith(it) })
    }

    private val NAME_ENDS = listOf("수당", "급", "비", "금", "료", "세", "험", "액", "계", "분", "대", "여")

    /** An amount's piece with the letters a reader takes for digits put back (O→0, l·I·|→1), for telling the pieces only. */
    private fun digitish(t: String): String =
        if (t.count { it.isDigit() } * 2 >= t.count { it.isLetterOrDigit() }) t.replace('O', '0').replace('o', '0').replace('l', '1').replace('I', '1').replace('|', '1') else t

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
        // [PHOTO-27] 1.30: a payslip stating a total twice (공제총액 at the head, 공제계 at the foot) with the two read apart: the
        // one the items do not add up to is looked at (the foot's "3,220" for 3,220,588 was taken silently).
        val earnSum = earnIdx.sumOf(::amt)
        val dedSum = dedIdx.sumOf(::amt)
        lines.forEachIndexed { i, l ->
            val a = l.amount ?: return@forEachIndexed
            if ((l.role == SlipRole.GROSS || l.role == SlipRole.TOTAL) && i != grossIdx && grossIdx >= 0 && a != amt(grossIdx) && a != earnSum && a != dedSum) doubt += i
            if (l.role == SlipRole.DEDUCTION_TOTAL && i != dedTotIdx && dedTotIdx >= 0 && a != amt(dedTotIdx) && a != dedSum) doubt += i
        }
        // A total under the items read is misread (items can be lost from a photo, never added).
        if (grossIdx >= 0 && amt(grossIdx) < earnSum) doubt += grossIdx
        if (dedTotIdx >= 0 && amt(dedTotIdx) < dedSum) doubt += dedTotIdx
        val fixed = read.copy(lines = lines)
        // Lines the sums vouch for are not asked about; a line changed here always is.
        val vouched = vouched(fixed)
        return Fixed(fixed, (doubt - vouched) + changed, changed)
    }

    /** Lines whose amounts the payslip's own sums confirm to the won. */
    fun vouched(read: PayslipRead): Set<Int> {
        val ok = HashSet<Int>()
        val lines = read.lines
        // [PHOTO-27] 1.30: a total vouched for only when it is the sum (a payslip stating its 공제 합계 twice, at the head and the
        // foot, had the foot's misread one vouched for by the head's).
        if (SlipVote.stated(read) && SlipVote.adds(read)) lines.forEachIndexed { i, l ->
            if (l.isEarning || (l.role == SlipRole.GROSS || l.role == SlipRole.TOTAL) && l.amount == read.earningsSum) ok += i
        }
        val dedTotal = lines.firstOrNull { it.role == SlipRole.DEDUCTION_TOTAL && it.amount != null }?.amount
        val dedSum = lines.filter { it.role == SlipRole.DEDUCTION && it.amount != null }.sumOf { it.amount!! }
        val dedOk = dedTotal != null && dedTotal == dedSum
        if (dedOk) lines.forEachIndexed { i, l -> if (l.role == SlipRole.DEDUCTION || l.role == SlipRole.DEDUCTION_TOTAL && l.amount == dedSum) ok += i }
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
    /**
     * [readings]: the words of each reading with their boxes (one list per made-over picture).
     * [PHOTO-24] 1.30: [templates], the company's payslips the user confirmed before: the one most like this photo (by the
     * first reading's names) fills in what this photo lost and orders the lines as before.
     */
    fun read(readings: List<List<OcrLine>>, templates: List<SlipTemplate> = emptyList()): SlipVote.Result {
        val pages = readings.filter { it.isNotEmpty() }
        val template = if (templates.isEmpty() || pages.isEmpty()) null else SlipTemplate.pick(templates, SlipTable.read(pages.first()).read)
        val each = pages.map { best(it, template) }
        val voted = SlipVote.mergeResults(each)
        val fixed = SlipSums.repair(voted.read, voted.unsure)
        if (template == null) return SlipVote.Result(fixed.read, fixed.unsure, voted.text)
        // [PHOTO-24] In the template's order, whichever reading led and whatever the vote added.
        val order = SlipTable.inOrder(fixed.read.lines, template)
        val unsure = order.indices.filter { order[it] in fixed.unsure }.toSet()
        return SlipVote.Result(fixed.read.copy(lines = order.map { fixed.read.lines[it] }), unsure, voted.text)
    }

    /**
     * [PHOTO-26] 1.30 AV: a payslip too long for one photo (지급 on one, 공제 on the next), each photo read on its own ([read]),
     * put together: the lines of every page, a line two pages both show (a total at the foot of one and the head of the
     * next) once. A line is looked at when the page it came from was unsure of it.
     */
    fun pages(results: List<SlipVote.Result>): SlipVote.Result {
        val read = results.filter { it.read.lines.isNotEmpty() }
        if (read.size <= 1) return read.firstOrNull() ?: results.firstOrNull() ?: SlipVote.Result(PayslipRead(emptyList()), emptySet(), "")
        val lines = mutableListOf<SlipLine>()
        val unsure = HashSet<Int>()
        for (r in read) r.read.lines.forEachIndexed { i, l ->
            val same = lines.indexOfFirst { it.role == l.role && it.amount == l.amount && SlipTemplate.key(it.name).take(3) == SlipTemplate.key(l.name).take(3) }
            if (same >= 0) { if (i !in r.unsure) unsure -= same; return@forEachIndexed }
            if (i in r.unsure) unsure += lines.size
            lines += l
        }
        // Items before the totals, as one payslip lists them.
        val order = lines.indices.sortedBy { if (lines[it].role in TOTALS) 1 else 0 }
        val merged = PayslipRead(order.map { lines[it] }, read.firstNotNullOfOrNull { it.read.month })
        val flags = order.indices.filter { order[it] in unsure }.toSet()
        val fixed = SlipSums.repair(merged, flags)
        return SlipVote.Result(fixed.read, fixed.unsure, read.joinToString("\n") { it.text })
    }

    /** The better of the table and the text reading of one picture. */
    fun best(words: List<OcrLine>, template: SlipTemplate? = null): SlipVote.Result {
        val table = SlipTable.read(words, template)
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
                // [PHOTO-27] 1.30: the sum to the won is that total even when the payslip states it twice ("공제" "총액" at the head,
                // 공제계 at the foot).
                earn > 0 && a == earn && a != ded -> { gross = true; l.copy(role = SlipRole.GROSS) }
                ded > 0 && a == ded && a != earn -> { dedTotal = true; l.copy(role = SlipRole.DEDUCTION_TOTAL) }
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
