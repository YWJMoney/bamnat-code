package com.bamnat.core.team

import java.time.LocalDate

/**
 * 1.38 [P3-X-04] 공정성 장부 (여러 달 누적): each member's 나이트·주말·공휴일 근무와 신청 지킨 비율 over the last 3, 6 or 12 months, so
 * "왜 나만 나이트가 많아요" has a number for an answer, and 자동 채우기 evens it out across months, not only inside one.
 */
object TeamLedger {
    data class Month(val board: TeamBoard, val wishes: List<TeamWish> = emptyList(), val holidays: Set<LocalDate> = emptySet())

    data class Row(
        val member: TeamMember, val months: Int, val nights: Int, val weekends: Int, val holidays: Int, val minutes: Int, val asked: Int, val kept: Int,
    ) {
        /** Share of requests kept, 0..100; null when nothing was asked. */
        val keptPercent: Int? get() = if (asked == 0) null else (kept * 100 + asked / 2) / asked
    }

    /** [months] oldest first or not, each counted once; only members active now, each over the months they were on the board. */
    fun of(team: Team, members: List<TeamMember>, months: List<Month>): List<Row> {
        val types = team.shifts.associateBy { it.id }
        return members.filter { it.active }.map { m ->
            var on = 0; var n = 0; var w = 0; var h = 0; var min = 0; var asked = 0; var kept = 0
            for (x in months) {
                if (x.board.members.none { it.id == m.id }) continue
                if (x.board.days.none { x.board.cell(m.id, it) != null }) continue
                on++
                val f = TeamCheck.fairness(team, x.board.copy(members = x.board.members.filter { it.id == m.id }), x.holidays).firstOrNull()
                if (f != null) { n += f.nights; w += f.weekends; h += f.holidays; min += f.minutes }
                x.wishes.filter { it.memberId == m.id }.forEach { wish ->
                    asked++
                    if (TeamAuto.kept(team, wish, x.board.cell(m.id, wish.date)?.shiftId?.let(types::get))) kept++
                }
            }
            Row(m, on, n, w, h, min, asked, kept)
        }
    }

    /** How far each member is above the team's per-month average (나이트, 주말): what 자동 채우기 leans against. */
    data class Past(val nights: Double, val weekends: Double)

    fun past(rows: List<Row>): Map<String, Past> {
        val counted = rows.filter { it.months > 0 }
        if (counted.size < 2) return emptyMap()
        val avgN = counted.map { it.nights.toDouble() / it.months }.average()
        val avgW = counted.map { it.weekends.toDouble() / it.months }.average()
        // Per month, times the months behind, so someone 2 nights a month over for 6 months leans 12 nights' worth.
        return counted.associate { r -> r.member.id to Past((r.nights.toDouble() / r.months - avgN) * r.months, (r.weekends.toDouble() / r.months - avgW) * r.months) }
    }
}
