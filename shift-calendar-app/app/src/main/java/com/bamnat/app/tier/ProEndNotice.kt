package com.bamnat.app.tier

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.bamnat.app.AppContainer
import com.bamnat.app.NoticeLinks
import com.bamnat.app.R
import com.bamnat.app.alarm.Notifications
import com.bamnat.app.appContainer
import com.bamnat.core.alarm.AlarmMission
import com.bamnat.core.tier.FeatureLease
import com.bamnat.core.tier.ProEnding
import com.bamnat.core.time.Today
import java.time.Instant
import java.time.ZoneId

/**
 * 2.0.0 [RVO-14] (검토 I11bb-02 '끝나기 전에 알려 주는 프로', PRO-29·U11b-10, 총괄 11-12 #481): the evening before a 프로 that ends by itself
 * ([Gate.ends]: 감사 기간, 보상 이용권, 1년 이용권, 끈 구독, 3.1 보류), one notice of what stops on this phone ([ProEnding.Stop]: 휴대폰 캘린더
 * 일정을 지움, 자동 백업, 끄기 미션, 구독 주소), only when something does. And the evening before the 끄기 미션's 7 days end while no 프로
 * keeps it, and the morning a shift alarm rang without the mission the user set ('아침 한 줄', [afterRing]). Once per end; inexact (an
 * hour late is fine). Nothing on a phone where 결제 is not open (tests, debug, the local server).
 *
 * 2.0.0 총괄 11-13: each is also a row of the 알림함 ([inbox]); turned off there with '다시 보지 않기', neither comes again.
 */
object ProEndNotice {
    private const val REQUEST = 3340
    private const val MISSION_REQUEST = 3341
    private const val PREFS = "pro_end_notice"
    private const val K_TOLD = "told"
    private const val K_MISSION = "mission"
    private const val K_MORNING = "morning"

    /** What this phone uses now, of what stops when 프로 ends at [end] (the server's free rows and a longer 7일권 keep theirs). */
    fun use(c: AppContainer, gate: Gate, end: Instant): ProEnding.Use {
        val s = c.settingsRepository.current
        fun stops(f: Feature) = f !in gate.free
        val lease = gate.leases.until[FeatureLease.Bundle.ALARM]
        return ProEnding.Use(
            phoneCalendar = stops(Feature.DEVICE_CALENDAR) && s.phoneCalendars.isNotEmpty(),
            autoBackup = stops(Feature.AUTO_BACKUP) && c.autoBackup.state.value.on,
            mission = stops(Feature.SURE_ALARM) && missionSet(c) && (lease == null || lease <= end),
            feed = stops(Feature.ICS_FEED) && feedOn(c),
        )
    }

    /** A 끄기 미션 (one for all, one per shift) or the 일어났나요 check is set. */
    fun missionSet(c: AppContainer): Boolean {
        val s = c.settingsRepository.current
        return AlarmMission.of(s.alarmMission) != AlarmMission.NONE || s.shiftMissions.values.any { AlarmMission.of(it) != AlarmMission.NONE } ||
            s.wakeCheckMinutes > 0
    }

    private fun feedOn(c: AppContainer): Boolean = c.calendars.calendars.value.filterNot { it.isExample }.any { cal ->
        com.bamnat.app.share.ShareLinkKey.all(cal.id).any { k -> c.shareLinksOf(k).let { it.feed && it.link.value != null } }
    }

    /**
     * The end of the 끄기 미션's 7일권 to tell of, or null: none, or 프로 keeps the mission past it, or the server opened it to everyone.
     * Whether a mission is set is asked when the notice is due ([fire]), so a mission set later is told too.
     */
    fun missionEnd(gate: Gate, now: Instant): Instant? {
        if (Feature.SURE_ALARM in gate.free) return null
        val lease = gate.leases.until[FeatureLease.Bundle.ALARM]?.takeIf { it > now } ?: return null
        if (gate.access.pro && (gate.ends == null || gate.ends >= lease)) return null
        return lease
    }

    /**
     * Sets the two evening-before alarms from [Gate] alone (cheap at the start, [com.bamnat.app.AppContainer] 'trialnotice'): what this
     * phone uses is looked at when one is due ([fire]), so turning a feature on after this still gets it told.
     */
    fun reschedule(context: Context, gate: Gate, now: Instant = Today.instant(), zone: ZoneId = Today.zone()) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        // The mission open again (프로, an ad's 7 days, the server): a later stop gets its morning line again.
        if (gate.allows(Feature.SURE_ALARM)) prefs.edit().remove(K_MORNING).apply()
        am.cancel(pending(context, false)); am.cancel(pending(context, true))
        gate.ends?.takeIf { prefs.getLong(K_TOLD, 0) != it.toEpochMilli() }?.let { end ->
            ProEnding.noticeAt(end, now, zone)?.let { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, it.toEpochMilli(), pending(context, false)) }
        }
        missionEnd(gate, now)?.takeIf { prefs.getLong(K_MISSION, 0) != it.toEpochMilli() }?.let { end ->
            ProEnding.noticeAt(end, now, zone)?.let { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, it.toEpochMilli(), pending(context, true)) }
        }
    }

    private fun pending(context: Context, mission: Boolean): PendingIntent = PendingIntent.getBroadcast(
        context, if (mission) MISSION_REQUEST else REQUEST, Intent(context, ProEndNoticeReceiver::class.java).putExtra(EXTRA_MISSION, mission),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    const val EXTRA_MISSION = "mission"

    /** Posts what is due now, and remembers it was told. */
    fun fire(context: Context, mission: Boolean, c: AppContainer = context.appContainer, gate: Gate = c.featureGate.state.value,
             now: Instant = Today.instant(), zone: ZoneId = Today.zone()) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (mission) {
            val end = missionEnd(gate, now) ?: return
            if (!missionSet(c) || prefs.getLong(K_MISSION, 0) == end.toEpochMilli() || now < (ProEnding.noticeAt(end, now, zone) ?: return)) return
            val (title, text) = ProEnding.missionWords(end, now, zone)
            if (inbox(context, "missionEnd:${end.toEpochMilli()}", MISSION_KIND, title, text, NoticeLinks.Target.SURE_ALARM, now)) {
                post(context, Notifications.MISSION_END_ID, title, text, NoticeLinks.Target.SURE_ALARM, backup = false, more = true)
            }
            prefs.edit().putLong(K_MISSION, end.toEpochMilli()).apply()
        } else {
            val end = gate.ends ?: return
            val stops = ProEnding.stops(use(c, gate, end))
            if (stops.isEmpty() || prefs.getLong(K_TOLD, 0) == end.toEpochMilli() || now < (ProEnding.noticeAt(end, now, zone) ?: return)) return
            val (title, text) = ProEnding.words(stops, end, now, zone)
            if (inbox(context, "proEnd:${end.toEpochMilli()}", PRO_KIND, title, text, NoticeLinks.Target.PRO, now)) {
                post(context, Notifications.PRO_STOP_ID, title, text, NoticeLinks.Target.PRO, backup = ProEnding.Stop.AUTO_BACKUP in stops)
            }
            prefs.edit().putLong(K_TOLD, end.toEpochMilli()).apply()
        }
    }

    /**
     * PRO-29 '아침 한 줄': [ring], a shift alarm just turned off, rang without the 끄기 미션 (or 일어났나요) the user set for it because
     * nothing keeps the mission open now. Once, until it is open again ([reschedule] forgets it then).
     */
    fun afterRing(context: Context, ring: com.bamnat.app.alarm.RingRequest, c: AppContainer = context.appContainer) {
        if (c.featureGate.allows(Feature.SURE_ALARM)) return
        val set = com.bamnat.app.alarm.BootAlarms.RingSettings.of(c.settingsRepository.current, sure = true)
        if (set.missionFor(ring) == AlarmMission.NONE && set.wakeCheckMinutes == 0) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(K_MORNING, false)) return
        val (title, text) = ProEnding.MORNING
        if (inbox(context, "missionMorning", MISSION_KIND, title, text, NoticeLinks.Target.SURE_ALARM, Today.instant())) {
            post(context, Notifications.MISSION_END_ID, title, text, NoticeLinks.Target.SURE_ALARM, backup = false, more = true)
        }
        prefs.edit().putBoolean(K_MORNING, true).apply()
    }

    /** The 알림함 kinds '다시 보지 않기' turns off: the 프로 that ends, and the 끄기 미션's 7 days. */
    const val PRO_KIND = "pro.ending"
    const val MISSION_KIND = "pro.missionEnd"

    /**
     * 2.0.0 [RVO-14] 총괄 11-13 (P #492 알림함): the notice is a row of the 알림함 'Inbox' too, under 안내, so it stays where the
     * other notices are (전체 지움, X, 다시 보지 않기) once the phone's notice is gone. False when the user said '다시 보지 않기' to this
     * kind: then the phone's notice does not come either, and it is not asked again.
     */
    private fun inbox(context: Context, key: String, kind: String, title: String, text: String, open: NoticeLinks.Target, now: Instant): Boolean =
        com.bamnat.app.inbox.Inbox.get(context).let { box ->
            box.post(com.bamnat.app.inbox.InboxRow(key = key, kind = kind, section = com.bamnat.app.inbox.InboxSection.NOTICE, title = title,
                line = text, target = open, at = now.toEpochMilli()))
        }

    private fun post(context: Context, id: Int, title: String, text: String, open: NoticeLinks.Target, backup: Boolean, more: Boolean = false) {
        val notification = NotificationCompat.Builder(context, Notifications.PRO_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(Notifications.open(context, open, if (id == Notifications.PRO_STOP_ID) 3342 else 3343))
            // I11bb-02 '지금 백업 한 번 저장하기': the last backup before it stops.
            .apply { if (backup) addAction(0, "지금 백업", Notifications.open(context, NoticeLinks.Target.BACKUP, 3344)) }
            // PRO-29 '광고 한 편으로 7일 더 / 프로 보기': the mission's 7 days, one tap to each.
            .apply {
                if (more) {
                    addAction(0, "7일 더 열기", Notifications.open(context, NoticeLinks.Target.SURE_ALARM, 3345))
                    addAction(0, "프로 보기", Notifications.open(context, NoticeLinks.Target.PRO, 3346))
                }
            }
            .setAutoCancel(true)
            .build()
        Notifications.post(context, id, notification)
    }
}

class ProEndNoticeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ProEndNotice.fire(context, intent.getBooleanExtra(ProEndNotice.EXTRA_MISSION, false))
    }
}
