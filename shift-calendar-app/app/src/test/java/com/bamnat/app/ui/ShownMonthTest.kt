package com.bamnat.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.YearMonth

/**
 * 2.0.0 [RVH-91] 검토 HQ-114 (다음 달 근무표로 시작한 직원 폰: 달력이 12월로 열려 가계부도 '12월 나간 돈 0원'): the calendar opens on the
 * roster's month by itself, 돈·근무·급여 stay on today's, and a month turned by hand anywhere is every tab's again ([NAV-01]).
 */
class ShownMonthTest {
    private val nov = YearMonth.of(2026, 11)
    private val dec = YearMonth.of(2026, 12)

    @Test fun aRosterMonthOpensTheCalendarOnly() {
        val shown = ShownMonth(nov)
        shown.showOpening(dec)
        assertEquals(dec, shown.month.value)
        assertEquals(nov, shown.others.value)
        // Turned by hand (the calendar's ‹, or 돈's ›): one month for all tabs again.
        shown.show(nov.minusMonths(1))
        assertEquals(nov.minusMonths(1), shown.others.value)
        shown.show(dec)
        assertEquals(dec, shown.month.value)
        assertEquals(dec, shown.others.value)
    }

    @Test fun theTurnOfTheMonthMovesBothAlong() {
        val shown = ShownMonth(nov)
        shown.followToday(nov)
        shown.showOpening(dec)
        shown.followToday(dec)
        // Today's month moved on: 돈 follows to December; the calendar was already there.
        assertEquals(dec, shown.others.value)
        assertEquals(dec, shown.month.value)
    }
}
