package com.bamnat.app.screenshots

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.data.InvestRepository
import com.bamnat.app.data.PersonalCalendar
import com.bamnat.app.ui.pay.PayViewModel
import com.bamnat.core.invest.Market
import com.bamnat.core.invest.Side
import com.bamnat.core.invest.TradeAlerts
import com.bamnat.core.invest.TradeDraft
import com.bamnat.core.money.Job
import com.bamnat.core.money.MoneyEntry
import com.bamnat.core.money.MoneyKind
import com.bamnat.core.tax.BusinessTax
import com.bamnat.core.tax.IncomeKind
import com.bamnat.core.tax.PayObservation
import com.bamnat.core.time.Today
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.YearMonth

/**
 * [TAX-02][TAX-03][TAX-04][TAX-05][INV-38][INV-39][INV-40] 묶음 W's screenshots, numbers 850~869 (1.25 순번 379~384, 409): the money
 * tab's 세금·실수령 for pay and for a business, the records sheet, a past month typed, the business sheet, the lock, the
 * trade sheet working out fee and tax as the price is typed, the saved fee rate, and 체결 내역 캡처로 단가 채우기 with its
 * guide and its yellow questions. The ones that tap check what the tap did (결정 D76).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class WScreenshotTest : ScreenshotBase() {
    private val c get() = app.container

    private companion object {
        const val FOLDED_NIGHT = "w369dp-h905dp-port-night-420dpi"
        /** A capture's text as the phone reads it: one row sure, one whose price disagrees with its amount. */
        val CAPTURE = """
            체결내역
            삼성전자 005930 매수 10 71,500 715,000 09:12:33
            카카오
            매도 체결
            체결수량 5주
            체결단가 42,250원
            체결금액 206,250원
        """.trimIndent()
    }

    /** Pay on the 10th of the next month with 4대 보험, three past payslips and this month's deposit. */
    private fun payWithRecords(): Screen.() -> Unit = style(WorkStyle.SHIFT) {
        runBlocking {
            c.payStore.save(PayViewModel(c).sampleProfile().copy(period = com.bamnat.core.pay.PayPeriod(payDay = 10, payNextMonth = true),
                taxMode = com.bamnat.core.pay.TaxMode.INSURANCE_4), keepUndo = false)
            val tax = c.tax(PersonalCalendar.MAIN)
            val now = YearMonth.from(today)
            listOf(3 to (3_420_000L to 2_948_000L), 2 to (3_180_000L to 2_760_000L)).forEach { (i, gn) ->
                tax.save(PayObservation(payMonth = now.minusMonths(i.toLong()), gross = gn.first, net = gn.second, date = now.minusMonths(i.toLong()).atEndOfMonth(),
                    source = PayObservation.Source.PAYSLIP, kind = IncomeKind.SALARY))
            }
            // The deposit's own ledger line: an observation whose line is gone is taken away when the money tab opens.
            val line = c.moneyRepository.save(MoneyEntry(date = today.withDayOfMonth(10), kind = MoneyKind.INCOME, amount = 3_012_400, category = "급여",
                merchant = "(주)밤낮병원"))
            tax.save(PayObservation(payMonth = now.minusMonths(1), gross = 3_510_000, net = 3_012_400, date = today.withDayOfMonth(10),
                source = PayObservation.Source.DEPOSIT, entryId = line.id))
        }
        settle()
    }

    private fun money(s: WorkStyle = WorkStyle.SHIFT, wide: Boolean = false): Screen.() -> Unit = {
        val vm = com.bamnat.app.ui.money.MoneyViewModel(c, PersonalCalendar.MAIN)
        content { com.bamnat.app.ui.money.MoneyScreen(vm, wide = wide, onOpenPay = {}, today = today) }
        settle()
        reveal("세금·실수령", exact = true, across = if (wide) 0.75f else 0.5f)
    }

    // ---- 380·381: 세금·실수령 for pay ----
    @Test fun payCard() = shoot("850_w_tax_card_pay", PHONE) { payWithRecords()(); money()(); assertTrue(appears("세후 예상 (실수령)")) }
    @Test fun takeHomeSheet() = shoot("851_w_take_home_sheet", FOLDED) {
        payWithRecords()(); money()()
        tapText("기록·자세히")
        assertTrue(appears("실수령 기록 3건"))
        assertTrue(appears("월급 아니에요"))
    }
    @Test fun payRecordFromPayslip() = shoot("852_w_pay_record_dialog", PHONE) {
        payWithRecords()(); money()()
        tapText("기록·자세히"); tapText("지난 월급 적기")
        typeInto("세전 (지급 총액)", "3400000")
        typeInto("실수령 (통장에 들어온 돈)", "2931000")
        assertTrue(appears("명세서 사진으로 채우기"))
    }
    @Test fun typedRecordIsSaved() = shoot("853_w_pay_record_saved", PHONE) {
        payWithRecords()(); money()()
        tapText("기록·자세히"); tapText("지난 월급 적기")
        tapText("시급 파트타임")
        typeInto("일한 시간", "120")
        typeInto("시급", "10030")
        typeInto("실수령 (통장에 들어온 돈)", "1163000")
        tapText("저장", exact = true)
        val saved = runBlocking { c.tax(PersonalCalendar.MAIN).allObservations() }.single { it.kind == IncomeKind.HOURLY }
        assertEquals(1_203_600L, saved.gross)
        assertEquals(1_163_000L, saved.net)
        assertEquals(120.0, saved.hours!!, 0.0)
    }
    @Test fun observedPayInIncome() = shoot("854_w_income_observed", PHONE) {
        payWithRecords()()
        val vm = com.bamnat.app.ui.money.MoneyViewModel(c, PersonalCalendar.MAIN)
        content { com.bamnat.app.ui.money.MoneyScreen(vm, wide = false, onOpenPay = {}, today = today) }
        settle()
        reveal("수입 내역", exact = true)
        assertTrue(appears("관측", exact = false))
    }
    @Test fun hugeTextCard() = shoot("855_w_tax_card_huge_text", SMALL_PHONE, fontScale = 2f) { payWithRecords()(); money()() }
    @Test fun darkSheet() = shoot("856_w_take_home_dark", FOLDED_NIGHT) { payWithRecords()(); money()(); tapText("기록·자세히") }
    @Test fun lockedCard() = shoot("857_w_tax_card_locked", PHONE) {
        payWithRecords()()
        c.settingsRepository.update { it.copy(previewTier = com.bamnat.app.tier.Tier.FREE) }
        money()()
    }

    // ---- 382: 사업자 ----
    private fun business(): Screen.() -> Unit = plain(WorkStyle.FIELD, more = { c ->
        c.moneyRepository.save(Job(date = today.minusDays(40), title = "상가 인테리어", amount = 12_000_000, balancePaid = true, costs = 3_100_000, withholding = true))
        c.moneyRepository.save(MoneyEntry(date = today.minusDays(20), kind = MoneyKind.EXPENSE, amount = 1_320_000, category = "자재비", proof = "세금계산서"))
        c.tax(PersonalCalendar.MAIN).saveSettings { it.copy(business = BusinessTax.Profile(vat = BusinessTax.VatType.SIMPLE, industry = BusinessTax.Industry.CONSTRUCTION,
            simpleRate = 86.6, standardRate = 18.6, lastYearTax = 420_000)) }
    })
    @Test fun businessCard() = shoot("858_w_tax_card_business", PHONE) { business()(); money(WorkStyle.FIELD)(); assertTrue(appears("낼 세금 예상", exact = false)) }
    @Test fun businessSheet() = shoot("859_w_business_sheet", FOLDED) {
        business()(); money(WorkStyle.FIELD)()
        tapText("자세히·설정")
        assertTrue(appears("내는 날"))
        assertTrue(appears("단순경비율"))
    }

    // ---- 379: 매도 단가 → 수수료·세금, 기본 수수료 ----
    private fun tradeSheet(then: Screen.() -> Unit = {}): Screen.() -> Unit = style(WorkStyle.INVEST) {
        val repo = InvestRepository.of(c.database)
        runBlocking {
            val acc = repo.ensureAccount()
            repo.saveTrade(com.bamnat.core.invest.Trade(accountId = acc, date = today.minusDays(3), symbol = "005930", name = "삼성전자",
                side = Side.BUY, quantity = 10.0, price = 68_000.0))
        }
        settle()
        val vm = com.bamnat.app.ui.invest.InvestViewModel(c, PersonalCalendar.MAIN)
        content {
            com.bamnat.app.ui.invest.InvestSheetHost(vm, com.bamnat.app.ui.invest.InvestSheet.TradeEdit(
                TradeDraft("005930", "삼성전자", Market.KR_STOCK, Side.SELL, 10.0, null, null, date = today)), {})
        }
        settle()
        then()
    }
    @Test fun sellPriceWorksOutCosts() = shoot("860_w_trade_sell_costs", FOLDED, tradeSheet {
        typeInto("단가", "72000")
        // 720,000 × 0.05% = 360 fee; 증권거래세 KOSPI 0.05% = 360 (research/1.25-W-조사.md 3장).
        assertTrue(appears("360", exact = false))
        assertTrue(appears("0.05%", exact = false))
    })
    @Test fun feeRatesSaved() = shoot("861_w_fee_rates", PHONE, tradeSheet {
        tapText("기본 수수료")
        typeInto("팔 때", "0.015")
        tapText("저장", exact = true)
        val s = runBlocking { InvestRepository.of(c.database).now().settings }
        assertEquals(0.015, s.sellFeeRate, 0.0)
        tapText("기본 수수료")
    })

    // ---- 409: 단가 채우기 ----
    private fun waiting(): List<TradeAlerts.Pending> {
        val at = today.atTime(9, 12).atZone(Today.zone()).toInstant().toEpochMilli()
        return listOf(
            TradeAlerts.Pending(at, "한국투자", Side.BUY, "005930", "삼성전자", 10.0, 0.0, Market.KR_STOCK),
            TradeAlerts.Pending(at + 3_000_000, "키움", Side.SELL, "035720", "카카오", 5.0, 0.0, Market.KR_STOCK),
        )
    }

    private fun foundWithoutPrice(then: Screen.() -> Unit = {}): Screen.() -> Unit = style(WorkStyle.INVEST) {
        val repo = InvestRepository.of(c.database)
        runBlocking {
            repo.ensureAccount()
            waiting().forEach { repo.addAlert(it) }
            // One with its price from the notification's amount, for the note beside it.
            repo.addAlert(TradeAlerts.Pending(Today.millis() - 600_000, "업비트", Side.BUY, "ETH", "이더리움", 0.03, 4_211_000.0, Market.CRYPTO, 126_330.0,
                filledBy = TradeAlerts.BY_AMOUNT))
        }
        settle()
        val vm = com.bamnat.app.ui.invest.InvestViewModel(c, PersonalCalendar.MAIN)
        content { com.bamnat.app.ui.invest.InvestScreen(vm, wide = false, snackbar = androidx.compose.runtime.remember { androidx.compose.material3.SnackbarHostState() }) }
        settle()
        then()
    }
    @Test fun foundTradesWithoutPrice() = shoot("862_w_found_without_price", PHONE, foundWithoutPrice {
        assertTrue(appears("단가 없는 알림 2건"))
        assertTrue(appears("체결 금액 ÷ 수량", exact = false))
    })
    @Test fun captureGuide() = shoot("863_w_capture_guide", PHONE, foundWithoutPrice {
        tapText("체결 내역 캡처로 채우기")
        assertTrue(appears("체결 내역 캡처로 채우기", exact = true))
        assertTrue(appears("이런 화면이면 돼요"))
    })
    @Test fun captureGuideSmall() = shoot("864_w_capture_guide_small", SMALL_PHONE, foundWithoutPrice { tapText("체결 내역 캡처로 채우기") })

    private fun captureRead(then: Screen.(com.bamnat.app.ui.invest.InvestViewModel) -> Unit = {}): Screen.() -> Unit = style(WorkStyle.INVEST) {
        val repo = InvestRepository.of(c.database)
        // [INVA-02] 1.29: 바로 넣기 is on unless asked for; these shots are the asking sheet.
        runBlocking { repo.ensureAccount(); waiting().forEach { repo.addAlert(it) }; repo.setFillGuideSeen(); repo.setFillTrust(false) }
        settle()
        val vm = com.bamnat.app.ui.invest.InvestViewModel(c, PersonalCalendar.MAIN)
        val alerts = runBlocking { repo.pendingAlerts.first() }
        content { com.bamnat.app.ui.invest.FillCaptureSheet(vm, alerts, null, onDismiss = {}, initialText = CAPTURE) }
        settle()
        then(vm)
    }
    @Test fun captureAsksInYellow() = shoot("865_w_capture_review_ask", FOLDED, captureRead {
        assertTrue(appears("수량 × 단가가 금액과 안 맞아요", exact = false))
        assertTrue(appears("노란 칸은 확인해야 넣을 수 있어요"))
        // [INV-43] 1.27 AK: the sure row goes in as a trade; the yellow one waits, unfilled.
        tapText("넣기 1건", exact = true)
        val repo = InvestRepository.of(c.database)
        assertEquals(71_500.0, runBlocking { repo.now().trades }.single().price, 0.0)
        assertTrue(runBlocking { repo.pendingAlerts.first() }.single().let { it.symbol == "035720" && it.price == 0.0 })
    })
    @Test fun captureFixedAndFilled() = shoot("866_w_capture_filled", FOLDED, captureRead {
        // The user looks at the capture and fixes the misread price: 41,250.
        typeInto("단가 확인", "41250")
        tapText("넣기 2건", exact = true)
        val repo = InvestRepository.of(c.database)
        val t = runBlocking { repo.now().trades }
        assertEquals(71_500.0, t.single { it.name == "삼성전자" }.price, 0.0)
        assertEquals(41_250.0, t.single { it.symbol == "035720" }.price, 0.0)
        // [INV-43] Written, so no longer waiting (결정 AK-D3).
        assertTrue(runBlocking { repo.pendingAlerts.first() }.isEmpty())
    })
    @Test fun captureDark() = shoot("867_w_capture_review_dark", FOLDED_NIGHT, captureRead())
    @Test fun filledLinesWaitForSave() = shoot("868_w_found_filled", PHONE, style(WorkStyle.INVEST) {
        val repo = InvestRepository.of(c.database)
        runBlocking {
            repo.ensureAccount()
            waiting().forEach { repo.addAlert(it) }
            val list = TradeAlerts.fill(TradeAlerts.fill(repo.alertsNow(), waiting()[0].at, 71_500.0, 715_000.0), waiting()[1].at, 41_250.0, 206_250.0)
            repo.restoreAlerts(list)
        }
        settle()
        val vm = com.bamnat.app.ui.invest.InvestViewModel(c, PersonalCalendar.MAIN)
        content { com.bamnat.app.ui.invest.InvestScreen(vm, wide = false, snackbar = androidx.compose.runtime.remember { androidx.compose.material3.SnackbarHostState() }) }
        settle()
        assertTrue(appears("체결 내역 캡처에서 읽었어요", exact = false))
        tapText("적기", exact = true)
        assertEquals(1, runBlocking { repo.now().trades.count { it.side == Side.BUY && it.price == 71_500.0 || it.side == Side.SELL && it.price == 41_250.0 } })
    })
    @Test fun unfoldedTaxCard() = shoot("869_w_tax_card_unfolded", UNFOLDED) { payWithRecords()(); money(wide = true)() }
}
