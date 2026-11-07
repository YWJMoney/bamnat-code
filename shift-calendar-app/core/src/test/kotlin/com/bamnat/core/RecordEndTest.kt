package com.bamnat.core

import com.bamnat.core.sleep.RecordEnd
import com.bamnat.core.sleep.SleepSchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** 1.39 [SLEEP-06] 자는 동안 소리 stops by itself, never into the day. */
class RecordEndTest {
    private val d = LocalDate.of(2026, 11, 9)
    private val next = d.plusDays(1)

    @Test fun anHourAfterTheAlarm() {
        assertEquals(next.atTime(7, 0), RecordEnd.at(d.atTime(23, 0), alarm = next.atTime(6, 0), plannedWake = null, targetMinutes = 420))
    }

    @Test fun theEarlierOfAlarmAndPlan() {
        assertEquals(next.atTime(6, 30), RecordEnd.at(d.atTime(23, 0), alarm = next.atTime(6, 0), plannedWake = next.atTime(5, 30), targetMinutes = 420))
    }

    @Test fun afterANightShiftThePlannedWake() {
        // Home at 08:00 after a night, the next alarm only tomorrow evening: the planned sleep's end counts.
        assertEquals(d.atTime(16, 0), RecordEnd.at(d.atTime(8, 30), alarm = d.atTime(23, 30), plannedWake = d.atTime(15, 0), targetMinutes = 420))
    }

    @Test fun noAlarmNoPlanTargetPlusSpare() {
        assertEquals(next.atTime(9, 0), RecordEnd.at(d.atTime(23, 0), alarm = null, plannedWake = null, targetMinutes = 420))
    }

    @Test fun anAlarmTooFarOrTooSoonIsNotTheNights() {
        // Alarms off for the weekend: the next one is two days on.
        assertEquals(next.atTime(9, 0), RecordEnd.at(d.atTime(23, 0), alarm = d.plusDays(2).atTime(6, 0), plannedWake = null, targetMinutes = 420))
        // A memo alarm ten minutes on.
        assertEquals(next.atTime(9, 0), RecordEnd.at(d.atTime(23, 0), alarm = d.atTime(23, 10), plannedWake = null, targetMinutes = 420))
    }

    @Test fun neverMoreThanTwelveHours() {
        assertEquals(next.atTime(11, 0), RecordEnd.at(d.atTime(23, 0), alarm = null, plannedWake = null, targetMinutes = 600))
    }

    @Test fun plannedWakeFindsTheSleepItStartsIn() {
        val sleep = SleepSchedule.Sleep(d.atTime(9, 0), d.atTime(16, 0), after = null, before = null, short = false, restMinutes = null)
        assertEquals(d.atTime(16, 0), RecordEnd.plannedWake(d.atTime(8, 30), listOf(sleep)))
        assertEquals(d.atTime(16, 0), RecordEnd.plannedWake(d.atTime(12, 0), listOf(sleep)))
        assertNull(RecordEnd.plannedWake(d.atTime(6, 0), listOf(sleep)))
        assertNull(RecordEnd.plannedWake(d.atTime(16, 0), listOf(sleep)))
    }
}
