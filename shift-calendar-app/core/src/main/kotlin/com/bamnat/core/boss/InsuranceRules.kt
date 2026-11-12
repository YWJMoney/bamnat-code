package com.bamnat.core.boss

import java.time.Period
import java.time.YearMonth

/**
 * 1.38 [BOSS-11] 4대보험 가입 대상 자동 판단 (정확도 공식 대조 G2·G3·G6, 갈래 정확도 PR #186): what the 사장님 ticked is the
 * wish; the law decides who is in. Each 빠짐 comes with one line why, shown beside the staff member.
 * - G2 월 60시간(주 15시간) 미만 단시간 근로자: 국민연금(시행령 제2조)·건강보험(시행령 제9조)·고용보험(법 제10조) 제외.
 *   고용보험은 3개월 이상 계속 근로하면 가입(시행령 제3조).
 * - G3 국민연금은 만 18세 이상 60세 미만(법 제6조).
 * - G6 65세 이후 새로 고용된 사람은 고용보험(실업급여) 제외(법 제10조).
 * Without a 생년월일 the age rules are not applied (생년월일은 선택, 결정 P3-D8), and the line says so.
 */
object InsuranceRules {
    /** 주 15시간: 월 60시간 기준 (근로기준법 제18조 제3항과 같은 선). */
    const val WEEK_MINUTES = 15 * 60

    data class Result(val effective: Insurance, val reasons: List<String>)

    fun of(place: Workplace, s: StaffMember, month: YearMonth): Result {
        val want = s.insurance
        var ins = want
        val why = ArrayList<String>()
        val weekly = s.weekly.values.sumOf { it.paidMinutes(place.legalBreaks) }
        val day = month.atEndOfMonth()
        // 2.0.0 [RVJ-16] 검토 BOSS-74: an 일급·시급 under 15 hours a week by the usual week is out of 국민연금·건강보험, whatever the form
        // (it was 시급 only); a 월급 is a whole month's pay and stays in.
        if (s.form != WageForm.MONTH && weekly in 1 until WEEK_MINUTES) {
            val threeMonths = s.hiredOn?.let { !it.plusMonths(3).isAfter(month.atDay(1)) } == true
            if (want.pension || want.health) why += "주 15시간(월 60시간) 미만이라 국민연금·건강보험 가입 대상이 아니에요"
            ins = ins.copy(pension = false, health = false)
            if (want.employment && !threeMonths) { why += "주 15시간 미만은 3개월 넘게 일해야 고용보험 대상이에요"; ins = ins.copy(employment = false) }
        }
        s.birth?.let { b ->
            val age = Period.between(b, day).years
            if (ins.pension && age >= 60) { why += "만 60세가 넘어 국민연금 의무 가입이 끝났어요"; ins = ins.copy(pension = false) }
            if (ins.pension && age < 18) { why += "만 18세 미만은 국민연금 가입 대상이 아니에요(본인이 원하면 가입)"; ins = ins.copy(pension = false) }
            val hiredAge = s.hiredOn?.let { Period.between(b, it).years }
            if (ins.employment && hiredAge != null && hiredAge >= 65) { why += "65세 이후 새로 일한 분은 고용보험(실업급여)을 떼지 않아요"; ins = ins.copy(employment = false) }
        }
        if (s.birth == null && want.pension) why += "생년월일을 넣으면 나이에 따른 국민연금·고용보험도 맞춰요"
        // [RVJ-16] 사업소득 3.3%는 근로자가 아니라 4대보험이 없어요; ticked boxes are not counted.
        if (want.tax == StaffTax.BIZ && ins.any) { why += "3.3%로 주는 분은 4대보험이 없어요. 실제로 근로자라면 소득세 떼는 법을 바꿔 주세요"; ins = ins.copy(pension = false, health = false, employment = false) }
        // [RVJ-16] 일용근로자: 고용보험은 첫날부터(근로내용확인신고). 국민연금·건강보험은 한 달 넘게 일하며 월 8일 이상이면 대상이라
        // (국민연금법 시행령 제2조, 국민건강보험법 시행령 제9조: 확인 필요) the 사장님 ticks those as the month goes.
        if (want.tax == StaffTax.DAILY && (want.pension || want.health)) why += "일용직은 한 달 넘게 월 8일 이상 일하면 국민연금·건강보험 대상이에요(확인 필요)"
        // [RVJ-16] 검토 BOSS-74: 외국인 직원은 국민연금·고용보험이 나라·체류자격에 따라 달라요 (확인 필요: 국민연금법 제126조, 고용보험법 제10조의2).
        if (want.foreign && (ins.pension || ins.employment || want.tax == StaffTax.WAGE))
            why += "외국인 직원은 나라에 따라 국민연금이, 체류자격에 따라 고용보험이 달라요. 4대사회보험 정보연계센터에서 확인해 주세요"
        return Result(ins, why)
    }

    /**
     * 1.39 G2 (PC f7f61818 '월급 직원을 넣어도 4대보험이 비어 사장님 4대보험 0원'): what a new staff member is enrolled in by law before
     * the boss says otherwise. 월급, or a usual week of 15시간 이상 (월 60시간), is in all four (국민연금법 제8조, 국민건강보험법 제6조,
     * 고용보험법 제8조); a shorter week is left empty and [of] says why. [legalBreaks] as the place counts its 휴게.
     */
    fun defaultFor(s: StaffMember, legalBreaks: Boolean, on: java.time.LocalDate? = null): Insurance {
        val weekly = s.weekly.values.sumOf { it.paidMinutes(legalBreaks) }
        val all = s.form == WageForm.MONTH || weekly >= WEEK_MINUTES
        val ins = (if (all) Insurance.ALL else Insurance()).copy(tax = s.insurance.tax)
        // 1.39 G2 PC 5066b027: a 16세 직원 had 국민연금 ticked by default beside '만 18세 미만은 국민연금 가입 대상이 아니에요'. With a
        // 생년월일 and a day, the 국민연금 age (만 18세 이상 60세 미만, 법 제6조) sets that chip too; [of] still decides the month.
        val age = if (s.birth != null && on != null) Period.between(s.birth, on).years else null
        return if (age != null && (age < 18 || age >= 60)) ins.copy(pension = false) else ins
    }

    /** Someone the law puts in the 4대보험 with none ticked: the 인건비 shown is short of what the boss pays. */
    fun unticked(s: StaffMember, legalBreaks: Boolean): Boolean = !s.insurance.any && s.insurance.tax != StaffTax.BIZ && defaultFor(s, legalBreaks).any

    /** G4 건강보험 보수월액 하한 (국민건강보험법 시행령 제32조): 28만 원. */
    const val HEALTH_BASE_MIN = com.bamnat.core.pay.TakeHome.HEALTH_BASE_MIN

}
