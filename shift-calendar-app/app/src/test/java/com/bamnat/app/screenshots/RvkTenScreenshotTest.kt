package com.bamnat.app.screenshots

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.core.money.Job
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 2.0.0 갈래 K 10차 [RVK-45] (조건 6 '빨강은 위험만, 주황은 주의만'): 가계부에서 '주의'를 뜻하던 빨강(CalendarColors.warning)을
 * 주황 주의 색으로. 약속일 지난 받을 돈(목록 머리·줄 칩·테두리·나이 막대·요약 카드), 쓰는 속도의 예산 선, 일감의 받을 돈.
 * Light·dark × 글자 100%·200% × 접은·편 폭. The code before draws the same scenes (the PR's 'before'). Today is 2026-10-14.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RvkTenScreenshotTest : ScreenshotBase() {
    /** 받을 돈: 라마바전자 9월 납품 대금, 약속일 엿새 지남. */
    private fun owedList(wide: Boolean = false): Screen.() -> Unit = owed(WorkStyle.BUSINESS, wide)

    /** 현장 장부 요약의 받을 돈 카드: 약속일 지난 일감 잔금 하나. */
    private fun summaryOwed(): Screen.() -> Unit = style(WorkStyle.FIELD) {
        runBlocking {
            app.container.moneyRepository.save(Job(date = today.minusDays(30), title = "주방 타일", client = "박OO 고객", amount = 1_800_000,
                deposit = 600_000, depositPaid = true, due = today.minusDays(5)))
        }
        settle()
        ledgerPage(null)()
        settle()
        reveal("약속일 지난 돈", exact = false)
    }

    /** 통계 › 쓰는 속도: 이번 달 예산 선. */
    private fun pace(wide: Boolean = false): Screen.() -> Unit = style(WorkStyle.DAY) {
        ledger()
        tab(1, wide)
        tapText("통계", exact = true, scrolls = 0)
        settle()
        reveal("쓰는 속도", exact = false)
        reveal("예산 ", exact = false)
    }

    @Test fun owedLight() = shoot("rvk10_01_owed_overdue_folded", FOLDED, owed(WorkStyle.BUSINESS) {
        assertTrue("약속일 지난 줄", appears("약속일 지난 것 1건", exact = false))
    })
    @Test fun owedDarkBig() = shoot("rvk10_02_owed_overdue_folded_dark_200", DARK, 2f, owedList())
    @Test fun owedWide() = shoot("rvk10_03_owed_overdue_unfolded", UNFOLDED, owedList(wide = true))
    @Test fun summaryOwedLight() = shoot("rvk10_04_summary_owed_folded", FOLDED, summaryOwed())
    @Test fun summaryOwedDarkBig() = shoot("rvk10_05_summary_owed_folded_dark_200", DARK, 2f, summaryOwed())
    @Test fun paceLight() = shoot("rvk10_06_pace_budget_folded", FOLDED, pace())
    @Test fun paceDarkBig() = shoot("rvk10_07_pace_budget_folded_dark_200", DARK, 2f, pace())

    private companion object {
        const val DARK = "w369dp-h905dp-port-night-420dpi"
    }
}
