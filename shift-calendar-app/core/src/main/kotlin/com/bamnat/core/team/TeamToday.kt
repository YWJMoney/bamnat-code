package com.bamnat.core.team

import java.time.LocalDate
import java.time.LocalTime

/**
 * 1.39 [FLOW-23] 팀 오늘 판 머리: one number for the leader's day ("오늘 출근 12명 · 빈자리 1") and, under it, the day's kinds of work in
 * the order they start (07:00 주간 6명 → 19:00 야간 6명), a kind short of [Team.need] marked. Days off are not work; a kind without a
 * start time comes after the timed ones in the paint bar's order.
 */
object TeamToday {
    data class Step(val shift: TeamShift, val count: Int, val need: Int) {
        val short: Int get() = (need - count).coerceAtLeast(0)
    }

    data class Day(val date: LocalDate, val working: Int, val steps: List<Step>, val unpainted: Int) {
        /** 빈자리: people missing from [Team.need] across the day's kinds. */
        val short: Int get() = steps.sumOf { it.short }

        /** "오늘 출근 12명 · 빈자리 1"; "오늘 출근 12명" when nothing is short; a day not painted for anyone yet says so. */
        fun head(word: String = "오늘"): String = when {
            working == 0 && steps.none { it.need > 0 } && unpainted > 0 -> "$word 근무가 아직 칠해지지 않았어요"
            else -> "$word 출근 ${working}명" + (if (short > 0) " · 빈자리 $short" else "")
        }
    }

    /** [date] on [board]; null when the date is not in the board's month (the leader looks at another month). */
    fun of(team: Team, board: TeamBoard, date: LocalDate): Day? {
        if (java.time.YearMonth.from(date) != board.month) return null
        val count = board.count(date)
        val work = team.shifts.filter { !it.isOff }
        val order = work.withIndex().associate { (i, s) -> s.id to i }
        val steps = work.filter { (count[it.id] ?: 0) > 0 || (team.need[it.id] ?: 0) > 0 }
            .map { Step(it, count[it.id] ?: 0, team.need[it.id] ?: 0) }
            .sortedWith(compareBy<Step>({ it.shift.start == null }, { it.shift.start ?: LocalTime.MIDNIGHT }, { order[it.shift.id] ?: 0 }))
        val active = board.members.filter { it.active }
        return Day(date, steps.sumOf { it.count }, steps, active.count { board.cell(it.id, date) == null })
    }
}
