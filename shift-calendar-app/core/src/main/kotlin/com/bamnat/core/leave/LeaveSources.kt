package com.bamnat.core.leave

import com.bamnat.core.holiday.KoreanHolidays
import com.bamnat.core.model.DayEntry
import com.bamnat.core.model.LeaveKind
import com.bamnat.core.pay.PayCalculator
import com.bamnat.core.pay.PayProfile
import com.bamnat.core.schedule.ResolvedDay
import java.time.LocalDate

/**
 * [LEAVE-01] Where a calendar's ledger comes from: the law's lots ([LeaveAccrual]) for the hire date in the pay
 * setup, the leave taken on the calendar, the 보상휴가 its extra work earned [PAYX-18], and the stored rows.
 */
object LeaveSources {
    /**
     * [LEAVE-14] 1.36: [profile] with a hire date typed on the 연차 screen when it has none — or, with no pay setup at all, a bare
     * one that only carries the date (5인 이상, the law's way of counting).
     */
    fun withHire(profile: PayProfile?, hire: LocalDate?): PayProfile? = when {
        hire == null -> profile
        profile == null -> PayProfile(com.bamnat.core.pay.Occupation.CUSTOM, com.bamnat.core.pay.WageType.MONTHLY, com.bamnat.core.pay.ValueTimeline(), hireDate = hire)
        profile.hireDate == null -> profile.copy(hireDate = hire)
        else -> profile
    }

    /**
     * Why the app makes no leave by itself, or null when it does. [weeklyHours]: [RVH-38] 검토 WORK-02 an hourly job's average
     * hours a week ([partTimeHours]); under 15 the law gives no 연차 (근로기준법 제18조 제3항).
     */
    fun noAuto(profile: PayProfile?, weeklyHours: Double? = null): String? = when {
        profile == null -> "입사일을 넣으면 연차를 법대로 세어 드려요"
        !profile.rules.leave.auto -> "연차를 직접 넣기로 했어요"
        profile.hireDate == null -> "입사일을 넣으면 연차를 법대로 세어 드려요"
        // 근로기준법 시행령 별표1: 제60조 is not for workplaces under 5.
        !profile.fiveOrMoreEmployees -> "5인 미만 사업장은 법으로 정한 연차가 없어요. 회사가 주는 날을 직접 넣어요"
        weeklyHours != null && weeklyHours > 0 && weeklyHours < SHORT_HOURS ->
            "최근 4주 평균 주 ${hours(weeklyHours)}시간이라 법으로 정한 연차가 생기지 않아요(주 15시간 미만). 회사가 주는 날은 직접 넣어요"
        else -> null
    }

    /** The law's lots through [until], or none (see [noAuto]). */
    fun autoLots(profile: PayProfile?, until: LocalDate, weeklyHours: Double? = null): List<LeaveLot> {
        if (profile == null || noAuto(profile, weeklyHours) != null) return emptyList()
        return LeaveAccrual.lots(profile.hireDate!!, profile.rules.leave, until)
    }

    /**
     * [RVH-38] 검토 WORK-02 An hourly job's 소정근로 hours a week, averaged over the four whole 주휴 weeks before [today]'s, as the
     * part-timer's tools count them; null for any other pay (a monthly or daily job is not judged by a few quiet weeks).
     */
    fun partTimeHours(profile: PayProfile?, today: LocalDate, resolve: (LocalDate) -> ResolvedDay): Double? {
        if (profile?.wageType != com.bamnat.core.pay.WageType.HOURLY) return null
        val last = today.minusWeeks(1).with(java.time.temporal.TemporalAdjusters.previousOrSame(profile.holidayWeekFrom))
        return (0L..3L).sumOf { com.bamnat.core.work.HourlyWork.week(last.minusWeeks(it), resolve).minutes } / 4.0 / 60.0
    }

    /**
     * [RVH-38] 검토 WORK-02 For a short-hours job (15 to under 40 a week): a day of 연차 is the day's 소정근로시간, weekly ÷ 5. Fifteen such
     * days are the law's 15 × weekly ÷ 40 × 8 hours (근로기준법 시행령 별표2 제4호 가목), so the count stays in days.
     */
    fun partTimeNote(weeklyHours: Double?): String? {
        if (weeklyHours == null || weeklyHours < SHORT_HOURS || weeklyHours >= FULL_HOURS) return null
        return "주 ${hours(weeklyHours)}시간이라 연차 하루는 ${hours(weeklyHours / 5)}시간으로 쳐요(주 시간 ÷ 5) · 참고용"
    }

    private fun hours(h: Double): String = (Math.round(h * 10) / 10.0).let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() }

    private const val SHORT_HOURS = 15.0
    private const val FULL_HOURS = 40.0

    /**
     * Leave taken on the calendar: 연차 (not on a 공휴일 the company does not count, [com.bamnat.core.pay.CompanyRules.holidayLeaveFree])
     * and 보상휴가. Leave only counts on a working day, as the calendar shows it.
     */
    fun uses(entries: List<DayEntry>, resolve: (LocalDate) -> ResolvedDay, profile: PayProfile?): List<LeaveUse> =
        entries.filter { it.leave != null }.mapNotNull { e ->
            val leave = resolve(e.date).leave ?: return@mapNotNull null
            when {
                leave.kind == LeaveKind.COMP -> LeaveUse(e.date, leave.days, LeavePool.COMP)
                !leave.kind.annual -> null
                profile?.rules?.holidayLeaveFree == true && KoreanHolidays.isHoliday(e.date) -> null
                else -> LeaveUse(e.date, leave.days)
            }
        }.sortedBy { it.date }

    /**
     * [PAYX-18] 보상휴가 earned by extra work marked as paid in time off: the pay months holding such work are
     * worked out, so the hours carry their premiums as the pay screen shows them. A day of 보상휴가 is the day's
     * 소정근로시간 (8 hours, or the 일당's).
     */
    fun compLots(profile: PayProfile?, entries: List<DayEntry>, resolve: (LocalDate) -> ResolvedDay, calculator: PayCalculator = PayCalculator()): List<LeaveLot> {
        if (profile == null) return emptyList()
        val months = entries.filter { e -> e.extras.any { it.compLeave } }.map { profile.period.monthOf(it.date) }.distinct()
        val dayMinutes = profile.dailyHours.coerceIn(60, 8 * 60).toDouble()
        return months.flatMap { m -> calculator.calculate(profile, m, resolve, checks = false).compLeave.entries }
            .sortedBy { it.key }
            .map { (date, minutes) ->
                LeaveLot(LotKind.COMP, Math.round(minutes / dayMinutes * 100) / 100.0, date, null,
                    note = "${PayCalculator.hours(minutes)} (가산 포함)")
            }
    }

    /** [LEAVE-01] The calendar's ledger on [today]. */
    fun summary(
        today: LocalDate,
        profile: PayProfile?,
        entries: List<DayEntry>,
        resolve: (LocalDate) -> ResolvedDay,
        stored: List<LedgerEntry>,
        until: LocalDate = today.plusYears(1),
        /** [RVH-38] An hourly job's average hours a week ([partTimeHours]). */
        weeklyHours: Double? = null,
    ): LeaveSummary = LeaveLedger.build(
        today = today,
        auto = autoLots(profile, until, weeklyHours),
        entries = stored,
        uses = uses(entries, resolve, profile),
        compLots = compLots(profile, entries, resolve),
        until = until,
    )
}
