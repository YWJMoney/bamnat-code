package com.bamnat.core.team

import com.bamnat.core.schedule.DutyCheck
import com.bamnat.core.schedule.RestRules
import com.bamnat.core.team.TeamCheck.asType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * 1.38 [P3-X-01] 결원 메우기 (사용자 2026-11-06 "밤낮 없이는 안 될 정도로"): someone cannot come to [TeamCover.Gap]; who could
 * take it without breaking a rule, best first. The leader no longer works it out in their head on the phone: the rules the board
 * checks ([TeamCheck] with last month carried), 주 52시간, and what each asked for the day, then who has had the least.
 */
/**
 * 1.38 [P3-X-01] A yes/no ask the leader sent (결원 메우기): the server's [id] and key, the gap it is for, who was asked in the
 * server's order ([people], member ids), their answers so far (true: 들어갈 수 있어요) and who the leader chose ([taken]).
 */
data class TeamCall(
    val id: String,
    val token: String?,
    val gap: TeamCover.Gap,
    val people: List<String>,
    val urls: List<String> = emptyList(),
    val answers: Map<String, Boolean> = emptyMap(),
    val seen: Set<String> = emptySet(),
    val taken: String? = null,
    val createdAt: Long = 0,
    val expiresAt: Long = 0,
)

object TeamCover {
    data class Gap(val memberId: String, val date: LocalDate, val shiftId: Long)

    /**
     * One person who could come. [ok]: nothing new is broken. [why]: what stops or weighs on it ("쉬는 신청한 날", "나이트 뒤 바로");
     * [minutes]: their hours this month so far, the fairness order.
     */
    data class Candidate(val member: TeamMember, val ok: Boolean, val why: List<String>, val minutes: Int, val sameKind: Int)

    fun candidates(
        team: Team, board: TeamBoard, gap: Gap, rules: RestRules = RestRules(), wishes: List<TeamWish> = emptyList(), prev: TeamBoard? = null,
    ): List<Candidate> {
        val types = team.shifts.associateBy { it.id }
        val shift = types[gap.shiftId] ?: return emptyList()
        val wish = wishes.associateBy { it.memberId to it.date }
        // The board as it will be: the one who cannot come is off that day (or empty if the team has no off day).
        val off = team.shifts.firstOrNull { it.isOff }
        val without = if (off != null) board.paint(gap.memberId, gap.date, off.id) else board.paint(gap.memberId, gap.date, null)
        val before = TeamCheck.check(team, without, rules, prev).map { it.kind to it.text }.toSet()
        val week = gap.date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).let { m -> (0..6L).map(m::plusDays) }
        return board.members.filter { it.active && it.id != gap.memberId }.mapNotNull { m ->
            val now = without.cell(m.id, gap.date)?.shiftId?.let(types::get)
            if (now != null && !now.isOff) return@mapNotNull null // already working that day
            val trial = without.paint(m.id, gap.date, gap.shiftId)
            val why = ArrayList<String>()
            TeamCheck.check(team, trial, rules, prev).filter { it.memberId == m.id && (it.kind to it.text) !in before }
                .forEach { why += it.text.removePrefix("${m.name}: ") }
            val weekMinutes = week.sumOf { d -> trial.cell(m.id, d)?.shiftId?.let(types::get)?.let(TeamCheck::minutesOf) ?: 0 }
            if (weekMinutes > TeamCheck.WEEK_LIMIT_MINUTES && why.none { it.contains("52시간") }) why += "그 주 ${weekMinutes / 60}시간, 52시간을 넘어요"
            wish[m.id to gap.date]?.takeIf { it.kind.rests || (it.kind == WishKind.AVOID_NIGHT && DutyCheck.isNight(shift.asType())) }
                ?.let { why += "${it.kind.short} 신청한 날이에요" }
            val minutes = board.days.sumOf { d -> without.cell(m.id, d)?.shiftId?.let(types::get)?.let(TeamCheck::minutesOf) ?: 0 }
            val same = board.days.count { d -> without.cell(m.id, d)?.shiftId == gap.shiftId }
            Candidate(m, why.isEmpty(), why, minutes, same)
        }.sortedWith(compareBy({ !it.ok }, { it.why.size }, { it.minutes }, { it.sameKind }, { it.member.sort }))
    }

    /** The board once [taker] comes: they get the shift, the one who could not is off (or empty). */
    fun apply(team: Team, board: TeamBoard, gap: Gap, taker: String): TeamBoard {
        val off = team.shifts.firstOrNull { it.isOff }
        // A locked cell is the leader's own word; a 결원 the leader settles here is too, so the lock is lifted for it.
        return board.lock(gap.memberId, gap.date, false).lock(taker, gap.date, false)
            .paint(gap.memberId, gap.date, off?.id, CellSource.MANUAL).paint(taker, gap.date, gap.shiftId, CellSource.MANUAL)
    }
}
