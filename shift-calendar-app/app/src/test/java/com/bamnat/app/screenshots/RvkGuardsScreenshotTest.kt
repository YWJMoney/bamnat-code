package com.bamnat.app.screenshots

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.core.work.WorkStyle
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 2.0.0 갈래 K 4·7차 [RVK-04]·[RVK-07] (검토 MONEY-27·U06a-02, MONEY-09) The guards on what is being typed, kept by a test: 저장하고 하나 더
 * starts on the day of the line just saved, a 돈 적기 closed without saving brings back what was typed, and 받을 돈 적기 and
 * 결제 문자 붙여넣기 ask before a tap outside or the X throws what was typed away. Today is 2026-10-14. Names and amounts are made up.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RvkGuardsScreenshotTest : ScreenshotBase() {
    private val ask = "적은 내용을 버릴까요?"

    /** MONEY-27: the day picked for one line is where 저장하고 하나 더 starts the next. */
    @Test fun saveAndNextStartsOnTheDayJustSaved() = shoot("rvk4g_01_save_and_next_day_folded", FOLDED, style(WorkStyle.DAY, ledgerPage(null, false) {
        settle()
        val yesterday = com.bamnat.app.ui.Fmt.dayTitle(today.minusDays(1))
        tapText("돈 적기", exact = true, scrolls = 0)
        settle()
        tapText("어제", exact = true)
        typeInto("금액", "5000")
        assertTrue("어제로 바뀌어요", appears(yesterday, exact = false))
        tapText("저장하고 하나 더", exact = true)
        settle()
        assertTrue("다시 돈 적기가 열려요", appears("돈 적기", exact = true))
        assertTrue("하나 더도 어제부터 시작해요", appears(yesterday, exact = false))
    }))

    /** MONEY-27: a 돈 적기 closed by its X keeps what was typed for the next one (swipe and 뒤로 ask first, as FormSheet does). */
    @Test fun aLineClosedWithoutSavingComesBack() = shoot("rvk4g_02_entry_draft_comes_back_folded", FOLDED, style(WorkStyle.DAY, ledgerPage(null, false) {
        settle()
        tapText("돈 적기", exact = true, scrolls = 0)
        settle()
        typeInto("어디서·무엇 (선택)", "단골 분식")
        tapText("닫기", exact = true)
        settle()
        assertTrue("닫혀요", gone("어디서·무엇 (선택)"))
        tapText("돈 적기", exact = true, scrolls = 0)
        settle()
        assertTrue("적던 말이 다음 돈 적기에 그대로 와요", appears("단골 분식", exact = false))
    }))

    /** MONEY-09: 받을 돈 적기 asks before what was typed is lost. */
    @Test fun owedAddAsksBeforeItGoes() = shoot("rvk7g_01_owed_add_close_asks_folded", FOLDED, owed(WorkStyle.BUSINESS) {
        tapText("받을 돈 적기", exact = true)
        settle()
        typeInto("누가 (손님·거래처·사람)", "새 거래처")
        tapText("닫기", exact = true)
        settle()
        assertTrue("묻고 닫아요", appears(ask))
        tapText("계속 적기", exact = true)
        settle()
        assertTrue("묻기는 닫혀요", gone(ask))
        assertTrue("적던 말이 그대로예요", shows("새 거래처", exact = false))
    })

    /** MONEY-09: 결제 문자 붙여넣기 with texts pasted asks before it goes. */
    @Test fun pastedTextsAskBeforeTheyGo() = shoot("rvk7g_02_paste_close_asks_folded", FOLDED) {
        content {
            com.bamnat.app.ui.money.PasteDialog(emptyList(), emptyMap(), onSave = {}, onDismiss = {}, initialText =
                "[Web발신]\n현대카드(3456)승인 홍*동 45,000원 3개월 10/14 19:20 쿠팡")
        }
        settle()
        tapText("닫기", exact = true)
        settle()
        assertTrue("묻고 닫아요", appears(ask))
        tapText("계속 적기", exact = true)
        settle()
        assertTrue("묻기는 닫혀요", gone(ask))
    }
}
