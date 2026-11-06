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

    @Test fun ringsEarlyOnlyInsideTheWindowAndOnlyAfterAMove() {
        assertTrue(SmartWakeRule.wakeNow(alarm - 12 * min, alarm, 20, alarm - 12 * min - 10_000))
        assertFalse("before the window", SmartWakeRule.wakeNow(alarm - 25 * min, alarm, 20, alarm - 25 * min))
        assertFalse("nothing moved", SmartWakeRule.wakeNow(alarm - 12 * min, alarm, 20, null))
        assertFalse("moved long ago", SmartWakeRule.wakeNow(alarm - 12 * min, alarm, 20, alarm - 18 * min))
        assertFalse("the alarm is about to ring anyway", SmartWakeRule.wakeNow(alarm - 30_000, alarm, 20, alarm - 40_000))
        assertFalse("off", SmartWakeRule.wakeNow(alarm - 5 * min, alarm, 0, alarm - 5 * min))
        assertFalse("never after the alarm", SmartWakeRule.wakeNow(alarm + min, alarm, 20, alarm + min))
    }

    @Test fun wordsAndWindow() {
        assertNull(SmartWakeRule.windowStart(alarm, 0))
        assertEquals(alarm - 30 * min, SmartWakeRule.windowStart(alarm, 30))
        assertEquals("얕은 잠이라 12분 일찍 깨웠어요", SmartWakeRule.line(12))
    }
}
