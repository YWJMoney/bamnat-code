package com.bamnat.app.screenshots

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.core.money.MoneyKind
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 2.0.0 갈래 K 4차 (총괄 #472 사진 검토 HQ-44·45): 가계부 요약 맨 위 카드의 설명 문단 셋은 한 줄 요약 + ⓘ 하나로, 셋은 ⓘ 시트로
 * (조건 1·4), 오늘 쓸 돈·예산 줄의 주의는 한 색·한 모양으로(조건 6). Light·dark × 글자 100%·200% × 접은·편 폭, for the PR's before/
 * after sheet: the scenes are the ones #472 drew (k2-01·k2-02), so the same test on the code before draws the 'before'.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RvkFourScreenshotTest : ScreenshotBase() {
    /** The seed's month with pay on the 25th and 월급날 기준: 가게 경비, 안 받은 일감, '앞으로의 돈' and repeating costs all at once. */
    private fun everything(wide: Boolean = false, then: Screen.() -> Unit = {}): Screen.() -> Unit = style(WorkStyle.SHIFT) {
        runBlocking { app.container.payStore.save(closeOn20(com.bamnat.app.ui.pay.PayViewModel(app.container).sampleProfile()), keepUndo = false) }
        app.container.settingsRepository.update { it.copy(moneyByPayCycle = true) }
        ledgerPage(null, wide).invoke(this)
        settle()
        then()
    }

    /** Someone who writes only spending, past today's share and heading over the month's budget. */
    private fun overToday(then: Screen.() -> Unit = {}): Screen.() -> Unit = style(WorkStyle.DAY) {
        ledger()
        runBlocking {
            val money = app.container.moneyRepository
            money.allEntries().filter { it.kind == MoneyKind.INCOME }.forEach { money.delete(it) }
            money.allJobs().forEach { money.delete(it) }
            (-90L..30L).forEach { money.clearSales(today.plusDays(it)) }
            app.container.payStore.save(null, keepUndo = false)
        }
        settle()
        tab(1, wide = false)
        then()
    }

    /** HQ-44: the card keeps the figures; the three paragraphs are one line with ⓘ, and the sentences wait in its sheet. */
    private fun noParagraphs(): Screen.() -> Unit = {
        assertTrue("한 줄 요약", appears("가게 경비·안 받은 일감은 따로 셌어요", exact = true))
        assertFalse("카드 안 설명 문단", shows("나간 돈이 아니라", exact = false))
        assertFalse("카드 안 설명 문단", shows("돈 받음을 켜면", exact = false))
        assertFalse("카드 안 설명 문단", shows("잔액으로 셈해요", exact = false))
    }

    /** HQ-45: '오늘 몫보다 … 더 썼어요' is said as 주의 (the mark reads '주의'), not in a red of its own. */
    private fun saysCaution(): Screen.() -> Unit = {
        val line = nodes().firstOrNull { n -> n.config.getOrElse(SemanticsProperties.Text) { emptyList() }.any { "오늘 몫보다" in it.text } }
        assertTrue("오늘 몫보다 줄", line != null)
        val said = line!!.config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() }
        assertTrue("주의로 읽혀요: $said", "주의" in said)
    }

    @Test fun summaryLight() = shoot("rvk4_01_summary_notes_folded", FOLDED, everything(then = noParagraphs()))
    @Test fun summaryDarkBig() = shoot("rvk4_02_summary_notes_folded_dark_200", DARK, 2f, everything(then = noParagraphs()))
    @Test fun summaryWide() = shoot("rvk4_03_summary_notes_unfolded", UNFOLDED, everything(wide = true) {
        // The same shape in '앞으로의 돈': the sum is one line with ⓘ, not four grey lines.
        assertTrue(appears("일로 나눴어요", exact = false))
        assertFalse(shows("그 전에 나갈 돈", exact = false))
    })
    @Test fun notesSheetLight() = shoot("rvk4_04_summary_notes_sheet_folded", FOLDED, everything {
        tapText("가게 경비·안 받은 일감은 따로 셌어요", exact = true)
        assertTrue(appears("잔액으로 셈해요", exact = false))
    })
    @Test fun notesSheetDarkBig() = shoot("rvk4_05_summary_notes_sheet_folded_dark_200", DARK, 2f, everything {
        tapText("가게 경비·안 받은 일감은 따로 셌어요", exact = true)
        assertTrue(appears("나간 돈이 아니라", exact = false))
    })
    @Test fun overLight() = shoot("rvk4_06_summary_over_today_folded", FOLDED, overToday(saysCaution()))
    @Test fun overDarkBig() = shoot("rvk4_07_summary_over_today_folded_dark_200", DARK, 2f, overToday(saysCaution()))

    private companion object {
        /** The folded phone at night, as [RvkTwoScreenshotTest] draws it. */
        const val DARK = "w369dp-h905dp-port-night-420dpi"
    }
}
