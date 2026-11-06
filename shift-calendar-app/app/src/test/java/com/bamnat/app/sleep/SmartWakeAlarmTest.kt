package com.bamnat.app.sleep

import android.Manifest
import android.app.AlarmManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.BamnatApp
import com.bamnat.app.alarm.AlarmSoon
import com.bamnat.app.alarm.AlarmsOff
import com.bamnat.app.alarm.Notifications
import com.bamnat.core.model.AlarmSpec
import com.bamnat.core.model.ShiftOverride
import com.bamnat.core.model.ShiftType
import com.bamnat.core.time.Today
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * 1.38 [SLEEP-08] 깨우는 구간 keeps 갈래1's promises: off by default nothing more is armed, on it is a separate alarm
 * before the on-time one, which stays the same; rung early, the on-time ring of that alarm is off and the next one stands.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class SmartWakeAlarmTest {
    private val app get() = ApplicationProvider.getApplicationContext<BamnatApp>()
    private val c get() = app.container
    private val am get() = shadowOf(app.getSystemService(AlarmManager::class.java))
    private val zone = ZoneId.systemDefault()
    private val day = LocalDate.of(2026, 11, 2)
    private fun millis(t: LocalDateTime) = t.atZone(zone).toInstant().toEpochMilli()

    @Before fun setUp() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        Notifications.createChannels(app)
        AlarmsOff.turnOn(app)
        AlarmSoon.unskip(app)
    }

    @After fun tearDown() = runBlocking {
        AlarmSoon.unskip(app)
        c.settingsRepository.update { it.copy(smartWakeMinutes = 0) }
        Today.reset()
    }

    private suspend fun mornings() {
        val repo = c.scheduleRepository
        val id = repo.saveShiftType(ShiftType(0, "주간", "주", 0xFF1E88E5, LocalTime.of(7, 0), LocalTime.of(16, 0), alarmEnabled = true,
            alarms = listOf(AlarmSpec(60))))
        repo.setOverride(day, ShiftOverride.Shift(id))
        repo.setOverride(day.plusDays(1), ShiftOverride.Shift(id))
        c.settingsRepository.update { it.copy(alarmsEnabled = true, alarmSoonMinutes = 0) }
    }

    private fun windowAlarm() = am.scheduledAlarms.firstOrNull { it.operation?.let { op -> shadowOf(op).savedIntent.action } == SmartWake.ACTION_WINDOW }

    @Test fun offByDefaultNothingMoreIsArmed() = runBlocking {
        Today.fix(day.atTime(1, 0).atZone(zone))
        mornings()
        c.rescheduleAlarms()
        assertEquals(millis(day.atTime(6, 0)), c.alarmScheduler.upcoming.value!!.triggerAtMillis)
        assertNull(windowAlarm())
    }

    @Test fun aWindowIsItsOwnAlarmAndTheOnTimeOneStays() = runBlocking {
        Today.fix(day.atTime(1, 0).atZone(zone))
        mornings()
        c.settingsRepository.update { it.copy(smartWakeMinutes = 20) }
        c.rescheduleAlarms()
        val six = millis(day.atTime(6, 0))
        assertEquals("the alarm itself does not move", six, c.alarmScheduler.upcoming.value!!.triggerAtMillis)
        assertEquals(six - 20 * 60_000L, windowAlarm()!!.triggerAtTime)

        // Rung early in the window: the on-time ring of this alarm is off and tomorrow's is next.
        AlarmSoon.skipQuietly(app, six)
        c.rescheduleAlarms()
        assertTrue(AlarmSoon.isSkipped(app, six))
        assertEquals(millis(day.plusDays(1).atTime(6, 0)), c.alarmScheduler.upcoming.value!!.triggerAtMillis)
        assertEquals(millis(day.plusDays(1).atTime(6, 0)) - 20 * 60_000L, windowAlarm()!!.triggerAtTime)
    }
}
