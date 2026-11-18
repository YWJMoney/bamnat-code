package com.bamnat.app.screenshots

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.core.work.WorkStyle
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 2.0.0 갈래 K [RVK-55] (총괄 HQ-120, 조건 12): 가계부 › 통계를 편 폭·글자 200%로 보면 'CSV (무료)' 단추 글자가 세로로 서고
 * '10월 한눈에' 금액이 숫자 가운데에서 줄이 바뀌던 것. 금액 타일은 글자 150% 이상에서 하나씩 아래로 쌓이고(StatTiles), 단추 줄은
 * 좁으면 아래로 내려가요. 같은 꼴인 투자 한눈에 타일도 찍어요. The code before draws the same scenes. Today is 2026-10-14.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RvkHq120ScreenshotTest : ScreenshotBase() {
    /** 통계 › '10월 한눈에' (들어온 돈·나간 돈·남은 돈). */
    private fun overview(wide: Boolean): Screen.() -> Unit = style(WorkStyle.DAY) {
        ledger()
        tab(1, wide)
        tapText("통계", exact = true, scrolls = 0)
        settle()
        reveal("한눈에", exact = false)
    }

    /** 통계 맨 아래 '○년 가계부 리포트': PDF·CSV 단추 둘. */
    private fun report(wide: Boolean): Screen.() -> Unit = style(WorkStyle.DAY) {
        ledger()
        tab(1, wide)
        tapText("통계", exact = true, scrolls = 0)
        settle()
        // On the wide screen the report is at the foot of the right-hand column, which scrolls by itself.
        val across = if (wide) 0.75f else 0.5f
        reveal("가계부 리포트", exact = false, across = across)
        reveal("CSV", exact = false, across = across)
    }

    /** 투자 › 한눈에의 원금·현금·평가손익 타일. */
    private val invest: Screen.() -> Unit = style(WorkStyle.INVEST) {
        val repo = com.bamnat.app.data.InvestRepository.of(app.container.database)
        kotlinx.coroutines.runBlocking {
            val acc = repo.saveAccount(com.bamnat.core.invest.InvestAccount(name = "투자 계좌", broker = "증권사"))
            repo.saveCash(com.bamnat.core.invest.CashFlow(accountId = acc, date = today.minusDays(20), kind = com.bamnat.core.invest.CashKind.DEPOSIT,
                amount = 12_345_000))
            repo.saveTrade(com.bamnat.core.invest.Trade(accountId = acc, date = today.minusDays(5), symbol = "005930", name = "삼성전자",
                market = com.bamnat.core.invest.Market.KR_STOCK, side = com.bamnat.core.invest.Side.BUY, quantity = 100.0, price = 71_500.0))
        }
        settle()
        val vm = com.bamnat.app.ui.invest.InvestViewModel(app.container, com.bamnat.app.data.PersonalCalendar.MAIN)
        content {
            androidx.compose.material3.Surface(color = androidx.compose.material3.MaterialTheme.colorScheme.background) {
                com.bamnat.app.ui.invest.InvestScreen(vm, wide = false, snackbar = androidx.compose.runtime.remember { androidx.compose.material3.SnackbarHostState() })
            }
        }
        settle()
        reveal("원금", exact = true)
    }

    @Test fun overviewWideBig() = shoot("hq120_01_stats_overview_unfolded_200", UNFOLDED, 2f, overview(wide = true))
    @Test fun overviewFoldedDarkBig() = shoot("hq120_02_stats_overview_folded_dark_200", DARK, 2f, overview(wide = false))
    @Test fun reportWideBig() = shoot("hq120_03_stats_report_unfolded_200", UNFOLDED, 2f, report(wide = true))
    @Test fun reportFolded() = shoot("hq120_04_stats_report_folded", FOLDED, report(wide = false))
    @Test fun overviewFolded() = shoot("hq120_05_stats_overview_folded", FOLDED, overview(wide = false))
    @Test fun investBig() = shoot("hq120_06_invest_tiles_folded_200", FOLDED, 2f, invest)

    private companion object {
        const val DARK = "w369dp-h905dp-port-night-420dpi"
    }
}
