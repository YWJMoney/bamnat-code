package com.bamnat.core.schedule

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * [FEAT-07] The shifts as events for a calendar of their own on the phone, the way the .ics file has
 * them ([IcsWriter]): a work day is an event at its times, a day without times an all-day event, and
 * each piece of extra work with clock times an event too; days off are left out. Each event has a key
 * that stays the same for its date, so a changed day updates its event instead of adding another.
 */
data class ShiftEvent(
    val key: String,
    val title: String,
    /** Both null for an all-day event on [date]. */
    val start: LocalDateTime?,
    val end: LocalDateTime?,
    val date: LocalDate,
    val description: String?,
) {
    val allDay: Boolean get() = start == null || end == null

    /** What the phone's copy is compared with: any change here rewrites the event. */
    val fingerprint: String get() = listOf(title, start, end, if (allDay) date else null, description).joinToString("|")
}

object ShiftEvents {
    fun of(days: List<ResolvedDay>): List<ShiftEvent> = days.flatMap { day ->
        buildList {
            val type = day.shiftType?.takeIf { day.isWorkDay }
            if (type != null) {
                val start = day.start
                val end = day.end
                val notes = day.extras.filter { it.from == null }.map { "${it.kind.name} ${hours(it.minutes)}" }
                if (start != null && end != null) {
                    val endAt = if (end.isAfter(start)) day.date.atTime(end) else day.date.plusDays(1).atTime(end)
                    add(ShiftEvent("${day.date}", type.name, day.date.atTime(start), endAt, day.date, notes.joinToString(", ").ifEmpty { null }))
                } else {
                    add(ShiftEvent("${day.date}", type.name, null, null, day.date, notes.joinToString(", ").ifEmpty { null }))
                }
            }
            day.extras.forEachIndexed { i, e ->
                val from = e.from ?: return@forEachIndexed
                val to = e.to ?: return@forEachIndexed
                add(ShiftEvent("${day.date}-x$i", e.kind.name + (e.covered?.let { " (${it.name})" } ?: ""), from, to, day.date, hours(e.minutes)))
            }
        }
    }

    /**
     * [WIDG-01] I 리뷰 I20: the next moment after [now] a shift or a timed piece of extra work starts or ends over
     * [days], so a widget that says "출근" or "근무 중" is redrawn then, not only at midnight.
     */
    fun nextChange(days: List<ResolvedDay>, now: LocalDateTime): LocalDateTime? =
        of(days).flatMap { listOfNotNull(it.start, it.end) }.filter { it.isAfter(now) }.minOrNull()

    /** [WIDG-05] The shift or timed extra going on at [now], if any. */
    fun current(days: List<ResolvedDay>, now: LocalDateTime): ShiftEvent? =
        of(days).firstOrNull { e -> e.start != null && e.end != null && !e.start.isAfter(now) && e.end.isAfter(now) }

    /** [WIDG-05] The first shift or timed extra that starts after [now]. */
    fun next(days: List<ResolvedDay>, now: LocalDateTime): ShiftEvent? =
        of(days).filter { it.start?.isAfter(now) == true }.minByOrNull { it.start!! }

    /**
     * [WIDG-05] I 리뷰 N6(a): the countdown widget's line, "야간까지 3시간 20분 · 22:00" or, while working,
     * "근무 중 · 07:00에 끝나요 (2시간 10분 남음)". Null when nothing is booked in [days].
     */
    fun countdown(days: List<ResolvedDay>, now: LocalDateTime): String? {
        current(days, now)?.let { e -> return "${e.title} 중 · ${hm(e.end!!)}에 끝나요 (${left(now, e.end)} 남음)" }
        val e = next(days, now) ?: return null
        val day = if (e.start!!.toLocalDate() == now.toLocalDate()) "" else if (e.start.toLocalDate() == now.toLocalDate().plusDays(1)) "내일 " else "${e.start.monthValue}/${e.start.dayOfMonth} "
        return "${e.title}까지 ${left(now, e.start)} · $day${hm(e.start)}"
    }

    /** G 1.32 [WTYP-05] 607 "다음 오프까지" under the countdown: today off, the next off day in [days], or none in the week. */
    fun nextOff(days: List<ResolvedDay>, today: java.time.LocalDate): String {
        // [J133-32] 순번 712: a day off with a 대근 (or other extra work) on it is a working day, not the next 오프.
        fun off(d: ResolvedDay) = (d.shiftType == null || d.shiftType.isOff || (d.leave != null && d.scheduledMinutes == 0)) && d.extras.isEmpty()
        days.firstOrNull { it.date == today }?.let { if (off(it)) return "오늘은 쉬는 날이에요" }
        val next = days.filter { it.date.isAfter(today) }.sortedBy { it.date }.firstOrNull(::off)
            ?: return "7일 안에 쉬는 날이 없어요"
        val n = next.date.toEpochDay() - today.toEpochDay()
        val dow = "월화수목금토일"[next.date.dayOfWeek.value - 1]
        return if (n == 1L) "내일 쉬어요" else "다음 오프까지 ${n}일 · ${next.date.monthValue}/${next.date.dayOfMonth}($dow)"
    }

    private fun left(from: LocalDateTime, to: LocalDateTime): String {
        // Rounded up to the minute, so "0분" is never said while there is time left.
        val m = ((java.time.Duration.between(from, to).seconds + 59) / 60).toInt()
        return when {
            m >= 24 * 60 -> "${m / (24 * 60)}일 ${m % (24 * 60) / 60}시간"
            m >= 60 -> if (m % 60 == 0) "${m / 60}시간" else "${m / 60}시간 ${m % 60}분"
            else -> "${m}분"
        }
    }

    private fun hm(t: LocalDateTime) = "%02d:%02d".format(t.hour, t.minute)

    /** What to do to the phone's events, by key, to make them [wanted]. */
    data class Plan(val insert: List<ShiftEvent>, val update: List<ShiftEvent>, val delete: Set<String>) {
        val empty: Boolean get() = insert.isEmpty() && update.isEmpty() && delete.isEmpty()
    }

    /** [existing] is the phone's events as key → fingerprint. */
    fun plan(existing: Map<String, String>, wanted: List<ShiftEvent>): Plan {
        val byKey = wanted.associateBy { it.key }
        return Plan(
            insert = wanted.filter { it.key !in existing },
            update = wanted.filter { it.key in existing && existing[it.key] != it.fingerprint },
            delete = existing.keys - byKey.keys,
        )
    }

    private fun hours(minutes: Int): String =
        if (minutes % 60 == 0) "${minutes / 60}시간" else "${minutes / 60}시간 ${minutes % 60}분"
}
