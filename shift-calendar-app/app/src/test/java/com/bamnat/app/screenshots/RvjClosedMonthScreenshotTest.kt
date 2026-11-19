package com.bamnat.app.screenshots

import androidx.compose.runtime.collectAsState
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.tier.Tier
import com.bamnat.core.time.Today
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 2.0.0 갈래 J 네 번째 묶음, 통합 2차 PC 확인(ac6c21ce)에서 찾은 급여 화면 다섯 줄:
 * [RVJ-32] PC-80 마감한 달을 보며 고친 값이 '10월 급여가 바뀐다'고 하던 것, [RVJ-33] PC-84 '(으)로', [RVJ-34] PC-85 값 위에 끼어든
 * 단추 넷 카드, [RVJ-35] PC-86 '달로 주는 몫' 풀이, [RVJ-36] PC-87·PAYU-17 마감한 달의 제목. 밝은·어두운 × 글자 100%·200% × 접은·편 폭.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RvjClosedMonthScreenshotTest : ScreenshotBase() {
    private val shown get() = Today.month()
    private val next get() = Today.month().plusMonths(1)

    /** The pay screen with this month closed as 도구 › 이 달 마감하기 closes it (RvjFrozenScreenshotTest goes through the button). */
    private fun closed(): Screen.() -> Unit = {
        payScreen(Tier.PRO, withProfile = true).invoke(this)
        runBlocking {
            val c = app.container
            val month = com.bamnat.app.ui.pay.PayViewModel(c).calculateOff(c.payStore.profile.value!!, c.scheduleRepository.snapshot.first(), Today.month())
            c.payStore.saveClosed(com.bamnat.core.pay.CalcSnapshot.close(month, Today.millis()), Today.month())
        }
        repeat(2) { settle() }
    }

    /** 내 급여 값 › 수당 추가 on the closed month, 200,000원 a month typed in. */
    private fun adding(): Screen.() -> Unit = {
        closed().invoke(this)
        tapText("내 급여 값", exact = true, scrolls = 0)
        settle()
        tapText("수당 추가", exact = true, scrolls = 14)
        typeInto("한 달 금액", "200000")
        repeat(2) { settle() }
    }

    @Test fun theItemWindowSaysTheClosedMonthStays() = shoot("rvj_80_closed_add_folded", FOLDED) {
        adding().invoke(this)
        assertTrue(appears("${shown.monthValue}월은 마감돼 그대로예요 · ${next.monthValue}월 급여부터"))
        assertFalse(shows("${shown.monthValue}월 급여에", exact = true))
    }

    @Test fun theItemWindowDarkBig() = shoot("rvj_81_closed_add_folded_dark_200", DARK, 2f) {
        adding().invoke(this)
        assertTrue(appears("${shown.monthValue}월은 마감돼 그대로예요"))
    }

    /** [RVJ-33] PC-84: the name asked once, its words with the particle worked out. */
    @Test fun theNameAskSaysItsParticle() = shoot("rvj_82_name_ask_folded", FOLDED) {
        adding().invoke(this)
        tapText("추가", exact = true)
        settle()
        assertTrue(appears("이름을 비우면 ‘매달 수당 1’로 넣어요"))
        assertFalse(shows("(으)로"))
    }

    /** [RVJ-32] After the save the band says where the change went. */
    @Test fun theSavedBandSaysTheNextMonth() = shoot("rvj_83_closed_saved_folded", FOLDED) {
        adding().invoke(this)
        tapText("추가", exact = true)
        settle()
        tapText("추가", exact = true)
        repeat(3) { settle() }
        assertTrue(appears("${shown.monthValue}월은 마감돼 그대로예요 · ${next.monthValue}월 급여부터 +200,000원"))
    }

    @Test fun theSavedBandWide() = shoot("rvj_84_closed_saved_unfolded", UNFOLDED) {
        adding().invoke(this)
        tapText("추가", exact = true)
        settle()
        tapText("추가", exact = true)
        repeat(3) { settle() }
        assertTrue(appears("${next.monthValue}월 급여부터 +200,000원"))
    }

    /** [RVJ-32] 회사 규칙 고치기 on a closed month: its band works out the next month. */
    @Test fun theRulesBandNamesTheNextMonth() = shoot("rvj_85_closed_rules_folded", FOLDED) {
        closed().invoke(this)
        val vm = com.bamnat.app.ui.pay.PayViewModel(app.container)
        content {
            val profile = vm.profile.collectAsState().value
            val snap = vm.snapshot.collectAsState().value
            if (profile != null && snap != null) com.bamnat.app.ui.pay.CompanyRulesEditor(vm, profile, snap, Today.month(), onClose = {}, onOpenKinds = {})
        }
        repeat(2) { settle() }
        assertTrue(appears("${shown.monthValue}월은 마감돼 그대로예요", exact = true))
        assertTrue(appears("${next.monthValue}월 합계"))
    }

    /** [RVJ-36] PC-87·PAYU-17: the closed month's title, here and on 근무·급여. */
    @Test fun theClosedTitle() = shoot("rvj_86_closed_title_folded", FOLDED) {
        closed().invoke(this)
        assertTrue(appears("${shown.monthValue}월 급여 (세전)", exact = true))
        assertFalse(shows("${shown.monthValue}월 예상 급여 (세전)", exact = true))
    }

    /** [RVJ-35] PC-86: the 날짜별 합 line opens into what the month's share is made of. */
    private fun split(open: Boolean): Screen.() -> Unit = {
        payScreen(Tier.PRO, withProfile = true).invoke(this)
        reveal("날짜별 합")
        if (open) {
            tapText("날짜별 합")
            settle()
        }
    }

    @Test fun theSplitLine() = shoot("rvj_87_split_folded", FOLDED, split(false))
    @Test fun theSplitOpens() = shoot("rvj_88_split_open_folded", FOLDED) {
        split(true).invoke(this)
        assertTrue(appears("달로 주는 몫 "))
        assertTrue(appears("= 기본급 "))
    }
    @Test fun theSplitOpensDarkBig() = shoot("rvj_89_split_open_folded_dark_200", DARK, 2f, split(true))

    /** [RVJ-34] PC-85: the invitation after an item is made sits under the values, one line, one button and X. */
    private fun shareAfterMade(): Screen.() -> Unit = {
        madeDeduction()
        reveal("우리 회사 방식, 동료에게도 알려 줄까요?", exact = true)
    }

    @Test fun theShareCardUnderTheValues() = shoot("rvj_90_share_under_values_folded", FOLDED) {
        shareAfterMade().invoke(this)
        assertFalse(shows("그만 볼게요", exact = true))
        assertTrue(shows("다음에 볼게요"))
    }
    @Test fun theShareCardDarkBig() = shoot("rvj_91_share_under_values_folded_dark_200", DARK, 2f, shareAfterMade())
    @Test fun theShareCardWide() = shoot("rvj_92_share_under_values_unfolded", UNFOLDED, shareAfterMade())

    private companion object {
        const val DARK = "w369dp-h905dp-port-night-420dpi"
    }
}
