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
        content { com.bamnat.app.ui.team.LeaderTeamScreen("main", onBack = {}) }
    }

    @Test fun leaderBoard() = shoot("p3_2_leader_board", FOLDED) {
        seedTeam()
        content { com.bamnat.app.ui.team.LeaderTeamScreen("main", onBack = {}) }
        settle()
        tapText("열기", exact = true)
    }

    @Test fun leaderBoardWide() = shoot("p3_3_leader_board_wide", UNFOLDED) {
        seedTeam()
        content { com.bamnat.app.ui.team.LeaderTeamScreen("main", onBack = {}) }
        settle()
        tapText("열기", exact = true)
    }

    @Test fun leaderBoardLower() = shoot("p3_4_leader_board_lower", FOLDED) {
        seedTeam()
        content { com.bamnat.app.ui.team.LeaderTeamScreen("main", onBack = {}) }
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

    @Test fun manageHub() = shoot("p3_8_manage", FOLDED) {
        seedTeam()
        runBlocking {
            val store = app.container.phaseThree
            store.saveWorkplace(com.bamnat.core.boss.Workplace("p1", "우리 가게", payday = 10))
            store.saveStaff(com.bamnat.core.boss.StaffMember("s1", "김알바", base = 10_320L, weekly = mapOf(java.time.DayOfWeek.MONDAY to StaffShift(LocalTime.of(9, 0), LocalTime.of(18, 0)))), "p1")
        }
        content { com.bamnat.app.ui.work.ManageScreen("main", onBack = {}, showAll = true) }
    }

    /** 1.39 [FLOW-23] this month's board: 오늘 출근·빈자리 and the day's kinds by start time, on the 관리 화면 and the board. */
    private fun seedToday() = runBlocking {
        val team = seedTeam()
        val store = app.container.phaseThree
        val now = YearMonth.from(Today.date())
        val members = store.boardNow(team.id, now).members
        val rot = listOf(1L, 1L, 2L, 2L, 3L, 4L, 4L, 4L)
        val cells = members.flatMapIndexed { i, m -> (1..now.lengthOfMonth()).map { d -> val date = now.atDay(d); (m.id to date) to TeamCell(m.id, date, rot[(d + i) % rot.size]) } }.toMap()
        store.saveBoard(team.id, TeamBoard(now, members, cells))
    }

    @Test fun teamTodayManage() = shoot("p3_11_team_today_manage", FOLDED) {
        seedToday()
        content { com.bamnat.app.ui.work.ManageScreen("main", onBack = {}, showAll = true) }
    }

    @Test fun teamTodayBoard() = shoot("p3_12_team_today_board", FOLDED) {
        seedToday()
        content { com.bamnat.app.ui.team.LeaderTeamScreen("main", onBack = {}) }
        settle()
        tapText("열기", exact = true)
        settle()
        tapText("지난달", exact = true)
    }

    /** 1.39 G6 [SETUP-18] A team made while the 리더's first setup waits: its times are asked once when the board opens. */
    private fun seedNoTimes() = runBlocking {
        val t = seedTeam()
        app.container.phaseThree.saveTeam(t.copy(shifts = com.bamnat.core.team.TeamTimes.blank(t.shifts)))
    }

    @Test fun teamTimesAsked() = shoot("p3_13_team_times_ask", FOLDED) {
        seedNoTimes()
        content { com.bamnat.app.ui.team.LeaderTeamScreen("main", onBack = {}) }
        settle()
        tapText("열기", exact = true)
    }

    @Test fun teamTimesCard() = shoot("p3_14_team_times_card", FOLDED) {
        seedNoTimes()
        content { com.bamnat.app.ui.team.LeaderTeamScreen("main", onBack = {}) }
        settle()
        tapText("열기", exact = true)
        settle()
        tapText("나중에", exact = true)
    }

    @Test fun teamPlanSheet() = shoot("p3_7_team_plan", FOLDED) {
        content {
            com.bamnat.app.ui.pro.WorkPlanSheet(com.bamnat.core.tier.WorkPlanKind.TEAM,
                com.bamnat.core.tier.WorkPlanRules.Need(com.bamnat.core.tier.WorkPlanRules.Verdict.PLAN, com.bamnat.core.tier.WorkPlanTable.DEFAULT_TEAM[1], 18), onDismiss = {})
        }
    }

    // 1.38 가져오기: ① 이름 ② 코드 on one page, and the staff list with what to look at.
    @Test fun teamImport() = shoot("p3_9_team_import", FOLDED) {
        val team = seedTeam()
        val month = YearMonth.from(Today.date()).plusMonths(1)
        val codes = listOf("D", "D", "N1", "휴", "E", "OFF", "D")
        val rows = listOf("민지 책임", "준호", "박하은", "서연").mapIndexed { i, n ->
            com.bamnat.core.imports.GridRow(n.substringBefore(" "), n.substringAfter(" ", "").ifBlank { null },
                (1..28).associate { d -> month.atDay(d) to codes[(d + i) % codes.size] }, if (i == 0) setOf(month.atDay(3)) else emptySet())
        }
        val grid = com.bamnat.core.imports.RosterGrid(month, (1..28).map(month::atDay), rows, codes.distinct())
        val members = runBlocking { app.container.phaseThree.boardNow("t1", month).members }
        content { com.bamnat.app.ui.team.TeamImportDialog(grid, true, members, team.shifts, onDismiss = {}, onApply = {}) }
    }

    @Test fun staffImport() = shoot("p3_10_staff_import", FOLDED) {
        val rows = listOf(
            com.bamnat.core.imports.StaffRow("김알바", com.bamnat.core.boss.WageForm.HOUR, 10_500, mapOf(DayOfWeek.MONDAY to (LocalTime.of(9, 0) to LocalTime.of(15, 0)), DayOfWeek.WEDNESDAY to (LocalTime.of(9, 0) to LocalTime.of(15, 0))), "홀"),
            com.bamnat.core.imports.StaffRow("이주방", null, null),
            com.bamnat.core.imports.StaffRow("박매니저", com.bamnat.core.boss.WageForm.MONTH, 2_500_000, doubtful = setOf("base")),
            com.bamnat.core.imports.StaffRow("최주말", com.bamnat.core.boss.WageForm.HOUR, 11_000, mapOf(DayOfWeek.SATURDAY to (LocalTime.of(10, 0) to LocalTime.of(20, 0)))),
        )
        val lines = com.bamnat.core.boss.StaffImport.lines(rows, listOf(com.bamnat.core.boss.StaffMember("x", "김알바")), Today.date())
        content { com.bamnat.app.ui.work.StaffImportDialog(lines, false, 2, onDismiss = {}, onApply = { _, _ -> }) }
    }
}
