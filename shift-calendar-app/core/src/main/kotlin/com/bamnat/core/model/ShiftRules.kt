package com.bamnat.core.model

import com.bamnat.core.recipe.RecipeText

/**
 * 2.0.0 [RVD-28] SD-08 (스튜디오 제작 오류 조사 뿌리 R6, 설계 S7-1) 근무 종류 한 규칙표: what a kind of day ([ShiftType]) may carry when it
 * leaves the phone (근무표 링크, 회사 방식 공유, a 작품's 근무 꾸미기·회사 근무 주기), in one place.
 *
 * The app and each server wrote these rules apart and drifted: an emoji 그림 broke the link (PC-92), a 휴게 the link did not know (PC-94),
 * a 근무 글자 of joined emoji came half or was refused (SN-16), a 회사 근무 주기 작품 went up that the company server refuses (SN-17). Now the
 * servers read the numbers from a file this table writes (ShiftRulesExportTest → server/company-worker/src/shift-rules.js and
 * server/share-worker/src/shift-rules.js, never written by hand), and every server takes whatever fits here.
 *
 * What does not fit is cut or dropped wherever that keeps the meaning (a 근무 글자 or a name on the link is cut to its first letters, a 그림
 * or a 휴게 is dropped), and refused only where a cut would change what the file says (a company file's name, which its 근무 주기 points at).
 * What a server took whole before 2.0.0 ([BEFORE]) it still takes whole, so nothing that worked stops working.
 */
object ShiftRules {
    /** Kinds of day one link or one company file carries. */
    const val TYPES = 24

    /** A name: letters as a person counts them ([RecipeText.graphemes]; a 작품's 근무 꾸미기 gives up to 20) and UTF-16 code units. */
    const val NAME_LETTERS = 20
    const val NAME_UNITS = 80

    /** 근무 글자: two letters (👩‍⚕️ and 🇰🇷 are one each) up to 16 code units (SN-16). */
    const val LABEL_LETTERS = 2
    const val LABEL_UNITS = 16

    /**
     * What each server took whole before 2.0.0, in code units whatever the letters, and still does: the company server a name of 40 and a
     * 근무 글자 of 8, the share server (an app before 2.0.0 cut them so) a name of 20 and a 근무 글자 of 4.
     */
    val NAME_BEFORE: Map<String, Int> = linkedMapOf("company" to 40, "link" to 20)
    val LABEL_BEFORE: Map<String, Int> = linkedMapOf("company" to 8, "link" to 4)

    /** A colour is ARGB (the company file's number; the link sends #RRGGBB of it). A kind of day without one gets [DEFAULT_COLOR]. */
    const val COLOR_MAX = 0xFFFFFFFFL
    const val DEFAULT_COLOR = 0xFF5C6BC0L

    /** 휴게 in minutes (the editor's stepper goes to 12 hours). */
    const val REST_MAX = 12 * 60

    /** A drawn 그림's key: [ShiftIcon] keys, and any like them so an older server keeps a newer app's. */
    const val ICON_KEY = "^[a-z0-9]{1,16}$"

    /**
     * An emoji 그림 ([ShiftIcon.EMOJI] + one emoji, 1.34 RCP-08), as the servers check it (a JavaScript `u` pattern; the app takes emoji
     * only from its picker, whose every emoji fits): one picture, a keycap (#️⃣), a flag (🇰🇷, 🏴 with tags) or those joined by ZWJ, with
     * the skin tone and VS16 forms. Letters, controls, '<', quotes and spaces never fit.
     */
    const val ICON_EMOJI_PART =
        """(?:[#*0-9]\u{FE0F}?\u{20E3}|[\u{1F1E6}-\u{1F1FF}]{2}|\p{Extended_Pictographic}\u{FE0F}?\p{Emoji_Modifier}?\u{FE0F}?(?:[\u{E0020}-\u{E007E}]+\u{E007F})?)"""
    val ICON_EMOJI: String = "^" + ShiftIcon.EMOJI + ICON_EMOJI_PART + "(?:\\u{200D}" + ICON_EMOJI_PART + ")*" + "$"

    /** At most this many code points after [ShiftIcon.EMOJI] (the picker's longest, 🧑🏻‍❤️‍💋‍🧑🏼, is 10). */
    const val EMOJI_CODE_POINTS = 12

    /** The 그림 keys this app draws. */
    val ICON_KEYS: List<String> get() = ShiftIcon.entries.map { it.key }

    /** 하루 두 구간 (RVH-44): the shortest gap and the shortest part either side of it ([WorkTime.gapFits]). */
    const val GAP_MIN = WorkTime.MIN_GAP_MINUTES
    const val PART_MIN = WorkTime.MIN_PART_MINUTES

    private const val ZWJ = 0x200D

    /**
     * The letters of [s] as [RecipeText.graphemes] counts them, cut apart: a joiner and what it joins, a variation selector, a skin tone, a
     * combining mark or a tag letter go with the letter before them, and two flag letters are one. Marks with no letter before them go with
     * the next. (The servers' letters() split the same way; the export's samples hold them to this.)
     */
    fun letters(s: String): List<String> {
        val out = ArrayList<StringBuilder>()
        val lead = StringBuilder()
        fun join(text: String) { if (out.isEmpty()) lead.append(text) else out.last().append(text) }
        fun push(text: String) { out += StringBuilder(lead).append(text); lead.setLength(0) }
        val cps = s.codePoints().toArray()
        var flagHalf = false
        var i = 0
        while (i < cps.size) {
            val cp = cps[i]
            val ch = String(Character.toChars(cp))
            val t = Character.getType(cp).toByte()
            when {
                cp == ZWJ -> { join(ch + (cps.getOrNull(i + 1)?.let { String(Character.toChars(it)) } ?: "")); i++ }
                cp in 0xFE00..0xFE0F || cp in 0x1F3FB..0x1F3FF || cp in 0xE0020..0xE007F ||
                    t == Character.NON_SPACING_MARK || t == Character.ENCLOSING_MARK -> join(ch)
                cp in 0x1F1E6..0x1F1FF -> {
                    if (flagHalf) join(ch) else push(ch)
                    flagHalf = !flagHalf
                    i++
                    continue
                }
                else -> push(ch)
            }
            flagHalf = false
            i++
        }
        return out.map { it.toString() }
    }

    /** [s] when it has at most [letters] letters in [units] code units, else as many of its first letters as fit (never half a letter). */
    fun cut(s: String, letters: Int, units: Int): String {
        if (s.length <= units && RecipeText.graphemes(s) <= letters) return s
        val out = StringBuilder()
        for (l in letters(s).take(letters)) {
            if (out.length + l.length > units) break
            out.append(l)
        }
        return out.toString()
    }

    fun nameFits(name: String): Boolean = name.isNotBlank() && name.length <= NAME_UNITS && RecipeText.graphemes(name) <= NAME_LETTERS
    fun labelFits(label: String): Boolean = label.length <= LABEL_UNITS && RecipeText.graphemes(label) <= LABEL_LETTERS

    /** A name or a 근무 글자 made to fit, cut at a letter: what the app sends so no server has to cut it. */
    fun fitName(name: String): String = cut(name.trim(), NAME_LETTERS, NAME_UNITS)
    fun fitLabel(label: String): String = cut(label.trim(), LABEL_LETTERS, LABEL_UNITS)

    fun restFits(minutes: Int): Boolean = minutes in 0..REST_MAX

    /** What of [t] does not fit (name, label, color, rest), empty when it all does. The 그림 is the servers' to check (the app's come from its picker). */
    fun problems(t: ShiftType): List<String> = buildList {
        if (!nameFits(t.name)) add("name")
        if (!labelFits(t.label)) add("label")
        if (t.colorArgb !in 0L..COLOR_MAX) add("color")
        if (!restFits(t.breakMinutes)) add("rest")
    }
}
