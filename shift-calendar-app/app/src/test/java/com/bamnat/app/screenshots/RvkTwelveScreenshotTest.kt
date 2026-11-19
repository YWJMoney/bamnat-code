package com.bamnat.app.screenshots

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.data.InvestRepository
import com.bamnat.app.data.PersonalCalendar
import com.bamnat.app.ui.invest.InvestSheet
import com.bamnat.app.ui.invest.InvestViewModel
import com.bamnat.core.invest.AlarmKind
import com.bamnat.core.invest.CashFlow
import com.bamnat.core.invest.CashKind
import com.bamnat.core.invest.InvestAccount
import com.bamnat.core.invest.InvestAlarm
import com.bamnat.core.invest.JournalEntry
import com.bamnat.core.invest.Market
import com.bamnat.core.invest.Side
import com.bamnat.core.invest.Trade
import com.bamnat.core.time.Today
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 2.0.0 갈래 K 12차 [RVK-52·53·54] (검토 MONEY-72·T06b-10·MONEY-71·MONEY-76): on a Saturday a coin-only investor is not told
 * '국내 증시가 쉬는 날', a coin plan has no 휴장 switch and a US plan skips the NYSE's own days; 금액 가리기 from the app hides
 * 수량 × 단가 in the 투자 records too; a day of many 단타 draws thirty lines and '나머지 N건 보기'. Amounts are made up.
 * The code before said '오늘은 국내 증시가 쉬는 날이에요' to coins only, showed '주말·공휴일·휴장일은 건너뛰기' on a coin plan,
 * and drew '10주 × 71,500원' with 가리기 on.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RvkTwelveScreenshotTest : ScreenshotBase() {
    private fun repo() = InvestRepository.of(app.container.database)

    private fun account(): Long = runBlocking {
        val acc = repo().saveAccount(InvestAccount(name = "투자 계좌", broker = "업비트"))
        repo().saveCash(CashFlow(accountId = acc, date = Today.date().minusDays(10), kind = CashKind.DEPOSIT, amount = 5_000_000))
        acc
    }

    private fun trade(acc: Long, daysAgo: Long, symbol: String, name: String, market: Market, side: Side, q: Double, p: Double, minute: Int? = null) =
        runBlocking { repo().saveTrade(Trade(accountId = acc, date = Today.date().minusDays(daysAgo), minute = minute, symbol = symbol, name = name, market = market,
            side = side, quantity = q, price = p)) }

    private fun vm(): InvestViewModel {
        settle()
        return InvestViewModel(app.container, PersonalCalendar.MAIN)
    }

    private fun Screen.screen(wide: Boolean = false) {
        val vm = vm()
        content {
            androidx.compose.material3.Surface(color = androidx.compose.material3.MaterialTheme.colorScheme.background) {
                com.bamnat.app.ui.invest.InvestScreen(vm, wide = wide, snackbar = androidx.compose.runtime.remember { androidx.compose.material3.SnackbarHostState() })
            }
        }
        settle()
    }

    /** 한눈에 › 오늘 on a Saturday: coins only say nothing about 휴장; with a Korean share, the line is there. */
    private fun today(coinsOnly: Boolean): Screen.() -> Unit = style(WorkStyle.INVEST) {
        // The base class puts its own day back for every test, so the Saturday is set here, before the data and the screen.
        Today.fix(SATURDAY)
        val acc = account()
        trade(acc, 1, "BTC", "비트코인", Market.CRYPTO, Side.BUY, 0.01, 140_000_000.0)
        trade(acc, 0, "ETH", "이더리움", Market.CRYPTO, Side.BUY, 0.2, 5_200_000.0, minute = 9 * 60)
        if (!coinsOnly) trade(acc, 2, "005930", "삼성전자", Market.KR_STOCK, Side.BUY, 10.0, 71_500.0)
        screen()
        reveal("오늘 · 11월 21일 (토)", exact = false)
        if (coinsOnly) assertFalse("코인만 하는 사람에게 휴장 문구 없음", shows("쉬는 날", exact = false))
        else assertTrue(appears("오늘은 국내 증시가 쉬는 날이에요", exact = true))
    }

    /** The alarm editor for a plan: a coin has no 휴장 switch; a US share skips the NYSE's own days. */
    private fun plan(market: Market): Screen.() -> Unit = style(WorkStyle.INVEST) {
        account()
        val alarm = if (market == Market.CRYPTO) InvestAlarm(kind = AlarmKind.PLAN, minute = 9 * 60 + 10, monthDay = 25, symbol = "BTC",
            name = "비트코인", market = market, amount = 100_000)
        else InvestAlarm(kind = AlarmKind.PLAN, minute = 22 * 60 + 40, monthDay = 25, symbol = "AAPL", name = "애플", market = market, quantity = 1.0)
        val id = runBlocking { repo().saveAlarm(alarm) }
        val vm = vm()
        content { com.bamnat.app.ui.invest.InvestSheetHost(vm, InvestSheet.AlarmEdit(editing = alarm.copy(id = id)), {}) }
        settle()
        if (market == Market.CRYPTO) {
            assertFalse("코인은 쉬는 날이 없어요", shows("휴장일", exact = false))
        } else {
            reveal("미국 증시 휴장일이면 다음 장날에", exact = true)
        }
    }

    /** 기록 with the app's 금액 가리기 on: 수량 × 단가 are dots too. */
    private val hiddenRecords: Screen.() -> Unit = style(WorkStyle.INVEST) {
        val acc = account()
        trade(acc, 1, "005930", "삼성전자", Market.KR_STOCK, Side.BUY, 10.0, 71_500.0, minute = 9 * 60 + 12)
        trade(acc, 1, "BTC", "비트코인", Market.CRYPTO, Side.BUY, 0.01, 140_000_000.0, minute = 21 * 60)
        app.container.settingsRepository.update { it.copy(hideAmounts = true) }
        screen()
        tapText("기록", exact = true)
        settle()
        reveal("삼성전자", exact = true)
        assertFalse("가리면 단가도 안 보여요", shows("71,500", exact = false))
        assertTrue(shows("•••주 × •••••", exact = false))
    }

    /** A day of forty 단타: thirty lines, then '나머지 10건 보기'. */
    private fun busyDay(wide: Boolean): Screen.() -> Unit = style(WorkStyle.INVEST) {
        val acc = account()
        (0 until 40).forEach { i ->
            trade(acc, 1, "035720", "카카오", Market.KR_STOCK, if (i % 2 == 0) Side.BUY else Side.SELL, 10.0, 41_000.0 + (i % 7) * 50, minute = 9 * 60 + i * 5)
        }
        screen(wide)
        tapText("기록", exact = true)
        settle()
        reveal("나머지 10건 보기", exact = true)
    }

    /** Forty-five days of 일지: thirty, then '지난 일지 15일 더 보기' (before, sixty and no way to the rest). */
    private val longJournal: Screen.() -> Unit = style(WorkStyle.INVEST) {
        account()
        runBlocking {
            (1..45).forEach { i ->
                repo().saveJournal(JournalEntry(date = Today.date().minusDays(i.toLong()), market = "외국인 순매도, 환율 1,4${i % 10}0원대", review = "계획대로 분할 매수"))
            }
        }
        screen()
        tapText("일지", exact = true)
        settle()
        reveal("지난 일지 15일 더 보기", exact = true)
    }

    @Test fun coinsOnlySaturday() = shoot("rvk12_01_coins_only_saturday_folded", FOLDED, today(coinsOnly = true))
    @Test fun sharesSaturday() = shoot("rvk12_02_shares_saturday_folded", FOLDED, today(coinsOnly = false))
    @Test fun coinPlan() = shoot("rvk12_03_coin_plan_no_holiday_folded", FOLDED, plan(Market.CRYPTO))
    @Test fun usPlanDarkBig() = shoot("rvk12_04_us_plan_nyse_folded_dark_200", DARK, 2f, plan(Market.US_STOCK))
    @Test fun hidden() = shoot("rvk12_05_records_hidden_folded", FOLDED, hiddenRecords)
    @Test fun hiddenDarkBig() = shoot("rvk12_06_records_hidden_folded_dark_200", DARK, 2f, hiddenRecords)
    @Test fun busy() = shoot("rvk12_07_busy_day_folded", FOLDED, busyDay(wide = false))
    @Test fun journal() = shoot("rvk12_09_journal_more_folded", FOLDED, longJournal)
    @Test fun busyWide() = shoot("rvk12_08_busy_day_unfolded", UNFOLDED, busyDay(wide = true))

    companion object {
        const val DARK = "w369dp-h905dp-port-night-420dpi"

        /** A Saturday (2026-11-21): the Korean market is shut, coins are not. */
        val SATURDAY: java.time.ZonedDateTime = java.time.ZonedDateTime.of(2026, 11, 21, 10, 30, 0, 0, java.time.ZoneId.of("Asia/Seoul"))
    }
}
