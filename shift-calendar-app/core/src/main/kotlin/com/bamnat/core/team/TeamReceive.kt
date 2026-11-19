package com.bamnat.core.team

import java.time.LocalDate
import java.time.YearMonth

/**
 * 1.38 [P3-L-15] 받기: what a member's link says after 다시 확정 ("바뀜: D→E" on each changed day, the old shift first), and the
 * whole month's table for 병동 전체 보기.
 */
object TeamReceive {
    /** Per member, each day whose shift moved from [was] (the board as last confirmed) to [now]. */
    fun changes(team: Team, now: TeamBoard, was: Map<Pair<String, LocalDate>, Long>): Map<String, Map<LocalDate, String>> {
        if (was.isEmpty()) return emptyMap()
        val labels = team.shifts.associate { it.id to it.label }
        fun l(id: Long?) = id?.let(labels::get) ?: "없음"
        val out = HashMap<String, HashMap<LocalDate, String>>()
        for (m in now.members) for (d in now.days) {
            val before = was[m.id to d]
            val after = now.cell(m.id, d)?.shiftId
            if (before != after) out.getOrPut(m.id) { HashMap() }[d] = "바뀜: ${l(before)}→${l(after)}"
        }
        return out
    }

    /**
     * 2.0.0 [RVJ-98] 검토 U05-06(PAYU-15): a member whose day [today] or tomorrow moved in this 다시 확정, with those days
     * ("내일 D→E"). Their phones take up to 6 hours to follow and the same link is not sent again by itself, so a change this close is
     * told by the leader now ([soonMessage]).
     */
    data class Soon(val member: TeamMember, val days: List<String>)

    /** [RVJ-98] The [Soon] of each active member, in the board's order; none on a first 확정 ([was] empty). */
    fun soon(team: Team, now: TeamBoard, was: Map<Pair<String, LocalDate>, Long>, today: LocalDate): List<Soon> {
        if (was.isEmpty()) return emptyList()
        val labels = team.shifts.associate { it.id to it.label }
        fun l(id: Long?) = id?.let(labels::get) ?: "없음"
        val days = listOf(today to "오늘", today.plusDays(1) to "내일").filter { (d, _) -> YearMonth.from(d) == now.month }
        return now.members.filter { it.active }.mapNotNull { m ->
            val said = days.mapNotNull { (d, word) ->
                val before = was[m.id to d]
                val after = now.cell(m.id, d)?.shiftId
                if (before == after) null else "$word ${l(before)}→${l(after)}"
            }
            if (said.isEmpty()) null else Soon(m, said)
        }
    }

    /** [RVJ-98] One message for the leader to send (단톡방 or each): who, which day, and each one's own link when known. */
    fun soonMessage(team: Team, soon: List<Soon>, links: Map<String, String> = emptyMap()): String =
        "${team.name} 근무가 바뀌었어요.\n" +
            soon.joinToString("\n") { s -> "${s.member.name}: ${s.days.joinToString(", ")}" + (links[s.member.id]?.let { " $it" } ?: "") } +
            "\n자기 링크에서 바뀐 근무를 한 번 봐 주세요."

    /** Rows of labels in the members' order ("" for an empty day), for the ward's page. */
    fun rows(team: Team, b: TeamBoard, members: List<TeamMember>): List<List<String>> {
        val labels = team.shifts.associate { it.id to it.label.take(4) }
        return members.map { m -> b.days.map { d -> b.cell(m.id, d)?.shiftId?.let(labels::get).orEmpty() } }
    }

    fun colors(team: Team): Map<String, String> = team.shifts.associate { it.label.take(4) to "#%06X".format(it.colorArgb and 0xFFFFFF) }

    /** Two day lines on one day (같이 쉬어요 and 바뀜) become one memo, the change first. */
    fun merge(vararg memos: Map<LocalDate, String>): Map<LocalDate, String> =
        memos.flatMap { it.entries }.groupBy({ it.key }, { it.value }).mapValues { (_, v) -> v.sortedByDescending { it.startsWith("바뀜") }.joinToString(" · ").take(80) }
}
