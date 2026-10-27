package com.bamnat.app.invest

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.appContainer
import com.bamnat.app.data.InvestRepository
import com.bamnat.app.data.PersonalCalendar
import com.bamnat.app.data.db.AppDatabase
import com.bamnat.app.ui.UndoMessage
import com.bamnat.app.ui.invest.InvestState
import com.bamnat.app.ui.invest.InvestViewModel
import com.bamnat.core.invest.CashFlow
import com.bamnat.core.invest.CashKind
import com.bamnat.core.invest.FillReconcile
import com.bamnat.core.invest.FillSheetReader
import com.bamnat.core.invest.InvestMonthSummary
import com.bamnat.core.invest.Market
import com.bamnat.core.invest.Portfolio
import com.bamnat.core.invest.Side
import com.bamnat.core.invest.Trade
import com.bamnat.core.invest.TradeAlerts
import com.bamnat.core.time.Today
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId

/**
 * [INVA-01][INVA-02][INVA-05] 1.29 AR (사용자 2026-10-27 "캡쳐로 놓기하면 자동으로 인식해서 적용되게 … 시간과 수량을 보고 자동으로
 * 넣기"), end to end through the 투자 screen's model: a capture (지어낸 OCR 글자) read, put beside what is written and what
 * waits; with nothing chosen the sure rows go in without a question (a waiting notification confirmed), only the rest is
 * asked; 되돌리기 takes it all back; it shows at once in the calendar, 원금·현금 and the month's numbers, and stays after the
 * app is opened again. Before 1.29 nothing went in until the user had found '다음부터 묻지 않고 바로 넣기'.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ARCaptureFlowTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val zone = ZoneId.of("Asia/Seoul")
    private val today = LocalDate.of(2026, 10, 27)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    /** 삼성전자 written already · NAVER new and sure · 기아 the waiting notification's · SK하이닉스 yellow (3 × 182,000 ≠ 548,000). */
    private val capture = """
        체결내역
        삼성전자 005930 매수 10 71,500 715,000 09:12:33
        NAVER 035420 매수 4 176,300 705,200 11:20:00
        기아 000270 매수 5 98,000 490,000 13:02:10
        SK하이닉스 000660 매수 3 182,000 548,000 13:40:00
    """.trimIndent()

    @Before fun fix() = Today.fix(today.atTime(15, 40).atZone(zone))

    @After fun reset() {
        scope.cancel()
        com.bamnat.app.pay.PayslipScanner.fake = null
        Today.reset()
    }

    private fun at(h: Int, m: Int) = today.atTime(h, m).atZone(zone).toInstant().toEpochMilli()

    /** Waits for the model's work (the database runs on its own threads) without sleeping the screen's thread. */
    private fun until(what: String, ok: () -> Boolean) {
        val end = System.currentTimeMillis() + 10_000
        while (!ok()) {
            ShadowLooper.idleMainLooper()
            check(System.currentTimeMillis() < end) { "기다렸지만 안 됨: $what" }
            runBlocking { delay(15) }
        }
    }

    private fun setUp(): Pair<InvestViewModel, MutableList<UndoMessage>> {
        val c = context.appContainer
        val repo = InvestRepository.of(c.database)
        runBlocking {
            val acc = repo.ensureAccount()
            repo.saveCash(CashFlow(accountId = acc, date = today.minusDays(30), kind = CashKind.DEPOSIT, amount = 10_000_000))
            repo.saveTrade(Trade(accountId = acc, date = today, minute = 9 * 60 + 12, symbol = "005930", name = "삼성전자", side = Side.BUY, quantity = 10.0, price = 71_500.0))
            repo.addAlert(TradeAlerts.Pending(at(13, 2), "키움", Side.BUY, "", "기아", 5.0, 0.0, Market.KR_STOCK))
        }
        val vm = InvestViewModel(c, PersonalCalendar.MAIN)
        val said = mutableListOf<UndoMessage>()
        scope.launch { vm.messages.collect { said += it } }
        return vm to said
    }

    private fun repo() = InvestRepository.of(context.appContainer.database)
    private fun trades() = runBlocking { repo().now().trades }

    @Test fun sureRowsGoInAtOnceTheWaitingOneIsConfirmedAndOnlyTheRestIsAsked() {
        val (vm, said) = setUp()
        assertTrue("1.29부터 처음부터 바로 넣기", runBlocking { repo().fillTrustNow() })
        vm.takeCapture(capture)
        until("바로 넣기") { trades().size == 3 && vm.captureRead.value != null }
        val t = trades()
        // NAVER went in at its own time; 기아 took the notification's price and left the waiting list; 삼성전자 once.
        val naver = t.single { it.symbol == "035420" }
        assertEquals(176_300.0, naver.price, 0.0)
        assertEquals(11 * 60 + 20, naver.minute)
        assertEquals(today, naver.date)
        assertEquals(98_000.0, t.single { it.name == "기아" }.price, 0.0)
        assertEquals(listOf("알림:키움"), t.single { it.name == "기아" }.tags)
        assertTrue(runBlocking { repo().alertsNow() }.isEmpty())
        assertEquals(1, t.count { it.symbol == "005930" })
        assertTrue(t.none { it.symbol == "000660" })
        // The sheet stays open for SK하이닉스 only.
        val read = vm.captureRead.value!!
        assertTrue(vm.capturing.value)
        assertEquals(2, read.putQuietly)
        assertEquals(1, read.same)
        val left = read.fills.filterIndexed { i, _ -> i !in read.done }
        assertEquals(listOf("SK하이닉스"), left.map { it.name })
        val r = FillReconcile.reconcile(left, read.written, read.waiting, read.day, zone)
        assertEquals(1, r.asks.size)
        assertTrue(r.autoNew.isEmpty())
        // One line, with where to have it ask the first time, and 되돌리기 for all of it.
        until("알림 한 줄") { said.isNotEmpty() }
        val line = said.single()
        assertTrue(line.message, line.message.startsWith("캡처에서 2건을 바로 넣었어요 · 이미 있음 1건"))
        assertTrue(line.message, line.message.contains("넣기 전에 묻게 하려면 투자 탭 ⋮"))
        vm.run(line.undo!!)
        until("되돌리기") { trades().size == 1 && runBlocking { repo().alertsNow() }.size == 1 }
        assertEquals("005930", trades().single().symbol)
    }

    @Test fun aCaptureWhoseRowsAreAllSureClosesTheSheetAndSaysWhereToAskOnlyOnce() {
        val (vm, said) = setUp()
        val sure = capture.lines().filterNot { it.startsWith("SK하이닉스") }.joinToString("\n")
        vm.takeCapture(sure)
        until("바로 넣기") { trades().size == 3 && said.isNotEmpty() }
        assertFalse(vm.capturing.value)
        assertNull(vm.captureRead.value)
        // The same capture again: everything is written, nothing goes in twice.
        vm.takeCapture(sure)
        until("두 번째 알림") { said.size == 2 }
        assertEquals(3, trades().size)
        assertEquals("이미 적은 체결이에요 · 이미 있음 3건", said[1].message)
        // Another sure capture later: no second hint.
        vm.takeCapture("셀트리온 068270 매도 2 180,000 360,000 14:10:00")
        until("세 번째 알림") { said.size == 3 }
        assertEquals("캡처에서 1건을 바로 넣었어요", said[2].message)
    }

    @Test fun whenTheUserChoseToBeAskedNothingGoesInUntilChecked() {
        val (vm, _) = setUp()
        runBlocking { repo().setFillTrust(false) }
        vm.takeCapture(capture)
        until("읽기") { vm.captureRead.value != null }
        assertEquals(1, trades().size)
        assertEquals(3, vm.captureRead.value!!.fills.size - vm.captureRead.value!!.done.size)
        assertEquals(0, vm.captureRead.value!!.putQuietly)
    }

    @Test fun aSharedScreenshotIsReadByTheModelAndStillGoesInWhenTheSheetIsClosed() {
        val (vm, _) = setUp()
        com.bamnat.app.pay.PayslipScanner.fake = { listOf(capture, capture) }
        vm.readCapture(android.net.Uri.parse("content://media/external/images/media/1"))
        // The user closes the sheet (or the screen goes off) while it reads.
        vm.closeCapture()
        until("공유 캡처 넣기") { trades().size == 3 }
        // What is left to ask brings the sheet back.
        until("시트 다시") { vm.capturing.value && vm.captureRead.value != null }
    }

    @Test fun aCaptureOfAnotherDayGoesInOnThatDay() {
        val (vm, _) = setUp()
        val text = """
            주문내역
            10월 24일
            카카오  2,062,500원
            50주 판매 · 오후 2:42
            1주당 41,250원
        """.trimIndent()
        vm.takeCapture(text)
        until("넣기") { trades().any { it.symbol.isEmpty() && it.name == "카카오" } }
        val k = trades().single { it.name == "카카오" }
        assertEquals(LocalDate.of(2026, 10, 24), k.date)
        assertEquals(14 * 60 + 42, k.minute)
        // A table without a day on its rows, from a screenshot taken yesterday: yesterday's.
        vm.takeCapture("현대차 005380 매수 2 250,000 500,000 10:15:00", day = today.minusDays(1))
        until("어제 캡처") { trades().any { it.symbol == "005380" } }
        assertEquals(today.minusDays(1), trades().single { it.symbol == "005380" }.date)
    }

    @Test fun whatWentInShowsInTheCalendarMoneyAndMonthAndStaysAfterReopening() = runBlocking {
        val db = Room.databaseBuilder(context, AppDatabase::class.java, "ar-capture.db").allowMainThreadQueries().build()
        val repo = InvestRepository(db)
        val acc = repo.ensureAccount()
        repo.saveCash(CashFlow(accountId = acc, date = today.minusDays(30), kind = CashKind.DEPOSIT, amount = 10_000_000))
        val fills = FillSheetReader.read(capture, today)
        val r = FillReconcile.reconcile(fills, repo.now().trades, repo.alertsNow(), today, zone)
        val applied = repo.applyCapture(r.autoNew.map { FillReconcile.pending(it, zone, today) }, emptyList())
        assertEquals(3, applied.written.size) // 삼성전자, NAVER, 기아 (nothing written before here)
        val book = repo.now()
        val state = InvestState.of(book, null, today)
        // 투자 달력: the day's trades; 원금 stays the deposit, 현금 goes down by what was bought (and the fee).
        assertEquals(3, state.tradesOn(today).size)
        val s = Portfolio.build(book).summary
        assertEquals(10_000_000L, s.principal)
        val bought = 715_000L + 705_200L + 490_000L
        assertTrue("현금 ${s.cash}", s.cash in (10_000_000L - bought - 2_000)..(10_000_000L - bought))
        assertEquals(3, InvestMonthSummary.of(book, YearMonth.from(today)).buys)
        // Opened again: still there.
        db.close()
        val again = Room.databaseBuilder(context, AppDatabase::class.java, "ar-capture.db").allowMainThreadQueries().build()
        val back = InvestRepository(again)
        assertEquals(3, back.now().trades.size)
        assertEquals(LocalTime.of(11, 20).let { it.hour * 60 + it.minute }, back.now().trades.single { it.symbol == "035420" }.minute)
        // 되돌리기 after reopening still takes exactly those three away.
        back.undoCapture(applied)
        assertTrue(back.now().trades.isEmpty())
        assertEquals(10_000_000L, Portfolio.build(back.now()).summary.cash)
        again.close()
        context.deleteDatabase("ar-capture.db")
        Unit
    }

    @Test fun moreBrokersTradeTextsAreRead() {
        val tc = TradeCapture
        // 카카오페이·토스 say it in words; a shop's 구매 완료 is not a trade.
        assertTrue(tc.worthReading("com.kakaopay.app", "삼성전자 10주를 71,500원에 샀어요", false))
        assertTrue(tc.worthReading("com.kakao.talk", "[토스증권] NAVER 3주 구매 완료 · 1주당 176,300원", true))
        assertFalse(tc.worthReading("com.coupang.mobile", "주문하신 상품 구매 완료 15,000원", false))
        val a = com.bamnat.core.invest.TradeAlertParser.parse("[NH투자증권] 체결통보 삼성전자 현금매수 10주 71,500원", "com.nh.mobilenh")!!
        assertEquals("삼성전자", a.name)
    }
}
