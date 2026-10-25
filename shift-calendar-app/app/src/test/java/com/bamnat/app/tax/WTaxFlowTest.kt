package com.bamnat.app.tax

import android.os.Looper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.BamnatApp
import com.bamnat.app.data.BackupManager
import com.bamnat.app.data.InvestRepository
import com.bamnat.app.data.MoneyRepository
import com.bamnat.app.data.PersonalCalendar
import com.bamnat.app.data.SettingsRepository
import com.bamnat.app.data.TaxRepository
import com.bamnat.app.data.db.AppDatabase
import com.bamnat.app.screenshots.ScreenshotBase
import com.bamnat.app.ui.pay.PayViewModel
import com.bamnat.core.invest.Side
import com.bamnat.core.invest.TradeAlerts
import com.bamnat.core.journal.JournalKind
import com.bamnat.core.money.MoneyCategories
import com.bamnat.core.money.MoneyKind
import com.bamnat.core.tax.BusinessTax
import com.bamnat.core.tax.IncomeKind
import com.bamnat.core.tax.PayObservation
import com.bamnat.core.tax.TaxLearner
import com.bamnat.core.time.Today
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.YearMonth

/**
 * [TAX-01][TAX-02][TAX-04][TAX-07][INV-38][INV-40] 1.25 W, end to end the way the app runs it (결정 D76): a bank's pay
 * notification goes through capture into 급여 and 월급 관측 and is counted once; the records teach next month's 세후; a
 * deposit that was not pay goes back; the tax data survives a backup; a trade notification without a price gets one
 * from its amount or from the next notification, and the saved fee rate is used when it is written.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class WTaxFlowTest {
    private val app get() = ApplicationProvider.getApplicationContext<BamnatApp>()
    private val c get() = app.container

    companion object {
        // [QA-08] 2026-10-14 10:30 in Seoul, as the screenshots, before the app reads the date.
        @JvmStatic @org.junit.BeforeClass fun stop() = ScreenshotBase.stopTheClock()
        @JvmStatic @org.junit.AfterClass fun start() = ScreenshotBase.startTheClock()
    }

    @Before fun ready() = runBlocking {
        val end = System.currentTimeMillis() + 60_000
        while (c.scheduleRepository.shiftTypes.first().isEmpty()) {
            check(System.currentTimeMillis() < end) { "Timed out waiting for the first shifts" }
            shadowOf(Looper.getMainLooper()).idle()
        }
        c.journal.clear()
    }

    private fun withPay() = runBlocking {
        c.settingsRepository.update { it.copy(workStyle = WorkStyle.SHIFT) }
        // Paid on the 10th of the next month, with 4대 보험 and 간이세액표 taken off.
        c.payStore.save(PayViewModel(c).sampleProfile().copy(period = com.bamnat.core.pay.PayPeriod(payDay = 10, payNextMonth = true),
            taxMode = com.bamnat.core.pay.TaxMode.INSURANCE_4), keepUndo = false)
        c.calendars.pay(PersonalCalendar.MAIN).profile.first { it != null }!!
    }

    /** The month whose pay day has come by today, with the 실수령 the app expects then. */
    private fun lastPay(): com.bamnat.core.tax.SalaryDeposits.Expected {
        val profile = c.calendars.pay(PersonalCalendar.MAIN).profile.value!!
        val snap = runBlocking { c.scheduleRepository.snapshot.first() }
        val today = Today.date()
        return SalaryWatch.expected(profile, snap, emptyList(), today).last { e -> val d = e.payDay; e.net != null && d != null && !d.isAfter(today) && d.isAfter(today.minusDays(40)) }
    }

    private fun bank(amount: Long, day: java.time.LocalDate, who: String = "(주)밤낮병원") =
        "하나 ${day.monthValue}/${day.dayOfMonth} 09:00 123-456***-78 입금 ${"%,d".format(amount)} 잔액 4,000,000 $who"

    private fun posted(day: java.time.LocalDate) = day.atTime(9, 0).atZone(Today.zone()).toInstant().toEpochMilli()

    @Test fun aPayDepositIsKeptAsAnObservationAndCountedOnce() = runBlocking {
        withPay()
        val e = lastPay()
        val came = e.net!! + 12_340
        assertEquals(MoneyRepository.Outcome.SAVED, c.capture(bank(came, e.payDay!!), "com.hanabank", "하나은행", posted(e.payDay!!), fromMessenger = false))
        val money = c.calendars.money(PersonalCalendar.MAIN)
        val line = money.allEntries().single { it.amount == came }
        // Filed as 급여, not 부수입 on top of the estimate (사용자 U9-다).
        assertEquals(MoneyCategories.SALARY, line.category)
        val obs = c.tax(PersonalCalendar.MAIN).allObservations().single()
        assertEquals(PayObservation.Source.DEPOSIT, obs.source)
        assertEquals(e.payMonth, obs.payMonth)
        assertEquals(came, obs.net)
        // The money tab's month: the estimate counts, the deposit is an observation beside it.
        val month = YearMonth.from(e.payDay!!)
        val estimate = 3_000_000L
        val m = money.book.first().month(month, estimate)
        assertEquals(estimate, m.payCounted)
        assertTrue(m.payObserved)
        assertEquals(0L, m.otherIncome)
        assertEquals(estimate, m.income)
        // Said once in '밤낮이 한 일', with no amount.
        val end = System.currentTimeMillis() + 20_000
        while (c.journal.load().none { it.kind == JournalKind.PAY_OBSERVED }) {
            check(System.currentTimeMillis() < end) { "no journal line" }
            shadowOf(Looper.getMainLooper()).idle()
        }
        // The same notification again changes nothing.
        c.capture(bank(came, e.payDay!!), "com.hanabank", "하나은행", posted(e.payDay!!) + 60_000, fromMessenger = false)
        assertEquals(1, c.tax(PersonalCalendar.MAIN).allObservations().size)
    }

    @Test fun theRecordsTeachNextMonthsTakeHome() = runBlocking {
        val profile = withPay()
        val snap = c.scheduleRepository.snapshot.first()
        val tax = c.tax(PersonalCalendar.MAIN)
        val table = TaxLearner.Table.of(profile)
        val gross = SalaryWatch.grossOf(profile, snap)
        val now = YearMonth.from(Today.date())
        // Three past payslips typed in, where the company takes 15% more than the table.
        for (i in 1..3) {
            val m = now.minusMonths(i.toLong())
            val g = gross(m) ?: 3_000_000L
            tax.save(PayObservation(payMonth = m, gross = g, net = g - (table.deductions(g, m) * 1.15).toLong(), date = m.atEndOfMonth(),
                source = PayObservation.Source.PAYSLIP, kind = IncomeKind.SALARY))
        }
        val view = TakeHomeView.of(profile, snap, tax.allObservations(), now)
        assertEquals(TaxLearner.Basis.LEARNED, view.fit.basis)
        assertEquals(1.15, view.fit.k, 0.02)
        val f = view.forecast!!
        assertTrue("배운 공제가 표보다 커서 세후가 더 적어요", f.net < f.tableNet)
        assertEquals(3, view.typed.size)
        // A second record for the same month replaces it.
        tax.save(PayObservation(payMonth = now.minusMonths(1), gross = 3_000_000, net = 2_600_000, date = now.minusMonths(1).atEndOfMonth()))
        assertEquals(3, tax.allObservations().size)
    }

    @Test fun aDepositThatWasNotPayGoesBackAndIsNeverTakenAgain() = runBlocking {
        withPay()
        val e = lastPay()
        val came = e.net!!
        c.capture(bank(came, e.payDay!!, "김철수"), "com.hanabank", "하나은행", posted(e.payDay!!), fromMessenger = false)
        val money = c.calendars.money(PersonalCalendar.MAIN)
        val line = money.allEntries().single { it.amount == came }
        assertEquals(MoneyCategories.SALARY, line.category)
        SalaryWatch.notPay(c, PersonalCalendar.MAIN, line)
        assertEquals("부수입", money.allEntries().single { it.id == line.id }.category)
        assertTrue(c.tax(PersonalCalendar.MAIN).allObservations().isEmpty())
        SalaryWatch.check(c, PersonalCalendar.MAIN)
        assertEquals("부수입", money.allEntries().single { it.id == line.id }.category)
        assertTrue(c.tax(PersonalCalendar.MAIN).allObservations().isEmpty())
    }

    @Test fun theTaxRecordsSurviveABackup() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            com.bamnat.app.data.ScheduleRepository(db).ensureDefaults()
            val tax = TaxRepository(db)
            tax.save(PayObservation(payMonth = YearMonth.of(2026, 9), gross = 3_100_000, net = 2_710_000, date = YearMonth.of(2026, 9).atEndOfMonth(),
                source = PayObservation.Source.PAYSLIP, kind = IncomeKind.HOURLY, hours = 160.5, note = "9월 명세서"))
            tax.saveSettings { it.copy(business = BusinessTax.Profile(vat = BusinessTax.VatType.SIMPLE, industry = BusinessTax.Industry.RESTAURANT, simpleRate = 89.7, family = 3), payers = setOf("밤낮병원")) }
            val before = tax.allObservations() to tax.currentSettings()
            val manager = BackupManager(db, SettingsRepository(app))
            val json = manager.export()
            db.taxDao().clearObservations(); db.taxDao().clearSettings()
            assertTrue(tax.allObservations().isEmpty())
            manager.restore(json)
            assertEquals(before.first, tax.allObservations())
            assertEquals(before.second, tax.currentSettings())
            // A backup from before 1.25 (no "tax") leaves the phone's.
            val old = org.json.JSONObject(json).apply { remove("tax") }.toString()
            manager.restore(old)
            assertEquals(before.first, tax.allObservations())
        } finally {
            db.close()
        }
    }

    // ---- 투자 (379, 409) ----

    private fun investor(): InvestRepository {
        c.settingsRepository.update { it.copy(workStyle = WorkStyle.INVEST) }
        return InvestRepository.of(c.database)
    }

    @Test fun aTradeNotificationWithOnlyTheAmountGetsItsPrice() = runBlocking {
        val repo = investor()
        val at = Today.millis()
        c.capture("[한국투자증권] 매수 체결\n삼성전자(005930) 10주\n체결금액 715,000원", "com.truefriend.neosmarta", "한국투자", at, fromMessenger = false)
        val p = repo.pendingAlerts.first().single()
        assertEquals(71_500.0, p.price, 0.0)
        assertEquals(TradeAlerts.BY_AMOUNT, p.filledBy)
        // Nothing went to the ledger.
        assertTrue(c.calendars.money(PersonalCalendar.MAIN).allEntries().none { it.kind == MoneyKind.EXPENSE && it.amount == 715_000L })
    }

    @Test fun splitFillNotificationsBecomeOneTradeAtTheAverage() = runBlocking {
        val repo = investor()
        val at = Today.millis()
        c.capture("[키움] 매수 체결 셀트리온(068270) 3주 단가 178,000원", "com.kiwoom.heromts", "키움", at, fromMessenger = false)
        c.capture("[키움] 매수 체결 셀트리온(068270) 7주 단가 178,100원", "com.kiwoom.heromts", "키움", at + 4_000, fromMessenger = false)
        val p = repo.pendingAlerts.first().single()
        assertEquals(10.0, p.quantity, 0.0)
        assertEquals(178_070.0, p.price, 0.0)
        assertEquals(2, p.parts)
    }

    @Test fun theSavedFeeRateIsUsedWhenAFoundTradeIsWritten() = runBlocking {
        val repo = investor()
        repo.saveSettings { it.copy(buyFeeRate = 0.1, sellFeeRate = 0.2) }
        val at = Today.millis()
        c.capture("[한국투자증권] 매도 체결 삼성전자(005930) 10주 단가 72,000원", "com.truefriend.neosmarta", "한국투자", at, fromMessenger = false)
        val p = repo.pendingAlerts.first().single()
        assertEquals(Side.SELL, p.side)
        val t = repo.confirmAlert(p)
        // 720,000 × 0.2% = 1,440 fee; 증권거래세 KOSPI 0.05%... the market's own rate (research/1.25-W-조사.md 3장).
        assertEquals(1_440.0, t.fee, 0.5)
        assertTrue(t.tax > 0)
        // The rates are still there next time.
        assertEquals(0.2, repo.now().settings.sellFeeRate, 0.0)
        assertFalse(repo.pendingAlerts.first().isNotEmpty())
        assertNotNull(repo.now().trades.single())
    }

    /**
     * [TAX-02][TAX-04][READ-08] After U: a payslip read on the pay screen (with the company's 계산 방법 as the other photo, in
     * either order) is that month's record for the estimate, and the pay screen and the calendar get 세후 beside 세전.
     */
    @Test fun aPayslipReadOnThePayScreenTeachesTheEstimateAndTakeHomeShows() = runBlocking {
        val profile = withPay()
        val rules = "제12조(연장근로수당) 연장근로에 대하여는 통상임금의 50%를 가산하여 지급한다.\n제13조(야간근로수당) 야간근로는 통상임금의 50%를 가산한다."
        val slip = "2026년 9월 급여명세서\n기본급 2,800,000\n식대 200,000\n지급합계 3,000,000\n국민연금 126,000\n건강보험 99,260\n" +
            "고용보험 25,200\n소득세 74,350\n지방소득세 7,430\n공제합계 332,240\n실수령액 2,667,760"
        val photos = com.bamnat.core.pay.PayPhotos.read(listOf(rules, slip))
        assertEquals("명세서가 둘째 사진이어도 명세서로 골라요", 1, photos.slipIndex)
        val read = photos.slip!!
        assertEquals(3_000_000L, read.gross)
        assertEquals(332_240L, read.deducted)
        assertEquals(2_667_760L, read.net)
        assertEquals(332_240.0 / 3_000_000, photos.deductionRate!!, 1e-9)

        val vm = PayViewModel(c)
        vm.observePayslip(read, YearMonth.of(2026, 8))
        val tax = c.tax(PersonalCalendar.MAIN)
        val end = System.currentTimeMillis() + 10_000
        while (tax.allObservations().isEmpty()) {
            check(System.currentTimeMillis() < end) { "the payslip was not kept" }
            shadowOf(Looper.getMainLooper()).idle()
        }
        val o = tax.allObservations().single()
        assertEquals("명세서에 적힌 달", YearMonth.of(2026, 9), o.payMonth)
        assertEquals(2_667_760L, o.net)
        assertEquals(3_000_000L, o.gross)
        assertEquals(PayObservation.Source.PAYSLIP, o.source)

        // 급여 화면·달력 요약: 세후 beside 세전, from the table × what the record taught.
        val month = YearMonth.from(Today.date())
        val f = vm.takeHome(profile, 3_000_000, month)!!
        assertTrue(f.net in 2_000_000 until 3_000_000)
        assertEquals(f.net, com.bamnat.app.ui.calendar.CalendarViewModel(c).takeHome(profile, 3_000_000, month))
        assertTrue(TakeHomeLine.text(f) { "%,d원".format(it) }.startsWith("세후 예상 "))
        // A payslip with no 실수령 to read keeps nothing.
        vm.observePayslip(com.bamnat.core.pay.PayslipReader.read("기본급 2,800,000"), month)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, tax.allObservations().size)
    }

    /** [INV-41] 해외 주식 이익 over the 250만 원 공제 last year: the investor calendar reminds on 5월 1일 and a week before 5월 신고 마감. */
    @Test fun foreignStockGainsBringAMayReportReminder() = runBlocking {
        val repo = investor()
        val acc = repo.ensureAccount()
        val base = com.bamnat.core.invest.Trade(accountId = acc, date = java.time.LocalDate.of(2026, 2, 10), symbol = "AAPL", name = "Apple",
            market = com.bamnat.core.invest.Market.US_STOCK, side = Side.BUY, quantity = 10.0, price = 100.0, fxRate = 1_400.0)
        repo.saveTrade(base)
        val today = Today.date()
        assertTrue("산 것만으로는 신고할 것이 없어요", com.bamnat.core.invest.InvestTaxes.reportReminders(repo.now(), today).isEmpty())
        repo.saveTrade(base.copy(date = java.time.LocalDate.of(2026, 6, 10), side = Side.SELL, price = 400.0))
        // 2026 gain 10 × 300 USD × 1,400 = 4,200,000원: 2027년 5월 31일(월)까지 신고.
        val events = com.bamnat.app.invest.InvestNotices.withReports(repo.now(), emptyList(), today)
        assertEquals(listOf(java.time.LocalDate.of(2027, 5, 1), java.time.LocalDate.of(2027, 5, 24)), events.map { it.date })
        assertTrue(events.all { it.id < 0 && "4,200,000" in it.memo && "374,000" in it.memo })
        val plan = com.bamnat.app.invest.InvestNotices.plansNow(c).single()
        val first = com.bamnat.core.invest.InvestAlarms.nextAny(plan.alarms, plan.events, Today.dateTime())
        assertEquals(java.time.LocalDate.of(2027, 5, 1).atTime(8, 30), first)

        // The notice itself, the morning it comes.
        shadowOf(app).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        val at = first!!.atZone(Today.zone()).toInstant().toEpochMilli()
        com.bamnat.app.invest.InvestNotices.handle(app, c, at, now = at)
        val nm = app.getSystemService(android.app.NotificationManager::class.java)
        val shown = shadowOf(nm).allNotifications.map { it.extras.getCharSequence(android.app.Notification.EXTRA_TITLE).toString() }
        assertTrue(shown.toString(), shown.any { "해외 주식 양도소득세 신고 시작" in it })
    }
}
