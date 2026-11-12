package com.bamnat.app.screenshots

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.tier.Tier
import com.bamnat.core.pay.PayPeriod
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 2.0.0 [RVJ-18] 뿌리 고침 6 '굳은 달': the 급여 screen's "이대로 마감할까요?" card (9월 급여, 10/10 지급, 오늘 10/14), the 날짜별 내역's
 * 합 line and 도구 › ○월 마감 with 저절로 마감, light·dark × 글자 100%·200% × 접은·편 폭. Written against what both the old and new
 * screens show, so the same test draws the 'before'.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RvjFrozenScreenshotTest : ScreenshotBase() {
    private val paidOnTheTenth: (com.bamnat.core.pay.PayProfile) -> com.bamnat.core.pay.PayProfile =
        { it.copy(period = PayPeriod(payDay = 10, payNextMonth = true)) }

    private fun ask(): Screen.() -> Unit = {
        payScreen(Tier.PRO, withProfile = true, adjust = paidOnTheTenth).invoke(this)
        if (scrolledTo("이대로 마감할까요?")) reveal("이대로 마감할까요?")
    }

    private fun days(): Screen.() -> Unit = {
        payScreen(Tier.PRO, withProfile = true, adjust = paidOnTheTenth).invoke(this)
        reveal("일 더 보기")
    }

    private fun tools(): Screen.() -> Unit = {
        payScreen(Tier.PRO, withProfile = true, adjust = paidOnTheTenth).invoke(this)
        tapText("도구", exact = true, scrolls = 0)
        settle()
        reveal("이 달 마감하기")
    }

    @Test fun askLight() = shoot("rvj_12_close_ask_folded", FOLDED, ask())
    @Test fun askDarkBig() = shoot("rvj_13_close_ask_folded_dark_200", DARK, 2f, ask())
    @Test fun askWide() = shoot("rvj_14_close_ask_unfolded", UNFOLDED, ask())
    @Test fun daysLight() = shoot("rvj_15_day_split_folded", FOLDED, days())
    @Test fun daysBig() = shoot("rvj_16_day_split_folded_200", FOLDED, 2f, days())
    @Test fun toolsLight() = shoot("rvj_17_close_month_folded", FOLDED, tools())
    @Test fun toolsDarkBig() = shoot("rvj_18_close_month_folded_dark_200", DARK, 2f, tools())

    private companion object {
        const val DARK = "w369dp-h905dp-port-night-420dpi"
    }
}
