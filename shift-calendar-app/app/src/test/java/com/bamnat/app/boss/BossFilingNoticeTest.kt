package com.bamnat.app.boss

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.alarm.Notifications
import com.bamnat.app.appContainer
import com.bamnat.app.data.AppSettings
import com.bamnat.app.ui.settings.NoticeBoard
import com.bamnat.app.ui.settings.SettingsIndex
import com.bamnat.app.ui.work.Filed
import com.bamnat.core.boss.Insurance
import com.bamnat.core.boss.StaffMember
import com.bamnat.core.boss.Workplace
import com.bamnat.core.time.Today
import com.bamnat.core.work.StaffShift
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * 2.0.0 [RVJ-96] 검토 U05c-05·I05ca-03: the 신고·지급 기한 알림 says a line 3 days before, once, not what was ticked 했어요; it is on the
 * board and found by 설정 찾기 only with a 급여 대장.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class BossFilingNoticeTest {
    private val context: Application = ApplicationProvider.getApplicationContext()
    private val seoul = ZoneId.of("Asia/Seoul")

    @After fun clockBack() = Today.reset()

    @Test fun nineInTheMorningOnce() {
        assertEquals(LocalDateTime.of(2026, 11, 7, 9, 0), BossFilingNotice.next(LocalDateTime.of(2026, 11, 7, 8, 59)))
        assertEquals(LocalDateTime.of(2026, 11, 8, 9, 0), BossFilingNotice.next(LocalDateTime.of(2026, 11, 7, 9, 0)))
        assertEquals(LocalDateTime.of(2026, 11, 8, 9, 0), BossFilingNotice.next(LocalDateTime.of(2026, 11, 7, 21, 30)))
    }

    @Test fun aLineThreeDaysAwayIsSaidOnceAndNotAfterItIsTicked() = runBlocking {
        Today.fix(ZonedDateTime.of(2026, 11, 7, 9, 0, 0, 0, seoul))
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        Notifications.createChannels(context)
        val store = context.appContainer.phaseThree
        val week = DayOfWeek.entries.take(5).associateWith { StaffShift(LocalTime.of(9, 0), LocalTime.of(18, 0)) }
        store.saveWorkplace(Workplace("p1", "가나다카페", fivePlus = true, payday = 5))
        store.saveStaff(StaffMember("a", "박마바", base = 10_320L, hiredOn = LocalDate.of(2025, 3, 3), weekly = week, insurance = Insurance.ALL), "p1", 0)
        val nm = context.getSystemService(NotificationManager::class.java)
        assertTrue("11/10 원천세 (3일 남음)", BossFilingNotice.tell(context) >= 1)
        val n = shadowOf(nm).allNotifications.single { it.channelId == Notifications.FILING_CHANNEL_ID }
        val title = shadowOf(n).contentTitle.toString()
        assertTrue(title, title.startsWith("가나다카페: ") && title.contains("11월 10일까지(3일 남음)"))
        assertTrue(shadowOf(n).contentText.toString(), shadowOf(n).contentText.toString().contains("11/10 원천세 신고·납부"))
        val told = Filed.told(context)
        assertTrue(told.toString(), told.any { it.startsWith("withhold:p1:") })
        // 한 번만: 다음 날 아침에도 같은 줄은 다시 알리지 않아요.
        nm.cancelAll()
        Today.fix(ZonedDateTime.of(2026, 11, 8, 9, 0, 0, 0, seoul))
        assertEquals(0, BossFilingNotice.tell(context))
        assertTrue(shadowOf(nm).allNotifications.isEmpty())
        // 했어요를 누른 줄은 처음부터 알리지 않아요.
        val prefs = context.getSharedPreferences("boss_filed", android.content.Context.MODE_PRIVATE)
        prefs.edit().remove("told").commit()
        told.forEach { Filed.set(context, it, true) }
        Today.fix(ZonedDateTime.of(2026, 11, 7, 9, 0, 0, 0, seoul))
        assertEquals(0, BossFilingNotice.tell(context))
    }

    @Test fun onTheBoardAndFoundOnlyWithALedger() {
        val s = AppSettings()
        assertTrue("기본으로 켜져 있어요", s.bossFilingNotice)
        assertFalse(NoticeBoard.shown(s, WorkStyle.BUSINESS).any { it.key == "bossFilingNotice" })
        assertTrue(NoticeBoard.shown(s, WorkStyle.BUSINESS, ledger = true).any { it.key == "bossFilingNotice" })
        assertTrue("교대 사장님도 대장이 있으면", NoticeBoard.shown(s, WorkStyle.SHIFT, ledger = true).any { it.key == "bossFilingNotice" })
        val without = SettingsIndex.entries(style = WorkStyle.BUSINESS)
        val with = SettingsIndex.entries(style = WorkStyle.BUSINESS, ledger = true)
        assertTrue(SettingsIndex.found("원천세 알림", without).sure.none { it.title == BossFilingNotice.ROW })
        assertTrue(SettingsIndex.found("원천세 알림", with).sure.any { it.title == BossFilingNotice.ROW })
        assertTrue(SettingsIndex.found("신고 기한", with).sure.any { it.title == BossFilingNotice.ROW })
    }
}
