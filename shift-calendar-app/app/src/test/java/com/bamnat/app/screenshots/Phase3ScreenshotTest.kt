package com.bamnat.app.screenshots

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.core.team.Skill
import com.bamnat.core.team.Team
import com.bamnat.core.team.TeamBoard
import com.bamnat.core.team.TeamCell
import com.bamnat.core.team.TeamMember
import com.bamnat.core.team.TeamShift
import com.bamnat.core.work.Staff
import com.bamnat.core.work.StaffRoster
import com.bamnat.core.work.StaffShift
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.YearMonth
import com.bamnat.core.time.Today

/** 1.38 Phase 3: 리더 팀 근무표·사장님 급여 대장·요금제 창, for the design review (사용자 2026-11-05 "디자인은 좋은지"). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class Phase3ScreenshotTest : ScreenshotBase() {
    private fun seedTeam(): Team = runBlocking {
        val store = app.container.phaseThree
        val shifts = listOf(
            TeamShift(1, "D", "주간", 0xFF4F8BD6, false, LocalTime.of(7, 0), LocalTime.of(15, 0)),
            TeamShift(2, "E", "저녁", 0xFFE39B3B, false, LocalTime.of(15, 0), LocalTime.of(23, 0)),
            TeamShift(3, "N", "야간", 0xFF5E4AA8, false, LocalTime.of(23, 0), LocalTime.of(7, 0)),
            TeamShift(4, "O", "휴무", 0xFF9AA3AD, true),
        )
        val team = Team("t1", "7병동", "민지", shifts, 1L, need = mapOf(1L to 2, 2L to 2, 3L to 1))
        store.saveTeam(team)
        val names = listOf("민지", "준호", "서연", "지훈", "하은", "도윤", "수아", "예준")
        val members = names.mapIndexed { i, n -> TeamMember("m$i", "t1", n, skill = if (i < 2) Skill.CHARGE else if (i == 7) Skill.NEW else Skill.REG, sort = i) }
        store.saveMembers(members)
        val month = YearMonth.from(Today.date()).plusMonths(1)
        val rot = listOf(1L, 1L, 2L, 2L, 3L, 3L, 4L, 4L)
        val cells = members.flatMapIndexed { i, m -> (1..18).map { d -> val date = month.atDay(d); (m.id to date) to TeamCell(m.id, date, rot[(d + i * 2) % rot.size]) } }.toMap()
        // N then D the next day for 준호, so a red sign shows.
        val bad = cells + ((members[1].id to month.atDay(19)) to TeamCell(members[1].id, month.atDay(19), 3L)) + ((members[1].id to month.atDay(20)) to TeamCell(members[1].id, month.atDay(20), 1L))
        store.saveBoard("t1", TeamBoard(month, members, bad))
        team
    }

    @Test fun leaderTeamList() = shoot("p3_1_leader_teams", FOLDED) {
        seedTeam()
        content { com.bamnat.app.ui.team.LeaderTeamScreen("main") {} }
    }

    @Test fun leaderBoard() = shoot("p3_2_leader_board", FOLDED) {
        seedTeam()
        content { com.bamnat.app.ui.team.LeaderTeamScreen("main") {} }
        settle()
        tapText("열기", exact = true)
    }

    @Test fun leaderBoardWide() = shoot("p3_3_leader_board_wide", UNFOLDED) {
        seedTeam()
        content { com.bamnat.app.ui.team.LeaderTeamScreen("main") {} }
        settle()
        tapText("열기", exact = true)
    }

    @Test fun leaderBoardLower() = shoot("p3_4_leader_board_lower", FOLDED) {
        seedTeam()
        content { com.bamnat.app.ui.team.LeaderTeamScreen("main") {} }
        settle()
        tapText("열기", exact = true)
        scrollUp(700); scrollUp(700)
    }

    private val roster = StaffRoster(listOf(
        Staff("s1", "김알바", 10_320, shifts = mapOf(DayOfWeek.MONDAY to StaffShift(LocalTime.of(9, 0), LocalTime.of(18, 0)), DayOfWeek.WEDNESDAY to StaffShift(LocalTime.of(9, 0), LocalTime.of(18, 0)), DayOfWeek.FRIDAY to StaffShift(LocalTime.of(9, 0), LocalTime.of(18, 0)))),
        Staff("s2", "이주말", 11_000, shifts = mapOf(DayOfWeek.SATURDAY to StaffShift(LocalTime.of(10, 0), LocalTime.of(22, 0)), DayOfWeek.SUNDAY to StaffShift(LocalTime.of(10, 0), LocalTime.of(22, 0)))),
        Staff("s3", "박야간", 10_500, shifts = mapOf(DayOfWeek.TUESDAY to StaffShift(LocalTime.of(20, 0), LocalTime.of(2, 0)), DayOfWeek.THURSDAY to StaffShift(LocalTime.of(20, 0), LocalTime.of(2, 0)))),
    ))

    @Test fun payroll() = shoot("p3_5_payroll", FOLDED) {
        content { com.bamnat.app.ui.work.PayrollScreen(roster, YearMonth.from(Today.date())) {} }
    }

    @Test fun payrollLower() = shoot("p3_6_payroll_lower", FOLDED) {
        content { com.bamnat.app.ui.work.PayrollScreen(roster, YearMonth.from(Today.date())) {} }
        settle()
        scrollUp(700); scrollUp(700)
    }

    @Test fun teamPlanSheet() = shoot("p3_7_team_plan", FOLDED) {
        content {
            com.bamnat.app.ui.pro.WorkPlanSheet(com.bamnat.core.tier.WorkPlanKind.TEAM,
                com.bamnat.core.tier.WorkPlanRules.Need(com.bamnat.core.tier.WorkPlanRules.Verdict.PLAN, com.bamnat.core.tier.WorkPlanTable.DEFAULT_TEAM[1], 18), onDismiss = {})
        }
    }
}
