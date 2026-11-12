package com.bamnat.core.plaza

/**
 * 2.0.0 [RVN-19] 검토 COMP-20·I10ab-01·U10a-11·T10a-10 회사 이름 도우미. A name typed by hand became a new company as it was
 * (두 글자, 치다 만 이름, 띄어쓰기·한 글자 다른 이름, 사업장을 붙인 이름), so one company split into several and its 맞아요 never
 * met; joining the wrong one holds the calendar for 30 days (COMP-12). Before a new company is made, the name is checked
 * ([problem]), a 사업장 written into it is taken apart ([split]), and the companies the phone already knows that it may mean
 * come first ([similar]: 같은 이름, 사업장을 붙인 이름, 포함, 한두 글자 차이, 초성; [MAX] at most).
 */
object CompanyNames {
    /** At most this many known names are offered. */
    const val MAX = 3

    /** Why a known name was offered, in the order they are offered. */
    enum class Why {
        /** The same name, written another way (띄어쓰기·(주)·대소문자). */
        SAME,
        /** The known name with a 사업장 after it ('현대제철 당진공장'): the rest goes in the 사업장 box. */
        SITE,
        /** One is a part of the other ('현대제' → '현대제철'). */
        PART,
        /** One or two letters apart ('현대재철' → '현대제철'). */
        NEAR,
        /** The same 초성 ('ㅎㄷㅈㅊ', or 한대제철 → 현대제철). */
        CHOSEONG,
    }

    /** [item] may be what was meant, by [name]; [site] is the rest of the typed name after it ([Why.SITE]). */
    data class Like<T>(val item: T, val name: String, val why: Why, val site: String = "", val distance: Int = 0)

    /** Why a typed name cannot become a company yet. */
    enum class Problem { SHORT, CHOSEONG, UNFINISHED }

    /** The words a 사업장 ends with rather than a company (공장, 지점, 센터, 현장 …), longest first. */
    private val SITE_ENDS = listOf(
        "물류센터", "사업장", "사업소", "사업부", "제철소", "발전소", "캠퍼스", "연구소", "영업소", "출장소", "터미널",
        "공장", "지점", "지사", "본사", "본점", "센터", "현장", "병동", "공구", "호기", "점",
    )

    private fun norm(s: String): String = PublicCycles.norm(s)

    private fun isSyllable(c: Char) = c.code in 0xAC00..0xD7A3

    /** A lone 자모 (ㄱ…ㅎ, ㅏ…ㅣ): a letter still being typed. */
    private fun isJamo(c: Char) = c.code in 0x3131..0x318E

    /** What keeps [typed] from becoming a company; null when nothing does. */
    fun problem(typed: String): Problem? {
        val t = typed.trim()
        return when {
            Choseong.isQuery(t) -> Problem.CHOSEONG
            t.any(::isJamo) -> Problem.UNFINISHED
            norm(t).length < 2 -> Problem.SHORT
            else -> null
        }
    }

    /** The line under the name box for [p]. */
    fun words(p: Problem): String = when (p) {
        Problem.SHORT -> "회사 이름을 두 글자 넘게 적어 주세요"
        Problem.CHOSEONG -> "초성만으로는 새 회사를 만들 수 없어요. 이름을 끝까지 적어 주세요"
        Problem.UNFINISHED -> "이름이 덜 쳐졌어요. 끝 글자를 한 번 봐 주세요"
    }

    /** A short name (two letters) mixes with other companies easily: worth a second look, never refused (KT·LG·쿠팡). */
    fun short(typed: String): Boolean = problem(typed) == null && norm(typed).length == 2

    /**
     * The name and the 사업장 written after it with a space ('현대제철 당진공장' → 현대제철 + 당진공장, '현대제철 당진 공장' →
     * 현대제철 + 당진 공장); null when the last word is not a 사업장's or nothing of a name is left before it.
     */
    fun split(typed: String): Pair<String, String>? {
        val words = typed.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.size < 2) return null
        val last = words.last()
        val take = when {
            last in SITE_ENDS && words.size >= 3 -> 2
            last !in SITE_ENDS && last.length >= 2 && SITE_ENDS.any { last.endsWith(it) } -> 1
            else -> return null
        }
        val head = words.dropLast(take).joinToString(" ")
        if (norm(head).length < 2 || problem(head) != null) return null
        return head to words.takeLast(take).joinToString(" ")
    }

    /**
     * The known names ([items], each by [names]: its name first, then other names) that [typed] may mean, best first and
     * [max] at most. [items] come in the caller's order (most used first), which breaks ties.
     */
    fun <T> similar(typed: String, items: List<T>, names: (T) -> List<String>, max: Int = MAX): List<Like<T>> {
        // A letter still being typed at the end ('현대제ㅊ') is not looked for yet.
        val t = typed.trim().let { s -> if (Choseong.isQuery(s)) s else s.trimEnd { isJamo(it) || it.isWhitespace() } }
        val cho = Choseong.isQuery(t)
        val k = if (cho) t.filter { !it.isWhitespace() } else norm(t)
        if (k.length < 2) return emptyList()
        val q = Query(t, k, cho)
        val found = ArrayList<Pair<Int, Like<T>>>()
        items.forEachIndexed { index, item ->
            val all = names(item).filter { it.isNotBlank() }
            if (all.isEmpty()) return@forEachIndexed
            all.mapNotNull { n -> like(q, n) }.minWithOrNull(compareBy({ it.first.ordinal }, { it.third }))?.let { (why, site, d) ->
                found += index to Like(item, all.first(), why, site, d)
            }
        }
        return found.sortedWith(compareBy({ it.second.why.ordinal }, { it.second.distance }, { it.first }))
            .map { it.second }.distinctBy { norm(it.name) }.take(max)
    }

    private class Query(val typed: String, val key: String, val cho: Boolean) {
        val jamo: String by lazy { CompanyMatch.jamo(key) }
        val chos: String by lazy { Choseong.of(key) }
        val syllables: Int = key.count { isSyllable(it) }
    }

    /** How [name] fits what was typed: why, the 사업장 left over, and how far apart; null when it does not. */
    private fun like(q: Query, name: String): Triple<Why, String, Int>? {
        val n = norm(name)
        if (n.length < 2) return null
        if (q.cho) return if (Choseong.of(n).startsWith(q.key)) Triple(Why.CHOSEONG, "", Choseong.of(n).length - q.key.length) else null
        val t = q.key
        if (n == t) return Triple(Why.SAME, "", 0)
        if (t.startsWith(n)) {
            val rest = restAfter(q.typed, n)
            // A 사업장 after the name: with a space before it, or a 사업장's word at its end ('현대제철당진공장').
            val spaced = rest.second
            val site = rest.first
            if (site.isNotEmpty() && (spaced || SITE_ENDS.any { site.endsWith(it) })) return Triple(Why.SITE, site, t.length - n.length)
            if (n.length >= 3 || n.length * 2 >= t.length) return Triple(Why.PART, "", t.length - n.length)
            return null
        }
        if (n.contains(t)) return Triple(Why.PART, "", n.length - t.length)
        if (n.length >= 3 && t.contains(n)) return Triple(Why.PART, "", t.length - n.length)
        // 한두 글자 차이: by 자모 (one letter of a syllable counts one), two for names of three syllables or more.
        if (kotlin.math.abs(n.length - t.length) <= 2) {
            val allowed = if (q.syllables >= 3 || t.length >= 4) 2 else 1
            val d = distance(CompanyMatch.jamo(n), q.jamo, allowed)
            if (d <= allowed) return Triple(Why.NEAR, "", d)
        }
        if (q.syllables >= 2 && q.syllables == t.length && Choseong.of(n) == q.chos) {
            return Triple(Why.CHOSEONG, "", distance(CompanyMatch.jamo(n), q.jamo, 99))
        }
        return null
    }

    /**
     * What is left of [typed] after the letters that make [name] (as [norm] sees them), as it was typed, and whether a space
     * came right before it.
     */
    private fun restAfter(typed: String, name: String): Pair<String, Boolean> {
        for (i in 1..typed.length) {
            if (norm(typed.substring(0, i)) != name) continue
            val rest = typed.substring(i)
            return rest.trim() to (rest.firstOrNull()?.isWhitespace() == true)
        }
        return norm(typed).removePrefix(name) to false
    }

    /** Levenshtein distance of [a] and [b], stopping once it is past [limit]. */
    internal fun distance(a: String, b: String, limit: Int): Int {
        if (kotlin.math.abs(a.length - b.length) > limit) return limit + 1
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1)
            cur[0] = i
            var best = cur[0]
            for (j in 1..b.length) {
                cur[j] = minOf(prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1, prev[j] + 1, cur[j - 1] + 1)
                if (cur[j] < best) best = cur[j]
            }
            if (best > limit) return limit + 1
            prev = cur
        }
        return prev[b.length]
    }

    /** The reason line under an offered name. */
    fun reason(why: Why, site: String = ""): String = when (why) {
        Why.SAME -> "이미 있는 회사예요"
        Why.SITE -> "‘$site’${com.bamnat.core.text.Josa.eunneun(site)} 사업장 칸에 넣어요"
        Why.PART -> "이름 일부가 같아요"
        Why.NEAR -> "한두 글자만 달라요"
        Why.CHOSEONG -> "초성이 같아요"
    }
}
