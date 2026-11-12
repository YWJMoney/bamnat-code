package com.bamnat.core.sleep

import com.bamnat.core.model.DayAlarm
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 1.38 [SLEEP-23] 낮잠 버튼: the wake for a nap goes in with the day's own alarms (the 근무 알람 체계: exact, on the ringing screen,
 * kept over a reboot), as the alarms set by hand for [date]. Those replace the shift's alarms on that day, so the shift's own
 * ([dayAlarms], as they ring now) come along. Null when one already rings within [NEAR_MINUTES] of the wake: nothing to add.
 * 2.0.0 [RVI-17] (검토 ALM-08) All of them: one on the day after (a night's [EASE-18] 다음날 알람) was dropped.
 */
object NapAlarm {
    const val NEAR_MINUTES = 10L

    fun merge(date: LocalDate, dayAlarms: List<LocalDateTime>, wake: LocalDateTime): List<DayAlarm>? {
        if (near(dayAlarms, wake) != null) return null
        return (dayAlarms + wake).mapNotNull { DayAlarm.ringingAt(date, it) }
            .distinctBy { it.at(date) }
            .sortedBy { it.at(date) }
    }

    /** 2.0.0 [RVI-40] (검토 ALM-25) The alarm already ringing within [NEAR_MINUTES] of [wake], which wakes the nap instead. */
    fun near(dayAlarms: List<LocalDateTime>, wake: LocalDateTime): LocalDateTime? =
        dayAlarms.filter { kotlin.math.abs(Duration.between(it, wake).toMinutes()) <= NEAR_MINUTES }.minByOrNull { kotlin.math.abs(Duration.between(it, wake).toMinutes()) }

    /**
     * 2.0.0 [RVI-17] (검토 ALM-08) After an early 깼어요: the day's [alarms] without the nap's [wake]. Null when the day had no
     * alarms of its own before the nap ([hadOwn] false) and nobody changed them since ([merged], what the nap put in), so the
     * day follows its shift again.
     */
    fun withoutWake(date: LocalDate, alarms: List<DayAlarm>, wake: LocalDateTime, hadOwn: Boolean, merged: List<DayAlarm>?): List<DayAlarm>? {
        if (!hadOwn && merged != null && alarms.sortedBy { it.at(date) } == merged.sortedBy { it.at(date) }) return null
        return alarms.filter { it.at(date) != wake }
    }
}
