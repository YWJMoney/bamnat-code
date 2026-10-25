package com.bamnat.core

import com.bamnat.core.alarm.AlarmOffsets
import com.bamnat.core.memo.CaretScroll
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

/** [ALRM-06][UX-11] 1.25 Z 398·400: the alarm wheels' clock ↔ minutes before, and where a memo scrolls for its caret. */
class ZAlarmCaretTest {
    @Test fun aClockTimeAndMinutesBeforeTurnIntoEachOther() {
        val seven = LocalTime.of(7, 0)
        assertEquals(LocalTime.of(5, 30), AlarmOffsets.ringAt(seven, 90))
        assertEquals(90, AlarmOffsets.offsetFor(seven, LocalTime.of(5, 30)))
        // The night before: 23:30 for a 07:00 shift.
        assertEquals(450, AlarmOffsets.offsetFor(seven, LocalTime.of(23, 30)))
        assertTrue(AlarmOffsets.dayBefore(seven, 450))
        assertFalse(AlarmOffsets.dayBefore(seven, 90))
        // At the start itself is 0, never a whole day.
        assertEquals(0, AlarmOffsets.offsetFor(seven, seven))
        // Every minute of the day goes there and back the same, for starts at any hour.
        for (start in listOf(LocalTime.MIDNIGHT, seven, LocalTime.of(19, 45), LocalTime.of(23, 59))) {
            for (m in 0..AlarmOffsets.MAX_MINUTES) assertEquals(m, AlarmOffsets.offsetFor(start, AlarmOffsets.ringAt(start, m)))
        }
    }

    @Test fun aMemoScrollsOnlyWhenTheCaretWouldBeHidden() {
        // In view: stays where it is.
        assertNull(CaretScroll.target(scroll = 0, viewport = 1000, top = 400f, bottom = 440f, room = 32f))
        // Under the keyboard: scrolls just enough to show it with room under it.
        assertEquals(472, CaretScroll.target(scroll = 0, viewport = 1000, top = 1400f, bottom = 1440f, room = 32f))
        // Above the top: back up with half the room over it.
        assertEquals(284, CaretScroll.target(scroll = 600, viewport = 1000, top = 300f, bottom = 340f, room = 32f))
        // Never past the end or above the start.
        assertEquals(200, CaretScroll.target(scroll = 0, viewport = 1000, top = 1400f, bottom = 1440f, room = 32f, max = 200))
        assertEquals(0, CaretScroll.target(scroll = 50, viewport = 1000, top = 5f, bottom = 40f, room = 32f))
        // Nothing to show into yet.
        assertNull(CaretScroll.target(scroll = 0, viewport = 0, top = 1400f, bottom = 1440f, room = 32f))
    }

    @Test fun onlyAKeyboardOpeningBringsTheCaretBack() {
        assertTrue(CaretScroll.followResize(before = 1800, after = 1000))
        // Closing the keyboard (U10): the memo stays still.
        assertFalse(CaretScroll.followResize(before = 1000, after = 1800))
        assertFalse(CaretScroll.followResize(before = 1000, after = 1000))
    }
}
