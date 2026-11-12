package com.bamnat.core.boss

import com.bamnat.core.pay.StandardHours

/**
 * 2.0.0 [RVJ-42] 검토 PC-104 (통합 2차 PC Q19): a 월급 직원 whose usual week is past 40시간 (월~토 48시간) was paid 연장근로 on top of the
 * 월급 in the 대장 (2,500,000원 → 2,882,784원) while the 계약서 said '월급 2,500,000원 · 소정근로 48시간'. The boss is asked once
 * ([StaffMember.overtimeInPay]) and the 대장, 계약서 and 명세서 then say the same:
 * - false/null '넘는 시간은 더 줘요': the 월급 is the 40시간 week's (월 [hours]시간), the hours past it are 연장근로 paid on top.
 * - true '이 월급에 다 들어 있어요': the 월급 is split into 기본급 and a 고정 연장근로수당 for the usual hours past 40 ([base], [fixed]),
 *   so the month comes to the same 월급 and a month with more 연장 than that pays the difference (the engine's 고정 연장수당,
 *   [com.bamnat.core.pay.PayItem.fixedOvertimeMinutes]). 명세서 lists the two apart.
 *
 * The law, as far as it was read here:
 * - 근로기준법 제50조(1주 40시간·1일 8시간), 제53조(합의로 1주 12시간까지 연장), 제56조(연장근로 통상임금 50% 이상 가산). 상시 4명 이하
 *   사업장은 시행령 제7조 [별표 1]에 이 세 조가 없어 적용되지 않아요(제4장은 제54조·제55조제1항·제63조만). 별표 1은 2차 자료 두 곳에서 같은
 *   목록으로 읽었고, 국가법령정보센터 원문 파일은 이 환경에서 열리지 않아 원문 대조는 확인 필요.
 * - 포괄임금: 근로시간을 셀 수 있는 일이면 정액으로 법정수당을 갈음하는 약정은 근로시간 규정을 어기는 만큼 허용되지 않고, 모자라는 법정수당은
 *   줘야 해요(대법원 2010. 5. 13. 선고 2008다6052 판결). 그래서 '들어 있어요'도 실제 연장이 고정 몫보다 많은 달은 차액을 더해요.
 * - 고용노동부 '포괄임금 오남용 방지 지도 지침'(2026년 4월, 임금대장·명세서에 기본급과 수당을 나눠 적기)과 포괄임금 금지 근로기준법 개정안
 *   (2026년 10월 국회 심의 중)은 보도로만 확인: 확인 필요.
 */
object MonthlyOvertime {
    data class Split(
        /** The usual week's paid minutes, and the part past the 소정 (each day 8시간, the week 40시간). */
        val weekMinutes: Int,
        val overMinutes: Int,
        /** 1.5 where 제56조 applies, 1.0 in a place of 4 or fewer. */
        val rate: Double,
        /** 월 소정근로시간 of the 월급 (209 for a full week). */
        val hours: Int,
        /** The usual 연장 a month: [overMinutes] × 365 ÷ 7 ÷ 12. */
        val monthOverMinutes: Int,
        /** '더 줘요': what the usual 연장 comes to a month on top of the 월급, at 월급 ÷ [hours]. */
        val onTop: Long,
        /** '들어 있어요': 기본급 and 고정 연장근로수당, [base] + [fixed] = the 월급. */
        val base: Long,
        val fixed: Long,
    ) {
        val overHours: String get() = (overMinutes / 60.0).let { if (it % 1.0 == 0.0) "${it.toInt()}" else "%.1f".format(it) }
        val monthOverHours: String get() = "%.1f".format(monthOverMinutes / 60.0).removeSuffix(".0")
    }

    /** null unless [s] is paid by the month and its usual week goes past the 소정. */
    fun of(place: Workplace, s: StaffMember): Split? {
        if (s.form != WageForm.MONTH || s.base <= 0) return null
        val week = s.weekly.values.sumOf { it.paidMinutes(place.legalBreaks) }
        val over = week - PayrollLedger.weeklyMinutes(place, s)
        if (over <= 0) return null
        val rate = if (place.fivePlus != false) 1.5 else 1.0
        val hours = PayrollLedger.monthlyHours(place, s).coerceAtLeast(1)
        val monthOver = Math.round(over * 365.0 / 7 / 12).toInt()
        val onTop = Math.round(s.base.toDouble() / hours * monthOver / 60.0 * rate)
        val base = Math.round(s.base * hours / (hours + monthOver / 60.0 * rate))
        return Split(week, over, rate, hours, monthOver, onTop, base, s.base - base)
    }

    /** Whether the boss still has to say how the hours past 40 are paid. */
    fun unasked(place: Workplace, s: StaffMember): Boolean = s.overtimeInPay == null && of(place, s) != null

    /** The question, as one line (편집 창과 대장의 '확인 필요'). */
    fun ask(sp: Split): String = "한 주 ${sp.weekMinutes / 60}시간 월급이에요 · 40시간을 넘는 주 ${sp.overHours}시간을 월급 말고 더 주나요?"
}
