package com.bamnat.core.pay

import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.roundToLong

/**
 * [ITEM-01] 1.30 BC: what an allowance pays when its amount is not the user's own typing: the company's one amount
 * ([ItemShare.FIXED] with [PayItem.fixedAmount]) or a share of another amount ([ItemShare.PERCENT]). The calculator works a
 * month out on [resolve]d items, so every line that reads an allowance's amount (통상임금, 상여 기준, 수당 줄) sees the same.
 */
object ItemShares {
    /** The date a resolved amount is set from: before any month a profile is worked out for. */
    private val SINCE: LocalDate = LocalDate.of(2000, 1, 1)

    /** Most a percent allowance may be, 5.0 = 500% (기본급2 = 기본급의 100%도 있어요). */
    const val MAX_PERCENT = 5.0

    /**
     * [p] with every company amount and percent allowance as the amount it pays in [month]. A percent is of the month's
     * amounts with no percent allowance in them, so one never feeds another (or itself). [p] itself when there is none.
     */
    fun resolve(p: PayProfile, month: YearMonth): PayProfile {
        if (p.items.none { it.isPercent || it.isCompanyFixed }) return p
        val fixed = p.copy(items = p.items.map { if (it.isCompanyFixed) it.copy(amount = once(it.fixedAmount ?: 0)) else it })
        val basis = fixed.copy(items = fixed.items.filter { !it.isPercent })
        return fixed.copy(items = fixed.items.map { i ->
            if (i.isPercent) i.copy(amount = once(Math.round(basis.basisAmount(i.percentOf, month) * (i.percent ?: 0.0)))) else i
        })
    }

    /** The amount [item]'s percent is of in [month] ([resolve]'s basis), for the line's formula. */
    fun basisOf(p: PayProfile, item: PayItem, month: YearMonth): Long {
        val fixed = p.copy(items = p.items.filter { !it.isPercent }.map { if (it.isCompanyFixed) it.copy(amount = once(it.fixedAmount ?: 0)) else it })
        return fixed.basisAmount(item.percentOf, month)
    }

    /** What [item] pays once in [month]: its own amount, the company's, or its percent; for screens that list allowances. */
    fun amountOf(p: PayProfile, item: PayItem, month: YearMonth): Long =
        resolve(p, month).items.firstOrNull { it.id == item.id }?.amount?.on(month)?.value ?: item.amount.on(month).value

    /** "기본급의 10%", "통상임금의 2.5%". */
    fun percentText(item: PayItem): String = "${item.percentOf.label}의 ${pct(item.percent ?: 0.0)}"

    /** One line for an allowance's kind: "회사 공통 · 매달 100,000원", "기본급의 10% · 매달", "사람마다 달라요". */
    fun describe(item: PayItem): String = when {
        item.isPercent -> "${percentText(item)} · ${item.cadence.label}"
        item.share == ItemShare.FIXED -> listOfNotNull("회사 공통", item.cadence.label, item.fixedAmount?.takeIf { it > 0 }?.let(::won)).joinToString(" · ")
        else -> ItemShare.PERSONAL.label
    }

    /** "10%", "2.5%": a percent with one decimal when it has one. */
    fun pct(x: Double): String {
        val v = (x * 1000).roundToLong() / 10.0
        return if (v == Math.floor(v)) "${v.toLong()}%" else "$v%"
    }

    private fun once(v: Long) = ValueTimeline(listOf(DatedValue(SINCE, v)))
}
