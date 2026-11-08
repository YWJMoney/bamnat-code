package com.bamnat.core.pay

/**
 * [NURS-05] This month's nights and what they pay (순번 277), the number nurses and shift workers count most:
 * "이달 나이트 7개 · 나이트 수당 약 210,000원". The money is what the month's pay already holds for night work:
 * the 야간 가산 line, a per-night 나이트 수당 and a company or 공무원 야간근무수당 rule.
 */
object NightSummary {
    private val NIGHT_WORDS = listOf("야간", "나이트", "심야", "night")

    /** Night pay in [pay], or null when nothing in it is paid for nights. */
    fun allowance(pay: MonthPay): Long? {
        val lines = pay.lines.filter { l ->
            l.kind == LineKind.NIGHT || l.kind == LineKind.ALLOWANCE && NIGHT_WORDS.any { l.label.contains(it, ignoreCase = true) }
        }
        return lines.takeIf { it.isNotEmpty() }?.sumOf { it.amount }?.takeIf { it > 0 }
    }

    /**
     * The calendar line: "이달 야간 7일 · 야간 수당 약 210,000원", or only the count without pay. 1.39 PC eae4e024: 야간 and 일,
     * as the 휴식 점검 lines under it on the same card say ("한 달 야간 많음 · 11월 야간 16일").
     */
    fun line(nights: Int, allowance: Long?): String =
        "이달 야간 ${nights}일" + (allowance?.let { " · 야간 수당 약 ${"%,d".format(it)}원" } ?: "")
}
