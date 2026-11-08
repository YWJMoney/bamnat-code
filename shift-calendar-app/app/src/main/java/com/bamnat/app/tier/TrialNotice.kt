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
 * [MON-06] One notice 7 days before the trial turns paid that the app has a question about going on, unless the user already
 * answered. 1.38 (경쟁 앱 조사 2026-11-06: 다른 앱 1~3점 리뷰의 결제 불만 59건이 '체험 뒤 모르게 결제'): if still no answer, one more
 * 3 days before, and both carry '구독 관리·해지', straight to Play's page for that subscription (프로, 팀 요금제 or 직원 요금제).
 * Inexact: an hour late is fine.
 */
object TrialNotice {
    private const val REQUEST = 3301
    private const val FINAL = 3304
    const val EXTRA_FINAL = "final"
    val FINAL_BEFORE: java.time.Duration = java.time.Duration.ofDays(3)

    fun reschedule(context: Context, state: ProState, zone: ZoneId = Today.zone(), now: Instant = Today.instant()) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val sub = state.askSub
        val end = sub?.trialEnd(zone)
        val stage = TrialConsent.stage(sub?.play(), end, state.consentAt, now)
        am.cancel(pending(context, false)); am.cancel(pending(context, true))
        if (sub == null || end == null || stage == TrialConsent.Stage.NONE || stage == TrialConsent.Stage.DONE) return
        when {
            state.trialNoticeFor != sub.purchasedAt ->
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, maxOf(TrialConsent.askFrom(end), now).toEpochMilli(), pending(context, false))
            state.trialFinalFor != sub.purchasedAt && now.isBefore(end) ->
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, maxOf(end.minus(FINAL_BEFORE), now).toEpochMilli(), pending(context, true))
        }
    }

    private fun pending(context: Context, final: Boolean): PendingIntent = PendingIntent.getBroadcast(
        context, if (final) FINAL else REQUEST, Intent(context, TrialNoticeReceiver::class.java).putExtra(EXTRA_FINAL, final),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** What the trial is of, in the notice: 프로, or the 팀·직원 요금제. */
    fun nameOf(product: String): String =
        com.bamnat.core.tier.WorkPlanKind.entries.firstOrNull { it.product == product }?.title ?: "밤낮 프로"

    fun post(context: Context, end: Instant, product: String = com.bamnat.app.billing.ProductIds.PRO, final: Boolean = false, zone: ZoneId = Today.zone()) {
        val day = end.atZone(zone).toLocalDate()
        val name = nameOf(product)
        val manage = PendingIntent.getActivity(context, if (final) 3306 else 3305,
            Intent(Intent.ACTION_VIEW, android.net.Uri.parse(com.bamnat.app.billing.ProductIds.manageUrl(context.packageName, product))),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, Notifications.PRO_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentTitle(if (final) "$name 무료 체험이 3일 뒤(${day.monthValue}월 ${day.dayOfMonth}일) 끝나요" else "$name 무료 체험이 ${day.monthValue}월 ${day.dayOfMonth}일에 끝나요")
            .setContentText(if (final) "그날부터 결제돼요. 그만두려면 ‘구독 관리·해지’에서 바로 해지할 수 있어요." else "그날부터 결제돼요. 앱을 열어 계속 쓸지 알려 주세요. 해지는 ‘구독 관리·해지’에서 바로 돼요.")
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                (if (final) "그날부터 결제돼요. 그만두려면 ‘구독 관리·해지’에서 바로 해지할 수 있어요." else "그날부터 결제돼요. 앱을 열어 계속 쓸지 알려 주세요. 해지는 ‘구독 관리·해지’에서 바로 돼요.") +
                    " 만든 기록은 해지해도 그대로 남아요."))
            .setContentIntent(Notifications.open(context, NoticeLinks.Target.PRO, 3302))
            .addAction(0, "구독 관리·해지", manage)
            .setAutoCancel(true)
            .build()
        Notifications.post(context, Notifications.PRO_ID, notification)
    }
}

class TrialNoticeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val c = context.appContainer
        val state = c.proStore.current
        val sub = state.askSub ?: return
        val end = sub.trialEnd(Today.zone())
        val stage = TrialConsent.stage(sub.play(), end, state.consentAt, Today.instant())
        if (stage != TrialConsent.Stage.ASK && stage != TrialConsent.Stage.LAST_DAY) return
        val final = intent.getBooleanExtra(TrialNotice.EXTRA_FINAL, false)
        TrialNotice.post(context, end, sub.product, final)
        if (final) c.proStore.trialFinalNoticed(sub.purchasedAt) else c.proStore.trialNoticed(sub.purchasedAt)
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
