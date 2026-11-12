package com.bamnat.app.screenshots

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.core.invest.InvestAccount
import com.bamnat.core.invest.TradeAlertParser
import com.bamnat.core.invest.TradeAlerts
import com.bamnat.core.time.Today
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 2.0.0 갈래 K (가계부·투자): the screens its first PR touches, light·dark × 글자 100%·200% × 접은·편 폭, for the PR's before/after
 * sheet (디자인 품질 조건 16가지). Lines marked K-NEW use what only the new code has; without them the same test draws the 'before'.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RvkScreenshotTest : ScreenshotBase() {
    /** 돈 적기 as it opens: the calculator is one icon in the amount box, so the sheet is no taller than before (총괄 #457 리뷰). */
    private fun entryPlain(): Screen.() -> Unit = {
        content { com.bamnat.app.ui.money.EntryDialog(today, null, onSave = {}, onDismiss = {}) }
        settle()
        // 총괄 #457: the icon says what it does and is a 48dp place to press (조건 9·14). // K-NEW
        val icon = nodes().single { it.config.getOrElse(androidx.compose.ui.semantics.SemanticsProperties.ContentDescription) { emptyList() }.contains("계산하기") } // K-NEW
        val dp = app.resources.displayMetrics.density // K-NEW
        // Drawn 40dp like every icon button; the place a finger lands is the 48dp around it. // K-NEW
        val touch = icon.touchBoundsInRoot // K-NEW
        org.junit.Assert.assertTrue("계산기 아이콘 누르는 곳 $touch", touch.width >= 48 * dp - 1 && touch.height >= 48 * dp - 1) // K-NEW
    }

    /** [RVK-15] 돈 적기 with "36000÷3" in the amount: + − × ÷ = under it and "= 12,000원". */
    private fun entryCalc(): Screen.() -> Unit = {
        content { com.bamnat.app.ui.money.EntryDialog(today, null, onSave = {}, onDismiss = {}) }
        settle()
        typeInto("금액", "36000÷3")
    }

    /** [RVK-13] One payment the app left out, shown under 새로 들어온 기록 as '자동으로 정리한 것 1건 보기'. */
    private fun tidied() = runBlocking<Unit> {
        val c = app.container
        c.autoTidy.add(com.bamnat.app.data.AutoTidy.AREA_MONEY, c.calendars.active.value, "스타벅스 4,500원", // K-NEW
            "같은 결제를 카드와 은행이 함께 알려 와 한 줄만 적었어요", "money.left_out", "{}") // K-NEW
    }

    private fun newRecords(): Screen.() -> Unit = {
        tidied()
        settle()
        tab(1, wide = false)
        reveal("새로 들어온 기록", exact = false)
        reveal("자동으로 정리한 것 1건 보기", exact = true) // K-NEW
    }

    private fun tidySheet(wide: Boolean = false): Screen.() -> Unit = {
        tidied()
        settle()
        tab(1, wide = wide)
        tapText("자동으로 정리한 것 1건 보기", exact = true)
        settle()
    }

    /** [RVK-17] Fills waiting from two 증권사 apps, each with the account it goes to, and '확실한 N건 모두 적기'. */
    private fun trades(): Screen.() -> Unit = style(WorkStyle.INVEST) {
        val c = app.container
        val repo = com.bamnat.app.data.InvestRepository.of(c.database)
        val at = Today.millis()
        runBlocking {
            repo.saveAccount(InvestAccount(name = "주식 계좌", broker = "한국투자증권"))
            repo.saveAccount(InvestAccount(name = "코인", broker = "업비트"))
            listOf(
                "[체결통보] 매도 삼성전자(005930) 3주 체결 단가 73,000원" to ("한국투자" to "com.truefriend.neosmarta"),
                "[업비트] 매수 체결 KRW-ETH 0.25 ETH @ 4,800,000원" to ("업비트" to "com.dunamu.exchange.upbit"),
            ).forEachIndexed { i, (text, src) ->
                repo.addAlert(TradeAlerts.of(TradeAlertParser.parse(text, src.second)!!, src.first, at - i * 600_000L))
            }
            c.autoTidy.add(com.bamnat.app.data.AutoTidy.AREA_INVEST, c.calendars.active.value, "삼성전자 3주 매도", // K-NEW
                "같은 체결을 다른 앱도 알려 와 한 번만 적었어요", "invest.fill", "{}") // K-NEW
        }
        settle()
        val vm = com.bamnat.app.ui.invest.InvestViewModel(c, com.bamnat.app.data.PersonalCalendar.MAIN)
        // In the app the tab sits in the Scaffold's surface, which gives the title its colour; without it a dark shot draws it black.
        content {
            androidx.compose.material3.Surface(color = androidx.compose.material3.MaterialTheme.colorScheme.background) {
                com.bamnat.app.ui.invest.InvestScreen(vm, wide = false, snackbar = androidx.compose.runtime.remember { androidx.compose.material3.SnackbarHostState() })
            }
        }
        settle()
        reveal("알림으로 찾은 거래", exact = false)
    }

    /** [RVK-18] A bank account's editor: '지금 잔액' and '실제 잔액으로 맞추기'. */
    private fun account(adjust: Boolean = false): Screen.() -> Unit = {
        ledger()
        val money = app.container.moneyRepository
        val bank = runBlocking { money.allAccounts().first { it.name == "국민 월급통장" } }
        content {
            val book = com.bamnat.app.ui.money.rememberLedger(null)
            com.bamnat.app.ui.money.AccountEditor(bank, null, book, onSave = {}, onRemove = {}, onDismiss = {},
                onAdjust = { _, _ -> }, // K-NEW
            )
        }
        settle()
        if (adjust) {
            tapText("실제 잔액으로 맞추기", exact = true)
            settle()
            typeInto("실제 잔액", "1800000")
        } else reveal("지금 잔액", exact = false)
    }

    /** [RVK-16] 자동 기록 with notification access: '마지막으로 읽음 … · 이번 달 3건', and the reader the phone cut. */
    private fun autoRecord(cut: Boolean): Screen.() -> Unit = ledgerPage(null) {
        val now = Today.millis()
        app.getSharedPreferences("capture_health", android.content.Context.MODE_PRIVATE).edit()
            .putLong("connected", now - 6 * DAY).putLong("read", now - 2 * 60 * 60 * 1000L)
            .putString("month", java.time.YearMonth.from(today).toString()).putInt("written", 3)
            .apply { if (cut) putLong("cut", now - 60 * 60 * 1000L) }.commit()
        android.provider.Settings.Secure.putString(app.contentResolver, "enabled_notification_listeners",
            android.content.ComponentName(app, com.bamnat.app.money.CaptureService::class.java).flattenToString())
        com.bamnat.app.money.CaptureService.setOn(app, true)
        tidied()
        com.bamnat.app.NoticeLinks.open(com.bamnat.app.NoticeLinks.Target.AUTO_RECORD)
        settle()
    }

    /** [RVK-10] Three lines picked and 지우기: how many, the days, how much and 30일 in 최근 지운 기록. */
    private fun deleteMany(): Screen.() -> Unit = ledgerPage("내역") {
        reveal("팀 점심")
        longPressText("팀 점심")
        tapText("이마트")
        tapText("고른 기록 지우기", exact = true, scrolls = 0)
        settle()
    }

    /** [RVK-06] 하루 마감 for a shop with this day's ledger spending: '가계부에 적은 이날 지출 2건 …은 따로 빠져요'. */
    private fun dayClose(): Screen.() -> Unit = style(WorkStyle.BUSINESS) {
        content { com.bamnat.app.ui.money.DayCloseDialog(today, null, onSave = {}, onDismiss = {}) }
        settle()
        reveal("경비 (재료비·인건비 등)", exact = true)
    }

    @Test fun entryPlainLight() = shoot("rvk_00_entry_folded", FOLDED, entryPlain())
    @Test fun entryPlainDarkBig() = shoot("rvk_00b_entry_folded_dark_200", DARK, 2f, entryPlain())
    @Test fun entryLight() = shoot("rvk_01_entry_calc_folded", FOLDED, entryCalc())
    @Test fun entryDarkBig() = shoot("rvk_02_entry_calc_folded_dark_200", DARK, 2f, entryCalc())
    @Test fun entryWide() = shoot("rvk_03_entry_calc_unfolded", UNFOLDED, entryCalc())
    @Test fun newRecordsLight() = shoot("rvk_04_money_new_records_folded", FOLDED, newRecords())
    @Test fun newRecordsDarkBig() = shoot("rvk_05_money_new_records_folded_dark_200", DARK, 2f, newRecords())
    @Test fun tradesLight() = shoot("rvk_08_found_trades_folded", FOLDED, trades())
    @Test fun tradesDarkBig() = shoot("rvk_09_found_trades_folded_dark_200", DARK, 2f, trades())
    @Test fun tradesWide() = shoot("rvk_10_found_trades_unfolded", UNFOLDED, trades())
    @Test fun accountLight() = shoot("rvk_11_account_folded", FOLDED, account())
    @Test fun accountDarkBig() = shoot("rvk_12_account_folded_dark_200", DARK, 2f, account())
    @Test fun autoLight() = shoot("rvk_14_auto_record_folded", FOLDED, autoRecord(cut = false))
    @Test fun autoDarkBig() = shoot("rvk_15_auto_record_cut_folded_dark_200", DARK, 2f, autoRecord(cut = true))
    @Test fun deleteLight() = shoot("rvk_16_delete_many_folded", FOLDED, deleteMany())
    @Test fun deleteDarkBig() = shoot("rvk_17_delete_many_folded_dark_200", DARK, 2f, deleteMany())
    @Test fun dayCloseLight() = shoot("rvk_18_day_close_folded", FOLDED, dayClose())
    @Test fun dayCloseDarkBig() = shoot("rvk_19_day_close_folded_dark_200", DARK, 2f, dayClose())

    // Only the new code has these screens.
    @Test fun tidyLight() = shoot("rvk_06_tidy_sheet_folded", FOLDED, tidySheet()) // K-NEW
    @Test fun tidyDarkBig() = shoot("rvk_07_tidy_sheet_folded_dark_200", DARK, 2f, tidySheet()) // K-NEW
    @Test fun tidyWide() = shoot("rvk_07b_tidy_sheet_unfolded", UNFOLDED, tidySheet(wide = true)) // K-NEW
    @Test fun adjustLight() = shoot("rvk_13_balance_adjust_folded", FOLDED, account(adjust = true)) // K-NEW
    @Test fun adjustDarkBig() = shoot("rvk_13b_balance_adjust_folded_dark_200", DARK, 2f, account(adjust = true)) // K-NEW

    private companion object {
        const val DARK = "w369dp-h905dp-port-night-420dpi"
        const val DAY = 24 * 60 * 60 * 1000L
    }
}
