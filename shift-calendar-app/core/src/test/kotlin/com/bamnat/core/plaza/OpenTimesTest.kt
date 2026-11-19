package com.bamnat.core.plaza

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** 2.0.0 [RVN-22] 검토 I10bc-02: the times worth reading ahead for, from this phone's own opens, shifts and alarms. */
class OpenTimesTest {
    private val day = LocalDate.of(2026, 11, 18)

    private fun opened(vararg at: LocalDateTime): Map<Long, Long> = at.fold(emptyMap()) { k, t -> OpenTimes.record(k, t) }

    @Test fun aHalfHourBecomesUsualOnThreeDays() {
        val two = opened(day.minusDays(2).atTime(7, 40), day.minusDays(1).atTime(7, 50))
        assertEquals(emptyList<Int>(), OpenTimes.usual(two, day))
        val three = OpenTimes.record(two, day.atTime(7, 31))
        assertEquals(listOf(OpenTimes.slot(LocalTime.of(7, 30))), OpenTimes.usual(three, day))
    }

    @Test fun onlyTheLastFourteenDaysAreKept() {
        val old = opened(day.minusDays(20).atTime(9, 0), day.minusDays(19).atTime(9, 0), day.minusDays(18).atTime(9, 0))
        val now = OpenTimes.record(old, day.atTime(12, 0))
        assertEquals(setOf(day.toEpochDay()), now.keys)
        assertEquals(emptyList<Int>(), OpenTimes.usual(now, day))
    }

    @Test fun aNightShiftEndsTheNextMorning() {
        assertEquals(day.plusDays(1).atTime(8, 0), OpenTimes.shiftEnd(day, LocalTime.of(22, 0), LocalTime.of(8, 0)))
        assertEquals(day.atTime(18, 0), OpenTimes.shiftEnd(day, LocalTime.of(9, 0), LocalTime.of(18, 0)))
        assertNull(OpenTimes.shiftEnd(day, null, LocalTime.of(18, 0)))
    }

    @Test fun theSoonestFarEnoughTimeIsChosen() {
        val kept = opened(day.minusDays(3).atTime(21, 5), day.minusDays(2).atTime(21, 10), day.minusDays(1).atTime(21, 20))
        val now = day.atTime(17, 50)
        // A shift ending at 18:00 is too close (20 minutes ahead and a little more are needed); the alarm's half hour before wins.
        val alarm = day.atTime(19, 0)
        assertEquals(day.atTime(18, 30), OpenTimes.next(now, kept, listOf(day.atTime(18, 0)), listOf(alarm)))
        // Without the alarm, the usual 21:00.
        assertEquals(day.atTime(21, 0), OpenTimes.next(now, kept, listOf(day.atTime(18, 0)), emptyList()))
        // After 21:00 has gone, tomorrow's.
        assertEquals(day.plusDays(1).atTime(21, 0), OpenTimes.next(day.atTime(21, 30), kept, emptyList(), emptyList()))
        assertNull(OpenTimes.next(now, emptyMap(), emptyList(), emptyList()))
    }

    @Test fun whatIsKeptReadsBack() {
        val kept = opened(day.minusDays(1).atTime(6, 0), day.atTime(23, 59))
        assertEquals(kept, OpenTimes.decode(OpenTimes.encode(kept)))
        assertEquals(mapOf(20410L to 3L), OpenTimes.decode("20410:3,x:1,20411,:"))
        assertEquals(emptyMap<Long, Long>(), OpenTimes.decode(null))
    }
}
