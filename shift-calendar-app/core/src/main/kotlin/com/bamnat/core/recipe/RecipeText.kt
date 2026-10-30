package com.bamnat.core.recipe


/**
 * [RCP-01][RCP-40] The words of a recipe and of everything said about one, cleaned and checked the same way on the phone and on
 * the server (설계서 4-7 S5·S6·S7):
 * - NFC, then no control or format characters: no direction overrides (U+202A..U+202E, U+2066..U+2069), no zero-width space or
 *   joiner except a joiner inside an emoji (👨‍🍳), no soft hyphen or tag characters. A line break only where lines are allowed.
 * - No email, phone number, amount of money, link or invitation to a chat room (금액은 입력 칸으로, 링크는 받지 않아요).
 */
object RecipeText {
    private const val ZWJ = 0x200D
    private const val VS16 = 0xFE0F

    /** [s] cleaned (S5); [lines] keeps line breaks (at most [maxLines]), else they become spaces. */
    fun clean(s: String, lines: Boolean = false, maxLines: Int = 20): String {
        val nfc = RecipePlatform.nfc(s)
        val cps = nfc.codePoints().toArray()
        val out = StringBuilder()
        for ((i, cp) in cps.withIndex()) {
            when {
                cp == '\n'.code -> out.append(if (lines) '\n' else ' ')
                cp == '\r'.code -> {}
                cp == '\t'.code -> out.append(' ')
                cp == ZWJ -> if (i > 0 && i < cps.size - 1 && emojiLike(cps[i - 1]) && emojiLike(cps[i + 1])) out.appendCodePoint(cp)
                cp == VS16 -> if (i > 0 && emojiLike(cps[i - 1])) out.appendCodePoint(cp)
                hidden(cp) -> {}
                else -> out.appendCodePoint(cp)
            }
        }
        var t = out.toString().replace(Regex("[ \\u00A0\\u3000]+"), " ")
        if (lines) {
            t = t.lines().joinToString("\n") { it.trim() }.replace(Regex("\n{3,}"), "\n\n")
            val kept = t.lines()
            if (kept.size > maxLines) t = kept.take(maxLines - 1).joinToString("\n") + " " + kept.drop(maxLines - 1).joinToString(" ")
        }
        return t.trim()
    }

    /** Control (Cc), format (Cf), private use, unassigned and surrogate halves: never kept in a recipe's words. */
    private fun hidden(cp: Int): Boolean = when (Character.getType(cp).toByte()) {
        Character.CONTROL, Character.FORMAT, Character.PRIVATE_USE, Character.UNASSIGNED, Character.SURROGATE -> true
        else -> cp in 0xFFF9..0xFFFB || cp in 0xE0000..0xE007F
    }

    /** Close enough to an emoji to keep a joiner or presentation selector next to it. */
    private fun emojiLike(cp: Int): Boolean = cp >= 0x1F000 || cp in 0x2190..0x2BFF || cp in 0x2300..0x23FF || cp == 0x00A9 ||
        cp == 0x00AE || cp in 0x1F3FB..0x1F3FF || Character.getType(cp).toByte() == Character.OTHER_SYMBOL

    /**
     * User-perceived characters (문자소) of [s], as the app and the server count letters against a limit: a joiner and what it
     * joins, a variation selector, a skin tone, a combining mark or a tag letter add nothing, and two flag letters are one
     * (UAX #29 in the cases a recipe meets; the server's `graphemes()` counts the same way, not by the platform's own rules).
     */
    fun graphemes(s: String): Int {
        val cps = s.codePoints().toArray()
        var n = 0
        var i = 0
        var flagHalf = false
        while (i < cps.size) {
            val cp = cps[i]
            val t = Character.getType(cp).toByte()
            when {
                cp == ZWJ -> i++ // the next one is joined to this one
                cp in 0xFE00..0xFE0F || cp in 0x1F3FB..0x1F3FF || cp in 0xE0020..0xE007F ||
                    t == Character.NON_SPACING_MARK || t == Character.ENCLOSING_MARK -> {}
                cp in 0x1F1E6..0x1F1FF -> { if (!flagHalf) n++; flagHalf = !flagHalf; i++; continue }
                else -> n++
            }
            flagHalf = false
            i++
        }
        return n
    }

    private val PERSONAL = listOf(
        "email" to Regex("[^\\s@]+@[^\\s@]+\\.[a-z]{2,}", RegexOption.IGNORE_CASE),
        "phone" to Regex("(\\+?82[\\s.-]?0?|0)\\d{1,2}[\\s.-]?\\d{3,4}[\\s.-]?\\d{4}"),
        "amount" to Regex(
            "\\d[\\d,.]*\\s*(원|만\\s*원|천\\s*원|억\\s*원|won(?![A-Za-z0-9])|krw(?![A-Za-z0-9]))|[일이삼사오육칠팔구십백천]+\\s*만\\s*원|\\d{1,3}(,\\d{3})+|\\d{5,}",
            RegexOption.IGNORE_CASE,
        ),
        // S6: no address to go to, written plainly or spaced out (bamnat . kr), and no move to a chat room.
        "link" to Regex(
            "(https?|ftp)\\s*:\\s*/\\s*/|www\\s*\\.|[a-z0-9-]+\\s*\\.\\s*(com|net|org|kr|co|io|me|ly|gl|app|xyz|link|site|shop|kakao)(?![A-Za-z0-9])|" +
                "bit\\s*\\.\\s*ly|open\\s*\\.\\s*kakao",
            RegexOption.IGNORE_CASE,
        ),
        "chat" to Regex("(오픈\\s*채팅|단톡\\s*방?|카톡\\s*방|카카오톡\\s*방|텔레그램|텔레\\s*방|디스코드|라인\\s*아이디|카톡\\s*아이디|문의는\\s*개인)"),
    )

    /** Why [text] cannot be shared (email, phone, amount, link, chat), or null. The server's `personal()` plus links and chats. */
    fun problem(text: String): String? {
        // [RCP-40] Read as it looks: full-width letters and dots (ｂｉｔ．ｌｙ) and 。 are the plain ones.
        val t = RecipePlatform.nfkc(text).replace('。', '.')
        return PERSONAL.firstOrNull { (_, re) -> re.containsMatchIn(t) }?.first
    }

    /** [RCP-15] What 투자 광장 never takes (결정 AI-D8): 리딩방, promised returns, 종목 추천 — the server's INVEST_ADVICE. */
    private val INVEST_ADVICE = Regex(
        "리딩\\s*방|리딩해|수익\\s*(보장|확정)|원금\\s*보장|무조건\\s*(올라|오릅|상승|수익)|(카톡|카카오톡|텔레그램|오픈\\s*채팅|단톡)\\s*방?\\s*(으로|에|들어|입장|초대)|" +
            "급등\\s*주\\s*(추천|알려)|종목\\s*(추천|찍어|알려)\\s*(드|줄|해)",
    )

    fun investAdvice(text: String): Boolean = INVEST_ADVICE.containsMatchIn(text)

    /** What to tell the user about a refused text. */
    fun problemText(code: String): String = when (code) {
        "email" -> "메일 주소는 적지 말아 주세요"
        "phone" -> "전화번호는 적지 말아 주세요"
        "amount" -> "금액은 글에 적지 않고 ‘받는 사람이 정할 칸’으로 바꿔요"
        "link" -> "인터넷 주소는 넣을 수 없어요"
        "chat" -> "채팅방·메신저로 부르는 글은 넣을 수 없어요"
        "invest_advice" -> "종목 추천·수익 보장·리딩방 글은 넣을 수 없어요"
        "name_taken" -> "공식·운영자처럼 보이는 이름은 쓸 수 없어요"
        // F-99: say which part, so the ✕ can be fixed (a bare "넣을 수 없는 내용" left no way out).
        "parent" -> "원래 판 표시가 맞지 않아요. 스튜디오에서 올리기를 다시 눌러 주세요"
        "cover" -> "표지를 다시 골라 주세요"
        else -> "넣을 수 없는 내용이 있어요"
    }

    // ---- emoji (RCP-18) ----

    /** One line of the picker's list: an emoji, its group, its 한국어 검색어 and its skin-tone forms. */
    data class EmojiEntry(val emoji: String, val group: String, val words: String, val tones: List<String>)

    /** Every emoji the app offers, base forms in Unicode's order (tools/emoji_list.py, Emoji 15.0까지). */
    val emoji: List<EmojiEntry> by lazy {
        val text = RecipePlatform.resource("/com/bamnat/core/recipe/emoji.tsv") ?: return@lazy emptyList()
        text.lineSequence().let { seq ->
            seq.filter { it.isNotBlank() && !it.startsWith("#") }.map { line ->
                val f = line.split('\t')
                EmojiEntry(f[0], f.getOrElse(1) { "" }, f.getOrElse(2) { "" }, f.getOrElse(3) { "" }.split(' ').filter { it.isNotEmpty() })
            }.toList()
        }
    }

    private val allowed: Set<String> by lazy { emoji.flatMapTo(HashSet()) { listOf(it.emoji) + it.tones } }

    /**
     * [s] as one emoji the app knows (fully qualified, with U+FE0F where Unicode puts it), or null: an emoji field holds one
     * grapheme and nothing else (설계서 4-6 이모지).
     */
    fun oneEmoji(s: String): String? {
        val t = RecipePlatform.nfc(s.trim())
        if (t.isEmpty()) return null
        if (t in allowed) return t
        val withVs = t.replace("️", "").let { bare ->
            allowed.firstOrNull { it.replace("️", "") == bare }
        }
        return withVs
    }

    /** The groups in the picker's order. */
    val emojiGroups: List<String> by lazy { emoji.map { it.group }.distinct() }

    /** Emoji whose 한국어 검색어 or 초성 hold every word of [q]; the emoji itself matches too. */
    fun searchEmoji(q: String, limit: Int = 120): List<EmojiEntry> {
        val words = q.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()
        return emoji.asSequence().filter { e ->
            words.all { w ->
                e.emoji == w || e.words.contains(w, ignoreCase = true) ||
                    (com.bamnat.core.plaza.Choseong.isQuery(w) && com.bamnat.core.plaza.Choseong.of(e.words).contains(w))
            }
        }.take(limit).toList()
    }
}
