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
import com.bamnat.core.money.ShiftPreview
import com.bamnat.core.time.Today
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 1.38 [LEDH-24] 근무 전 한 줄 예고: the evening before a work day whose kind of work spends noticeably more on one category, a quiet
 * line ("내일 야간이에요 · 지난 야간 날엔 배달에 평균 1만 8천 원"). Once a week at most; off by default; 설정 › 알림과 잠 › 근무 전 한 줄
 * 예고, with its hour. Worked out on the phone from the main calendar's book and roster; nothing leaves it. Inexact, like the others.
 */
object ShiftPreviewNotice {
    private const val REQUEST = 3750
    private const val REQUEST_OPEN = 3751
    const val ID = 7650
    private const val ACTION_FIRE = "com.bamnat.app.SHIFT_PREVIEW"
    private const val PREFS = "bamnat_shift_preview"
    private const val K_LAST = "last_told"

    private fun fits(style: WorkStyle?) = style == WorkStyle.SHIFT || style == WorkStyle.DAY || style == WorkStyle.HOURLY

    private fun lastTold(context: Context): LocalDate? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(K_LAST, Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE }?.let(LocalDate::ofEpochDay)

    private suspend fun plan(context: Context, now: LocalDateTime): Pair<LocalDateTime, ShiftPreview.Tip>? {
        val c = context.appContainer
        val s = c.settingsRepository.current
        if (!s.shiftPreviewNotice || !fits(s.workStyle)) return null
        return runCatching {
            val resolver = c.scheduleRepository.snapshot.first().resolver
            val book = c.calendars.money(PersonalCalendar.MAIN).book.first()
            ShiftPreview.next(now, s.shiftPreviewHour, lastTold(context), book.entries) { d ->
                val day = resolver.resolve(d)
                day.shiftType?.takeIf { day.isWorkDay && !it.isOff && day.leave?.isFullDay != true }?.name
            }
        }.getOrNull()
    }

    suspend fun reschedule(context: Context, now: LocalDateTime = Today.dateTime()) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val at = plan(context, now)?.first
        if (at == null) {
            am.cancel(pending(context))
            return
        }
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.atZone(Today.zone()).toInstant().toEpochMilli(), pending(context))
    }

    private fun pending(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, REQUEST, Intent(context, ShiftPreviewReceiver::class.java).setAction(ACTION_FIRE),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    internal suspend fun post(context: Context) {
        // The plan as of a few minutes before the hour, so the alarm being a little late still finds tomorrow's tip.
        val now = Today.dateTime()
        val (at, tip) = plan(context, now.minusMinutes(30)) ?: return
        if (at.isAfter(now.plusMinutes(5)) || tip.day != now.toLocalDate().plusDays(1)) return
        val (title, text) = ShiftPreview.words(tip, Fmt::won)
        val n = NotificationCompat.Builder(context, Notifications.MONEY_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setSilent(true)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$text\n설정 › 알림과 잠 › 근무 전 한 줄 예고에서 끄거나 시각을 바꿀 수 있어요."))
            .setContentIntent(Notifications.open(context, NoticeLinks.Target.MONEY, REQUEST_OPEN))
            .setAutoCancel(true)
            .setTimeoutAfter(12 * 60 * 60 * 1000L)
            .build()
        Notifications.post(context, ID, n)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(K_LAST, now.toLocalDate().toEpochDay()).apply()
    }

    internal const val FIRE = ACTION_FIRE
}

class ShiftPreviewReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val c = context.appContainer
        val pending = goAsync()
        c.appScope.launch {
            try {
                if (intent.action == ShiftPreviewNotice.FIRE) ShiftPreviewNotice.post(context)
            } finally {
                ShiftPreviewNotice.reschedule(context, Today.dateTime().plusMinutes(1))
                pending.finish()
            }
        }
    }
}
