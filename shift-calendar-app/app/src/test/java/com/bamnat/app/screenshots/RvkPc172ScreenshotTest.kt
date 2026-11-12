package com.bamnat.app.screenshots

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 2.0.0 갈래 K [RVK-49] (PC-172, 데이터): 가계부가 다음 달을 보고 있을 때 '돈 적기'가 그달 말일로 저장됐어요(팀원 링크로 다음 달 근무표를
 * 받아 시작한 직원 폰: '12월 31일 · -1,000원'). 이제 날짜 기본값은 늘 오늘이고, 보던 달 밖에 들어가면 어디에 적었는지 말해요. Today is 2026-10-14.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RvkPc172ScreenshotTest : ScreenshotBase() {
    private fun writeWhileAhead(months: Long, wide: Boolean = false): Screen.() -> Unit = style(WorkStyle.DAY) {
        tab(1, wide)
        app.container.shownMonth.show(java.time.YearMonth.from(today).plusMonths(months))
        settle()
        tapText("돈 적기", exact = true, scrolls = 0)
        assertTrue("시트의 날짜가 오늘", appears(com.bamnat.app.ui.Fmt.dayTitle(today), exact = false))
        typeInto("금액", "1000")
        tapText("저장", exact = true, scrolls = 0)
        settle()
        val saved = runBlocking { app.container.moneyRepository.allEntries().filter { it.amount == 1000L } }
        assertEquals("오늘에 적혀요(그달 말일이 아니라)", listOf(today), saved.map { it.date })
    }

    @Test fun nextMonth() = shoot("rvk_pc172_01_write_next_month_folded", FOLDED, writeWhileAhead(2))
    @Test fun lastMonth() = shoot("rvk_pc172_02_write_last_month_folded", FOLDED, writeWhileAhead(-1))
}
