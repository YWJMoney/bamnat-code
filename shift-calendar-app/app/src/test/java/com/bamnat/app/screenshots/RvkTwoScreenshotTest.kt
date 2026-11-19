package com.bamnat.app.screenshots

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.core.money.MoneyKind
import com.bamnat.core.time.Today
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 2.0.0 갈래 K 2차: 요약의 큰 숫자(지출만 적는 사람), 월급날 기준으로 보기가 요약·내역·통계 모두에, 새로 들어온 기록 줄(큰 글자)과
 * 자동 기록 창의 단추. Light·dark × 글자 100%·200% × 접은·편 폭, for the PR's before/after sheet. Nothing here needs the new code,
 * so the same test draws the 'before'.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RvkTwoScreenshotTest : ScreenshotBase() {
    /** [RVK-19] (U06a-06·MONEY-19) Someone who writes only spending: no money in, no pay to come. */
    private fun spendOnly(): Screen.() -> Unit = style(WorkStyle.DAY) {
        ledger()
        runBlocking {
            val money = app.container.moneyRepository
            money.allEntries().filter { it.kind == MoneyKind.INCOME }.forEach { money.delete(it) }
            // The seed's jobs and shop sales are money in too.
            money.allJobs().forEach { money.delete(it) }
            (-90L..30L).forEach { money.clearSales(today.plusDays(it)) }
            app.container.payStore.save(null, keepUndo = false)
        }
        settle()
        tab(1, wide = false)
    }

    /** [RVK-20] (MONEY-18·U06a-07) Pay on the 25th and 월급날 기준으로 보기 on: today (10/14) is in 9/25~10/24. */
    private fun payCycle(page: String?, wide: Boolean = false): Screen.() -> Unit = style(WorkStyle.SHIFT) {
        runBlocking { app.container.payStore.save(closeOn20(com.bamnat.app.ui.pay.PayViewModel(app.container).sampleProfile()), keepUndo = false) }
        app.container.settingsRepository.update { it.copy(moneyByPayCycle = true) }
        // Spending in 9/25~9/30, so the cycle's sums differ from October's.
        runBlocking {
            app.container.moneyRepository.save(com.bamnat.core.money.MoneyEntry(date = today.minusDays(17), kind = MoneyKind.EXPENSE,
                amount = 52_000, category = "식비", merchant = "이마트"))
        }
        ledgerPage(page, wide).invoke(this)
        settle()
    }

    /** [RVK-21]·HQ-23 새로 들어온 기록: a long shop name at large text. */
    private fun newRecords(): Screen.() -> Unit = {
        runBlocking {
            app.container.moneyRepository.save(com.bamnat.core.money.MoneyEntry(date = today, kind = MoneyKind.EXPENSE, amount = 38_900,
                category = "식비", merchant = "가나다라마바사 프랜차이즈 본점 (역삼역 2번 출구)", source = com.bamnat.core.money.MoneySource.NOTIFICATION,
                reviewed = false))
        }
        settle()
        tab(1, wide = false)
        reveal("새로 들어온 기록", exact = false)
    }

    /** [RVK-22]·HQ-24 자동 기록 with notification access on. */
    private fun autoRecord(): Screen.() -> Unit = ledgerPage(null) {
        val now = Today.millis()
        app.getSharedPreferences("capture_health", android.content.Context.MODE_PRIVATE).edit()
            .putLong("connected", now - 6 * DAY).putLong("read", now - 2 * 60 * 60 * 1000L)
            .putString("month", java.time.YearMonth.from(today).toString()).putInt("written", 3).commit()
        android.provider.Settings.Secure.putString(app.contentResolver, "enabled_notification_listeners",
            android.content.ComponentName(app, com.bamnat.app.money.CaptureService::class.java).flattenToString())
        com.bamnat.app.money.CaptureService.setOn(app, true)
        com.bamnat.app.NoticeLinks.open(com.bamnat.app.NoticeLinks.Target.AUTO_RECORD)
        settle()
        reveal("더 정확하게 적기", exact = true)
    }

    /** [RVK-23]·HQ-30 앞으로의 돈 with its next five, names that need the room at large text ('10/14 신한카드 카드값'). */
    private fun ahead(): Screen.() -> Unit = ledgerPage(null) {
        reveal("평소 하루 지출", exact = false)
    }

    /** [RVK-24]·HQ-31 알림으로 찾은 거래: two fills from two apps, each with its account. */
    private fun trades(wide: Boolean = false): Screen.() -> Unit = style(WorkStyle.INVEST) {
        val c = app.container
        val repo = com.bamnat.app.data.InvestRepository.of(c.database)
        val at = Today.millis()
        runBlocking {
            repo.saveAccount(com.bamnat.core.invest.InvestAccount(name = "주식 계좌", broker = "한국투자증권"))
            repo.saveAccount(com.bamnat.core.invest.InvestAccount(name = "코인", broker = "업비트"))
            listOf(
                "[체결통보] 매도 삼성전자(005930) 3주 체결 단가 73,000원" to ("한국투자" to "com.truefriend.neosmarta"),
                "[업비트] 매수 체결 KRW-ETH 0.25 ETH @ 4,800,000원" to ("업비트" to "com.dunamu.exchange.upbit"),
            ).forEachIndexed { i, (text, src) ->
                repo.addAlert(com.bamnat.core.invest.TradeAlerts.of(com.bamnat.core.invest.TradeAlertParser.parse(text, src.second)!!, src.first, at - i * 600_000L))
            }
        }
        settle()
        val vm = com.bamnat.app.ui.invest.InvestViewModel(c, com.bamnat.app.data.PersonalCalendar.MAIN)
        content {
            androidx.compose.material3.Surface(color = androidx.compose.material3.MaterialTheme.colorScheme.background) {
                com.bamnat.app.ui.invest.InvestScreen(vm, wide = wide, snackbar = androidx.compose.runtime.remember { androidx.compose.material3.SnackbarHostState() })
            }
        }
        settle()
        reveal("알림으로 찾은 거래", exact = false)
    }

    /**
     * 2.0.0 [RVK-24] (총괄 #472 리뷰, 조건 9·14) Each ✓ reads '적기' and is a 48dp place to press, and a line opened to its sheet is
     * let go there with '넘기기', without the swipe to the left.
     */
    private fun letGo(): Screen.() -> Unit {
        val base = trades()
        return {
            base()
            val dp = app.resources.displayMetrics.density
            val ticks = nodes().filter {
                it.config.getOrElse(androidx.compose.ui.semantics.SemanticsProperties.ContentDescription) { emptyList() }.contains("적기")
            }
            org.junit.Assert.assertEquals(2, ticks.size)
            ticks.forEach { t ->
                val touch = t.touchBoundsInRoot
                org.junit.Assert.assertTrue("✓ 누르는 곳 $touch", touch.width >= 48 * dp - 1 && touch.height >= 48 * dp - 1)
            }
            tapText("삼성전자", exact = false)
            org.junit.Assert.assertTrue(appears("넘기기", exact = true))
            tapText("넘기기", exact = true)
            settle()
            org.junit.Assert.assertEquals(1, runBlocking { com.bamnat.app.data.InvestRepository.of(app.container.database).alertsNow().size })
        }
    }

    @Test fun spendOnlyLight() = shoot("rvk2_01_spend_only_summary_folded", FOLDED, spendOnly())
    @Test fun spendOnlyDarkBig() = shoot("rvk2_02_spend_only_summary_folded_dark_200", DARK, 2f, spendOnly())
    @Test fun cycleSummaryLight() = shoot("rvk2_03_pay_cycle_summary_folded", FOLDED, payCycle(null))
    @Test fun cycleSummaryDarkBig() = shoot("rvk2_04_pay_cycle_summary_folded_dark_200", DARK, 2f, payCycle(null))
    @Test fun cycleListLight() = shoot("rvk2_05_pay_cycle_list_folded", FOLDED, payCycle("내역"))
    @Test fun cycleReportLight() = shoot("rvk2_06_pay_cycle_report_folded", FOLDED, payCycle("통계"))
    @Test fun cycleReportWide() = shoot("rvk2_07_pay_cycle_report_unfolded", UNFOLDED, payCycle("통계", wide = true))
    @Test fun newRecordsDarkBig() = shoot("rvk2_08_new_records_folded_dark_200", DARK, 2f, newRecords())
    @Test fun newRecordsLight() = shoot("rvk2_08b_new_records_folded", FOLDED, newRecords())
    @Test fun autoRecordLight() = shoot("rvk2_09_auto_record_folded", FOLDED, autoRecord())
    @Test fun autoRecordDarkBig() = shoot("rvk2_10_auto_record_folded_dark_200", DARK, 2f, autoRecord())
    @Test fun aheadLight() = shoot("rvk2_11_ahead_folded", FOLDED, ahead())
    @Test fun aheadDarkBig() = shoot("rvk2_12_ahead_folded_dark_200", DARK, 2f, ahead())
    @Test fun tradesLight() = shoot("rvk2_13_found_trades_folded", FOLDED, trades())
    @Test fun tradesDarkBig() = shoot("rvk2_14_found_trades_folded_dark_200", DARK, 2f, trades())
    @Test fun tradesWide() = shoot("rvk2_15_found_trades_unfolded", UNFOLDED, trades(wide = true))
    @Test fun letGoLight() = shoot("rvk2_16_found_trade_let_go_folded", FOLDED, letGo())

    private companion object {
        const val DARK = "w369dp-h905dp-port-night-420dpi"
        const val DAY = 24 * 60 * 60 * 1000L
    }
}
