package com.bamnat.app.data

import com.bamnat.core.pay.PayPeriod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 1.39 [PAYX-60] (사용자 2026-11-07): 급여 기간 21일~20일이면 근무 시간도 그 기간으로, 1일~말일을 고른 사람은 그대로. */
class StatsPeriodTest {
    private val twentieth = PayPeriod(endDay = 20)

    @Test fun byItselfWhenThePayHasAPeriod() {
        assertTrue(StatsPeriod.AUTO.byPay(twentieth))
        assertTrue(StatsPeriod.PAY.byPay(twentieth))
        // 1일~말일 picked on 통계 stays.
        assertFalse(StatsPeriod.MONTH.byPay(twentieth))
        // No pay, or a pay of 1일~말일: the month as it is.
        assertFalse(StatsPeriod.AUTO.byPay(null))
        assertFalse(StatsPeriod.AUTO.byPay(PayPeriod()))
        assertFalse(StatsPeriod.AUTO.byPay(PayPeriod(endDay = 31)))
    }

    @Test fun novembersPayIsTheTwentyFirstToTheTwentieth() {
        val nov = java.time.YearMonth.of(2026, 11)
        assertEquals(java.time.LocalDate.of(2026, 10, 21), twentieth.start(nov))
        assertEquals(java.time.LocalDate.of(2026, 11, 20), twentieth.end(nov))
    }

    @Test fun theOldSwitchIsReadAsTheNewWay() {
        // Off was only the default before 1.39, so it now follows the pay.
        assertEquals(StatsPeriod.AUTO, StatsPeriod.read(null, old = false))
        assertEquals(StatsPeriod.PAY, StatsPeriod.read(null, old = true))
        assertEquals(StatsPeriod.MONTH, StatsPeriod.read("MONTH", old = true))
        assertEquals(StatsPeriod.AUTO, StatsPeriod.read("??", old = false))
    }
}
