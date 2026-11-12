package com.bamnat.core

import com.bamnat.core.money.FieldRules
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** 2.0.0 검토 갈래 H (달력·근무·첫 설정·유형): core 쪽 재현 시험. */
class ReviewHTest {
    /** [RVH-23] 검토 CAL-28: a day's own 일당, saved without '이 날부터 바꾸기', is that day's only. */
    @Test fun aOneDayRateLeavesTheDayRateAlone() {
        val day = LocalDate.of(2026, 11, 12)
        val rules = FieldRules(dayRate = 200_000)
        // No change yet, and the 특별 수당 day is written without the tick: the 일당 of later days is still 200,000.
        val after = rules.afterDayRate(day, 250_000, fromHere = false)
        assertEquals(200_000L, after.dayRate)
        assertEquals(200_000L, after.rateOn(day.plusDays(1)))
        // With the tick it is the 일당 from that day, the days before keep theirs.
        val ticked = rules.afterDayRate(day, 250_000, fromHere = true)
        assertEquals(250_000L, ticked.rateOn(day.plusDays(1)))
        assertEquals(200_000L, ticked.rateOn(day.minusDays(1)))
        // The first 일당 ever written is remembered.
        assertEquals(180_000L, FieldRules().afterDayRate(day, 180_000, fromHere = false).dayRate)
    }
}
