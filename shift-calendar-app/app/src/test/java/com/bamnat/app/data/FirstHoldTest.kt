package com.bamnat.app.data

import com.bamnat.core.work.WorkStyle
import org.junit.Assert.assertEquals
import org.junit.Test

/** [CELL-06] 1.39 발굴: a first setup of 일근·알바 leaves 날짜를 길게 누르면 at 메모 쓰기; nobody already using the app is changed. */
class FirstHoldTest {
    @Test fun aFirstSetupOfDayOrHourlyWorkWritesAMemoOnHold() {
        assertEquals("memo", firstHold(AppSettings(), WorkStyle.DAY))
        assertEquals("memo", firstHold(AppSettings(), WorkStyle.HOURLY))
        assertEquals("menu", firstHold(AppSettings(), WorkStyle.SHIFT))
        assertEquals("menu", firstHold(AppSettings(), WorkStyle.FIELD))
        assertEquals("menu", firstHold(AppSettings(), WorkStyle.BUSINESS))
    }

    @Test fun anyoneAlreadyUsingTheAppKeepsTheirs() {
        // A setup done again, or finished after being put off: the app was in use, so the hold stays.
        assertEquals("menu", firstHold(AppSettings(workStyle = WorkStyle.SHIFT), WorkStyle.DAY))
        assertEquals("menu", firstHold(AppSettings(workStyle = WorkStyle.DAY, setupLater = true), WorkStyle.DAY))
        // A choice of the user's own is never replaced.
        assertEquals("open", firstHold(AppSettings(longPress = "open"), WorkStyle.DAY))
    }
}
