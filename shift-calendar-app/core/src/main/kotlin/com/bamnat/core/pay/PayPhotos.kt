package com.bamnat.core.pay

import com.bamnat.core.model.ExtraKind
import com.bamnat.core.model.ShiftType

/**
 * [READ-08] A payslip and a page of the company's pay rules (취업규칙의 임금 조항, 수당표, 계산 방법) sent together as two
 * photos (사용자 2026-10-25: "명세서와 회사 계산방법 사진을 두 장 보낼 수 있게 해 줘 … 보낸 사진 기반 분석으로 급여와 세금을
 * 계산"). Each photo's text is told apart by what it reads as, in whichever order they came: the one with amounts by name
 * is the payslip, the rest are rules. What comes out goes on: the payslip to 명세서로 채우기·맞춰 보기, the rules to the
 * user's ticks and then the company's rules (묶음 V), the payslip's 지급·공제·실수령 to the tax estimate (묶음 W).
 * Read on the phone only (결정 D68): nothing is sent anywhere.
 */
data class PayPhotos(
    val slip: PayslipRead?,
    val rules: List<RuleFinding>,
    /** Which of the texts was taken for the payslip, or -1. */
    val slipIndex: Int = -1,
) {
    /** Share of 지급 총액 taken off (4대 보험과 세금), for the tax estimate; null without both. */
    val deductionRate: Double?
        get() {
            val g = slip?.gross ?: return null
            val d = slip.deducted ?: return null
            return if (g > 0 && d in 0..g) d.toDouble() / g else null
        }

    val empty: Boolean get() = (slip?.lines.isNullOrEmpty()) && rules.isEmpty()

    companion object {
        /** Amounts by name a text needs to be taken for a payslip. */
        const val SLIP_LINES = 2

        fun read(texts: List<String>, kinds: List<ExtraKind> = emptyList(), shiftTypes: List<ShiftType> = emptyList(), fiveOrMore: Boolean = true): PayPhotos {
            val slips = texts.map { PayslipReader.read(it) }
            val found = texts.map { RuleText.read(it, kinds, shiftTypes, fiveOrMore) }
            val score = slips.map { s -> s.lines.count { it.amount != null && (it.isEarning || it.role in TOTALS) } }
            // A rules page names amounts too (식대 월 200,000원): it is a payslip only when it reads as more pay lines than rules.
            val best = score.indices.filter { score[it] >= SLIP_LINES && score[it] > found[it].size }.maxByOrNull { score[it] } ?: -1
            val rules = texts.indices.filter { it != best }.flatMap { found[it] }
            return PayPhotos(slips.getOrNull(best), rules, best)
        }

        private val TOTALS = setOf(SlipRole.GROSS, SlipRole.TOTAL, SlipRole.NET, SlipRole.DEDUCTION_TOTAL)
    }
}
