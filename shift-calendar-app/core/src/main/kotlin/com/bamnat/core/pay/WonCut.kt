package com.bamnat.core.pay

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * [P3-ACC] 원 금액 × 요율을 10원 미만 버림으로 (공단 고지·간이세액표 계산 방식). A Double product drops
 * just under a round amount (3,000,000 × 0.009 = 26,999.999…), and cutting that to 10원 lost 10원; the rates
 * are taken at their written decimal (`BigDecimal.valueOf`) and multiplied exactly, so 27,000원 stays 27,000원.
 */
object WonCut {
    /** [base] × each of [rates], exactly, then cut to 10원 (never below 0). */
    fun cut10(base: Long, vararg rates: Double): Long {
        var v = BigDecimal.valueOf(base)
        for (r in rates) v = v.multiply(exact(r))
        return (v.setScale(0, RoundingMode.FLOOR).toLong() / 10 * 10).coerceAtLeast(0)
    }

    /**
     * [P3-ACC] A ratio of two written rates kept as the pair, so 장기요양보험료 = 건강보험료 × 장기요양보험료율 ÷
     * 건강보험료율 (공단 산정식) multiplies before it divides: 107,850 × 1.0% ÷ 7.19% is exactly 15,000원.
     */
    data class Ratio(val top: BigDecimal, val bottom: BigDecimal) {
        fun toDouble(): Double = top.divide(bottom, MathContext.DECIMAL64).toDouble()

        companion object {
            fun of(top: Double, bottom: Double) = Ratio(BigDecimal.valueOf(top), BigDecimal.valueOf(bottom))
            fun of(rate: Double) = Ratio(exact(rate), BigDecimal.ONE)
        }
    }

    /** [base] × [ratio], cut to 10원. */
    fun cut10(base: Long, ratio: Ratio): Long =
        (BigDecimal.valueOf(base).multiply(ratio.top).divide(ratio.bottom, 6, RoundingMode.FLOOR)
            .setScale(0, RoundingMode.FLOOR).toLong() / 10 * 10).coerceAtLeast(0)

    /** A rate made by dividing two written rates (장기요양 ÷ 건강), rounded to 12 significant digits so 0.9994 ÷ 7.19 is 0.139. */
    fun exact(rate: Double): BigDecimal = BigDecimal.valueOf(rate).round(MathContext(12, RoundingMode.HALF_EVEN))
}
