package com.bamnat.app.imports

import com.bamnat.app.ui.imports.inOrder
import org.junit.Assert.assertEquals
import org.junit.Test

/** [READ-37] 1.38 PC: "12" read at the 1st's place drew 12일's cell over 1일's; the dates keep their order. */
class StripOrderTest {
    @Test fun aDateOutOfPlaceIsLeftForItsNeighbours() {
        val found = (1..30).filter { it != 12 }.map { it to 100 + it * 50 } + (12 to 152)
        val placed = inOrder(found)
        assertEquals(150, placed[1])
        assertEquals(null, placed[12])
        assertEquals(29, placed.size)
    }

    @Test fun aDateReadTwiceKeepsThePlaceInOrder() {
        val found = (1..30).map { it to 100 + it * 50 } + (12 to 160)
        assertEquals(700, inOrder(found)[12])
    }
}
