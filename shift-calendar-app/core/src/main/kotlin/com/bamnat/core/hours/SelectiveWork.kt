package com.bamnat.core.hours

import com.bamnat.core.schedule.ResolvedDay
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * 1.32 [WTYP-15] (근무유형 조사 4장 사무직) 선택적 근로시간제 (근로기준법 제52조): the worker picks each day's hours, and only the
 * 정산 기간's total counts: 1개월 이내 (신상품·신기술 연구개발 업무는 3개월 이내), 평균 주 40시간 = 일수 ÷ 7 × 40시간. Hours past
 * that total are 연장근로. This tracks the period: worked so far, the roster still ahead, what is left and per remaining day.
 */
object SelectiveWork {
    val MONTHS = listOf(1, 3)

    data class Period(val from: LocalDate, val to: LocalDate) {
        val days: Long get() = java.time.temporal.ChronoUnit.DAYS.between(from, to) + 1
        /** 일수 ÷ 7 × 40시간, in minutes. */
        val standardMinutes: Int get() = (days * 40 * 60 / 7.0).roundToInt()
    }

    /** The period [date] is in: whole months from [startMonth] (1~12) in steps of [months]. */
    fun period(date: LocalDate, months: Int, startMonth: Int = 1): Period {
        val m = months.coerceIn(1, 3)
        val offset = Math.floorMod(date.monthValue - startMonth.coerceIn(1, 12), m)
        val from = date.withDayOfMonth(1).minusMonths(offset.toLong())
        return Period(from, from.plusMonths(m.toLong()).minusDays(1))
    }

    data class Progress(val period: Period, val workedMinutes: Int, val aheadMinutes: Int, val workDaysLeft: Int) {
        val leftMinutes: Int get() = (period.standardMinutes - workedMinutes).coerceAtLeast(0)
        /** Worked past the period's total so far: 연장근로. */
        val overMinutes: Int get() = (workedMinutes - period.standardMinutes).coerceAtLeast(0)
        /** The roster to the end, against the total: plus is past it. */
        val plannedOverMinutes: Int get() = workedMinutes + aheadMinutes - period.standardMinutes
        val perDayLeft: Int? get() = if (workDaysLeft > 0 && leftMinutes > 0) leftMinutes / workDaysLeft else null
    }

    /** Days up to [today] count as worked, the roster's after it as ahead. */
    fun progress(period: Period, today: LocalDate, resolve: (LocalDate) -> ResolvedDay): Progress {
        var worked = 0
        var ahead = 0
        var daysLeft = 0
        var d = period.from
        while (!d.isAfter(period.to)) {
            val r = resolve(d)
            if (!d.isAfter(today)) worked += r.totalMinutes else {
                ahead += r.totalMinutes
                if (r.isWorkDay) daysLeft++
            }
            d = d.plusDays(1)
        }
        return Progress(period, worked, ahead, daysLeft)
    }

    const val NOTE = "선택근무는 하루·한 주가 아니라 정산 기간 전체로 셈해요. 기간 총 시간(일수 ÷ 7 × 40시간)을 넘은 시간이 연장근로예요(근로기준법 제52조). " +
        "급여 계산의 연장은 회사 규칙대로 하니, 회사가 정산 기간으로 셈하면 이 카드의 '넘은 시간'과 맞춰 보세요."
}
