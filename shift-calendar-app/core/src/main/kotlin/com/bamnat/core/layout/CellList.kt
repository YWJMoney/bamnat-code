package com.bamnat.core.layout

/**
 * [CELL-01] 1.39 날짜 칸에 일감·메모를 줄줄이: how the lines a day cell has room for are shared among its items.
 *
 * 사용자 관찰(1.37 출시 뒤, 일감 위주 사용자): 칸의 일감 이름이 '스윈이'처럼 잘리고 메모가 안 보여, 칸 안에 여러 일을 여러 줄로
 * 읽고 싶다. Every item gets one line first, in order; lines left over go to the items again, one more each in order, up to [MAX_EACH],
 * so a short day reads its long names whole and a busy day still shows every item. Where the items do not fit even one line each, the
 * last line says how many more ("+2") and the items before it keep one line.
 */
object CellList {
    /** No item takes more than this many lines, so one long memo cannot push the others out. */
    const val MAX_EACH = 3

    /** [lines] for each item shown (in order) and how many are left out, said on the last line. */
    data class Fit(val lines: List<Int>, val hidden: Int) {
        val shown: Int get() = lines.size
        /** Lines used, the "+n" line included. */
        val used: Int get() = lines.sum() + if (hidden > 0) 1 else 0
    }

    /**
     * [items] the item count, [slots] the whole lines the cell has room for, [wants] how many lines each item would take whole
     * (missing = [MAX_EACH]).
     */
    fun fit(items: Int, slots: Int, wants: List<Int> = emptyList()): Fit {
        if (items <= 0 || slots <= 0) return Fit(emptyList(), items.coerceAtLeast(0))
        if (items > slots) {
            // Every slot but the last an item, the last "+n"; one slot holds only "+n".
            val shown = slots - 1
            return Fit(List(shown) { 1 }, items - shown)
        }
        val want = List(items) { i -> (wants.getOrNull(i) ?: MAX_EACH).coerceIn(1, MAX_EACH) }
        val lines = IntArray(items) { 1 }
        var left = slots - items
        var grew = true
        while (left > 0 && grew) {
            grew = false
            for (i in 0 until items) {
                if (left == 0) break
                if (lines[i] < want[i]) { lines[i]++; left--; grew = true }
            }
        }
        return Fit(lines.toList(), 0)
    }

    /** Whole lines of [lineHeight] with [gap] between them in [height] (all in the same unit). */
    fun slots(height: Float, lineHeight: Float, gap: Float): Int =
        if (lineHeight <= 0f || height < lineHeight) 0 else ((height + gap) / (lineHeight + gap)).toInt()
}
