package com.bamnat.core.invest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 2.0.0 [RVK-26] (검토 U06b-01·I06bb-01) 잔고 캡처. The screens here are written for this test in the shapes 잔고 screens take (a
 * card per holding with its words, a short list, a table under a header, coins, dollars); none is copied from an app.
 * Amounts are made up.
 */
class HoldingSheetReaderTest {
    @Test fun aCardPerHoldingWithItsWords() {
        val rows = HoldingSheetReader.read("""
            총 평가금액 1,280,900원
            삼성전자
            보유수량 10주
            평균단가 70,000원
            현재가 73,400원
            평가금액 734,000원
            평가손익 +34,000원 (+4.86%)
            SK하이닉스
            보유수량 3주
            평균단가 180,000원
            현재가 182,300원
            평가금액 546,900원
            평가손익 +6,900원 (+1.28%)
        """.trimIndent())
        assertEquals(2, rows.size)
        val s = rows[0]
        assertEquals("삼성전자", s.name)
        assertEquals(10.0, s.quantity, 0.0)
        assertEquals(73_400.0, s.price, 0.0)
        assertEquals(734_000.0, s.value, 0.0)
        assertEquals(70_000.0, s.average, 0.0)
        assertEquals(true, s.checked)
        assertFalse(s.unsure)
        assertEquals(182_300.0, rows[1].price, 0.0)
        assertEquals("SK하이닉스", rows[1].name)
    }

    @Test fun aShortListOfQuantityAndValue() {
        val rows = HoldingSheetReader.read("""
            삼성전자
            10주 · 734,000원
            +34,000원 (4.86%)
            KODEX 200
            5주 · 186,250원
            -1,250원 (-0.67%)
        """.trimIndent())
        assertEquals(listOf("삼성전자", "KODEX 200"), rows.map { it.name })
        assertEquals(73_400.0, rows[0].price, 0.0)
        assertTrue(rows[0].priceFromValue)
        // One number beside the quantity could be the price or the value: asked.
        assertTrue(rows[0].unsure)
        assertEquals(37_250.0, rows[1].price, 0.0)
        assertEquals(Market.KR_ETF, rows[1].market)
        assertEquals(5.0, rows[1].quantity, 0.0)
    }

    @Test fun aTableUnderAHeader() {
        val rows = HoldingSheetReader.read("""
            종목명 보유수량 평균가 현재가 평가금액 평가손익 수익률
            삼성전자 10 70,000 73,400 734,000 34,000 4.86%
            카카오 7 52,000 41,050 287,350 -76,650 -21.06%
            예수금 120,000
        """.trimIndent())
        assertEquals(2, rows.size)
        assertEquals(41_050.0, rows[1].price, 0.0)
        assertEquals(7.0, rows[1].quantity, 0.0)
        assertEquals(52_000.0, rows[1].average, 0.0)
        assertFalse(rows[1].unsure)
    }

    @Test fun coinsWithoutAPriceGetOneFromTheValue() {
        val rows = HoldingSheetReader.read("""
            비트코인 BTC
            보유수량 0.02 BTC
            매수평균가 95,000,000 KRW
            평가금액 1,920,000 KRW
            평가손익 +20,000 KRW
        """.trimIndent())
        val b = rows.single()
        assertEquals("비트코인", b.name)
        assertEquals("BTC", b.symbol)
        assertEquals(Market.CRYPTO, b.market)
        assertEquals(0.02, b.quantity, 1e-12)
        assertEquals(96_000_000.0, b.price, 0.0)
        assertTrue(b.priceFromValue)
        assertEquals(95_000_000.0, b.average, 0.0)
    }

    @Test fun dollarsKeepTheirPriceAndLeaveTheWonValueAside() {
        val rows = HoldingSheetReader.read("""
            애플 AAPL
            보유 3주
            평균 ${'$'}180.50
            현재가 ${'$'}195.20
            평가금액 ${'$'}585.60 (819,840원)
        """.trimIndent())
        val a = rows.single()
        assertEquals("애플", a.name)
        assertEquals("AAPL", a.symbol)
        assertEquals(Market.US_STOCK, a.market)
        assertEquals(195.2, a.price, 1e-9)
        assertEquals(585.6, a.value, 1e-9)
        assertFalse(a.unsure)
    }

    @Test fun numbersThatDoNotAgreeAreAsked() {
        val r = HoldingSheetReader.read("""
            삼성전자
            보유수량 10주
            현재가 73,400원
            평가금액 784,000원
        """.trimIndent()).single()
        assertEquals(false, r.checked)
        assertTrue(r.unsure)
    }

    @Test fun nothingToReadIsNothing() {
        assertTrue(HoldingSheetReader.read("내 자산\n총 평가금액 0원").isEmpty())
        assertTrue(HoldingSheetReader.read("").isEmpty())
    }

    private fun holding(name: String, qty: Double, avg: Double, symbol: String = "", market: Market = Market.KR_STOCK) =
        Holding(holdingKey(symbol, name), symbol, name, market, null, qty, qty * avg, qty * avg, null, 1.0, 0.0, 0L, LocalDate.of(2026, 1, 2), 1)

    @Test fun rowsFindTheirHoldingsAndSaySplitsAndGaps() {
        val rows = HoldingSheetReader.read("""
            삼성전자(005930)
            보유수량 50주
            현재가 14,680원
            평가금액 734,000원
            카카오
            보유수량 9주
            현재가 41,050원
            평가금액 369,450원
            네이버
            보유수량 1주
            현재가 210,000원
        """.trimIndent())
        val found = HoldingShots.match(rows, listOf(holding("삼성전자", 10.0, 70_000.0, "005930"), holding("카카오", 7.0, 52_000.0)))
        assertEquals(3, found.size)
        // 10주 → 50주: a 1:5 split, the price a fifth.
        assertEquals(40.0, found[0].gap!!, 0.0)
        assertEquals(5.0, found[0].ratio!!, 0.0)
        // 7 → 9: no whole split, two shares missing from the book.
        assertEquals(2.0, found[1].gap!!, 0.0)
        assertNull(found[1].ratio)
        // Not in the book: the user writes its buy first.
        assertNull(found[2].holding)
    }

    @Test fun aLoneNumberThatIsThePriceIsPutRight() {
        // "10주 · 73,400원": the number is the price, not 평가금액 (73,400 ÷ 10 = 7,340 is far from what the shares cost).
        val rows = HoldingSheetReader.read("삼성전자\n10주 · 73,400원")
        val f = HoldingShots.match(rows, listOf(holding("삼성전자", 10.0, 70_000.0))).single()
        assertEquals(73_400.0, f.price, 0.0)
        assertTrue(f.unsure)
    }

    @Test fun aMergeIsAFraction() {
        val rows = HoldingSheetReader.read("에코프로\n보유수량 2주\n현재가 500,000원\n평가금액 1,000,000원")
        val f = HoldingShots.match(rows, listOf(holding("에코프로", 20.0, 50_000.0))).single()
        assertEquals(0.1, f.ratio!!, 1e-12)
    }
}
