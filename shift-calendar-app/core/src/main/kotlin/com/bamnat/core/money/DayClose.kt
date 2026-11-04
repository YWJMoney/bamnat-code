package com.bamnat.core.money

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * 1.37 [LEDH-09] 퇴근 시각 하루 마감: the 가계부's evening look comes when the user's day is done, not at a fixed 9 o'clock —
 * [AFTER] minutes after a shift ends (a night's comes the next morning, after the night), and at [EVENING] on a day
 * nothing ends and no shift is going on then. One a calendar day.
 */
object DayClose {
    const val AFTER = 30L
    val EVENING: LocalTime = LocalTime.of(21, 0)

    /** A day's work as the calendar has it: its start and end (end after start, the next day for a night), or none. */
    data class Work(val from: LocalDateTime, val to: LocalDateTime)

    /** When [date]'s notice comes, given the work starting on each day ([work]); null when it has none. */
    fun at(date: LocalDate, work: (LocalDate) -> Work?): LocalDateTime? {
        val shifts = listOfNotNull(work(date.minusDays(1)), work(date))
        val ends = shifts.map { it.to }.filter { it.toLocalDate() == date }
        ends.maxOrNull()?.let { return it.plusMinutes(AFTER) }
        val evening = date.atTime(EVENING)
        return if (shifts.any { !it.from.isAfter(evening) && it.to.isAfter(evening) }) null else evening
    }

    /** The next notice after [now], within a week. */
    fun next(now: LocalDateTime, work: (LocalDate) -> Work?): LocalDateTime? =
        (0L..7L).asSequence().mapNotNull { at(now.toLocalDate().plusDays(it), work) }.firstOrNull { it.isAfter(now) }

    /** The notice's words: what is waiting and what was spent today. */
    fun words(toSort: Int, spentToday: Long, won: (Long) -> String): Pair<String, String> = when {
        toSort > 0 -> "오늘 정리할 게 ${toSort}건 있어요" to (if (spentToday > 0) "오늘 쓴 돈 ${won(spentToday)} · " else "") + "눌러서 한 번에 정리해요"
        spentToday > 0 -> "오늘 쓴 돈 ${won(spentToday)}" to "빠진 지출이 없는지 한 번 볼까요?"
        else -> "오늘 쓴 돈을 적을 시간이에요" to "쓴 게 없으면 그냥 넘어가도 돼요"
    }
}
