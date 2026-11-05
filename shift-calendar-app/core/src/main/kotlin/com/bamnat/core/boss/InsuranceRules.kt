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
        if (s.form == WageForm.HOUR && weekly in 1 until WEEK_MINUTES) {
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
        return Result(ins, why)
    }

    /** G4 건강보험 보수월액 하한 (국민건강보험법 시행령 제32조): 28만 원. */
    const val HEALTH_BASE_MIN = 280_000L

}
