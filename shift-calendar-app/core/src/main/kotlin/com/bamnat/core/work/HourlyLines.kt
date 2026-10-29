package com.bamnat.core.work

import com.bamnat.core.model.ExtraTiming
import com.bamnat.core.schedule.ResolvedDay
import java.time.YearMonth
import kotlin.math.roundToLong

/**
 * 1.32 [WTYP-17][WTYP-18] (근무유형 조사 5장 알바) Lines under the 알바 pay card:
 * - 대타: the month's covers (대근, extra work in a shift's place), which are not 소정근로, so 주휴 does not count them
 *   (근로기준법 제55조·시행령 제30조; the pay engine leaves extra work out of the weekly hours).
 * - 휴게 포함 시급 환산: the wage over the time spent at work with breaks, as 알바천국 users asked for.
 * - 알바 2곳까지 무료: the main calendar and one added calendar cost nothing.
 */
object HourlyLines {
    const val FREE_PLACES = 2
    const val FREE_LINE = "알바 ${FREE_PLACES}곳까지 무료예요. 다른 알바는 달력 이름 › 새 달력 만들기로 따로 세요."

    data class Covers(val count: Int, val minutes: Int)

    fun covers(month: YearMonth, resolve: (java.time.LocalDate) -> ResolvedDay): Covers {
        var n = 0
        var m = 0
        for (d in 1..month.lengthOfMonth()) {
            resolve(month.atDay(d)).extras.filter { it.kind.timing is ExtraTiming.AsShift }.forEach { n++; m += it.minutes }
        }
        return Covers(n, m)
    }

    fun coverLine(c: Covers): String? = if (c.count == 0) null else
        "이번 달 대타 ${c.count}번(${com.bamnat.core.hours.HoursFormat.korean(c.minutes)})은 주휴 계산에 넣지 않았어요. 대타는 정해진 근무(소정근로)가 아니라서요."

    data class Stay(val paidMinutes: Int, val breakMinutes: Int, val hourly: Long) {
        /** The wage per hour at work, breaks included. */
        val rate: Long get() = if (paidMinutes + breakMinutes <= 0) hourly else (hourly * paidMinutes.toDouble() / (paidMinutes + breakMinutes)).roundToLong()
    }

    /** The month's worked and break minutes, on days that were worked. */
    fun stay(month: YearMonth, hourly: Long, resolve: (java.time.LocalDate) -> ResolvedDay): Stay {
        var paid = 0
        var breaks = 0
        for (d in 1..month.lengthOfMonth()) {
            val r = resolve(month.atDay(d))
            if (!r.isWorkDay) continue
            paid += r.scheduledMinutes
            breaks += r.breakMinutes
        }
        return Stay(paid, breaks, hourly)
    }

    fun stayLine(s: Stay): String? = if (s.breakMinutes <= 0 || s.hourly <= 0) null else
        "휴게 포함 시급 환산: 쉬는 시간까지 가게에 있던 시간으로 나누면 시간당 ${"%,d".format(s.rate)}원이에요 " +
            "(일한 ${com.bamnat.core.hours.HoursFormat.korean(s.paidMinutes)} + 휴게 ${com.bamnat.core.hours.HoursFormat.korean(s.breakMinutes)})."
}
