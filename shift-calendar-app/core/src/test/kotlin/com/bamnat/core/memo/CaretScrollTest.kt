package com.bamnat.core.memo

import com.bamnat.core.memo.CaretScroll.End
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** [MEMO-SEL-01][MEMO-SEL-04] 1.30 AY: a selection handle dragged to the memo's edge scrolls it, following the end that moves. */
class CaretScrollTest {
    @Test fun theEndThatMovedIsFollowed() {
        // The lower handle pulled down: the end moves, the start stays.
        assertEquals(End.END, CaretScroll.movingEnd(10, 30, 10, 31))
        // The upper handle pulled up: only the start moves (before, nothing scrolled for it).
        assertEquals(End.START, CaretScroll.movingEnd(10, 30, 9, 30))
        // Pulled past the other end: the selection turns around, still the start that moved.
        assertEquals(End.START, CaretScroll.movingEnd(10, 30, 40, 30))
    }

    @Test fun aTapALongPressOrNothingIsNotADrag() {
        // A tap puts the caret down: collapsed.
        assertNull(CaretScroll.movingEnd(10, 30, 12, 12))
        // A long press picks a word: both ends.
        assertNull(CaretScroll.movingEnd(12, 12, 10, 15))
        // Nothing changed.
        assertNull(CaretScroll.movingEnd(10, 30, 10, 30))
        // From a caret, one end pulled out (a word grown by a handle) is a drag.
        assertEquals(End.END, CaretScroll.movingEnd(12, 12, 12, 20))
    }

    @Test fun nearTheBottomItScrollsDownFasterTheCloserItIs() {
        val viewport = 2000
        assertEquals(0f, CaretScroll.edgeSpeed(1000f, viewport, edge = 150f, maxPerFrame = 24f))
        val entering = CaretScroll.edgeSpeed(1880f, viewport, 150f, 24f)
        val deep = CaretScroll.edgeSpeed(1960f, viewport, 150f, 24f)
        assertTrue(entering > 0f && deep > entering)
        assertEquals(24f, CaretScroll.edgeSpeed(1999f, viewport, 150f, 24f), 0.5f)
        // A finger past the box (on the bar under it) goes no faster than the most.
        assertEquals(24f, CaretScroll.edgeSpeed(2400f, viewport, 150f, 24f))
    }

    @Test fun nearTheTopItScrollsUp() {
        assertTrue(CaretScroll.edgeSpeed(100f, 2000, 150f, 24f) < 0f)
        assertEquals(-24f, CaretScroll.edgeSpeed(-300f, 2000, 150f, 24f))
        // Right at the band's start it already moves a little, never a dead stop.
        assertTrue(CaretScroll.edgeSpeed(149f, 2000, 150f, 24f) <= -2.4f + 0.01f)
    }

    @Test fun aShortBoxWithTheKeyboardUpKeepsAStillMiddle() {
        // 300 px over the keyboard: the band is 100, not 150, so the middle third does not scroll.
        assertEquals(0f, CaretScroll.edgeSpeed(150f, 300, 150f, 24f))
        assertTrue(CaretScroll.edgeSpeed(250f, 300, 150f, 24f) > 0f)
        assertEquals(0f, CaretScroll.edgeSpeed(500f, 0, 150f, 24f))
    }

    @Test fun heldLongerALongMemoGoesFaster() {
        assertEquals(1f, CaretScroll.boost(0))
        assertEquals(1f, CaretScroll.boost(1_000))
        assertEquals(2.5f, CaretScroll.boost(2_000), 0.001f)
        assertEquals(4f, CaretScroll.boost(10_000))
    }

    @Test fun theMovingLineStaysInsideTheBox() {
        assertEquals(500f, CaretScroll.keepInside(500f, 1000, 60f))
        assertEquals(969f, CaretScroll.keepInside(1400f, 1000, 60f))
        assertEquals(30f, CaretScroll.keepInside(-200f, 1000, 60f))
        assertEquals(20f, CaretScroll.keepInside(5f, 40, 60f))
    }
}
