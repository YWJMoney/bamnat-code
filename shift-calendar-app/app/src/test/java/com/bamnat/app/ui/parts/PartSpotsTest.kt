package com.bamnat.app.ui.parts

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** [BLU-09] 누락 점검 N4: a tap picks the deepest part drawn there, again the part around it, and from the outermost back in. */
class PartSpotsTest {
    private val spots = PartSpots().apply {
        spots[1] = "cal.cell" to Rect(0f, 0f, 100f, 100f)
        spots[2] = "cal.cell.date" to Rect(10f, 10f, 40f, 40f)
        spots[3] = "cal.cell" to Rect(100f, 0f, 200f, 100f)
        spots[4] = "cal.head.title" to Rect(0f, -50f, 200f, -10f)
    }

    @Test
    fun aTapGoesInThenOut() {
        val at = Offset(20f, 20f)
        assertEquals("cal.cell.date", spots.pick(at, null))
        assertEquals("cal.cell", spots.pick(at, "cal.cell.date"))
        assertEquals("cal.cell.date", spots.pick(at, "cal.cell"))
        // Another part open: the tap starts from the deepest again.
        assertEquals("cal.cell.date", spots.pick(at, "cal.head.title"))
        assertEquals("cal.cell", spots.pick(Offset(150f, 50f), null))
        assertNull(spots.pick(Offset(500f, 500f), null))
        assertEquals(2, spots.boxes("cal.cell").size)
    }
}
