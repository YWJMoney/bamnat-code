package com.bamnat.core.pay

import kotlin.math.abs

/**
 * 1.39 발굴 3-2 (작은 판, 사용자 2026-11-07 18:45 발굴 요청): with someone else's pay way in use its calculation is never shown, so a
 * payslip is checked against it by amounts alone: the 지급 총액 and, when the user has them, a few lines of the payslip (연장·야간·
 * 휴일·수당과 상여). What comes back says only whether it matches and where it differs most; the app then sends 맞아요, or a
 * 보완 요청 with those places picked. No rate, multiplier or formula is part of it.
 */
object SlipGlance {
    /** A part of the payslip, with the 보완 요청 field it stands for. */
    enum class Part(val fix: String, val label: String, val kinds: Set<LineKind>) {
        OVERTIME("overtime", "연장", setOf(LineKind.OVERTIME, LineKind.EXTRA, LineKind.EXTRA_HOURS)),
        NIGHT("night", "야간", setOf(LineKind.NIGHT)),
        HOLIDAY("holiday", "휴일", setOf(LineKind.HOLIDAY)),
        ALLOWANCE("allowance", "수당·상여", setOf(LineKind.ALLOWANCE)),
    }

    /**
     * [diff] the app's 지급 총액 less the payslip's (more is +); [matched] when it is within [TOTAL_SLACK]; [differs] the parts given
     * whose amounts are apart by more than [PART_SLACK], the furthest first.
     */
    data class Result(val diff: Long, val matched: Boolean, val differs: List<Part>) {
        /** The 보완 요청 fields to pick for the user. */
        val fixFields: List<String> get() = differs.map { it.fix }
    }

    /** Within 0.5 % of the payslip's total, at least 1,000원: 원 단위 rounding and 절사 differ between payroll programs. */
    const val TOTAL_SLACK = 0.005
    /** A part apart by more than 2 %, at least 1,000원. */
    const val PART_SLACK = 0.02

    /** What the app counts for [part] in [month]. */
    fun amount(month: MonthPay, part: Part): Long = month.lines.filter { it.kind in part.kinds }.sumOf { it.amount }

    fun of(month: MonthPay, slipTotal: Long, parts: Map<Part, Long> = emptyMap()): Result {
        val diff = month.total - slipTotal
        val matched = abs(diff) <= maxOf(1_000L, (slipTotal * TOTAL_SLACK).toLong())
        val differs = parts.filterValues { it >= 0 }.mapNotNull { (part, slip) ->
            val gap = abs(amount(month, part) - slip)
            (part to gap).takeIf { gap > maxOf(1_000L, (slip * PART_SLACK).toLong()) }
        }.sortedByDescending { it.second }.map { it.first }
        return Result(diff, matched && differs.isEmpty(), differs)
    }

    /**
     * 2.0.0 [RVN-20] 검토 U10a-15 (명세서 사진에서 읽어 채우기): what a payslip read on the phone ([PayslipReader]) gives the check, its
     * 지급 총액 and the parts it prints (추가 근무 with 연장, 상여 with 수당); a part the payslip does not print is left out, so it is
     * not taken as 0.
     */
    fun fromSlip(read: PayslipRead): Pair<Long?, Map<Part, Long>> {
        fun sum(vararg roles: SlipRole): Long? = read.lines.filter { it.role in roles && it.amount != null }.takeIf { it.isNotEmpty() }?.sumOf { it.amount!! }
        val parts = buildMap {
            sum(SlipRole.OVERTIME, SlipRole.EXTRA_WORK)?.let { put(Part.OVERTIME, it) }
            sum(SlipRole.NIGHT)?.let { put(Part.NIGHT, it) }
            sum(SlipRole.HOLIDAY)?.let { put(Part.HOLIDAY, it) }
            sum(SlipRole.ALLOWANCE, SlipRole.BONUS)?.let { put(Part.ALLOWANCE, it) }
        }
        return read.gross to parts
    }

    /** One line for the result: "명세서와 맞아요 (차이 800원)", or how far apart and where it differs most. */
    fun line(r: Result, won: (Long) -> String): String = when {
        r.matched -> "지급 총액이 명세서와 맞아요" + if (r.diff != 0L) " (차이 ${won(abs(r.diff))})" else ""
        else -> (if (r.diff == 0L) "지급 총액은 같지만" else "명세서보다 ${won(abs(r.diff))} ${if (r.diff > 0) "많아요" else "적어요"}") +
            if (r.differs.isNotEmpty()) " · ${r.differs.joinToString("·") { it.label }}에서 차이가 커요"
            else ". 기본급과 내 수당 금액이 명세서와 같은지 먼저 봐 주세요"
    }
}
