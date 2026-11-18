package com.bamnat.app.watch

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.bamnat.app.NoticeLinks
import com.bamnat.app.R
import com.bamnat.app.alarm.Notifications
import com.bamnat.app.appContainer
import com.bamnat.core.time.Today
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * 2.0.0 [RVI-11] (검토 ALM-10) 오늘 알람 끄기 pressed on the watch: the phone says so with a quiet notice and 되돌리기, so a brush on
 * the wrist never turns the day's alarms off unseen. The watch's own 되돌리기 ([com.bamnat.core.work.WatchDays.ALARM_ON_PATH]) and this
 * notice's do the same: the day's alarms come back, and the watch is told.
 */
object WatchAlarmNotice {
    const val ID = 7013

    fun post(context: Context, date: LocalDate) {
        val undo = PendingIntent.getBroadcast(
            context, 2111,
            Intent(context, WatchAlarmUndoReceiver::class.java).putExtra(WatchAlarmUndoReceiver.EXTRA_DAY, date.toEpochDay()),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, Notifications.STATUS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setSilent(true)
            .setContentTitle("워치에서 오늘 알람을 껐어요")
            .setContentText("오늘 남은 근무 알람이 울리지 않아요")
            .setContentIntent(Notifications.open(context, NoticeLinks.Target.TODAY, 2112, payload = NoticeLinks.TODAY_DAY))
            .addAction(NotificationCompat.Action.Builder(0, "되돌리기", undo).setAuthenticationRequired(false).build())
            .setAutoCancel(true)
            .setGroup(Notifications.GROUP_STATUS + "_watch").setGroupSummary(true)
            .build()
        Notifications.post(context, ID, n)
    }

    /** The day's alarms back on, the notice gone, the watch told. */
    suspend fun undo(context: Context, date: LocalDate) {
        Notifications.cancel(context, ID)
        if (date != Today.date()) return
        val c = context.appContainer
        c.scheduleRepository.updateDay(date) { it.copy(alarmSkipped = false) }
        WatchSync.push(context, force = true)
    }
}

class WatchAlarmUndoReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val day = intent.getLongExtra(EXTRA_DAY, Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE }?.let(LocalDate::ofEpochDay) ?: return
        val pending = goAsync()
        context.appContainer.appScope.launch {
            try {
                WatchAlarmNotice.undo(context, day)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_DAY = "day"
    }
}
