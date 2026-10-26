package com.bamnat.core.pay

import com.bamnat.core.model.ShiftType
import java.time.YearMonth

/**
 * [PAYS-17] 1.27 순번 448 (사용자 2026-10-25 "규칙만 놓은 사람은 시수와 기본급등 필수 사항은 직접 입력해야해 … 바로 입력할수 있게
 * 안내 제안"): the values the company's rules leave to each person, still empty or guessed. Each is one line on the pay
 * screen ("이것만 넣으면 정확해져요") and on the calendar, with a button to the field; the list empties as they are put in.
 *
 * A payslip fills most of them at once (기본급, 시수 from its amounts), so the card says so too, but none needs one.
 */
object RuleOnlyNeeds {
    enum class Kind { BASE, MONTHLY_HOURS, SHIFT_TIMES, ITEM_AMOUNT, FIVE_OR_MORE }

    /**
     * A new monthly setup's 월 소정근로시간 is the law's 209 until the user says (the easy setup never asks it). Kept in
     * [PayProfile.estimatedFields] like [Unknowns]' keys, which [Unknowns.of] passes over; gone once the hours are set
     * anywhere ([settled]) or a payslip was matched ([PayProfile.verified]).
     */
    const val HOURS_UNSET = "hoursUnset"

    /** [p] with its 시수 said: both marks off. */
    fun settled(p: PayProfile): PayProfile = p.copy(estimatedFields = p.estimatedFields - Unknowns.MONTHLY_HOURS - HOURS_UNSET)

    data class Need(
        val kind: Kind,
        /** What to put in, as the button says it. */
        val label: String,
        /** Why it matters for the month's pay, one short sentence. */
        val why: String,
        /** The allowance ([Kind.ITEM_AMOUNT]) or kind of work ([Kind.SHIFT_TIMES]) it is about. */
        val itemId: String? = null,
        val shiftTypeId: Long? = null,
    )

    /**
     * What [profile] still needs for [month]; [worked] are the kinds of work on the calendar that month (a kind with no
     * times counts no hours, so neither its pay nor its night premium).
     */
    fun of(profile: PayProfile, month: YearMonth, worked: List<ShiftType>): List<Need> = buildList {
        val base = profile.base.on(month).value
        val baseName = when (profile.wageType) { WageType.MONTHLY -> "월 기본급"; WageType.HOURLY -> "시급"; WageType.DAILY -> "일당" }
        if (base <= 0) add(Need(Kind.BASE, baseName, "이 값이 없으면 급여가 0원으로 나와요"))
        else if (Unknowns.BASE in profile.estimatedFields) add(Need(Kind.BASE, baseName, "대략 넣은 값이라 합계가 달라질 수 있어요"))
        val hoursOpen = Unknowns.MONTHLY_HOURS in profile.estimatedFields || HOURS_UNSET in profile.estimatedFields
        if (profile.wageType == WageType.MONTHLY && profile.publicGrade == null && profile.verified == null && hoursOpen) {
            add(Need(Kind.MONTHLY_HOURS, "월 소정근로시간(시수)", "통상시급 = 기본급 ÷ 시수라 연장·야간 수당이 달라져요 (지금 ${profile.monthlyHours}시간)"))
        }
        worked.filter { !it.isOff && (it.start == null || it.end == null) }.distinctBy { it.id }.forEach { t ->
            add(Need(Kind.SHIFT_TIMES, "‘${t.name}’ 근무 시각", "시각이 없으면 그 근무 시간과 야간 수당을 셀 수 없어요", shiftTypeId = t.id))
        }
        profile.items.filter { Unknowns.item(it.id) in profile.estimatedFields }.forEach { item ->
            add(Need(Kind.ITEM_AMOUNT, "‘${item.name}’ 금액", "금액을 모르면 합계에 넣지 않아요", itemId = item.id))
        }
        if (Unknowns.FIVE_OR_MORE in profile.estimatedFields) {
            add(Need(Kind.FIVE_OR_MORE, "직원 5명 이상인지", "5명 미만이면 연장·야간·휴일 가산이 없어요"))
        }
    }
}
