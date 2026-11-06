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

    /** 1.38 [SLEEP-14] The watch tile's sleep line goes over and back; an older phone's payload has none. */
    @Test fun theWatchGetsTheSleepLine() {
        val p = com.bamnat.core.work.WatchPayload(emptyList(), day.atTime(9, 0), moneyCategories = listOf("식비"), sleepLine = "잘 시간 08:00–15:00")
        val back = com.bamnat.core.work.WatchDays.decode(com.bamnat.core.work.WatchDays.encode(p))!!
        assertEquals("잘 시간 08:00–15:00", back.sleepLine)
        assertEquals(listOf("식비"), back.moneyCategories)
        val old = com.bamnat.core.work.WatchDays.encode(p).lines().first().split('\t').take(13).joinToString("\t")
        assertEquals("", com.bamnat.core.work.WatchDays.decode(old)!!.sleepLine)
    }
}
