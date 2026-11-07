package com.bamnat.core.memo

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 1.39 [TASK-04][TASK-20] 알림에서 바로 미루기 (할 일 앱의 알림 단추, 밤낮은 근무 기준으로): a memo's schedule notice offers
 * 10분 뒤 · 1시간 뒤 · 다음 쉬는 날 아침. The last is read from the memo's own calendar; a calendar with no day off ahead
 * (투자, nothing scheduled) offers 내일 instead, at the same time.
 */
object MemoSnooze {
    enum class Way(val key: String) { MIN10("m10"), HOUR1("h1"), REST("rest"), TOMORROW("tmr");
        companion object { fun of(key: String?): Way? = entries.firstOrNull { it.key == key } }
    }

    /** How far ahead a day off is looked for. */
    const val LOOK_DAYS = 60

    /** The first day after [today] that [isRest] says is a day off, within [LOOK_DAYS]; null for none. */
    fun nextRest(today: LocalDate, isRest: (LocalDate) -> Boolean): LocalDate? =
        generateSequence(today.plusDays(1)) { it.plusDays(1) }.take(LOOK_DAYS).firstOrNull(isRest)

    /** The third button: to [rest] when there is one, else to tomorrow. */
    fun later(rest: LocalDate?): Way = if (rest != null) Way.REST else Way.TOMORROW

    /** The button's words: "10분 뒤", "1시간 뒤", "쉬는 날(11/9)", "내일". */
    fun label(way: Way, rest: LocalDate?): String = when (way) {
        Way.MIN10 -> "10분 뒤"
        Way.HOUR1 -> "1시간 뒤"
        Way.REST -> rest?.let { "쉬는 날(${it.monthValue}/${it.dayOfMonth})" } ?: "내일"
        Way.TOMORROW -> "내일"
    }

    /** The new time: [now] on, or the day off's morning ([ShiftMemoTime.OFF_TIME]), or tomorrow at [was]'s time. */
    fun at(way: Way, now: LocalDateTime, was: LocalDateTime, rest: LocalDate?): LocalDateTime = when (way) {
        Way.MIN10 -> now.plusMinutes(10)
        Way.HOUR1 -> now.plusHours(1)
        Way.REST -> rest?.atTime(ShiftMemoTime.OFF_TIME) ?: now.toLocalDate().plusDays(1).atTime(was.toLocalTime())
        Way.TOMORROW -> now.toLocalDate().plusDays(1).atTime(was.toLocalTime())
    }.withSecond(0).withNano(0)
}
