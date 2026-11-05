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

    /**
     * [EASE-18] 1.38: an alarm can also ring after its shift has started, on the shift's day or the next (a nap after a
     * 야간), kept as minutes below zero. The earliest is a whole day after the start, less a minute.
     */
    const val MIN_MINUTES = -MAX_MINUTES

    /** [EASE-18] Which day the alarm rings on, counted from the shift's date: −1 전날, 0 당일, 1 다음날. */
    fun dayOf(start: LocalTime, offsetMinutes: Int): Int {
        val ring = start.hour * 60 + start.minute - offsetMinutes
        return Math.floorDiv(ring, DAY)
    }

    /**
     * [EASE-18] Minutes before [start] for an alarm at [ring] on [day] (−1 전날, 0 당일, 1 다음날); null when that is more
     * than a day from the start either way (다음날 09:00 for a 09:00 shift).
     */
    fun offsetFor(start: LocalTime, ring: LocalTime, day: Int): Int? {
        val m = start.hour * 60 + start.minute - (day * DAY + ring.hour * 60 + ring.minute)
        return m.takeIf { it in MIN_MINUTES..MAX_MINUTES }
    }

    /** [EASE-18] "전날", "당일", "다음날". */
    fun dayWord(day: Int): String = when {
        day < 0 -> "전날"
        day > 0 -> "다음날"
        else -> "당일"
    }
}
