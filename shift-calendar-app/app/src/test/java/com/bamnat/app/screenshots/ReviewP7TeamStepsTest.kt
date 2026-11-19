package com.bamnat.app.screenshots

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.core.team.Skill
import com.bamnat.core.team.Team
import com.bamnat.core.team.TeamBoard
import com.bamnat.core.team.TeamCell
import com.bamnat.core.team.TeamMember
import com.bamnat.core.team.TeamMonth
import com.bamnat.core.team.TeamShift
import com.bamnat.core.time.Today
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.YearMonth

/**
 * 2.0.0 갈래 P 묶음 2 [RVP-46] (검토 UIX-03·T13-04·U13-16, 조건 2·8): 팀 근무표 이달 도구 위에 달마다 하는 순서(신청 → 채우기 → 확정 → 보내기)가
 * 띠로 보이고, 지금 단계가 굵게·앞 단계는 체크로 보여요. 단추는 늘 그대로(채운 단추 하나).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ReviewP7TeamStepsTest : ScreenshotBase() {
    private val month: YearMonth get() = YearMonth.from(Today.date()).plusMonths(1)

    /**
     * 7병동, eight people on a seven-day turn (D D E E N N O: every night pair is followed by one day off and a D, 나오데), 서연 on
     * 12-hour days through the month's first full week (over 52 hours), painted to the [painted]th, so the rest is short.
     */
    private fun seedTeam(painted: Int = 24, confirmed: Boolean = false, links: Boolean = false): Team = runBlocking {
        val store = app.container.phaseThree
        val shifts = listOf(
            TeamShift(1, "D", "데이", 0xFF4F8BD6, false, LocalTime.of(7, 0), LocalTime.of(15, 0)),
            TeamShift(2, "E", "이브닝", 0xFFE39B3B, false, LocalTime.of(15, 0), LocalTime.of(23, 0)),
            TeamShift(3, "N", "나이트", 0xFF5E4AA8, false, LocalTime.of(23, 0), LocalTime.of(7, 0)),
            TeamShift(4, "O", "오프", 0xFF9AA3AD, true),
            TeamShift(5, "L", "롱데이", 0xFF2E7D6B, false, LocalTime.of(7, 0), LocalTime.of(19, 0)),
        )
        val team = Team("t1", "7병동", "민지", shifts, 1L, need = mapOf(1L to 2, 2L to 2, 3L to 1))
        store.saveTeam(team)
        val names = listOf("민지", "준호", "서연", "지훈", "하은", "도윤", "수아", "예준")
        val members = names.mapIndexed { i, n -> TeamMember("m$i", "t1", n, skill = if (i < 2) Skill.CHARGE else if (i == 7) Skill.NEW else Skill.REG, sort = i,
            linkId = if (links) "l$i" else null, linkToken = if (links) "k$i" else null) }
        store.saveMembers(members)
        val turn = listOf(1L, 1L, 2L, 2L, 3L, 3L, 4L)
        val monday = (1..7).map { month.atDay(it) }.first { it.dayOfWeek == DayOfWeek.MONDAY }
        val long = (0 until 5).map { monday.plusDays(it.toLong()) }.toSet()
        val cells = members.flatMapIndexed { i, m ->
            (1..painted.coerceAtMost(month.lengthOfMonth())).map { d ->
                val date = month.atDay(d)
                val kind = if (i == 2 && date in long) 5L else turn[(d + i * 2) % turn.size]
                (m.id to date) to TeamCell(m.id, date, kind)
            }
        }.toMap()
        val board = TeamBoard(month, members, cells)
        store.saveBoard("t1", board)
        if (confirmed) store.confirm("t1", board, TeamMonth("t1", month))
        team
    }

    private fun open(): Screen.() -> Unit = {
        content { com.bamnat.app.ui.team.LeaderTeamScreen("main", onBack = {}) }
        settle()
        tapText("열기", exact = true)
        settle()
    }

    private fun steps(painted: Int, confirmed: Boolean, now: String, step: Int): Screen.() -> Unit = {
        seedTeam(painted, confirmed)
        open().invoke(this)
        // The band says one thing to a reader of the screen: which of the four steps the month is at.
        reveal("이달 순서 4단계 중 ${step}단계 $now", exact = true)
        assertTrue("순서 띠가 이달 도구 위에 지금 단계를 말해요: $now", shows("이달 순서 4단계 중 ${step}단계 $now", exact = true))
        assertTrue("그 아래에 이달 도구", shows("더 보기", exact = true))
    }

    @Test fun fillLight() = shoot("rvp7_team_steps_fill_folded_light", FOLDED, steps(24, false, "채우기", 2))
    @Test fun confirmDark200() = shoot("rvp7_team_steps_confirm_folded_dark_200", DARK, 2f, steps(31, false, "확정", 3))
    @Test fun sentLight200() = shoot("rvp7_team_steps_sent_folded_light_200", FOLDED, 2f, steps(31, true, "보내기", 4))
    @Test fun fillWide() = shoot("rvp7_team_steps_fill_unfolded_light", UNFOLDED, steps(24, false, "채우기", 2))

    /** The paint keys are swatches in their own colours, not four more filled buttons beside the stage's one. */
    private fun keypad(): Screen.() -> Unit = {
        seedTeam(24, false)
        open().invoke(this)
        reveal("✕ 지우기", exact = true)
        assertTrue("지우기·건너뛰기 키가 같은 줄에", shows("» 건너뛰기", exact = true))
    }

    @Test fun keypadLight() = shoot("rvp7_team_keypad_folded_light", FOLDED, keypad())
    @Test fun keypadDark200() = shoot("rvp7_team_keypad_folded_dark_200", DARK, 2f, keypad())

    companion object {
        const val DARK = "w369dp-h905dp-port-night-420dpi"
    }
}
