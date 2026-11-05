package com.bamnat.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

/** [TIME-03] A flick turns the time wheel only a few rows, and the window says which time is about to change. */
class TimeWheelFlingTest {
    @Test
    fun slowReleaseSettlesOnNearestRow() {
        assertEquals(0, wheelFlingRows(0f))
        assertEquals(0, wheelFlingRows(-300f))
    }

    @Test
    fun flickMovesAtMostTwoRowsEitherWay() {
        assertEquals(1, wheelFlingRows(1000f))
        assertEquals(-1, wheelFlingRows(-1000f))
        assertEquals(WHEEL_MAX_FLING_ROWS, wheelFlingRows(20_000f))
        assertEquals(-WHEEL_MAX_FLING_ROWS, wheelFlingRows(-20_000f))
        // 18:30 can no longer become 22:30 or 02:30 from one flick (PC rec-70710298-3).
        assertTrue(WHEEL_MAX_FLING_ROWS <= 2)
    }

    @Test
    fun changeNoteNamesThePartAndBothTimes() {
        assertEquals("끝 18:30 → 07:30으로 바뀌어요", timeChangeNote("끝", LocalTime.of(18, 30), LocalTime.of(7, 30)))
        assertEquals("09:00 → 09:10으로 바뀌어요", timeChangeNote(null, LocalTime.of(9, 0), LocalTime.of(9, 10)))
        assertNull(timeChangeNote("시작", LocalTime.of(9, 0), LocalTime.of(9, 0)))
        // Two changes stay on one line.
        assertEquals(
            "시작 07:00 → 08:00 · 끝 18:30 → 19:00으로 바뀌어요",
            timeChangeNote(listOf(Triple("시작", LocalTime.of(7, 0), LocalTime.of(8, 0)), Triple("끝", LocalTime.of(18, 30), LocalTime.of(19, 0)))),
        )
        assertEquals(
            "끝 18:30 → 19:00으로 바뀌어요",
            timeChangeNote(listOf(Triple("시작", LocalTime.of(7, 0), LocalTime.of(7, 0)), Triple("끝", LocalTime.of(18, 30), LocalTime.of(19, 0)))),
        )
    }
}
