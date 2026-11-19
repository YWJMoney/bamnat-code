package com.bamnat.app.screenshots

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.data.InvestRepository
import com.bamnat.app.data.PersonalCalendar
import com.bamnat.app.ui.invest.InvestViewModel
import com.bamnat.core.invest.CashFlow
import com.bamnat.core.invest.CashKind
import com.bamnat.core.invest.DayGrade
import com.bamnat.core.invest.InvestAccount
import com.bamnat.core.invest.JournalEntry
import com.bamnat.core.invest.Market
import com.bamnat.core.invest.Side
import com.bamnat.core.invest.Trade
import com.bamnat.core.time.Today
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 2.0.0 갈래 K [RVK-57] (총괄 HQ-147, 조건 12): 투자·수익 › 기록 on a folded phone at 200% stood '매수' as '매/수' and cut '입금'
 * to '입'. From 150% a line is two rows: the word and the name above, how many and the amount below. The same shape in 가계부
 * 내역 and 일지 is shot too. The code before draws the same scenes. Amounts are made up. Today is 2026-10-14.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RvkHq147ScreenshotTest : ScreenshotBase() {
    private fun repo() = InvestRepository.of(app.container.database)

    private fun Screen.invest() {
        settle()
        val vm = InvestViewModel(app.container, PersonalCalendar.MAIN)
        content {
            androidx.compose.material3.Surface(color = androidx.compose.material3.MaterialTheme.colorScheme.background) {
                com.bamnat.app.ui.invest.InvestScreen(vm, wide = false, snackbar = androidx.compose.runtime.remember { androidx.compose.material3.SnackbarHostState() })
            }
        }
        settle()
    }

    /** 기록: 매수·매도 (a 실현 손익), a coin, 입금 and a 배당 with its tax. */
    private val records: Screen.() -> Unit = records(top = false)

    private fun records(top: Boolean): Screen.() -> Unit = style(WorkStyle.INVEST) {
        runBlocking {
            val acc = repo().saveAccount(InvestAccount(name = "투자 계좌", broker = "증권사"))
            val d = Today.date()
            repo().saveCash(CashFlow(accountId = acc, date = d.minusDays(9), kind = CashKind.DEPOSIT, amount = 12_000_000))
            repo().saveTrade(Trade(accountId = acc, date = d.minusDays(6), minute = 9 * 60 + 5, symbol = "005930", name = "삼성전자",
                market = Market.KR_STOCK, side = Side.BUY, quantity = 100.0, price = 71_500.0))
            repo().saveTrade(Trade(accountId = acc, date = d.minusDays(2), minute = 14 * 60 + 40, symbol = "005930", name = "삼성전자",
                market = Market.KR_STOCK, side = Side.SELL, quantity = 40.0, price = 74_200.0))
            repo().saveTrade(Trade(accountId = acc, date = d.minusDays(1), minute = 21 * 60, symbol = "BTC", name = "비트코인",
                market = Market.CRYPTO, side = Side.BUY, quantity = 0.01, price = 140_000_000.0))
            repo().saveCash(CashFlow(accountId = acc, date = d.minusDays(1), kind = CashKind.DIVIDEND, amount = 36_100, tax = 5_559, symbol = "삼성전자"))
        }
        invest()
        tapText("기록", exact = true)
        settle()
        if (top) {
            reveal("배당", exact = true)
        } else {
            reveal("입금", exact = true)
            reveal("매도", exact = true)
        }
    }

    /** 일지: a day's title beside its grade and 손익. */
    private val journal: Screen.() -> Unit = style(WorkStyle.INVEST) {
        runBlocking {
            repo().saveAccount(InvestAccount(name = "투자 계좌", broker = "증권사"))
            (1..3).forEach { i ->
                repo().saveJournal(JournalEntry(date = Today.date().minusDays(i.toLong()), market = "외국인 순매도, 환율 1,430원대",
                    review = "계획대로 분할 매수, 추격 매수는 참았어요", grade = DayGrade.entries.first()))
            }
        }
        invest()
        tapText("일지", exact = true)
        settle()
        reveal("복기", exact = false)
    }

    /** 가계부 › 내역: names, captions and amounts on one line each. */
    private val ledgerLines = style(WorkStyle.DAY, ledgerPage("내역", false) {
        settle()
        reveal("라마바전자", exact = false)
    })

    @Test fun recordsBig() = shoot("hq147_01_invest_records_folded_200", FOLDED, 2f, records)
    @Test fun recordsDarkBig() = shoot("hq147_02_invest_records_folded_dark_200", DARK, 2f, records)
    @Test fun recordsNormal() = shoot("hq147_03_invest_records_folded", FOLDED, records)
    @Test fun journalBig() = shoot("hq147_04_invest_journal_folded_200", FOLDED, 2f, journal)
    @Test fun ledgerBig() = shoot("hq147_05_ledger_lines_folded_200", FOLDED, 2f, ledgerLines)
    @Test fun dividendBig() = shoot("hq147_07_invest_records_coin_dividend_folded_200", FOLDED, 2f, records(top = true))
    @Test fun ledgerDarkBig() = shoot("hq147_06_ledger_lines_folded_dark_200", DARK, 2f, ledgerLines)

    private companion object {
        const val DARK = "w369dp-h905dp-port-night-420dpi"
    }
}
