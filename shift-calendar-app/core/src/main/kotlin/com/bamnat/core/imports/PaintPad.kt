package com.bamnat.core.imports

/**
 * [EASE-13] 1.38 칩 연속 칠하기: the keypad's rules on their own, for any screen that paints days one key at a time — the
 * month's 근무 전부 넣기, a roster photo's cells to fix ([EASE-15]), and the leader's team roster ([P3-02] 리더 팀 근무표, one
 * line per member). A key puts its value on the marked day and the mark moves on: to the next doubtful day while any is
 * left, else the next day, so a month of 30 days is 30 taps. ✕ takes the day's value away and moves on, » moves on
 * leaving the day as it is, and 되돌리기 takes back one step at a time (the mark goes back to where that step was).
 *
 * The state is a value: a screen keeps it (in a view model or remembered) and puts back what each call returns. [T] is
 * whatever the screen paints with (a shift id, a roster cell, a member's code). Days are 1-based, 1..[days].
 */
data class PaintPad<T>(
    val days: Int,
    /** What was painted, by day. A day not here keeps what it had before the pad opened. */
    val cells: Map<Int, T> = emptyMap(),
    /** The day the next key goes into. */
    val cursor: Int = 1,
    /** Days to look at first (a photo's unsure cells): the mark goes from one to the next until none is left. */
    val doubtful: Set<Int> = emptySet(),
    /** Doubtful days at the start, for "고친 칸 n개" ([fixedCount]). */
    val firstDoubtful: Set<Int> = doubtful,
    /** Days whose painted value differs from what was read at the start ([start]), for "고친 칸 n개". */
    val start: Map<Int, T> = cells,
    /** Earlier steps, oldest first, at most [MAX_UNDO]. */
    val history: List<Step<T>> = emptyList(),
    /** The key pressed last, which a finger over days paints with. */
    val lastKey: T? = null,
) {
    /** One step back: the cells, the doubtful days and the mark as they were. */
    data class Step<T>(val cells: Map<Int, T>, val doubtful: Set<Int>, val cursor: Int)

    init {
        require(days >= 1) { "days must be 1 or more" }
    }

    val canUndo: Boolean get() = history.isNotEmpty()

    /** Days still to look at, in order. */
    val toCheck: List<Int> get() = doubtful.filter { it in 1..days }.sorted()

    /**
     * [EASE-15] Cells the user changed from what was there at the start (a photo's reading): "사진에서 n칸만 고쳤어요". A
     * doubtful day confirmed with the same value counts too, as it was looked at and answered.
     */
    val fixedCount: Int
        get() = (1..days).count { d -> cells[d] != start[d] }

    /** Doubtful days at the start that were answered (with any key, ✕ too). */
    val checkedCount: Int get() = (firstDoubtful - doubtful).count { it in 1..days }

    /** A key: [value] on the marked day, and the mark on. */
    fun put(value: T): PaintPad<T> = set(cursor, value).copy(lastKey = value)

    /** ✕ 지우기: the marked day back to what it had before the pad opened, and the mark on. */
    fun erase(): PaintPad<T> {
        val day = cursor
        val kept = checkpoint()
        return kept.copy(cells = cells - day, doubtful = doubtful - day, cursor = next(day, doubtful - day))
    }

    /** » 건너뛰기: the mark to the next day (or the next doubtful one), the day left as it is. */
    fun skip(): PaintPad<T> = copy(cursor = next(cursor, doubtful - cursor, answered = false))

    /** The mark on [day] (a tap on it), so the next key goes there. */
    fun tap(day: Int): PaintPad<T> = copy(cursor = day.coerceIn(1, days))

    /** The mark [delta] days on or back, within the month. */
    fun step(delta: Int): PaintPad<T> = copy(cursor = (cursor + delta).coerceIn(1, days))

    /** A finger over [over]: the last key on each of them, one step, the mark after the last. */
    fun paint(over: List<Int>): PaintPad<T> {
        val key = lastKey ?: return this
        val inside = over.filter { it in 1..days }
        if (inside.isEmpty()) return this
        val kept = checkpoint()
        return kept.copy(cells = cells + inside.associateWith { key }, doubtful = doubtful - inside.toSet(),
            cursor = (inside.max() + 1).coerceAtMost(days))
    }

    /** Several days at once ("이 순서 반복", "지난달 그대로"), one step, the mark on [cursorAfter]. */
    fun putAll(values: Map<Int, T>, cursorAfter: Int = days): PaintPad<T> {
        val inside = values.filterKeys { it in 1..days }
        if (inside.isEmpty()) return this
        val kept = checkpoint()
        return kept.copy(cells = cells + inside, doubtful = doubtful - inside.keys, cursor = cursorAfter.coerceIn(1, days))
    }

    /** 되돌리기: one step back, the mark where that step was. */
    fun undo(): PaintPad<T> {
        val last = history.lastOrNull() ?: return this
        return copy(cells = last.cells, doubtful = last.doubtful, cursor = last.cursor, history = history.dropLast(1))
    }

    private fun set(day: Int, value: T): PaintPad<T> {
        val kept = checkpoint()
        val left = doubtful - day
        return kept.copy(cells = cells + (day to value), doubtful = left, cursor = next(day, left, answered = day in doubtful))
    }

    private fun checkpoint(): PaintPad<T> = copy(history = (history + Step(cells, doubtful, cursor)).takeLast(MAX_UNDO))

    /**
     * Where the mark goes after [day]: while doubtful days are left and [day] was one of them ([answered]) or the user is
     * going through them, the next of them (round to the first); else the next day, staying on the last.
     */
    private fun next(day: Int, left: Set<Int>, answered: Boolean = true): Int {
        val todo = left.filter { it in 1..days }.sorted()
        if (todo.isNotEmpty() && (answered || day in firstDoubtful)) return todo.firstOrNull { it > day } ?: todo.first()
        return (day + 1).coerceAtMost(days)
    }

    companion object {
        const val MAX_UNDO = 60

        /** A pad on [days] days whose doubtful days come first: the mark on the first of them, else after what is there. */
        fun <T> open(days: Int, cells: Map<Int, T> = emptyMap(), doubtful: Set<Int> = emptySet()): PaintPad<T> {
            val inside = cells.filterKeys { it in 1..days }
            val doubt = doubtful.filter { it in 1..days }.toSet()
            val cursor = doubt.minOrNull() ?: ((inside.keys.maxOrNull() ?: 0) + 1).coerceAtMost(days)
            return PaintPad(days, inside, cursor, doubt, doubt, inside)
        }
    }
}
