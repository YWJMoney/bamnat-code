package com.bamnat.core.schedule

/**
 * [HRLY-05] 법정 휴게 자동 빼기 (근로기준법 제54조): a working day with no break set loses 30 minutes from
 * 4 hours on the clock and an hour from 8½. A break the user set, even 0 on purpose, is left alone, and so
 * is a day with leave, whose break is already worked out.
 *
 * From 2026-12-10 제54조 ① 단서 (법률 제21784호) lets a worker on a 4시간 day ask for no break; that day's
 * break set to 0 keeps the whole span as work, so the rule itself does not change.
 *
 * 2.0.0 [RVH-44] A day of two parts takes one rule, not one per part (설계 docs/plans/2.0.0/두-구간-근무-시간-연차-설계.md 2-3절, 총괄
 * 3-1): the break the law asks for on the outer span (first start to last end) less the gap's minutes, never under 0. 11~15·17~21
 * loses nothing (8시간 stays), 5시간 + 5시간 with 2 hours between nothing, a 9-hour day with 30 minutes between 30 minutes. Taking
 * 30 minutes off each part would make a 식당's 4 + 4 hours 7 and pay less. This reads the gap as the break 제54조 asks for (근로시간
 * 도중에 주는 휴게); the pay sheet says so ('확인 필요', 결정 9번 묶음) and the gap stays unpaid and apart from the break.
 */
object LegalBreak {
    /**
     * The break the law asks for on a span of [minutes] without one. An hour only from 8시간 30분 on the clock:
     * 8 hours less 30 minutes is 7시간 30분 worked, which needs 30 minutes, not an hour.
     */
    fun minutesFor(minutes: Int): Int = when {
        minutes >= 8 * 60 + 30 -> 60
        minutes >= 4 * 60 -> 30
        else -> 0
    }

    fun apply(day: ResolvedDay): ResolvedDay {
        if (day.start == null || day.end == null || day.breakMinutes > 0 || day.leave != null) return day
        if (day.entry?.customBreakMinutes != null) return day
        val brk = if (day.gapMinutes > 0) (minutesFor(day.scheduledMinutes + day.gapMinutes) - day.gapMinutes).coerceAtLeast(0)
        else minutesFor(day.scheduledMinutes)
        if (brk == 0) return day
        return day.copy(breakMinutes = brk, breakStart = null, scheduledMinutes = day.scheduledMinutes - brk)
    }
}
