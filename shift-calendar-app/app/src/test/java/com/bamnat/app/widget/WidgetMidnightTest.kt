package com.bamnat.app.widget

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.core.time.Today
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager

/**
 * 2.0.0 [RVI-37] (검토 ALM-02·PC-07, 도약 7: 자정 뒤 1분 안) The home screen's widgets moved to the new day up to 3분 50초 late:
 * the midnight wake-up was an inexact window. Where the app may set exact alarms it is exact, at 00:00:05; a phone that took that
 * away keeps the one-minute window, never the hour an inexact alarm may wait.
 */
@RunWith(AndroidJUnit4::class)
class WidgetMidnightTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val alarms get() = shadowOf(context.getSystemService(AlarmManager::class.java))

    @After fun reset() = ShadowAlarmManager.setCanScheduleExactAlarms(true)

    private fun midnight(): Long = Today.date().plusDays(1).atStartOfDay(Today.zone()).toInstant().toEpochMilli() + 5_000

    @Test fun theMidnightWakeUpIsExact() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        WidgetRefresher.scheduleNext(context, null)
        val next = alarms.peekNextScheduledAlarm()
        assertNotNull("the midnight wake-up is set", next)
        next!!
        assertEquals(AlarmManager.RTC, next.type)
        assertEquals("at 00:00:05", midnight(), next.triggerAtMs)
        assertEquals("exact, no window", 0L, next.windowLengthMs)
    }

    @Test fun withoutExactAlarmsItWaitsAMinuteAtMost() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        WidgetRefresher.scheduleNext(context, null)
        val next = alarms.peekNextScheduledAlarm()!!
        assertEquals(midnight(), next.triggerAtMs)
        assertEquals("one minute at most", 60_000L, next.windowLengthMs)
    }

    @Test fun aShiftEndingBeforeMidnightComesFirst() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        val end = Today.date().atTime(23, 0)
        WidgetRefresher.scheduleNext(context, end)
        val at = end.atZone(Today.zone()).toInstant().toEpochMilli() + 5_000
        // A test run just past 23:00 has that moment behind it; the wake-up is still the earlier of the two.
        assertEquals(minOf(at, midnight()), alarms.peekNextScheduledAlarm()!!.triggerAtMs)
    }
}
