package com.bamnat.core.pay

import java.time.YearMonth
import kotlin.math.roundToLong

/**
 * [PAYF-06] 1.29 묶음 AP (사용자 2026-10-27 "기본급은 무조건 입력을 해야해 … 회사를 고르더라도 공유된 정보는 규칙이므로 기본급
 * 기반 동작"): a company's pay way brings rules, never amounts, so every way into the pay ends on the 기본급 (or 시급·일당).
 * This says when it is still to be asked, takes the 통상시급's hours (월 소정근로시간, or the 통상시급 itself to work them out)
 * and puts both on the setup without touching anything else.
 */
object BaseAsk {
    /** The hours a month most often counts, for the chips: 주 40시간 + 주휴 (209), and the two most shift workplaces use. */
    val HOURS_CHOICES = listOf(209, 226, 243)
    /** Hours outside this are a typing slip, not a 소정근로시간. */
    val HOURS_RANGE = 100..320

    /** Whether [p] still lacks its own base for [month]: none (0원) or one only guessed (대략·추정). */
    fun needed(p: PayProfile?, month: YearMonth): Boolean =
        p == null || p.base.on(month).value <= 0 || Unknowns.BASE in p.estimatedFields

    /** The base [p] has for [month], or null when it has none of its own yet (0원). */
    fun known(p: PayProfile?, month: YearMonth): Long? = p?.base?.on(month)?.value?.takeIf { it > 0 }

    /**
     * The 월 소정근로시간 that makes [p]'s 통상시급 [hourly] with [base]: (기본급 + 통상 수당 + 상여 월할) ÷ 통상시급. Null for pay
     * that is not monthly, a 통상시급 of nothing, or hours no workplace counts ([HOURS_RANGE]).
     */
    fun hoursFromHourly(p: PayProfile, month: YearMonth, base: Long, hourly: Long): Int? {
        if (p.wageType != WageType.MONTHLY || hourly <= 0 || base <= 0) return null
        val withBase = p.copy(base = ValueTimeline(listOf(DatedValue(month.atDay(1), base))))
        val ordinary = PayCalculator.ordinaryWage(withBase, month).total
        val hours = (ordinary.toDouble() / hourly).roundToLong().toInt()
        return hours.takeIf { it in HOURS_RANGE }
    }

    /**
     * [p] with its own [base] from [from] and, when said, its [hours]. A base that was missing or guessed is replaced where it
     * stood (the months already worked come to the same amount); a known one changes from [from] on, as an edit would.
     */
    fun apply(p: PayProfile, base: Long, from: YearMonth, hours: Int? = null): PayProfile {
        require(base > 0) { "base" }
        val at = p.base.on(from)
        val timeline = when {
            at.basedOn == null -> p.base.with(DatedValue(from.atDay(1), base))
            at.value <= 0 || Unknowns.BASE in p.estimatedFields ->
                p.base.copy(entries = p.base.entries.map { if (it == at.basedOn || it.value <= 0) it.copy(value = base) else it })
            at.value == base -> p.base
            else -> p.base.with(DatedValue(from.atDay(1), base))
        }
        val withBase = p.copy(base = timeline, estimatedFields = p.estimatedFields - Unknowns.BASE)
        val h = hours?.takeIf { it in HOURS_RANGE && p.wageType == WageType.MONTHLY } ?: return withBase
        // [FIN130-04] Where the hours came from: the setup's own (a company's way says PLAZA) when they are the same, the user's
        // (TYPED) when they changed them, so the 통상시급 card says 광장 or 직접 truly.
        val source = if (h == p.monthlyHours) p.hoursSource else HourlySource.TYPED
        return RuleOnlyNeeds.settled(withBase.copy(monthlyHours = h, hoursSource = source))
    }

    /** What a month comes to is worth showing only with a base of the user's own (else it reads 0원 or a guess). */
    fun showsEstimate(p: PayProfile, month: YearMonth): Boolean = p.base.on(month).value > 0

    /**
     * [PAYG-02] 1.30 묶음 BA (사용자 2026-10-27 "기본급 칸(비우면 다음으로 못 감) — 입력을 안 해도 잘 넘어가고 있음"): a company's pay
     * way is never put in on a base the user did not give. The field starts empty; a base the setup already has is offered as one
     * chip ("지금 기본급 2,090,000원 그대로 쓰기"), never typed in for the user. Null when there is none of the user's own (0원,
     * or only guessed).
     */
    fun keepChip(p: PayProfile?, month: YearMonth): Long? = known(p, month)?.takeIf { p != null && Unknowns.BASE !in p.estimatedFields }

    /** [PAYG-01] Whether [base] lets a company's pay way be put in: something above 0원, typed or picked. */
    fun ready(base: Long?): Boolean = base != null && base > 0

    /**
     * [PAYG-08] 1.30 BA (사용자 "통상임금과 근로시수는 광장에 제공된 정보가 있으면 해당 정보를 따르게"): the hour chips, the
     * company's own first (LG화학 180) and then the usual ones, each once. A company's hours outside [HOURS_RANGE] are not offered.
     */
    fun hoursChoices(company: Int?): List<Int> = (listOfNotNull(company?.takeIf { it in HOURS_RANGE }) + HOURS_CHOICES).distinct()

    /**
     * [PAYG-08] The hours to say back to the company's 광장 after the way went in with them: the user's [hours] when the company
     * said other ones ([company]); null when they are the company's, not said, or the company said none ("고치면 의견으로").
     */
    fun hoursToPropose(hours: Int?, company: Int?): Int? =
        hours?.takeIf { company != null && company in HOURS_RANGE && it in HOURS_RANGE && it != company }
}
