package com.bamnat.core

import com.bamnat.core.team.Skill
import com.bamnat.core.team.Team
import com.bamnat.core.team.TeamAuto
import com.bamnat.core.team.TeamBoard
import com.bamnat.core.team.TeamMember
import com.bamnat.core.team.TeamShift
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime
import java.time.YearMonth

/**
 * 2.0.0 갈래 J [RVJ-103] 검토 PAYU-12: 빈칸 자동 채우기 sees the 책임자 (a team that has named one is asked for one on each kind of work)
 * and shares the 공휴일 like a weekend.
 */
class TeamAutoChargeHolidayTest {
    private val day = TeamShift(1, "D", "데이", 0xFF2196F3, false, LocalTime.of(7, 0), LocalTime.of(15, 0))
    private val off = TeamShift(4, "O", "오프", 0xFF9E9E9E, true)
    private fun team() = Team("t", "3병동", shifts = listOf(day, off), need = mapOf(1L to 1))

    @Test fun aChargeIsPickedMoreOftenWhereNoneIsOnTheKind() {
        val nov = YearMonth.of(2026, 11)
        val members = listOf(TeamMember("c", "t", "책임", sort = 0, skill = Skill.CHARGE), TeamMember("a", "t", "가", sort = 1), TeamMember("b", "t", "나", sort = 2))
        val plan = TeamAuto.fill(team(), TeamBoard(nov, members))
        val withCharge = nov.let { m -> (1..m.lengthOfMonth()).count { plan.board.cell("c", m.atDay(it))?.shiftId == 1L } }
        // Shared evenly the 책임 would take a third of 30 days (10); leaning toward the 책임자 takes more, still no one is left without a share.
        assertTrue("책임 on $withCharge days", withCharge >= 12)
        assertTrue(members.drop(1).all { m -> (1..nov.lengthOfMonth()).count { plan.board.cell(m.id, nov.atDay(it))?.shiftId == 1L } >= 5 })
    }

    @Test fun noOneIsLeanedTowardInATeamWithoutACharge() {
        val nov = YearMonth.of(2026, 11)
        val members = listOf("a", "b", "c").mapIndexed { i, id -> TeamMember(id, "t", id, sort = i) }
        val plan = TeamAuto.fill(team(), TeamBoard(nov, members))
        val days = members.map { m -> (1..nov.lengthOfMonth()).count { plan.board.cell(m.id, nov.atDay(it))?.shiftId == 1L } }
        assertTrue("shared evenly: $days", days.max() - days.min() <= 1)
    }

    @Test fun weekdayHolidaysGoToDifferentPeople() {
        // 2026-05: 노동절 5/1 (금), 어린이날 5/5 (화), 대체공휴일 5/25 (월).
        val may = YearMonth.of(2026, 5)
        val members = listOf("a", "b", "c", "d").mapIndexed { i, id -> TeamMember(id, "t", id, sort = i) }
        val plan = TeamAuto.fill(team(), TeamBoard(may, members))
        val holidays = listOf(1, 5, 25).map { may.atDay(it) }
        val who = holidays.map { d -> members.first { m -> plan.board.cell(m.id, d)?.shiftId == 1L }.id }
        assertEquals("공휴일 셋은 서로 다른 사람이: $who", 3, who.distinct().size)
    }
}
