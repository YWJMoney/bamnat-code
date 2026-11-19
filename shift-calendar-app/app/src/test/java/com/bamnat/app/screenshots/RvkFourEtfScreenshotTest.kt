package com.bamnat.app.screenshots

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.data.InvestRepository
import com.bamnat.app.data.PersonalCalendar
import com.bamnat.app.ui.invest.InvestSheet
import com.bamnat.app.ui.invest.InvestViewModel
import com.bamnat.core.invest.CashFlow
import com.bamnat.core.invest.CashKind
import com.bamnat.core.invest.InvestAccount
import com.bamnat.core.invest.Market
import com.bamnat.core.invest.Side
import com.bamnat.core.invest.Trade
import com.bamnat.core.invest.TradeDraft
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 2.0.0 갈래 K 4차 [RVK-31] (검토 MONEY-66·T06b-12·U06b-11): a 국내 ETF of a foreign index was told "양도소득세가 없어요" when sold.
 * The trade sheet now asks the ETF's kind once and says the sale's own line; the year's 세금 미리 보기 asks for the ETFs sold
 * without a kind and moves the gain to its line. Light·dark × 글자 100%·200% × 접은·편 폭. The code before drew the same trade sheet
 * with "국내 상장주식은 … 양도소득세가 없어요" and the tax card with the ETF's gain under 국내 주식. Amounts are made up.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RvkFourEtfScreenshotTest : ScreenshotBase() {
    /** TIGER 미국S&P500 bought and half sold this month, KODEX 200 bought and all sold: neither told its kind. */
    private fun book(): Long {
        val repo = InvestRepository.of(app.container.database)
        val start = today.withDayOfMonth(1)
        return runBlocking {
            val acc = repo.saveAccount(InvestAccount(name = "주식 계좌", broker = "한국투자증권"))
            repo.saveCash(CashFlow(accountId = acc, date = start, kind = CashKind.DEPOSIT, amount = 5_000_000))
            fun t(day: Int, symbol: String, name: String, side: Side, q: Double, p: Double) =
                Trade(accountId = acc, date = start.plusDays(day.toLong().coerceAtMost(today.dayOfMonth - 1L)), symbol = symbol, name = name,
                    market = Market.KR_ETF, side = side, quantity = q, price = p)
            listOf(
                t(0, "360750", "TIGER 미국S&P500", Side.BUY, 100.0, 18_500.0),
                t(0, "069500", "KODEX 200", Side.BUY, 20.0, 36_000.0),
                t(5, "360750", "TIGER 미국S&P500", Side.SELL, 50.0, 21_300.0),
                t(5, "069500", "KODEX 200", Side.SELL, 20.0, 38_150.0),
            ).forEach { repo.saveTrade(it) }
            acc
        }
    }

    private fun vm(): InvestViewModel {
        settle()
        return InvestViewModel(app.container, PersonalCalendar.MAIN)
    }

    /** Selling the other half: the sheet asks the kind, and picking it says the 배당소득세 the broker takes. */
    private fun sell(pick: Boolean): Screen.() -> Unit = style(WorkStyle.INVEST) {
        val acc = book()
        val vm = vm()
        content {
            com.bamnat.app.ui.invest.InvestSheetHost(vm, InvestSheet.TradeEdit(TradeDraft("360750", "TIGER 미국S&P500", Market.KR_ETF, Side.SELL,
                50.0, 21_600.0, accountId = acc)), {})
        }
        settle()
        assertTrue("ETF 종류를 묻는 칩", appears("ETF 종류", exact = true))
        assertFalse("국내 주식 문장이 ETF에 나오지 않아요", shows("양도소득세가 없어요", exact = false))
        if (pick) {
            tapText("해외지수·채권·원자재 등", exact = true)
            settle()
            reveal("어림 배당소득세", exact = false)
            assertTrue(appears("× 15.4%", exact = false))
        } else {
            reveal("위에서 종류를 골라 주세요", exact = false)
        }
    }

    /** 통계: the year's 세금 미리 보기 asks for both ETFs sold this year. */
    private fun taxCard(wide: Boolean): Screen.() -> Unit = style(WorkStyle.INVEST) {
        book()
        val vm = vm()
        content {
            androidx.compose.material3.Surface(color = androidx.compose.material3.MaterialTheme.colorScheme.background) {
                com.bamnat.app.ui.invest.InvestScreen(vm, wide = wide, snackbar = androidx.compose.runtime.remember { androidx.compose.material3.SnackbarHostState() })
            }
        }
        settle()
        tapText("통계", exact = true)
        settle()
        reveal("어떤 ETF예요?", exact = false)
        assertTrue(appears("TIGER 미국S&P500, 어떤 ETF예요?", exact = true))
        assertTrue(appears("KODEX 200, 어떤 ETF예요?", exact = true))
    }

    @Test fun sellAskLight() = shoot("rvk4_08_etf_sell_ask_folded", FOLDED, sell(pick = false))
    @Test fun sellPickedLight() = shoot("rvk4_09_etf_sell_picked_folded", FOLDED, sell(pick = true))
    @Test fun sellPickedDarkBig() = shoot("rvk4_10_etf_sell_picked_folded_dark_200", DARK, 2f, sell(pick = true))
    @Test fun taxCardLight() = shoot("rvk4_11_etf_tax_card_folded", FOLDED, taxCard(wide = false))
    @Test fun taxCardDarkBig() = shoot("rvk4_12_etf_tax_card_folded_dark_200", DARK, 2f, taxCard(wide = false))
    @Test fun taxCardWide() = shoot("rvk4_13_etf_tax_card_unfolded", UNFOLDED, taxCard(wide = true))

    private companion object {
        /** The folded phone at night, as [RvkTwoScreenshotTest] draws it. */
        const val DARK = "w369dp-h905dp-port-night-420dpi"
    }
}
