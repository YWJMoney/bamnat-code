package com.bamnat.app.screenshots

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.share.ShareApi
import com.bamnat.app.share.ShareLinkException
import com.bamnat.app.ui.Fmt
import com.bamnat.core.team.AskRound
import com.bamnat.core.team.AskRules
import com.bamnat.core.team.Skill
import com.bamnat.core.team.Team
import com.bamnat.core.team.TeamBoard
import com.bamnat.core.team.TeamCell
import com.bamnat.core.team.TeamMember
import com.bamnat.core.team.TeamShift
import com.bamnat.core.time.Today
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZonedDateTime

/**
 * 2.0.0 갈래 J 열아홉째 PR [RVJ-102] (검토 PC-19·U05-07·U05-08·PAYU-16·PAYU-18): 팀 표의 이름 칸·'한 달 한눈에'·신청 카드·신청 받기 창.
 * Light·dark × 글자 100%·200% × 접은·편 폭. The 'before' of the names is 1.39's board, where 8 names of 3~4 letters lay one line over each
 * other in 한눈에 (the 64dp column and cells as tall as they are wide: 9dp).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RvjTeamBoardScreenshotTest : ScreenshotBase() {
    private val month: YearMonth get() = YearMonth.from(Today.date()).plusMonths(1)
    private val names = listOf("김하늘별", "박서준호", "이다정한", "최민아름", "정수빈이", "한지우솔")

    /** 7병동, six people with names of four letters, every day of the month painted so the 한눈에 grid is full. */
    private fun seedTeam(round: AskRound? = null): Team = runBlocking {
        val c = app.container
        val shifts = listOf(
            TeamShift(1, "D", "데이", 0xFF4F8BD6, false, LocalTime.of(7, 0), LocalTime.of(15, 0)),
            TeamShift(2, "E", "이브닝", 0xFFE39B3B, false, LocalTime.of(15, 0), LocalTime.of(23, 0)),
            TeamShift(3, "N", "나이트", 0xFF5E4AA8, false, LocalTime.of(23, 0), LocalTime.of(7, 0)),
            TeamShift(4, "O", "오프", 0xFF9AA3AD, true),
        )
        val team = Team("t1", "7병동", "민지", shifts, 1L, need = mapOf(1L to 2, 2L to 2, 3L to 1), ask = round)
        c.phaseThree.saveTeam(team)
        val members = names.mapIndexed { i, n -> TeamMember("m$i", "t1", n, skill = if (i < 2) Skill.CHARGE else Skill.REG, sort = i) }
        c.phaseThree.saveMembers(members)
        val turn = listOf(1L, 1L, 2L, 2L, 3L, 3L, 4L)
        val cells = members.flatMapIndexed { i, m ->
            (1..month.lengthOfMonth()).map { d -> val date = month.atDay(d); (m.id to date) to TeamCell(m.id, date, turn[(d + i * 2) % turn.size]) }
        }.toMap()
        c.phaseThree.saveBoard("t1", TeamBoard(month, members, cells))
        team
    }

    private fun open(): Screen.() -> Unit = {
        content { com.bamnat.app.ui.team.LeaderTeamScreen("main", onBack = {}) }
        settle()
        tapText("열기", exact = true)
        settle()
    }

    // ---- 한 달 한눈에: 이름이 겹치지 않고, 칸을 누르면 그 날의 큰 보기로 (검토 PC-19·U05-07) ----
    private fun fit(): Screen.() -> Unit = {
        seedTeam()
        open().invoke(this)
        tapText("한 달 한눈에", exact = true)
        settle()
        assertTrue("한눈에로 바뀌었어요", shows("크게 보기", exact = true))
        names.forEach { n -> assertTrue("$n 이름이 보여요", shows(n)) }
    }

    @Test fun fitLight() = shoot("rvj_379_team_fit_folded", FOLDED, fit())
    @Test fun fitDarkBig() = shoot("rvj_380_team_fit_folded_dark_200", DARK, 2f, fit())
    @Test fun fitWide() = shoot("rvj_381_team_fit_unfolded", UNFOLDED, fit())

    /** 글자 200%: the name column grows (112dp at most) and a name keeps two lines, in the big view too. */
    @Test fun bigViewDarkBig() = shoot("rvj_382_team_names_folded_dark_200", DARK, 2f) {
        seedTeam()
        open().invoke(this)
        names.forEach { n -> assertTrue("$n 이름이 보여요", shows(n)) }
    }

    /** A cell is too thin to hit in 한눈에: a tap on one opens the big view at that day and person. */
    @Test fun aTapOnACellOpensTheBigView() = shoot("rvj_383_team_fit_tap_folded", FOLDED) {
        fit().invoke(this)
        val day = month.atDay(12)
        tapText("${names[1]} ${Fmt.monthDay(day)}", prefix = true, scrolls = 0)
        settle()
        assertTrue("크게 보기로 돌아와요", shows("한 달 한눈에", exact = true))
        assertTrue("한눈에 단추는 사라져요", gone("크게 보기", exact = true))
    }

    // ---- 신청 카드: 못 가져오면 말로, 다시 (검토 U05-08·PAYU-16) ----
    private fun failingAsk(): Screen.() -> Unit = {
        val c = app.container
        val real = c.shareApi
        c.shareApi = object : ShareApi by real {
            override suspend fun askRead(id: String, token: String): com.bamnat.app.share.AskGot = throw ShareLinkException("server")
        }
        val round = AskRound("A1", "atok", month, AskRules(), names.indices.associate { "m$it" to "https://s.example/a/K$it" }, syncedAt = Today.millis() - 3_600_000L)
        seedTeam(round)
        open().invoke(this)
        assertTrue("못 가져왔다고 말해요: " + shownWords().joinToString(" | "), appears("새 신청을 못 가져왔어요", exact = false))
        assertTrue("다시 단추가 있어요", shows("다시", exact = true))
    }

    @Test fun askCardFailedLight() = shoot("rvj_384_team_ask_failed_folded", FOLDED, failingAsk())
    @Test fun askCardFailedDarkBig() = shoot("rvj_385_team_ask_failed_folded_dark_200", DARK, 2f, failingAsk())
    @Test fun askCardFailedWide() = shoot("rvj_386_team_ask_failed_unfolded", UNFOLDED, failingAsk())

    // ---- 신청 받기 창: 마감이 이미 지난 시각이면 말로 (검토 PAYU-18) ----
    /** At 13시 today, 마감 12시 of today has gone by: the 링크 만들기 button is off, and the window says why. */
    private fun closeGone(): Screen.() -> Unit = {
        val at = ZonedDateTime.of(2026, 10, 14, 13, 0, 0, 0, SEOUL)
        Today.fix(at)
        try {
            // This month's board, so the 마감 is today (a month that begun is not asked a week ahead).
            val c = app.container
            val team = Team("t1", "7병동", "민지", listOf(TeamShift(1, "D", "데이", 0xFF4F8BD6, false, LocalTime.of(7, 0), LocalTime.of(15, 0)), TeamShift(4, "O", "오프", 0xFF9AA3AD, true)), 1L)
            val now = YearMonth.from(Today.date())
            runBlocking {
                c.phaseThree.saveTeam(team)
                val members = names.take(4).mapIndexed { i, n -> TeamMember("m$i", "t1", n, sort = i) }
                c.phaseThree.saveMembers(members)
                c.phaseThree.saveBoard("t1", TeamBoard(now, members, emptyMap()))
            }
            open().invoke(this)
            tapText("더 보기", exact = true)
            settle()
            tapText("근무 신청 받기", exact = true, scrolls = 0)
            settle()
            tapText("12시", exact = true, scrolls = 0)
            settle()
            assertTrue("지난 마감을 말로 알려요", shows("마감이 벌써 지난 시각이에요", exact = false))
            assertTrue("링크 만들기는 꺼져 있어요", !enabled("링크 만들기"))
        } finally {
            Today.fix(SHOT_TIME)
        }
    }

    @Test fun closeGoneLight() = shoot("rvj_387_team_ask_close_gone_folded", FOLDED, closeGone())
    @Test fun closeGoneDarkBig() = shoot("rvj_388_team_ask_close_gone_folded_dark_200", DARK, 2f, closeGone())

    private companion object {
        const val DARK = "w369dp-h905dp-port-night-420dpi"
    }
}
