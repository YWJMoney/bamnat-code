package com.bamnat.core.team

import java.time.LocalDate

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
