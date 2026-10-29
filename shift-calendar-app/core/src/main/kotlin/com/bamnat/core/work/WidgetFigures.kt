package com.bamnat.core.work

import com.bamnat.core.money.Job
import com.bamnat.core.money.JobStatus
import com.bamnat.core.money.MoneyCalculator
import java.time.LocalDate

/**
 * [COMN-05] The today widget's line for each kind of work, under today's work: a part-timer's week
 * against 주휴 15시간, a field worker's week of days (or 공수) and money still owed, a day worker's
 * 연차 left and today's 야근. Shift and shop calendars keep what they had.
 */
object WidgetFigures {
    /** 주휴수당 needs 15 hours in the week (근로기준법 제55조, 시행령 제30조). */
    const val WEEKLY_HOLIDAY_MINUTES = 15 * 60

    /** [COMN-05] A part-timer's week so far against 주휴 15시간. */
    data class HourlyWeek(val minutes: Int) {
        val reached: Boolean get() = minutes >= WEEKLY_HOLIDAY_MINUTES
        val left: Int get() = (WEEKLY_HOLIDAY_MINUTES - minutes).coerceAtLeast(0)
        /** 0..1, for a bar. */
        val progress: Float get() = (minutes.toFloat() / WEEKLY_HOLIDAY_MINUTES).coerceIn(0f, 1f)
    }

    /** [COMN-05] A field worker's week: days with a job, or 공수 when the jobs count it, and what is still owed. */
    data class FieldWeek(val days: Int, val units: Double?, val unpaid: Long)

    /**
     * The week [weekStart]..+6: the days a job (not cancelled) runs on, the 공수 of the week's jobs when
     * any job has one, and everything owed on jobs up to [today].
     */
    fun fieldWeek(jobs: List<Job>, weekStart: LocalDate, today: LocalDate): FieldWeek {
        val live = jobs.filter { it.status != JobStatus.CANCELLED }
        val week = (0L..6L).map { weekStart.plusDays(it) }
        val days = week.count { d -> live.any { it.covers(d) } }
        val inWeek = live.filter { it.date in week.first()..week.last() }
        val units = if (inWeek.any { it.units != null }) inWeek.sumOf { it.units ?: it.days.toDouble() } else null
        val unpaid = MoneyCalculator.unpaid(live, today).sumOf { it.unpaid }
        return FieldWeek(days, units, unpaid)
    }

    /** G 1.32 [WTYP-23] A platform worker's 건수 today and this week (no amounts: a home screen is seen by others). */
    data class PlatformDay(val today: Int, val week: Int, val platform: String?)

    fun platformDay(jobs: List<Job>, weekStart: LocalDate, today: LocalDate): PlatformDay {
        val live = jobs.filter { com.bamnat.core.money.PlatformWork.isPlatform(it) && !it.cancelled }
        val week = weekStart..weekStart.plusDays(6)
        return PlatformDay(live.filter { it.date == today }.sumOf(com.bamnat.core.money.PlatformWork::count),
            live.filter { it.date in week }.sumOf(com.bamnat.core.money.PlatformWork::count),
            com.bamnat.core.money.PlatformWork.usual(today, jobs))
    }

    /** G 1.32 [WTYP-23] 조사 대조 C14: 공무원 "10월 시간외 41/57시간", without pay. */
    data class Overtime(val minutes: Int, val capHours: Int) {
        val progress: Float get() = if (capHours <= 0) 0f else (minutes / 60f / capHours).coerceIn(0f, 1f)
        val over: Boolean get() = minutes > capHours * 60
    }

    /** G 1.32 [WTYP-23] 조사 대조 C14: 자영업 "이번 달 영업 22일 · 휴무 8일". */
    data class ShopDays(val open: Int, val closed: Int)

    /** "1.5공수", "2공수". */
    fun units(value: Double): String =
        if (value == Math.floor(value)) "${value.toLong()}공수" else "${String.format(java.util.Locale.ROOT, "%.2f", value).trimEnd('0').trimEnd('.')}공수"
}
