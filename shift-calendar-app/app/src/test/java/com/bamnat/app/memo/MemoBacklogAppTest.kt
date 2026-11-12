package com.bamnat.app.memo

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.alarm.Notifications
import com.bamnat.app.appContainer
import com.bamnat.app.ui.memo.OldWay
import com.bamnat.core.memo.Memo
import com.bamnat.core.memo.MemoReminder
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * 2.0.0 [RVIB-05] (검토 I07ac-02) 근무 중에 온 일정은 퇴근 때 한 장(소리 한 번), 울리는 알람은 제때; 지난 할 일은 쉬는 날 아침 한 장, 하루
 * 한 번, 세 답이 모두에.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class MemoBacklogAppTest {
    private val context: Application = ApplicationProvider.getApplicationContext()
    private val nm = context.getSystemService(NotificationManager::class.java)
    private fun millis(t: LocalDateTime) = t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Before fun channels() {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        Notifications.createChannels(context)
    }

    @Test fun atWorkTheyWaitAndComeInOneCardWhenItEnds() {
        val at = LocalDateTime.of(LocalDate.now(), LocalTime.of(15, 0))
        val end = at.toLocalDate().atTime(18, 0)
        val memos = listOf(
            Memo(id = 1, title = "약 받기", date = at.toLocalDate(), reminder = MemoReminder(at, ring = false, notify = true)),
            Memo(id = 2, title = "택배 찾기", date = at.toLocalDate(), reminder = MemoReminder(at, ring = false, notify = true)),
        )
        MemoScheduleAlarm.handle(context, memos, millis(at), now = millis(at) + 30_000, workEnd = { if (it.hour in 9..17) end else null })
        // Nothing at work: no schedule notice, and the card waits for 18:00.
        assertNull(shadowOf(nm).getNotification(MemoScheduleAlarm.SLOT))
        assertNull(shadowOf(nm).getNotification(MemoMissed.Card.AT_WORK.notice))
        assertEquals(end, MemoMissed.heldUntil(context))
        val release = shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms
            .single { shadowOf(it.operation).savedIntent.action == MemoMissed.ACTION_RELEASE }
        assertEquals(millis(end), release.triggerAtTime)
        // Before the end, nothing; at the end, one card with both.
        MemoMissed.release(context, end.minusMinutes(1))
        assertNull(shadowOf(nm).getNotification(MemoMissed.Card.AT_WORK.notice))
        MemoMissed.release(context, end)
        val card = shadowOf(nm).getNotification(MemoMissed.Card.AT_WORK.notice)!!
        assertEquals("근무 중에 온 일정 2개", shadowOf(card).contentTitle.toString())
        assertEquals("약 받기 · 택배 찾기", shadowOf(card).contentText.toString())
        assertEquals(listOf("모두 끝내기", "쉬는 날로"), card.actions.map { it.title.toString() })
        assertNull(MemoMissed.heldUntil(context))
    }

    @Test fun aRingingOneRingsOnTimeAndTheSwitchTurnsTheWaitOff() {
        val at = LocalDateTime.of(LocalDate.now(), LocalTime.of(15, 0))
        val told = Memo(id = 3, title = "물 마시기", date = at.toLocalDate(), reminder = MemoReminder(at, ring = false, notify = true))
        // Not at work (the switch off gives no end): told at once.
        MemoScheduleAlarm.handle(context, listOf(told), millis(at), now = millis(at) + 30_000, workEnd = { null })
        assertTrue(shadowOf(nm).getNotification(MemoScheduleAlarm.SLOT) != null)
        assertNull(MemoMissed.heldUntil(context))
        // 근무 중 메모 알림 모으기 off: no work end is read, so nothing waits.
        context.appContainer.settingsRepository.update { it.copy(memoHoldAtWork = false) }
        assertNull(runBlocking { MemoRestCard.workEnd(context) }(at))
    }

    @Test fun aDayOffsMorningCardOnceAWithItsThreeAnswers() = runBlocking<Unit> {
        val repo = context.appContainer.memoRepository
        val today = LocalDate.now()
        val a = repo.save(Memo(date = today.minusDays(2), title = "약 받기", reminder = MemoReminder(today.minusDays(2).atTime(15, 0), ring = false, notify = true)))
        val b = repo.save(Memo(date = today.minusDays(12), title = "서류 내기", reminder = MemoReminder(today.minusDays(12).atTime(10, 0), ring = false, notify = true)))
        repo.save(Memo(date = today.minusDays(1), title = "회식"))
        MemoRestCard.show(context, today)
        val card = shadowOf(nm).getNotification(MemoRestCard.NOTICE_ID)!!
        assertEquals("지난 할 일 2개", shadowOf(card).contentTitle.toString())
        assertTrue(shadowOf(card).contentText.toString().startsWith("쉬는 날이에요. 천천히 해도 돼요 · 서류 내기 · 약 받기"))
        assertEquals(listOf(OldWay.DONE.label, "오늘로", "지우기"), card.actions.map { it.title.toString() })
        // Once a day.
        nm.cancelAll()
        MemoRestCard.show(context, today)
        assertNull(shadowOf(nm).getNotification(MemoRestCard.NOTICE_ID))
        // 오늘로: both on today, a time gone today dropped.
        settleLeft(repo, MemoRestCard.named(context), OldWay.TODAY, today.atTime(12, 0))
        assertEquals(today, repo.get(a.id)!!.date)
        assertEquals(today.atTime(15, 0), repo.get(a.id)!!.reminder!!.at)
        assertNull(repo.get(b.id)!!.reminder)
        assertTrue(MemoRestCard.named(context).isEmpty())
        // Off: nothing on the next day off.
        context.appContainer.settingsRepository.update { it.copy(memoRestCard = false) }
        repo.save(Memo(date = today.minusDays(1), title = "장보기", reminder = MemoReminder(today.minusDays(1).atTime(9, 0), ring = false, notify = true)))
        MemoRestCard.show(context, today.plusDays(1))
        assertNull(shadowOf(nm).getNotification(MemoRestCard.NOTICE_ID))
        assertEquals(1, repo.memos.first().count { it.title == "장보기" })
    }
}
