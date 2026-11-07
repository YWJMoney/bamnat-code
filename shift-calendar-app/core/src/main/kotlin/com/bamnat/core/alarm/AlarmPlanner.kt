package com.bamnat.core.alarm

import com.bamnat.core.schedule.ScheduleResolver
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** One upcoming alarm. */
data class PlannedAlarm(
    val triggerAt: LocalDateTime,
    val shiftDate: LocalDate,
    /** Start of that day's shift, or the alarm time itself on a day without work. */
    val shiftStart: LocalTime,
    /** 0 when the day has no shift. */
    val shiftTypeId: Long,
    /** Empty when the day has no shift. */
    val shiftName: String,
    /** Minutes before the shift start; negative when it rings after it (set by hand, or [EASE-18] 다음날). */
    val offsetMinutes: Int,
    val soundUri: String?,
    /** Set by hand for this date from the calendar. */
    val custom: Boolean = false,
    /** [EASE-20] The shift's colour (ARGB), for the ringing screen; 0 when the day has no shift. */
    val colorArgb: Long = 0,
)

/**
 * Finds the next alarm. Only one alarm is registered with the system at a time;
 * once it rings, the next one is planned from its trigger time. This keeps the system
 * alarm in sync with any schedule edit without tracking dozens of pending intents.
 */
object AlarmPlanner {
    /** How far ahead to search. Covers long vacations and sparse part-time schedules. */
    const val LOOKAHEAD_DAYS = 120L

    /**
     * The first alarm that triggers strictly after [after].
     *
     * The search starts a day early because an alarm for an early shift can fall on
     * the previous evening (e.g. 00:30 shift with a 60-minute offset).
     */
    fun nextAlarm(
        resolver: ScheduleResolver,
        after: LocalDateTime,
        lookaheadDays: Long = LOOKAHEAD_DAYS,
    ): PlannedAlarm? {
        val maxOffsetDays = 2L
        var date = after.toLocalDate().minusDays(maxOffsetDays)
        val last = after.toLocalDate().plusDays(lookaheadDays)
        var best: PlannedAlarm? = null
        while (!date.isAfter(last)) {
            for (alarm in alarmsOn(resolver, date)) {
                if (alarm.triggerAt.isAfter(after) && (best == null || alarm.triggerAt.isBefore(best.triggerAt))) {
                    best = alarm
                }
            }
            // Triggers lie within a day of their shift date, so dates this far past the
            // best candidate cannot ring earlier.
            if (best != null && date.isAfter(best.triggerAt.toLocalDate().plusDays(maxOffsetDays))) break
            date = date.plusDays(1)
        }
        return best
    }

    /**
     * Every alarm belonging to [date], earliest first, ignoring whether it is in the past.
     * Alarms set by hand for the date replace the shift's own alarms on that day.
     * [includeSkipped] gives what a day with its alarms turned off would ring [SLP-14].
     */
    fun alarmsOn(resolver: ScheduleResolver, date: LocalDate, includeSkipped: Boolean = false): List<PlannedAlarm> {
        val day = resolver.resolve(date)
        if (day.alarmSkipped && !includeSkipped) return emptyList()
        val type = day.shiftType?.takeIf { day.isWorkDay }
        val start = day.start
        val custom = day.entry?.customAlarms
        val list = if (custom != null) {
            custom.filter { it.enabled }.map { alarm ->
                val at = alarm.at(date) // [DAYW-09] the evening before, for one set so
                PlannedAlarm(
                    triggerAt = at,
                    shiftDate = date,
                    shiftStart = start ?: alarm.time,
                    shiftTypeId = type?.id ?: 0,
                    shiftName = type?.name.orEmpty(),
                    offsetMinutes = if (start != null) Duration.between(at, date.atTime(start)).toMinutes().toInt() else 0,
                    soundUri = type?.alarmSoundUri,
                    custom = true,
                    colorArgb = type?.colorArgb ?: 0,
                )
            }
        } else {
            // [FEAT-02][EXTRA-01] A 대근 for one of the user's shifts rings with that shift's alarms.
            val second = day.extras.flatMap { e ->
                val t = e.covered ?: return@flatMap emptyList()
                val at = e.from ?: return@flatMap emptyList()
                t.activeAlarms.map { spec ->
                    val offset = spec.offsetMinutes.coerceIn(-MAX_OFFSET_MINUTES, MAX_OFFSET_MINUTES) // [EASE-18] below zero: after the start
                    PlannedAlarm(
                        triggerAt = at.minusMinutes(offset.toLong()),
                        shiftDate = date,
                        shiftStart = at.toLocalTime(),
                        shiftTypeId = t.id,
                        shiftName = t.name,
                        offsetMinutes = offset,
                        soundUri = t.alarmSoundUri,
                        colorArgb = t.colorArgb,
                    )
                }
            }
            if (type == null || start == null) return second.distinctBy { it.triggerAt }.sortedBy { it.triggerAt }
            second + type.activeAlarms.map { spec ->
                val offset = spec.offsetMinutes.coerceIn(-MAX_OFFSET_MINUTES, MAX_OFFSET_MINUTES) // [EASE-18] below zero: after the start
                PlannedAlarm(
                    triggerAt = date.atTime(start).minusMinutes(offset.toLong()),
                    shiftDate = date,
                    shiftStart = start,
                    shiftTypeId = type.id,
                    shiftName = type.name,
                    offsetMinutes = offset,
                    soundUri = type.alarmSoundUri,
                    colorArgb = type.colorArgb,
                )
            }
        }
        return list.distinctBy { it.triggerAt }.sortedBy { it.triggerAt }
    }

    /** The first alarm of the shift on [date], or null. */
    fun alarmOn(resolver: ScheduleResolver, date: LocalDate): PlannedAlarm? = alarmsOn(resolver, date).firstOrNull()

    /**
     * [ALRM-20] A working day that became a day off, 연차 or 결근: the alarms it had ([was]) and those still set to ring
     * ([left]: ones set by hand for the date, or a 대근's). Null when the day did not stop being a working day or had no alarm.
     */
    data class RestDay(val date: LocalDate, val was: List<PlannedAlarm>, val left: List<PlannedAlarm>)

    fun restDay(before: ScheduleResolver, after: ScheduleResolver, date: LocalDate): RestDay? {
        if (!before.resolve(date).isWorkDay || after.resolve(date).isWorkDay) return null
        val was = alarmsOn(before, date).takeIf { it.isNotEmpty() } ?: return null
        return RestDay(date, was, alarmsOn(after, date))
    }

    /**
     * [ALRM-29] 1.38 (사용자 2026-11-05 '대근이나 대타시 알람을 맞춰논 시간이 있다면 켜지게'): a day whose alarms changed because
     * it became a working day, got a 대근, or its shift or times moved (오전 반차 too). [was] and [now] are what it would ring
     * with its 알람 끄기 left aside; [skipped] says it is turned off, [byHand] that alarms set by hand stand in for the shift's,
     * [wasWork] that it was a working day (or had a 대근) before. Null when the day is not working now or rings the same.
     */
    data class WorkDay(
        val date: LocalDate,
        val was: List<PlannedAlarm>,
        val now: List<PlannedAlarm>,
        val skipped: Boolean,
        val byHand: Boolean,
        val wasWork: Boolean,
    )

    fun workDay(before: ScheduleResolver, after: ScheduleResolver, date: LocalDate): WorkDay? {
        val b = before.resolve(date)
        val a = after.resolve(date)
        if (!a.isWorkDay && a.extras.none { it.covered != null }) return null
        val was = alarmsOn(before, date, includeSkipped = true)
        val now = alarmsOn(after, date, includeSkipped = true)
        if (now.isEmpty() || now.map { it.triggerAt } == was.map { it.triggerAt }) return null
        return WorkDay(date, was, now, a.alarmSkipped, a.entry?.customAlarms != null,
            wasWork = b.isWorkDay || b.extras.any { it.covered != null })
    }

    /**
     * [ALRM-29] Whether the alarm at [next] comes after a stretch without any (휴가, 연휴): none in the [quietHours] before it,
     * so the evening before can say "내일부터 다시 근무".
     */
    fun afterBreak(resolver: ScheduleResolver, next: LocalDateTime, quietHours: Long = BREAK_HOURS): Boolean =
        nextAlarm(resolver, next.minusHours(quietHours), lookaheadDays = quietHours / 24 + 2)?.triggerAt?.let { !it.isBefore(next) } ?: true

    /** [ALRM-29] Two days without an alarm is a break worth a word before work starts again. */
    const val BREAK_HOURS = 48L

    /** Offsets are limited to 24h either way so the two-day search window above is always enough. */
    const val MAX_OFFSET_MINUTES = 24 * 60
}
