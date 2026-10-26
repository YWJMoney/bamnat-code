package com.bamnat.app.screenshots

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.data.InvestRepository
import com.bamnat.app.data.PersonalCalendar
import com.bamnat.app.data.db.toEntity
import com.bamnat.core.invest.Market
import com.bamnat.core.invest.Side
import com.bamnat.core.invest.Trade
import com.bamnat.core.invest.TradeAlerts
import com.bamnat.core.memo.Memo
import com.bamnat.core.time.Today
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * [INV-42..45][MEMO-03..07] 1.27 묶음 AK's screenshots, numbers 1160~1179 (순번 450·451), each checking what its taps saved:
 * 체결 내역 캡처 — written ones skipped, a different one asked side by side, only new ones checked and written as trades;
 * '다음부터 묻지 않고 바로 넣기' and after it the sure new rows going in at once while a difference and a yellow row are still
 * asked; the ⋮ menu to ask again. 메모 — 초성·날짜·근무 search, '야간 날' chip, a 50,000-letter memo's card around the word,
 * '다음 야간 때' offered as the next night's time, the dialog's 근무에 맞춰 chips, 지난 같은 근무 메모, 200% date rows, and a
 * thousand memos.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class AKScreenshotTest : ScreenshotBase() {
    private val c get() = app.container

    private companion object {
        const val FOLDED_NIGHT = "w369dp-h905dp-port-night-420dpi"
        /** A capture's text as the phone reads it (지어낸 배치): 삼성전자 written already, 카카오 written at 41,000, NAVER new, SK하이닉스 yellow. */
        val CAPTURE = """
            체결내역
            삼성전자 005930 매수 10 71,500 715,000 09:12:33
            카카오 035720 매도 5 41,250 206,250 10:05:00
            NAVER 035420 매수 4 176,300 705,200 11:20:00
            SK하이닉스 000660 매수 3 182,000 548,000 13:40:00
        """.trimIndent()
    }

    // ---- 450 캡처로 채우기 ----

    private fun repo() = InvestRepository.of(c.database)

    private fun written(): Pair<Long, Long> = runBlocking {
        val r = repo()
        val acc = r.ensureAccount()
        val a = r.saveTrade(Trade(accountId = acc, date = today, minute = 9 * 60 + 12, symbol = "005930", name = "삼성전자", side = Side.BUY, quantity = 10.0, price = 71_500.0))
        val b = r.saveTrade(Trade(accountId = acc, date = today, minute = 10 * 60 + 5, symbol = "035720", name = "카카오", side = Side.SELL, quantity = 5.0, price = 41_000.0, memo = "직접 적음"))
        r.setFillGuideSeen()
        a to b
    }

    private fun capture(trust: Boolean = false, then: Screen.(Pair<Long, Long>) -> Unit = {}): Screen.() -> Unit = style(WorkStyle.INVEST) {
        val ids = written()
        if (trust) runBlocking { repo().setFillTrust(true) }
        settle()
        val vm = com.bamnat.app.ui.invest.InvestViewModel(c, PersonalCalendar.MAIN)
        // The trades are in the screen's state before the capture is read, as when the 투자 tab is open.
        val end = System.currentTimeMillis() + 10_000
        while (vm.state.value.full.trades.size < 2 && System.currentTimeMillis() < end) settle()
        content { com.bamnat.app.ui.invest.FillCaptureSheet(vm, emptyList(), null, onDismiss = {}, initialText = CAPTURE) }
        settle()
        then(ids)
    }

    private fun trades() = runBlocking { repo().now().trades }

    @Test fun captureSkipsAsksAndChecksOnlyNew() = shoot("1160_ak_capture_review", FOLDED, capture {
        assertTrue(shows("이미 적은 체결 1건은 건너뛰어요"))
        assertTrue(shows("기록과 다른 체결 1건 · 고칠까요?"))
        assertTrue(shows("아직 안 적은 체결 2건"))
        assertTrue(shows("수량 × 단가가 금액과 안 맞아요", exact = false))
        // NAVER is ticked; the yellow SK하이닉스 is not until looked at; 카카오 stays as written unless ticked.
        tapText("넣기 1건", exact = true)
        val t = trades()
        assertEquals(3, t.size)
        assertEquals(176_300.0, t.single { it.symbol == "035420" }.price, 0.0)
        assertEquals(41_000.0, t.single { it.symbol == "035720" }.price, 0.0)
        assertEquals(1, t.count { it.symbol == "005930" })
        assertTrue(t.none { it.symbol == "000660" })
    })

    @Test fun captureFixesWhenTickedAndTrustsAfter() = shoot("1161_ak_capture_fix_trust", PHONE, capture { (_, kakao) ->
        tapText("카카오 캡처대로 고치기", exact = true)
        // The user looks at SK하이닉스 and fixes the misread amount: 546,000.
        typeInto("금액 확인", "546000")
        assertTrue(shows("넣기 3건", exact = true))
        tapText("넣고, 다음부터 묻지 않고 바로 넣기")
        val t = trades()
        val k = t.single { it.id == kakao }
        assertEquals(41_250.0, k.price, 0.0)
        assertEquals("직접 적음", k.memo) // only quantity, price and minute change
        assertEquals(182_000.0, t.single { it.symbol == "000660" }.price, 0.0)
        assertEquals(4, t.size)
        assertTrue(runBlocking { repo().fillTrust.first() })
    })

    @Test fun trustedCapturePutsSureNewAtOnceAndStillAsks() = shoot("1162_ak_capture_trusted", FOLDED, capture(trust = true) {
        // NAVER went in without a question; 카카오 (different) and SK하이닉스 (yellow) are still asked.
        assertEquals(176_300.0, trades().single { it.symbol == "035420" }.price, 0.0)
        assertTrue(shows("확실한 1건은 묻지 않고 바로 넣었어요", exact = false))
        assertTrue(shows("기록과 다른 체결 1건 · 고칠까요?"))
        assertTrue(shows("아직 안 적은 체결 1건"))
        assertFalse(shows("NAVER", exact = true))
        assertEquals(41_000.0, trades().single { it.symbol == "035720" }.price, 0.0)
        assertTrue(trades().none { it.symbol == "000660" })
        assertTrue(shows("묻지 않고 바로 넣기가 켜져 있어요"))
    })

    @Test fun trustedCaptureUndo() = shoot("1163_ak_capture_trusted_dark", FOLDED_NIGHT, capture(trust = true) {
        val before = trades().size
        assertEquals(3, before)
        // 되돌리기 takes back what went in without asking.
        val applied = runBlocking {
            val r = repo()
            val p = TradeAlerts.Pending(Today.millis(), "체결 내역 캡처", Side.BUY, "035900", "JYP", 2.0, 60_000.0, Market.KR_STOCK)
            r.applyCapture(listOf(p), emptyList())
        }
        assertEquals(4, trades().size)
        runBlocking { repo().undoCapture(applied) }
        assertEquals(3, trades().size)
    })

    @Test fun captureAsksAgain() = shoot("1164_ak_capture_ask_again", PHONE, capture(trust = true) {
        // '다시 묻기' on the sheet turns asking back on (the ⋮ menu of the 투자 tab does the same).
        tapText("다시 묻기", exact = true)
        assertFalse(runBlocking { repo().fillTrust.first() })
    })

    // ---- 451 메모 ----

    private fun memos(then: Screen.() -> Unit): Screen.() -> Unit = {
        runBlocking {
            val m = c.memoRepository
            // 10/9 was a 주간 like today (the seed's 주주야야휴 from 7/1): the last 주간 memo before today's 인수인계.
            m.save(Memo(date = today.minusDays(5), title = "지난 주간 인수인계", body = "3번 라인 센서 교체 완료\n열쇠는 경비실"))
            m.save(Memo(title = "열쇠", body = "다음 야간 때 열쇠 챙기기"))
        }
        tab(2, wide = false)
        then()
    }

    @Test fun memoChoseongSearch() = shoot("1165_ak_memo_search_choseong", FOLDED, memos {
        typeInto("제목, 내용, #태그 검색", "ㅇㅅㅇㄱ")
        assertTrue(shows("인수인계", exact = true))
        assertFalse(shows("준비물", exact = true))
    })

    @Test fun memoShiftSearchAndChip() = shoot("1166_ak_memo_search_shift", FOLDED, memos {
        tapText("필터 펼치기", exact = true, scrolls = 0)
        tapText("야간 날", exact = true, scrolls = 0)
        assertTrue(shows("야간 특이사항", exact = true))
        assertFalse(shows("준비물", exact = true))
    })

    @Test fun memoDateSearch() = shoot("1167_ak_memo_search_date", PHONE, memos {
        typeInto("제목, 내용, #태그 검색", "${today.monthValue}월 ${today.dayOfMonth}일")
        assertTrue(shows("준비물", exact = true))
        assertFalse(shows("야간 특이사항", exact = true))
    })

    @Test fun longMemoCardShowsTheWord() = shoot("1168_ak_memo_long_search", FOLDED, {
        runBlocking { c.memoRepository.save(Memo(title = "긴 기록", body = "처음 줄\n" + "가나다라마바사 ".repeat(6_000) + "\n여기에 비상 연락처 정리\n" + "끝 ".repeat(1_000))) }
        tab(2, wide = false)
        tapText("일반 메모", prefix = true, scrolls = 0)
        typeInto("제목, 내용, #태그 검색", "비상")
        assertTrue(shows("…여기에 비상 연락처 정리", exact = false))
    })

    @Test fun memoOffersTheNextNight() = shoot("1169_ak_memo_shift_offer", FOLDED, memos {
        tapText("일반 메모", prefix = true, scrolls = 0)
        tapText("다음 야간 때 열쇠 챙기기")
        // 10/14 is 주간; the next 야간 is 10/16 from 20:00, so an hour before.
        assertTrue(shows("다음 야간(10/16) 출근 1시간 전", exact = false))
    })

    @Test fun memoScheduleShiftChips() = shoot("1170_ak_memo_shift_chips", FOLDED, memos {
        tapText("일반 메모", prefix = true, scrolls = 0)
        tapText("다음 야간 때 열쇠 챙기기")
        tapText("일정으로 넣기", exact = true)
        assertTrue(shows("근무에 맞춰", exact = true))
        tapText("저장", exact = true)
        back() // the editor saves as it closes
        settle()
        val saved = runBlocking { c.memoRepository.memos.first().single { it.title == "열쇠" } }
        assertEquals(LocalDateTime.of(today.plusDays(2), LocalTime.of(19, 0)), saved.reminder?.at)
    })

    @Test fun memoLastSameShift() = shoot("1171_ak_memo_last_same_shift", FOLDED, memos {
        tapText("3번 라인 센서 점검 요청", exact = false)
        tapText("지난 주간(10/9) 메모 1개 보기", exact = false)
        assertTrue(shows("열쇠는 경비실", exact = false))
    })

    @Test fun memosHugeTextDateRows() = shoot("1172_ak_memos_huge_text", PHONE, fontScale = 2f) {
        tab(2, wide = false)
        val m = app.resources.displayMetrics
        val cuts = LayoutCheck.cuts(nodes(merged = false), m.widthPixels, m.heightPixels)
            .filter { cut -> cut.how == "말줄임" && listOf("주간", "야간", "오후", "휴무").any { cut.text.startsWith(it) } }
        LayoutCheck.report("ak_memos_huge_text", cuts)
        assertEquals("$cuts", emptyList<LayoutCheck.Cut>(), cuts)
    }

    /** [MEMO-04] 메모 1,000개(그중 10개는 5만 자): 목록과 찾기가 1초 안에. */
    @Test fun aThousandMemos() = shoot("1173_ak_memos_thousand", FOLDED, {
        val big = "야간 근무 인수인계 ".repeat(5_000).take(50_000)
        runBlocking {
            val dao = c.database.memoDao()
            for (i in 1..1_000) {
                val memo = Memo(date = if (i % 2 == 0) today.minusDays((i % 300).toLong()) else null, title = "메모 $i",
                    body = if (i % 100 == 0) "$big 끝$i" else "내용 $i #태그${i % 7}", createdAtMillis = i.toLong(), updatedAtMillis = i.toLong())
                dao.insert(memo.toEntity())
            }
        }
        val vm = com.bamnat.app.ui.memo.MemoListViewModel(c)
        val t0 = System.nanoTime()
        content { com.bamnat.app.ui.memo.MemoListScreen(vm, onOpen = {}, onNew = { _, _ -> }, onOpenTrash = {}) }
        val end = System.currentTimeMillis() + 20_000
        while (vm.state.value.calendarCount < 500 && System.currentTimeMillis() < end) settle()
        val list = (System.nanoTime() - t0) / 1_000_000
        val t1 = System.nanoTime()
        vm.setQuery("끝500")
        while (vm.state.value.query != "끝500" && System.currentTimeMillis() < end + 20_000) settle()
        val search = (System.nanoTime() - t1) / 1_000_000
        println("AK 메모 1,000개: 목록 ${list}ms, 찾기 ${search}ms")
        assertEquals(1, vm.state.value.shownCount)
        assertTrue("목록 ${list}ms", list < 15_000)
        assertTrue("찾기 ${search}ms", search < 5_000)
        assertNotNull(vm.state.value)
    })
}
