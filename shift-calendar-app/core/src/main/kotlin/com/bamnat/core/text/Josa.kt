package com.bamnat.core.text

/** 1.38 PC 9차 ("팀 요금제이"): 이/가, 을/를, 은/는 by the last letter's 받침, for words the code puts into a sentence. */
object Josa {
    private fun batchim(w: String): Boolean? = w.trim().lastOrNull()?.let { c -> if (c in '가'..'힣') (c - '가') % 28 != 0 else null }

    fun iga(w: String): String = if (batchim(w) == false) "가" else "이"
    fun eulreul(w: String): String = if (batchim(w) == false) "를" else "을"
    fun eunneun(w: String): String = if (batchim(w) == false) "는" else "은"
    /** 1.39 PC NAV2 ("‘연차’과 묶을 화면"): 과/와. */
    fun gwawa(w: String): String = if (batchim(w) == false) "와" else "과"
    /** 2.0.0 [RVL-36] ("주간으로 읽었어요", "연차로"): 으로/로, 로 after no 받침 or ㄹ. */
    fun euro(w: String): String {
        val c = w.trim().lastOrNull()?.takeIf { it in '가'..'힣' } ?: return "로"
        val end = (c - '가') % 28
        return if (end == 0 || end == 8) "로" else "으로"
    }

    /** How a word ends when read aloud: with a 받침 or not, and whether that 받침 is ㄹ (으로/로). */
    data class Sound(val batchim: Boolean, val rieul: Boolean = false)

    /**
     * [RVM-03] The sound [w] ends with, or null when it cannot be told (Latin letters, an emoji, nothing). A number reads as Korean:
     * 1 일, 3 삼, 6 육, 7 칠, 8 팔 end in a 받침 (1·7·8 in ㄹ), and a run of 0 is 십·백·천·만.
     */
    fun sound(w: String): Sound? {
        val t = w.trimEnd()
        val c = t.lastOrNull() ?: return null
        if (c in '가'..'힣') {
            val jong = (c - '가') % 28
            return Sound(jong != 0, jong == 8)
        }
        return when (c) {
            '1', '7', '8' -> Sound(true, rieul = true)
            // 0 alone is 영; 10 십, 100 백, 1000 천, 10000 만 … all end in a 받침 that is not ㄹ.
            '0', '3', '6' -> Sound(true)
            in '0'..'9' -> Sound(false)
            else -> null
        }
    }

    /**
     * [RVM-03] PC-37 ('오늘 주간예요'): [after], the words that follow [word] in a sentence, with the 조사 it starts with fitted to how
     * [word] ends (예요/이에요, 이/가, 을/를, 은/는, 과/와, 로/으로, 랑/이랑). A writer's "(이)에요" is fitted the same way. When [word]'s
     * sound cannot be told, [after] is left as written.
     */
    fun fit(word: String, after: String): String {
        if (after.isEmpty()) return after
        val s = sound(word) ?: return after
        for (p in PAIRS) {
            val token = p.forms.firstOrNull { after.startsWith(it) } ?: continue
            val rest = after.substring(token.length)
            // A one-letter 조사 must stand alone ("야간이 되면" is 이, "이번" is not): the next letter is not Hangul.
            if (!p.copula && rest.firstOrNull()?.let { it in '가'..'힣' } == true) continue
            return p.pick(s) + rest
        }
        return after
    }

    private class Pair(val withBatchim: String, val without: String, val forms: List<String>, val copula: Boolean = false, val ro: Boolean = false) {
        fun pick(s: Sound): String = if (ro) (if (s.batchim && !s.rieul) withBatchim else without) else if (s.batchim) withBatchim else without
    }

    // Longest first: "(이)에요" before "이에요" before "이".
    private val PAIRS = listOf(
        Pair("이에요", "예요", listOf("(이)에요", "(이)예요", "이에요", "이예요", "예요", "에요"), copula = true),
        Pair("이었어요", "였어요", listOf("(이)었어요", "(이)였어요", "이었어요", "이였어요", "였어요"), copula = true),
        Pair("이라서", "라서", listOf("(이)라서", "이라서", "라서"), copula = true),
        Pair("이라", "라", listOf("(이)라", "이라")),
        Pair("으로", "로", listOf("(으)로", "으로", "로"), ro = true),
        Pair("이랑", "랑", listOf("(이)랑", "이랑", "랑")),
        Pair("이", "가", listOf("(이)가", "(가)이", "이", "가")),
        Pair("을", "를", listOf("(을)를", "(를)을", "을", "를")),
        Pair("은", "는", listOf("(은)는", "(는)은", "은", "는")),
        Pair("과", "와", listOf("(과)와", "(와)과", "과", "와")),
    )
}
