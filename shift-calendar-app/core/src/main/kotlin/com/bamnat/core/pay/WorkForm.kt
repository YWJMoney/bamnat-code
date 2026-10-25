package com.bamnat.core.pay

import com.bamnat.core.model.ShiftType
import com.bamnat.core.model.WorkPattern
import com.bamnat.core.model.WorkTime
import java.time.LocalDate
import java.time.YearMonth

/**
 * [PAYS-12] 급여 간편 설정의 첫 질문: how the user works (research/1.25-V-조사.md 4장, research/1.21-직군-조사.md). Each form
 * brings the way such work is usually paid, so the user only says how much and checks one page; anything else comes
 * from a payslip photo or the detailed editor (D70). [occupation] picks the job preset and the 광장 job.
 */
enum class WorkForm(val label: String, val detail: String, val occupation: Occupation, val wageType: WageType, val asksHoliday: Boolean) {
    THREE_SHIFT("3교대", "모닝·이브닝·나이트를 돌아가며 일해요", Occupation.MANUFACTURING, WageType.MONTHLY, true),
    TWO_SHIFT("2교대", "주간·야간을 돌아가며 12시간씩 일해요", Occupation.MANUFACTURING, WageType.MONTHLY, true),
    FOUR_TEAM("4조 2교대", "주간 이틀·야간 이틀·쉬는 날 나흘을 돌아요", Occupation.MANUFACTURING, WageType.MONTHLY, true),
    NURSE("간호 듀티", "데이·이브닝·나이트 듀티표로 일해요", Occupation.NURSE, WageType.MONTHLY, true),
    ON_CALL("당직이 있는 근무", "평일 근무에 당직이 끼어요", Occupation.CUSTOM, WageType.MONTHLY, true),
    DAY_ON_DAY_OFF("24시간 맞교대·격일", "하루 일하고 하루 쉬어요(경비·시설·소방)", Occupation.SECURITY, WageType.MONTHLY, true),
    PUBLIC("공무원 교대", "공무원 수당 규정으로 받아요(경찰·소방·현업)", Occupation.PUBLIC, WageType.MONTHLY, true),
    OFFICE("주5일 낮 근무", "월~금 정해진 시간에 일해요", Occupation.CUSTOM, WageType.MONTHLY, false),
    PART_TIME("시급 파트타임", "일한 시간만큼 시급으로 받아요", Occupation.PART_TIME, WageType.HOURLY, false),
    DAILY("일당", "하루 일하면 정해진 금액을 받아요", Occupation.CUSTOM, WageType.DAILY, false),
    ;

    val shift: Boolean get() = asksHoliday && this != PUBLIC
}

object WorkForms {
    /**
     * [PAYS-12] The form the calendar points to, picked first on the screen; null when it says nothing. A pay setup
     * already made says it by its job; otherwise the kinds of shift: a shift of 20 hours or more is 맞교대, a kind named
     * 당직 is 당직, three working kinds with a night one are 3교대 (간호 듀티 when named D·E·N or 데이·이브닝·나이트),
     * two of about 12 hours are 2교대 (4조 2교대 when the order runs 8 days), and one daytime kind is 주5일.
     */
    fun guess(shiftTypes: List<ShiftType>, pattern: WorkPattern? = null, profile: PayProfile? = null): WorkForm? {
        when (profile?.occupation) {
            Occupation.NURSE -> return WorkForm.NURSE
            Occupation.PART_TIME -> return WorkForm.PART_TIME
            Occupation.SECURITY -> return WorkForm.DAY_ON_DAY_OFF
            Occupation.PUBLIC, Occupation.POLICE, Occupation.FIRE -> return WorkForm.PUBLIC
            else -> Unit
        }
        if (profile?.wageType == WageType.HOURLY) return WorkForm.PART_TIME
        if (profile?.wageType == WageType.DAILY) return WorkForm.DAILY
        val all = shiftTypes.filter { !it.isOff && it.start != null && it.end != null }
        // The kinds the order actually runs, when it says; a kind put in once by hand does not change the form.
        val inOrder = (pattern as? WorkPattern.Rotation)?.steps?.filterNotNull()?.toSet().orEmpty()
        val working = all.filter { it.id in inOrder }.ifEmpty { all }
        if (working.isEmpty()) return null
        fun span(t: ShiftType) = WorkTime.spanMinutes(t.start!!, t.end!!)
        fun night(t: ShiftType) = t.end!! <= t.start!! || t.start!!.hour >= 21
        if (working.any { span(it) >= 20 * 60 }) return WorkForm.DAY_ON_DAY_OFF
        if (working.any { it.name.contains("당직") }) return WorkForm.ON_CALL
        val nurseNames = working.map { it.label.uppercase() }.containsAll(listOf("D", "E", "N")) ||
            working.map { it.name }.containsAll(listOf("데이", "이브닝", "나이트"))
        if (working.size >= 3 && working.any(::night)) return if (nurseNames) WorkForm.NURSE else WorkForm.THREE_SHIFT
        if (working.size == 2 && working.all { span(it) >= 11 * 60 } && working.any(::night)) {
            return if ((pattern as? WorkPattern.Rotation)?.steps?.size == 8) WorkForm.FOUR_TEAM else WorkForm.TWO_SHIFT
        }
        if (working.size == 1 && !night(working.single())) return WorkForm.OFFICE
        return if (working.any(::night)) WorkForm.THREE_SHIFT else null
    }

    /**
     * [PAYS-12] The pay setup of [form] at [amount] (월 기본급, 시급 or 일당) from [since]. [existing] keeps everything it
     * holds (D70: a setup made before is never cleared): only the amount, the way it is paid (월급·시급·일당), the form's job when it had none, and the 빨간날
     * choice, when given, change. A new setup starts from the law, with the job's own first-setup rules ([PayPresets.firstSetup]).
     */
    fun apply(form: WorkForm, amount: Long, since: LocalDate, existing: PayProfile? = null, holiday: HolidayWork? = null): PayProfile {
        val base = existing?.let { if (it.base.on(since).value == amount) it.base else it.base.with(DatedValue(since, amount)) } ?: ValueTimeline(listOf(DatedValue(since, amount)))
        val p = existing?.copy(
            base = base,
            wageType = form.wageType,
            occupation = if (existing.occupation == Occupation.CUSTOM) form.occupation else existing.occupation,
            estimatedFields = existing.estimatedFields - Unknowns.BASE,
        ) ?: PayPresets.firstSetup(
            PayProfile(occupation = form.occupation, wageType = form.wageType, base = base,
                dailyLimitMinutes = PayPresets.of(form.occupation).dailyLimitMinutes),
        )
        return if (holiday == null || p.rules.holidayWork == holiday) p
        else p.copy(rules = p.rules.withHolidayWork(holiday), estimatedFields = p.estimatedFields - Unknowns.PUBLIC_HOLIDAYS)
    }
}

/**
 * [PAYS-12] "이렇게 계산해요": a pay setup in a few plain sentences, for the one page the easy setup ends on and the top of the
 * pay screen. Each says one thing the month is worked out by, the company's own way where it differs from the law.
 */
object PayStory {
    fun sentences(p: PayProfile, month: YearMonth, shiftNames: Map<Long, String> = emptyMap()): List<String> = buildList {
        val r = p.rules
        val amount = p.base.on(month).value
        val rates = RateTable.on(month.atDay(1))
        when (p.wageType) {
            WageType.MONTHLY -> {
                val hourly = Math.round(PayCalculator.ordinaryWage(p, month).total.toDouble() / p.monthlyHours.coerceAtLeast(1))
                add("월 기본급 ${won(amount)}, 통상시급 ${won(hourly)}(한 달 ${p.monthlyHours}시간)으로 셈해요")
            }
            WageType.HOURLY -> add("시급 ${won(amount)}으로 일한 시간만큼" + if (r.weeklyHolidayPay) ", 주 15시간 넘으면 주휴수당도" else "")
            WageType.DAILY -> add("일당 ${won(amount)}, 하루 ${PayCalculator.hours(p.dailyHours)} 기준")
        }
        val premiums = p.fiveOrMoreEmployees && !r.supervisoryApproved
        if (!p.fiveOrMoreEmployees) add("5명 미만 회사라 연장·야간·휴일 가산은 법으로 없어요")
        else {
            val night = r.nightPremium ?: rates.nightPremium
            add("야간(${r.nightLabel(rates)}) 일한 시간은 ${PayCalculator.pct(night)} 더")
            if (premiums) add("하루 8시간·주 40시간 넘으면 연장 ${PayCalculator.pct(r.overtimePremium ?: rates.overtimePremium)} 더" +
                if (p.scheduleIsRegular) " (근무표 시간은 기본 근무)" else "")
            add(r.holidayWorkText(shiftNames))
        }
        if (r.restDayWork != RestDayWork.LAW) add("쉬는 날 나가서 일하면 ${r.restDayWork.label} (${r.restDayWork.detail})")
        r.extra.forEach { (k, e) -> if (!e.isLegal) add("${r.extraName(k)}: ${e.describe()}") }
        p.items.filter { it.cadence != Cadence.BONUS }.forEach { add("${it.name} ${it.cadence.label} ${won(it.amount.on(month).value)}") }
        p.items.filter { it.cadence == Cadence.BONUS }.forEach { b ->
            add("${b.name} " + (b.percentOfBase?.let { "기본급의 ${ExtraRule.percent(it)}" } ?: won(b.amount.on(month).value)) +
                " (${b.months.sorted().joinToString(",")}월)")
        }
        p.customRules.filter { it.on }.forEach { add(it.describe(shiftNames, r.extraNames)) }
    }
}
