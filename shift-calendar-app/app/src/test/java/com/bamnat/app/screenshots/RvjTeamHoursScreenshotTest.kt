package com.bamnat.app.screenshots

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.ui.components.TimeRange
import com.bamnat.app.ui.team.pickedBreak
import com.bamnat.app.ui.team.teamHoursWords
import com.bamnat.core.boss.Workplace
import com.bamnat.core.team.Skill
import com.bamnat.core.team.Team
import com.bamnat.core.team.TeamBoard
import com.bamnat.core.team.TeamCell
import com.bamnat.core.team.TeamMember
import com.bamnat.core.team.TeamShift
import com.bamnat.core.team.TeamTimes
import com.bamnat.core.time.Today
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalTime
import java.time.YearMonth

/**
 * 2.0.0 갈래 J 네 번째 PR [RVJ-29]·[RVJ-30]·[RVJ-31] (검토 T05-04·PAYU-03·PC-18·PAYU-08·T05-01): 팀 표의 '근무 시간과 휴게' 줄, 고치는
 * 시트, 일하는 시간으로 센 주 52시간, 5인 미만 사업장. Light·dark × 글자 100%·200% × 접은·편 폭. The 'before' of the sheet is 1.39's
 * '근무 시간 정하기' window (`p3_13_team_times_ask`), which opened only while a time was missing.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RvjTeamHoursScreenshotTest : ScreenshotBase() {
    private val month: YearMonth get() = YearMonth.from(Today.date()).plusMonths(1)

    /**
     * A team made from the leader's own calendar (주간 09~18 휴게 1시간, 오후, 야간), whose 주간 was then set to 07~15 with no 휴게 of
     * its own: the PC-18 case the other way round (the calendar moved, the team stayed). [days] in a row for everyone, from the 1st.
     */
    private fun seedTeam(days: Int = 5, dayShift: (TeamShift) -> TeamShift = { it.copy(start = LocalTime.of(7, 0), end = LocalTime.of(15, 0), breakMinutes = null) }): Team = runBlocking {
        val c = app.container
        c.settingsRepository.update { it.copy(workStyle = WorkStyle.SHIFT, setupLater = false) }
        val mine = c.scheduleRepository.shiftTypes.first()
        myDay = mine.first { it.name == "주간" }
        val shifts = mine.map(TeamTimes::of).map { if (it.name == "주간") dayShift(it) else it }
        val team = Team("t1", "7병동", "민지", shifts, 1L)
        c.phaseThree.saveTeam(team)
        val members = listOf("민지", "준호", "서연").mapIndexed { i, n -> TeamMember("m$i", "t1", n, skill = if (i == 0) Skill.CHARGE else Skill.REG, sort = i) }
        c.phaseThree.saveMembers(members)
        val dayId = shifts.first { it.name == "주간" }.id
        // From the month's first Monday, so the days in a row are one week of the law.
        val monday = (1..7).map { month.atDay(it) }.first { it.dayOfWeek == java.time.DayOfWeek.MONDAY }
        val cells = members.flatMap { m -> (0 until days).map { d -> val date = monday.plusDays(d.toLong()); (m.id to date) to TeamCell(m.id, date, dayId) } }.toMap()
        c.phaseThree.saveBoard("t1", TeamBoard(month, members, cells))
        team
    }

    /** The leader's own 주간, as the test phone has it (the base sets its own times). */
    private lateinit var myDay: com.bamnat.core.model.ShiftType
    private fun myDayWords(): String = com.bamnat.app.ui.Fmt.timeRange(myDay.start!!, myDay.end!!)

    private fun board(): Screen.() -> Unit = {
        content { com.bamnat.app.ui.team.LeaderTeamScreen("main", onBack = {}) }
        settle()
        tapText("열기", exact = true)
        settle()
    }

    private fun team(): Team = runBlocking { app.container.phaseThree.teamNow("t1")!! }
    private fun dayOf(t: Team): TeamShift = t.shifts.first { it.name == "주간" }

    // ---- 팀 표의 '근무 시간과 휴게' 줄 (검토 PC-18 '팀 표가 어느 시간을 쓰는지 표시가 없어요') ----
    private fun line(): Screen.() -> Unit = {
        seedTeam()
        board().invoke(this)
        reveal("근무 시간과 휴게", exact = true)
        assertTrue("팀이 쓰는 시간이 보여요", shows("07:00–15:00"))
        assertTrue("내 달력과 다르다고 알려 줘요", shows("내 달력과 다른 시간이 있어요", exact = true))
    }

    @Test fun lineLight() = shoot("rvj_60_team_hours_line_folded", FOLDED, line())
    @Test fun lineDarkBig() = shoot("rvj_61_team_hours_line_folded_dark_200", DARK, 2f, line())
    @Test fun lineWide() = shoot("rvj_62_team_hours_line_unfolded", UNFOLDED, line())

    // ---- 근무 시간과 휴게 시트 (검토 T05-04 '휴게를 넣을 곳이 없어요') ----
    private fun sheet(): Screen.() -> Unit = {
        line().invoke(this)
        tapText("근무 시간과 휴게", exact = true, scrolls = 0)
        settle()
        // 07~15 with no 휴게 of its own: the law's 30분 off, 7시간 30분 worked.
        assertTrue("법으로 정한 휴게를 빼고 보여요", shows("휴게 30분 · 일하는 시간 7시간 30분"))
        assertTrue("내 달력 시간이 나란히: " + nodes(merged = false).mapNotNull { n -> n.config.getOrNull(SemanticsProperties.Text)?.joinToString { t -> t.text } }.filter { l -> "달력" in l },
            shows("내 달력은 ${myDayWords()} · 휴게 ${com.bamnat.core.hours.HoursFormat.breakText(myDay.breakMinutes)}", exact = true))
        assertTrue(shows("내 달력 시간으로", exact = true))
    }

    @Test fun sheetLight() = shoot("rvj_63_team_hours_sheet_folded", FOLDED, sheet())
    @Test fun sheetDarkBig() = shoot("rvj_64_team_hours_sheet_folded_dark_200", DARK, 2f, sheet())
    @Test fun sheetWide() = shoot("rvj_65_team_hours_sheet_unfolded", UNFOLDED, sheet())
    @Test fun sheetWideDarkBig() = shoot("rvj_66_team_hours_sheet_unfolded_dark_200", UNFOLDED_DARK, 2f, sheet())

    /** '내 달력 시간으로' 한 번, 저장 한 번: the team's 주간 is the calendar's with its 휴게, and the line stops saying 'differs'. */
    @Test fun takesMyCalendarsTimes() = shoot("rvj_67_team_hours_mine_folded", FOLDED) {
        sheet().invoke(this)
        tapText("내 달력 시간으로", exact = true, scrolls = 0)
        settle()
        val worked = TimeRange(myDay.start!!, myDay.end!!, myDay.breakMinutes).workMinutes
        assertTrue(shows(myDayWords(), exact = true))
        assertTrue(shows("휴게 ${com.bamnat.core.hours.HoursFormat.breakText(myDay.breakMinutes)} · 일하는 시간 ${com.bamnat.core.hours.HoursFormat.korean(worked)}", exact = true))
        assertTrue("같아졌으니 알림 줄은 사라져요", gone("내 달력 시간으로", exact = true))
        tapText("저장", exact = true, scrolls = 0)
        settle()
        val d = dayOf(team())
        assertEquals(Triple(myDay.start, myDay.end, myDay.breakMinutes.takeIf { it > 0 }), Triple(d.start, d.end, d.breakMinutes))
        assertTrue(gone("내 달력과 다른 시간이 있어요", exact = true))
    }

    /** A 휴게 of the team's own, picked in the window the calendar's kinds use: 90분 is kept, and the row counts 6시간 30분. */
    @Test fun picksItsOwnBreak() = shoot("rvj_68_team_hours_break_folded", FOLDED) {
        sheet().invoke(this)
        tapText("일하는 시간 7시간 30분", scrolls = 0)
        settle()
        tapText("휴게", exact = true, scrolls = 0)
        settle()
        tapText("1시간 30분", exact = true, scrolls = 0)
        settle()
        tapText("완료", exact = true, scrolls = 0)
        settle()
        assertTrue(shows("휴게 1시간 30분 · 일하는 시간 6시간 30분"))
        tapText("저장", exact = true, scrolls = 0)
        settle()
        assertEquals(90, dayOf(team()).breakMinutes)
        assertEquals("휴게만 고친 저장은 내 달력 시간을 건드리지 않아요", myDay.start,
            runBlocking { app.container.scheduleRepository.shiftTypes.first().first { it.name == "주간" }.start })
    }

    // ---- 주 52시간은 일하는 시간으로 (검토 T05-04: 09~18 엿새는 48시간인데 54시간으로 세어 빨강) ----
    private fun nineToSix(days: Int): Screen.() -> Unit = {
        seedTeam(days) { it.copy(start = LocalTime.of(9, 0), end = LocalTime.of(18, 0), breakMinutes = null) }
        board().invoke(this)
    }

    @Test fun sixDaysOfNineToSixAreNotOverTheLimit() = shoot("rvj_69_team_six_days_folded", FOLDED) {
        nineToSix(6).invoke(this)
        assertTrue("엿새 48시간은 52시간을 넘지 않아요", gone("52시간을 넘어요"))
    }

    private fun sevenDays(): Screen.() -> Unit = {
        nineToSix(7).invoke(this)
        assertTrue("이레 56시간은 휴게를 뺐다고 말하며 알려요", scrolledTo("휴게 빼고 56시간, 52시간을 넘어요"))
    }

    @Test fun sevenDaysAreToldWithTheBreakOff() = shoot("rvj_70_team_seven_days_folded", FOLDED, sevenDays())
    @Test fun sevenDaysDarkBig() = shoot("rvj_71_team_seven_days_folded_dark_200", DARK, 2f, sevenDays())

    // ---- 5인 미만 사업장 (검토 T05-01: 제50·53조가 적용되지 않는데 52시간으로 빨강) ----
    private fun underFive(): Screen.() -> Unit = {
        runBlocking { app.container.phaseThree.saveWorkplace(Workplace("w1", "가나다카페", fivePlus = false, payday = 10, teamId = "t1")) }
        nineToSix(7).invoke(this)
        assertTrue("5인 미만 사업장에는 주 52시간을 알리지 않아요", gone("52시간을 넘어요"))
        reveal("근무 시간과 휴게", exact = true)
        tapText("근무 시간과 휴게", exact = true, scrolls = 0)
        settle()
        assertTrue("왜 안 알리는지 시트에 한 줄", scrolledTo("5인 미만 사업장이라 주 52시간은 알리지 않아요"))
    }

    @Test fun underFiveIsNotToldTheWeekLimit() = shoot("rvj_72_team_under_five_folded", FOLDED, underFive())
    @Test fun underFiveDarkBig() = shoot("rvj_73_team_under_five_folded_dark_200", DARK, 2f, underFive())

    // ---- 시트가 돌려주는 휴게 (화면 없이) ----
    @Test fun theBreakKeptAfterTheWindow() {
        val auto = TeamTimes.Hours(LocalTime.of(9, 0), LocalTime.of(18, 0), null)
        fun range(a: Int, b: Int, brk: Int) = TimeRange(LocalTime.of(a, 0), LocalTime.of(b, 0), brk)
        assertNull("손대지 않으면 법으로 정한 휴게 그대로", pickedBreak(auto, 60, range(9, 18, 60)))
        assertNull("시간만 줄이면 휴게도 법대로 따라가요(09~13은 30분)", pickedBreak(auto, 60, range(9, 13, 60)))
        assertEquals("직접 고른 휴게는 지켜요", 90, pickedBreak(auto, 60, range(9, 18, 90)))
        assertEquals("휴게 없음도 고른 값이에요", 0, pickedBreak(auto, 60, range(9, 18, 0)))
        val own = TeamTimes.Hours(LocalTime.of(9, 0), LocalTime.of(18, 0), 90)
        assertEquals(90, pickedBreak(own, 90, range(9, 19, 90)))
        assertNull("법과 같은 값으로 돌리면 다시 법대로", pickedBreak(own, 90, range(9, 18, 60)))
        assertEquals("09:00 – 18:00 · 휴게 1시간 · 일하는 시간 8시간",
            teamHoursWords(TeamShift(1, "D", "주간", 0, false, LocalTime.of(9, 0), LocalTime.of(18, 0))))
        assertEquals("09:00 – 18:00 · 휴게 없음 · 일하는 시간 9시간",
            teamHoursWords(TeamShift(1, "D", "주간", 0, false, LocalTime.of(9, 0), LocalTime.of(18, 0), breakMinutes = 0)))
        assertNull(teamHoursWords(TeamShift(1, "D", "주간", 0, false)))
    }

    private companion object {
        const val DARK = "w369dp-h905dp-port-night-420dpi"
        const val UNFOLDED_DARK = "w707dp-h823dp-port-night-420dpi"
    }
}
