package com.bamnat.core.memo

import java.time.LocalDate

/**
 * [TAB-02] 1.36 메모장 보기 (2차 조사 제안 22, 사용성 제안 "같은 메모를 다른 방식으로"): the same memos as a 목록, a 보드 of 칸 by
 * 꼬리표·속성·근무·색·체크, a 표 of 속성 열, or a 갤러리 of big cards. A view never changes a memo; it is kept in settings and a recipe's
 * `tabView` piece carries it ([TAB-07]).
 */
data class MemoView(
    val view: String = LIST,
    /** A 보드's 묶음: [BY_TAG], [BY_COLOR], [BY_CHECK], [BY_SHIFT], or "p:<칸 이름>". */
    val by: String = BY_TAG,
    /** "" keeps the 메모장's own order; "p:<칸 이름>" (작은 것부터), "-p:<칸 이름>" (큰 것부터), [SORT_TITLE]. */
    val sort: String = "",
) {
    val isDefault: Boolean get() = this == MemoView()

    fun encode(): String = if (isDefault) "" else listOf(view, by, sort).joinToString("|")

    /** A 보드 column: its key, the words on its head, and its memos in the view's order. */
    data class Column(val key: String, val label: String, val memos: List<Memo>)

    /**
     * The board's columns: a memo with two 꼬리표 is in both, one with none in "없음" last. [shiftOf] names a dated memo's 근무.
     * [extra] (누락 점검 N9, PC 확인 dc7f51ae "상태로 묶으면 값이 있는 칸만 생겨 '할 일'로 옮길 길이 없어요"): the values a 칸 묶음 can take
     * though no memo has them yet ([choicesFor]), shown as empty 칸 to drop into, after the full ones.
     */
    fun board(memos: List<Memo>, shiftOf: (LocalDate) -> String? = { null }, extra: List<String> = emptyList()): List<Column> {
        val sorted = sorted(memos)
        val groups = LinkedHashMap<String, MutableList<Memo>>()
        val none = ArrayList<Memo>()
        fun put(k: String?, m: Memo) = if (k.isNullOrBlank()) none.add(m) else groups.getOrPut(k) { ArrayList() }.add(m)
        for (m in sorted) when {
            by == BY_TAG -> if (m.tags.isEmpty()) none.add(m) else m.tags.forEach { put(it, m) }
            by == BY_COLOR -> put(m.colorIndex.toString(), m)
            by == BY_CHECK -> put(checkState(m), m)
            by == BY_SHIFT -> put(m.date?.let(shiftOf), m)
            by.startsWith("p:") -> put(MemoProps.of(m).firstOrNull { it.label == by.drop(2) }?.raw, m)
            else -> none.add(m)
        }
        // PC 확인 5ff9ae28 "열 순서가 매번 바뀌어요": the 칸 keep their place when a card moves — a 칸 묶음 in [extra]'s order (할 일 · 하는 중 ·
        // 끝, 높음 · 보통 · 낮음) then the rest in number or word order, 꼬리표 in word order; empty [extra] 칸 sit in their place.
        val known = if (by.startsWith("p:")) extra.map { it.trim() }.filter { it.isNotEmpty() }.distinct() else emptyList()
        val keys = when {
            by == BY_CHECK -> CHECK_ORDER.filter { it in groups }
            by == BY_COLOR -> groups.keys.sortedBy { it.toIntOrNull() ?: 0 }
            by == BY_SHIFT -> groups.keys.sortedByDescending { groups[it]!!.size }
            else -> (groups.keys + known).distinct().sortedWith(compareBy<String> { k -> known.indexOf(k).let { if (it < 0) Int.MAX_VALUE else it } }
                .thenComparator { a, b -> natural(a, b) })
        }
        return keys.map { Column(it, labelOf(it), groups[it].orEmpty()) } +
            (if (none.isEmpty()) emptyList() else listOf(Column(NONE, noneLabel(), none)))
    }

    private fun labelOf(key: String): String = when (by) {
        BY_TAG -> "#$key"
        BY_COLOR -> if (key == "0") "기본 색" else "색 $key"
        // PC 확인 bad7d860 사용성 제안: "5" alone is unclear, so a property column says "별점 5".
        // PC 확인 5ff9ae28: a 금액 칸 reads as the card does, "금액 15,000원", not "금액 15000".
        else -> if (by.startsWith("p:")) "${by.drop(2)} ${money(by.drop(2), key) ?: key}" else key
    }

    private fun money(label: String, key: String): String? = MemoProps.read(label, key).let { p ->
        if (p.num != null && (p.type == MemoProps.Type.MONEY || (p.type == MemoProps.Type.NUM && MemoProps.isMoneyLabel(label)))) "%,d원".format(p.num.toLong()) else null
    }

    private fun natural(a: String, b: String): Int {
        val x = a.replace(",", "").toDoubleOrNull(); val y = b.replace(",", "").toDoubleOrNull()
        return if (x != null && y != null) x.compareTo(y) else a.compareTo(b)
    }

    private fun noneLabel(): String = when {
        by == BY_TAG -> "꼬리표 없음"
        by == BY_SHIFT -> "근무 없음"
        by.startsWith("p:") -> "${by.drop(2)} 없음"
        else -> "없음"
    }

    /** [memos] in this view's order; ties keep the order they came in. */
    fun sorted(memos: List<Memo>): List<Memo> {
        if (sort.isEmpty()) return memos
        if (sort == SORT_TITLE) return memos.sortedBy { it.title.ifBlank { it.preview } }
        val down = sort.startsWith("-")
        val label = sort.removePrefix("-").removePrefix("p:")
        val keyed = memos.map { m -> m to MemoProps.of(m).firstOrNull { it.label == label } }
        val (has, missing) = keyed.partition { it.second != null }
        val cmp = Comparator<Pair<Memo, MemoProps.Prop?>> { a, b ->
            val x = a.second!!; val y = b.second!!
            if (x.num != null && y.num != null) x.num.compareTo(y.num) else x.raw.compareTo(y.raw)
        }
        val ordered = has.sortedWith(if (down) cmp.reversed() else cmp)
        return (ordered + missing).map { it.first }
    }

    companion object {
        const val LIST = "list"
        const val BOARD = "board"
        const val TABLE = "table"
        const val GALLERY = "gallery"
        /** [TAB-02] 누락 점검 N9 (2차 5-7 "보기: 목록·카드·보드·표·달력·갤러리"): dated memos on a month. */
        const val CALENDAR = "cal"
        val VIEWS = listOf(LIST, BOARD, TABLE, CALENDAR, GALLERY)
        val LABELS = mapOf(LIST to "목록", BOARD to "보드", TABLE to "표", CALENDAR to "달력", GALLERY to "갤러리")

        const val BY_TAG = "tag"
        const val BY_COLOR = "color"
        const val BY_CHECK = "check"
        const val BY_SHIFT = "shift"
        val BY_FIXED = listOf(BY_TAG, BY_SHIFT, BY_CHECK, BY_COLOR)
        val BY_LABELS = mapOf(BY_TAG to "꼬리표", BY_SHIFT to "근무", BY_CHECK to "체크 진행", BY_COLOR to "색")
        const val SORT_TITLE = "title"
        const val NONE = "\u0000none"

        private val CHECK_ORDER = listOf("할 일", "하는 중", "다 함", "메모")

        /** Whether a card can be moved between [by]'s columns (근무 and 체크 진행 come from the day and the ticks, not the card). */
        fun canMove(by: String): Boolean = by == BY_TAG || by == BY_COLOR || by.startsWith("p:")

        /**
         * [TAB-02] 누락 점검 N9 (보드에서 카드를 끌어 다른 칸으로): [m] as it reads in column [to] after leaving [from] — the 칸's value set
         * (or its line gone for "없음"), a #꼬리표 swapped, or the colour. Null when [by] cannot move a card or nothing changes.
         */
        fun moved(m: Memo, by: String, from: String, to: String): Memo? {
            if (from == to || !canMove(by)) return null
            return when {
                by.startsWith("p:") -> MemoProps.set(m, by.drop(2), if (to == NONE) "" else to)
                by == BY_COLOR -> to.toIntOrNull()?.let { m.copy(colorIndex = it) } ?: if (to == NONE) m.copy(colorIndex = 0) else null
                else -> {
                    fun swap(t: String) = if (from == NONE) t else t.replace(Regex("#" + Regex.escape(from) + "(?![\\p{L}\\p{N}_])"), if (to == NONE) "" else "#$to")
                        .replace(Regex(" {2,}"), " ").trimEnd()
                    val inText = from != NONE && (m.title.contains("#$from") || m.body.contains("#$from") || m.checklist.any { it.text.contains("#$from") })
                    when {
                        from == NONE || !inText -> if (to == NONE) null else m.copy(body = (m.body.trimEnd() + if (m.body.isBlank()) "#$to" else " #$to"))
                        else -> m.copy(title = swap(m.title), body = swap(m.body), checklist = m.checklist.map { it.copy(text = swap(it.text)) })
                    }
                }
            }?.takeIf { it != m }
        }

        /**
         * [TAB-02] 누락 점검 N9 메모 '달력' 보기: the day [m] sits on — its own date, else the first 날짜 칸 written in it ("마감일: 11월 5일"), so a
         * 일반 메모 with a date line still lands on the month. Null when it has neither.
         */
        fun dayOf(m: Memo): java.time.LocalDate? = m.date ?: MemoProps.of(m).firstOrNull { it.type == MemoProps.Type.DATE }?.date

        /** [dayOf] for each of [memos] that has one, by day, in the order given. */
        fun byDay(memos: List<Memo>): Map<java.time.LocalDate, List<Memo>> =
            memos.mapNotNull { m -> dayOf(m)?.let { it to m } }.groupBy({ it.first }, { it.second })

        /**
         * [TAB-01] The values a 고르기 칸 named [label] offers: those already written in [memos], then a 틀's 고르기 choices for it ([fromTemplates]),
         * then the usual words for a 칸 named like 상태·우선순위 (할 일 · 하는 중 · 끝), at most [MemoProps.MAX_CHOICES].
         */
        fun choicesFor(label: String, memos: List<Memo>, fromTemplates: List<String> = emptyList()): List<String> =
            (MemoProps.choices(memos, label) + fromTemplates + usual(label)).map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(MemoProps.MAX_CHOICES)

        /** The values a 칸 named [label] is known to take whatever the memos say — a 틀's choices, then the usual words — in their own order (a 보드's 칸 order). */
        fun knownChoices(label: String, fromTemplates: List<String> = emptyList()): List<String> =
            (fromTemplates + usual(label)).map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(MemoProps.MAX_CHOICES)

        private fun usual(label: String): List<String> = when {
            listOf("상태", "진행").any { label.contains(it) } -> listOf("할 일", "하는 중", "끝")
            listOf("우선", "중요").any { label.contains(it) } -> listOf("높음", "보통", "낮음")
            label.contains("결과") -> listOf("성공", "보류", "실패")
            else -> emptyList()
        }

        /** A checklist's 상태 for the board: none ticked, some, all; a plain memo is "메모". */
        fun checkState(m: Memo): String = when {
            !m.isChecklist || m.itemCount == 0 -> "메모"
            m.checkedCount == 0 -> "할 일"
            m.checkedCount < m.itemCount -> "하는 중"
            else -> "다 함"
        }

        /** A 칸 name or 묶음 written in a recipe: [BY_FIXED], or "p:" + a 칸 name [MemoProps.isLabel] takes. */
        fun validBy(s: String): Boolean = s in BY_FIXED || (s.startsWith("p:") && MemoProps.isLabel(s.drop(2)))

        fun validSort(s: String): Boolean = s.isEmpty() || s == SORT_TITLE ||
            (s.removePrefix("-").startsWith("p:") && MemoProps.isLabel(s.removePrefix("-").drop(2)))

        /** Read back from [encode]; anything it does not know falls back to the default part by part. */
        fun decode(raw: String?): MemoView {
            val p = raw.orEmpty().split('|')
            if (p.size < 3) return MemoView()
            return MemoView(
                view = p[0].takeIf { it in VIEWS } ?: LIST,
                by = p[1].takeIf(::validBy) ?: BY_TAG,
                sort = p.drop(2).joinToString("|").takeIf(::validSort) ?: "",
            )
        }
    }
}
