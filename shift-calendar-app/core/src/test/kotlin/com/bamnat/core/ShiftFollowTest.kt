package com.bamnat.core

import com.bamnat.core.model.AlarmSpec
import com.bamnat.core.model.ShiftType
import com.bamnat.core.model.WorkPattern
import com.bamnat.core.schedule.ScheduleResolver
import com.bamnat.core.sleep.ShiftFollow
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

/** 2.0.0 [RVIB-10] (검토 I03c-04) 근무를 바꾸면 한 줄로 끝: what followed the change, in the change's own line. */
class ShiftFollowTest {
    private val night = ShiftType(12, "야간", "야", 0xFF5E35B1, LocalTime.of(19, 0), LocalTime.of(7, 0), breakMinutes = 60,
        alarmEnabled = true, alarms = listOf(AlarmSpec(90)))
    private val off = ShiftType(13, "휴무", "휴", 0xFF9E9E9E, null, null, isOff = true)
    private val start = LocalDate.of(2026, 11, 13)
    private val resolver = ScheduleResolver(listOf(night, off), WorkPattern.Rotation(start, listOf(night.id, off.id)), emptyList())
    private val now = LocalDateTime.of(2026, 11, 12, 21, 0)

    @Test fun tomorrowsNightSaysItsAlarmItsSleepAndTheWatch() {
        assertEquals(" · 알람 17:30 · 잘 시간 08:00–15:00 · 워치에도 보냈어요",
            ShiftFollow.line(start, now, resolver, alarmsOn = true, alarmSaid = false, watch = true))
        // The line already said the alarm (a day off that became work); alarms off; no watch.
        assertEquals(" · 잘 시간 08:00–15:00", ShiftFollow.line(start, now, resolver, alarmsOn = true, alarmSaid = true, watch = false))
        assertEquals(" · 잘 시간 08:00–15:00", ShiftFollow.line(start, now, resolver, alarmsOn = false, alarmSaid = false, watch = false))
    }

    @Test fun nothingForADayOffOrFurtherOff() {
        assertEquals("", ShiftFollow.line(start.plusDays(1), now.plusDays(1), resolver, true, false, true))
        assertEquals("", ShiftFollow.line(start.plusDays(2), now, resolver, true, false, true))
    }
}
