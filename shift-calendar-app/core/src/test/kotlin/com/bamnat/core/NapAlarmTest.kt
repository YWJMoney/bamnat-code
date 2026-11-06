package com.bamnat.core

import com.bamnat.core.model.DayAlarm
import com.bamnat.core.sleep.NapAlarm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class NapAlarmTest {
    private val day = LocalDate.of(2026, 11, 12)

    @Test fun theWakeJoinsTheShiftsOwnAlarms() {
        val merged = NapAlarm.merge(day, listOf(day.atTime(19, 30), day.minusDays(1).atTime(23, 0)), day.atTime(18, 30))
        assertEquals(listOf(DayAlarm(LocalTime.of(23, 0), dayBefore = true), DayAlarm(LocalTime.of(18, 30)), DayAlarm(LocalTime.of(19, 30))), merged)
    }

    @Test fun anAlarmAlreadyNearTheWakeIsEnough() {
        assertNull(NapAlarm.merge(day, listOf(day.atTime(18, 35)), day.atTime(18, 30)))
    }

    @Test fun aDayWithNoAlarmsGetsOnlyTheWake() {
        assertEquals(listOf(DayAlarm(LocalTime.of(16, 0))), NapAlarm.merge(day, emptyList(), day.atTime(16, 0)))
    }
}
