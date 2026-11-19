package com.bamnat.core.team

/**
 * 2.0.0 [RVJ-90] (총괄 HQ-133, 조건 6·4) 빈칸 자동 채우기 창의 안 견주기: what every 안 shares is said once above them ('어느 안으로
 * 채워도 주 52시간 넘김 1건이 남아요', one red mark for the window, not one per 안), and each 안 says only where it differs from the
 * others, its best values marked. One 안 alone says everything, as before.
 */
object PlanSides {
    /** A value the 안 are told apart by; the lower [key] the better. [FILL] and [SHORT] go in the 안's title line, the rest under it. */
    enum class Facet(val key: (TeamAuto.Plan) -> Int, val words: (TeamAuto.Plan) -> String) {
        FILL({ -it.filled }, { "${it.filled}칸" }),
        SHORT({ it.shortPeople * 1000 + it.short.size }, { if (it.shortPeople > 0) "부족 ${it.shortPeople}명·${it.short.size}일" else "부족 없음" }),
        RUN({ it.maxRun }, { "최대 연속 ${it.maxRun}일" }),
        HOURS({ it.hourSpread }, { "시간 차이 ${it.hourSpread}시간" }),
        WISHES({ -it.wishesKept }, { "희망 ${it.wishesKept}/${it.wishesKept + it.wishesMissed}" }),
        NIGHTS({ it.spread }, { "야간 차이 ${it.spread}번" }),
    }

    /** The values worth a word: 희망 only when someone asked for days. */
    fun facets(plans: List<TeamAuto.Plan>): List<Facet> =
        Facet.entries.filter { it != Facet.WISHES || plans.any { p -> p.wishesKept + p.wishesMissed > 0 } }

    /** Every 안 has the same [f] (never with one 안: it is then said in the 안 itself). */
    fun same(plans: List<TeamAuto.Plan>, f: Facet): Boolean = plans.size > 1 && plans.all { f.key(it) == f.key(plans[0]) }

    /** [p]'s [f] is the best of the 안 (ties all best). */
    fun best(plans: List<TeamAuto.Plan>, f: Facet, p: TeamAuto.Plan): Boolean = f.key(p) == plans.minOf(f.key)

    private fun lawSame(plans: List<TeamAuto.Plan>) = plans.size > 1 && plans.all { it.overWeeks == plans[0].overWeeks }
    private fun ruleSame(plans: List<TeamAuto.Plan>) = plans.size > 1 && plans.all { it.ruleSigns == plans[0].ruleSigns }

    /** A line's words and how heavy it is: [law] a 주 52시간 넘김 (위험), [rule] only a team 기준 (주의), neither all kept. */
    data class Say(val words: String, val law: Boolean, val rule: Boolean)

    /** What every 안 leaves over, said once above them; null with one 안, or when the 안 differ in both 주 52시간 and 기준. */
    fun shared(plans: List<TeamAuto.Plan>): Say? {
        if (plans.size < 2) return null
        val law = lawSame(plans)
        val rule = ruleSame(plans)
        val first = plans[0]
        val parts = listOfNotNull(first.overWeeks.takeIf { law && it > 0 }?.let { "주 52시간 넘김 ${it}건" },
            first.ruleSigns.takeIf { rule && it > 0 }?.let { "기준 넘김 ${it}건" })
        return when {
            parts.isNotEmpty() -> Say("어느 안으로 채워도 ${parts.joinToString(" · ")}이 남아요", law && first.overWeeks > 0, rule && first.ruleSigns > 0)
            law && rule -> Say("어느 안으로 채워도 넘는 것 없어요", law = false, rule = false)
            else -> null
        }
    }

    /** What [p] leaves over that the other 안 do not share; null when [shared] already said it all. */
    fun own(plans: List<TeamAuto.Plan>, p: TeamAuto.Plan): Say? {
        if (plans.size < 2) {
            val parts = listOfNotNull(p.overWeeks.takeIf { it > 0 }?.let { "주 52시간 넘김 ${it}건" }, p.ruleSigns.takeIf { it > 0 }?.let { "기준 넘김 ${it}건" })
            return Say(parts.joinToString(" · ").ifEmpty { "넘는 것 없음" }, p.overWeeks > 0, p.ruleSigns > 0)
        }
        val law = !lawSame(plans)
        val rule = !ruleSame(plans)
        if (!law && !rule) return null
        val parts = listOfNotNull(p.overWeeks.takeIf { law && it > 0 }?.let { "주 52시간 넘김 ${it}건" },
            p.ruleSigns.takeIf { rule && it > 0 }?.let { "기준 넘김 ${it}건" })
        val none = when {
            law && rule -> "넘는 것 없음"
            law -> "주 52시간 넘김 없음"
            else -> "기준 넘김 없음"
        }
        return Say(parts.joinToString(" · ").ifEmpty { none }, law && p.overWeeks > 0, rule && p.ruleSigns > 0)
    }
}
