package com.bamnat.core

import com.bamnat.core.layout.CellList
import org.junit.Test
import org.junit.Assert.assertEquals

class CellListTest {
    @Test fun everyItemGetsALineThenTheRestGrowInOrder() {
        val f = CellList.fit(items = 2, slots = 5)
        assertEquals(listOf(3, 2), f.lines)
        assertEquals(0, f.hidden)
    }

    @Test fun shortItemsKeepOneLineAndLeaveRoomToTheLongOnes() {
        assertEquals(listOf(1, 3, 1), CellList.fit(3, 6, listOf(1, 3, 1)).lines)
    }

    @Test fun tooManyItemsShowPlusOnTheLastLine() {
        val f = CellList.fit(items = 5, slots = 3)
        assertEquals(listOf(1, 1), f.lines)
        assertEquals(3, f.hidden)
        assertEquals(3, f.used)
    }

    @Test fun oneSlotOnlySaysHowMany() {
        val f = CellList.fit(items = 2, slots = 1)
        assertEquals(emptyList<Int>(), f.lines)
        assertEquals(2, f.hidden)
    }

    @Test fun exactFitHasNoPlus() {
        val f = CellList.fit(items = 3, slots = 3)
        assertEquals(listOf(1, 1, 1), f.lines)
        assertEquals(0, f.hidden)
    }

    @Test fun noOneTakesMoreThanTheMax() {
        assertEquals(listOf(CellList.MAX_EACH), CellList.fit(1, 10).lines)
    }

    @Test fun slotsCountWholeLines() {
        assertEquals(3, CellList.slots(height = 44f, lineHeight = 13f, gap = 2f))
        assertEquals(0, CellList.slots(height = 10f, lineHeight = 13f, gap = 2f))
    }
}
