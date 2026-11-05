package com.bamnat.core

import com.bamnat.core.model.ExtraEntry
import com.bamnat.core.schedule.MinuteNudge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalTime

/** [EASE-26] +10분·+30분·+1시간 and − (조퇴) on extra work and the day's end. */
class MinuteNudgeTest {
    private fun t(h: Int, m: Int = 0) = LocalTime.of(h, m)

    @Test fun labels() {
        assertEquals("+10분", MinuteNudge.label(10))
        assertEquals("+1시간", MinuteNudge.label(60))
        assertEquals("−30분", MinuteNudge.label(-30))
    }

    @Test fun minutesOnlyGrowAndStopAtTen() {
        val e = ExtraEntry(kindId = 1, minutes = 120)
        assertEquals(90, MinuteNudge.extra(e, null, null, -30).minutes)
        assertEquals(180, MinuteNudge.extra(e, null, null, 60).minutes)
        assertEquals(10, MinuteNudge.extra(e, null, null, -600).minutes)
    }

    @Test fun clockTimesMoveTheEnd() {
        val e = ExtraEntry(kindId = 1, start = t(18), end = t(20))
        val longer = MinuteNudge.extra(e, t(18), t(20), 30)
        assertEquals(t(18), longer.start); assertEquals(t(20, 30), longer.end)
        // Past midnight is fine.
        assertEquals(t(0, 30), MinuteNudge.extra(ExtraEntry(kindId = 1, start = t(22), end = t(23)), t(22), t(23), 90).end)
    }

    @Test fun aCoverTakesTheShiftsTimes() {
        val e = ExtraEntry(kindId = 2, coveredShiftId = 5)
        val n = MinuteNudge.extra(e, t(9), t(18), 60)
        assertEquals(t(9), n.start); assertEquals(t(19), n.end); assertEquals(0, n.minutes)
    }

    @Test fun leavingEarly() {
        assertEquals(t(17), MinuteNudge.end(t(9), t(18), 60, -60))
        assertEquals(t(19), MinuteNudge.end(t(9), t(18), 60, 60))
        assertNull(MinuteNudge.end(t(9), t(10), 60, -10))
    }
}
