package com.bamnat.core.recipe


/**
 * [RCP-04] 이름 사칭 막기 (설계서 3-1 6번, 4-7 S4·S5; Unicode UTS #39 Security Mechanisms 4 Confusable Detection):
 * a name's "뼈대" (skeleton) folds look-alike letters to one form — NFKC, lower case, Cyrillic and Greek letters that look Latin,
 * 0/o, 1/l/i, rn/m — and drops spaces and marks, so "밤낮 공식", "밤 낮", "ВАМNАТ" and "bamnat" meet. The server keeps the
 * same table (`nameSkeleton()` in server/company-worker) and the shared cases hold the two to one rule.
 *
 * Only a subset of UTS #39's confusables.txt is used: the letters that can pass for Latin or Hangul in a short name. A full
 * table is not needed because names that only an operator may use are refused on the skeleton, and two users' names that
 * share a skeleton are told apart by the server ("비슷한 이름이 있어요").
 */
object NameSkeleton {
    /** Look-alikes folded to the Latin letter they pass for (UTS #39 confusables, MA table, the common ones). */
    private val FOLD: Map<Char, String> = buildMap {
        // Cyrillic
        val pairs = listOf(
            'а' to "a", 'в' to "b", 'е' to "e", 'ё' to "e", 'к' to "k", 'м' to "m", 'н' to "h", 'о' to "o", 'р' to "p", 'с' to "c",
            'т' to "t", 'у' to "y", 'х' to "x", 'ѕ' to "s", 'і' to "i", 'ј' to "j", 'ԁ' to "d", 'һ' to "h", 'ԛ' to "q", 'ԝ' to "w",
            'ɡ' to "g", 'ı' to "i", 'ӏ' to "l", 'ո' to "n", 'ս' to "u",
            // Greek
            'α' to "a", 'β' to "b", 'ε' to "e", 'η' to "n", 'ι' to "i", 'κ' to "k", 'ν' to "v", 'ο' to "o", 'ρ' to "p", 'τ' to "t",
            'υ' to "u", 'χ' to "x", 'ω' to "w", 'γ' to "y",
            // Digits and Latin that pass for each other in a short name
            '0' to "o", '1' to "l", '|' to "l", '!' to "l", 'i' to "l", '5' to "s", '$' to "s", '@' to "a", '3' to "e", '8' to "b",
            // Hangul compatibility look-alikes (ㅇ/o is left alone: Korean names use both)
        )
        for ((k, v) in pairs) put(k, v)
    }

    /** The skeleton of [name]: what two names that look alike have in common. */
    fun of(name: String): String {
        val n = RecipePlatform.nfkc(RecipeText.clean(name)).lowercase()
        val b = StringBuilder()
        for (c in n) {
            if (c.isWhitespace()) continue
            val f = FOLD[c]
            when {
                f != null -> b.append(f)
                c.isLetterOrDigit() -> b.append(c)
            }
        }
        return b.toString().replace("rn", "m").replace("vv", "w").replace("cl", "d")
    }

    /**
     * Names only the operator's account may use (설계서 4-7 S4), on the skeleton so "밤 낮 공식", "BAMNАT" (Cyrillic А) and
     * "0fficial" are caught too. 밤낮 and 공식 are also everyday words (밤낮 바뀌는 사람 알람, 야간수당 공식), so they are refused
     * only alone or as a claim (밤낮 공식, 밤낮팀, 공식 레시피, 공식 인증).
     */
    private val RESERVED = listOf("관리자", "개발자", "운영자", "운영진", "운영팀", "개발팀", "bamnat", "admin", "official").map { of(it) }
    private val ALONE = setOf(of("밤낮"), of("공식"), of("운영"))
    private val CLAIM = Regex("(${of("밤낮")}|앱|레시피)(공식|팀|운영|관리|개발|본사|제공|인증)|공식(레시피|인증|추천|앱|배포|템플릿|틀|계정|${of("밤낮")})")

    /** Whether [name] (a recipe's title or a maker's name) claims to be the operator's (S4). */
    fun reserved(name: String): Boolean {
        val s = of(name)
        return s in ALONE || RESERVED.any { s.contains(it) } || CLAIM.containsMatchIn(s)
    }

    enum class Script { HANGUL, LATIN, CYRILLIC, GREEK, HAN, KANA, OTHER }

    private fun scriptOf(c: Char): Script? = when (Character.UnicodeScript.of(c.code)) {
        Character.UnicodeScript.HANGUL -> Script.HANGUL
        Character.UnicodeScript.LATIN -> Script.LATIN
        Character.UnicodeScript.CYRILLIC -> Script.CYRILLIC
        Character.UnicodeScript.GREEK -> Script.GREEK
        Character.UnicodeScript.HAN -> Script.HAN
        Character.UnicodeScript.HIRAGANA, Character.UnicodeScript.KATAKANA -> Script.KANA
        Character.UnicodeScript.COMMON, Character.UnicodeScript.INHERITED -> null
        else -> Script.OTHER
    }

    /**
     * [RCP-04] 섞인 문자 체계 (S5, UTS #39 5.1 mixed-script detection): a word that mixes Cyrillic or Greek letters with Latin or
     * Hangul ones ("밤낮 bаmnat" with a Cyrillic а). Korean with English words, or 漢字 with Hangul, are normal and pass.
     */
    fun mixedScript(text: String): Boolean = text.split(Regex("[\\s\\p{P}]+")).any { word ->
        val scripts = word.mapNotNull(::scriptOf).toSet()
        (Script.CYRILLIC in scripts || Script.GREEK in scripts) && (Script.LATIN in scripts || Script.HANGUL in scripts)
    }
}
