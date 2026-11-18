package com.bamnat.app.screenshots

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.core.work.WorkStyle
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 2.0.0 갈래 K [RVK-48] (PC-161, 조건 12·4): 가장 흔한 폰 폭(411dp, 글자 100%)에서 가계부·장부 머리. ‹ 달 ›, 눈, 찾기, ⋮, 나가 한 줄에
 * 서서 탭 제목이 글자 조각만 보였어요. 찾기는 제목 자리가 없을 때 ⋮ 안으로 들어가요. 접은 폭(369)·편 폭만 찍던 시험에 411dp를 더해요.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RvkPc161ScreenshotTest : ScreenshotBase() {
    private fun head(s: WorkStyle, title: String): Screen.() -> Unit = style(s) {
        ledgerPage(null)()
        settle()
        val node = nodes().firstOrNull { n -> n.config.getOrNull(SemanticsProperties.Text)?.any { it.text == title } == true }
        assertTrue("‘$title’ 제목이 머리에 있어요", node != null)
        val dp = app.resources.displayMetrics.density
        val wide = node!!.boundsInWindow.width / dp
        assertTrue("‘$title’ 제목이 글자 조각이 아니에요 (${wide}dp)", wide >= title.length * 16f)
        assertFalse("찾기는 ⋮ 안에 있어요", shows("기능·설정 찾기", exact = true))
    }

    @Test fun dayHead() = shoot("rvk_pc161_01_money_head_411", PHONE_411, head(WorkStyle.DAY, "가계부"))
    @Test fun shopHead() = shoot("rvk_pc161_02_shop_ledger_head_411", PHONE_411, head(WorkStyle.BUSINESS, "장부"))
    @Test fun searchInMenu() = shoot("rvk_pc161_03_money_menu_search_411", PHONE_411, style(WorkStyle.DAY) {
        ledgerPage(null)()
        tapText("가계부 메뉴", exact = true, scrolls = 0)
        assertTrue("⋮ 안의 기능·설정 찾기", inPopup("기능·설정 찾기"))
    })
    @Test fun dayHeadDark() = shoot("rvk_pc161_04_money_head_411_dark", "w411dp-h923dp-port-night-420dpi", head(WorkStyle.DAY, "가계부"))

    private companion object {
        /** 1080×2424 at 420dpi, the most common phone width. */
        const val PHONE_411 = "w411dp-h923dp-port-420dpi"
    }
}
