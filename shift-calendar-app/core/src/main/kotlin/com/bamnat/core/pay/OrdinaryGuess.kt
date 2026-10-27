package com.bamnat.core.pay

/**
 * Whether a newly added allowance is 통상임금, guessed from its name, so the switch starts where the law
 * usually puts it (순번 285, research/shift-wage-practices.md 5장). The user can always flip it.
 *
 * - Paid every month at a set amount for the work itself: 교대·근속·자격·면허·직책·직무·위험 수당, 정액 식대·교통비
 *   (대법원 2013.12.18. 2012다89399 전원합의체; 2024.12.19. 2020다247190 전원합의체로 고정성 요건 폐기).
 *   A flat 교대수당 paid for night rotations is 통상임금 too (대법원 2021.9.30. 2019다288898).
 * - Not 통상임금: 성과급·인센티브, 실비 (출장비 등), allowances that prepay or pay by the hour of 연장·야간·휴일
 *   work, and 가족수당 that follows the number of dependants.
 * - Anything else, or a 가족수당 that may be paid alike to everyone, is left to the user (null).
 */
object OrdinaryGuess {
    private val never = Regex("성과|인센티브|실비|출장|부양|고정.*(연장|시간외|OT)|연장|시간외|특근|휴일", RegexOption.IGNORE_CASE)
    private val night = Regex("야간|나이트|심야|night", RegexOption.IGNORE_CASE)
    private val usual = Regex("교대|근속|자격|면허|직책|직무|직급|위험|기술|식대|식비|급식|교통|차량|통근")

    fun of(name: String, cadence: Cadence): Boolean? {
        val n = name.filterNot { it.isWhitespace() }
        if (n.isEmpty() || cadence == Cadence.BONUS) return null
        if (never.containsMatchIn(n)) return false
        // A night allowance paid per night or per hour stands in for the night premium; a flat monthly one
        // for being on the rotation is 통상임금 (2019다288898).
        if (night.containsMatchIn(n)) return if (cadence == Cadence.MONTHLY && "교대" in n) true else false
        if (cadence == Cadence.MONTHLY && usual.containsMatchIn(n)) return true
        return null
    }
}

/**
 * [ITEM-05] 1.30 BC: who sets a new allowance's amount, guessed from its name so the writer of a company's setup starts on
 * the usual one (사용자 2026-10-27 "자격수당이나 직책수당 같은 것은 같은 회사여도 추가사항이 존재할수 있으므로").
 *
 * - [ItemShare.PERCENT]: a name that says it is a share ("%", "비율"), 기본급2 and 위험예지 (often a % of 기본급·통상임금).
 * - [ItemShare.PERSONAL]: 자격·면허·직책·직급·직무·가족·부양·근속(호봉처럼 사람마다), 성과·인센티브, 고정 연장(계약마다).
 * - [ItemShare.FIXED]: 식대·교통·통근·차량·교대·위험·야간·명절 정액, the same for everyone at a workplace.
 * - Anything else is left to the writer (null): 사람마다 is the safe start, since it is asked rather than assumed.
 */
object ShareGuess {
    private val percent = Regex("%|퍼센트|비율|기본급2|위험예지|예지")
    private val personal = Regex("자격|면허|직책|직급|직무|가족|부양|자녀|근속|호봉|성과|인센티브|고정.*(연장|시간외|OT)|조정", RegexOption.IGNORE_CASE)
    private val fixed = Regex("식대|식비|급식|중식|교통|통근|차량|교대|위험|야간|나이트|심야|명절|휴가비|복지")

    fun of(name: String): ItemShare? {
        val n = name.filterNot { it.isWhitespace() }
        if (n.isEmpty()) return null
        if (percent.containsMatchIn(n)) return ItemShare.PERCENT
        if (personal.containsMatchIn(n)) return ItemShare.PERSONAL
        if (fixed.containsMatchIn(n)) return ItemShare.FIXED
        return null
    }

    /** [ITEM-05] What a percent allowance is of, from its name: 위험예지·통상 → 통상임금, else 기본급. */
    fun basisOf(name: String): BonusBasis = if (Regex("예지|통상").containsMatchIn(name)) BonusBasis.ORDINARY else BonusBasis.BASE
}
