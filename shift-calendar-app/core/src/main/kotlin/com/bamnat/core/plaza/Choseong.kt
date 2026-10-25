package com.bamnat.core.plaza

/**
 * [PLAZA-03] 초성 찾기 ("ㅅㅅㅈ" → 삼성전자): a Hangul syllable U+AC00..U+D7A3 is (초성 × 21 + 중성) × 28 + 종성 from
 * U+AC00, so its 초성 is (code − 0xAC00) / 588 (The Unicode Standard, 3.12 Conjoining Jamo Behavior). The company
 * server keeps the same letters of each name (`cho`, server/company-worker); this is the app's copy for the
 * hint under the search box and the tests that hold the two to one rule.
 */
object Choseong {
    private const val FIRST = 0xAC00
    private const val LAST = 0xD7A3
    private const val CHO = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ"

    /** The 초성 of each syllable of [s]; other letters and digits as they are (lower case), the rest dropped. */
    fun of(s: String): String = buildString {
        for (c in s) when {
            c.code in FIRST..LAST -> append(CHO[(c.code - FIRST) / 588])
            c in CHO -> append(c)
            c.isLetterOrDigit() -> append(c.lowercaseChar())
        }
    }

    /** Whether [q] is made of 초성 only (two or more), so it is looked up by them. */
    fun isQuery(q: String): Boolean {
        val t = q.filter { !it.isWhitespace() }
        return t.length >= 2 && t.all { it in CHO }
    }
}
