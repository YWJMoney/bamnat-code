package com.bamnat.app.alarm

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.view.KeyEvent
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.BamnatApp
import com.bamnat.app.data.BackupManager
import com.bamnat.app.data.db.AppDatabase
import com.bamnat.core.journal.JournalKind
import com.bamnat.core.model.AlarmSpec
import com.bamnat.core.model.ShiftOverride
import com.bamnat.core.model.ShiftType
import com.bamnat.core.time.Today
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
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
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * 1.25 묶음 AB (계획/1.25-AB-개발계획.md): [ALRM-07] the ringing notice's three buttons and what keeps the ringing
 * screen off the lock screen, [ALRM-08] the volume and power buttons, [ALRM-09] 오늘 남은 알람 모두 끄기 from the
 * first ring to tomorrow's alarm, and 다시 켜기.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class AlarmButtonsAllOffTest {
    private val app get() = ApplicationProvider.getApplicationContext<BamnatApp>()
    private val c get() = app.container
    private val nm get() = shadowOf(app.getSystemService(NotificationManager::class.java))
    private var running: ServiceController<AlarmService>? = null
    private val zone = ZoneId.systemDefault()
    private val day = LocalDate.of(2026, 11, 2)

    private fun millis(t: LocalDateTime) = t.atZone(zone).toInstant().toEpochMilli()
    private fun at(t: LocalDateTime) = Today.fix(t.atZone(zone))

    private fun ring(type: Long = 3, trigger: LocalDateTime = day.atTime(6, 0), snooze: Boolean = false) =
        RingRequest("주간", day, LocalTime.of(7, 0), 60, null, millis(trigger), isSnooze = snooze, shiftTypeId = type)

    private fun start(ring: RingRequest): AlarmService {
        running?.get()?.onDestroy()
        val controller = Robolectric.buildService(AlarmService::class.java,
            ring.putInto(Intent(app, AlarmService::class.java).setAction(AlarmService.ACTION_START))).create().startCommand(0, 1)
        running = controller
        return controller.get()
    }

    private fun AlarmService.send(action: String, how: String? = null) {
        onStartCommand(Intent(app, AlarmService::class.java).setAction(action).apply { how?.let { putExtra("com.bamnat.app.alarm.extra.HOW", it) } }, 0, 2)
    }

    @Before fun setUp() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS, Manifest.permission.USE_FULL_SCREEN_INTENT)
        Notifications.createChannels(app)
        WakeCheck.awake(app)
        AlarmsOff.turnOn(app)
        AlarmService.screenGone()
    }

    @After fun tearDown() = runBlocking {
        running?.get()?.onDestroy()
        WakeCheck.awake(app)
        AlarmsOff.turnOn(app)
        AlarmService.screenGone()
        c.alarmScheduler.cancelSnooze()
        c.settingsRepository.update { it.copy(alarmMission = "none", shiftMissions = emptyMap(), wakeCheckMinutes = 0, alarmButtonAction = "dismiss") }
        Today.reset()
    }

    // ---- 412 오늘 남은 알람 모두 끄기 ----

    @Test fun todayEndsAtMidnightOrAtTheShiftItWokeFor() {
        val now = day.atTime(6, 0)
        assertEquals(day.plusDays(1).atStartOfDay(), AlarmsOff.cutoff(ring(), now))
        // 23:30 for a shift at 00:30: that shift's alarms go too.
        val late = RingRequest("야간", day.plusDays(1), LocalTime.of(0, 30), 60, null, millis(day.atTime(23, 30)))
        assertEquals(day.plusDays(1).atTime(0, 30), AlarmsOff.cutoff(late, day.atTime(23, 30)))
        // A memo's schedule or a test has no shift: midnight.
        assertEquals(day.plusDays(1).atStartOfDay(), AlarmsOff.cutoff(late.copy(label = "치과"), day.atTime(23, 30)))
        at(now)
        assertEquals("밤 12시", AlarmsOff.untilText(day.plusDays(1).atStartOfDay()))
        assertEquals("내일 00:30", AlarmsOff.untilText(day.plusDays(1).atTime(0, 30)))
    }

    /**
     * End to end: two alarms before 주간 (06:00, 06:30) today and tomorrow. The first rings, 오늘 모두 끄기 on its
     * notice: it stops, nothing more today (no 06:30, no snooze, no 일어났나요?), tomorrow's 06:00 is what the
     * system holds, the calendar line and the journal say so; 다시 켜기 brings 06:30 back.
     */
    @Test fun allOffFromTheFirstRingKeepsOnlyTomorrow() = runBlocking {
        at(day.atTime(5, 50))
        val repo = c.scheduleRepository
        val id = repo.saveShiftType(ShiftType(0, "주간", "주", 0xFF1E88E5, LocalTime.of(7, 0), LocalTime.of(16, 0), alarmEnabled = true,
            alarms = listOf(AlarmSpec(60), AlarmSpec(30))))
        repo.setOverride(day, ShiftOverride.Shift(id))
        repo.setOverride(day.plusDays(1), ShiftOverride.Shift(id))
        c.settingsRepository.update { it.copy(alarmsEnabled = true, wakeCheckMinutes = 5) }
        c.rescheduleAlarms()
        assertEquals(millis(day.atTime(6, 0)), c.alarmScheduler.upcoming.value!!.triggerAtMillis)

        at(day.atTime(6, 0))
        val first = RingRequest.from(c.alarmScheduler.upcoming.value!!.plan, millis(day.atTime(6, 0)))
        c.alarmScheduler.markFired(first.triggerAtMillis)
        val service = start(first)
        assertNotNull(AlarmService.ringing.value)
        // The ringing notice: 다시 울림, 끄기, 오늘 모두 끄기, all without unlocking.
        val actions = shadowOf(service).lastForegroundNotification.actions
        assertEquals(listOf("5분 뒤 다시 울림", "끄기", "오늘 모두 끄기"), actions.map { it.title.toString() })
        assertTrue(actions.none { it.isAuthenticationRequired })
        assertTrue(shadowOf(actions[2].actionIntent).isBroadcastIntent)
        // As the notice's button does it: through the receiver.
        val pressed = shadowOf(actions[2].actionIntent).savedIntent
        AlarmActionReceiver().onReceive(app, pressed)
        val asked = shadowOf(app).nextStartedService
        assertEquals(AlarmService.ACTION_ALL_OFF, asked.action)
        service.onStartCommand(asked, 0, 3)

        assertNull("stopped", AlarmService.ringing.value)
        assertNull("no 일어났나요?", WakeCheck.pendingRing(app))
        assertNull("no snooze", c.alarmScheduler.snoozeAt.value)
        assertEquals(millis(day.plusDays(1).atStartOfDay()), AlarmsOff.until.value)
        withTimeout(5_000) { c.alarmScheduler.upcoming.first { it?.triggerAtMillis == millis(day.plusDays(1).atTime(6, 0)) } }
        val notice = nm.allNotifications.single { it.extras.getString("android.title") == "오늘 남은 알람을 껐어요" }
        assertEquals("다시 켜기", notice.actions.single().title.toString())
        assertTrue(withTimeout(5_000) { c.journal.load() }.any { it.kind == JournalKind.ALARMS_OFF })

        // Anything that still comes today stays silent: the 06:30 alarm, had it been held, or a snooze.
        at(day.atTime(6, 30))
        start(ring(type = id, trigger = day.atTime(6, 30)))
        assertNull(AlarmService.ringing.value)
        start(ring(type = id, snooze = true))
        assertNull(AlarmService.ringing.value)
        // 알람 점검's test still rings.
        start(RingRequest(AlarmScheduler.TEST_NAME, day, LocalTime.of(6, 31), 0, null, millis(day.atTime(6, 31)), label = AlarmScheduler.TEST_NAME))
        assertNotNull(AlarmService.ringing.value)
        running?.get()?.onDestroy()

        // 다시 켜기 from the notice: what is still ahead today is back.
        at(day.atTime(6, 10))
        AlarmActionReceiver().onReceive(app, shadowOf(notice.actions.single().actionIntent).savedIntent)
        assertNull(AlarmsOff.until.value)
        withTimeout(5_000) { c.alarmScheduler.upcoming.first { it?.triggerAtMillis == millis(day.atTime(6, 30)) } }
        assertTrue(nm.allNotifications.none { it.extras.getString("android.title") == "오늘 남은 알람을 껐어요" })

        // Tomorrow at 06:00 the quiet is over by itself.
        AlarmsOff.turnOff(app, first, day.atTime(6, 10))
        at(day.plusDays(1).atTime(6, 0))
        assertFalse(AlarmsOff.isOff(app))
        start(ring(type = id, trigger = day.plusDays(1).atTime(6, 0)))
        assertNotNull(AlarmService.ringing.value)
    }

    @Test fun onAMissionAlarmAllOffWaitsForTheMission() {
        c.settingsRepository.update { it.copy(alarmMission = "math") }
        at(day.atTime(6, 0))
        val service = start(ring())
        val allOff = shadowOf(service).lastForegroundNotification.actions[2]
        assertEquals("오늘 모두 끄기", allOff.title.toString())
        assertTrue("opens the mission", shadowOf(allOff.actionIntent).isActivityIntent)
        assertTrue(shadowOf(allOff.actionIntent).savedIntent.getBooleanExtra(AlarmActivity.EXTRA_ALL_OFF, false))

        service.send(AlarmService.ACTION_ALL_OFF)
        assertNotNull("still ringing until the mission is done", AlarmService.ringing.value)
        assertTrue(AlarmService.allOffAfterMission.value)
        assertFalse(AlarmsOff.isOff(app))
        assertEquals("미션을 마치면 오늘 남은 알람이 모두 꺼져요", missionNote(AlarmButtons.Action.NONE, quiet = false, allOffWaits = true))

        service.send(AlarmService.ACTION_DISMISS, how = "mission")
        assertNull(AlarmService.ringing.value)
        assertTrue(AlarmsOff.isOff(app))
    }

    @Test fun aSnoozeInsteadOfTheMissionDoesNotTurnTodayOff() {
        c.settingsRepository.update { it.copy(alarmMission = "math") }
        at(day.atTime(6, 0))
        val service = start(ring())
        service.send(AlarmService.ACTION_ALL_OFF)
        service.send(AlarmService.ACTION_SNOOZE)
        assertFalse(AlarmsOff.isOff(app))
        assertFalse(AlarmService.allOffAfterMission.value)
        assertNotNull(c.alarmScheduler.snoozeAt.value)
    }

    // ---- 411 버튼으로 알람 끄기 ----

    @Test fun aButtonTurnsTheAlarmOffAsAnyOffDoes() {
        c.settingsRepository.update { it.copy(wakeCheckMinutes = 5) }
        at(day.atTime(6, 0))
        start(ring()).send(AlarmService.ACTION_BUTTON)
        assertNull(AlarmService.ringing.value)
        assertNotNull("일어났나요? still asks", WakeCheck.pendingRing(app))
        assertTrue(c.telemetry.log.read().any { it.name == com.bamnat.core.diag.DiagEvents.ALARM_DISMISSED && it.detail.startsWith("button") })
    }

    @Test fun theSettingCanMakeItASnoozeOrNothing() {
        at(day.atTime(6, 0))
        c.settingsRepository.update { it.copy(alarmButtonAction = "snooze") }
        start(ring()).send(AlarmService.ACTION_BUTTON)
        assertNull(AlarmService.ringing.value)
        assertEquals(millis(day.atTime(6, 5)), c.alarmScheduler.snoozeAt.value)

        c.settingsRepository.update { it.copy(alarmButtonAction = "none") }
        start(ring()).send(AlarmService.ACTION_BUTTON)
        assertNotNull("rings on", AlarmService.ringing.value)
    }

    @Test fun onAMissionAlarmAButtonOnlyQuietsTheSound() {
        c.settingsRepository.update { it.copy(alarmMission = "math") }
        at(day.atTime(6, 0))
        start(ring()).send(AlarmService.ACTION_BUTTON)
        assertNotNull("the mission stays", AlarmService.ringing.value)
        assertTrue(AlarmService.quiet.value)
        assertEquals("버튼을 눌러 소리를 30초 줄였어요", missionNote(AlarmButtons.Action.DISMISS, quiet = true, allOffWaits = false))
        shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(AlarmButtons.QUIET_SECONDS + 1L))
        assertFalse("loud again", AlarmService.quiet.value)
        assertNotNull(AlarmService.ringing.value)
    }

    @Test fun thePowerButtonCountsOnlyWithTheRingingScreenInFront() {
        val now = millis(day.atTime(6, 0))
        assertFalse("never shown", AlarmButtons.screenOffIsPress(0, 0, now))
        assertFalse("just shown: the screen settling", AlarmButtons.screenOffIsPress(now - 300, 0, now))
        assertTrue("in front", AlarmButtons.screenOffIsPress(now - 5_000, 0, now))
        assertTrue("stopped by the screen going off itself", AlarmButtons.screenOffIsPress(now - 5_000, now - 100, now))
        assertFalse("left long ago (home button)", AlarmButtons.screenOffIsPress(now - 60_000, now - 30_000, now))

        // The broadcast, while ringing with the screen in front.
        at(day.atTime(6, 0))
        start(ring())
        AlarmService.screenShown(now - 5_000)
        app.sendBroadcast(Intent(Intent.ACTION_SCREEN_OFF))
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertNull(AlarmService.ringing.value)

        // Not in front: the screen going off is no press.
        start(ring())
        AlarmService.screenShown(now - 60_000)
        AlarmService.screenHidden(now - 30_000)
        app.sendBroadcast(Intent(Intent.ACTION_SCREEN_OFF))
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertNotNull(AlarmService.ringing.value)
    }

    @Test fun theRingingScreenTakesTheVolumeKeys() {
        at(day.atTime(6, 0))
        start(ring())
        // [QA-09] Closed at the end, so the ringing screen does not stay open under the next tests in this JVM.
        val screen = Robolectric.buildActivity(AlarmActivity::class.java).setup()
        try {
            val activity = screen.get()
            assertTrue(activity.onKeyDown(KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_DOWN)))
            assertEquals(AlarmService.ACTION_BUTTON, shadowOf(app).nextStartedService.action)
            assertTrue("shown, for the power button", AlarmService.screenShownAt > 0)
            // 아무것도 안 함: the volume moves as usual.
            c.settingsRepository.update { it.copy(alarmButtonAction = "none") }
            assertFalse(activity.onKeyDown(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_UP)))
        } finally {
            screen.pause().stop().destroy()
        }
    }

    @Test fun theSettingIsInTheBackupAndTheLockedCopy() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            val settings = c.settingsRepository
            val backups = BackupManager(db, settings)
            com.bamnat.app.data.ScheduleRepository(db).ensureDefaults()
            assertEquals("끄기 at first", "dismiss", settings.current.alarmButtonAction)
            settings.update { it.copy(alarmButtonAction = "snooze") }
            val json = backups.export()
            settings.update { it.copy(alarmButtonAction = "dismiss") }
            backups.restore(json)
            assertEquals("snooze", settings.current.alarmButtonAction)
            assertEquals(AlarmButtons.Action.DISMISS, AlarmButtons.Action.of("junk"))
        } finally {
            db.close()
        }
        BootAlarms.save(app, emptyList(), BootAlarms.RingSettings(buttons = AlarmButtons.Action.NONE))
        assertEquals(AlarmButtons.Action.NONE, BootAlarms.settings(app).buttons)
        BootAlarms.save(app, emptyList(), BootAlarms.RingSettings())
    }

    // ---- 410 잠금 화면 ----

    @Test fun aRingThatCouldNotShowItsScreenSaysWhy() {
        at(day.atTime(6, 0))
        shadowOf(app).denyPermissions(Manifest.permission.USE_FULL_SCREEN_INTENT)
        assertFalse(Notifications.canUseFullScreen(app))
        try {
            start(ring()).send(AlarmService.ACTION_DISMISS)
            assertNotNull(nm.allNotifications.firstOrNull { it.extras.getString("android.title") == "잠금 화면 알람이 꺼져 있어 알림으로만 울렸어요" })
        } finally {
            shadowOf(app).grantPermissions(Manifest.permission.USE_FULL_SCREEN_INTENT)
        }
        app.getSystemService(NotificationManager::class.java).cancelAll()
        // With the ringing screen shown, nothing to say.
        start(ring())
        AlarmService.screenShown()
        running!!.get().send(AlarmService.ACTION_DISMISS)
        assertTrue(nm.allNotifications.none { it.extras.getString("android.title") == "잠금 화면 알람이 꺼져 있어 알림으로만 울렸어요" })
    }

    @Test fun aQuietOrHiddenAlarmChannelIsFound() {
        assertNull(Notifications.alarmChannelTrouble(app))
        val manager = app.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(Notifications.ALARM_CHANNEL_ID, "근무 알람", NotificationManager.IMPORTANCE_LOW))
        assertEquals("‘근무 알람’ 알림이 조용히로 바뀌어 알람 화면이 안 떠요", Notifications.alarmChannelTrouble(app))
        manager.deleteNotificationChannel(Notifications.ALARM_CHANNEL_ID)
        Notifications.createChannels(app)
        assertTrue(Notifications.makerLockScreenPermission("Xiaomi"))
        assertFalse(Notifications.makerLockScreenPermission("samsung"))
    }
}
