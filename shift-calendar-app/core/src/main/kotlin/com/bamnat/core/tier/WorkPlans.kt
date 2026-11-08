package com.bamnat.core.tier

import java.time.Duration
import java.time.Instant
import java.time.YearMonth

/**
 * 1.38 Phase 3 요금 (사용자 결정 2026-11-05 20:22, Phase 3 완성 계획 8장): 프로와 따로 팀 요금제(리더)와 직원 요금제(사장님). 둘 다 개인 프로
 * 기능을 포함하고, 인원은 구간 상품이에요(Play 구독 상품 하나에 구간마다 base plan 둘: 월·연). 연에만 1개월 무료 체험, 자동 결제 없는
 * 1년 이용권은 구간마다 일회성 상품. 팀원은 영구 무료이고, 끝나도 만든 표·대장은 보기·내보내기를 막지 않아요.
 */
enum class WorkPlanKind(val product: String, val title: String, val who: String) {
    TEAM("bamnat_team", "팀 요금제", "팀원"),
    STAFF("bamnat_staff", "직원 요금제", "직원"),
    ;

    /** The 1년 이용권 of [cap]: a one-time product, no automatic payment. */
    fun passProduct(cap: Int): String = "${product}_pass_$cap"

    companion object {
        fun ofProduct(id: String): WorkPlanKind? = entries.firstOrNull { id == it.product || id.startsWith("${it.product}_pass_") }
    }
}

/** One band: up to [cap] people, and the prices shown before Play says its own (Play Console is the real price). */
data class PlanBand(val cap: Int, val monthly: Long, val yearly: Long, val pass: Long) {
    val monthlyPlan: String get() = "c$cap-monthly"
    val yearlyPlan: String get() = "c$cap-yearly"

    /** What the yearly saves against twelve months, in whole percent. */
    val yearlySaves: Int get() = if (monthly <= 0) 0 else (100 - yearly * 100 / (monthly * 12)).toInt()
}

data class WorkPlanTable(
    val team: List<PlanBand> = DEFAULT_TEAM,
    val staff: List<PlanBand> = DEFAULT_STAFF,
    /** 직원 맛보기: this many staff on the 급여 대장 are free. */
    val freeStaff: Int = 3,
    /** 팀 맛보기: a team's first [freeTeamMonths] confirmed months are free (첫 달 확정까지). */
    val freeTeamMonths: Int = 1,
) {
    fun bands(kind: WorkPlanKind): List<PlanBand> = (if (kind == WorkPlanKind.TEAM) team else staff).sortedBy { it.cap }

    /** The smallest band that holds [count] people, or null above the largest (문의로 받아요). */
    fun bandFor(kind: WorkPlanKind, count: Int): PlanBand? = bands(kind).firstOrNull { it.cap >= count }

    fun max(kind: WorkPlanKind): Int = bands(kind).lastOrNull()?.cap ?: 0

    /** The band a Play base plan or pass product names, when it is one of ours. */
    fun capOf(kind: WorkPlanKind, basePlanOrProduct: String?): Int? {
        val s = basePlanOrProduct ?: return null
        val n = Regex("""^c(\d+)-(monthly|yearly)$""").find(s)?.groupValues?.get(1)
            ?: s.removePrefix("${kind.product}_pass_").takeIf { it != s }
        return n?.toIntOrNull()?.takeIf { it > 0 }
    }

    companion object {
        /** 계획 8장 표: 월, 연(월×12의 약 50%), 1년 이용권(연보다 약 15% 높게). */
        val DEFAULT_TEAM = listOf(
            PlanBand(15, 7_900, 47_000, 54_000),
            PlanBand(30, 9_900, 59_000, 68_000),
            PlanBand(50, 14_900, 89_000, 102_000),
            PlanBand(100, 19_900, 119_000, 137_000),
        )
        val DEFAULT_STAFF = listOf(
            PlanBand(5, 9_900, 59_000, 68_000),
            PlanBand(10, 14_900, 89_000, 102_000),
            PlanBand(20, 24_900, 149_000, 171_000),
            PlanBand(50, 49_900, 299_000, 344_000),
        )
        val DEFAULT = WorkPlanTable()
    }
}

/** What a phone holds of one kind: the largest band among its live subscriptions and 1년 이용권. */
data class WorkHold(val cap: Int, val until: Instant?, val trial: Boolean = false, val pass: Boolean = false)

/**
 * The one answer to "may this leader confirm" and "may this boss add one more": the free taste first, then the band the phone holds.
 * Before the 정식 출시 (billing off) everything is open, as every other 프로 feature.
 */
object WorkPlanRules {
    enum class Verdict { OPEN, FREE, PLAN, BIGGER, CONTACT }

    /** Why, with the band to offer ([band] null for [Verdict.CONTACT], or when nothing is asked). */
    data class Need(val verdict: Verdict, val band: PlanBand? = null, val count: Int = 0) {
        val allowed: Boolean get() = verdict == Verdict.OPEN || verdict == Verdict.FREE
    }

    /**
     * Confirming [month] of a team of [members] active people. [confirmed] are the months this team was confirmed already; the
     * first [WorkPlanTable.freeTeamMonths] of them (and re-confirming one of those) are the free taste.
     */
    fun teamConfirm(table: WorkPlanTable, live: Boolean, hold: WorkHold?, confirmed: Set<YearMonth>, month: YearMonth, members: Int): Need {
        if (!live) return Need(Verdict.OPEN, count = members)
        val free = confirmed.sorted().take(table.freeTeamMonths)
        if (month in free || (free.size < table.freeTeamMonths && month !in confirmed)) return Need(Verdict.FREE, count = members)
        return byHold(table, WorkPlanKind.TEAM, hold, members)
    }

    /** A second team or more: any team band holds as many teams as the leader keeps, each up to its cap. */
    fun moreTeams(table: WorkPlanTable, live: Boolean, hold: WorkHold?, teams: Int): Need {
        if (!live || teams < 1) return Need(Verdict.OPEN)
        return if (hold != null) Need(Verdict.OPEN) else Need(Verdict.PLAN, table.bands(WorkPlanKind.TEAM).firstOrNull())
    }

    /** The 급여 대장 with [count] staff (after adding one). */
    fun staff(table: WorkPlanTable, live: Boolean, hold: WorkHold?, count: Int): Need {
        if (!live) return Need(Verdict.OPEN, count = count)
        if (count <= table.freeStaff) return Need(Verdict.FREE, count = count)
        return byHold(table, WorkPlanKind.STAFF, hold, count)
    }

    private fun byHold(table: WorkPlanTable, kind: WorkPlanKind, hold: WorkHold?, count: Int): Need {
        if (hold != null && hold.cap >= count) return Need(Verdict.OPEN, count = count)
        val band = table.bandFor(kind, count) ?: return Need(Verdict.CONTACT, count = count)
        return Need(if (hold == null) Verdict.PLAN else Verdict.BIGGER, band, count)
    }

    /** The words on the ask, in the user's terms. */
    fun words(kind: WorkPlanKind, need: Need, table: WorkPlanTable): String = when (need.verdict) {
        Verdict.OPEN, Verdict.FREE -> ""
        Verdict.PLAN -> if (kind == WorkPlanKind.TEAM) "첫 달 확정까지는 무료였어요. 이어서 확정하려면 ${kind.title}${com.bamnat.core.text.Josa.iga(kind.title)} 필요해요(${need.count}명이면 ${need.band?.cap}명 구간)."
            else "직원 ${table.freeStaff}명까지는 무료예요. ${need.count}명째부터 ${kind.title}${com.bamnat.core.text.Josa.iga(kind.title)} 필요해요(${need.band?.cap}명 구간)."
        Verdict.BIGGER -> "지금 구간보다 ${kind.who}이 많아요(${need.count}명). ${need.band?.cap}명 구간으로 바꾸면 남은 기간은 Play가 비례 계산해요."
        Verdict.CONTACT -> "${table.max(kind)}명이 넘는 곳은 문의로 따로 맞춰 드려요. 설정 › 문의하기에 인원을 적어 주세요."
    }

    /** One year from a 1년 이용권 bought at [at]; stacked like [YearPass]. */
    fun passUntil(purchases: Collection<Instant>): Instant? = YearPass.until(purchases)

    val PASS_DAYS: Duration = Duration.ofDays(YearPass.DAYS)
}
