package com.bamnat.app.screenshots

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.company.PayWay
import com.bamnat.app.data.PersonalCalendar
import com.bamnat.app.data.WorkToolsStore
import com.bamnat.app.tier.Tier
import com.bamnat.app.ui.work.ShopToolCards
import com.bamnat.core.money.MoneyBook
import com.bamnat.core.time.Today
import com.bamnat.core.work.Staff
import com.bamnat.core.work.StaffRoster
import com.bamnat.core.work.StaffShift
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.YearMonth

/**
 * 2.0.0 갈래 J 여덟 번째: [RVJ-66] 총괄 HQ-103(조건 6 '빨강은 위험만, 주황은 주의만'): 1억 원 넘는 수당·최저임금보다 적은 직원 시급처럼 확인할 일이
 * 주의(주황)로. [RVJ-67] 총괄 PC-156: 회사 급여 방식을 넣은 폰의 급여 머리 줄이 '직접 설정' 대신 '가나다화학 방식 · 월급제', 급여 설정 고치기
 * 위에도 같은 이름.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RvjRedScreenshotTest : ScreenshotBase() {
    @After fun noWay() {
        app.container.companyOf(PersonalCalendar.MAIN).payWays.use(null)
    }

    // ── [RVJ-67] PC-156 ──
    private fun withWay(): Screen.() -> Unit = {
        app.container.companyOf(PersonalCalendar.MAIN).payWays.use(PayWay("c-g", "가나다화학", "생산부", "s-c-g"))
        payScreen(Tier.PRO, withProfile = true).invoke(this)
    }

    @Test fun payHeadSaysTheWay() = shoot("rvj_190_pay_head_way_folded", FOLDED) {
        withWay().invoke(this)
        assertTrue(shows("가나다화학 방식 · "))
        assertTrue(!shows("직접 설정 · "))
    }

    @Test fun payHeadSaysTheWayDarkBig() = shoot("rvj_191_pay_head_way_folded_dark_200", DARK, 2f) {
        withWay().invoke(this)
        assertTrue(shows("가나다화학 방식 · "))
    }

    @Test fun theSetupSaysTheWayToo() = shoot("rvj_192_pay_setup_way_folded", FOLDED) {
        withWay().invoke(this)
        tapText("급여 설정 고치기", exact = true, scrolls = 0)
        // The menu is a popup of its own, outside the window the screen reads: found through the compose rule (as BAScreenshotTest).
        compose.onNodeWithText("자세히 고치기 (하나씩 묻기)").performClick()
        repeat(2) { settle() }
        assertTrue(shows("지금 ‘가나다화학’ 급여 방식으로 계산하고 있어요", exact = true))
    }

    // ── [RVJ-66] HQ-103 ──
    private fun huge(): Screen.() -> Unit = {
        payScreen(Tier.PRO, withProfile = true).invoke(this)
        tapText("내 급여 값", exact = true, scrolls = 0)
        settle()
        tapText("수당 추가", exact = true, scrolls = 14)
        typeInto("한 달 금액", "200000000")
        repeat(2) { settle() }
    }

    @Test fun aHugeAllowanceIsToCheck() = shoot("rvj_193_item_huge_folded", FOLDED) {
        huge().invoke(this)
        assertTrue(appears("금액을 확인해 주세요. 1억 원이 넘어요"))
    }

    @Test fun aHugeAllowanceIsToCheckDarkBig() = shoot("rvj_194_item_huge_folded_dark_200", DARK, 2f, huge())

    private fun underMinimum(): Screen.() -> Unit = {
        val roster = StaffRoster(listOf(
            Staff("a", "주말알바", 9_000, shifts = listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY).associateWith { StaffShift(LocalTime.of(10, 0), LocalTime.of(18, 0)) }),
        ))
        WorkToolsStore.get(app).update(PersonalCalendar.MAIN) { it.copy(roster = roster) }
        settle()
        content {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ShopToolCards(PersonalCalendar.MAIN, MoneyBook(), YearMonth.from(Today.date()))
            }
        }
        settle()
        tapText("근무표", exact = true, scrolls = 0)
        settle()
        reveal("최저임금")
    }

    @Test fun aWageUnderTheMinimumIsToCheck() = shoot("rvj_195_shop_staff_minimum_folded", FOLDED) {
        underMinimum().invoke(this)
        assertTrue(shows("최저임금"))
    }

    @Test fun aWageUnderTheMinimumIsToCheckDarkBig() = shoot("rvj_196_shop_staff_minimum_folded_dark_200", DARK, 2f, underMinimum())

    private companion object {
        const val DARK = "w369dp-h905dp-port-night-420dpi"
    }
}
