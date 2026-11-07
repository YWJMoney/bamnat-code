package com.bamnat.core

import com.bamnat.core.sleep.SleepPlanShare
import com.bamnat.core.sleep.SleepSchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** 1.39 G5-N4 계획 잠 시간을 가족 링크에. */
class SleepPlanShareTest {
    private val d = LocalDate.of(2026, 11, 9)
    private fun sleep(fromH: Int, toH: Int, day: LocalDate = d, toDay: LocalDate = day) =
        SleepSchedule.Sleep(day.atTime(fromH, 0), toDay.atTime(toH, 0), after = null, before = null, short = false, restMinutes = null)

    @Test fun onlyPlansNotOverAndNotTooLong() {
        val now = d.atTime(10, 0)
        val w = SleepPlanShare.windows(listOf(sleep(0, 7), sleep(9, 16), sleep(22, 6, toDay = d.plusDays(1)), sleep(1, 20, day = d.plusDays(2))), now)
        assertEquals(listOf(d.atTime(9, 0), d.atTime(22, 0)), w.map { it.from })
    }

    @Test fun theLineSaysNowOrTheNextWithinADay() {
        val w = SleepPlanShare.windows(listOf(sleep(9, 16), sleep(22, 6, toDay = d.plusDays(1))), d.atTime(8, 0))
        assertEquals("오늘 09:00~16:00 잘 계획이에요", SleepPlanShare.line(w, d.atTime(8, 0)))
        assertEquals("지금은 잘 계획 시간이에요 · 16:00까지", SleepPlanShare.line(w, d.atTime(12, 0)))
        assertEquals("오늘 22:00~06:00 잘 계획이에요", SleepPlanShare.line(w, d.atTime(17, 0)))
        // Nothing within a day: no line.
        assertNull(SleepPlanShare.line(SleepPlanShare.windows(listOf(sleep(9, 16, day = d.plusDays(3))), d.atTime(8, 0)), d.atTime(8, 0)))
    }
}
