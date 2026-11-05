package com.bamnat.app.alarm

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.media.AudioManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.BamnatApp
import com.bamnat.app.ui.diag.AlarmCheck
import com.bamnat.app.ui.diag.Fix
import com.bamnat.core.model.AlarmSpec
import com.bamnat.core.model.ShiftOverride
import com.bamnat.core.model.ShiftType
import com.bamnat.core.time.Today
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** [ALRM-27] 알람 지킴이: a few hours before an alarm, a notice when something would keep it from ringing; quiet otherwise. */
@RunWith(AndroidJUnit4::class)
// Android 13: full-screen alarms need no grant, which Robolectric cannot give on 14+.
@Config(sdk = [33])
class AlarmGuardTest {
    private val app get() = ApplicationProvider.getApplicationContext<BamnatApp>()
    private val c get() = app.container
    private val nm get() = shadowOf(app.getSystemService(NotificationManager::class.java))
    private val audio get() = app.getSystemService(AudioManager::class.java)
    private val zone = ZoneId.systemDefault()
    private val day = LocalDate.of(2026, 11, 6)
    private val six get() = day.atTime(6, 0).atZone(zone).toInstant().toEpochMilli()

    @Before fun setUp() = runBlocking {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS, Manifest.permission.USE_FULL_SCREEN_INTENT)
        Notifications.createChannels(app)
        org.robolectric.shadows.ShadowAlarmManager.setCanScheduleExactAlarms(true)
        audio.setStreamVolume(AudioManager.STREAM_ALARM, audio.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0)
        AlarmsOff.turnOn(app)
        AlarmSoon.unskip(app)
        Today.fix(day.atTime(2, 0).atZone(zone))
        val id = c.scheduleRepository.saveShiftType(ShiftType(0, "주간", "주", 0xFF1E88E5, LocalTime.of(7, 0), LocalTime.of(16, 0),
            alarmEnabled = true, alarms = listOf(AlarmSpec(60))))
        c.scheduleRepository.setOverride(day, ShiftOverride.Shift(id))
        c.settingsRepository.update { it.copy(alarmsEnabled = true, alarmGuard = true) }
        c.rescheduleAlarms()
    }

    @After fun tearDown() {
        audio.setStreamVolume(AudioManager.STREAM_ALARM, audio.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0)
        Today.reset()
    }

    private fun look() = AlarmActionReceiver().onReceive(app,
        Intent(app, AlarmActionReceiver::class.java).setAction(AlarmGuard.ACTION_GUARD).putExtra("at", six))

    @Test fun looksThreeHoursAheadButNeverTooClose() {
        val now = 0L
        val alarm = 10 * 3_600_000L
        assertEquals(alarm - AlarmGuard.LEAD_MILLIS, AlarmGuard.lookAt(alarm, now))
        // An alarm in an hour: a minute from now.
        assertEquals(60_000L, AlarmGuard.lookAt(3_600_000L, now))
        assertNull(AlarmGuard.lookAt(10 * 60_000L, now))
    }

    @Test fun onlyWhatStopsTheAlarmCounts() {
        val bad = listOf(
            AlarmCheck("알람 소리 크기", false, "", "", Fix.AlarmVolume),
            AlarmCheck("배터리 최적화 제외", false, "", "", Fix.Repair),
            AlarmCheck("정확한 알람", true, "", "", Fix.Repair),
        )
        assertEquals(listOf("알람 소리 크기"), AlarmGuard.problems(bad).map { it.title })
    }

    @Test fun silentAlarmVolumeIsSaidBeforeTheAlarm() {
        audio.setStreamVolume(AudioManager.STREAM_ALARM, 0, 0)
        look()
        val n = nm.getNotification(AlarmGuard.NOTICE_ID)
        assertNotNull("the notice says the alarm may not be heard", n)
        assertEquals(AlarmGuard.title("06:00"), n.extras.getString("android.title"))
        assertTrue(n.extras.getCharSequence("android.text").toString().startsWith("알람 소리 크기"))
        // Once a day for the same problem.
        app.getSystemService(NotificationManager::class.java).cancelAll()
        look()
        assertNull(nm.getNotification(AlarmGuard.NOTICE_ID))
    }

    @Test fun quietWhenAllIsWellOrTurnedOff() {
        val left = AlarmGuard.problems(com.bamnat.app.ui.diag.alarmChecks(c, app)).map { it.title }
        assertEquals("the test phone is set up for alarms", emptyList<String>(), left)
        look()
        assertNull("nothing wrong, nothing said", nm.getNotification(AlarmGuard.NOTICE_ID))
        audio.setStreamVolume(AudioManager.STREAM_ALARM, 0, 0)
        c.settingsRepository.update { it.copy(alarmGuard = false) }
        look()
        assertNull("알람 지킴이 off", nm.getNotification(AlarmGuard.NOTICE_ID))
        // An alarm turned off ahead is not warned about.
        c.settingsRepository.update { it.copy(alarmGuard = true) }
        AlarmSoon.skip(app, six)
        look()
        assertNull(nm.getNotification(AlarmGuard.NOTICE_ID))
        AlarmSoon.unskip(app)
    }
}
