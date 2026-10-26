package com.bamnat.core.plaza

/**
 * [PLAZA-17] 1.27 AH-I1 회사 이름 맞춰 찾기: people type a company the way they say it. Spaces, (주) and case never count
 * ([PublicCycles.norm]); an English short name and its Korean reading are the same name (GS칼텍스 = 지에스칼텍스, S-OIL =
 * 에스오일); and a name of three syllables or more is still found with one letter (자모) wrong (GS칼텍 → GS칼텍스 is
 * already a part; 칼택스 → 칼텍스 is one 자모). Used by the public cycles' search and, when the company server finds
 * nothing, to ask it once more by the other spelling.
 */
object CompanyMatch {
    private val LETTERS = mapOf(
        'a' to "에이", 'b' to "비", 'c' to "씨", 'd' to "디", 'e' to "이", 'f' to "에프", 'g' to "지", 'h' to "에이치", 'i' to "아이",
        'j' to "제이", 'k' to "케이", 'l' to "엘", 'm' to "엠", 'n' to "엔", 'o' to "오", 'p' to "피", 'q' to "큐", 'r' to "알",
        's' to "에스", 't' to "티", 'u' to "유", 'v' to "브이", 'w' to "더블유", 'x' to "엑스", 'y' to "와이", 'z' to "제트",
    )

    /** English words that are read as a word, not letter by letter (회사 이름에 흔한 것만). */
    private val WORDS = linkedMapOf(
        "hyundai" to "현대", "samsung" to "삼성", "hanwha" to "한화", "lotte" to "롯데", "doosan" to "두산", "posco" to "포스코",
        "emart" to "이마트", "korail" to "코레일", "kepco" to "한전", "hanjin" to "한진", "oil" to "오일", "steel" to "스틸",
        "power" to "파워", "chemical" to "케미칼", "logis" to "로지스", "tech" to "테크", "eps" to "이피에스",
    )

    /** How [s] is said in Korean letters: English words and short names read out, the rest as [PublicCycles.norm] leaves it. */
    fun spoken(s: String): String {
        val n = PublicCycles.norm(s)
        val out = StringBuilder()
        var i = 0
        while (i < n.length) {
            val c = n[i]
            if (c !in 'a'..'z') {
                out.append(c)
                i++
                continue
            }
            var j = i
            while (j < n.length && n[j] in 'a'..'z') j++
            out.append(readWord(n.substring(i, j)))
            i = j
        }
        return out.toString()
    }

    private fun readWord(w: String): String {
        WORDS[w]?.let { return it }
        // A known word inside (s-oil → s + oil): read it as a word, the rest letter by letter.
        for ((en, ko) in WORDS) {
            val at = w.indexOf(en)
            if (at >= 0 && en.length >= 3) return readWord(w.substring(0, at)) + ko + readWord(w.substring(at + en.length))
        }
        return w.map { LETTERS[it] ?: it.toString() }.joinToString("")
    }

    /** The spellings to look [q] up by: as typed (normalized) and as said in Korean letters, once each. */
    fun variants(q: String): List<String> = listOf(PublicCycles.norm(q), spoken(q)).filter { it.isNotEmpty() }.distinct()

    /**
     * How well [name] fits [q]: 0 starts with it, 1 has it, 2 has it with one 자모 wrong (names of 3 syllables or more only),
     * null not at all. Either spelling of each counts.
     */
    fun fit(name: String, q: String): Int? {
        val names = listOf(PublicCycles.norm(name), spoken(name)).distinct()
        val qs = variants(q)
        if (qs.isEmpty()) return null
        if (names.any { n -> qs.any { n.startsWith(it) } }) return 0
        if (names.any { n -> qs.any { it in n } }) return 1
        val near = qs.filter { it.count(::isSyllable) >= 3 }
        if (near.any { v -> names.any { n -> nearIn(jamo(n), jamo(v)) } }) return 2
        return null
    }

    private fun isSyllable(c: Char) = c.code in 0xAC00..0xD7A3

    /** Each Hangul syllable as its 초성·중성·종성 (Unicode 3.12), so 칼택스 and 칼텍스 differ by one letter, not one syllable. */
    internal fun jamo(s: String): String = buildString {
        for (c in s) {
            if (!isSyllable(c)) { append(c); continue }
            val i = c.code - 0xAC00
            append((0x1100 + i / 588).toChar())
            append((0x1161 + (i % 588) / 28).toChar())
            if (i % 28 != 0) append((0x11A7 + i % 28).toChar())
        }
    }

    /** Whether [q] is somewhere in [text] with at most one letter added, dropped or changed (Sellers' approximate search). */
    internal fun nearIn(text: String, q: String): Boolean {
        if (q.isEmpty()) return true
        var prev = IntArray(text.length + 1) // free start anywhere in text
        for (i in 1..q.length) {
            val cur = IntArray(text.length + 1)
            cur[0] = i
            for (j in 1..text.length) {
                val sub = prev[j - 1] + if (q[i - 1] == text[j - 1]) 0 else 1
                cur[j] = minOf(sub, prev[j] + 1, cur[j - 1] + 1)
            }
            prev = cur
        }
        return prev.min() <= 1
    }
}

/**
 * [PLAZA-14] 1.27 AH-D5·AH-I2 같은 주기: a company's order as each day's kind (a work shift by its name, any day off as 휴),
 * and whether two orders are the same one started on another day (another 조 of the same company). Then sharing it adds a
 * 맞아요 to the 대표 주기 instead of a second version.
 */
object CycleMatch {
    const val OFF = "휴"

    /** One day of an order: the work shift's own name, or [OFF] for a day off of any kind (휴무·비번·null). */
    fun day(name: String?, off: Boolean): String = if (name == null || off) OFF else name.trim()

    /** Whether [b] is [a] begun on another day of it (same length, a rotation of it). Empty orders are never the same. */
    fun same(a: List<String>, b: List<String>): Boolean {
        if (a.isEmpty() || a.size != b.size) return false
        return a.indices.any { k -> a.indices.all { i -> a[(i + k) % a.size] == b[i] } }
    }

    /** As [same], for the work and off pattern only: 주간 and 주 count alike as work of the same kind by first letter. */
    fun sameShape(a: List<String>, b: List<String>): Boolean = same(a.map(::shape), b.map(::shape))

    private fun shape(d: String): String = if (d == OFF) OFF else d.take(1)
}
