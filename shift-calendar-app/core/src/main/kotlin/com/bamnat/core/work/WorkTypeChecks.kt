package com.bamnat.core.work

import com.bamnat.core.pay.PayProfile
import java.time.LocalDate
import java.time.Period

/**
 * G 1.32 [WTYP-07] 609 조사 보고서의 '확인 필요' 항목 중 앱에 없던 것: 포괄임금의 고정 연장 시간 대 실제 연장, 근속 기간과
 * 퇴직금 1년, 공무원의 시간외 상한 기본값.
 */
object WorkTypeChecks {
    /** The month's extra hours against the hours a 고정 연장수당 (포괄임금) is meant to cover. */
    data class Cover(val coveredMinutes: Int, val usedMinutes: Int) {
        val overMinutes: Int get() = (usedMinutes - coveredMinutes).coerceAtLeast(0)
        val leftMinutes: Int get() = (coveredMinutes - usedMinutes).coerceAtLeast(0)
        val over: Boolean get() = overMinutes > 0
        val share: Double get() = if (coveredMinutes <= 0) 0.0 else usedMinutes.toDouble() / coveredMinutes
    }

    /** Null when no allowance is a 고정 연장수당. */
    fun cover(profile: PayProfile?, extraMinutes: Int): Cover? =
        profile?.items.orEmpty().filter { it.isFixedOvertime }.sumOf { it.fixedOvertimeMinutes }.takeIf { it > 0 }
            ?.let { Cover(it, extraMinutes.coerceAtLeast(0)) }

    /** 근속: how long since [hire], the next anniversary, and whether the year for 퇴직금 is done. */
    data class Tenure(val hire: LocalDate, val today: LocalDate) {
        val period: Period get() = Period.between(hire, today)
        val days: Long get() = today.toEpochDay() - hire.toEpochDay()
        val oneYear: Boolean get() = !today.isBefore(hire.plusYears(1))
        val next: LocalDate get() = hire.plusYears(period.years + 1L)
        val toNext: Long get() = next.toEpochDay() - today.toEpochDay()
        val text: String get() = listOfNotNull(period.years.takeIf { it > 0 }?.let { "${it}년" },
            period.months.takeIf { it > 0 }?.let { "${it}개월" }).joinToString(" ").ifBlank { "${days + 1}일째" }
        val note: String get() = if (oneYear) "${period.years + 1}년 차까지 ${toNext}일 남았어요"
            else "1년을 채우면 퇴직금 대상이에요 (주 15시간 이상) · ${toNext}일 남았어요"
    }

    fun tenure(hire: LocalDate?, today: LocalDate): Tenure? = hire?.takeIf { !it.isAfter(today) }?.let { Tenure(it, today) }

    /** 공무원 시간외: the monthly 57시간 unless the user set a cap of their own. */
    fun capHours(saved: Int?, profile: PayProfile?): Int? = saved ?: if (profile?.publicGrade != null) DayWork.DEFAULT_CAP_HOURS else null
}
