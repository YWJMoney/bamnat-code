package com.bamnat.app.screenshots

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.tier.Tier
import com.bamnat.core.boss.StaffMember
import com.bamnat.core.boss.Workplace
import com.bamnat.core.pay.PayPeriod
import com.bamnat.core.time.Today
import com.bamnat.core.work.StaffRoster
import com.bamnat.core.work.StaffShift
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZonedDateTime

/**
 * 2.0.0 갈래 J, 통합 2차 PC 21:33 KST판(Q34~Q37, 7절): [RVJ-48] PC-117 새해 첫날 12월 대장에서 더한 직원의 시급과 최저임금 경고가 다른 해,
 * [RVJ-49] PC-121 '이대로 마감할까요?'에 그 달 금액이 없던 것, [RVJ-50] PC-118 가게 인건비 칸의 빨간 바탕, [RVJ-51] PC-119 금액 없는
 * 수당을 넣은 뒤 '합계에 바로 들어갔어요', [RVJ-52] PC-120 말일 마감에 월급날 10일이 그달 10일로 잡히던 것.
 * The shots hold no assertion so the same test draws the 'before'; the tests that name what they look for check.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RvjPc117ScreenshotTest : ScreenshotBase() {
    /** The class keeps 2026-10-14 (SHOT_TIME); a test that moves the clock gives it back. */
    @After fun clockBack() = Today.fix(SHOT_TIME)

    // ── [RVJ-48] PC-117: 2027-01-02, the 대장 on 12월 ──
    private fun newYear(): Screen.() -> Unit = {
        Today.fix(ZonedDateTime.of(2027, 1, 2, 10, 0, 0, 0, SEOUL))
        runBlocking { app.container.phaseThree.saveWorkplace(Workplace("p1", "식당", fivePlus = false, payday = 10)) }
        content { com.bamnat.app.ui.work.PayrollScreen(StaffRoster(emptyList()), YearMonth.of(2026, 12)) {} }
        repeat(2) { settle() }
        tapText("직원 더하기", exact = true)
        settle()
    }

    @Test fun newYearStaff() = shoot("rvj_120_new_year_staff_folded", FOLDED, newYear())

    @Test fun theFirstWageAndItsWarningAreOneYear() = shoot("rvj_121_new_year_staff_low_folded", FOLDED) {
        newYear().invoke(this)
        assertFalse("처음 값은 그 해 최저임금이라 경고가 없어요", shows("최저임금"))
        typeInto("시급", "10320")
        repeat(2) { settle() }
        assertTrue(appears("2027년 최저임금"))
    }

    @Test fun newYearStaffDarkBig() = shoot("rvj_122_new_year_staff_low_folded_dark_200", DARK, 2f) {
        newYear().invoke(this)
        typeInto("시급", "10320")
        repeat(2) { settle() }
    }

    // ── [RVJ-49] PC-121: 9월 급여, 10/10 지급, 오늘 10/14 ──
    private val paidOnTheTenth: (com.bamnat.core.pay.PayProfile) -> com.bamnat.core.pay.PayProfile =
        { it.copy(period = PayPeriod(payDay = 10, payNextMonth = true)) }

    private fun ask(): Screen.() -> Unit = {
        payScreen(Tier.PRO, withProfile = true, adjust = paidOnTheTenth).invoke(this)
        if (scrolledTo("이대로 마감할까요?")) reveal("이대로 마감할까요?")
    }

    @Test fun closeAsk() = shoot("rvj_123_close_ask_folded", FOLDED, ask())
    @Test fun closeAskDarkBig() = shoot("rvj_124_close_ask_folded_dark_200", DARK, 2f, ask())
    @Test fun closeAskWide() = shoot("rvj_139_close_ask_unfolded", UNFOLDED, ask())

    @Test fun theCloseAskShowsThatMonthsAmount() = shoot("rvj_125_close_ask_amount_folded", FOLDED) {
        ask().invoke(this)
        assertTrue(appears("9월 급여 (세전)"))
    }

    @Test fun laterSaysWhereTheCloseIs() = shoot("rvj_126_close_later_folded", FOLDED) {
        ask().invoke(this)
        tapText("나중에", exact = true)
        repeat(2) { settle() }
        assertTrue(gone("이대로 마감할까요?"))
        assertTrue(scrolledTo("9월 마감은 도구 › 9월 마감에서 할 수 있어요"))
    }

    @Test fun theLaterLineOpensThatMonthsTools() = shoot("rvj_127_close_later_tools_folded", FOLDED) {
        ask().invoke(this)
        tapText("나중에", exact = true)
        repeat(2) { settle() }
        tapText("9월 마감은 도구", exact = false)
        repeat(2) { settle() }
        assertTrue(scrolledTo("9월 마감", exact = true))
    }

    // ── [RVJ-50] PC-118: the shop card on 팀·가게 관리 ──
    private fun shopCard(): Screen.() -> Unit = {
        runBlocking {
            val store = app.container.phaseThree
            store.saveWorkplace(Workplace("p1", "우리 가게", payday = 10))
            store.saveStaff(StaffMember("s1", "김알바", base = 10_320L, weekly = mapOf(DayOfWeek.MONDAY to StaffShift(LocalTime.of(9, 0), LocalTime.of(18, 0)))), "p1")
        }
        content { com.bamnat.app.ui.work.ManageScreen("main", onBack = {}, showAll = true) }
        repeat(2) { settle() }
        reveal("인건비", exact = false)
    }

    @Test fun shopCardLight() = shoot("rvj_128_shop_card_folded", FOLDED, shopCard())
    @Test fun shopCardDarkBig() = shoot("rvj_129_shop_card_folded_dark_200", DARK, 2f, shopCard())
    @Test fun shopCardWide() = shoot("rvj_140_shop_card_unfolded", UNFOLDED, shopCard())

    @Test fun theShopCardSaysWhatIsLeftWithoutRed() = shoot("rvj_130_shop_card_said_folded", FOLDED) {
        shopCard().invoke(this)
        assertTrue(appears("닫기 전"))
        assertTrue(scrolledTo("5인 이상인지 아직 안 골랐어요"))
    }

    // ── [RVJ-51] PC-119: a 매달 수당 with no amount ──
    private fun unknownItem(): Screen.() -> Unit = {
        payScreen(Tier.PRO, withProfile = true).invoke(this)
        tapText("내 급여 값", exact = true, scrolls = 0)
        settle()
        tapText("수당 추가", exact = true, scrolls = 14)
        typeInto("이름 (선택)", "가족수당")
        repeat(2) { settle() }
        tapText("추가", exact = true)
        repeat(3) { settle() }
        reveal("만들었어요", exact = false)
    }

    @Test fun unknownItemMade() = shoot("rvj_131_unknown_item_made_folded", FOLDED, unknownItem())

    @Test fun anItemWithNoAmountIsNotSaidToBeInTheTotal() = shoot("rvj_132_unknown_item_said_folded", FOLDED) {
        unknownItem().invoke(this)
        assertTrue(appears("‘가족수당’ 항목을 만들었어요. 금액을 넣으면 합계에 들어가요."))
        assertFalse(shows("합계에 바로 들어갔어요"))
    }

    // ── [RVJ-52] PC-120: 말일 마감, 월급날 10일 ──
    private fun payDay(): Screen.() -> Unit = {
        rulesEditor { companyRules }.invoke(this)
        reveal("월급날", exact = true)
        tapText("10일", exact = true)
        repeat(2) { settle() }
    }

    @Test fun payDayTen() = shoot("rvj_133_pay_day_ten_folded", FOLDED, payDay())
    @Test fun payDayTenDarkBig() = shoot("rvj_134_pay_day_ten_folded_dark_200", DARK, 2f, payDay())

    @Test fun payDayTenIsTheNextMonths() = shoot("rvj_135_pay_day_ten_said_folded", FOLDED) {
        payDay().invoke(this)
        assertTrue(scrolledTo("10월 급여는 11월 10일에 들어와요."))
        assertFalse(shows("10월 급여는 10월 10일"))
    }

    @Test fun payDayTwentyFiveSaysItIsEarly() = shoot("rvj_136_pay_day_25_folded", FOLDED) {
        rulesEditor { companyRules }.invoke(this)
        reveal("월급날", exact = true)
        tapText("25일", exact = true)
        repeat(2) { settle() }
        // 2026-10-25 is a Sunday: with K's 쉬는 날 칩 (RVK-38, 처음 값 '앞당겨 받아요') the one sentence says the moved day and why, and
        // '미리 받는 날' is judged on the 25th itself (J·K 맞춤).
        assertTrue(scrolledTo("10월 급여는 10월 23일에 들어와요(25일이 쉬는 날이라 앞당겨요). 일이 끝나기 전에 미리 받는 날이에요."))
    }

    // ── [RVJ-53] PC-130: a team made today opens on this month ──
    private fun newTeam(nextStarted: Boolean): Screen.() -> Unit = {
        runBlocking {
            val c = app.container
            c.settingsRepository.update { it.copy(workStyle = com.bamnat.core.work.WorkStyle.SHIFT, setupLater = false) }
            val shifts = c.scheduleRepository.shiftTypes.first().map(com.bamnat.core.team.TeamTimes::of)
            c.phaseThree.saveTeam(com.bamnat.core.team.Team("t1", "7병동", "민지", shifts, 1L))
            val members = listOf("민지", "준호").mapIndexed { i, n -> com.bamnat.core.team.TeamMember("m$i", "t1", n, sort = i) }
            c.phaseThree.saveMembers(members)
            if (nextStarted) {
                val next = YearMonth.from(Today.date()).plusMonths(1)
                val day = next.atDay(1)
                c.phaseThree.saveBoard("t1", com.bamnat.core.team.TeamBoard(next, members,
                    mapOf(("m0" to day) to com.bamnat.core.team.TeamCell("m0", day, shifts.first().id))))
            }
        }
        content { com.bamnat.app.ui.team.LeaderTeamScreen("main", onBack = {}) }
        settle()
        tapText("열기", exact = true)
        repeat(2) { settle() }
    }

    @Test fun aNewTeamOpensOnThisMonth() = shoot("rvj_137_new_team_this_month_folded", FOLDED) {
        newTeam(nextStarted = false).invoke(this)
        assertTrue(appears(com.bamnat.app.ui.Fmt.month(YearMonth.from(Today.date())), exact = true))
    }

    @Test fun aTeamPlanningAheadOpensOnNextMonth() = shoot("rvj_138_team_next_started_folded", FOLDED) {
        newTeam(nextStarted = true).invoke(this)
        assertTrue(appears(com.bamnat.app.ui.Fmt.month(YearMonth.from(Today.date()).plusMonths(1)), exact = true))
    }

    private companion object {
        const val DARK = "w369dp-h905dp-port-night-420dpi"
    }
}
