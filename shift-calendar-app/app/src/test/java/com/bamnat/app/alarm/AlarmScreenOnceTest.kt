package com.bamnat.app.alarm

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/**
 * [ALRM-25] 1.36 빌드 7: the app's banner opens the ringing screen once per ring and never over the screen already up, so the
 * two windows do not hand the focus back and forth (the '응답 없음' while swiping the alarm off).
 */
class AlarmScreenOnceTest {
    private fun ring(at: Long, snooze: Boolean = false) =
        RingRequest("주간", LocalDate.of(2026, 12, 1), LocalTime.of(7, 0), 60, null, at, isSnooze = snooze)

    @After fun clear() = AlarmService.screenGone()

    @Test fun oncePerRing() {
        AlarmService.screenGone()
        assertTrue(AlarmService.openScreenOnce(ring(1_000)))
        assertFalse(AlarmService.openScreenOnce(ring(1_000)))
        assertTrue("다시 울림은 새 울림", AlarmService.openScreenOnce(ring(1_000, snooze = true)))
    }

    @Test fun notOverTheScreenAlreadyUp() {
        AlarmService.screenShown(10)
        assertFalse(AlarmService.openScreenOnce(ring(2_000)))
        AlarmService.screenHidden(20)
        assertTrue("화면을 떠났다 오면 한 번 더", AlarmService.openScreenOnce(ring(2_000)))
    }
}
