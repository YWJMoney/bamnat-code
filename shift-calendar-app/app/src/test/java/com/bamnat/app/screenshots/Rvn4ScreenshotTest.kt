package com.bamnat.app.screenshots

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 2.0.0 갈래 N 4차 [RVN-18] (회사·광장 작은 것 묶음): the screens it touches, light·dark × 글자 100%·200% × 접은·편 폭, for the PR's
 * before/after sheet. Only taps both the old and new screens have, so the same test draws the 'before' on the commit before.
 * 내 활동 (보낸 규칙 줄이 눌려요, U10a-16), 임금표·규칙 광장 위 칩 줄 (한 줄 + 펼치기, U10a-04), 회사 판 카드의 두 단추 줄 (I10aa-05).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class Rvn4ScreenshotTest : ScreenshotBase() {
    private fun activity(): Screen.() -> Unit = {
        val vm = com.bamnat.app.ui.company.CompanyBoardViewModel(companyOn())
        content { com.bamnat.app.ui.company.CompanyBoardScreen(vm, onBack = {}, onOpen = {}, onShare = {}, onRates = {}) }
        vm.activity.show()
        settle()
        reveal("고치자는 제안 1", exact = true)
    }

    private fun ratesTop(): Screen.() -> Unit = {
        val vm = com.bamnat.app.ui.company.RatesViewModel(companyOn())
        content { com.bamnat.app.ui.company.RatesScreen(vm, onBack = {}) }
        settle()
    }

    private fun boardTop(): Screen.() -> Unit = {
        val vm = com.bamnat.app.ui.company.CompanyBoardViewModel(companyOn())
        content { com.bamnat.app.ui.company.CompanyBoardScreen(vm, onBack = {}, onOpen = {}, onShare = {}, onRates = {}) }
        settle()
    }

    private fun setupButtons(): Screen.() -> Unit = {
        val vm = com.bamnat.app.ui.company.CompanyDetailViewModel(companyOn(), "c1")
        content { com.bamnat.app.ui.company.CompanyDetailScreen(vm, setup = false, onBack = {}, onApplied = { _, _ -> }, onByHand = {}) }
        settle()
        // 2.0.0 [RVN-19] U10a-08: the card's other buttons are behind '이 판 더 보기'.
        tapText("이 판 더 보기", exact = true)
        reveal("우리는 달라요 · 다른 점 말하기", exact = true)
    }

    @Test fun activityLight() = shoot("rvn_42_activity_folded", FOLDED, activity())
    @Test fun activityDarkBig() = shoot("rvn_43_activity_folded_dark_200", DARK, 2f, activity())
    @Test fun activityWide() = shoot("rvn_44_activity_unfolded", UNFOLDED, activity())
    @Test fun ratesTopLight() = shoot("rvn_45_rates_top_folded", FOLDED, ratesTop())
    @Test fun ratesTopDarkBig() = shoot("rvn_46_rates_top_folded_dark_200", DARK, 2f, ratesTop())
    @Test fun ratesTopWide() = shoot("rvn_47_rates_top_unfolded_dark", DARK_WIDE, ratesTop())
    @Test fun boardTopBig() = shoot("rvn_49_board_top_folded_200", FOLDED, 2f, boardTop())
    @Test fun boardTopWide() = shoot("rvn_50_board_top_unfolded", UNFOLDED, boardTop())
    @Test fun setupButtonsLight() = shoot("rvn_51_setup_buttons_folded", FOLDED, setupButtons())
    @Test fun setupButtonsDarkBig() = shoot("rvn_52_setup_buttons_folded_dark_200", DARK, 2f, setupButtons())
    @Test fun setupButtonsWide() = shoot("rvn_53_setup_buttons_unfolded", UNFOLDED, setupButtons())

    // 2.0.0 [RVN-18] 총괄 HQ-89: the heads with a title and text buttons at 200% on the folded width (the bar's buttons under it there).
    private fun boardInVillage(): Screen.() -> Unit = {
        val vm = com.bamnat.app.ui.company.CompanyBoardViewModel(companyOn())
        content { com.bamnat.app.ui.company.CompanyBoardScreen(vm, onBack = null, onOpen = {}, onShare = {}, onRates = {}) }
        settle()
    }

    private fun companyTop(): Screen.() -> Unit = {
        val vm = com.bamnat.app.ui.company.CompanyDetailViewModel(companyOn(), "c1")
        content { com.bamnat.app.ui.company.CompanyDetailScreen(vm, setup = false, onBack = {}, onApplied = { _, _ -> }, onByHand = {}) }
        settle()
    }

    private fun reportTop(): Screen.() -> Unit = {
        val vm = com.bamnat.app.ui.company.CompanyReportViewModel(companyOn())
        content { com.bamnat.app.ui.company.CompanyReportScreen(vm, onBack = {}, onDone = {}, onOpenSent = {}) }
        settle()
    }

    private fun talkTop(): Screen.() -> Unit = {
        val vm = com.bamnat.app.ui.company.CompanyBoardViewModel(companyOn())
        content { com.bamnat.app.ui.company.CompanyBoardScreen(vm, onBack = {}, onOpen = {}, onShare = {}, onRates = {}) }
        vm.talk.show("c-v1", "s-v1", "가나다화학 · 품질팀")
        settle()
    }

    @Test fun boardTopDarkBig() = shoot("rvn_85_board_top_folded_dark_200", DARK, 2f, boardTop())
    @Test fun boardInVillageBig() = shoot("rvn_86_board_village_folded_200", FOLDED, 2f, boardInVillage())
    @Test fun companyTopDarkBig() = shoot("rvn_87_company_top_folded_dark_200", DARK, 2f, companyTop())
    @Test fun reportTopBig() = shoot("rvn_88_report_top_folded_200", FOLDED, 2f, reportTop())
    @Test fun talkTopBig() = shoot("rvn_89_talk_top_folded_200", FOLDED, 2f, talkTop())

    private companion object {
        const val DARK = "w369dp-h905dp-port-night-420dpi"
        const val DARK_WIDE = "w707dp-h823dp-port-night-420dpi"
    }
}
