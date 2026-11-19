package com.bamnat.app.screenshots

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 2.0.0 갈래 K [RVK-59] (총괄 HQ-151, 조건 12): 가계부 › 통계 › 분류별 지출 on a folded phone at 200% cut the names to two letters
 * ('일감…', '장보…'): name, %, amount and ▲▼ on one line. The name keeps its width now, the amount beside it while both fit, else under
 * it at the right. The same row is 결제 수단·자주 간 곳·태그. The code before draws the same scenes. Today is 2026-10-14.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RvkHq151ScreenshotTest : ScreenshotBase() {
    private fun categories(wide: Boolean, more: Boolean = false): Screen.() -> Unit = style(WorkStyle.FIELD) {
        ledger()
        runBlocking {
            val money = app.container.moneyRepository
            val d = today.minusDays(2)
            money.save(com.bamnat.core.money.MoneyEntry(date = d, kind = com.bamnat.core.money.MoneyKind.EXPENSE, amount = 184_300,
                category = "장보기·생활용품", merchant = "동네마트"))
            money.save(com.bamnat.core.money.MoneyEntry(date = d, kind = com.bamnat.core.money.MoneyKind.EXPENSE, amount = 1_236_000,
                category = "자재·공구비", merchant = "철물점"))
        }
        settle()
        tab(1, wide)
        tapText("통계", exact = true, scrolls = 0)
        settle()
        reveal("분류별 지출", exact = true)
        reveal(if (more) "▲▼는" else "자재·공구비", exact = false)
    }

    @Test fun foldedBig() = shoot("hq151_01_stats_categories_folded_200", FOLDED, 2f, categories(wide = false))
    @Test fun foldedDarkBig() = shoot("hq151_02_stats_categories_folded_dark_200", DARK, 2f, categories(wide = false, more = true))
    @Test fun folded() = shoot("hq151_03_stats_categories_folded", FOLDED, categories(wide = false))
    @Test fun wideBig() = shoot("hq151_04_stats_categories_unfolded_200", UNFOLDED, 2f, categories(wide = true))

    private companion object {
        const val DARK = "w369dp-h905dp-port-night-420dpi"
    }
}
