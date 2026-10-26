package com.bamnat.core

import com.bamnat.core.invest.FillReconcile
import com.bamnat.core.invest.FillReconcile.Kind
import com.bamnat.core.invest.FillReconcile.Part
import com.bamnat.core.invest.FillSheetReader
import com.bamnat.core.invest.Market
import com.bamnat.core.invest.Side
import com.bamnat.core.invest.Trade
import com.bamnat.core.invest.TradeAlerts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** [INV-42] 1.27 AK 순번 450: 캡처 줄을 이미 적은 기록·기다리는 알림과 맞춰 같음·다름·새 것으로 (결정 AK-D1·D2·D5). */
class FillReconcileTest {
    private val zone = ZoneId.of("Asia/Seoul")
    private val today = LocalDate.of(2026, 10, 26)

    private fun fill(name: String, code: String, side: Side, q: Double, p: Double, a: Double = q * p, date: LocalDate? = today, time: LocalTime? = null,
                     unsure: Set<FillSheetReader.Field> = emptySet()) =
        FillSheetReader.Fill(side, name, code, q, p, a, Market.KR_STOCK, date, time, unsure)

    private fun trade(name: String, code: String, side: Side, q: Double, p: Double, date: LocalDate = today, minute: Int? = null, id: Long = 1) =
        Trade(id = id, accountId = 1, date = date, minute = minute, symbol = code, name = name, side = side, quantity = q, price = p)

    @Test fun aWrittenTradeIsSkippedADifferentOneIsAskedAndTheRestIsNew() {
        val fills = listOf(
            fill("삼성전자", "005930", Side.BUY, 10.0, 71_500.0),
            fill("카카오", "035720", Side.SELL, 5.0, 41_250.0),
            fill("NAVER", "035420", Side.BUY, 4.0, 176_300.0),
        )
        val trades = listOf(
            trade("삼성전자", "005930", Side.BUY, 10.0, 71_500.0, id = 1),
            trade("카카오", "035720", Side.SELL, 5.0, 41_000.0, id = 2),
        )
        val r = FillReconcile.reconcile(fills, trades, emptyList(), today, zone)
        assertEquals(listOf(Kind.SAME, Kind.DIFFERENT, Kind.NEW), r.items.map { it.kind })
        assertEquals(1L, r.same.single().trade!!.id)
        assertEquals(setOf(Part.PRICE), r.different.single().parts)
        assertEquals(2L, r.different.single().trade!!.id)
        assertEquals("NAVER", r.new.single().fill.name)
        assertEquals(1, r.sureNew.size)
        // A fix changes only quantity, price and minute.
        val fixed = FillReconcile.fixed(r.different.single(), zone)!!
        assertEquals(41_250.0, fixed.price, 0.0)
        assertEquals(trades[1].copy(price = 41_250.0), fixed)
    }

    @Test fun twoBuysOfOneStockOnOneDayAreBothSkipped() {
        val fills = listOf(fill("삼성전자", "005930", Side.BUY, 3.0, 71_000.0, time = LocalTime.of(9, 5)), fill("삼성전자", "005930", Side.BUY, 7.0, 72_000.0, time = LocalTime.of(14, 0)))
        val trades = listOf(trade("삼성전자", "005930", Side.BUY, 7.0, 72_000.0, minute = 14 * 60, id = 1), trade("삼성전자", "005930", Side.BUY, 3.0, 71_000.0, minute = 9 * 60 + 5, id = 2))
        val r = FillReconcile.reconcile(fills, trades, emptyList(), today, zone)
        assertTrue(r.items.all { it.kind == Kind.SAME })
        assertEquals(listOf(2L, 1L), r.items.map { it.trade!!.id })
    }

    @Test fun aTradeTheScreenSplitsIntoRowsIsTheSame() {
        val fills = listOf(fill("SK하이닉스", "000660", Side.BUY, 2.0, 181_000.0), fill("SK하이닉스", "000660", Side.BUY, 1.0, 184_000.0))
        val trades = listOf(trade("SK하이닉스", "000660", Side.BUY, 3.0, 182_000.0))
        val r = FillReconcile.reconcile(fills, trades, emptyList(), today, zone)
        assertEquals(1, r.same.size)
        assertEquals(2, r.same.single().fills.size)
        assertTrue(r.new.isEmpty())
    }

    @Test fun aRowWithoutADateIsTheSameAsATradeOfTheLastWeekAndNewOtherwise() {
        val row = fill("카카오", "035720", Side.BUY, 5.0, 41_250.0, date = null)
        assertEquals(Kind.SAME, FillReconcile.reconcile(listOf(row), listOf(trade("카카오", "035720", Side.BUY, 5.0, 41_250.0, date = today.minusDays(3))), emptyList(), today, zone).items.single().kind)
        assertEquals(Kind.NEW, FillReconcile.reconcile(listOf(row), listOf(trade("카카오", "035720", Side.BUY, 5.0, 41_250.0, date = today.minusDays(9))), emptyList(), today, zone).items.single().kind)
        // A row with its own date is compared on that date only.
        assertEquals(Kind.NEW, FillReconcile.reconcile(listOf(row.copy(date = today)), listOf(trade("카카오", "035720", Side.BUY, 5.0, 41_250.0, date = today.minusDays(1))), emptyList(), today, zone).items.single().kind)
    }

    @Test fun timesMoreThanHalfAnHourApartAreTwoTrades() {
        val row = fill("카카오", "035720", Side.BUY, 5.0, 41_250.0, time = LocalTime.of(14, 0))
        val r = FillReconcile.reconcile(listOf(row), listOf(trade("카카오", "035720", Side.BUY, 4.0, 41_000.0, minute = 9 * 60)), emptyList(), today, zone)
        assertEquals(Kind.NEW, r.items.single().kind)
        // Within half an hour, a different quantity is asked.
        val near = FillReconcile.reconcile(listOf(row), listOf(trade("카카오", "035720", Side.BUY, 4.0, 41_250.0, minute = 13 * 60 + 50)), emptyList(), today, zone)
        assertEquals(setOf(Part.QUANTITY, Part.TIME), near.items.single().parts)
        // Only the minute differs: the same trade.
        val minute = FillReconcile.reconcile(listOf(row), listOf(trade("카카오", "035720", Side.BUY, 5.0, 41_250.0, minute = 13 * 60 + 58)), emptyList(), today, zone)
        assertEquals(Kind.SAME, minute.items.single().kind)
    }

    @Test fun aRowThatFillsAWaitingAlertIsNewWithItsAlertAndADisagreeingSaidPriceIsAsked() {
        val at = LocalDateTime.of(today, LocalTime.of(9, 12)).atZone(zone).toInstant().toEpochMilli()
        val waiting = TradeAlerts.Pending(at, "한국투자", Side.BUY, "005930", "삼성전자", 10.0, 0.0, Market.KR_STOCK)
        val row = fill("삼성전자", "005930", Side.BUY, 10.0, 71_500.0, time = LocalTime.of(9, 12))
        val r = FillReconcile.reconcile(listOf(row), emptyList(), listOf(waiting), today, zone)
        val item = r.new.single()
        assertEquals(waiting, item.alert)
        val p = FillReconcile.pending(item, zone, today)
        assertEquals(at, p.at)
        assertEquals(71_500.0, p.price, 0.0)
        assertEquals(TradeAlerts.BY_CAPTURE, p.filledBy)
        val said = FillReconcile.reconcile(listOf(row), emptyList(), listOf(waiting.copy(price = 70_000.0)), today, zone)
        assertEquals(Kind.DIFFERENT, said.items.single().kind)
        assertNull(said.items.single().trade)
    }

    @Test fun aYellowNewRowIsNeverPutInWithoutAsking() {
        val yellow = fill("카카오", "035720", Side.SELL, 5.0, 42_250.0, a = 206_250.0)
        val unsure = fill("NAVER", "", Side.BUY, 4.0, 176_300.0, unsure = setOf(FillSheetReader.Field.NAME))
        val r = FillReconcile.reconcile(listOf(yellow, unsure), emptyList(), emptyList(), today, zone)
        assertEquals(2, r.new.size)
        assertTrue(r.sureNew.isEmpty())
        assertEquals(2, r.toAsk.size)
    }

    @Test fun aNewRowBecomesALineAtItsOwnDateAndTime() {
        val row = fill("NAVER", "035420", Side.BUY, 4.0, 176_300.0, date = today.minusDays(1), time = LocalTime.of(10, 30))
        val p = FillReconcile.pending(FillReconcile.reconcile(listOf(row), emptyList(), emptyList(), today, zone).items.single(), zone, today)
        assertEquals(LocalDateTime.of(today.minusDays(1), LocalTime.of(10, 30)).atZone(zone).toInstant().toEpochMilli(), p.at)
        assertEquals(FillReconcile.CAPTURE_APP, p.app)
        assertEquals(705_200.0, p.amount, 0.0)
    }
}
