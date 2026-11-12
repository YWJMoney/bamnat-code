package com.bamnat.app.alarm

import android.media.AudioManager
import com.bamnat.app.alarm.CallHold.Outcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2.0.0 [RVI-36] (검토 PC-26·ALM-01) Long calls replayed at the service's 1.5초 checks: a phone call holds until it ends (the
 * PC's 255초 call rang right after it), a call in an app rings after 3분 and does not hold again in the same mode.
 */
class CallHoldTest {
    private val step = 1_500L

    private fun run(hold: CallHold, mode: Int, from: Long, millis: Long): List<CallHold.Step> =
        (0..millis / step).map { hold.check(mode, from + it * step) }

    @Test fun aPhoneCallHoldsUntilItEnds() {
        val hold = CallHold()
        val steps = run(hold, AudioManager.MODE_IN_CALL, 0L, 255_000L)
        assertTrue(steps.all { it.outcome == Outcome.HOLD })
        // Hung up: rings at once.
        val end = hold.check(AudioManager.MODE_NORMAL, 256_500L)
        assertEquals(Outcome.ENDED, end.outcome)
        assertEquals(255_000L, end.heldMillis)
        assertEquals(Outcome.FREE, hold.check(AudioManager.MODE_NORMAL, 258_000L).outcome)
    }

    @Test fun aCallInAnAppRingsAfterThreeMinutesAndStaysRinging() {
        val hold = CallHold()
        val steps = run(hold, AudioManager.MODE_IN_COMMUNICATION, 0L, 240_000L)
        val firstFree = steps.indexOfFirst { !it.holds }
        assertEquals(Outcome.CAPPED, steps[firstFree].outcome)
        assertTrue(steps[firstFree].heldMillis in AlarmService.APP_CALL_HOLD_MILLIS until AlarmService.APP_CALL_HOLD_MILLIS + step)
        // Before R391-02 the next check held another 3분 (1.5초 of sound every 3분); now it keeps ringing.
        assertTrue(steps.drop(firstFree + 1).none { it.holds })
        assertEquals("통화 앱이 소리를 쓰는 중이라 잠시 멈췄어요 · 3분 안에 다시 울려요", CallHold.line(AudioManager.MODE_IN_COMMUNICATION))
    }

    @Test fun aRealCallAfterTheCapHoldsAgain() {
        val hold = CallHold()
        run(hold, AudioManager.MODE_IN_COMMUNICATION, 0L, 200_000L)
        assertEquals(Outcome.HOLD, hold.check(AudioManager.MODE_RINGTONE, 201_500L).outcome)
        assertEquals(AudioManager.MODE_RINGTONE, hold.holding)
    }

    @Test fun aPhoneLeftInCallModeRingsAfterThirtyMinutes() {
        val hold = CallHold()
        val steps = run(hold, AudioManager.MODE_IN_CALL, 0L, 31 * 60_000L)
        val firstFree = steps.indexOfFirst { !it.holds }
        assertEquals(Outcome.CAPPED, steps[firstFree].outcome)
        assertTrue(steps[firstFree].heldMillis >= AlarmService.CALL_HOLD_MILLIS)
        assertEquals("통화 중이라 잠시 멈췄어요 · 통화가 끝나면 바로 울려요", CallHold.line(AudioManager.MODE_IN_CALL))
    }
}
