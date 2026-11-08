package com.bamnat.core.memo

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 1.39 [TASK-04][TASK-20] 알림에서 바로 미루기 (할 일 앱의 알림 단추, 밤낮은 근무 기준으로): a memo's schedule notice offers
 * 10분 뒤 · 1시간 뒤 · 다음 쉬는 날 아침. The last is read from the memo's own calendar; a calendar with no day off ahead
 * (투자, nothing scheduled) offers 내일 instead, at the same time.
 */
object MemoSnooze {
    enum class Way(val key: String) { MIN10("m10"), MIN30("m30"), HOUR1("h1"), HOUR3("h3"), REST("rest"), TOMORROW("tmr");
        companion object { fun of(key: String?): Way? = entries.firstOrNull { it.key == key } }
    }

    /** How far ahead a day off is looked for. */
    const val LOOK_DAYS = 60

    /** The first day after [today] that [isRest] says is a day off, within [LOOK_DAYS]; null for none. */
    fun nextRest(today: LocalDate, isRest: (LocalDate) -> Boolean): LocalDate? =
        generateSequence(today.plusDays(1)) { it.plusDays(1) }.take(LOOK_DAYS).firstOrNull(isRest)

    /**
     * 1.39 [TASK-39] '다른 때…' (두 앱 실사용 2차: 할 일 앱은 5분 고정, 비즈 달력은 고르는 창): the ways the picking window offers,
     * the day off's morning first when there is one ahead (밤낮은 근무 기준으로 미뤄요).
     */
    fun choices(rest: LocalDate?): List<Way> =
        listOfNotNull(Way.REST.takeIf { rest != null }, Way.MIN10, Way.MIN30, Way.HOUR1, Way.HOUR3, Way.TOMORROW)

    /** The third button: to [rest] when there is one, else to tomorrow. */
    fun later(rest: LocalDate?): Way = if (rest != null) Way.REST else Way.TOMORROW

    /** The button's words: "10분 뒤", "1시간 뒤", "쉬는 날(11/9)", "내일". */
    fun label(way: Way, rest: LocalDate?): String = when (way) {
        Way.MIN10 -> "10분 뒤"
        Way.MIN30 -> "30분 뒤"
        Way.HOUR1 -> "1시간 뒤"
        Way.HOUR3 -> "3시간 뒤"
        Way.REST -> rest?.let { "쉬는 날(${it.monthValue}/${it.dayOfMonth})" } ?: "내일"
        Way.TOMORROW -> "내일"
    }

    /** The new time: [now] on, or the day off's morning ([ShiftMemoTime.OFF_TIME]), or tomorrow at [was]'s time. */
    fun at(way: Way, now: LocalDateTime, was: LocalDateTime, rest: LocalDate?): LocalDateTime = when (way) {
        Way.MIN10 -> now.plusMinutes(10)
        Way.MIN30 -> now.plusMinutes(30)
        Way.HOUR1 -> now.plusHours(1)
        Way.HOUR3 -> now.plusHours(3)
        Way.REST -> rest?.atTime(ShiftMemoTime.OFF_TIME) ?: now.toLocalDate().plusDays(1).atTime(was.toLocalTime())
        Way.TOMORROW -> now.toLocalDate().plusDays(1).atTime(was.toLocalTime())
    }.withSecond(0).withNano(0)
}
