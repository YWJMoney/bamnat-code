package com.bamnat.app.money

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
import com.bamnat.app.data.PersonalCalendar
import com.bamnat.app.ui.Fmt
import com.bamnat.core.money.DayClose
import com.bamnat.core.money.MoneyKind
import com.bamnat.core.money.MoneySource
import com.bamnat.core.time.Today
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/**
 * 1.37 [LEDH-09] 퇴근 뒤 가계부 정리 알림: 30 minutes after the main calendar's work ends (a night's the next morning; 21:00
 * on a day off), a quiet notice of what was spent today and what waits in 정리함. Off by default; on in 설정 › 알림과 잠 ›
 * 퇴근 뒤 가계부 정리 알림. Not posted while the 가계부 is not in use (nothing written in 30 days). Inexact, like the others.
 */
object DayCloseNotice {
    private const val REQUEST = 3740
    private const val REQUEST_OPEN = 3741
    const val ID = 7640
    private const val ACTION_FIRE = "com.bamnat.app.DAY_CLOSE"

    private fun fits(style: WorkStyle?) = style != WorkStyle.INVEST && style != WorkStyle.BUSINESS

    suspend fun reschedule(context: Context, now: LocalDateTime = Today.dateTime()) {
        val c = context.appContainer
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val s = c.settingsRepository.current
        val at = if (!s.dayCloseNotice || !fits(s.workStyle)) null else runCatching {
            val resolver = c.scheduleRepository.snapshot.first().resolver
            DayClose.next(now) { d ->
                val day = resolver.resolve(d)
                val from = day.shiftFrom?.takeIf { day.isWorkDay && day.leave?.isFullDay != true }
                val to = day.shiftTo
                if (from != null && to != null) DayClose.Work(from, to) else null
            }
        }.getOrNull()
        if (at == null) {
            am.cancel(pending(context))
            return
        }
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.atZone(Today.zone()).toInstant().toEpochMilli(), pending(context))
    }

    private fun pending(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, REQUEST, Intent(context, DayCloseReceiver::class.java).setAction(ACTION_FIRE),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    internal suspend fun post(context: Context) {
        val c = context.appContainer
        val s = c.settingsRepository.current
        if (!s.dayCloseNotice || !fits(s.workStyle)) return
        val book = c.calendars.money(PersonalCalendar.MAIN).book.first()
        val today = Today.date()
        if (book.entries.none { !it.date.isBefore(today.minusDays(30)) }) return
        val toSort = book.pending.size + book.entries.count { !it.reviewed }
        val spent = book.entries.filter { it.date == today && it.kind == MoneyKind.EXPENSE && it.source != MoneySource.FIXED }.sumOf { it.amount }
        val (title, text) = DayClose.words(toSort, spent, Fmt::won)
        val n = NotificationCompat.Builder(context, Notifications.MONEY_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setSilent(true)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$text\n설정 › 알림과 잠 › 퇴근 뒤 가계부 정리 알림에서 끌 수 있어요."))
            .setContentIntent(Notifications.open(context, NoticeLinks.Target.MONEY, REQUEST_OPEN))
            .setAutoCancel(true)
            .setTimeoutAfter(6 * 60 * 60 * 1000L)
            .build()
        Notifications.post(context, ID, n)
    }

    internal const val FIRE = ACTION_FIRE
}

class DayCloseReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val c = context.appContainer
        val pending = goAsync()
        c.appScope.launch {
            try {
                if (intent.action == DayCloseNotice.FIRE) DayCloseNotice.post(context)
            } finally {
                DayCloseNotice.reschedule(context, Today.dateTime().plusMinutes(1))
                pending.finish()
            }
        }
    }
}
