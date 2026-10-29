package com.bamnat.app.calendar

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.bamnat.app.AppContainer
import com.bamnat.app.NoticeLinks
import com.bamnat.app.R
import com.bamnat.app.alarm.AlarmScheduler
import com.bamnat.app.alarm.Notifications
import com.bamnat.app.appContainer
import com.bamnat.app.invest.invest
import com.bamnat.app.ui.guide.FeatureSwitches
import com.bamnat.core.invest.InvestTaxes
import com.bamnat.core.invest.Portfolio
import com.bamnat.core.notify.DateNotices
import com.bamnat.core.time.Today
import com.bamnat.core.work.WorkStyle
import java.time.LocalDateTime
import kotlinx.coroutines.launch

/**
 * 1.32 [WTYP-11][WTYP-29] Notices tied to a date ([DateNotices]): 희망 오프 신청 마감 (two days before and on the day) of every
 * shift calendar with a deadline, and 코인 12월 31일 시가 기록 of every investor calendar that holds coins. One loose system
 * alarm for the next of them (a few minutes either way is fine); each is off with its feature in 기능 켜고 끄기.
 */
object DateNoticeAlarm {
    const val WISH_OFF = "shift.wishOff"
    const val YEAR_END = "invest.yearEnd"
    private const val REQUEST = 3720
    private const val REQUEST_OPEN = 3721
    private const val NOTICE_ID = 7620
    private const val EXTRA_AT = "at"
    /** A notice delivered much later than its time (the phone was off) is still worth saying on the same day. */
    private const val STALE_AFTER_MILLIS = 10 * 60 * 60 * 1000L

    suspend fun gather(c: AppContainer, now: LocalDateTime = Today.dateTime()): List<DateNotices.Notice> {
        val main = c.settingsRepository.current.workStyle
        val cals = c.calendars.calendars.value.filterNot { it.isExample }
        val many = cals.size > 1
        return cals.flatMap { cal ->
            when (c.calendars.styleOf(cal.id, main)) {
                null, WorkStyle.SHIFT -> if (FeatureSwitches.hidden(c, WISH_OFF, cal.id)) emptyList() else {
                    val w = c.wishOff.of(cal.id)
                    DateNotices.wishOff(cal.id, if (many) cal.name else "", w.deadlineDay, { m -> w.picked(m).size }, now)
                }
                WorkStyle.INVEST -> if (FeatureSwitches.hidden(c, YEAR_END, cal.id)) emptyList() else
                    listOfNotNull(DateNotices.coinYearEnd(cal.id, InvestTaxes.coinsHeld(Portfolio.build(c.invest(cal.id).now())), now))
                else -> emptyList()
            }
        }
    }

    fun reschedule(context: Context, all: List<DateNotices.Notice>, after: LocalDateTime = Today.dateTime()) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val next = DateNotices.next(all, after).firstOrNull()
        if (next == null) {
            am.cancel(pending(context, 0L))
            return
        }
        val at = next.at.atZone(Today.zone()).toInstant().toEpochMilli()
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(context, at))
    }

    private fun pending(context: Context, at: Long): PendingIntent = PendingIntent.getBroadcast(
        context, REQUEST, Intent(context, DateNoticeReceiver::class.java).putExtra(EXTRA_AT, at),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Posts what is due at [atMillis], read again now, so a deadline changed or a coin sold since is not announced. */
    internal suspend fun fire(context: Context, c: AppContainer, atMillis: Long, now: Long = Today.millis()) {
        if (atMillis == 0L || now - atMillis > STALE_AFTER_MILLIS) return
        val at = AlarmScheduler.millisToLocal(atMillis)
        gather(c, at.minusSeconds(1)).filter { it.at == at }.forEach { post(context, it) }
    }

    private fun post(context: Context, n: DateNotices.Notice) {
        val invest = n.kind == DateNotices.Kind.COIN_YEAR_END
        val notification = NotificationCompat.Builder(context, if (invest) Notifications.INVEST_CHANNEL_ID else Notifications.MEMO_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentTitle(n.title)
            .setContentText(n.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(n.text))
            .setContentIntent(Notifications.open(context, if (invest) NoticeLinks.Target.INVEST else NoticeLinks.Target.TODAY, REQUEST_OPEN, n.calendarId))
            .setAutoCancel(true)
            .build()
        Notifications.post(context, NOTICE_ID, notification, n.key)
    }

    /** Kept planned while the app runs: calendars, their 희망 오프, the investor books and the feature switches. */
    suspend fun follow(c: AppContainer) {
        kotlinx.coroutines.flow.combine(
            c.calendars.calendars, c.wishOff.all, com.bamnat.app.data.GuideStore.get(c.appContext).state,
            com.bamnat.app.invest.InvestNotices.plans(c),
        ) { _, _, _, _ -> Unit }.collect { reschedule(c.appContext, gather(c)) }
    }
}

class DateNoticeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val c = context.appContainer
        val atMillis = intent.getLongExtra("at", 0L)
        val pending = goAsync()
        c.appScope.launch {
            try {
                DateNoticeAlarm.fire(context, c, atMillis)
            } finally {
                val after = if (atMillis != 0L) AlarmScheduler.millisToLocal(atMillis) else Today.dateTime()
                DateNoticeAlarm.reschedule(context, DateNoticeAlarm.gather(c), maxOf(after, Today.dateTime().minusSeconds(1)))
                pending.finish()
            }
        }
    }
}
