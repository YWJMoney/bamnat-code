package com.bamnat.app.screenshots

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.data.InvestRepository
import com.bamnat.app.data.PersonalCalendar
import com.bamnat.app.ui.invest.InvestSheet
import com.bamnat.app.ui.invest.InvestViewModel
import com.bamnat.core.invest.CashFlow
import com.bamnat.core.invest.CashKind
import com.bamnat.core.invest.Emotion
import com.bamnat.core.invest.InvestAccount
import com.bamnat.core.invest.Market
import com.bamnat.core.invest.Quote
import com.bamnat.core.invest.Side
import com.bamnat.core.invest.Trade
import com.bamnat.core.invest.TradeDraft
import com.bamnat.core.work.WorkStyle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 2.0.0 갈래 K 3차: 현재가가 며칠 전 것인지(요약·종목 카드·현재가 창), 잔고 캡처로 현재가 채우기와 장부와 다른 수량 묻기, 수량 바뀜
 * (분할·병합·입고·출고) 창, 종목 한 장. Light·dark × 글자 100%·200% × 접은·편 폭, for the PR's before/after sheet. The overview, the
 * holdings and the trade sheet are drawn by the code before too; the new sheets have no 'before'. Amounts are made up.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RvkThreeScreenshotTest : ScreenshotBase() {
    /** 삼성전자 and 카카오 priced nine days ago, SK하이닉스 two; 네이버 sold out; 카카오 bought twice with notes. */
    private fun book(): Long {
        val repo = InvestRepository.of(app.container.database)
        return runBlocking {
            val acc = repo.saveAccount(InvestAccount(name = "주식 계좌", broker = "한국투자증권"))
            repo.saveCash(CashFlow(accountId = acc, date = today.minusDays(40), kind = CashKind.DEPOSIT, amount = 3_000_000))
            fun t(day: Long, symbol: String, name: String, side: Side, q: Double, p: Double, memo: String = "", emotions: Set<Emotion> = emptySet(),
                  rules: Boolean? = null) =
                Trade(accountId = acc, date = today.minusDays(day), symbol = symbol, name = name, side = side, quantity = q, price = p, memo = memo,
                    emotions = emotions, followedRules = rules)
            listOf(
                t(35, "035420", "네이버", Side.BUY, 2.0, 200_000.0),
                t(30, "005930", "삼성전자", Side.BUY, 10.0, 70_000.0, memo = "반도체 회복, 실적 발표 전에 나눠 사기"),
                t(28, "035720", "카카오", Side.BUY, 5.0, 55_000.0, memo = "광고 실적이 돌아선다는 기사", emotions = setOf(Emotion.CONFIDENT)),
                t(20, "000660", "SK하이닉스", Side.BUY, 3.0, 180_000.0),
                t(14, "035720", "카카오", Side.BUY, 2.0, 44_500.0, memo = "떨어져서 더 샀어요. 정한 날이 아니었어요", emotions = setOf(Emotion.ANXIOUS), rules = false),
                t(10, "035420", "네이버", Side.SELL, 2.0, 215_000.0),
            ).forEach { repo.saveTrade(it) }
            repo.saveCash(CashFlow(accountId = acc, date = today.minusDays(12), kind = CashKind.DIVIDEND, amount = 3_610, symbol = "005930"))
            repo.saveQuotes(listOf(
                Quote("005930", 73_400.0, today.minusDays(9), "삼성전자", Market.KR_STOCK),
                Quote("035720", 41_050.0, today.minusDays(9), "카카오", Market.KR_STOCK),
                Quote("000660", 182_300.0, today.minusDays(2), "SK하이닉스", Market.KR_STOCK),
            ))
            acc
        }
    }

    private fun vm(): InvestViewModel {
        settle()
        return InvestViewModel(app.container, PersonalCalendar.MAIN)
    }

    private fun screen(tab: String?, then: Screen.() -> Unit = {}): Screen.() -> Unit = style(WorkStyle.INVEST) {
        book()
        val vm = vm()
        content {
            androidx.compose.material3.Surface(color = androidx.compose.material3.MaterialTheme.colorScheme.background) {
                com.bamnat.app.ui.invest.InvestScreen(vm, wide = false, snackbar = androidx.compose.runtime.remember { androidx.compose.material3.SnackbarHostState() })
            }
        }
        settle()
        tab?.let { tapText(it, exact = true) }
        then()
    }

    private fun sheet(make: (Long) -> InvestSheet, then: Screen.() -> Unit = {}): Screen.() -> Unit = style(WorkStyle.INVEST) {
        val acc = book()
        val vm = vm()
        content { com.bamnat.app.ui.invest.InvestSheetHost(vm, make(acc), {}) }
        settle()
        then()
    }

    private fun capture(then: Screen.() -> Unit = {}): Screen.() -> Unit = style(WorkStyle.INVEST) {
        book()
        val vm = vm()
        content {
            val state = vm.state.collectAsStateWithLifecycle().value
            if (state.fullResult.held.isNotEmpty()) com.bamnat.app.ui.invest.QuoteSheet(vm, state, {}, {}, initialShot = SHOT)
        }
        settle()
        then()
    }

    @Test fun summaryLight() = shoot("rvk3_01_summary_price_age_folded", FOLDED, screen(null) {
        reveal("평가손익", exact = true)
        assertTrue(appears("9일 전 가격"))
    })
    @Test fun holdingsDarkBig() = shoot("rvk3_02_holdings_card_folded_dark_200", DARK, 2f, screen("종목") {
        tapText("카카오", exact = true)
        reveal("기록 한 장", exact = true)
    })
    @Test fun captureLight() = shoot("rvk3_03_quote_capture_folded", FOLDED, capture())
    // 2.0.0 [RVK-36] (총괄 HQ-63 ②) Three holdings differ from the book: one line to open, the prices on the first screen at 200%.
    @Test fun captureDarkBig() = shoot("rvk3_04_quote_capture_folded_dark_200", DARK, 2f, capture {
        assertTrue("다른 종목은 한 줄로", appears("장부와 다른 종목 3개", exact = true))
        assertTrue("물음은 접힌 채", !shows("분할·병합 적기", exact = true))
    })
    @Test fun captureOpened() = shoot("rvk3_11_quote_capture_opened_folded", FOLDED, capture {
        tapText("장부와 다른 종목 3개", exact = true)
        settle()
        assertTrue(appears("분할·병합 적기", exact = true))
    })
    @Test fun splitLight() = shoot("rvk3_05_quantity_split_folded", FOLDED, sheet({ acc ->
        InvestSheet.QuantityEdit(TradeDraft("005930", "삼성전자", Market.KR_STOCK, Side.SPLIT, 5.0, accountId = acc))
    }) { assertTrue(appears("평균 70,", exact = false)) })
    @Test fun splitDarkBig() = shoot("rvk3_06_quantity_split_folded_dark_200", DARK, 2f, sheet({ acc ->
        InvestSheet.QuantityEdit(TradeDraft("005930", "삼성전자", Market.KR_STOCK, Side.SPLIT, 5.0, accountId = acc))
    }))
    @Test fun sharesInWide() = shoot("rvk3_07_quantity_in_unfolded", UNFOLDED, sheet({ acc ->
        InvestSheet.QuantityEdit(TradeDraft("035720", "카카오", Market.KR_STOCK, Side.IN, 2.0, accountId = acc))
    }))
    @Test fun storyLight() = shoot("rvk3_08_story_folded", FOLDED, sheet({ InvestSheet.Story("035720") }) {
        assertTrue(appears("정한 날이 아니었어요", exact = false))
    })
    @Test fun storyDarkBig() = shoot("rvk3_09_story_folded_dark_200", DARK, 2f, sheet({ InvestSheet.Story("035720") }))
    @Test fun tradeSheetLight() = shoot("rvk3_10_trade_sheet_folded", FOLDED, sheet({ acc ->
        InvestSheet.TradeEdit(TradeDraft(side = Side.BUY, accountId = acc))
    }))

    private companion object {
        /** The folded phone at night, as [RvkTwoScreenshotTest] draws it. */
        const val DARK = "w369dp-h905dp-port-night-420dpi"
        /** A 잔고 screen in the card shape, written for this test: 삼성전자 split 1:5, 카카오 two more, 네이버 not in the book. */
        val SHOT = """
            총 평가금액 2,135,250원
            삼성전자(005930)
            보유수량 50주
            현재가 14,680원
            평가금액 734,000원
            카카오
            보유수량 9주
            현재가 41,500원
            평가금액 373,500원
            SK하이닉스
            보유수량 3주
            현재가 183,000원
            평가금액 549,000원
            네이버
            보유수량 1주
            현재가 210,000원
            평가금액 210,000원
        """.trimIndent()
    }
}
