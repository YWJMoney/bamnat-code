package com.bamnat.core.team

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/**
 * 1.38 [P3-L-12] 신청 받기 (사용자 2026-11-06, 병동 종이 신청표 "12월중 근무, 평일 2명, 주말 3명 신청"): the rules a round of
 * requests runs by. A day takes at most [weekdayCap] or [weekendCap] people asking it off (0: no limit), a person at most
 * [perPerson] such days (0: no limit), until [closesAt]. [firstCome]: a full day takes no more (the paper's way); otherwise
 * everyone may ask and the leader settles the days over the line, those who got fewer last month first ([TeamAsk.order]).
 * The share server checks the same rules with the same words (server/share-worker `askCheck`).
 */
data class AskRules(
    val weekdayCap: Int = 2,
    val weekendCap: Int = 3,
    val perPerson: Int = 0,
    /** Epoch millis; null: open until the leader closes it. */
    val closesAt: Long? = null,
    val kinds: Set<WishKind> = setOf(WishKind.OFF, WishKind.LEAVE, WishKind.SHIFT),
    val firstCome: Boolean = true,
    /** 공휴일, counted as 주말 for the cap. */
    val holidays: Set<LocalDate> = emptySet(),
    /** Days not open for asking (the paper's grey columns). */
    val blocked: Set<LocalDate> = emptySet(),
    /** Members see who asked a day, not only how many. Off by default: names stay with the leader. */
    val showNames: Boolean = false,
)

object TeamAsk {
    enum class No(val text: String) {
        CLOSED("신청이 마감됐어요"),
        KIND("이 신청은 받지 않아요"),
        BLOCKED("이 날은 신청을 받지 않아요"),
        MONTH("이 달 날짜가 아니에요"),
        DAY_FULL("이 날은 자리가 다 찼어요"),
        MINE_FULL("신청할 수 있는 날을 다 썼어요"),
    }

    fun weekend(r: AskRules, d: LocalDate): Boolean = d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY || d in r.holidays

    /** How many may ask [d] off; 0 is no limit. */
    fun cap(r: AskRules, d: LocalDate): Int = if (weekend(r, d)) r.weekendCap else r.weekdayCap

    /** People asking [d] off in [all]. */
    fun taken(all: List<TeamWish>, d: LocalDate): Int = all.count { it.date == d && it.kind.rests }

    /**
     * Why [w] cannot be taken now, or null when it can. [all] is every request of the round; [w]'s member's own request for the
     * same day is replaced, so changing 오프 to 연차 never counts twice.
     */
    fun check(r: AskRules, month: YearMonth, all: List<TeamWish>, w: TeamWish, now: Long): No? {
        if (r.closesAt != null && now >= r.closesAt) return No.CLOSED
        if (YearMonth.from(w.date) != month) return No.MONTH
        if (w.kind !in r.kinds) return No.KIND
        if (w.date in r.blocked) return No.BLOCKED
        if (!w.kind.rests) return null
        val others = all.filterNot { it.memberId == w.memberId && it.date == w.date }
        val cap = cap(r, w.date)
        if (r.firstCome && cap > 0 && taken(others, w.date) >= cap) return No.DAY_FULL
        if (r.perPerson > 0 && others.count { it.memberId == w.memberId && it.kind.rests } >= r.perPerson) return No.MINE_FULL
        return null
    }

    /** One day of the round for the leader: who is inside the cap and who is over it. */
    data class Day(val date: LocalDate, val cap: Int, val inside: List<TeamWish>, val over: List<TeamWish>)

    /**
     * The days asked off, each in order: [firstCome] by the time asked; otherwise those with more [misses] (wishes not kept last
     * month) first, then by time. A cap of 0 takes everyone.
     */
    fun order(r: AskRules, all: List<TeamWish>, misses: Map<String, Int> = emptyMap()): List<Day> =
        all.filter { it.kind.rests }.groupBy { it.date }.toSortedMap().map { (d, l) ->
            val sorted = if (r.firstCome) l.sortedBy { it.at ?: Long.MAX_VALUE }
                else l.sortedWith(compareBy<TeamWish>({ -(misses[it.memberId] ?: 0) }, { it.at ?: Long.MAX_VALUE }))
            val cap = cap(r, d)
            if (cap <= 0) Day(d, cap, sorted, emptyList()) else Day(d, cap, sorted.take(cap), sorted.drop(cap))
        }

    /** A member's count line: "오프 3 · 연차 1" of the round. */
    fun mine(all: List<TeamWish>, memberId: String): String =
        all.filter { it.memberId == memberId }.groupingBy { it.kind }.eachCount().entries.sortedBy { it.key.ordinal }
            .joinToString(" · ") { (k, n) -> "${k.short} $n" }
}
