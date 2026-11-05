package com.bamnat.core.team

import com.bamnat.core.model.ShiftType
import com.bamnat.core.schedule.DutyCheck
import com.bamnat.core.schedule.RestRules
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * 1.38 [P3-04][TEAM-16] L2 팀 규칙 바로 검사 (순번 490, 설계 2-1 [132]): while the leader paints, each member's row read by the same
 * [DutyCheck] the user's own 한 달 넣기 uses (역순 N→D·E→D, 11시간, 나이트 뒤 쉬는 날, 연속 근무, 월 야간 상한), plus what only a team
 * has: 근무별 최소 인원 ([Team.need]), 책임자 없는 근무, and 주 52시간 (근로기준법 제53조). 알림 only; the board saves as painted.
 */
object TeamCheck {
    enum class Kind { RULE, SHORT, NO_CHARGE, WEEK_HOURS }

    /** One sign: a member's cell ([memberId] set) or a whole day ([memberId] null). */
    data class Sign(val kind: Kind, val date: LocalDate, val memberId: String?, val text: String)

    /** 주 52시간: 40 + 12 (근로기준법 제50·53조). */
    const val WEEK_LIMIT_MINUTES = 52 * 60

    fun TeamShift.asType(): ShiftType = ShiftType(id = id, name = name, label = label, colorArgb = colorArgb, start = start, end = end, isOff = isOff)

    /** Minutes a kind of day takes, start to end (over midnight too); 0 for an off day or one without times. */
    fun minutesOf(s: TeamShift): Int {
        if (s.isOff || s.start == null || s.end == null) return 0
        val m = (s.end.toSecondOfDay() - s.start.toSecondOfDay()) / 60
        return if (m <= 0) m + 24 * 60 else m
    }

    fun check(team: Team, board: TeamBoard, rules: RestRules = RestRules()): List<Sign> {
        val types = team.shifts.associateBy { it.id }
        val asTypes = types.mapValues { it.value.asType() }
        val out = ArrayList<Sign>()
        val active = board.members.filter { it.active }
        for (m in active) {
            val days = board.days.map { d -> d to board.cell(m.id, d)?.shiftId?.let(asTypes::get) }
            DutyCheck.check(days, rules).forEach { w -> out += Sign(Kind.RULE, w.date, m.id, "${m.name}: ${w.text}") }
            // 주 52시간, Monday weeks that start in the month.
            board.days.map { it.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }.distinct().forEach { mon ->
                val week = (0..6).map { mon.plusDays(it.toLong()) }
                val minutes = week.sumOf { d -> board.cell(m.id, d)?.shiftId?.let(types::get)?.let(::minutesOf) ?: 0 }
                if (minutes > WEEK_LIMIT_MINUTES) out += Sign(Kind.WEEK_HOURS, week.last().coerceAtMost(board.days.last()), m.id,
                    "${m.name}: ${mon.monthValue}/${mon.dayOfMonth} 주 ${minutes / 60}시간, 52시간을 넘어요(근로기준법 제53조)")
            }
        }
        board.short(team.need).forEach { (d, missing) ->
            out += Sign(Kind.SHORT, d, null, missing.entries.joinToString(" ") { (k, n) -> "${types[k]?.label ?: "?"} ${n}명 부족" })
        }
        // 책임자 없는 근무: only once the team has said who is 책임 (a team without any is not asked).
        if (active.any { it.skill == Skill.CHARGE }) for (d in board.days) {
            val working = active.mapNotNull { m -> board.cell(m.id, d)?.shiftId?.let(types::get)?.takeIf { !it.isOff }?.let { it to m } }
            working.groupBy({ it.first }, { it.second }).forEach { (shift, people) ->
                if (people.none { it.skill == Skill.CHARGE }) out += Sign(Kind.NO_CHARGE, d, null, "${shift.label} 책임자 없음")
            }
        }
        return out.sortedWith(compareBy({ it.date }, { it.kind }))
    }

    /** [TEAM-17] 공정성 표 (설계 L2 C): each member's 야간·주말·공휴일 근무와 쉬는 날 in the month. */
    data class Fair(val member: TeamMember, val nights: Int, val weekends: Int, val holidays: Int, val offs: Int, val minutes: Int)

    fun fairness(team: Team, board: TeamBoard, holidays: Set<LocalDate> = emptySet()): List<Fair> {
        val types = team.shifts.associateBy { it.id }
        return board.members.filter { it.active }.map { m ->
            val row = board.days.mapNotNull { d -> board.cell(m.id, d)?.shiftId?.let(types::get)?.let { d to it } }
            val work = row.filter { !it.second.isOff }
            Fair(m,
                nights = work.count { DutyCheck.isNight(it.second.asType()) },
                weekends = work.count { it.first.dayOfWeek == DayOfWeek.SATURDAY || it.first.dayOfWeek == DayOfWeek.SUNDAY },
                holidays = work.count { it.first in holidays },
                offs = row.count { it.second.isOff },
                minutes = work.sumOf { minutesOf(it.second) })
        }
    }

    /**
     * [TEAM-18] 교환 요청 검사 (순번 493, 출시 후 할 일 C2·C7): what trading [date] between [a] and [b] would break, as new signs only
     * (signs already there before the trade are left out), so the leader sees "이 맞바꾸기는 준호 N→D 11시간 미만" before saying yes.
     */
    fun swapCheck(team: Team, board: TeamBoard, a: String, b: String, date: LocalDate, rules: RestRules = RestRules()): List<Sign> {
        val before = check(team, board, rules).map { it.kind to it.text }.toSet()
        return check(team, board.swap(a, b, date), rules).filter { (it.kind to it.text) !in before }
    }
}
