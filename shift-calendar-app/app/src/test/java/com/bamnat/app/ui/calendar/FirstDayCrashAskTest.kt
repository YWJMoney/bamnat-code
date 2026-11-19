package com.bamnat.app.ui.calendar

import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * 2.0.0 [RVL-85] (PC-190) The app closing on the day it was put on: '앱이 갑자기 닫혔어요' is not one of the asks that wait for the
 * next day, so the 알림함's 안내 row and the bell's mark are there that day.
 */
class FirstDayCrashAskTest {
    @Test fun theStopAskDoesNotWaitForTheNextDay() {
        assertFalse(FIRST_DAY_WAITS.toString(), "crashAsk" in FIRST_DAY_WAITS)
    }
}
