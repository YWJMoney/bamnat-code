package com.bamnat.core.memo

import java.time.LocalDate
import java.time.LocalTime

/**
 * [TAB-01] 1.36 메모 속성 (사용자 2026-11-01 "탭마다 엄청난 자유도", 2차 조사 제안 22 메모장 데이터화): a memo's "칸: 값" lines read as
 * typed properties — the same lines a 메모 틀's 입력 칸 has written since 1.35 ([TemplateAsk.lines]). Nothing new is stored: an old memo, a
 * backup and a shared memo carry their properties as they are, and the user edits one by editing the line.
 */
object MemoProps {
    enum class Type(val label: String) { TEXT("글"), NUM("숫자"), MONEY("금액"), STAR("별점"), DATE("날짜"), TIME("시각"), CHECK("체크") }

    /** One property: its 칸 name, the value as written, what it reads as, and its number when it has one (별 수, 금액, 1·0 for a check). */
    data class Prop(val label: String, val raw: String, val type: Type, val num: Double? = null, val date: LocalDate? = null)

    const val MAX_LABEL = 20
    const val MAX_PROPS = 12

    // A 칸 name: up to 20 letters, at most three words, not starting with a list mark or '#', with a letter in it (so "10:30 회의" is not one).
    private val LINE = Regex("^\\s*([^:#\\-•*\\s][^:\\n]{0,19}?)\\s*:\\s*(.+?)\\s*$")
    private val STAR_WORDS = listOf("별점", "평점")
    private val STARS = Regex("^[★☆]{1,5}$")
    private val NUM = Regex("^[-+]?[0-9][0-9,]*(\\.[0-9]+)?$")
    private val MONEY = Regex("^[-+]?[0-9][0-9,]*\\s*(원|₩)$|^₩\\s*[0-9][0-9,]*$")
    private val MAN = Regex("^([0-9]+(\\.[0-9]+)?)\\s*만\\s*원?$")
    private val ISO = Regex("^(\\d{4})-(\\d{1,2})-(\\d{1,2})$")
    private val KO_DATE = Regex("^(?:(\\d{4})년\\s*)?(\\d{1,2})월\\s*(\\d{1,2})일$")
    private val SLASH = Regex("^(\\d{1,2})/(\\d{1,2})$")
    private val TIME = Regex("^([01]?\\d|2[0-3]):([0-5]\\d)$")
    private val YES = setOf("✓", "✔", "☑", "예", "네", "O", "o", "했음", "완료", "yes", "true")
    private val NO = setOf("☐", "아니오", "아니요", "X", "x", "안 함", "no", "false")

    /** The 칸 name a body line writes ("매출: 32000" → "매출"), or null when the line is not a property. */
    fun labelOfLine(line: String): String? = LINE.matchEntire(line)?.groupValues?.get(1)?.trim()?.takeIf(::isLabel)

    /** The properties written in [memo]'s body, first line wins for a name written twice; [year] reads a date with no year. */
    fun of(memo: Memo, year: Int = memo.date?.year ?: com.bamnat.core.time.Today.date().year): List<Prop> = parse(memo.body, year)

    fun parse(text: String, year: Int = com.bamnat.core.time.Today.date().year): List<Prop> {
        val seen = HashSet<String>()
        val out = ArrayList<Prop>()
        for (line in text.lineSequence()) {
            val m = LINE.matchEntire(line) ?: continue
            val label = m.groupValues[1].trim()
            val raw = m.groupValues[2].trim()
            if (!isLabel(label) || raw.isEmpty() || raw.startsWith("//")) continue
            if (!seen.add(label)) continue
            out += read(label, raw, year)
            if (out.size == MAX_PROPS) break
        }
        return out
    }

    /** A name a person gives a 칸: a letter in it, at most three words, no link. */
    fun isLabel(s: String): Boolean = s.length in 1..MAX_LABEL && s.any { it.isLetter() } &&
        s.split(' ').size <= 3 && !s.contains("http", ignoreCase = true) && s.none { it == '/' || it == '|' || it == ':' || it == ';' }

    /** What [raw] reads as. */
    fun read(label: String, raw: String, year: Int = com.bamnat.core.time.Today.date().year): Prop {
        val v = raw.trim()
        STARS.matchEntire(v)?.let { return Prop(label, v, Type.STAR, v.count { it == '★' }.toDouble()) }
        // PC 확인 b81b643f: "별점: 4" is a 별점 (averaged in the 표, asked as stars in a 틀), not a number to add up.
        if (STAR_WORDS.any { label.contains(it) }) v.removeSuffix("점").trim().toIntOrNull()?.takeIf { it in 1..5 }?.let {
            return Prop(label, v, Type.STAR, it.toDouble())
        }
        if (v in YES) return Prop(label, v, Type.CHECK, 1.0)
        if (v in NO) return Prop(label, v, Type.CHECK, 0.0)
        MAN.matchEntire(v)?.let { return Prop(label, v, Type.MONEY, it.groupValues[1].toDouble() * 10_000) }
        if (MONEY.matches(v)) return Prop(label, v, Type.MONEY, v.filter { it.isDigit() || it == '-' }.toDoubleOrNull())
        if (NUM.matches(v)) return Prop(label, v, Type.NUM, v.replace(",", "").toDoubleOrNull())
        TIME.matchEntire(v)?.let { return Prop(label, v, Type.TIME, it.groupValues[1].toInt() * 60.0 + it.groupValues[2].toInt()) }
        dateOf(v, year)?.let { return Prop(label, v, Type.DATE, it.toEpochDay().toDouble(), it) }
        return Prop(label, v, Type.TEXT)
    }

    private fun dateOf(v: String, year: Int): LocalDate? = runCatching {
        ISO.matchEntire(v)?.let { return@runCatching LocalDate.of(it.groupValues[1].toInt(), it.groupValues[2].toInt(), it.groupValues[3].toInt()) }
        KO_DATE.matchEntire(v)?.let { m ->
            return@runCatching LocalDate.of(m.groupValues[1].toIntOrNull() ?: year, m.groupValues[2].toInt(), m.groupValues[3].toInt())
        }
        SLASH.matchEntire(v)?.let { return@runCatching LocalDate.of(year, it.groupValues[1].toInt(), it.groupValues[2].toInt()) }
        null
    }.getOrNull()

    /**
     * The 칸 names across [memos] for a table's columns or a board's 묶음: the ones most memos have first, a name's type the one most of its
     * values read as (a column of 금액 stays 금액 when one cell says "모름").
     */
    fun columns(memos: List<Memo>, max: Int = 8): List<Pair<String, Type>> {
        val count = LinkedHashMap<String, MutableMap<Type, Int>>()
        for (m in memos) for (p in of(m)) count.getOrPut(p.label) { HashMap() }.merge(p.type, 1, Int::plus)
        return count.entries
            .sortedByDescending { e -> e.value.values.sum() }
            .take(max)
            .map { e ->
                val types = e.value
                val numeric = (types[Type.NUM] ?: 0) + (types[Type.MONEY] ?: 0)
                val type = when {
                    (types[Type.MONEY] ?: 0) > 0 && numeric * 2 >= types.values.sum() -> Type.MONEY
                    // PC 확인 bad7d860: "금액: 12000" (원 없이) reads as 금액 in the 표, with commas.
                    (types[Type.NUM] ?: 0) * 2 >= types.values.sum() && isMoneyLabel(e.key) -> Type.MONEY
                    else -> types.maxByOrNull { it.value }!!.key
                }
                e.key to type
            }
    }

    /** The sum of [label]'s numbers across [memos] (금액·숫자 칸의 합계), or null when none has a number. */
    fun total(memos: List<Memo>, label: String): Double? {
        val nums = memos.mapNotNull { m -> of(m).firstOrNull { it.label == label }?.takeIf { it.type == Type.NUM || it.type == Type.MONEY }?.num }
        return if (nums.isEmpty()) null else nums.sum()
    }

    /** The average of [label]'s 별점 across [memos] (a 별점 column's foot shows this, not a sum), or null when none has one. */
    fun average(memos: List<Memo>, label: String): Double? {
        val nums = memos.mapNotNull { m -> of(m).firstOrNull { it.label == label }?.takeIf { it.type == Type.STAR || it.type == Type.NUM }?.num }
        return if (nums.isEmpty()) null else nums.average()
    }

    /** Whether a 칸 name says money (금액, 매출, 현금…), so a bare number under it is 원. */
    fun isMoneyLabel(label: String): Boolean = MONEY_WORDS.any { label.contains(it) }

    /** The first 금액 property, the one '가계부에 넣기' offers ([TAB-03]); a 숫자 칸 named like money counts too. */
    fun money(memo: Memo): Prop? = of(memo).firstOrNull { it.type == Type.MONEY && (it.num ?: 0.0) > 0 }
        ?: of(memo).firstOrNull { it.type == Type.NUM && (it.num ?: 0.0) > 0 && MONEY_WORDS.any { w -> it.label.contains(w) } }

    private val MONEY_WORDS = listOf("금액", "매출", "지출", "수입", "비용", "현금", "카드", "가격", "값", "원", "팁", "급여")

    /** [memo]'s body with [label]'s value set to [value] (a new line at the top when it had none, the line gone when [value] is blank). */
    fun set(memo: Memo, label: String, value: String): Memo {
        val lines = memo.body.lines().toMutableList()
        val at = lines.indexOfFirst { l -> LINE.matchEntire(l)?.groupValues?.get(1)?.trim() == label }
        val v = value.trim()
        when {
            at >= 0 && v.isEmpty() -> lines.removeAt(at)
            at >= 0 -> lines[at] = "$label: $v"
            v.isNotEmpty() -> lines.add(0, "$label: $v")
        }
        return memo.copy(body = lines.joinToString("\n").trimEnd('\n'))
    }

    /** A time property as a clock time. */
    fun timeOf(p: Prop): LocalTime? = if (p.type == Type.TIME && p.num != null) LocalTime.of(p.num.toInt() / 60, p.num.toInt() % 60) else null
}
