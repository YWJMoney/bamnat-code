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
        // Start and end together stay one short line; a night's end says 다음날.
        assertEquals(
            "07:00~19:00 → 06:30~19:30으로 바뀌어요",
            rangeChangeNote(LocalTime.of(7, 0), LocalTime.of(19, 0), LocalTime.of(6, 30), LocalTime.of(19, 30)),
        )
        assertEquals(
            "19:00~07:00 → 20:00~다음날 08:00으로 바뀌어요",
            rangeChangeNote(LocalTime.of(19, 0), LocalTime.of(7, 0), LocalTime.of(20, 0), LocalTime.of(8, 0)),
        )
        assertEquals("끝 07:00 → 다음날 08:00으로 바뀌어요", rangeChangeNote(LocalTime.of(19, 0), LocalTime.of(7, 0), LocalTime.of(19, 0), LocalTime.of(8, 0)))
        assertEquals("시작 09:00 → 08:30으로 바뀌어요", rangeChangeNote(LocalTime.of(9, 0), LocalTime.of(18, 0), LocalTime.of(8, 30), LocalTime.of(18, 0)))
        assertNull(rangeChangeNote(LocalTime.of(9, 0), LocalTime.of(18, 0), LocalTime.of(9, 0), LocalTime.of(18, 0)))
        // Large text: only the new times, so two lines hold them whole.
        assertEquals(
            "20:00~다음날 08:00으로 바뀌어요",
            rangeChangeNote(LocalTime.of(19, 0), LocalTime.of(7, 0), LocalTime.of(20, 0), LocalTime.of(8, 0), short = true),
        )
    }

    @Test
    fun endTabOffersEndsThatFitTheStart() {
        val choices = endChoices(LocalTime.of(19, 0), listOf(LocalTime.of(7, 0), LocalTime.of(19, 0), LocalTime.of(9, 0)), original = LocalTime.of(7, 0))
        // 야간 19:00: 03·04·05·07시 by length, then 09:00; never its own 19:00 start, 07:00 only once.
        assertEquals(listOf(LocalTime.of(3, 0), LocalTime.of(4, 0), LocalTime.of(5, 0), LocalTime.of(7, 0), LocalTime.of(9, 0)), choices.map { it.first })
        assertEquals("다음날 07:00 · 12시간 · 원래", choices[3].second)
        assertEquals("다음날 09:00", choices[4].second)
        // A day shift's ends stay on the same day.
        assertEquals("17:00 · 8시간", endChoices(LocalTime.of(9, 0), emptyList()).first().second)
        // With a break the length is the work after it, as the line above the tabs counts it: 09:00 + 1시간 휴게 → 18:00.
        assertEquals(LocalTime.of(18, 0) to "18:00 · 8시간 근무 · 원래", endChoices(LocalTime.of(9, 0), emptyList(), LocalTime.of(18, 0), 60).first())
    }
}
