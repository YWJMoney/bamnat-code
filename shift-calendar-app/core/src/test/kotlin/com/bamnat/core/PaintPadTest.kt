package com.bamnat.core

import com.bamnat.core.imports.PaintPad
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** [EASE-13] The keypad's rules: 30 days are 30 keys, ✕·»·되돌리기, and a photo's doubtful days first ([EASE-15]). */
class PaintPadTest {
    @Test fun aMonthIsOneKeyADay() {
        var pad = PaintPad.open<String>(30)
        val order = listOf("D", "D", "E", "E", "N", "N", "O", "O")
        repeat(30) { pad = pad.put(order[it % order.size]) }
        assertEquals(30, pad.cells.size)
        assertEquals("D", pad.cells[1]); assertEquals("O", pad.cells[8]); assertEquals("N", pad.cells[30])
        // The mark stays on the last day.
        assertEquals(30, pad.cursor)
    }

    @Test fun eraseTakesTheDayAwayAndMovesOn() {
        val pad = PaintPad.open(30, mapOf(1 to "D", 2 to "E")).tap(2).erase()
        assertEquals(mapOf(1 to "D"), pad.cells)
        assertEquals(3, pad.cursor)
    }

    @Test fun skipLeavesTheDay() {
        val pad = PaintPad.open<String>(30).put("D").skip().put("N")
        assertEquals(mapOf(1 to "D", 3 to "N"), pad.cells)
        assertEquals(4, pad.cursor)
    }

    @Test fun undoGoesBackAStepWithTheMark() {
        val pad = PaintPad.open<String>(30).put("D").put("E").tap(10).put("N")
        val back = pad.undo()
        assertEquals(mapOf(1 to "D", 2 to "E"), back.cells)
        assertEquals(10, back.cursor)
        assertEquals(mapOf(1 to "D"), back.undo().cells)
        assertFalse(PaintPad.open<String>(3).canUndo)
    }

    @Test fun doubtfulDaysComeFirstThenOn() {
        val read = (1..30).associateWith { "D" }
        var pad = PaintPad.open(30, read, doubtful = setOf(5, 12, 20))
        assertEquals(5, pad.cursor)
        pad = pad.put("E")
        assertEquals(12, pad.cursor)
        pad = pad.put("D") // the same as read: looked at, not changed
        assertEquals(20, pad.cursor)
        pad = pad.put("N")
        assertTrue(pad.toCheck.isEmpty())
        assertEquals(3, pad.checkedCount)
        // Two cells differ from the photo.
        assertEquals(2, pad.fixedCount)
        // With none left the mark goes day by day.
        assertEquals(21, pad.cursor)
    }

    @Test fun skippingADoubtfulDayKeepsItToCheck() {
        val pad = PaintPad.open(30, emptyMap<Int, String>(), doubtful = setOf(5, 12)).skip()
        assertEquals(12, pad.cursor)
        assertEquals(listOf(5, 12), pad.toCheck)
    }

    @Test fun paintAndPutAllAreOneStep() {
        val pad = PaintPad.open<String>(30).put("N").paint(listOf(2, 3, 4))
        assertEquals("N", pad.cells[4]); assertEquals(5, pad.cursor)
        val all = pad.putAll(mapOf(10 to "O", 11 to "O", 40 to "O"))
        assertEquals(setOf(1, 2, 3, 4, 10, 11), all.cells.keys)
        assertEquals(pad.cells, all.undo().cells)
    }

    @Test fun undoIsKeptToItsLimit() {
        var pad = PaintPad.open<Int>(31)
        repeat(100) { pad = pad.tap(1).put(it) }
        assertEquals(PaintPad.MAX_UNDO, pad.history.size)
    }
}
