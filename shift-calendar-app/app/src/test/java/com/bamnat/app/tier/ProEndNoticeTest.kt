package com.bamnat.app.tier

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.alarm.Notifications
import com.bamnat.app.appContainer
import com.bamnat.app.data.SettingsRepository
import com.bamnat.core.tier.Access
import com.bamnat.core.tier.FeatureLease
import com.bamnat.core.tier.ProReason
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 2.0.0 [RVO-14] (검토 I11bb-02·PRO-29·U11b-10, 총괄 11-12): the gate knows when a 프로 that ends by itself ends, and the evening before
 * one notice says what stops on this phone, only what it uses, once. The 끄기 미션's 7 days get theirs too.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ProEndNoticeTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val zone = ZoneId.of("Asia/Seoul")
    private val now = Instant.parse("2026-12-01T03:00:00Z")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val nm = context.getSystemService(NotificationManager::class.java)
    private fun kst(text: String) = LocalDateTime.parse(text).atZone(zone).toInstant()

    @After fun stop() {
        scope.cancel()
        context.appContainer.settingsRepository.update { it.copy(phoneCalendars = emptyList(), alarmMission = "none", wakeCheckMinutes = 0, previewTier = null) }
        context.getSharedPreferences("pro_end_notice", Context.MODE_PRIVATE).edit().clear().commit()
        com.bamnat.app.inbox.Inbox.get(context).run { clearAll(); unmute(ProEndNotice.PRO_KIND); unmute(ProEndNotice.MISSION_KIND) }
    }

    private fun gate(config: LaunchConfig, debug: Boolean = false): Gate {
        val remote = RemoteConfigStore(context).apply { save(config) }
        val flags = com.bamnat.app.flags.Flags(remote.config, emptyList(), scope, now = { now },
            facts = { com.bamnat.core.flags.PhoneFacts(now = now, serverNow = now, versionCode = 54, agreed = setOf("3.1")) })
        return FeatureGate(SettingsRepository(context), remote, ProStore(context), scope, flags, installedAt = { Instant.parse("2026-11-01T00:00:00Z") },
            debug = debug, clock = { now }, zone = { zone }).state.value
    }

    @Test fun theGateKnowsWhenTheThanksMonthEnds() {
        val launch = Instant.parse("2026-11-11T15:17:42.082Z")
        val thanks = gate(LaunchConfig(billingLive = true, launchedAt = launch, thanksMonths = 1))
        assertEquals(ProReason.THANKS, thanks.access.reason)
        assertEquals(kst("2026-12-12T00:17:42.082"), thanks.ends)
        assertNull("no 프로, nothing ends", gate(LaunchConfig(billingLive = true, launchedAt = launch, thanksMonths = 0)).ends)
        assertNull("결제가 열리지 않은 휴대폰(시험·로컬 서버)", gate(LaunchConfig(billingLive = false)).ends)
    }

    private val end = Instant.parse("2026-12-11T15:17:42.082Z") // 12-12 00:17 KST
    private val thanks = Gate(live = true, tier = Tier.PRO, access = Access(true, ProReason.THANKS, end), ends = end)

    @Test fun theEveningBeforeItSaysOnlyWhatThisPhoneUsesOnce() {
        val c = context.appContainer
        val evening = kst("2026-12-10T20:00")
        assertEquals(com.bamnat.core.tier.ProEnding.Use(), ProEndNotice.use(c, thanks, end))
        ProEndNotice.fire(context, mission = false, c = c, gate = thanks, now = evening, zone = zone)
        assertNull("nothing this phone uses stops: no notice", shadowOf(nm).getNotification(Notifications.PRO_STOP_ID))

        c.settingsRepository.update { it.copy(phoneCalendars = listOf("main")) }
        assertTrue(ProEndNotice.use(c, thanks, end).phoneCalendar)
        assertEquals("the server opened it to everyone: it does not stop", false,
            ProEndNotice.use(c, thanks.copy(free = setOf(Feature.DEVICE_CALENDAR)), end).phoneCalendar)
        ProEndNotice.fire(context, mission = false, c = c, gate = thanks, now = kst("2026-12-10T19:00"), zone = zone)
        assertNull("not before the evening", shadowOf(nm).getNotification(Notifications.PRO_STOP_ID))
        ProEndNotice.fire(context, mission = false, c = c, gate = thanks, now = evening, zone = zone)
        val posted = shadowOf(nm).getNotification(Notifications.PRO_STOP_ID)
        assertNotNull("the evening before: one notice", posted)
        assertEquals("프로를 내일(12월 11일)까지 써요", posted.extras.getString("android.title"))
        val text = posted.extras.getCharSequence("android.bigText").toString()
        assertTrue(text, text.contains("휴대폰 캘린더의 근무 일정을 지워요(앱 안 근무는 그대로예요)"))
        assertTrue(text, text.contains("다시 프로가 되면 다시 켜져요"))
        // 총괄 11-13: the same words are a row of the 알림함 under 안내, so they stay after the phone's notice goes.
        val row = com.bamnat.app.inbox.Inbox.get(context).rows.value.single { it.kind == ProEndNotice.PRO_KIND }
        assertEquals(com.bamnat.app.inbox.InboxSection.NOTICE, row.section)
        assertEquals("프로를 내일(12월 11일)까지 써요", row.title)
        assertEquals(com.bamnat.app.NoticeLinks.Target.PRO, row.target)
        nm.cancel(Notifications.PRO_STOP_ID)
        ProEndNotice.fire(context, mission = false, c = c, gate = thanks, now = evening.plusSeconds(600), zone = zone)
        assertNull("once per end", shadowOf(nm).getNotification(Notifications.PRO_STOP_ID))
    }

    @Test fun theMissionsSevenDaysAreToldWhenNoProKeepsThem() {
        val c = context.appContainer
        val lease = kst("2026-12-03T07:30")
        val free = Gate(live = true, tier = Tier.FREE, access = Access(false, ProReason.NONE),
            leases = FeatureLease.State(until = mapOf(FeatureLease.Bundle.ALARM to lease)))
        assertEquals(lease, ProEndNotice.missionEnd(free, now))
        assertNull("프로 keeps the mission past it", ProEndNotice.missionEnd(free.copy(tier = Tier.PRO, access = Access(true, ProReason.SUBSCRIPTION)), now))
        assertNull("the server opened it", ProEndNotice.missionEnd(free.copy(free = setOf(Feature.SURE_ALARM)), now))
        val evening = kst("2026-12-02T20:00")
        ProEndNotice.fire(context, mission = true, c = c, gate = free, now = evening, zone = zone)
        assertNull("no mission set: nothing to tell", shadowOf(nm).getNotification(Notifications.MISSION_END_ID))
        c.settingsRepository.update { it.copy(alarmMission = "math") }
        ProEndNotice.fire(context, mission = true, c = c, gate = free, now = evening, zone = zone)
        val posted = shadowOf(nm).getNotification(Notifications.MISSION_END_ID)
        assertEquals("끄기 미션이 내일(12월 3일) 오전 7:30에 끝나요", posted.extras.getString("android.title"))
    }

    /** PRO-29 '아침 한 줄': the alarm rang without the mission set for it; said once after it is off, again only after it was open. */
    @Test fun theMorningItRangWithoutTheMissionSaysSoOnce() = kotlinx.coroutines.runBlocking {
        val c = context.appContainer
        val ring = com.bamnat.app.alarm.RingRequest("주간", com.bamnat.core.time.Today.date(), java.time.LocalTime.of(7, 0), 60, null, com.bamnat.core.time.Today.millis())
        c.settingsRepository.update { it.copy(alarmMission = "math") }
        ProEndNotice.afterRing(context, ring, c)
        assertNull("the mission is open: it rang with it", shadowOf(nm).getNotification(Notifications.MISSION_END_ID))
        c.settingsRepository.update { it.copy(previewTier = Tier.FREE) }
        c.featureGate.state.first { it.locked(Feature.SURE_ALARM) }
        ProEndNotice.afterRing(context, ring, c)
        val posted = shadowOf(nm).getNotification(Notifications.MISSION_END_ID)
        assertNotNull(posted)
        assertEquals("오늘 알람은 끄기 미션 없이 울렸어요", posted.extras.getString("android.title"))
        assertEquals(listOf("7일 더 열기", "프로 보기"), posted.actions.map { it.title.toString() })
        nm.cancel(Notifications.MISSION_END_ID)
        ProEndNotice.afterRing(context, ring, c)
        assertNull("once", shadowOf(nm).getNotification(Notifications.MISSION_END_ID))
        ProEndNotice.reschedule(context, Gate(), now, zone)
        ProEndNotice.afterRing(context, ring, c)
        assertNotNull("open again in between: the next stop is told", shadowOf(nm).getNotification(Notifications.MISSION_END_ID))
        nm.cancel(Notifications.MISSION_END_ID)
        c.settingsRepository.update { it.copy(alarmMission = "none") }
        ProEndNotice.reschedule(context, Gate(), now, zone)
        ProEndNotice.afterRing(context, ring, c)
        assertNull("no mission set: nothing to say", shadowOf(nm).getNotification(Notifications.MISSION_END_ID))
    }

    @Test fun theEveningsAreSetFromTheGateAlone() {
        val am = context.getSystemService(AlarmManager::class.java)
        fun ours() = shadowOf(am).scheduledAlarms.filter { shadowOf(it.operation).savedIntent.component?.className == ProEndNoticeReceiver::class.java.name }
        ProEndNotice.reschedule(context, thanks, now, zone)
        assertEquals(listOf(kst("2026-12-10T20:00").toEpochMilli()), ours().map { it.triggerAtTime })
        ProEndNotice.reschedule(context, Gate(), now, zone)
        assertTrue("nothing ends: nothing set", ours().isEmpty())
    }

    /** 총괄 11-13 (알림함 규칙): '다시 보지 않기' on the 알림함 row turns the phone's notice off too, and it is not asked again. */
    @Test fun aKindSaidNoToInTheInboxDoesNotComeAsANoticeEither() {
        val c = context.appContainer
        c.settingsRepository.update { it.copy(phoneCalendars = listOf("main")) }
        com.bamnat.app.inbox.Inbox.get(context).mute(ProEndNotice.PRO_KIND, "프로가 끝나기 전 알림")
        ProEndNotice.fire(context, mission = false, c = c, gate = thanks, now = kst("2026-12-10T20:00"), zone = zone)
        assertNull(shadowOf(nm).getNotification(Notifications.PRO_STOP_ID))
        assertTrue(com.bamnat.app.inbox.Inbox.get(context).rows.value.none { it.kind == ProEndNotice.PRO_KIND })
    }
}
