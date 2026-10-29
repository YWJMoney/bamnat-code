package com.bamnat.core.pay

import java.time.YearMonth
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * [PLAZA-30] 1.27 묶음 AI (사용자 2026-10-25: "실제로 명세서로 확인한 것만 체크 가능하게"): what a 명세서와 맞춰 봤어요 tick
 * rests on. Only a month this calendar really matched against a payslip ([PayProfile.verified], kept in the pay file and
 * so in the backup) counts, and only when the app's month came within the match's tolerance. What leaves the phone is the
 * month and yes or no ([month], [match]); never an amount, a total or a photo (결정 D68, AI-D3).
 */
data class SlipProof(val month: YearMonth, val match: Boolean, val source: String) {
    /** Whether the tick may be on: a month that matched. */
    val counts: Boolean get() = match

    companion object {
        /** 명세서로 맞춰 보기 ([PayslipMatch]). */
        const val MATCH = "match"
        /** 명세서로 회사 방식 맞추기 ([SlipRules]). */
        const val RULES = "rules"
        /** A check older than this many months says little about the rule in use now (결정 AI-D3), as the server holds it. */
        const val MONTHS = 13L

        /** The proof [check] gives on [today]'s month, or null when there is none to give. */
        fun of(check: PayslipCheck?, today: YearMonth): SlipProof? {
            val c = check ?: return null
            if (c.gross <= 0 || c.appTotal <= 0) return null
            if (c.month.isAfter(today) || c.month.isBefore(today.minusMonths(MONTHS))) return null
            val gap = abs(c.gross - c.appTotal)
            val match = if (c.source == RULES) gap <= maxOf(PayslipMatch.TOLERANCE_WON, (c.gross * SlipRulesResult.CLOSE_SHARE).roundToLong())
            else gap <= PayslipMatch.tolerance(c.gross)
            return SlipProof(c.month, match, c.source)
        }

        /** D 1.32 [PAYA-01] How many matched months a calendar keeps. */
        const val KEEP = 12

        /**
         * D 1.32 [PAYA-01] (사용자 2026-10-28 "명세서로 맞춘 사용자가 '내 설정 보내기' 하면 자동 체크"): the proof from the best of
         * every month matched — the newest that matched; else the newest at all. 1.31 looked at the last match only, so a
         * later 맞춰 보기 that was off (or a month past 13) took the tick away from a calendar that had matched.
         */
        fun best(p: PayProfile?, today: YearMonth): SlipProof? {
            val all = listOfNotNull(p?.verified) + p?.checks.orEmpty()
            val proofs = all.mapNotNull { of(it, today) }.sortedByDescending { it.month }
            return proofs.firstOrNull { it.match } ?: proofs.firstOrNull()
        }

        /** D 1.32 [PAYA-01] [checks] with [new] put first, a month once, at most [KEEP]. */
        fun remember(checks: List<PayslipCheck>, new: PayslipCheck): List<PayslipCheck> =
            (listOf(new) + checks.filter { it.month != new.month }).sortedByDescending { it.month }.take(KEEP)

        /** What the report form says under the tick: why it is on, or why it cannot be. */
        fun words(proof: SlipProof?): String = when {
            // [J133-84] 1.33 광장 급여 써 보기: read as if the send needed a payslip; it never did, the tick only counts double.
            proof == null -> "이 달력에서 명세서로 맞춰 본 기록이 없어요. 맞춰 보지 않아도 보낼 수 있고, 맞춰 보면 2점이라 더 빨리 채택돼요"
            proof.match -> "${proof.month.monthValue}월 명세서로 맞춰 본 기록이 있어요 · 일치. 명세서로 확인한 설정은 2점이라 더 빨리 채택돼요"
            else -> "${proof.month.monthValue}월 명세서와 차이가 커서 확인으로 셀 수 없어요. 맞춰 보기로 차이를 줄이면 고를 수 있어요"
        }
    }
}
