package com.bamnat.app.alarm

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.BamnatApp
import com.bamnat.core.diag.DiagEvents
import com.bamnat.core.diag.EventBook
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
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * 2.0.0 [RVIB-28] (PC-188) 울리던 알람이 앱 프로세스가 죽으면 그대로 끝났어요(안드로이드 17 폴드, 구글 플레이 서비스 갱신 중, 53초 동안
 * 다시 울리지 않음). Now a ring keeps an exact alarm 30초 ahead while it rings: the app gone mid-ring, it goes off and the same alarm
 * rings again within a minute, from where it was, and 알람 점검 says so.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class RingKeeperTest {
    private val app get() = ApplicationProvider.getApplicationContext<BamnatApp>()
    private val c get() = app.container
    private val am get() = shadowOf(app.getSystemService(AlarmManager::class.java))
    private var running: ServiceController<AlarmService>? = null
    private val zone = ZoneId.systemDefault()
    private val day = LocalDate.of(2026, 11, 19)

    private fun millis(t: LocalDateTime) = t.atZone(zone).toInstant().toEpochMilli()
    private fun at(t: LocalDateTime) = Today.fix(t.atZone(zone))
    private fun ring() = RingRequest("주간", day, LocalTime.of(7, 0), 60, null, millis(day.atTime(6, 0)), shiftTypeId = 3)

    private fun start(intent: Intent): AlarmService {
        val controller = Robolectric.buildService(AlarmService::class.java, intent).create().startCommand(0, 1)
        running = controller
        return controller.get()
    }

    private fun keeperAlarm() = am.scheduledAlarms.lastOrNull { shadowOf(it.operation).savedIntent.action == RingKeeper.ACTION }

    @Before fun setUp() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS, Manifest.permission.USE_FULL_SCREEN_INTENT)
        Notifications.createChannels(app)
        WakeCheck.awake(app)
        AlarmsOff.turnOn(app)
        AlarmService.screenGone()
        AppSettled.alarms(app)
        RingKeeper.ended(app)
        c.telemetry.log.clear()
    }

    @After fun tearDown() {
        running?.get()?.let { it.onStartCommand(Intent(app, AlarmService::class.java).setAction(AlarmService.ACTION_DISMISS), 0, 9) }
        running?.get()?.onDestroy()
        RingKeeper.ended(app)
        AlarmService.screenGone()
        Today.reset()
    }

    /** The whole way: rings, the app dies mid-ring, the keeping alarm goes off, the same alarm rings again, 알람 점검 says how long. */
    @Test fun aRingCutByTheAppDyingRingsAgainWithinAMinute() {
        at(day.atTime(6, 0))
        start(ring().putInto(Intent(app, AlarmService::class.java).setAction(AlarmService.ACTION_START)))
        assertNotNull(AlarmService.ringing.value)
        val since = AlarmService.ringingSince.value!!
        val kept = keeperAlarm()
        assertNotNull("울리는 동안 지킴이 알람이 걸려 있어요", kept)
        assertTrue(kept!!.triggerAtTime - Today.millis() in 1..RingKeeper.KEEP_AHEAD_MILLIS)

        // Twelve seconds on, still ringing: the keeper is pushed on, never going off while the app is there.
        at(day.atTime(6, 0, 12))
        RingKeeper.push(app)
        assertTrue(keeperAlarm()!!.triggerAtTime >= Today.millis() + RingKeeper.KEEP_AHEAD_MILLIS - 1)

        // The process dies (kill -9): nothing finishes the ring; the service's state goes with the process.
        running!!.get().onDestroy()
        running = null
        assertNull(AlarmService.ringing.value)
        assertNotNull("죽어도 지킴이는 남아요", keeperAlarm())

        // 30 seconds after the last push, the keeping alarm goes off: within a minute of the cut.
        at(day.atTime(6, 0, 42))
        AlarmReceiver().onReceive(app, shadowOf(keeperAlarm()!!.operation).savedIntent)
        val again = shadowOf(app).nextStartedService
        assertEquals(AlarmService.ACTION_START, again.action)
        assertEquals(ring().encode(), RingRequest.fromIntent(again)!!.encode())
        start(again)
        assertNotNull(AlarmService.ringing.value)
        // The mission's 5 minutes and 자동으로 끄기 count from the first ring.
        assertEquals(since, AlarmService.ringingSince.value)

        val cut = EventBook.cuts(c.telemetry.log.read()).single()
        assertTrue(cut.resumed)
        assertEquals(30L, cut.silentSeconds)
        assertEquals(ring().triggerAtMillis, cut.trigger)
    }

    @Test fun anAlarmTurnedOffKeepsNothing() {
        at(day.atTime(6, 0))
        val s = start(ring().putInto(Intent(app, AlarmService::class.java).setAction(AlarmService.ACTION_START)))
        assertNotNull(keeperAlarm())
        s.onStartCommand(Intent(app, AlarmService::class.java).setAction(AlarmService.ACTION_DISMISS), 0, 2)
        assertNull(AlarmService.ringing.value)
        assertNull(RingKeeper.kept(app))
        assertNull("끈 알람은 다시 울리지 않아요", keeperAlarm())
        // A keeper that went off anyway (late) does nothing.
        AlarmReceiver().onReceive(app, Intent(app, AlarmReceiver::class.java).setAction(RingKeeper.ACTION))
        assertNull(shadowOf(app).nextStartedService)
    }

    @Test fun whatTheKeeperDoes() {
        val k = RingKeeper.Kept(ring(), since = 1_000_000L, pushedAt = 1_000_000L, resumes = 0)
        val ten = 10
        assertEquals(RingKeeper.Outcome.RING_AGAIN, RingKeeper.decide(k, ringingHere = false, now = 1_030_000L, ringMinutes = ten))
        assertEquals(RingKeeper.Outcome.STILL_RINGING, RingKeeper.decide(k, ringingHere = true, now = 1_030_000L, ringMinutes = ten))
        // Past 자동으로 끄기 from the first ring: not rung again.
        assertEquals(RingKeeper.Outcome.OVER, RingKeeper.decide(k, false, 1_000_000L + 10 * 60_000L, ten))
        // Cut again and again: the app dies as it rings; the loud notice instead.
        assertEquals(RingKeeper.Outcome.TOO_MANY, RingKeeper.decide(k.copy(resumes = RingKeeper.MAX_RESUMES), false, 1_030_000L, ten))
        assertEquals(RingKeeper.Outcome.NOTHING_KEPT, RingKeeper.decide(null, false, 1_030_000L, ten))
    }

    @Test fun theSameRingRungAgainKeepsItsFirstTime() {
        at(day.atTime(6, 0))
        val first = RingKeeper.started(app, ring())
        at(day.atTime(6, 1))
        assertEquals(first, RingKeeper.started(app, ring()))
        // Another alarm is its own ring.
        val other = ring().copy(triggerAtMillis = millis(day.atTime(6, 30)))
        assertEquals(Today.millis(), RingKeeper.started(app, other))
    }

    /** Stopped by force (which takes the app's alarms too): never rung again, so the next start puts it among the possible misses. */
    @Test fun aRingNeverRungAgainIsAPossibleMiss() = runBlocking {
        at(day.atTime(6, 0))
        RingKeeper.started(app, ring())
        at(day.atTime(6, 20))
        RingKeeper.settle(app) { n, d -> c.telemetry.event(n, d) }
        assertNull(RingKeeper.kept(app))
        val cut = EventBook.cuts(c.telemetry.log.read()).single()
        assertTrue(cut.lost)
        assertEquals(DiagEvents.ALARM_CUT, c.telemetry.log.read().last { it.name == DiagEvents.ALARM_CUT }.name)
    }
}
