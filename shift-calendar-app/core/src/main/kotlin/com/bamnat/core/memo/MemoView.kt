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

    /** The board's columns: a memo with two 꼬리표 is in both, one with none in "없음" last. [shiftOf] names a dated memo's 근무. */
    fun board(memos: List<Memo>, shiftOf: (LocalDate) -> String? = { null }): List<Column> {
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
        val keys = when (by) {
            BY_CHECK -> CHECK_ORDER.filter { it in groups }
            BY_COLOR -> groups.keys.sortedBy { it.toIntOrNull() ?: 0 }
            else -> groups.keys.sortedByDescending { groups[it]!!.size }
        }
        return keys.map { Column(it, labelOf(it), groups[it]!!) } +
            (if (none.isEmpty()) emptyList() else listOf(Column(NONE, noneLabel(), none)))
    }

    private fun labelOf(key: String): String = when (by) {
        BY_TAG -> "#$key"
        BY_COLOR -> if (key == "0") "기본 색" else "색 $key"
        // PC 확인 bad7d860 사용성 제안: "5" alone is unclear, so a property column says "별점 5".
        else -> if (by.startsWith("p:")) "${by.drop(2)} $key" else key
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
        val VIEWS = listOf(LIST, BOARD, TABLE, GALLERY)
        val LABELS = mapOf(LIST to "목록", BOARD to "보드", TABLE to "표", GALLERY to "갤러리")

        const val BY_TAG = "tag"
        const val BY_COLOR = "color"
        const val BY_CHECK = "check"
        const val BY_SHIFT = "shift"
        val BY_FIXED = listOf(BY_TAG, BY_SHIFT, BY_CHECK, BY_COLOR)
        val BY_LABELS = mapOf(BY_TAG to "꼬리표", BY_SHIFT to "근무", BY_CHECK to "할 일 상태", BY_COLOR to "색")
        const val SORT_TITLE = "title"
        const val NONE = "\u0000none"

        private val CHECK_ORDER = listOf("할 일", "하는 중", "다 함", "메모")

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
