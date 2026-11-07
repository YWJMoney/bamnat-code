package com.bamnat.core.sleep

import java.time.LocalDateTime

/**
 * 1.39 [SLEEP-06] When 자는 동안 소리 stops by itself, so the microphone never runs into the day.
 *
 * Turning the morning alarm off or tapping 깼어요 ends it at once (the app does that). This is the backstop when
 * neither comes: an hour after the first wake time it knows (the next alarm, or the planned sleep's end), or,
 * with neither, the sleep target plus a few hours. Never more than [MAX_HOURS].
 */
object RecordEnd {
    /** After the alarm or planned wake: room for snoozes and a lie-in. */
    const val AFTER_WAKE_MINUTES = 60L
    /** With no alarm and no plan: the target plus this. */
    const val SPARE_MINUTES = 180L
    /** Never longer, whatever the plan says (the wake lock holds 14 hours). */
    const val MAX_HOURS = 12L
    /** A wake time this close to the start is a nap's or a mistake, not the night's. */
    const val MIN_MINUTES = 30L

    fun at(start: LocalDateTime, alarm: LocalDateTime?, plannedWake: LocalDateTime?, targetMinutes: Int): LocalDateTime {
        val cap = start.plusHours(MAX_HOURS)
        val wake = listOfNotNull(alarm, plannedWake)
            .filter { it.isAfter(start.plusMinutes(MIN_MINUTES)) && it.isBefore(cap) }
            .minOrNull()
        val end = wake?.plusMinutes(AFTER_WAKE_MINUTES) ?: start.plusMinutes(targetMinutes.coerceAtLeast(0) + SPARE_MINUTES)
        return if (end.isAfter(cap)) cap else end
    }

    /** The planned main sleep this start falls in (or begins within two hours of), for its wake time. */
    fun plannedWake(start: LocalDateTime, sleeps: List<SleepSchedule.Sleep>): LocalDateTime? =
        sleeps.firstOrNull { s -> !start.isBefore(s.from.minusHours(2)) && start.isBefore(s.to) }?.to
}
