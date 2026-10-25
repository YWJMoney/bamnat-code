package com.bamnat.core.alarm

import java.time.LocalTime

/**
 * [ALRM-06] 1.25 Z (U14): a shift's alarm is kept as minutes before the shift starts, but people think of it as a
 * time on the clock ("06:30에 울려요"). These turn one into the other, so the alarm picker can show the same hour
 * and minute wheels as every other time in the app.
 */
object AlarmOffsets {
    private const val DAY = 24 * 60

    /** The latest an alarm can ring before its shift: the day before at the same minute is a whole day. */
    const val MAX_MINUTES = DAY - 1

    /** When an alarm [offsetMinutes] before a shift starting at [start] rings. */
    fun ringAt(start: LocalTime, offsetMinutes: Int): LocalTime = start.minusMinutes(offsetMinutes.toLong())

    /** Minutes before [start] for an alarm that rings at [ring]: 05:30 for a 07:00 shift is 90; 23:30 the night before is 450. */
    fun offsetFor(start: LocalTime, ring: LocalTime): Int {
        val s = start.hour * 60 + start.minute
        val r = ring.hour * 60 + ring.minute
        return Math.floorMod(s - r, DAY)
    }

    /** True when the alarm rings the day before the shift (a 06:00 shift with an alarm 7 hours before). */
    fun dayBefore(start: LocalTime, offsetMinutes: Int): Boolean = offsetMinutes > start.hour * 60 + start.minute
}
