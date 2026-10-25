package com.bamnat.app.alarm

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.bamnat.app.NoticeLinks
import com.bamnat.app.R
import com.bamnat.app.appContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import com.bamnat.core.time.Today

/**
 * [ALRM-09] 오늘 남은 알람 모두 끄기 (결정 D78): until the next midnight, or the start of the shift the ringing alarm
 * woke the user for when that is later, nothing rings: no shift alarm, snooze, 일어났나요? or memo and invest ring.
 * Tomorrow's alarms stay. Kept in device-protected storage so a locked reboot keeps it too, and never backed up:
 * it means something only for that day. 알람 점검's test alarm still rings.
 */
object AlarmsOff {
    private const val PREFS = "alarms_off"
    private const val K_UNTIL = "until"
    private const val REQ_ON = 2201
    const val ACTION_ON = "com.bamnat.app.alarm.ALARMS_ON"
    const val NOTICE_ID = 7007

    private val _until = MutableStateFlow<Long?>(null)
    /** When the alarms come back (epoch ms) while they are off, for the calendar's 오늘 알람 끔 line. */
    val until: StateFlow<Long?> = _until.asStateFlow()

    private fun prefs(context: Context) =
        context.createDeviceProtectedStorageContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** The end of the quiet, or null when the alarms are on. */
    fun activeUntil(context: Context, now: Long = Today.millis()): Long? =
        prefs(context).getLong(K_UNTIL, 0L).takeIf { it > now }.also { _until.value = it }

    fun isOff(context: Context, now: Long = Today.millis()): Boolean = activeUntil(context, now) != null

    /** [ring] is silenced: anything while off, except 알람 점검's own test. */
    fun silences(context: Context, ring: RingRequest, now: Long = Today.millis()): Boolean =
        ring.label != AlarmScheduler.TEST_NAME && isOff(context, now)

    /** The next midnight after [now], or [ring]'s shift start when that comes later (AB-1). */
    fun cutoff(ring: RingRequest?, now: LocalDateTime = Today.dateTime()): LocalDateTime {
        val midnight = now.toLocalDate().plusDays(1).atStartOfDay()
        val start = ring?.takeIf { it.shiftName.isNotBlank() && it.label == null }?.let { it.shiftDate.atTime(it.shiftStart) }
        return if (start != null && start.isAfter(midnight)) start else midnight
    }

    /**
     * Turns the rest of today's alarms off. The ringing one is turned off by the caller ([AlarmService]); here the
     * snooze and 일어났나요? go, the system's next alarm moves past [cutoff], and a quiet notice offers 다시 켜기.
     */
    fun turnOff(context: Context, ring: RingRequest?, now: LocalDateTime = Today.dateTime()) {
        val until = cutoff(ring, now).atZone(Today.zone()).toInstant().toEpochMilli()
        prefs(context).edit().putLong(K_UNTIL, until).commit()
        _until.value = until
        WakeCheck.cancel(context)
        if (BootAlarms.isLocked(context)) {
            // [REL-10] Still locked after a reboot: the snooze and the next alarm come from the device-protected copy.
            context.getSystemService(android.app.AlarmManager::class.java)?.cancel(AlarmScheduler.snoozeIntent(context, null))
            BootAlarms.saveSnooze(context, null, 0L)
            Notifications.cancel(context, Notifications.SNOOZE_NOTIFICATION_ID)
            BootAlarms.registerNext(context, after = until - 1)
        } else {
            val c = context.appContainer
            c.alarmScheduler.cancelSnooze()
            c.telemetry.event("alarm_all_off", "until_h=${AlarmScheduler.millisToLocal(until).hour}")
            c.journal.note(com.bamnat.core.journal.JournalKind.ALARMS_OFF)
            c.appScope.launch { runCatching { c.rescheduleAlarms() } }
        }
        postNotice(context, until)
    }

    /** 다시 켜기: the alarms still ahead today are registered again. */
    fun turnOn(context: Context) {
        prefs(context).edit().remove(K_UNTIL).commit()
        _until.value = null
        Notifications.cancel(context, NOTICE_ID)
        if (BootAlarms.isLocked(context)) {
            BootAlarms.registerNext(context)
        } else {
            val c = context.appContainer
            c.telemetry.event("alarm_all_on")
            c.appScope.launch { runCatching { c.rescheduleAlarms() } }
        }
    }

    /** Where planning starts while off: just before the end, so an alarm right at midnight still rings. */
    fun planFrom(context: Context, after: LocalDateTime): LocalDateTime {
        val until = activeUntil(context) ?: return after
        val end = AlarmScheduler.millisToLocal(until - 1_000)
        return if (end.isAfter(after)) end else after
    }

    private fun postNotice(context: Context, until: Long) {
        val at = AlarmScheduler.millisToLocal(until)
        val notification = NotificationCompat.Builder(context, Notifications.STATUS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSilent(true)
            .setContentTitle("오늘 남은 알람을 껐어요")
            .setContentText("${untilText(at)}까지 울리지 않아요 · 내일 알람은 그대로예요")
            .setContentIntent(Notifications.open(context, NoticeLinks.Target.TODAY, REQ_ON + 1))
            .addAction(NotificationCompat.Action.Builder(0, "다시 켜기", PendingIntent.getBroadcast(
                context, REQ_ON, Intent(context, AlarmActionReceiver::class.java).setAction(ACTION_ON),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )).setAuthenticationRequired(false).build())
            .setTimeoutAfter((until - Today.millis()).coerceAtLeast(1_000L))
            .build()
        Notifications.post(context, NOTICE_ID, notification)
    }

    /** "밤 12시" for midnight, else "내일 06:30" for a shift start after it. */
    fun untilText(at: LocalDateTime): String =
        if (at.toLocalTime() == java.time.LocalTime.MIDNIGHT) "밤 12시" else "%s %02d:%02d".format(if (at.toLocalDate() == Today.date()) "오늘" else "내일", at.hour, at.minute)
}
