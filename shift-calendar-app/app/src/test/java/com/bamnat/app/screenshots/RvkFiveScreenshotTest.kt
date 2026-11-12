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
import com.bamnat.core.invest.Quote
import com.bamnat.core.invest.Side
import com.bamnat.core.invest.Snapshot
import com.bamnat.core.invest.Trade
import com.bamnat.core.invest.TradeDraft
import com.bamnat.core.work.WorkStyle
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/**
 * 2.0.0 갈래 K 5차 [RVK-34] (검토 U06b-03·05·06·07·08): 투자 통계는 핵심 4칸 + '더 보기'(접은 폭·큰 글자 2칸), 월별 손익의 달 이름과
 * 요일별 손익이 큰 글자에서 두 줄이 되지 않게, 총자산 흐름을 누르면 그날 값, 매매 창은 이유·마음을 한 줄로 접고, 종목 카드의 배당·목표가
 * 알람은 그 종목을 채워 열고, ⋮ 메뉴는 여섯 줄. Light·dark × 글자 100%·200% × 접은·편 폭. The code before draws the same scenes (the
 * PR's 'before'). Amounts are made up.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RvkFiveScreenshotTest : ScreenshotBase() {
    /** A year of 삼성전자·카카오·SK하이닉스: bought early, sold a little on each weekday, priced yesterday, a value kept each other month. */
    private fun book(): Long {
        val repo = InvestRepository.of(app.container.database)
        return runBlocking {
            val acc = repo.saveAccount(InvestAccount(name = "주식 계좌", broker = "한국투자증권"))
            repo.saveCash(CashFlow(accountId = acc, date = LocalDate.of(2026, 1, 2), kind = CashKind.DEPOSIT, amount = 10_000_000))
            fun t(m: Int, d: Int, symbol: String, name: String, side: Side, q: Double, p: Double) =
                Trade(accountId = acc, date = LocalDate.of(2026, m, d), symbol = symbol, name = name, side = side, quantity = q, price = p)
            listOf(
                t(1, 5, "005930", "삼성전자", Side.BUY, 30.0, 55_000.0),
                t(2, 3, "035720", "카카오", Side.BUY, 20.0, 48_000.0),
                t(3, 4, "005930", "삼성전자", Side.SELL, 10.0, 61_000.0),
                t(4, 9, "035720", "카카오", Side.SELL, 10.0, 44_000.0),
                t(5, 8, "000660", "SK하이닉스", Side.BUY, 5.0, 170_000.0),
                t(6, 15, "000660", "SK하이닉스", Side.SELL, 2.0, 191_000.0),
                t(7, 7, "005930", "삼성전자", Side.SELL, 5.0, 66_000.0),
                t(8, 20, "035720", "카카오", Side.SELL, 5.0, 41_000.0),
                t(9, 18, "000660", "SK하이닉스", Side.SELL, 1.0, 230_000.0),
                t(10, 7, "005930", "삼성전자", Side.SELL, 5.0, 79_600.0),
            ).forEach { repo.saveTrade(it) }
            repo.saveQuotes(listOf(
                Quote("005930", 78_900.0, today.minusDays(1), "삼성전자", Market.KR_STOCK),
                Quote("035720", 42_300.0, today.minusDays(1), "카카오", Market.KR_STOCK),
                Quote("000660", 236_500.0, today.minusDays(1), "SK하이닉스", Market.KR_STOCK),
            ))
            listOf(
                Snapshot(LocalDate.of(2026, 1, 30), 9_870_000, 10_000_000),
                Snapshot(LocalDate.of(2026, 3, 31), 10_140_000, 10_000_000),
                Snapshot(LocalDate.of(2026, 5, 29), 9_960_000, 10_000_000),
                Snapshot(LocalDate.of(2026, 7, 31), 10_420_000, 10_000_000),
                Snapshot(LocalDate.of(2026, 9, 30), 10_610_000, 10_000_000),
                Snapshot(today.minusDays(1), 10_790_000, 10_000_000),
            ).forEach { repo.saveSnapshot(it) }
            acc
        }
    }

    private fun vm(): InvestViewModel {
        settle()
        return InvestViewModel(app.container, PersonalCalendar.MAIN)
    }

    private fun screen(wide: Boolean = false, then: Screen.() -> Unit): Screen.() -> Unit = style(WorkStyle.INVEST) {
        book()
        val vm = vm()
        content {
            androidx.compose.material3.Surface(color = androidx.compose.material3.MaterialTheme.colorScheme.background) {
                com.bamnat.app.ui.invest.InvestScreen(vm, wide = wide, snackbar = androidx.compose.runtime.remember { androidx.compose.material3.SnackbarHostState() })
            }
        }
        settle()
        then()
    }

    private fun sheet(make: (Long) -> InvestSheet, then: Screen.() -> Unit = {}): Screen.() -> Unit = style(WorkStyle.INVEST) {
        val acc = book()
        val vm = vm()
        content { com.bamnat.app.ui.invest.InvestSheetHost(vm, make(acc), {}) }
        settle()
        then()
    }

    /** 통계 › 연: the year's figures (four first), the months' bars and the weekdays. */
    private fun stats(wide: Boolean = false, below: String? = null) = screen(wide) {
        tapText("통계", exact = true)
        settle()
        tapText("연", exact = true)
        settle()
        assertTrue("핵심 4칸 뒤 '더 보기'", appears("개 더 보기", exact = false))
        below?.let { reveal(it, exact = true) }
    }

    /** 종목 › 삼성전자 › [chip]: the sheet opens with 삼성전자 in it. */
    private fun fromCard(chip: String, then: Screen.() -> Unit = {}) = screen {
        tapText("종목", exact = true)
        settle()
        tapText("삼성전자", exact = true)
        settle()
        tapText(chip, exact = true)
        settle()
        then()
    }

    @Test fun statsLight() = shoot("rvk5_01_stats_year_folded", FOLDED, stats(below = "2026년 월별 손익"))
    @Test fun statsDarkBig() = shoot("rvk5_02_stats_year_folded_dark_200", DARK, 2f, stats(below = "요일별 손익"))
    @Test fun statsWide() = shoot("rvk5_03_stats_year_unfolded", UNFOLDED, stats(wide = true))
    @Test fun historyTapped() = shoot("rvk5_04_history_tap_folded", FOLDED, screen {
        reveal("총자산 흐름", exact = true)
        tapText("까지 총자산", exact = false, scrolls = 0)
        settle()
        assertTrue("누른 날의 총자산·원금", appears("· 원금 ", exact = false))
    })
    @Test fun tradeSheetLight() = shoot("rvk5_05_trade_sheet_folded", FOLDED, sheet({ acc ->
        InvestSheet.TradeEdit(TradeDraft(side = Side.BUY, accountId = acc))
    }) { assertTrue(appears("이유·마음·메모 적기", exact = true)) })
    @Test fun tradeSheetDarkBig() = shoot("rvk5_06_trade_sheet_folded_dark_200", DARK, 2f, sheet({ acc ->
        InvestSheet.TradeEdit(TradeDraft(side = Side.BUY, accountId = acc))
    }))
    @Test fun dividendFromCard() = shoot("rvk5_07_dividend_from_card_folded", FOLDED, fromCard("배당") {
        assertTrue("배당 창에 종목이 채워져요", appears("005930", exact = false))
    })
    @Test fun alarmFromCardDarkBig() = shoot("rvk5_08_alarm_from_card_folded_dark_200", DARK, 2f, fromCard("목표가 알람") {
        assertTrue("알람 창에 종목이 채워져요", appears("삼성전자", exact = false))
    })
    /**
     * ⋮ › [item]. The menu is a popup the shot does not draw, so its six lines are checked through the tree (CSV 다섯 줄은 시트 안으로)
     * and the sheet it opens is the picture.
     */
    private fun fromMenu(item: String, wide: Boolean = false, then: Screen.() -> Unit) = screen(wide) {
        tapText("더 보기", exact = true, scrolls = 0)
        settle()
        compose.onNodeWithText("CSV로 내보내기").assertExists()
        compose.onNodeWithText("체결 알림·캡처 설정").assertExists()
        assertTrue("CSV 다섯 줄은 시트 안으로", compose.onAllNodesWithText("매매 기록 CSV로 내보내기").fetchSemanticsNodes().isEmpty())
        assertTrue("켜기·끄기 줄도 시트 안으로", compose.onAllNodesWithText("체결 알림 오면 바로 적기").fetchSemanticsNodes().isEmpty())
        compose.onNodeWithText(item).performClick()
        settle()
        then()
    }

    @Test fun fillSettingsLight() = shoot("rvk5_09_fill_settings_folded", FOLDED, fromMenu("체결 알림·캡처 설정") {
        assertTrue(appears("캡처로 채울 때 넣기 전에 묻기", exact = true))
    })
    @Test fun fillSettingsDarkBig() = shoot("rvk5_10_fill_settings_folded_dark_200", DARK, 2f, fromMenu("체결 알림·캡처 설정") {
        assertTrue(appears("체결 알림 오면 바로 적기", exact = true))
    })
    @Test fun csvWide() = shoot("rvk5_11_csv_sheet_unfolded", UNFOLDED, fromMenu("CSV로 내보내기", wide = true) {
        assertTrue(appears("해외 주식 세금 신고 자료", exact = true))
    })

    /**
     * [RVK-35] (PC-126) 가게의 장부 › 내역, nothing written yet: one '돈 적기' (the floating one), the ⋮ the words point to, the period
     * chips all seen, and the menu read as '장부 메뉴'.
     */
    private fun shopLedgerEmpty(wide: Boolean = false) = style(WorkStyle.BUSINESS) {
        // A shop that has written nothing yet (the shots' seed has a few lines).
        runBlocking { app.container.moneyRepository.let { m -> m.allEntries().forEach { m.delete(it) } } }
        settle()
        tab(1, wide)
        tapText("내역", exact = true, scrolls = 0)
        settle()
        assertTrue(appears("아직 적은 돈이 없어요", exact = true))
        assertTrue("'돈 적기'는 떠 있는 단추 하나", compose.onAllNodesWithText("돈 적기").fetchSemanticsNodes().size <= 1)
        compose.onNodeWithContentDescription("장부 메뉴").assertExists()
        compose.onNodeWithContentDescription("기능·설정 찾기").assertExists()
        assertTrue("'전체' 칩이 보여요", shows("전체", exact = true))
        // At 200% the month is '10월', so 나 is not pushed past the edge.
        assertTrue("'나'가 머리에 보여요", shows(ME, exact = true))
    }

    @Test fun shopLedgerEmptyLight() = shoot("rvk5_12_shop_ledger_empty_folded", FOLDED, shopLedgerEmpty())
    @Test fun shopLedgerEmptyDarkBig() = shoot("rvk5_13_shop_ledger_empty_folded_dark_200", DARK, 2f, shopLedgerEmpty())
    /** [RVK-35] (총괄 #477 사진) 가계부 › 내역 › 달력 at 200%: a week's days as tall as its tallest. */
    @Test fun ledgerCalendarDarkBig() = shoot("rvk5_14_ledger_calendar_folded_dark_200", DARK, 2f, ledgerPage("내역") {
        tapText("달력", exact = true, scrolls = 3)
        settle()
        // '18.3만' cut to '18.' in a 200% cell: the month's amounts are − and + with the words under the month, none cut.
        assertTrue("금액 대신 −·+와 그 뜻", compose.onAllNodesWithText("− 나간 돈 · + 들어온 돈", substring = true).fetchSemanticsNodes().isNotEmpty())
        assertTrue("'나'가 머리에 보여요", shows(ME, exact = true))
    })
    /** The same month at 100%: the amounts as they were ('18.3만'). */
    @Test fun ledgerCalendarLight() = shoot("rvk5_15_ledger_calendar_folded", FOLDED, ledgerPage("내역") {
        tapText("달력", exact = true, scrolls = 3)
        settle()
        assertTrue("금액이 그대로(−·+ 뜻 줄 없음)", compose.onAllNodesWithText("− 나간 돈 · + 들어온 돈", substring = true).fetchSemanticsNodes().isEmpty())
    })

    /**
     * [RVK-36] (총괄 HQ-63 ③, 검토 MONEY-77) The six tabs whole on the folded phone (one row at 100%, chips over two lines at 200%),
     * and each tab keeps its own place: 통계 scrolled to 요일별 손익, over to 종목 (its top), back to 통계 (still 요일별 손익).
     */
    private fun tabsKeepTheirPlace() = screen {
        for (t in listOf("한눈에", "종목", "기록", "통계", "일지", "알람")) assertTrue("'$t' 탭이 다 보여요", shows(t, exact = true))
        tapText("통계", exact = true, scrolls = 0)
        settle()
        reveal("요일별 손익", exact = true)
        tapText("종목", exact = true, scrolls = 0)
        settle()
        assertTrue("종목은 맨 위에서", shows("현재가 적기", exact = true))
        tapText("통계", exact = true, scrolls = 0)
        settle()
        assertTrue("통계는 내린 자리 그대로", shows("요일별 손익", exact = true))
    }

    @Test fun tabsLight() = shoot("rvk5_16_tabs_back_to_stats_folded", FOLDED, tabsKeepTheirPlace())
    @Test fun tabsDarkBig() = shoot("rvk5_17_tabs_back_to_stats_folded_dark_200", DARK, 2f, tabsKeepTheirPlace())

    private companion object {
        /** The folded phone at night, as [RvkTwoScreenshotTest] draws it. */
        const val DARK = "w369dp-h905dp-port-night-420dpi"

        /** What the 나 button reads as. */
        const val ME = "나 · 설정과 다른 화면"
    }
}
