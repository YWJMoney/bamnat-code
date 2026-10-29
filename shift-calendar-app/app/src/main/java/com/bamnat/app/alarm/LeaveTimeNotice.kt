package com.bamnat.app.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.bamnat.app.NoticeLinks
import com.bamnat.app.R
import com.bamnat.app.appContainer
import com.bamnat.app.ui.calendar.LeaveNowWriter
import com.bamnat.app.widget.LeaveNowAction
import com.bamnat.app.widget.WidgetRefresher
import com.bamnat.core.model.ExtraKind
import com.bamnat.core.schedule.ScheduleResolver
import com.bamnat.core.time.Today
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/**
 * 1.32 [WTYP-16] (근무유형 조사 4장 사무직) 퇴근 시각 알림: at the end of a day calendar's working day, a quiet notice with
 * 지금 퇴근, the same as the day sheet's chip and the widget's button (잔업 from the end to now, or an earlier end). Off by
 * default, on in 설정 › 알림과 잠 › 퇴근 시각 알림. The main calendar only, as the widget.
 */
object LeaveTimeNotice {
    private const val REQUEST = 3730
    private const val REQUEST_LEAVE = 3731
    private const val REQUEST_OPEN = 3732
    const val ID = 7630
    private const val ACTION_FIRE = "com.bamnat.app.LEAVE_TIME"
    private const val ACTION_LEAVE = "com.bamnat.app.LEAVE_NOW"
    /** Pressed later than this after the end, the button is not offered any more (the next morning). */
    private const val STALE_AFTER_MILLIS = 8 * 60 * 60 * 1000L

    /** The next end of work after [now] in the coming week, or null. */
    fun next(resolver: ScheduleResolver, now: LocalDateTime): LocalDateTime? =
        (0L..7L).asSequence().map { resolver.resolve(now.toLocalDate().plusDays(it)) }
            .filter { LeaveNowAction.offered(WorkStyle.DAY, it) }.mapNotNull { it.shiftTo }.firstOrNull { it.isAfter(now) }

    suspend fun reschedule(context: Context, now: LocalDateTime = Today.dateTime()) {
        val c = context.appContainer
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val s = c.settingsRepository.current
        val at = if (s.leaveTimeNotice && s.workStyle == WorkStyle.DAY) next(c.scheduleRepository.snapshot.first().resolver, now) else null
        if (at == null) {
            am.cancel(pending(context, ACTION_FIRE, REQUEST))
            return
        }
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.atZone(Today.zone()).toInstant().toEpochMilli(), pending(context, ACTION_FIRE, REQUEST))
    }

    private fun pending(context: Context, action: String, request: Int): PendingIntent = PendingIntent.getBroadcast(
        context, request, Intent(context, LeaveTimeReceiver::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    internal suspend fun post(context: Context) {
        val c = context.appContainer
        if (!c.settingsRepository.current.leaveTimeNotice) return
        val day = c.scheduleRepository.snapshot.first().resolver.resolve(Today.date())
        if (!LeaveNowAction.offered(c.settingsRepository.current.workStyle, day)) return
        val n = NotificationCompat.Builder(context, Notifications.DAY_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setSilent(true)
            .setContentTitle("퇴근 시간이에요 (${day.end?.let { "%02d:%02d".format(it.hour, it.minute) } ?: ""})")
            .setContentText("더 일하고 나갈 때 ‘지금 퇴근’을 누르면 잔업을 그 시각까지 적어요")
            .addAction(0, "지금 퇴근", pending(context, ACTION_LEAVE, REQUEST_LEAVE))
            .setContentIntent(Notifications.open(context, NoticeLinks.Target.TODAY, REQUEST_OPEN))
            .setTimeoutAfter(STALE_AFTER_MILLIS)
            .build()
        Notifications.post(context, ID, n)
    }

    internal suspend fun leave(context: Context) {
        val c = context.appContainer
        val snapshot = c.scheduleRepository.snapshot.first()
        val day = snapshot.resolver.resolve(Today.date())
        val text = runCatching { LeaveNowWriter.write(c.scheduleRepository, day, Today.dateTime(), snapshot.kinds.name(ExtraKind.OVERTIME_ID)).second }.getOrNull()
        val n = NotificationCompat.Builder(context, Notifications.DAY_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setSilent(true)
            .setContentTitle(text ?: "퇴근 시각이에요. 따로 적을 게 없어요")
            .setContentIntent(Notifications.open(context, NoticeLinks.Target.TODAY, REQUEST_OPEN))
            .setAutoCancel(true)
            .setTimeoutAfter(60 * 60 * 1000L)
            .build()
        Notifications.post(context, ID, n)
        WidgetRefresher.refresh(context)
    }

    fun cancel(context: Context) = NotificationManagerCompat.from(context).cancel(ID)

    internal const val FIRE = ACTION_FIRE
    internal const val LEAVE = ACTION_LEAVE
}

class LeaveTimeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val c = context.appContainer
        val pending = goAsync()
        c.appScope.launch {
            try {
                when (intent.action) {
                    LeaveTimeNotice.FIRE -> LeaveTimeNotice.post(context)
                    LeaveTimeNotice.LEAVE -> LeaveTimeNotice.leave(context)
                }
            } finally {
                LeaveTimeNotice.reschedule(context, Today.dateTime().plusMinutes(1))
                pending.finish()
            }
        }
    }
}
