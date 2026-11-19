package com.bamnat.app.screenshots

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.ui.settings.SettingsGo
import com.bamnat.app.ui.settings.SettingsLinks
import com.bamnat.core.work.WorkStyle
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 2.0.0 갈래 J 15차 앞·뒤 사진 (검토 05c 사장님·가게 도구·도움말 색인). [RVJ-93] 검토 BOSS-70·U05c-10: 두 낱말 중 하나만 맞는 찾기는
 * '꼭 맞는 설정은 없어요'와 '비슷한 설정'(세 개까지)으로 나뉘어요. [RVJ-93] BOSS-68: 가게 사장님이 '관리'로 찾으면 '가게 관리'가 맨 위.
 * [RVJ-94] BOSS-73: 사장님이 사장님 말('4대보험')로 물으면 사장님 답이 나와요. The same scenes run on the code before the batch for
 * the 앞 pictures. Light·dark × 글자 100%·200% × 접은·편 폭.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RvjHelpSearchScreenshotTest : ScreenshotBase() {
    /** No text cut or overlapped on this screen (조건 12). */
    private fun Screen.noCuts(name: String) {
        val m = app.resources.displayMetrics
        val cuts = LayoutCheck.cuts(nodes(merged = false), m.widthPixels, m.heightPixels)
        LayoutCheck.report(name, cuts)
        org.junit.Assert.assertEquals("$name: $cuts", emptyList<LayoutCheck.Cut>(), cuts)
    }

    /**
     * A 가게 사장님 with a 급여 대장: `boss.payroll` waits for 처리방침 3.11 (its day and its hold, read by the server's clock), so the
     * clock is the one after 3.14 ([policy314]) and the 사업장 is there, as on a phone after 정식 출시.
     */
    private fun Screen.bossOn() {
        policy314()
        kotlinx.coroutines.runBlocking { app.container.phaseThree.saveWorkplace(com.bamnat.core.boss.Workplace("p1", "식당", fivePlus = true)) }
        app.container.flags.refresh()
        for (i in 0 until 200) { if (app.container.flags.on(com.bamnat.core.flags.FlagCatalog.BOSS_PAYROLL)) break; Thread.sleep(20) }
        assertTrue("boss.payroll: ${app.container.flags.state(com.bamnat.core.flags.FlagCatalog.BOSS_PAYROLL)?.reason}",
            app.container.flags.on(com.bamnat.core.flags.FlagCatalog.BOSS_PAYROLL))
        settle()
    }

    /** 설정 › 설정 찾기 with [query] typed, as a 가게 사장님. */
    private fun search(query: String, after: Screen.() -> Unit = {}): Screen.() -> Unit = style(WorkStyle.BUSINESS) {
        bossOn()
        tab(SETTINGS_TAB, wide = false)
        typeInto("설정 찾기", query)
        after()
    }

    /** One word of two fits: no count of found settings, the near ones apart, and the help answer for 근로계약서 below. */
    private fun near(): Screen.() -> Unit = search("근로계약서 알람음") {
        assertTrue(shownWords().joinToString(" | "), appears("꼭 맞는 설정은 없어요"))
        assertTrue(appears("비슷한 설정", exact = true))
        noCuts("rvj_search_near")
    }

    @Test fun nearFolded() = shoot("rvj_331_settings_search_near_folded", FOLDED, near())
    @Test fun nearFoldedDarkBig() = shoot("rvj_332_settings_search_near_folded_dark_200", RvjTeamCheckScreenshotTest.DARK, 2f, near())
    @Test fun nearUnfolded() = shoot("rvj_333_settings_search_near_unfolded", UNFOLDED, near())

    /** '관리' finds the 가게 관리 screen first, where 더 보기 holds it. */
    private fun manage(): Screen.() -> Unit = search("관리") {
        assertTrue(shownWords().joinToString(" | "), appears("가게 관리", exact = true))
        noCuts("rvj_search_manage")
    }

    @Test fun manageFolded() = shoot("rvj_334_settings_search_manage_folded", FOLDED, manage())
    @Test fun manageFoldedDarkBig() = shoot("rvj_335_settings_search_manage_folded_dark_200", RvjTeamCheckScreenshotTest.DARK, 2f, manage())

    /** 문의·도움말 › 기능과 도움말 찾기 '4대보험' as a 사장님: the 사장님's answer, opened. */
    private fun insurance(): Screen.() -> Unit = style(WorkStyle.BUSINESS) {
        bossOn()
        SettingsLinks.go(SettingsGo.INQUIRY)
        settle()
        typeInto("기능과 도움말 찾기", "4대보험")
        tapText("직원이 4대보험 가입 대상인지 알고 싶어요")
        assertTrue(shownWords().joinToString(" | "), appears("4대보험 (사장님 몫 계산)"))
        noCuts("rvj_help_insurance")
    }

    @Test fun insuranceFolded() = shoot("rvj_336_help_boss_insurance_folded", FOLDED, insurance())
    @Test fun insuranceFoldedDarkBig() = shoot("rvj_337_help_boss_insurance_folded_dark_200", RvjTeamCheckScreenshotTest.DARK, 2f, insurance())
    @Test fun insuranceUnfolded() = shoot("rvj_338_help_boss_insurance_unfolded", UNFOLDED, insurance())
}
