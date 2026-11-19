package com.bamnat.core.invest

import com.bamnat.core.holiday.KoreanHolidays
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * 2.0.0 [RVK-52] (검토 MONEY-72·T06b-10): which market is shut on a day, and when the Korean market opens late.
 * - 코인 never shuts, so an alarm for a coin is never moved and a coin-only investor is never told '휴장'.
 * - 미국 주식 follows the NYSE's own holidays (rules below, observed on the Friday or Monday), not Korea's.
 * - 국내 주식 shuts on weekends, 관공서 공휴일, 근로자의 날 and 12월 31일; on 수능일 it opens and shuts an hour late
 *   (10:00~16:30), and on the year's first trading day it opens at 10:00. 수능일 are the dates KRX announced
 *   (2026-11-19: 한국거래소 11-05 발표); a year not listed is read as an ordinary day.
 */
object MarketDays {
    /** 수능일 (KRX 거래시간 임시 조정): 2024학년도 ~ 2027학년도. */
    val SUNEUNG: Set<LocalDate> = setOf(
        LocalDate.of(2023, 11, 16), LocalDate.of(2024, 11, 14), LocalDate.of(2025, 11, 13), LocalDate.of(2026, 11, 19),
    )

    /** The markets an alarm or a day note is about: coins, US shares, or the Korean market (everything else). */
    enum class Venue(val label: String) { KR("국내 증시"), US("미국 증시"), COIN("코인") }

    fun venueOf(market: Market): Venue = when (market) {
        Market.CRYPTO -> Venue.COIN
        Market.US_STOCK -> Venue.US
        else -> Venue.KR
    }

    fun krClosed(d: LocalDate): Boolean =
        weekend(d) || KoreanHolidays.isHoliday(d) || KoreanHolidays.isLaborDay(d) || (d.monthValue == 12 && d.dayOfMonth == 31)

    fun usClosed(d: LocalDate): Boolean = weekend(d) || usHoliday(d)

    fun closed(d: LocalDate, venue: Venue): Boolean = when (venue) {
        Venue.KR -> krClosed(d)
        Venue.US -> usClosed(d)
        Venue.COIN -> false
    }

    /** Minutes the Korean market opens late on [d]: 60 on 수능일 and on the year's first trading day. */
    fun krOpenLate(d: LocalDate): Int = if (d in SUNEUNG || firstTradingDay(d)) 60 else 0

    /** Minutes the Korean market shuts late on [d]: 60 on 수능일. */
    fun krCloseLate(d: LocalDate): Int = if (d in SUNEUNG) 60 else 0

    private fun firstTradingDay(d: LocalDate): Boolean {
        if (krClosed(d)) return false
        var x = LocalDate.of(d.year, 1, 1)
        while (krClosed(x)) x = x.plusDays(1)
        return x == d
    }

    /**
     * The markets of [venues] that are shut on [d], for '오늘은 … 쉬는 날이에요'. A weekend is said only for the Korean
     * market (it has always been), and nothing is said for coins.
     */
    fun closedToday(d: LocalDate, venues: Set<Venue>): List<Venue> {
        val asked = venues.ifEmpty { setOf(Venue.KR) }
        return listOfNotNull(
            Venue.KR.takeIf { it in asked && krClosed(d) },
            Venue.US.takeIf { it in asked && !weekend(d) && usHoliday(d) },
        )
    }

    /** "오늘은 국내 증시가 쉬는 날이에요", "오늘은 국내·미국 증시가 쉬는 날이에요", or null. */
    fun closedLine(d: LocalDate, venues: Set<Venue>): String? = closedToday(d, venues).takeIf { it.isNotEmpty() }?.let { shut ->
        "오늘은 " + (if (shut.size == 2) "국내·미국 증시" else shut.single().label) + "가 쉬는 날이에요"
    }

    /** The short form on the calendar card: "오늘은 휴장일", "오늘은 미국 휴장일". */
    fun closedShort(d: LocalDate, venues: Set<Venue>): String? = closedToday(d, venues).takeIf { it.isNotEmpty() }?.let { shut ->
        if (shut == listOf(Venue.US)) "오늘은 미국 휴장일" else "오늘은 휴장일"
    }

    private fun weekend(d: LocalDate) = d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY

    /** NYSE holidays: New Year's Day, MLK Day, Washington's Birthday, Good Friday, Memorial Day, Juneteenth (2022~), Independence Day, Labor Day, Thanksgiving, Christmas. */
    fun usHoliday(d: LocalDate): Boolean {
        val y = d.year
        val fixed = listOfNotNull(
            observed(LocalDate.of(y, 1, 1), newYear = true),
            if (y >= 2022) observed(LocalDate.of(y, 6, 19)) else null,
            observed(LocalDate.of(y, 7, 4)),
            observed(LocalDate.of(y, 12, 25)),
        )
        val floating = listOf(
            nth(y, 1, DayOfWeek.MONDAY, 3),
            nth(y, 2, DayOfWeek.MONDAY, 3),
            easter(y).minusDays(2),
            LocalDate.of(y, 5, 31).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
            nth(y, 9, DayOfWeek.MONDAY, 1),
            nth(y, 11, DayOfWeek.THURSDAY, 4),
        )
        return d in fixed || d in floating
    }

    /** Saturday → Friday, Sunday → Monday; New Year's Day on a Saturday is not made up on the Friday before. */
    private fun observed(d: LocalDate, newYear: Boolean = false): LocalDate? = when (d.dayOfWeek) {
        DayOfWeek.SATURDAY -> if (newYear) null else d.minusDays(1)
        DayOfWeek.SUNDAY -> d.plusDays(1)
        else -> d
    }

    private fun nth(y: Int, month: Int, day: DayOfWeek, n: Int): LocalDate =
        LocalDate.of(y, month, 1).with(TemporalAdjusters.dayOfWeekInMonth(n, day))

    /** Western Easter (anonymous Gregorian algorithm). */
    internal fun easter(y: Int): LocalDate {
        val a = y % 19; val b = y / 100; val c = y % 100; val d = b / 4; val e = b % 4
        val f = (b + 8) / 25; val g = (b - f + 1) / 3; val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4; val k = c % 4; val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31
        val day = (h + l - 7 * m + 114) % 31 + 1
        return LocalDate.of(y, month, day)
    }
}
