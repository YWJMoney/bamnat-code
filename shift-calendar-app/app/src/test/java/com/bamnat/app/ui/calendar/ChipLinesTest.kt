package com.bamnat.app.ui.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 2.0.0 [RVH-71] 검토 HQ-87: how [chipLines] puts chips on lines before any go into "+n". */
class ChipLinesTest {
    @Test fun oneLineWhenTheyFit() = assertEquals(listOf(0, 0, 0), chipLines(listOf(30, 30, 30), room = 100, gap = 5, lines = 2))

    @Test fun theNextLineTakesWhatDoesNotFit() =
        assertEquals(listOf(0, 0, 1, 1), chipLines(listOf(40, 40, 40, 40), room = 100, gap = 5, lines = 2))

    @Test fun moreThanTheLinesIsNull() = assertNull(chipLines(listOf(60, 60, 60), room = 100, gap = 5, lines = 2))

    @Test fun theButtonNeedsItsPlaceToo() {
        assertEquals(listOf(0, 0), chipLines(listOf(40, 40), room = 100, gap = 5, lines = 1))
        assertNull(chipLines(listOf(40, 40), room = 100, gap = 5, lines = 1, more = 20))
        assertEquals(listOf(0, 0), chipLines(listOf(40, 40), room = 100, gap = 5, lines = 2, more = 20))
    }

    @Test fun aChipWiderThanTheRowNeverFits() = assertNull(chipLines(listOf(120), room = 100, gap = 5, lines = 2))
}
