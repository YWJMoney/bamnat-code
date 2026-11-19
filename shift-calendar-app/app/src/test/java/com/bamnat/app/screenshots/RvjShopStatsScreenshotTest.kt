package com.bamnat.app.screenshots

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.ui.guide.FeatureLinks
import com.bamnat.core.boss.StaffMember
import com.bamnat.core.boss.Workplace
import com.bamnat.core.guide.FeatureRoute
import com.bamnat.core.guide.Place
import com.bamnat.core.team.Team
import com.bamnat.core.team.TeamBoard
import com.bamnat.core.team.TeamMember
import com.bamnat.core.team.TeamShift
import com.bamnat.core.time.Today
import com.bamnat.core.work.StaffRoster
import com.bamnat.core.work.StaffShift
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.YearMonth

/**
 * 2.0.0 갈래 J 21차 앞·뒤 사진 (검토 05·05b·05c의 작은 것). [RVJ-105] 직원 인건비 막대의 이름은 막대 위 줄에(U05c-06), 카드 매출 입금의 '입금까지 영업일'은
 * 공통 − + 부품으로(U05c-07), 통계 주별 막대는 넘은 주를 글자로도(U05-12), 이번 달 비교는 '말일까지 센 예상'이라고(U05b-10).
 * [RVJ-106] 손으로 만든 − + 단추는 읽어 줄 이름과 48dp(PAYU-25·PAYU-51): 팀 신청 받기 창의 사람 수. The same scenes run on the code before the batch
 * for the 앞 pictures (lines marked AFTER leave out what is new). Light·dark × 글자 100%·200% × 접은·편 폭.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RvjShopStatsScreenshotTest : ScreenshotBase() {
    /** No text cut or overlapped on this screen (조건 12). */
    private fun Screen.noCuts(name: String) {
        val m = app.resources.displayMetrics
        val cuts = LayoutCheck.cuts(nodes(merged = false), m.widthPixels, m.heightPixels)
        LayoutCheck.report(name, cuts)
        assertEquals("$name: $cuts", emptyList<LayoutCheck.Cut>(), cuts)
    }

    // ---- [RVJ-105] 카드 매출 입금: 입금까지 영업일 (검토 U05c-07·PAYU-25) ----
    /** 영업일·정기휴무 of a shop: the − 2일 + stepper reads "입금까지 영업일 늘리기", and one tap on it makes 3일. */
    private fun settle2(): Screen.() -> Unit = style(WorkStyle.BUSINESS) {
        FeatureLinks.open(FeatureRoute(Place.SHOP_DAYS))
        settle()
        reveal("입금까지 영업일", exact = true)
        assertTrue(shownWords().joinToString(" | "), appears("2일", exact = true))
        compose.onNodeWithContentDescription("입금까지 영업일 늘리기").assertHeightIsAtLeast(48.dp) // AFTER
        compose.onNodeWithContentDescription("입금까지 영업일 늘리기").performClick() // AFTER
        settle() // AFTER
        assertTrue("한 번 눌러 3일", appears("3일", exact = true)) // AFTER
        noCuts("rvj_shop_settle")
    }

    @Test fun settleLight() = shoot("rvj_395_shop_settle_stepper_folded", FOLDED, settle2())
    @Test fun settleDarkBig() = shoot("rvj_396_shop_settle_stepper_folded_dark_200", DARK, 2f, settle2())
    @Test fun settleWide() = shoot("rvj_397_shop_settle_stepper_unfolded", UNFOLDED, settle2())

    // ---- [RVJ-105] 직원 인건비 막대: 이름이 막대 위 줄에 (검토 U05c-06) ----
    /** Three people, one name long, one past 주 52시간 (9~22시, five days), a 5인 이상 shop. */
    private fun bars(): Screen.() -> Unit = {
        runBlocking {
            val store = app.container.phaseThree
            store.saveWorkplace(Workplace("p1", "식당", fivePlus = true))
            val nine = StaffShift(LocalTime.of(9, 0), LocalTime.of(18, 0))
            store.saveStaff(StaffMember("s1", "김은영(주방 오전 전담)", base = 10_320L, weekly = DayOfWeek.entries.take(5).associateWith { nine }), "p1", 0)
            store.saveStaff(StaffMember("s2", "박종수", base = 10_320L, weekly = DayOfWeek.entries.take(5).associateWith { StaffShift(LocalTime.of(9, 0), LocalTime.of(22, 0)) }), "p1", 1)
            store.saveStaff(StaffMember("s3", "이지호", base = 10_320L, weekly = listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY).associateWith { StaffShift(LocalTime.of(10, 0), LocalTime.of(19, 0)) }), "p1", 2)
        }
        content { com.bamnat.app.ui.work.PayrollScreen(StaffRoster(emptyList()), null) {} }
        repeat(2) { settle() }
        var found = false
        repeat(4) { if (!found) { found = scrolledTo("사장님이 내는 몫까지"); if (!found) settle() } }
        reveal("사장님이 내는 몫까지", exact = false)
        assertTrue(shownWords().joinToString(" | "), appears("김은영(주방 오전 전담)", exact = true)) // AFTER
        assertTrue("넘은 사람은 글자로도", appears("주 52시간 넘음", exact = true))
        noCuts("rvj_staff_bars")
    }

    @Test fun barsLight() = shoot("rvj_398_staff_cost_bars_folded", FOLDED, bars())
    @Test fun barsDarkBig() = shoot("rvj_399_staff_cost_bars_folded_dark_200", DARK, 2f, bars())
    @Test fun barsWide() = shoot("rvj_400_staff_cost_bars_unfolded", UNFOLDED, bars())

    // ---- [RVJ-105] 통계: 주별 막대의 넘음 글자, 이번 달 비교 한 줄 (검토 U05-12·U05b-10) ----
    /** A 한 주 근무 경고 of 20시간, so the weeks of the rotation are past it. */
    private fun weeks(): Screen.() -> Unit = {
        app.container.settingsRepository.update { it.copy(weeklyLimitHours = 20) }
        tab(3, wide = false)
        reveal("주별", exact = false)
        assertTrue(shownWords().joinToString(" | "), appears("넘음 +", exact = false)) // AFTER
    }

    @Test fun weeksLight() = shoot("rvj_401_stats_week_over_folded", FOLDED, weeks())
    @Test fun weeksDarkBig() = shoot("rvj_402_stats_week_over_folded_dark_200", DARK, 2f, weeks())

    private fun compare(): Screen.() -> Unit = {
        tab(3, wide = false)
        reveal("월 비교", exact = false)
        assertTrue(shownWords().joinToString(" | "), appears("이번 달은 근무표대로 말일까지 센 예상이고", exact = false)) // AFTER
    }

    @Test fun compareLight() = shoot("rvj_403_stats_compare_this_month_folded", FOLDED, compare())

    // ---- [RVJ-106] 팀 신청 받기 창의 사람 수: 공통 − + 부품 (검토 PAYU-25) ----
    private fun ask(): Screen.() -> Unit = {
        val c = app.container
        val team = Team("t1", "7병동", "민지", listOf(TeamShift(1, "D", "데이", 0xFF4F8BD6, false, LocalTime.of(7, 0), LocalTime.of(15, 0)), TeamShift(4, "O", "오프", 0xFF9AA3AD, true)), 1L)
        runBlocking {
            c.phaseThree.saveTeam(team)
            val members = listOf("김하늘별", "박서준호", "이다정한").mapIndexed { i, n -> TeamMember("m$i", "t1", n, sort = i) }
            c.phaseThree.saveMembers(members)
            c.phaseThree.saveBoard("t1", TeamBoard(YearMonth.from(Today.date()).plusMonths(1), members, emptyMap()))
        }
        content { com.bamnat.app.ui.team.LeaderTeamScreen("main", onBack = {}) }
        settle()
        tapText("열기", exact = true)
        settle()
        tapText("더 보기", exact = true)
        settle()
        tapText("근무 신청 받기", exact = true, scrolls = 0)
        settle()
        assertTrue(shownWords().joinToString(" | "), appears("평일 하루 쉬는 신청", exact = true))
        compose.onNodeWithContentDescription("평일 하루 쉬는 신청 늘리기").assertHeightIsAtLeast(48.dp) // AFTER
        compose.onNodeWithContentDescription("평일 하루 쉬는 신청 늘리기").performClick() // AFTER
        settle() // AFTER
        assertTrue("한 번 눌러 1명", appears("1명", exact = true)) // AFTER
        noCuts("rvj_team_ask_steppers")
    }

    @Test fun askLight() = shoot("rvj_404_team_ask_steppers_folded", FOLDED, ask())
    @Test fun askDarkBig() = shoot("rvj_405_team_ask_steppers_folded_dark_200", DARK, 2f, ask())

    // AFTER-BEGIN
    /** [RVJ-106] The glyph button of a hand-made stepper (규칙표의 마감일·월급날, 회계연도, 월 소정근로시간, ‹ ›): named, and 48dp both ways. */
    @Test fun glyphButtonsAreNamedAndBigEnough() = shoot("rvj_406_glyph_step_buttons_folded", FOLDED) {
        content {
            Column {
                com.bamnat.app.ui.components.GlyphStepButton("−", "정산 마감일 줄이기", {})
                com.bamnat.app.ui.components.GlyphStepButton("+", "정산 마감일 늘리기", {})
                com.bamnat.app.ui.components.GlyphStepButton("‹", "이전 달", {}, outlined = false)
                com.bamnat.app.ui.components.GlyphStepButton("›", "다음 달", {}, enabled = false, outlined = false)
            }
        }
        settle()
        listOf("정산 마감일 줄이기", "정산 마감일 늘리기", "이전 달", "다음 달").forEach {
            compose.onNodeWithContentDescription(it).assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        }
    }
    // AFTER-END

    private companion object {
        const val DARK = "w369dp-h905dp-port-night-420dpi"
    }
}
