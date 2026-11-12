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

    /** 2.0.0 [RVI-17] (검토 ALM-08) A night's 다음날 14:00 stays when a nap's wake goes in, and rings on the day after. */
    @Test fun theNextDaysAlarmStays() {
        val merged = NapAlarm.merge(day, listOf(day.atTime(19, 0), day.plusDays(1).atTime(14, 0)), day.atTime(11, 0))!!
        assertEquals(listOf(day.atTime(11, 0), day.atTime(19, 0), day.plusDays(1).atTime(14, 0)), merged.map { it.at(day) })
        assertEquals(DayAlarm(LocalTime.of(14, 0), dayAfter = true), merged.last())
        // Stored and read back as 다음날, not 14:00 of the day.
        val raw = com.bamnat.core.alarm.AlarmCodec.encodeDayAlarms(merged)
        assertEquals(merged, com.bamnat.core.alarm.AlarmCodec.decodeDayAlarms(raw))
    }

    /** 2.0.0 [RVI-17] The alarms a planner rings for the day, as the day's own: 전날, 당일 and 다음날 each where it rings. */
    @Test fun eachAlarmKeepsItsDay() {
        assertEquals(DayAlarm(LocalTime.of(23, 0), dayBefore = true), DayAlarm.ringingAt(day, day.minusDays(1).atTime(23, 0)))
        assertEquals(DayAlarm(LocalTime.of(14, 0), dayAfter = true), DayAlarm.ringingAt(day, day.plusDays(1).atTime(14, 0)))
        assertNull(DayAlarm.ringingAt(day, day.plusDays(2).atTime(14, 0)))
        // The planner rings a day's 다음날 alarm on the next day.
        val c = com.bamnat.core.alarm.AlarmCodec.decodeDayAlarms("${24 * 60 + 14 * 60}:1")!!
        assertEquals(day.plusDays(1).atTime(14, 0), c.single().at(day))
    }

    /** 2.0.0 [RVI-17] (검토 ALM-08) 깼어요 before the wake: the day follows its shift again, or keeps its own without the wake. */
    @Test fun anEarlyWakeTakesTheNapsAlarmBack() {
        val wake = day.atTime(11, 0)
        val merged = NapAlarm.merge(day, listOf(day.atTime(19, 0)), wake)!!
        assertNull(NapAlarm.withoutWake(day, merged, wake, hadOwn = false, merged = merged))
        assertEquals(listOf(DayAlarm(LocalTime.of(19, 0))), NapAlarm.withoutWake(day, merged, wake, hadOwn = true, merged = merged))
        // Changed by hand since: kept as the user left it, without the wake.
        val changed = merged + DayAlarm(LocalTime.of(21, 0))
        assertEquals(listOf(DayAlarm(LocalTime.of(19, 0)), DayAlarm(LocalTime.of(21, 0))), NapAlarm.withoutWake(day, changed, wake, hadOwn = false, merged = merged))
    }

    /** 2.0.0 [RVI-40] (검토 ALM-25) The alarm near the wake that wakes the nap instead, for "○시쯤 울리는 알람이 있어 그걸로 깨워요". */
    @Test fun theNearAlarmIsNamed() {
        assertEquals(day.atTime(18, 35), NapAlarm.near(listOf(day.atTime(7, 0), day.atTime(18, 35)), day.atTime(18, 30)))
        assertNull(NapAlarm.near(listOf(day.atTime(7, 0)), day.atTime(18, 30)))
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
