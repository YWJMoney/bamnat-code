package com.bamnat.app.ui.leave

import com.bamnat.app.data.ScheduleSnapshot
import com.bamnat.core.work.DayWork
import com.bamnat.core.work.WorkStyle
import java.time.LocalDate

/**
 * [LEAVE-04] 징검다리 for every kind of work: a 일근 calendar's 공휴일 next to its weekends ([DayWork.bridges]),
 * and for 교대·알바·현장 the roster's own days off, which 공휴일 do not move ([DayWork.restBridges]).
 */
internal object LeaveBridges {
    fun of(snap: ScheduleSnapshot, style: WorkStyle?, today: LocalDate): List<DayWork.Bridge> {
        // 2.0.0 [RVH-85] 검토 PC-173: a day past what the calendar covers is not a day off; read as a working day, a long rest stops at it.
        val span = covered(snap, today) ?: return emptyList()
        val work = { d: LocalDate -> d !in span || snap.resolver.resolve(d).let { it.isWorkDay && it.leave == null } }
        // 2.0.0 [RVH-85] 검토 PC-12 (주 13시간 30분 알바에게 '연차 1일로 6일 쉬어요' 여덟 줄): 4주를 평균해 한 주 소정근로가 15시간 아래면
        // 연차가 없어요(근로기준법 제18조 제3항이 제60조를 빼요. [시행 2026. 10. 8.] [법률 제21533호] 원문, 국가법령정보센터 lsiSeq=285279,
        // 2026-11-19 확인). The next four weeks of the roster are what is counted, as the law counts the 소정 hours.
        if (style == WorkStyle.HOURLY && underFifteen(snap, today)) return emptyList()
        val found = when (style) {
            WorkStyle.DAY -> DayWork.bridges(today, isWorkDay = work)
            WorkStyle.BUSINESS, WorkStyle.INVEST -> emptyList()
            else -> DayWork.restBridges(today, isWorkDay = work)
        }
        // 2.0.0 [RVH-85] 검토 PC-173 (12월 표만 받은 직원에게 '12/30~1/30 · 연차 1일로 32일 쉬어요'): past the stretch the calendar covers
        // (its first to its last day a pattern or an entry says something about) a day is not known to be a day off, so a break that
        // reaches out of it is not offered. A blank day inside it is still a day off, as the calendar shows it.
        return found.filter { b -> !b.from.isBefore(span.start) && !b.to.isAfter(span.endInclusive) }
    }

    /** [RVH-85] The first and last day a pattern or an entry covers, from a month back to 13 months on (the breaks' reach); null for none. */
    internal fun covered(snap: ScheduleSnapshot, today: LocalDate): ClosedRange<LocalDate>? {
        val days = snap.resolver.resolveRange(today.minusDays(40), today.plusMonths(13))
            .filter { it.source != com.bamnat.core.schedule.DaySource.NONE }
        return if (days.isEmpty()) null else days.first().date..days.last().date
    }

    /** [RVH-85] 근로기준법 제18조 제3항: the next 4 weeks' scheduled hours average under 15 a week (days past what the calendar covers left out). */
    internal fun underFifteen(snap: ScheduleSnapshot, today: LocalDate): Boolean {
        val span = covered(snap, today) ?: return false
        val days = snap.resolver.resolveRange(today, today.plusDays(27)).filter { it.date in span }
        if (days.size < 7) return false
        val perWeek = days.sumOf { if (it.isWorkDay) it.scheduledMinutes else 0 } * 7.0 / days.size
        return perWeek < 15 * 60
    }

    /**
     * [LIFE-02] Whether the 연차 left after the days already on the calendar ([com.bamnat.core.leave.LeaveSummary.afterPlanned])
     * covers [b]; null when the ledger is not known yet, and then every suggestion stands as before.
     */
    fun fits(b: DayWork.Bridge, left: Double?): Boolean = left == null || b.leave.size <= left + 1e-9

    /** "남은 연차 1일로는 모자라요" under a suggestion that needs more than is left; null when it fits. */
    fun shortNote(b: DayWork.Bridge, left: Double?): String? =
        if (fits(b, left)) null else "남은 연차 ${com.bamnat.core.leave.LeaveAccrual.days(maxOf(left!!, 0.0))}로는 모자라요"

    fun caption(style: WorkStyle?): String = when (style) {
        WorkStyle.DAY -> "앞으로 12개월의 공휴일과 쉬는 요일로 찾았어요."
        else -> "앞으로 12개월 근무표의 쉬는 날 사이에 낀 하루이틀을 찾았어요. 달마다 가장 긴 두 곳이에요."
    }
}
