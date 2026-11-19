package com.bamnat.core.invest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

/** 2.0.0 [RVK-52] (검토 MONEY-72·T06b-10): an investor's alarms and '쉬는 날' follow the market they are about. */
class MarketDaysTest {
    private val weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)

    @Test fun nyseHolidaysByTheirOwnRules() {
        val y2026 = listOf("2026-01-01", "2026-01-19", "2026-02-16", "2026-04-03", "2026-05-25", "2026-06-19", "2026-07-03", "2026-09-07",
            "2026-11-26", "2026-12-25").map(LocalDate::parse)
        y2026.forEach { assertTrue("$it", MarketDays.usHoliday(it)) }
        // 7월 4일이 토요일인 2026년은 3일(금)에 쉬고, 4일 자체는 주말이에요.
        assertFalse(MarketDays.usHoliday(LocalDate.of(2026, 7, 6)))
        // 2027-01-01 is a Friday; 2028-01-01 a Saturday, not made up on 2027-12-31.
        assertTrue(MarketDays.usHoliday(LocalDate.of(2027, 1, 1)))
        assertFalse(MarketDays.usHoliday(LocalDate.of(2027, 12, 31)))
        assertEquals(LocalDate.of(2027, 3, 28), MarketDays.easter(2027))
        // Korean holidays are not the NYSE's: 추석 2026-09-25, 개천절 대체 10-05.
        assertFalse(MarketDays.usClosed(LocalDate.of(2026, 9, 25)))
        assertTrue(MarketDays.krClosed(LocalDate.of(2026, 9, 25)))
    }

    @Test fun coinsNeverShutAndAreNeverMoved() {
        val coinPlan = InvestAlarm(kind = AlarmKind.PLAN, minute = 9 * 60 + 10, monthDay = 25, market = Market.CRYPTO, name = "비트코인")
        // 2026-12-25 is 성탄절 (a Friday): a coin plan stays on the 25th.
        assertEquals(LocalDateTime.of(2026, 12, 25, 9, 10), InvestAlarms.next(coinPlan, LocalDateTime.of(2026, 12, 1, 0, 0)))
        val krPlan = coinPlan.copy(market = Market.KR_STOCK, name = "삼성전자")
        assertEquals(LocalDateTime.of(2026, 12, 28, 9, 10), InvestAlarms.next(krPlan, LocalDateTime.of(2026, 12, 1, 0, 0)))
        // A weekly coin buy keeps its Saturday.
        val weekly = InvestAlarm(kind = AlarmKind.PLAN, minute = 20 * 60, weekdays = setOf(DayOfWeek.SATURDAY), market = Market.CRYPTO, name = "BTC")
        assertEquals(LocalDateTime.of(2026, 11, 21, 20, 0), InvestAlarms.next(weekly, LocalDateTime.of(2026, 11, 18, 22, 0)))
    }

    @Test fun usSharesFollowTheNyseNotKorea() {
        val usPlan = InvestAlarm(kind = AlarmKind.PLAN, minute = 22 * 60, monthDay = 25, market = Market.US_STOCK, name = "애플")
        // 2026-09-25 추석 (Korea) is an open day in New York.
        assertEquals(LocalDateTime.of(2026, 9, 25, 22, 0), InvestAlarms.next(usPlan, LocalDateTime.of(2026, 9, 1, 0, 0)))
        // 2026-11-26 Thanksgiving: a plan on the 26th moves to the 27th.
        assertEquals(LocalDateTime.of(2026, 11, 27, 22, 0), InvestAlarms.next(usPlan.copy(monthDay = 26), LocalDateTime.of(2026, 11, 1, 0, 0)))
    }

    @Test fun suneungAndTheFirstTradingDayOpenAnHourLate() {
        val open = InvestAlarms.default(AlarmKind.MARKET_OPEN)
        val close = InvestAlarms.default(AlarmKind.MARKET_CLOSE)
        // 2026-11-19 수능일: 10:00 개장, 16:30 마감 (한국거래소 11-05 발표).
        assertEquals(LocalDateTime.of(2026, 11, 19, 10, 0), InvestAlarms.next(open, LocalDateTime.of(2026, 11, 18, 22, 0)))
        assertEquals(LocalDateTime.of(2026, 11, 19, 16, 20), InvestAlarms.next(close, LocalDateTime.of(2026, 11, 19, 10, 0)))
        assertEquals(LocalDateTime.of(2026, 11, 20, 9, 0), InvestAlarms.next(open, LocalDateTime.of(2026, 11, 19, 10, 0)))
        // 2027-01-04 (월) is the first trading day: 10:00. 2026-12-31 is shut.
        assertEquals(LocalDateTime.of(2027, 1, 4, 10, 0), InvestAlarms.next(open, LocalDateTime.of(2026, 12, 30, 9, 30)))
        assertEquals(LocalDateTime.of(2027, 1, 4, 15, 20), InvestAlarms.next(close, LocalDateTime.of(2027, 1, 4, 10, 0)))
        // A 09:10 plan on 수능일 waits for the open; a 14:00 one does not move.
        val plan = InvestAlarm(kind = AlarmKind.PLAN, minute = 9 * 60 + 10, monthDay = 19, name = "삼성전자")
        assertEquals(LocalDateTime.of(2026, 11, 19, 10, 10), InvestAlarms.next(plan, LocalDateTime.of(2026, 11, 1, 0, 0)))
        assertEquals(LocalDateTime.of(2026, 11, 19, 14, 0), InvestAlarms.next(plan.copy(minute = 14 * 60), LocalDateTime.of(2026, 11, 1, 0, 0)))
        // 근로자의 날 (2026-05-01, 금) the market is shut.
        assertEquals(LocalDateTime.of(2026, 5, 4, 9, 0), InvestAlarms.next(open.copy(weekdays = weekdays), LocalDateTime.of(2026, 4, 30, 10, 0)))
    }

    @Test fun theDayNoteSaysOnlyTheMarketsTraded() {
        val sat = LocalDate.of(2026, 11, 21)
        assertNull("코인만", MarketDays.closedLine(sat, setOf(MarketDays.Venue.COIN)))
        assertEquals("오늘은 국내 증시가 쉬는 날이에요", MarketDays.closedLine(sat, setOf(MarketDays.Venue.KR, MarketDays.Venue.COIN)))
        assertEquals("아무것도 안 적었으면 국내", "오늘은 국내 증시가 쉬는 날이에요", MarketDays.closedLine(sat, emptySet()))
        val thanksgiving = LocalDate.of(2026, 11, 26)
        assertEquals("오늘은 미국 증시가 쉬는 날이에요", MarketDays.closedLine(thanksgiving, setOf(MarketDays.Venue.US, MarketDays.Venue.KR)))
        assertEquals("오늘은 미국 휴장일", MarketDays.closedShort(thanksgiving, setOf(MarketDays.Venue.US)))
        assertEquals("오늘은 국내·미국 증시가 쉬는 날이에요", MarketDays.closedLine(LocalDate.of(2026, 12, 25), setOf(MarketDays.Venue.US, MarketDays.Venue.KR)))
        // The investor's markets are read from trades and plans.
        val book = InvestBook(trades = listOf(Trade(accountId = 1, date = sat, side = Side.BUY, symbol = "BTC", name = "비트코인", quantity = 0.1,
            price = 1.0, market = Market.CRYPTO)))
        assertEquals(setOf(MarketDays.Venue.COIN), book.venues)
    }
}
