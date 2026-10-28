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

    /** [MEMO-SEL-01] Which end of a selection a finger is moving. */
    enum class End { START, END }

    /**
     * [MEMO-SEL-01] 1.30 AY: the end a selection handle (or a long press) moved, when only one end changed and the
     * text did not: the start, the end, or null (a tap, both ends, nothing). A collapsed selection is a caret, not a drag.
     */
    fun movingEnd(beforeStart: Int, beforeEnd: Int, afterStart: Int, afterEnd: Int): End? {
        if (afterStart == afterEnd) return null
        val start = afterStart != beforeStart
        val end = afterEnd != beforeEnd
        return when {
            start && !end -> End.START
            end && !start -> End.END
            else -> null
        }
    }

    /**
     * [MEMO-SEL-01] How far to scroll this frame while a selection end is held at [y] (its line, in the box that shows,
     * [viewport] high): toward the top (minus) or the bottom (plus) inside the [edge] band, faster the closer it is,
     * [maxPerFrame] at the edge or past it; 0 elsewhere. The band is never more than a third of a short box (the
     * keyboard up), so the middle stays still.
     */
    fun edgeSpeed(y: Float, viewport: Int, edge: Float, maxPerFrame: Float): Float {
        if (viewport <= 0 || edge <= 0f || maxPerFrame <= 0f) return 0f
        val band = minOf(edge, viewport / 3f)
        return when {
            y < band -> -maxPerFrame * ((band - y) / band).coerceIn(0.1f, 1f)
            y > viewport - band -> maxPerFrame * ((y - (viewport - band)) / band).coerceIn(0.1f, 1f)
            else -> 0f
        }
    }

    /**
     * [MEMO-SEL-01] Held at the edge a while, a long memo (thousands of lines) goes faster: as is for the first second,
     * then up to 4 times by the third.
     */
    fun boost(heldMillis: Long): Float = when {
        heldMillis <= 1_000 -> 1f
        heldMillis >= 3_000 -> 4f
        else -> 1f + 3f * (heldMillis - 1_000) / 2_000f
    }

    /**
     * [MEMO-SEL-01] Where the moving end's line ([line] high, its middle at [y]) may be: inside the box, so its handle
     * stays on screen (a handle that leaves the box is taken away mid-drag). Past the edge the scroll does the rest.
     */
    fun keepInside(y: Float, viewport: Int, line: Float): Float {
        if (viewport <= 0) return y
        val half = (line / 2f).coerceAtLeast(0f)
        if (viewport <= line) return viewport / 2f
        return y.coerceIn(half, viewport - half - 1f)
    }
}
