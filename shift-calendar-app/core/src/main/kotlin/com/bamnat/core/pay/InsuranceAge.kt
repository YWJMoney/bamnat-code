package com.bamnat.core.pay

import java.time.YearMonth

/**
 * 2.0.0 [RVJ-81] 결정 5 (G3-B5, 처리방침 3.14 7일 줄 '생년(선택)'): the 4대보험 lines the law leaves out by age, from the pay setup's
 * [PayProfile.birthYear] alone (no month or day is asked, so a year where the age changes says so instead of guessing).
 *
 * - 국민연금: 18세 이상 60세 미만이 가입 대상(국민연금법 제6조, 제8조 ①). 사업장가입자는 60세가 된 날의 다음 날 자격을 잃고
 *   (제12조 ① 4호), 가입기간은 자격을 잃은 날의 전날이 속하는 달까지예요(제17조 ①). So 국민연금 is not taken from the year after
 *   the one the worker turns 60; in that year it is taken until the birthday month. 60세가 된 뒤 스스로 신청한 임의계속가입자는
 *   65세까지 내요(제13조 ①): [PayProfile.insuranceKeep] keeps the line.
 * - 고용보험: 65세 이후에 고용된 사람(65세 전부터 피보험 자격을 유지하던 사람이 계속 고용된 경우는 빼고)에게는 실업급여를 적용하지
 *   않아요(고용보험법 제10조 ②). 실업급여 보험료를 걷지 않고(보험료징수법 제13조 ③), 근로자 몫은 실업급여 보험료의 절반뿐이라
 *   (같은 조 ②) the worker's line is all gone. It needs [PayProfile.hireDate]; without it the line is said, not guessed.
 * - 건강보험에는 나이 기준이 없어요(국민건강보험법 제5조 ①).
 *
 * Law text read on law.go.kr 2026-11-19 (국민연금법 법률 제22096호 2026-10-20 시행, 고용보험법 법률 제21473호 2026-09-18 시행,
 * 보험료징수법 법률 제21532호 2026-10-08 시행). 확인 필요: whether a later-dated amendment is waiting.
 */
object InsuranceAge {
    /** What the age says of one line in a month: [off] when it is surely left out; [note] says why, or what the app cannot tell. */
    data class Line(val line: InsuranceLine, val off: Boolean, val note: String)

    /** The age's word on each line it touches in [month]; empty without a [PayProfile.birthYear]. */
    fun lines(profile: PayProfile, month: YearMonth): List<Line> {
        val born = profile.birthYear ?: return emptyList()
        val out = mutableListOf<Line>()
        val turns = month.year - born
        when {
            turns >= 61 -> out += Line(InsuranceLine.PENSION, true, "만 60세가 지나 국민연금을 떼지 않아요")
            turns == 60 -> out += Line(InsuranceLine.PENSION, false, "올해 만 60세가 돼요. 생일이 있는 달까지 떼고 그다음 달부터 떼지 않아요")
        }
        val hired = profile.hireDate
        if (hired == null) {
            if (turns >= 66) out += Line(InsuranceLine.EMPLOYMENT, false, "입사일을 적으면 65세 이후 새로 일한 곳인지 셈해요(그렇다면 고용보험을 떼지 않아요)")
        } else {
            val atHire = hired.year - born
            when {
                atHire >= 66 -> out += Line(InsuranceLine.EMPLOYMENT, true, "65세 이후 새로 일한 곳이라 고용보험을 떼지 않아요")
                atHire == 65 -> out += Line(InsuranceLine.EMPLOYMENT, false, "입사한 해에 만 65세가 됐어요. 생일 뒤에 입사했다면 고용보험을 떼지 않아요")
            }
        }
        return out
    }

    /** The lines the age leaves out of [month], less those the user still pays ([PayProfile.insuranceKeep]). */
    fun off(profile: PayProfile, month: YearMonth): Set<InsuranceLine> =
        lines(profile, month).filter { it.off && it.line !in profile.insuranceKeep }.map { it.line }.toSet()

    /** Why each line [off] leaves out is left out, for [TakeHome]'s notes. */
    fun why(profile: PayProfile, month: YearMonth): Map<InsuranceLine, String> =
        lines(profile, month).filter { it.off && it.line !in profile.insuranceKeep }.associate { it.line to "생년 ${profile.birthYear}년" }

    /**
     * One press on a line's '안 뗌' chip: a chip that shows off (by hand or by age) turns the line back on (an age line is then kept,
     * '그래도 내요'); one that shows on turns it off (an age line by taking back the keep, any other by hand).
     */
    fun toggle(profile: PayProfile, line: InsuranceLine, month: YearMonth): PayProfile {
        val byAge = lines(profile, month).any { it.line == line && it.off }
        val shownOff = line in profile.insuranceOff || (byAge && line !in profile.insuranceKeep)
        return when {
            shownOff -> profile.copy(insuranceOff = profile.insuranceOff - line,
                insuranceKeep = if (byAge) profile.insuranceKeep + line else profile.insuranceKeep)
            byAge -> profile.copy(insuranceKeep = profile.insuranceKeep - line)
            else -> profile.copy(insuranceOff = profile.insuranceOff + line)
        }
    }

    /** A 생년 the field takes: four digits, 14 to 100 years before [year]. */
    fun valid(birthYear: Int, year: Int): Boolean = birthYear in (year - 100)..(year - 14)
}
