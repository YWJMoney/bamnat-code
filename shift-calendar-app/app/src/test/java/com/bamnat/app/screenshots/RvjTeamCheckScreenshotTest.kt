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
 * 2.0.0 갈래 J [RVJ-68]·[RVJ-69] (총괄 HQ-107·HQ-108, 디자인 품질 조건 2·6·8, Q1 사진 `계획/2.0.0-화면사진/Q1/3b/뒤-01`): 팀 근무표의
 * '확인할 것' (28줄이 모두 빨간 ⚠였던 카드: 같은 것은 한 줄로 묶고, 주 52시간만 빨강 표시, 규칙·부족은 주황 표시, 숫자만 굵게)과 이달 도구
 * (채운 단추 하나와 테두리 단추 아홉: 단계마다 채운 단추 하나, 글자 단추 둘, 나머지 '더 보기'), [RVJ-70] 빈칸 자동 채우기의 안마다 '이 안으로
 * 채우기'(PC-178), [RVJ-73] 안 짠 달은 팀·가게 관리에서 조용히(PC-176). Light·dark × 글자 100%·200% × 접은·편 폭.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RvjTeamCheckScreenshotTest : ScreenshotBase() {
    private val month: YearMonth get() = YearMonth.from(Today.date()).plusMonths(1)

    /**
     * 7병동, eight people on a seven-day turn (D D E E N N O: every night pair is followed by one day off and a D, 나오데), 서연 on
     * 12-hour days through the month's first full week (over 52 hours), painted to the [painted]th, so the rest is short.
     */
    private fun seed(painted: Int = 24, confirmed: Boolean = false): Team = runBlocking {
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
        val members = names.mapIndexed { i, n -> TeamMember("m$i", "t1", n, skill = if (i < 2) Skill.CHARGE else if (i == 7) Skill.NEW else Skill.REG, sort = i) }
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

    // ---- 확인할 것 (HQ-107) ----
    private fun check(): Screen.() -> Unit = {
        seed()
        open().invoke(this)
        reveal("확인할 것")
        assertTrue("같은 것은 한 줄: 나오데 여러 건", shows("N-O-D(나오데) · 야간 뒤 2일째 근무"))
        assertTrue("주 52시간은 한 줄로 맨 위", shows("서연 · 주 52시간 넘김"))
        assertFalse("⚠ 글머리는 없어요", shows("⚠ "))
    }

    @Test fun checkLight() = shoot("rvj_200_team_check_folded", FOLDED, check())
    @Test fun checkDarkBig() = shoot("rvj_201_team_check_folded_dark_200", DARK, 2f, check())
    @Test fun checkWide() = shoot("rvj_202_team_check_unfolded", UNFOLDED, check())

    /** A grouped line opens to its days; a day goes to that cell (the keypad's name and day follow it). */
    private fun checkOpen(): Screen.() -> Unit = {
        check().invoke(this)
        tapText("N-O-D(나오데) · 야간 뒤 2일째 근무", scrolls = 0)
        settle()
        assertTrue("펼치면 날짜와 사람", appears("야간 뒤 데이"))
    }

    @Test fun checkOpenLight() = shoot("rvj_203_team_check_open_folded", FOLDED, checkOpen())
    @Test fun checkOpenDarkBig() = shoot("rvj_204_team_check_open_folded_dark_200", DARK, 2f, checkOpen())

    // ---- 이달 도구 (HQ-108) ----
    private fun tools(painted: Int = 24, confirmed: Boolean = false): Screen.() -> Unit = {
        seed(painted, confirmed)
        open().invoke(this)
        reveal("알림이에요. 그대로 확정해도 돼요")
    }

    /** Cells still empty: 빈칸 자동 채우기 is the one filled; 지난달 이어 칠하기 and 확정 beside it; the rest in '더 보기'. */
    private fun fill(): Screen.() -> Unit = {
        tools().invoke(this)
        assertTrue(shows("빈칸 자동 채우기", exact = true))
        assertTrue(shows("지난달 이어 칠하기", exact = true))
        assertTrue(shows("더 보기", exact = true))
        assertFalse("나머지는 '더 보기' 안에", shows("공정성 표", exact = true))
    }

    @Test fun fillLight() = shoot("rvj_205_team_tools_fill_folded", FOLDED, fill())
    @Test fun fillDarkBig() = shoot("rvj_206_team_tools_fill_folded_dark_200", DARK, 2f, fill())
    @Test fun fillWide() = shoot("rvj_207_team_tools_fill_unfolded", UNFOLDED, fill())

    private fun more(): Screen.() -> Unit = {
        fill().invoke(this)
        tapText("더 보기", exact = true, scrolls = 0)
        settle()
        assertTrue(appears("이달 근무표 도구", exact = true))
        assertTrue(shows("받기", exact = true) && shows("채우기", exact = true) && shows("보기", exact = true))
        assertTrue(shows("공정성 표", exact = true) && shows("근무별 인원", exact = true) && shows("파일·사진에서 가져오기", exact = true))
    }

    @Test fun moreLight() = shoot("rvj_208_team_tools_more_folded", FOLDED, more())
    @Test fun moreDarkBig() = shoot("rvj_209_team_tools_more_folded_dark_200", DARK, 2f, more())

    /** All painted, not confirmed: 확정 is the one filled. */
    @Test fun confirmStage() = shoot("rvj_210_team_tools_confirm_folded", FOLDED) {
        tools(painted = 31).invoke(this)
        assertTrue(shows("확정하고 팀원 링크 보내기", exact = true))
        assertTrue(shows("공정성 표", exact = true))
    }

    /** Confirmed and sent: nothing filled; 링크 다시 보내기 is a 글자 단추 (it was the filled one on Q1's picture). */
    @Test fun sentStage() = shoot("rvj_211_team_tools_sent_folded", FOLDED) {
        tools(painted = 31, confirmed = true).invoke(this)
        assertTrue(shows("링크 다시 보내기", exact = true))
        assertTrue(shows("바꾸기 요청 받기", exact = true))
    }

    // ---- 빈칸 자동 채우기의 안 (PC-178) ----
    /** Each 안 says what pressing it does: '이 안으로 채우기', not only the window's 닫기. */
    private fun plans(): Screen.() -> Unit = {
        tools().invoke(this)
        tapText("빈칸 자동 채우기", exact = true, scrolls = 0)
        settle()
        assertTrue(appears("이 안으로 채우기", exact = true))
    }

    @Test fun plansLight() = shoot("rvj_212_team_autofill_plans_folded", FOLDED, plans())
    @Test fun plansDarkBig() = shoot("rvj_213_team_autofill_plans_folded_dark_200", DARK, 2f, plans())

    // ---- 안 짠 달 (PC-176) ----
    /** Only next month painted and confirmed: this month is '아직 안 짬', without 오늘 출근 0명 · 빈자리, 빈칸 or 모자란 날. */
    private fun unpainted(): Screen.() -> Unit = {
        seed(painted = 31, confirmed = true)
        content { com.bamnat.app.ui.work.ManageScreen("main", onBack = {}, showAll = true) }
        repeat(2) { settle() }
        assertTrue(scrolledTo("아직 안 짬", exact = true))
        assertFalse("모자란 날은 칠한 달부터", shows("모자란 날", exact = false))
        assertFalse("오늘 출근·빈자리도", shows("빈자리", exact = false))
    }

    @Test fun unpaintedLight() = shoot("rvj_214_team_unpainted_manage_folded", FOLDED, unpainted())
    @Test fun unpaintedDarkBig() = shoot("rvj_215_team_unpainted_manage_folded_dark_200", DARK, 2f, unpainted())

    companion object {
        const val DARK = "w369dp-h905dp-port-night-420dpi"
    }
}
