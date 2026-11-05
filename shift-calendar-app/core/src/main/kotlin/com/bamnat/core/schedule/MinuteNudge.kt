package com.bamnat.core.schedule

import com.bamnat.core.model.ExtraEntry
import com.bamnat.core.model.WorkTime
import java.time.LocalTime

/**
 * [EASE-26] 1.38 추가 근무 +10분·+30분·+1시간, and the same taken off (− 조퇴): a piece of extra work made longer or shorter by
 * a tap instead of a time picker (오늘근무 비교: 잔업 1시간 30분 넣기 6번 → 밤낮 4번 이하). A piece with clock times keeps its
 * start and moves its end; one with minutes only changes its minutes. Never shorter than [MIN] of work, never longer than
 * a day. The day's own shift (조퇴·연장) moves its end the same way ([end]).
 */
object MinuteNudge {
    /** The steps offered, in minutes. */
    val STEPS = listOf(10, 30, 60)

    /** Shortest a piece can be made by a tap: 10 minutes of work. */
    const val MIN = 10

    /** "+10분", "−30분", "+1시간". */
    fun label(minutes: Int): String {
        val sign = if (minutes < 0) "−" else "+"
        val m = kotlin.math.abs(minutes)
        return sign + if (m % 60 == 0) "${m / 60}시간" else "${m}분"
    }

    /**
     * [entry] [delta] minutes longer (shorter when negative). [from]/[to] are its times as the day resolved them (a 대근 of a
     * shift has the shift's), null when it has none; [max] is the longest it may be.
     */
    fun extra(entry: ExtraEntry, from: LocalTime?, to: LocalTime?, delta: Int, max: Int = WorkTime.MINUTES_PER_DAY): ExtraEntry {
        if (from != null && to != null && (entry.hasTimes || entry.coveredShiftId != null)) {
            val span = WorkTime.spanMinutes(from, to)
            val next = (span + delta).coerceIn(entry.breakMinutes + MIN, minOf(max, WorkTime.MINUTES_PER_DAY - 1))
            return entry.copy(start = from, end = from.plusMinutes(next.toLong()), minutes = 0)
        }
        return entry.copy(minutes = (entry.minutes + delta).coerceIn(MIN, max))
    }

    /**
     * The day's shift ending [delta] minutes later (earlier when negative, 조퇴): the new end, or null when it would leave
     * less than [MIN] of work or reach a whole day.
     */
    fun end(start: LocalTime, end: LocalTime, breakMinutes: Int, delta: Int): LocalTime? {
        val next = WorkTime.spanMinutes(start, end) + delta
        if (next < breakMinutes + MIN || next >= WorkTime.MINUTES_PER_DAY) return null
        return start.plusMinutes(next.toLong())
    }
}
