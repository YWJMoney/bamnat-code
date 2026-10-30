package com.bamnat.app.alarm

import android.app.AlarmManager
import android.app.KeyguardManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.bamnat.app.NoticeLinks
import com.bamnat.app.R
import com.bamnat.app.appContainer
import com.bamnat.app.ui.Fmt
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import com.bamnat.core.time.Today

/**
 * [ALRM-21] 1.35 곧 알람이 울려요: a few minutes before the next alarm, while the phone is in hand (screen on and
 * unlocked), a small notice at the top offers to turn that one alarm off. Asleep or locked, nothing shows: the alarm is
 * what wakes the user. 이번 알람 끄기 skips only that alarm (kept in device-protected storage like [AlarmsOff]) and
 * the next one is registered; 다시 켜기 on the notice that follows puts it back while it is still ahead.
 */
object AlarmSoon {
    private const val PREFS = "alarm_soon"
    private const val K_SKIP = "skip"
    const val ACTION_SOON = "com.bamnat.app.alarm.SOON"
    const val ACTION_SKIP = "com.bamnat.app.alarm.SOON_SKIP"
    const val ACTION_KEEP = "com.bamnat.app.alarm.SOON_KEEP"
    const val ACTION_UNSKIP = "com.bamnat.app.alarm.SOON_UNSKIP"
    private const val EXTRA_AT = "at"
    private const val EXTRA_TEXT = "text"
    const val NOTICE_ID = 7009
    private const val REQ_FIRE = 2301
    private const val REQ_SKIP = 2302
    private const val REQ_KEEP = 2303
    private const val REQ_UNSKIP = 2304

    private fun prefs(context: Context) =
        context.createDeviceProtectedStorageContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** The alarm turned off with 이번 알람 끄기 (its trigger, epoch ms) while it is still ahead, else null. */
    fun skipped(context: Context, now: Long = Today.millis()): Long? =
        prefs(context).getLong(K_SKIP, 0L).takeIf { it > now - 60_000 }

    /** Where planning starts: just past the skipped alarm, so the one after it is registered. */
    fun planFrom(context: Context, after: LocalDateTime): LocalDateTime {
        val skip = skipped(context) ?: return after
        val at = AlarmScheduler.millisToLocal(skip)
        return if (at.isBefore(after)) after else at
    }

    /** [ring] was turned off ahead of time; it stays quiet even if it was registered before. */
    fun silences(context: Context, ring: RingRequest): Boolean =
        !ring.isSnooze && !ring.recheck && ring.label != AlarmScheduler.TEST_NAME && skipped(context) == ring.triggerAtMillis

    private fun fireIntent(context: Context, at: Long, text: String?): PendingIntent = PendingIntent.getBroadcast(
        context, REQ_FIRE,
        Intent(context, AlarmActionReceiver::class.java).setAction(ACTION_SOON).putExtra(EXTRA_AT, at).putExtra(EXTRA_TEXT, text),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /**
     * Registers the look ahead for the alarm at [triggerAt] ([text] says which), [minutes] before it; null or 0 takes it
     * away. Not exact: a minute late changes nothing, and the phone is awake when it matters.
     */
    fun schedule(context: Context, triggerAt: Long?, text: String?, minutes: Int) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val at = triggerAt?.minus(minutes * 60_000L)
        if (triggerAt == null || minutes <= 0 || at == null || at <= Today.millis()) {
            am.cancel(fireIntent(context, 0L, null))
            return
        }
        runCatching { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, fireIntent(context, triggerAt, text)) }
    }

    /** The phone is on and unlocked: someone is using it. */
    fun inUse(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java)?.isInteractive == true &&
            context.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked != true

    /** The look ahead came: the notice, only while the phone is in use and the alarm is still ahead. */
    fun onSoon(context: Context, intent: Intent) {
        val at = intent.getLongExtra(EXTRA_AT, 0L)
        val now = Today.millis()
        if (at <= now || at - now > 31 * 60_000L || !inUse(context) || AlarmsOff.isOff(context) || skipped(context) == at) return
        val text = intent.getStringExtra(EXTRA_TEXT).orEmpty()
        val time = Fmt.time(AlarmScheduler.millisToLocal(at).toLocalTime())
        val notification = NotificationCompat.Builder(context, Notifications.ALARM_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentTitle("곧 알람이 울려요 · $time")
            .setContentText(text.ifEmpty { "이번 알람을 끌까요?" })
            .setContentIntent(Notifications.open(context, NoticeLinks.Target.TODAY, REQ_FIRE + 10))
            .addAction(action(context, "이번 알람 끄기", ACTION_SKIP, REQ_SKIP, at))
            .addAction(action(context, "그대로 두기", ACTION_KEEP, REQ_KEEP, at))
            .setOnlyAlertOnce(true)
            .setTimeoutAfter((at - now).coerceAtLeast(1_000L))
            .setAutoCancel(true)
            .build()
        Notifications.post(context, NOTICE_ID, notification)
        if (!BootAlarms.isLocked(context)) context.appContainer.telemetry.event("alarm_soon_shown")
    }

    /** 이번 알람 끄기: that alarm alone; the next one is registered and a quiet notice offers 다시 켜기. */
    fun skip(context: Context, at: Long) {
        Notifications.cancel(context, NOTICE_ID)
        if (at <= Today.millis()) return
        prefs(context).edit().putLong(K_SKIP, at).commit()
        if (!BootAlarms.isLocked(context)) {
            val c = context.appContainer
            c.telemetry.event("alarm_soon_skip")
            c.appScope.launch { runCatching { c.rescheduleAlarms() } }
        }
        val time = Fmt.time(AlarmScheduler.millisToLocal(at).toLocalTime())
        val notification = NotificationCompat.Builder(context, Notifications.STATUS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setSilent(true)
            .setContentTitle("$time 알람을 껐어요")
            .setContentText("다음 알람은 그대로 울려요")
            .addAction(action(context, "다시 켜기", ACTION_UNSKIP, REQ_UNSKIP, at))
            .setTimeoutAfter((at - Today.millis()).coerceAtLeast(1_000L))
            .build()
        Notifications.post(context, NOTICE_ID, notification)
    }

    /** 다시 켜기: the skipped alarm rings after all, while it is still ahead. */
    fun unskip(context: Context) {
        prefs(context).edit().remove(K_SKIP).commit()
        Notifications.cancel(context, NOTICE_ID)
        if (!BootAlarms.isLocked(context)) {
            val c = context.appContainer
            c.appScope.launch { runCatching { c.rescheduleAlarms() } }
        }
    }

    private fun action(context: Context, title: String, action: String, requestCode: Int, at: Long) =
        NotificationCompat.Action.Builder(0, title, PendingIntent.getBroadcast(
            context, requestCode,
            Intent(context, AlarmActionReceiver::class.java).setAction(action).putExtra(EXTRA_AT, at),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )).setAuthenticationRequired(false).build()

    fun at(intent: Intent): Long = intent.getLongExtra(EXTRA_AT, 0L)
}
