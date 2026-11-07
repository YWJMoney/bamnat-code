package com.bamnat.core.sleep

import com.bamnat.core.model.DayAlarm
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 1.38 [SLEEP-23] 낮잠 버튼: the wake for a nap goes in with the day's own alarms (the 근무 알람 체계: exact, on the ringing screen,
 * kept over a reboot), as the alarms set by hand for [date]. Those replace the shift's alarms on that day, so the shift's own
 * ([dayAlarms], as they ring now) come along. Null when one already rings within [NEAR_MINUTES] of the wake: nothing to add.
 */
object NapAlarm {
    const val NEAR_MINUTES = 10L

    fun merge(date: LocalDate, dayAlarms: List<LocalDateTime>, wake: LocalDateTime): List<DayAlarm>? {
        if (dayAlarms.any { kotlin.math.abs(Duration.between(it, wake).toMinutes()) <= NEAR_MINUTES }) return null
        return (dayAlarms + wake).filter { !it.toLocalDate().isBefore(date.minusDays(1)) && !it.toLocalDate().isAfter(date) }
            .map { DayAlarm(it.toLocalTime(), dayBefore = it.toLocalDate() == date.minusDays(1)) }
            .distinctBy { it.dayBefore to it.time }
            .sortedWith(compareBy({ !it.dayBefore }, { it.time }))
    }
}
