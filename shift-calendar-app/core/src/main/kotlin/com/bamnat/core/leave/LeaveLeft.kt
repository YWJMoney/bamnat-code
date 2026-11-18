package com.bamnat.core.leave

import java.time.LocalDate

/**
 * 1.38 [P3-X-05] 연차 남은 날 자동 (사용자 2026-11-06 혁신안): from a hire date alone, the law's leave (근로기준법 제60조, [LeaveAccrual.lots])
 * less the days taken, each taken day from the lot that runs out first. For the 리더's 신청 링크 ("남은 연차 7일", no more 연차
 * asked than that) and the 사장님's 급여 대장. Full attendance is assumed, as [LeaveAccrual.entitlement] does.
 */
object LeaveLeft {
    data class Left(val made: Double, val used: Double, val left: Double, val nextOn: LocalDate?, val nextDays: Double)

    /** [used]: each day of leave taken (0.5 for 반차); [on]: the day it is asked for. */
    fun of(hire: LocalDate, used: Map<LocalDate, Double>, on: LocalDate, policy: LeavePolicy = LeavePolicy()): Left {
        val all = LeaveAccrual.lots(hire, policy, on.plusYears(1))
        val lots = all.filter { !it.on.isAfter(on) }
        val rest = lots.map { it.days }.toMutableList()
        var usedIn = 0.0
        for ((d, n) in used.toSortedMap()) {
            if (d.isAfter(on)) continue
            var need = n
            // The lot valid that day which ends first.
            for (i in lots.indices.sortedBy { lots[it].expires ?: LocalDate.MAX }) {
                val l = lots[i]
                if (l.on.isAfter(d) || (l.expires != null && d.isAfter(l.expires)) || rest[i] <= 0.0) continue
                val take = minOf(need, rest[i])
                rest[i] -= take; need -= take; usedIn += take
                if (need <= 0.0) break
            }
        }
        val valid = lots.indices.filter { lots[it].expires == null || !on.isAfter(lots[it].expires!!) }
        val made = valid.sumOf { lots[it].days }
        val left = valid.sumOf { rest[it] }
        val next = all.firstOrNull { it.on.isAfter(on) }
        return Left(made, made - left, left, next?.on, next?.days ?: 0.0)
    }

    /** The 사장님's view: 연차 is the law's only at a place of 5 or more (제11조, 시행령 별표 1), and needs a hire date. */
    fun forStaff(fivePlus: Boolean?, hiredOn: LocalDate?, used: Map<LocalDate, Double>, on: LocalDate, weekMinutes: Int? = null): Left? =
        // 2.0.0 [RVG-03] 검토 BOSS-51: 한 주 15시간 미만(초단시간)은 제60조가 적용되지 않아요(근로기준법 제18조 제3항).
        if (fivePlus == false || hiredOn == null || weekMinutes != null && weekMinutes in 1 until 15 * 60) null else of(hiredOn, used, on)
}
