package com.bamnat.app.screenshots

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.IncomingRoster
import com.bamnat.app.data.InvestRepository
import com.bamnat.app.data.PersonalCalendar
import com.bamnat.app.invest.IncomingFills
import com.bamnat.app.invest.invest
import com.bamnat.app.pay.PayslipScanner
import com.bamnat.core.invest.Side
import com.bamnat.core.invest.Trade
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * [INVA-01][INVA-02][INVA-05] 1.29 묶음 AR's screenshots, numbers 1270~1279 (사용자 2026-10-27 "캡쳐로 놓기하면 자동으로 인식해서
 * 적용되게 … 시간과 수량을 보고 자동으로 넣기"): with nothing chosen the sure rows of a 체결 내역 capture go in at once and
 * only the rest is asked; a capture of sure rows only closes with one line and 되돌리기; a photo shared on a 근무 calendar
 * asks where it goes and then reaches the investor calendar; the ⋮ menu to be asked first; dark, small and 200% text.
 * Each checks what went in. The captures are made-up OCR text (PayslipScanner.fake), never a real screenshot.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ARScreenshotTest : ScreenshotBase() {
    private val c get() = app.container

    private companion object {
        const val FOLDED_NIGHT = "w369dp-h905dp-port-night-420dpi"
        const val SMALL = "w320dp-h640dp-port-320dpi"
        /** 삼성전자 written already, NAVER and 기아 new and sure, SK하이닉스 yellow (3 × 182,000 ≠ 548,000). Shots run on 10/14 10:30. */
        val CAPTURE = """
            체결내역
            삼성전자 005930 매수 10 71,500 715,000 09:12:33
            NAVER 035420 매수 4 176,300 705,200 09:20:00
            기아 000270 매수 5 98,000 490,000 09:41:10
            SK하이닉스 000660 매수 3 182,000 548,000 10:02:00
        """.trimIndent()
        val SURE = CAPTURE.lines().filterNot { it.startsWith("SK하이닉스") }.joinToString("\n")
        val SHOT: Uri = Uri.parse("content://media/external/images/media/1270")
    }

    @After fun clear() {
        PayslipScanner.fake = null
        IncomingFills.take()
        IncomingRoster.take()
    }

    private fun repo(id: String = PersonalCalendar.MAIN): InvestRepository = c.invest(id)
    private fun trades(id: String = PersonalCalendar.MAIN) = runBlocking { repo(id).now().trades }

    /** 삼성전자 09:12 written by hand, as before the capture. */
    private fun written(id: String = PersonalCalendar.MAIN) = runBlocking {
        val r = repo(id)
        val acc = r.ensureAccount()
        r.saveTrade(Trade(accountId = acc, date = today, minute = 9 * 60 + 12, symbol = "005930", name = "삼성전자", side = Side.BUY, quantity = 10.0, price = 71_500.0))
        r.setFillGuideSeen()
    }

    /** The sheet with [CAPTURE] read, the user's choice untouched (바로 넣기 on from the start). */
    private fun sheet(then: Screen.() -> Unit = {}): Screen.() -> Unit = style(WorkStyle.INVEST) {
        written()
        settle()
        val vm = com.bamnat.app.ui.invest.InvestViewModel(c, PersonalCalendar.MAIN)
        content { com.bamnat.app.ui.invest.FillCaptureSheet(vm, emptyList(), null, onDismiss = vm::closeCapture, initialText = CAPTURE) }
        val end = System.currentTimeMillis() + 10_000
        while (trades().size < 3 && System.currentTimeMillis() < end) settle()
        settle()
        then()
    }

    /** The checks every sheet shot shares: NAVER and 기아 in at their own times, 삼성전자 once, SK하이닉스 left to ask. */
    private fun Screen.sureWentInRestAsked() {
        val t = trades()
        assertEquals(3, t.size)
        assertEquals(9 * 60 + 20, t.single { it.symbol == "035420" }.minute)
        assertEquals(98_000.0, t.single { it.symbol == "000270" }.price, 0.0)
        assertEquals(1, t.count { it.symbol == "005930" })
        assertTrue(t.none { it.symbol == "000660" })
        assertFalse(shows("NAVER", exact = true))
        assertTrue(shows("SK하이닉스", exact = false))
        // Below the rows on a small phone: scrolled to.
        reveal("확실한 체결은 묻지 않고 바로 넣어요", exact = false)
        assertTrue(shows("확실한 체결은 묻지 않고 바로 넣어요", exact = false))
    }

    @Test fun sureRowsGoInOnlyTheRestIsAsked() = shoot("1270_ar_capture_auto", FOLDED, sheet {
        assertTrue(runBlocking { repo().fillTrust.first() })
        sureWentInRestAsked()
    })

    /** A photo shared while the 투자 calendar is on screen: read, the sure rows in, one line with 되돌리기. */
    private fun shared(text: String, then: Screen.() -> Unit): Screen.() -> Unit = style(WorkStyle.INVEST) {
        written()
        PayslipScanner.fake = { listOf(text, text) }
        settle()
        IncomingRoster.put(IncomingRoster.Shared.Photo(SHOT))
        val end = System.currentTimeMillis() + 15_000
        while (!(trades().size >= 3 && shows("바로 넣었어요", exact = false)) && System.currentTimeMillis() < end) settle()
        then()
    }

    @Test fun aSharedCaptureOfSureRowsGoesInWithOneLine() = shoot("1271_ar_shared_all_sure", PHONE, shared(SURE) {
        assertEquals(3, trades().size)
        assertTrue(shows("캡처에서 2건을 바로 넣었어요 · 이미 있음 1건", exact = false))
        // The first time it says where to be asked first.
        assertTrue(shows("넣기 전에 묻게 하려면 투자 탭 ⋮", exact = false))
        assertFalse(shows("확실한 체결은 묻지 않고 바로 넣어요", exact = false))
    })

    @Test fun undoTakesBackWhatWentIn() = shoot("1272_ar_shared_undo", PHONE, shared(SURE) {
        tapText("되돌리기", exact = true, scrolls = 0)
        val end = System.currentTimeMillis() + 10_000
        while (trades().size != 1 && System.currentTimeMillis() < end) settle()
        assertEquals(listOf("005930"), trades().map { it.symbol })
    })

    /** A 근무 calendar on screen and an investor calendar kept beside it. */
    private fun twoCalendars(): String {
        val id = c.calendars.begin("주식")
        c.calendars.finishSetup(id, WorkStyle.INVEST)
        c.calendars.setActive(PersonalCalendar.MAIN)
        written(id)
        return id
    }

    @Test fun aPhotoSharedOnAShiftCalendarAsksWhereItGoes() = shoot("1273_ar_shared_choice", PHONE, {
        twoCalendars()
        PayslipScanner.fake = { listOf(SURE, SURE) }
        settle()
        IncomingRoster.put(IncomingRoster.Shared.Photo(SHOT))
        settle()
        assertTrue(shows("이 사진을 어디에 넣을까요?"))
        assertTrue(shows("투자 체결 내역", exact = true))
        assertTrue(shows("근무표", exact = true))
    })

    @Test fun theChosenInvestorCalendarTakesIt() = shoot("1274_ar_shared_choice_invest", PHONE, {
        val id = twoCalendars()
        PayslipScanner.fake = { listOf(SURE, SURE) }
        settle()
        IncomingRoster.put(IncomingRoster.Shared.Photo(SHOT))
        settle()
        tapText("투자 체결 내역", exact = true, scrolls = 0)
        val end = System.currentTimeMillis() + 15_000
        while (trades(id).size < 3 && System.currentTimeMillis() < end) settle()
        settle()
        assertEquals(3, trades(id).size)
        assertEquals(id, c.calendars.active.value)
        // The 근무 calendar got nothing.
        assertTrue(trades().isEmpty())
        assertTrue(shows("바로 넣었어요", exact = false))
    })

    /** Where to have it ask first: settings search finds it (the ⋮ menu is a popup window these shots do not reach). */
    @Test fun settingsSearchSaysWhereToBeAskedFirst() = shoot("1275_ar_search_ask_first", PHONE, style(WorkStyle.INVEST) {
        tab(4, wide = false)
        typeInto("설정 찾기", "넣기 전에 묻기")
        assertTrue(shows("넣기 전에 묻게 하려면", exact = false))
        reveal("캡처로 바로 넣기", exact = false)
        assertTrue(shows("캡처로 바로 넣기", exact = false))
    })

    @Test fun sheetDark() = shoot("1276_ar_capture_auto_dark", FOLDED_NIGHT, sheet { sureWentInRestAsked() })

    @Test fun sheetSmallPhone() = shoot("1277_ar_capture_auto_small", SMALL, sheet { sureWentInRestAsked() })

    @Test fun sheetHugeText() = shoot("1278_ar_capture_auto_huge_text", PHONE, fontScale = 2f, sheet {
        assertEquals(3, trades().size)
        reveal("확실한 체결은 묻지 않고 바로 넣어요", exact = false)
        assertTrue(shows("넣기 전에 묻기", exact = true))
    })

    @Test fun askedFirstNothingGoesIn() = shoot("1279_ar_capture_asked_first", FOLDED, style(WorkStyle.INVEST) {
        written()
        runBlocking { repo().setFillTrust(false) }
        settle()
        val vm = com.bamnat.app.ui.invest.InvestViewModel(c, PersonalCalendar.MAIN)
        content { com.bamnat.app.ui.invest.FillCaptureSheet(vm, emptyList(), null, onDismiss = vm::closeCapture, initialText = CAPTURE) }
        settle()
        val end = System.currentTimeMillis() + 10_000
        while (!shows("아직 안 적은 체결", exact = false) && System.currentTimeMillis() < end) settle()
        assertEquals(1, trades().size)
        assertTrue(shows("아직 안 적은 체결 3건", exact = false))
        assertTrue(shows("넣고, 다음부터 묻지 않고 바로 넣기", exact = false))
    })
}
