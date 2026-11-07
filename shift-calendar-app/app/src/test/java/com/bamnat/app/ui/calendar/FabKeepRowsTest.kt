package com.bamnat.app.ui.calendar

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.YearMonth

/** 12.1: the rows of a month the floating '빠른 입력' must not sit on. */
class FabKeepRowsTest {
    @Test fun aMonthEndingOnSaturdayKeepsItsLastRow() {
        // 2026-10: 1st is Thursday, 31st Saturday, row 5 (index 4) right side is October.
        assertEquals(5, fabKeepRows(YearMonth.of(2026, 10), DayOfWeek.SUNDAY))
    }

    @Test fun aSixthRowWithItsDaysOnTheLeftIsFreeOnTheRight() {
        // 2026-08: 1st is Saturday, 30·31 are Sunday·Monday of the sixth row; Thursday to Saturday there are September's.
        assertEquals(5, fabKeepRows(YearMonth.of(2026, 8), DayOfWeek.SUNDAY))
    }

    @Test fun theWeekStartCounts() {
        // 2026-08 from Monday: 31st is Monday of the sixth row.
        assertEquals(5, fabKeepRows(YearMonth.of(2026, 8), DayOfWeek.MONDAY))
        // 2026-05 from Sunday: 1st Friday, 31st Sunday of the sixth row.
        assertEquals(5, fabKeepRows(YearMonth.of(2026, 5), DayOfWeek.SUNDAY))
        // 2026-01 from Sunday: 1st Thursday, 31st Saturday of the fifth row.
        assertEquals(5, fabKeepRows(YearMonth.of(2026, 1), DayOfWeek.SUNDAY))
        // 2026-02 from Sunday: four whole rows, the fifth and sixth are March's.
        assertEquals(4, fabKeepRows(YearMonth.of(2026, 2), DayOfWeek.SUNDAY))
    }
}
