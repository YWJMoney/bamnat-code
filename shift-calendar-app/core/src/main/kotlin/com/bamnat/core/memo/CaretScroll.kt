package com.bamnat.core.memo

/**
 * [UX-11] 1.25 Z (U10): where a memo's scroll goes so the line being typed shows, worked out on the memo's own scroll
 * only. Before, the caret was brought into view through every scrolling parent up to the window, and right after the
 * memo opened (the screen still sliding in, the keyboard's height still changing) that moved the whole window up when
 * the keyboard closed. Null means "leave it where it is": a caret already in view never moves the memo.
 */
object CaretScroll {
    /**
     * [scroll]: how far the memo is scrolled now; [viewport]: the height that shows; [top]..[bottom]: the caret's line in
     * the memo's content; [room]: space kept under the line (above the keyboard) and half of it over the line.
     */
    fun target(scroll: Int, viewport: Int, top: Float, bottom: Float, room: Float, max: Int = Int.MAX_VALUE): Int? {
        if (viewport <= 0) return null
        val want = when {
            bottom + room > scroll + viewport -> (bottom + room - viewport).toInt()
            top - room / 2 < scroll -> (top - room / 2).toInt()
            else -> return null
        }
        return want.coerceIn(0, max.coerceAtLeast(0)).takeIf { it != scroll }
    }

    /**
     * Whether a change of the visible height should bring the caret back into view: only when it shrank (the keyboard
     * opening), never when it grew (the keyboard closing), which is the moment the memo must stay still.
     */
    fun followResize(before: Int, after: Int): Boolean = after < before
}
