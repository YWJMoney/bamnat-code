package com.bamnat.core.plaza

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * 2.0.0 [RVN-22] 검토 I10bc-02 열기 직전에 미리 받기 ('매일 1초'): the times this phone is likely to open the company page or the plaza, so
 * they can be read a little before, on Wi‑Fi, and show at once. Three kinds of time, all from the phone itself (nothing is sent, nothing
 * new is kept anywhere but here):
 * - a usual time: a half hour of the day the app was opened on [USUAL] of the last [DAYS] days,
 * - the end of a shift (the calendar's own times; a night shift ends the next morning),
 * - 30 minutes before an alarm.
 *
 * What is kept is one mark per half hour per day ([record]), [DAYS] days at most: "20412:4398046511104,20413:…" (the day, the 48 half
 * hours as bits).
 */
object OpenTimes {
    const val DAYS = 14
    const val SLOTS = 48
    /** A half hour counts as usual when the app was opened in it on this many of the last [DAYS] days. */
    const val USUAL = 3
    /** How long before the time the reading ahead is asked for. */
    val AHEAD: Duration = Duration.ofMinutes(20)
    /** The soonest a reading ahead is set for (a time closer than this is let go). */
    private val SOON: Duration = Duration.ofMinutes(5)
    /** How long before an alarm the phone is likely to be looked at. */
    private val BEFORE_ALARM: Duration = Duration.ofMinutes(30)

    fun slot(t: LocalTime): Int = (t.hour * 60 + t.minute) / 30

    /** [kept] with an open at [at]; days older than [DAYS] go, and days after [at] (marked while the clock was set ahead). */
    fun record(kept: Map<Long, Long>, at: LocalDateTime): Map<Long, Long> {
        val day = at.toLocalDate().toEpochDay()
        val bit = 1L shl slot(at.toLocalTime())
        return (kept + (day to ((kept[day] ?: 0L) or bit))).filterKeys { it > day - DAYS && it <= day }
    }

    /** The half hours of the day opened on [USUAL] days or more of the [DAYS] before and including [today]. */
    fun usual(kept: Map<Long, Long>, today: LocalDate): List<Int> {
        val d = today.toEpochDay()
        val days = kept.filterKeys { it > d - DAYS && it <= d }.values
        return (0 until SLOTS).filter { s -> days.count { it and (1L shl s) != 0L } >= USUAL }
    }

    /** When a shift of [date] from [start] to [end] ends (the next day when it goes past midnight); null without times. */
    fun shiftEnd(date: LocalDate, start: LocalTime?, end: LocalTime?): LocalDateTime? {
        if (start == null || end == null) return null
        return if (end <= start) date.plusDays(1).atTime(end) else date.atTime(end)
    }

    /**
     * The next time to have the pages ready by, after [now]: the soonest of a usual half hour (today or tomorrow), a shift's end and
     * 30 minutes before an alarm, that is at least [AHEAD] plus a little away. Null when there is none.
     */
    fun next(now: LocalDateTime, kept: Map<Long, Long>, shiftEnds: List<LocalDateTime>, alarms: List<LocalDateTime>): LocalDateTime? {
        val today = now.toLocalDate()
        val usual = usual(kept, today).flatMap { s -> listOf(today, today.plusDays(1)).map { it.atStartOfDay().plusMinutes(30L * s) } }
        val earliest = now.plus(AHEAD).plus(SOON)
        return (usual + shiftEnds + alarms.map { it.minus(BEFORE_ALARM) }).filter { !it.isBefore(earliest) }.minOrNull()
    }

    fun encode(kept: Map<Long, Long>): String = kept.entries.sortedBy { it.key }.joinToString(",") { "${it.key}:${it.value}" }

    /** What [encode] wrote; a part it cannot read is left out. */
    fun decode(text: String?): Map<Long, Long> = text.orEmpty().split(',').mapNotNull { p ->
        val (d, m) = p.split(':').takeIf { it.size == 2 } ?: return@mapNotNull null
        (d.toLongOrNull() ?: return@mapNotNull null) to (m.toLongOrNull() ?: return@mapNotNull null)
    }.toMap()
}
