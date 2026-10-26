package com.bamnat.core.memo

import com.bamnat.core.plaza.Choseong
import java.time.LocalDate

/**
 * [MEMO-03][MEMO-04] 1.27 AK 순번 451: 메모 찾기. Every word of the query has to fit the memo one of these ways:
 *  - the letters, any case ("키 반납");
 *  - 초성 only ("ㅇㄱ" finds 야간, "ㅇㅅㅇㄱ" 인수인계), read with [Choseong] as the company search does;
 *  - a #tag's beginning ("#인수" finds #인수인계);
 *  - the memo's date: "10월", "19일", "10월 19일", "10/19", "2026년", "오늘", "내일", "어제", "모레";
 *  - the shift worked that day, by its name ("야간"), from the calendar the memo belongs to.
 * The text a memo is searched by is made once per saved version ([Cache]), not again on every key.
 */
object MemoSearch {
    /** What one memo is searched by. */
    class Index(memo: Memo) {
        /** The memo as last listed (its pin, colour or date may change without its text). */
        @Volatile var memo: Memo = memo
            internal set
        /** Title, body and items, lower case, in one string. */
        val text: String = buildString {
            append(memo.title.lowercase()); append('\n')
            append(memo.body.lowercase())
            memo.checklist.forEach { append('\n'); append(it.text.lowercase()) }
        }
        /** The 초성 of [text], made the first time a 초성 query needs it. */
        val choseong: String by lazy { Choseong.of(text) }
        val tags: List<String> = memo.tags.map { it.lowercase() }
    }

    /** Indexes kept by memo id and saved version: typing a query reuses them. */
    class Cache {
        private val kept = HashMap<Long, Index>()

        @Synchronized
        fun of(memo: Memo): Index {
            val old = kept[memo.id]
            // A saved change of the text always moves the save time; pin, colour and calendars do not touch the text.
            if (old != null && old.memo.updatedAtMillis == memo.updatedAtMillis && memo.updatedAtMillis != 0L) {
                old.memo = memo
                return old
            }
            return Index(memo).also { if (memo.id != 0L) kept[memo.id] = it }
        }

        /** Forgets memos that are gone. */
        @Synchronized
        fun keepOnly(ids: Set<Long>) { kept.keys.retainAll(ids) }

        @Synchronized
        fun size(): Int = kept.size
    }

    fun words(query: String): List<String> = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }

    private val MONTH_DAY = Regex("^(\\d{1,2})/(\\d{1,2})$")
    private val MONTH = Regex("^(\\d{1,2})월$")
    private val DAY = Regex("^(\\d{1,2})일$")
    private val YEAR = Regex("^(\\d{4})년$")

    /** Whether [word] names [date]; null when [word] is not a date word at all. */
    fun dateFits(word: String, date: LocalDate?, today: LocalDate): Boolean? {
        val w = word.trim()
        val rel = when (w) { "오늘" -> 0L; "내일" -> 1L; "모레" -> 2L; "어제" -> -1L; "그제", "그저께" -> -2L; else -> null }
        if (rel != null) return date == today.plusDays(rel)
        MONTH_DAY.find(w)?.let { m -> return date != null && date.monthValue == m.groupValues[1].toInt() && date.dayOfMonth == m.groupValues[2].toInt() }
        MONTH.find(w)?.let { m -> return date != null && date.monthValue == m.groupValues[1].toInt() }
        DAY.find(w)?.let { m -> return date != null && date.dayOfMonth == m.groupValues[1].toInt() }
        YEAR.find(w)?.let { m -> return date != null && date.year == m.groupValues[1].toInt() }
        return null
    }

    /**
     * Whether [index] fits every word of [query]. [shiftOn] names the shift worked on a date (null for none or
     * not known), for memos with a date.
     */
    fun matches(index: Index, query: String, today: LocalDate, shiftOn: (LocalDate) -> String? = { null }): Boolean =
        matches(index, words(query), today, shiftOn)

    fun matches(index: Index, words: List<String>, today: LocalDate, shiftOn: (LocalDate) -> String? = { null }): Boolean {
        if (words.isEmpty()) return true
        val date = index.memo.date
        return words.all { raw ->
            val w = raw.lowercase()
            if (index.text.contains(w)) return@all true
            if (w.startsWith("#") && w.length > 1 && index.tags.any { it.startsWith(w.substring(1)) }) return@all true
            if (Choseong.isQuery(w) && index.choseong.contains(w.filter { !it.isWhitespace() })) return@all true
            if (dateFits(w, date, today) == true) return@all true
            if (date != null) shiftOn(date)?.lowercase()?.let { name -> if (name.isNotEmpty() && name.contains(w)) return@all true }
            false
        }
    }

    /** Characters a memo card draws at most: a 50,000-letter memo is laid out as its start, not whole. */
    const val CARD_CHARS = 600

    /**
     * What a card shows of [text]: its start, or, when a query word is found further on, the lines around it with "…".
     */
    fun excerpt(text: String, query: String, max: Int = CARD_CHARS): String {
        val t = text.trim()
        val words = words(query).map { it.removePrefix("#") }.filter { it.isNotEmpty() }
        val at = words.mapNotNull { w -> t.indexOf(w, ignoreCase = true).takeIf { it >= 0 } }.minOrNull()
        if (at == null || at + 40 < max) return if (t.length <= max) t else t.substring(0, max).trimEnd() + "…"
        // Start at the line (or the space) a little before the word, so it reads as a sentence.
        val back = maxOf(0, at - 80)
        val line = t.lastIndexOf('\n', at).takeIf { it >= back }?.plus(1)
        val start = line ?: t.indexOf(' ', back).takeIf { it in back until at }?.plus(1) ?: back
        val end = minOf(t.length, start + max)
        return "…" + t.substring(start, end).trim() + if (end < t.length) "…" else ""
    }
}
