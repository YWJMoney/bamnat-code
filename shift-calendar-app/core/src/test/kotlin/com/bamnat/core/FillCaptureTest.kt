package com.bamnat.core

import com.bamnat.core.invest.FillMatcher
import com.bamnat.core.invest.FillSheetReader
import com.bamnat.core.invest.Market
import com.bamnat.core.invest.Side
import com.bamnat.core.invest.TradeAlertParser
import com.bamnat.core.invest.TradeAlerts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId

/** [INV-40] 1.25 W 409: 체결 단가 자동 1단계 — A 알림 금액 ÷ 수량, B 알림 묶기, C 체결 내역 캡처 읽기 (결정 D77). */
class FillCaptureTest {
    private val zone = ZoneId.of("Asia/Seoul")
    private fun at(h: Int, m: Int) = LocalDateTime.of(2026, 10, 24, h, m).atZone(zone).toInstant().toEpochMilli()

    // ---- A ----

    @Test fun anAmountWithoutAPriceGivesAmountOverQuantity() {
        val a = TradeAlertParser.parse("[한국투자증권] 매수 체결\n삼성전자(005930) 10주\n체결금액 715,000원", "com.truefriend.neosmarta")!!
        assertEquals(71_500.0, a.price, 0.0)
        assertTrue(a.priceFromAmount)
        assertEquals(715_000.0, a.amount, 0.0)
    }

    @Test fun theAmountIsNeverReadAsThePrice() {
        // Before 409 "715,000원" was taken for the price of one share.
        val a = TradeAlertParser.parse("삼성전자 10주 매수 체결 체결금액 715,000원")!!
        assertEquals(71_500.0, a.price, 0.0)
        val said = TradeAlertParser.parse("삼성전자 10주 매수 체결 단가 71,500원 체결금액 715,000원")!!
        assertEquals(71_500.0, said.price, 0.0)
        assertFalse(said.priceFromAmount)
        assertEquals(true, TradeAlerts.of(said, "앱", 0).checked)
    }

    @Test fun aPriceThatDoesNotAgreeWithTheAmountIsHeldForALook() {
        val a = TradeAlertParser.parse("삼성전자 10주 매수 체결 단가 81,500원 체결금액 715,000원")!!
        assertEquals(false, TradeAlerts.of(a, "앱", 0).checked)
    }

    @Test fun aDollarPriceBesideAWonAmountIsNotCompared() {
        val a = TradeAlertParser.parse("[해외] 애플(AAPL) 3주 매수 체결 체결가 $231.50 원화 체결금액 960,000원")!!
        assertEquals(Market.US_STOCK, a.market)
        assertEquals(231.5, a.price, 0.0)
        assertNull(TradeAlerts.of(a, "앱", 0).checked)
    }

    @Test fun aCoinAmountOverAFractionKeepsFourPlaces() {
        val a = TradeAlertParser.parse("[업비트] 이더리움(KRW-ETH) 매수 체결 0.03 ETH 거래금액 126,330원", "com.dunamu.exchange.upbit")!!
        assertEquals(4_211_000.0, a.price, 0.0)
        assertEquals(Market.CRYPTO, a.market)
    }

    // ---- B ----

    private fun pending(at: Long, qty: Double, price: Double, amount: Double = 0.0) =
        TradeAlerts.Pending(at, "앱", Side.BUY, "005930", "삼성전자", qty, price, Market.KR_STOCK, amount)

    @Test fun splitFillsOfOneOrderBecomeOneLineAtTheAveragePrice() {
        var list = TradeAlerts.add(emptyList(), pending(at(10, 15), 3.0, 178_000.0))!!
        list = TradeAlerts.add(list, pending(at(10, 16), 5.0, 178_100.0))!!
        list = TradeAlerts.add(list, pending(at(10, 18), 2.0, 178_200.0))!!
        val one = list.single()
        assertEquals(10.0, one.quantity, 0.0)
        assertEquals(178_090.0, one.price, 0.0)
        assertEquals(1_780_900.0, one.amount, 0.0)
        assertEquals(3, one.parts)
        assertEquals(TradeAlerts.BY_PARTS, one.filledBy)
    }

    @Test fun aSettlementNoticeGivesThePriceToTheFillWithout() {
        var list = TradeAlerts.add(emptyList(), pending(at(9, 0), 10.0, 0.0))!!
        list = TradeAlerts.add(list, pending(at(9, 2), 10.0, 71_500.0, 715_000.0))!!
        assertEquals(71_500.0, list.single().price, 0.0)
        // And a priceless repeat after it changes nothing.
        assertNull(TradeAlerts.add(list, pending(at(9, 3), 10.0, 0.0)))
    }

    @Test fun anotherOrderLaterOrAnotherStockStaysApart() {
        var list = TradeAlerts.add(emptyList(), pending(at(9, 0), 10.0, 71_500.0))!!
        list = TradeAlerts.add(list, pending(at(9, 30), 5.0, 71_000.0))!!
        list = TradeAlerts.add(list, pending(at(9, 31), 5.0, 41_000.0).copy(symbol = "035720", name = "카카오"))!!
        assertEquals(3, list.size)
    }

    @Test fun newFieldsSurviveTheWaitingListAndOldLinesStillRead() {
        val p = pending(at(9, 0), 10.0, 71_500.0, 715_000.0).copy(parts = 2, filledBy = TradeAlerts.BY_CAPTURE)
        assertEquals(listOf(p), TradeAlerts.decode(TradeAlerts.encode(listOf(p))))
        val old = TradeAlerts.decode("1\t앱\tBUY\t005930\t삼성전자\t10.0\t0.0\tKR_STOCK").single()
        assertEquals(0.0, old.amount, 0.0)
        assertEquals(1, old.parts)
    }

    // ---- C ----

    private data class Expect(val side: Side, val name: String, val code: String, val qty: Double, val price: Double, val amount: Double, val ask: Set<FillSheetReader.Field>)

    private fun samples(): List<Pair<String, Pair<List<Expect>, String>>> {
        val dir = File(javaClass.classLoader.getResource("fill-captures")!!.toURI())
        return dir.listFiles { f -> f.name.endsWith(".txt") }!!.sortedBy { it.name }.map { f ->
            val (head, body) = f.readText().split("\n---\n", limit = 2)
            f.name to (head.lines().filter { it.startsWith("# expect:") }.map { l ->
                val c = l.removePrefix("# expect:").trim().split('|')
                Expect(Side.valueOf(c[0]), c[1], c[2], c[3].toDouble(), c[4].toDouble(), c[5].toDouble(),
                    if (c[6] == "sure") emptySet() else c[6].removePrefix("ask:").split(',').map { FillSheetReader.Field.valueOf(it) }.toSet())
            } to body)
        }
    }

    @Test fun everyCaptureSampleReadsExactly() {
        val all = samples()
        assertTrue("표본 10장 이상", all.size >= 10)
        for ((file, sample) in all) {
            val (expects, text) = sample
            val got = FillSheetReader.read(text)
            assertEquals("$file: 체결 수 $got", expects.size, got.size)
            expects.zip(got).forEach { (e, g) ->
                assertEquals("$file 방향", e.side, g.side)
                assertTrue("$file 종목 ${g.name}/${g.symbol} ≠ ${e.name}", g.name == e.name || g.symbol == e.code && e.code.isNotEmpty())
                if (e.code.isNotEmpty()) assertEquals("$file 코드", e.code, g.symbol)
                assertEquals("$file 수량", e.qty, g.quantity, 1e-9)
                assertEquals("$file 단가", e.price, g.price, 1e-6)
                assertEquals("$file 금액", e.amount, g.amount, 1e-6)
                assertEquals("$file 물을 칸", e.ask, g.unsure)
            }
        }
    }

    @Test fun nothingWrongGoesInQuietly() {
        // Whatever is read, a fill that is marked sure always agrees with itself.
        for ((_, sample) in samples()) for (f in FillSheetReader.read(sample.second)) {
            if (f.sure) assertTrue("${f.title}: 수량×단가≈금액", f.checked != false && f.price > 0)
        }
        assertTrue(FillSheetReader.read("오늘 날씨 맑음 12도").isEmpty())
    }

    @Test fun readFillsMatchTheWaitingLinesByHoldingSideQuantityAndTime() {
        val text = File(javaClass.classLoader.getResource("fill-captures/01_kr_table_rows.txt")!!.toURI()).readText().substringAfter("\n---\n")
        val fills = FillSheetReader.read(text)
        val waiting = listOf(
            TradeAlerts.Pending(at(9, 12), "앱", Side.BUY, "", "삼성전자", 10.0, 0.0, Market.KR_STOCK),
            // A sell of another day is not this one.
            TradeAlerts.Pending(at(10, 3) - 86_400_000L * 0, "앱", Side.SELL, "035720", "카카오", 5.0, 0.0, Market.KR_STOCK),
            TradeAlerts.Pending(at(15, 0), "앱", Side.BUY, "", "삼성전자", 3.0, 0.0, Market.KR_STOCK),
        )
        val r = FillMatcher.match(waiting, fills, zone)
        assertEquals(2, r.matches.size)
        assertEquals(71_500.0, r.matches.first { it.alert.name == "삼성전자" }.price, 0.0)
        assertEquals(41_250.0, r.matches.first { it.alert.symbol == "035720" }.price, 0.0)
        assertTrue(r.matches.all { it.sure })
        assertTrue(r.unmatched.isEmpty())
    }

    @Test fun splitRowsAddUpToOneWaitingLine() {
        val text = File(javaClass.classLoader.getResource("fill-captures/04_kr_split_fills.txt")!!.toURI()).readText().substringAfter("\n---\n")
        val waiting = listOf(TradeAlerts.Pending(at(10, 15), "앱", Side.BUY, "068270", "셀트리온", 10.0, 0.0, Market.KR_STOCK))
        val m = FillMatcher.match(waiting, FillSheetReader.read(text), zone).matches.single()
        assertEquals(3, m.fills.size)
        assertEquals(178_090.0, m.price, 0.0)
        val filled = TradeAlerts.fill(waiting, waiting[0].at, m.price, m.amount).single()
        assertEquals(TradeAlerts.BY_CAPTURE, filled.filledBy)
        assertEquals(true, filled.checked)
        assertNotNull(filled)
    }
}
