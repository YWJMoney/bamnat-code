package com.bamnat.core

import com.bamnat.core.sleep.SmartWakeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 1.38 [SLEEP-08] 깨우는 구간은 앞으로만, 움직였을 때만. */
class SmartWakeTest {
    private val alarm = 10 * 3_600_000L
    private val min = 60_000L

    @Test fun ringsEarlyOnlyInsideTheWindowAndOnlyAfterMovingTwice() {
        val now = alarm - 12 * min
        assertTrue(SmartWakeRule.wakeNow(now, alarm, 20, listOf(now - 40_000, now - 10_000)))
        assertFalse("one move is not a light sleep (PC 11차)", SmartWakeRule.wakeNow(now, alarm, 20, listOf(now)))
        assertFalse("a shake's readings in one second are one move", SmartWakeRule.wakeNow(now, alarm, 20, listOf(now - 900, now - 500, now)))
        assertFalse("moves before the window do not count",
            SmartWakeRule.wakeNow(alarm - 20 * min + 30_000, alarm, 20, listOf(alarm - 20 * min - 30_000, alarm - 20 * min + 30_000)))
        assertFalse("before the window", SmartWakeRule.wakeNow(alarm - 25 * min, alarm, 20, listOf(alarm - 25 * min - 10_000, alarm - 25 * min)))
        assertFalse("nothing moved", SmartWakeRule.wakeNow(now, alarm, 20, emptyList()))
        assertFalse("moved long ago", SmartWakeRule.wakeNow(now, alarm, 20, listOf(alarm - 18 * min, alarm - 17 * min)))
        assertFalse("the alarm is about to ring anyway", SmartWakeRule.wakeNow(alarm - 30_000, alarm, 20, listOf(alarm - 60_000, alarm - 40_000)))
        assertFalse("off", SmartWakeRule.wakeNow(now, alarm, 0, listOf(now - 10_000, now)))
        assertFalse("never after the alarm", SmartWakeRule.wakeNow(alarm + min, alarm, 20, listOf(alarm + min - 10_000, alarm + min)))
    }

    @Test fun wordsAndWindow() {
        assertNull(SmartWakeRule.windowStart(alarm, 0))
        assertEquals(alarm - 30 * min, SmartWakeRule.windowStart(alarm, 30))
        assertEquals("얕은 잠이라 12분 일찍 깨웠어요", SmartWakeRule.line(12))
    }
}
