package com.bamnat.app.invest

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.data.BackupManager
import com.bamnat.app.data.InvestRepository
import com.bamnat.app.data.SettingsRepository
import com.bamnat.app.data.db.AppDatabase
import com.bamnat.core.invest.FillReconcile
import com.bamnat.core.invest.FillSheetReader
import com.bamnat.core.invest.Market
import com.bamnat.core.invest.Side
import com.bamnat.core.invest.Trade
import com.bamnat.core.invest.TradeAlerts
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

/**
 * [INV-42][INV-43][INV-44] 1.27 AK 순번 450, end to end below the screen: a capture's text (지어낸 OCR 글자) read, put beside
 * what is written (같음 건너뜀 · 다름 고침 요청 · 새 것), the new ones and a fix written as trades, all of it taken back with
 * 되돌리기; reading the same capture again puts nothing in twice; 묻지 않기 survives a backup.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class AKCaptureFlowTest {
    private val app get() = ApplicationProvider.getApplicationContext<android.app.Application>()
    private lateinit var db: AppDatabase
    private lateinit var repo: InvestRepository
    private val zone: ZoneId get() = com.bamnat.core.time.Today.zone()
    private val today = LocalDate.of(2026, 10, 26)

    private val ocr = """
        체결내역
        삼성전자 005930 매수 10 71,500 715,000 09:12:33
        카카오 035720 매도 5 41,250 206,250 10:05:00
        NAVER 035420 매수 4 176,300 705,200 11:20:00
        SK하이닉스 000660 매수 3 182,000 548,000 13:40:00
    """.trimIndent()

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
        repo = InvestRepository(db)
    }

    @After fun close() = db.close()

    private fun read() = FillSheetReader.read(ocr).map { it.copy(date = it.date ?: today) }

    @Test fun sameSkippedDifferentAskedNewWrittenAndUndone() = runBlocking {
        val acc = repo.ensureAccount()
        val sam = repo.saveTrade(Trade(accountId = acc, date = today, minute = 9 * 60 + 12, symbol = "005930", name = "삼성전자", side = Side.BUY, quantity = 10.0, price = 71_500.0))
        val kakao = repo.saveTrade(Trade(accountId = acc, date = today, minute = 10 * 60 + 5, symbol = "035720", name = "카카오", side = Side.SELL, quantity = 5.0, price = 41_000.0, memo = "직접"))
        val r = FillReconcile.reconcile(read(), repo.now().trades, repo.alertsNow(), today, zone)
        assertEquals(listOf(FillReconcile.Kind.SAME, FillReconcile.Kind.DIFFERENT, FillReconcile.Kind.NEW, FillReconcile.Kind.NEW), r.items.map { it.kind })
        assertEquals(sam, r.same.single().trade!!.id)
        // The yellow SK하이닉스 (3 × 182,000 ≠ 548,000) is never put in without a question.
        assertEquals(listOf("035420"), r.sureNew.map { it.fill.symbol })
        assertEquals(2, r.toAsk.size)

        // The user ticks the fix and puts NAVER in.
        val done = repo.applyCapture(r.sureNew.map { FillReconcile.pending(it, zone, today) }, listOfNotNull(FillReconcile.fixed(r.different.single(), zone)))
        val after = repo.now().trades
        assertEquals(3, after.size)
        assertEquals(41_250.0, after.single { it.id == kakao }.price, 0.0)
        assertEquals("직접", after.single { it.id == kakao }.memo)
        val naver = after.single { it.symbol == "035420" }
        assertEquals(176_300.0, naver.price, 0.0)
        assertEquals(today, naver.date)
        assertEquals(11 * 60 + 20, naver.minute)
        assertTrue(naver.fee > 0)

        // The same capture again: everything written is skipped, nothing goes in twice.
        val again = FillReconcile.reconcile(read(), after, repo.alertsNow(), today, zone)
        assertEquals(3, again.same.size)
        assertTrue(again.sureNew.isEmpty())

        // 되돌리기.
        repo.undoCapture(done)
        val back = repo.now().trades
        assertEquals(2, back.size)
        assertEquals(41_000.0, back.single { it.id == kakao }.price, 0.0)
    }

    @Test fun aWaitingAlertFilledFromTheCaptureIsWrittenAndLeavesTheList() = runBlocking {
        repo.ensureAccount()
        val at = today.atTime(9, 12).atZone(zone).toInstant().toEpochMilli()
        repo.addAlert(TradeAlerts.Pending(at, "한국투자", Side.BUY, "005930", "삼성전자", 10.0, 0.0, Market.KR_STOCK))
        val r = FillReconcile.reconcile(read(), repo.now().trades, repo.alertsNow(), today, zone)
        val sam = r.new.single { it.alert != null }
        val done = repo.applyCapture(listOf(FillReconcile.pending(sam, zone, today)), emptyList())
        assertTrue(repo.alertsNow().isEmpty())
        val t = repo.now().trades.single()
        assertEquals(71_500.0, t.price, 0.0)
        assertEquals(listOf("알림:한국투자"), t.tags)
        repo.undoCapture(done)
        assertEquals(1, repo.alertsNow().size)
        assertTrue(repo.now().trades.isEmpty())
    }

    @Test fun askNoMoreSurvivesABackup() = runBlocking {
        com.bamnat.app.data.ScheduleRepository(db).ensureDefaults()
        assertFalse(repo.fillTrust.first())
        repo.setFillTrust(true)
        val manager = BackupManager(db, SettingsRepository(app))
        val json = manager.export()
        repo.setFillTrust(false)
        manager.restore(json)
        assertTrue(repo.fillTrust.first())
    }
}
