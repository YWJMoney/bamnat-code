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
        return RuleOnlyNeeds.settled(withBase.copy(monthlyHours = h))
    }

    /** What a month comes to is worth showing only with a base of the user's own (else it reads 0원 or a guess). */
    fun showsEstimate(p: PayProfile, month: YearMonth): Boolean = p.base.on(month).value > 0
}
