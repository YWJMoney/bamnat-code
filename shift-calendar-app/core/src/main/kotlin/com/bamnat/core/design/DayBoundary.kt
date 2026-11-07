package com.bamnat.core.design

import com.bamnat.core.schedule.ResolvedDay
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * 1.39 [FLOW-30] 근무 기준 하루 경계 (흐름·디자인 계획 9-3): the day a moment belongs to is the shift's, not the clock's. A coffee at
 * 02:10 during last night's 19:00–07:00 is that night's, as is a memo or a sleep begun then; on a day with no work around, what is
 * written before [dayStartsAt] (기본 04:00, 설정 › 화면과 글자 › 하루 시작 시각) is yesterday's, as people count their night.
 * A shift that starts in the small hours (새벽 03:00 출근) is that day's own.
 */
object DayBoundary {
    val DEFAULT_START: LocalTime = LocalTime.of(4, 0)

    fun dayOf(at: LocalDateTime, shiftOn: (LocalDate) -> ResolvedDay?, dayStartsAt: LocalTime = DEFAULT_START): LocalDate {
        val date = at.toLocalDate()
        // The shift that holds this moment wins: yesterday's into today, then today's.
        for (d in listOf(date.minusDays(1), date)) {
            val r = shiftOn(d) ?: continue
            if (!r.isWorkDay) continue
            val s = r.start ?: continue
            val e = r.end ?: continue
            val from = d.atTime(s)
            val to = if (e.isAfter(s)) d.atTime(e) else d.plusDays(1).atTime(e)
            if (!at.isBefore(from) && at.isBefore(to)) return d
        }
        return if (at.toLocalTime().isBefore(dayStartsAt)) date.minusDays(1) else date
    }

    /** [dayStartsAt] from minutes past midnight as settings keep it (0 = 자정). */
    fun startAt(minutes: Int): LocalTime = LocalTime.MIDNIGHT.plusMinutes(minutes.coerceIn(0, 12 * 60).toLong())
}
