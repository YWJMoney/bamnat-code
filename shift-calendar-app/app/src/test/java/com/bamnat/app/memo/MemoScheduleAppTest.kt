package com.bamnat.app.memo

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.alarm.Notifications
import com.bamnat.app.appContainer
import com.bamnat.app.alarm.RingRequest
import com.bamnat.app.data.ConsentStore
import com.bamnat.core.consent.ConsentItem
import com.bamnat.core.consent.ConsentPlan
import com.bamnat.core.memo.Memo
import com.bamnat.core.memo.MemoReminder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** [SCHED-01] Memo schedules ring or notify at their minute; [ONBOARD-01] what the phone was told is kept. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class MemoScheduleAppTest {
    private val context: Application = ApplicationProvider.getApplicationContext()
    private val nm = context.getSystemService(NotificationManager::class.java)
    private val alarms = shadowOf(context.getSystemService(AlarmManager::class.java))
    private fun millis(t: LocalDateTime) = t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private fun ours() = alarms.scheduledAlarms.filter {
        shadowOf(it.operation).savedIntent.component?.className == MemoScheduleReceiver::class.java.name
    }

    @Test fun oneAlarmIsKeptForTheNextScheduleAndNoneWithout() {
        val now = LocalDateTime.now()
        val soon = now.plusHours(2).withSecond(0).withNano(0)
        val memos = listOf(
            Memo(id = 1, title = "치과", reminder = MemoReminder(soon, ring = false, notify = true)),
            Memo(id = 2, title = "면접", reminder = MemoReminder(soon.plusDays(1), ring = true, notify = false)),
            Memo(id = 3, title = "그냥 메모"),
        )
        MemoScheduleAlarm.reschedule(context, memos, now)
        assertEquals(listOf(millis(soon)), ours().map { it.triggerAtTime })
        // Past the first one, the ringing one is next.
        MemoScheduleAlarm.reschedule(context, memos, soon)
        assertEquals(listOf(millis(soon.plusDays(1))), ours().map { it.triggerAtTime })
        MemoScheduleAlarm.reschedule(context, memos.take(0), now)
        assertTrue(ours().isEmpty())
    }

    @Test fun aNoticeSaysWhatAndWhenAndALateOneIsDropped() {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        Notifications.createChannels(context)
        val at = LocalDateTime.of(LocalDate.now(), LocalTime.of(15, 0))
        val memo = Memo(id = 7, title = "치과", body = "스케일링", reminder = MemoReminder(at, ring = false, notify = true))
        MemoScheduleAlarm.handle(context, listOf(memo), millis(at), now = millis(at) + 30_000)
        val posted = shadowOf(nm).allNotifications.single()
        assertEquals(Notifications.MEMO_CHANNEL_ID, posted.channelId)
        assertEquals("치과", shadowOf(posted).contentTitle.toString())
        assertEquals("오후 3:00 일정 · 스케일링", shadowOf(posted).contentText.toString())

        nm.cancelAll()
        MemoScheduleAlarm.handle(context, listOf(memo), millis(at), now = millis(at) + 3 * 60 * 60 * 1000L)
        assertTrue(shadowOf(nm).allNotifications.isEmpty())
    }

    /**
     * 1.39 [TASK-04][TASK-20][TASK-39] The notice's buttons: 끝내기 · 10분 뒤 · 다른 때…, the last opening the window that offers the
     * next day off first (내일 이 시각 with none ahead).
     */
    @Test fun aNoticeOffersToFinishOrPutItOff() {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        Notifications.createChannels(context)
        val at = LocalDateTime.of(LocalDate.now(), LocalTime.of(15, 0))
        val memo = Memo(id = 8, title = "택배 받기", reminder = MemoReminder(at, ring = false, notify = true))
        val rest = LocalDate.of(2026, 11, 12)
        MemoScheduleAlarm.handle(context, listOf(memo), millis(at), now = millis(at) + 30_000, restOf = { rest })
        val posted = shadowOf(nm).allNotifications.single()
        assertEquals(listOf("끝내기", "10분 뒤", "다른 때…"), posted.actions.map { it.title.toString() })
        val done = shadowOf(posted.actions[0].actionIntent).savedIntent
        assertEquals(MemoDoneReceiver::class.java.name, done.component?.className)
        assertEquals(8L, done.getLongExtra("memo", -1))
        assertEquals(at.toLocalDate().toEpochDay(), done.getLongExtra("day", 0))
        val ten = shadowOf(posted.actions[1].actionIntent).savedIntent
        assertEquals(MemoSnoozeReceiver::class.java.name, ten.component?.className)
        assertEquals("m10", ten.getStringExtra("way"))
        val later = shadowOf(posted.actions[2].actionIntent)
        assertTrue("다른 때… opens the window", later.isActivityIntent)
        assertEquals(MemoLaterActivity::class.java.name, later.savedIntent.component?.className)
        assertEquals(rest.toEpochDay(), later.savedIntent.getLongExtra("rest", 0))
    }

    /** 1.39 [TASK-39] 끝내기 finishes the memo for real (the 할 일 앱's Complete left it open), a repeating one on that day only. */
    @Test fun finishingFromTheNoticeFinishesTheMemo() = kotlinx.coroutines.runBlocking<Unit> {
        val repo = context.appContainer.memoRepository
        val day = LocalDate.now()
        val once = repo.save(Memo(date = day, title = "택배 받기", reminder = MemoReminder(day.atTime(15, 0), ring = false, notify = true)))
        val done = MemoDoneReceiver.finish(context, once.id, day)!!
        assertTrue(done.isDoneOn(day))
        assertTrue("in the list too", repo.get(once.id)!!.isDoneOn(day))
        // A second press leaves it finished, not back to do.
        assertTrue(MemoDoneReceiver.finish(context, once.id, day)!!.isDoneOn(day))
        val daily = repo.save(Memo(date = day, title = "물 마시기", repeat = "D",
            reminder = MemoReminder(day.atTime(9, 0), ring = false, notify = true)))
        val one = MemoDoneReceiver.finish(context, daily.id, day)!!
        assertTrue(one.isDoneOn(day))
        assertTrue("tomorrow is still to do", !one.isDoneOn(day.plusDays(1)))
        assertNull(MemoDoneReceiver.finish(context, 987_654L, day))
    }

    /** 1.39 [TASK-39] A way picked in 다른 때… moves the schedule from now; the toast says when. */
    @Test fun aWayPickedMovesTheSchedule() = kotlinx.coroutines.runBlocking<Unit> {
        val repo = context.appContainer.memoRepository
        val day = LocalDate.now()
        val memo = repo.save(Memo(date = day, title = "약 먹기", reminder = MemoReminder(day.atTime(8, 0), ring = false, notify = true)))
        val rest = day.plusDays(3)
        val at = MemoSnoozeReceiver.put(context, memo.id, com.bamnat.core.memo.MemoSnooze.Way.REST, rest)!!
        assertEquals(rest.atTime(com.bamnat.core.memo.ShiftMemoTime.OFF_TIME), at)
        assertEquals(at, repo.get(memo.id)!!.reminder!!.at)
        val now = LocalDateTime.of(2026, 11, 8, 14, 3)
        assertEquals("오후 2:13", laterSaid(now.plusMinutes(10), now))
        assertEquals("내일 오후 2:00", laterSaid(LocalDateTime.of(2026, 11, 9, 14, 0), now))
        assertEquals("11/12 (목) 오전 9:00", laterSaid(LocalDateTime.of(2026, 11, 12, 9, 0), now))
    }

    @Test fun aMemoScheduleRingsUnderItsOwnWords() {
        val ring = RingRequest("", LocalDate.now(), LocalTime.of(15, 0), 0, null, 1L, custom = true, label = "치과")
        assertEquals("치과", ring.title)
        assertEquals("치과", RingRequest.decode(ring.encode())!!.label)
        assertNull(RingRequest.decode(RingRequest("주간", LocalDate.now(), LocalTime.of(7, 0), 60, null, 1L).encode())!!.label)
    }

    @Test fun thePagesAreRememberedAndAPhoneSetUpBeforeCountsWhatItWasAskedThen() {
        val store = ConsentStore(context)
        store.forget()
        assertNull(store.seen.value)
        store.markFirstStartDone(setOf(ConsentItem.NOTIFICATIONS, ConsentItem.CALENDAR))
        assertEquals(ConsentPlan.BEFORE_PAGES + ConsentItem.CALENDAR, ConsentStore(context).seen.value)
        store.forget()
        store.markSeen(emptyList())
        assertEquals(ConsentPlan.BEFORE_PAGES, ConsentStore(context).seen.value)
    }
}
