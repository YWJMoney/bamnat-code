package com.bamnat.app.alarm

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Intent
import android.media.AudioManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.BamnatApp
import com.bamnat.core.model.AlarmSpec
import com.bamnat.core.model.ShiftOverride
import com.bamnat.core.model.ShiftType
import com.bamnat.core.time.Today
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.TimeUnit
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * 1.35 [ALRM-21] 곧 알람이 울려요: shown only while the phone is in use, and 이번 알람 끄기 skips that one alarm alone.
 * [ALRM-22] A call and an alarm at once: the ring holds its sound and its clock during the call and comes back after.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class AlarmSoonCallTest {
    private val app get() = ApplicationProvider.getApplicationContext<BamnatApp>()
    private val c get() = app.container
    private val nm get() = shadowOf(app.getSystemService(NotificationManager::class.java))
    private val audio get() = app.getSystemService(AudioManager::class.java)
    private var running: ServiceController<AlarmService>? = null
    private val zone = ZoneId.systemDefault()
    private val day = LocalDate.of(2026, 11, 2)

    private fun millis(t: LocalDateTime) = t.atZone(zone).toInstant().toEpochMilli()
    private fun at(t: LocalDateTime) = Today.fix(t.atZone(zone))

    /**
     * 2.0.0 [RVR-18] (검토 TEST-09) The clock moves with the looper: [seconds] pass 1.5초 at a time (the service looks for a
     * call that often), so a hold that reads Today runs out as it does on a phone. Before, the clock stood still while the
     * looper ran 30분, and a hold that never ended (ALM-01) passed. [each] runs after every step.
     */
    private var now: LocalDateTime = day.atTime(6, 0)
    private fun advance(seconds: Long, each: () -> Unit = {}) {
        repeat((seconds * 2 / 3).toInt()) {
            now = now.plusNanos(1_500_000_000L)
            at(now)
            ShadowLooper.idleMainLooper(1_500, TimeUnit.MILLISECONDS)
            each()
        }
    }

    @Before fun setUp() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS, Manifest.permission.USE_FULL_SCREEN_INTENT)
        Notifications.createChannels(app)
        AlarmsOff.turnOn(app)
        AlarmSoon.unskip(app)
        audio.mode = AudioManager.MODE_NORMAL
    }

    @After fun tearDown() = runBlocking {
        running?.get()?.onDestroy()
        AlarmSoon.unskip(app)
        audio.mode = AudioManager.MODE_NORMAL
        Today.reset()
    }

    private suspend fun twoMornings(days: Int = 2): Long {
        val repo = c.scheduleRepository
        val id = repo.saveShiftType(ShiftType(0, "주간", "주", 0xFF1E88E5, LocalTime.of(7, 0), LocalTime.of(16, 0), alarmEnabled = true,
            alarms = listOf(AlarmSpec(60))))
        repo.setOverride(day, ShiftOverride.Shift(id))
        repo.setOverride(day.plusDays(1), ShiftOverride.Shift(id))
        for (i in 2 until days) repo.setOverride(day.plusDays(i.toLong()), ShiftOverride.Shift(id))
        c.settingsRepository.update { it.copy(alarmsEnabled = true, alarmSoonMinutes = 5) }
        c.rescheduleAlarms()
        return id
    }

    @Test fun skippingTheNextAlarmKeepsTheOneAfter() = runBlocking {
        at(day.atTime(5, 50))
        twoMornings()
        val six = millis(day.atTime(6, 0))
        assertEquals(six, c.alarmScheduler.upcoming.value!!.triggerAtMillis)
        // The look ahead waits five minutes before it.
        val soon = shadowOf(app.getSystemService(AlarmManager::class.java)).scheduledAlarms
            .firstOrNull { it.triggerAtTime == six - 5 * 60_000L }
        assertNotNull("곧 알람이 울려요 is registered 5 minutes ahead", soon)

        // The phone in hand: the notice shows, with 이번 알람 끄기.
        at(day.atTime(5, 55))
        AlarmActionReceiver().onReceive(app, Intent(app, AlarmActionReceiver::class.java).setAction(AlarmSoon.ACTION_SOON).putExtra("at", six).putExtra("text", "주간 근무 알람이에요"))
        val notice = nm.getNotification(AlarmSoon.NOTICE_ID)
        assertNotNull(notice)
        assertEquals(listOf("이번 알람 끄기", "그대로 두기"), notice.actions.map { it.title.toString() })

        AlarmSoon.skip(app, six)
        c.rescheduleAlarms()
        assertEquals("tomorrow's alarm is next", millis(day.plusDays(1).atTime(6, 0)), c.alarmScheduler.upcoming.value!!.triggerAtMillis)
        val skipped = RingRequest("주간", day, LocalTime.of(7, 0), 60, null, six)
        assertTrue(AlarmSoon.silences(app, skipped))
        assertFalse("a snooze is not the skipped alarm", AlarmSoon.silences(app, skipped.copy(isSnooze = true)))

        // 다시 켜기 while it is still ahead.
        AlarmSoon.unskip(app)
        c.rescheduleAlarms()
        assertEquals(six, c.alarmScheduler.upcoming.value!!.triggerAtMillis)
    }

    /** [ALRM-26] Several alarms ahead put aside at once, each brought back on its own; the card lists them. */
    @Test fun skippingSeveralKeepsTheRest() = runBlocking {
        at(day.atTime(5, 0))
        twoMornings(days = 3)
        val one = millis(day.atTime(6, 0))
        val two = millis(day.plusDays(1).atTime(6, 0))
        val three = millis(day.plusDays(2).atTime(6, 0))
        assertEquals(listOf(one, two, three), c.alarmScheduler.ahead.value.map { it.triggerAtMillis })
        AlarmSoon.skip(app, one)
        AlarmSoon.skip(app, two)
        c.rescheduleAlarms()
        assertEquals("both off, the third rings", three, c.alarmScheduler.upcoming.value!!.triggerAtMillis)
        assertEquals(listOf(one, two), AlarmSoon.skippedAll(app))
        // The card still lists the two turned off, with the one that rings.
        assertEquals(listOf(one, two, three), c.alarmScheduler.ahead.value.map { it.triggerAtMillis })
        // 다시 켜기 on the second alone.
        AlarmSoon.unskip(app, two)
        c.rescheduleAlarms()
        assertEquals(two, c.alarmScheduler.upcoming.value!!.triggerAtMillis)
        assertEquals(listOf(one), AlarmSoon.skippedAll(app))
        assertTrue(AlarmSoon.silences(app, RingRequest("주간", day, LocalTime.of(7, 0), 60, null, one)))
        assertFalse(AlarmSoon.silences(app, RingRequest("주간", day.plusDays(1), LocalTime.of(7, 0), 60, null, two)))
        // One that has passed is forgotten.
        at(day.atTime(6, 5))
        assertTrue(AlarmSoon.skippedAll(app).isEmpty())
    }

    /** [ALRM-26] Up to the third alarm that still rings, with those turned off in between. */
    @Test fun cardListsThreeThatRing() {
        val r = { m: Long -> RingRequest("주간", day, LocalTime.of(7, 0), 60, null, m) }
        val all = (1L..6L).map(r)
        assertEquals(listOf(1L, 2L, 3L), AlarmScheduler.shownAhead(all, emptySet()).map { it.triggerAtMillis })
        assertEquals(listOf(1L, 2L, 3L, 4L), AlarmScheduler.shownAhead(all, setOf(2L)).map { it.triggerAtMillis })
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), AlarmScheduler.shownAhead(all, setOf(1L, 2L)).map { it.triggerAtMillis })
    }

    @Test fun nothingShowsWhileThePhoneIsAsleep() {
        at(day.atTime(5, 55))
        shadowOf(app.getSystemService(android.os.PowerManager::class.java)).turnScreenOn(false)
        try {
            AlarmSoon.onSoon(app, Intent().putExtra("at", millis(day.atTime(6, 0))))
            assertNull(nm.getNotification(AlarmSoon.NOTICE_ID))
        } finally {
            shadowOf(app.getSystemService(android.os.PowerManager::class.java)).turnScreenOn(true)
        }
    }

    @Test fun aCallHoldsTheRingAndItComesBackAfter() {
        at(now)
        val ring = RingRequest("주간", day, LocalTime.of(7, 0), 60, null, millis(now), shiftTypeId = 3)
        running = Robolectric.buildService(AlarmService::class.java,
            ring.putInto(Intent(app, AlarmService::class.java).setAction(AlarmService.ACTION_START))).create().startCommand(0, 1)
        assertFalse(AlarmService.inCall.value)

        audio.mode = AudioManager.MODE_RINGTONE
        advance(3)
        assertTrue("held for the call", AlarmService.inCall.value)
        val held = nm.getNotification(Notifications.ALARM_NOTIFICATION_ID)
        assertTrue(held.extras.getCharSequence("android.text").toString().contains("통화"))
        assertNull("no screen over the call", held.fullScreenIntent)
        // A long call (29분, under the 30분 cap, with the clock moving) does not use up 자동으로 끄기.
        audio.mode = AudioManager.MODE_IN_CALL
        advance(29 * 60) { assertTrue("held through the call", AlarmService.inCall.value) }
        assertNotNull("still waiting for the call", AlarmService.ringing.value)

        audio.mode = AudioManager.MODE_NORMAL
        advance(3)
        assertFalse(AlarmService.inCall.value)
        assertNotNull(AlarmService.ringing.value)
        assertNotNull("rings with its screen again", nm.getNotification(Notifications.ALARM_NOTIFICATION_ID).fullScreenIntent)
    }

    /** 2.0.0 [RVR-18] (검토 TEST-09) A phone call longer than 30분: the alarm rings at 30분 and the same call does not hold it again. */
    @Test fun aPhoneCallOverThirtyMinutesRingsAndDoesNotHoldAgain() {
        at(now)
        val ring = RingRequest("주간", day, LocalTime.of(7, 0), 60, null, millis(now), shiftTypeId = 3)
        running = Robolectric.buildService(AlarmService::class.java,
            ring.putInto(Intent(app, AlarmService::class.java).setAction(AlarmService.ACTION_START))).create().startCommand(0, 1)
        audio.mode = AudioManager.MODE_IN_CALL
        advance(3)
        assertTrue("held for the call", AlarmService.inCall.value)
        advance(AlarmService.CALL_HOLD_MILLIS / 1000 + 60)
        assertFalse("rings after 30분 even though the call goes on", AlarmService.inCall.value)
        assertNotNull(AlarmService.ringing.value)
        advance(2 * 60) { assertFalse("the call that used up its hold does not hold it again", AlarmService.inCall.value) }
    }

    // 1.39.1 [R391-02] 검토 ALM-01: a phone left in 메신저 통화 mode held the ring 3분, rang 1.5초, then held 3분 again.
    @Test fun aCallModeLeftOnHoldsOnceThenKeepsRinging() {
        at(now)
        val ring = RingRequest("주간", day, LocalTime.of(7, 0), 60, null, millis(now), shiftTypeId = 3)
        running = Robolectric.buildService(AlarmService::class.java,
            ring.putInto(Intent(app, AlarmService::class.java).setAction(AlarmService.ACTION_START))).create().startCommand(0, 1)
        audio.mode = AudioManager.MODE_IN_COMMUNICATION
        // The clock the hold reads moves with the looper, 1.5초 at a time ([advance]).
        advance(3)
        assertTrue("held for the app call", AlarmService.inCall.value)
        advance(AlarmService.APP_CALL_HOLD_MILLIS / 1000)
        assertFalse("rings after 3분", AlarmService.inCall.value)
        advance(5 * 60) { assertFalse("keeps ringing, the cap is not taken again", AlarmService.inCall.value) }
        // A real call after it still holds.
        audio.mode = AudioManager.MODE_IN_CALL
        advance(3)
        assertTrue("a phone call holds it", AlarmService.inCall.value)
    }
}
