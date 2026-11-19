package com.bamnat.app.screenshots

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.core.team.Team
import com.bamnat.core.team.TeamBoard
import com.bamnat.core.team.TeamMember
import com.bamnat.core.team.TeamReceive
import com.bamnat.core.team.TeamShift
import com.bamnat.core.time.Today
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.YearMonth

/**
 * 2.0.0 갈래 J [RVJ-98] 검토 U05-06(PAYU-15): after 다시 확정 the 팀원 whose today or tomorrow moved are named first, with one
 * '지금 알리기', since their phones take up to 6 hours to follow.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RvjTeamSoonScreenshotTest : ScreenshotBase() {
    private val team = Team("t", "6병동", shifts = listOf(TeamShift(1, "D", "데이", 0xFF3B82F6, false), TeamShift(2, "E", "이브닝", 0xFFF59E0B, false),
        TeamShift(3, "N", "나이트", 0xFF6366F1, false), TeamShift(9, "O", "오프", 0xFF9E9E9E, true)))
    private val people = listOf(TeamMember("a", "t", "김하늘", sort = 0), TeamMember("b", "t", "이바다", sort = 1), TeamMember("c", "t", "박산", sort = 2))

    private fun soon(): List<TeamReceive.Soon> {
        val today = Today.date()
        val month = YearMonth.from(today)
        val was = TeamBoard(month, people).paint("a", today.plusDays(1), 1).paint("b", today, 2).paint("c", today.plusDays(5), 1)
        val now = was.paint("a", today.plusDays(1), 3).paint("b", today, 9).paint("c", today.plusDays(5), 2)
        return TeamReceive.soon(team, now, was.cells.mapValues { it.value.shiftId }, today)
    }

    private fun shown(): Screen.() -> Unit = {
        val s = soon()
        content { com.bamnat.app.ui.team.ReconfirmedDialog(4, s, onTellSoon = {}, onSendAll = {}, onClose = {}) }
        settle()
    }

    @Test fun reconfirmed() = shoot("rvj_354_team_soon_folded", FOLDED, shown())
    @Test fun reconfirmedDarkBig() = shoot("rvj_355_team_soon_folded_dark_200", DARK, 2f, shown())
    @Test fun reconfirmedWide() = shoot("rvj_356_team_soon_unfolded", UNFOLDED, shown())

    @Test fun whoChangedTodayOrTomorrowIsNamed() = shoot("rvj_357_team_soon_names_folded", FOLDED) {
        shown().invoke(this)
        assertTrue(appears("오늘·내일 근무가 바뀐 2명"))
        assertTrue(appears("김하늘 · 내일 D→N"))
        assertTrue(appears("이바다 · 오늘 E→O"))
        assertTrue("닷새 뒤는 기다려도 돼요", !shows("박산"))
        assertTrue(enabled("2명에게 지금 알리기"))
        assertTrue(scrolledTo("그래도 알리기"))
    }

    private companion object {
        const val DARK = "w369dp-h905dp-port-night-420dpi"
    }
}
