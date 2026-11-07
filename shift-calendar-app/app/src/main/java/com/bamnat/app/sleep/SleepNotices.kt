package com.bamnat.app.sleep

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.bamnat.app.NoticeLinks
import com.bamnat.app.R
import com.bamnat.app.alarm.Notifications
import com.bamnat.app.appContainer
import com.bamnat.core.hours.HoursFormat
import com.bamnat.core.schedule.ScheduleResolver
import com.bamnat.core.sleep.SleepDebt
import com.bamnat.core.sleep.SleepEntry
import com.bamnat.core.sleep.SleepSchedule
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import com.bamnat.core.time.Today

/** [SLP-15] "잠들었어요" and "깼어요" on a notice, usable from the lock screen. */
object SleepActions {
    const val ACTION_ASLEEP = "com.bamnat.app.sleep.ASLEEP"
    const val ACTION_AWAKE = "com.bamnat.app.sleep.AWAKE"
    /** 1.39 G5-B1: 깼어요 pressed by a sleepy hand. The sleep goes on from where it began. */
    const val ACTION_STILL_ASLEEP = "com.bamnat.app.sleep.STILL_ASLEEP"
    const val EXTRA_SLEEP_ID = "sleep_id"

    fun asleep(context: Context): NotificationCompat.Action = action(context, "잠들었어요", ACTION_ASLEEP, 3311)
    fun awake(context: Context): NotificationCompat.Action = action(context, "깼어요", ACTION_AWAKE, 3312)

    /** On the 몇 시간 잤어요 notice: 깼어요 by mistake (from the notice, the app or the night screen) is undone in one tap. */
    fun stillAsleep(context: Context, sleepId: Long): NotificationCompat.Action =
        action(context, "아직 자요", ACTION_STILL_ASLEEP, 3315) { putExtra(EXTRA_SLEEP_ID, sleepId) }

    private fun action(context: Context, title: String, action: String, requestCode: Int, extras: Intent.() -> Unit = {}): NotificationCompat.Action =
        NotificationCompat.Action.Builder(0, title, PendingIntent.getBroadcast(
            context, requestCode, Intent(context, SleepActionReceiver::class.java).setAction(action).apply(extras),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )).setAuthenticationRequired(false).build()
}

/** Writes the tap into the sleep log; the 자는 중 notice follows the log by itself. */
class SleepActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val container = context.appContainer
        val pending = goAsync()
        container.appScope.launch {
            try {
                when (intent.action) {
                    SleepActions.ACTION_ASLEEP -> {
                        container.sleepRepository.fellAsleep()
                        Notifications.cancel(context, Notifications.BEDTIME_ID)
                        Notifications.cancel(context, Notifications.COMMUTE_ID)
                    }
                    SleepActions.ACTION_AWAKE -> {
                        container.sleepRepository.wokeUp()
                        // 1.39 [SLEEP-06] 깼어요 ends 자는 동안 소리 too, at once.
                        SleepRecordService.stop(context)
                    }
                    SleepActions.ACTION_STILL_ASLEEP -> {
                        // Only the sleep that just ended, and only while no other sleep was begun since: the 자는 중 notice and
                        // 방해 금지 come back by themselves (they follow the log). 자는 동안 소리 is not started again here (Android
                        // lets the microphone start only from the app on screen); the 자는 중 notice opens the night screen for it.
                        val id = intent.getLongExtra(SleepActions.EXTRA_SLEEP_ID, 0L)
                        if (id > 0 && container.sleepRepository.openNow() == null) container.sleepRepository.reopen(id)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}

/**
 * [SLP-15] 자는 중: while a logged sleep is open, a quiet notice that stays, counts the time and has
 * the 깼어요 button. After 깼어요 it says how long the sleep was and what comes next, for an hour.
 */
object SleepingNotice {
    private const val WOKE_SHOWN_MILLIS = 60 * 60 * 1000L

    fun show(context: Context, open: SleepEntry, nextAlarm: LocalDateTime?, now: LocalDateTime) {
        val start = open.start
        // A sleep left open past 16 hours is a missed 깼어요, not a sleep: the notice goes by itself.
        val left = SleepDebt.MAX_SLEEP_MINUTES * 60_000 - Duration.between(start, now).toMillis()
        if (left <= 0) return cancel(context)
        val title = (if (start.toLocalDate() == now.toLocalDate()) "" else "어제 ") + "${hm(start)}부터 자는 중"
        val notification = NotificationCompat.Builder(context, Notifications.SLEEPING_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setGroup(Notifications.SLEEPING_GROUP).setGroupSummary(true) // 1.39 PC c0ca3d9f: a group with no summary is bundled by Android 15+
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setSilent(true)
            .setContentTitle(title)
            // 1.39 (PC a483ca15): any day after today was "내일" here; the night screen's day word now (오늘·내일·모레·11/15).
            .setContentText(nextAlarm?.let { "다음 알람 " + com.bamnat.core.sleep.NightLine.day(now, it) + " " + hm(it) } ?: "깨면 ‘깼어요’를 눌러 주세요")
            .setWhen(start.atZone(Today.zone()).toInstant().toEpochMilli())
            .setUsesChronometer(true)
            .setShowWhen(true)
            // 1.39 [FLOW-13] Opened at night, it is the night screen (clock, the alarm, 깼어요), not the bright calendar.
            .setContentIntent(Notifications.open(context, NoticeLinks.Target.NIGHT, 3319))
            .addAction(SleepActions.awake(context))
            .setTimeoutAfter(left)
            .build()
        Notifications.post(context, Notifications.SLEEPING_ID, notification)
    }

    fun woke(context: Context, slept: SleepEntry, next: String?) {
        val minutes = slept.minutes ?: return cancel(context)
        val notification = NotificationCompat.Builder(context, Notifications.DAY_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setSilent(true)
            .setContentTitle("${HoursFormat.korean(minutes.toInt())} 잤어요")
            .apply { next?.let { setContentText("다음: $it") } }
            .setContentIntent(Notifications.open(context, NoticeLinks.Target.TODAY, 3313))
            // 1.39 G5-B1 (PC 1.38 최종 사용성 6: 깼어요 잘못 누르기): the sleep goes on, from when it began.
            .apply { if (slept.id > 0 && !slept.imported) addAction(SleepActions.stillAsleep(context, slept.id)) }
            .setAutoCancel(true)
            .setTimeoutAfter(WOKE_SHOWN_MILLIS)
            .build()
        Notifications.post(context, Notifications.SLEEPING_ID, notification)
    }

    fun cancel(context: Context) = Notifications.cancel(context, Notifications.SLEEPING_ID)

    /** "오늘 19:00 야간": the next work after [now], for the line under how long the sleep was. */
    fun nextWork(resolver: ScheduleResolver, now: LocalDateTime): String? {
        val today = now.toLocalDate()
        val w = SleepSchedule.of(resolver, today, today.plusDays(6), alarmsOn = false).work
            .firstOrNull { it.from.isAfter(now) } ?: return null
        val day = when (w.from.toLocalDate()) {
            today -> "오늘"
            today.plusDays(1) -> "내일"
            else -> "${w.from.monthValue}/${w.from.dayOfMonth}"
        }
        return "$day ${hm(w.from)} ${w.name}"
    }

    private fun hm(x: LocalDateTime) = "%02d:%02d".format(x.hour, x.minute)
}
