package com.bamnat.app.tier

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.bamnat.app.NoticeLinks
import com.bamnat.app.R
import com.bamnat.app.alarm.Notifications
import com.bamnat.app.appContainer
import com.bamnat.core.tier.TrialConsent
import java.time.Instant
import java.time.ZoneId
import com.bamnat.core.time.Today

/**
 * [MON-06] One notice on day 23 of the month's trial (7 days before it turns paid) that the app has a
 * question about going on, unless the user already answered. Inexact: an hour late is fine.
 */
object TrialNotice {
    private const val REQUEST = 3301

    fun reschedule(context: Context, state: ProState, zone: ZoneId = Today.zone(), now: Instant = Today.instant()) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val sub = state.sub
        val end = sub?.trialEnd(zone)
        val stage = TrialConsent.stage(sub?.play(), end, state.consentAt, now)
        if (sub == null || end == null || stage == TrialConsent.Stage.NONE || stage == TrialConsent.Stage.DONE ||
            state.trialNoticeFor == sub.purchasedAt) {
            am.cancel(pending(context))
            return
        }
        val at = maxOf(TrialConsent.askFrom(end), now).toEpochMilli()
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(context))
    }

    private fun pending(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, REQUEST, Intent(context, TrialNoticeReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun post(context: Context, end: Instant, zone: ZoneId = Today.zone()) {
        val day = end.atZone(zone).toLocalDate()
        val notification = NotificationCompat.Builder(context, Notifications.PRO_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentTitle("무료 체험이 ${day.monthValue}월 ${day.dayOfMonth}일에 끝나요")
            .setContentText("그날부터 결제돼요. 앱을 열어 계속 쓸지 알려 주세요. 해지는 Play 스토어에서 해요.")
            .setContentIntent(Notifications.open(context, NoticeLinks.Target.PRO, 3302))
            .setAutoCancel(true)
            .build()
        Notifications.post(context, Notifications.PRO_ID, notification)
    }
}

class TrialNoticeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val c = context.appContainer
        val state = c.proStore.current
        val sub = state.sub ?: return
        val end = sub.trialEnd(Today.zone())
        val stage = TrialConsent.stage(sub.play(), end, state.consentAt, Today.instant())
        if (stage != TrialConsent.Stage.ASK && stage != TrialConsent.Stage.LAST_DAY) return
        TrialNotice.post(context, end)
        c.proStore.trialNoticed(sub.purchasedAt)
    }
}

/**
 * 1.36 840·841 (수익 3판 3절): one notice 7 days before money is taken or 프로 ends without it. A yearly subscription about to renew
 * (not a trial, which has [TrialNotice]) says what the year gave ("올해 밤낮이 찾아 준 돈 · 광고 0편"), never a feature list; a
 * 1년 이용권 says it ends by itself and that buying again carries on. Once per end.
 */
object EndNotice {
    private const val REQUEST = 3303
    private val BEFORE: java.time.Duration = java.time.Duration.ofDays(com.bamnat.core.tier.YearPass.NOTICE_DAYS)

    /** The end to tell of, and whether it is a 1년 이용권's; null when there is none. */
    fun end(state: ProState, now: Instant): Pair<Instant, Boolean>? {
        val sub = state.sub
        val subscribed = sub?.play()?.entitled(now) == true
        if (subscribed && sub != null) {
            return sub.expiry?.takeIf { sub.basePlan == com.bamnat.app.billing.ProductIds.YEARLY && sub.autoRenew && !sub.trial }?.let { it to false }
        }
        return state.yearPassUntil?.takeIf { now < it }?.let { it to true }
    }

    fun reschedule(context: Context, state: ProState, now: Instant = Today.instant()) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val end = end(state, now)?.first
        if (end == null || state.endNoticeFor == end) {
            am.cancel(pending(context))
            return
        }
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, maxOf(end.minus(BEFORE), now).toEpochMilli(), pending(context))
    }

    private fun pending(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, REQUEST, Intent(context, EndNoticeReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** The title and text, for the notice and its test. */
    fun words(end: Instant, pass: Boolean, found: Long, zone: ZoneId = Today.zone()): Pair<String, String> {
        val day = end.atZone(zone).toLocalDate().let { "${it.monthValue}월 ${it.dayOfMonth}일" }
        return if (pass) "1년 이용권이 ${day}에 끝나요" to "자동 결제가 없어서 그날 그냥 끝나요. 이어 쓰려면 프로 화면에서 다시 사면 이어져요."
        else "프로 연 요금제가 ${day}에 갱신돼요" to
            ((if (found > 0) "올해 밤낮이 찾아 준 돈 %,d원 · 광고 0편. ".format(found) else "올해 광고 0편으로 썼어요. ") + "해지는 Play 스토어에서 언제든 해요.")
    }

    fun post(context: Context, end: Instant, pass: Boolean, found: Long) {
        val (title, text) = words(end, pass, found)
        val notification = NotificationCompat.Builder(context, Notifications.PRO_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(Notifications.open(context, NoticeLinks.Target.PRO, 3304))
            .setAutoCancel(true)
            .build()
        Notifications.post(context, Notifications.PRO_END_ID, notification)
    }
}

class EndNoticeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val c = context.appContainer
        val state = c.proStore.current
        val now = Today.instant()
        val (end, pass) = EndNotice.end(state, now) ?: return
        if (state.endNoticeFor == end || now < end.minus(java.time.Duration.ofDays(com.bamnat.core.tier.YearPass.NOTICE_DAYS))) return
        EndNotice.post(context, end, pass, c.proMoments.state.value.foundIn(end.atZone(Today.zone()).year))
        c.proStore.endNoticed(end)
    }
}
