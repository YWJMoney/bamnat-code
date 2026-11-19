package com.bamnat.app.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.bamnat.app.NoticeLinks
import com.bamnat.app.R
import com.bamnat.app.appContainer
import com.bamnat.core.diag.DiagEvents
import com.bamnat.core.diag.EventBook
import com.bamnat.core.time.Today
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * 2.0.0 [RVI-91] (검토 I03c-02) 무음 예행 연습으로 알람 보증. 'Did not ring' is mostly the phone's own power saving, found only when an
 * alarm is missed. Once a day in the daytime (13:00, never within two hours of an alarm) a silent exact alarm comes to the app and
 * is timed; nothing rings, nothing shows. When it came late, the phone says so before the night, "이 폰은 알람이 4분 늦게 와요",
 * and the tap opens 알람 점검 where the setting is fixed. Under 알람 지킴이 (설정 › 알람 › 다음 알람), like the other looks ahead.
 */
object AlarmRehearsal {
    const val ACTION = "com.bamnat.app.alarm.REHEARSAL"
    private const val EXTRA_AT = "at"
    private const val REQUEST = 2321
    const val NOTICE_ID = 7014
    private const val PREFS = "alarm_rehearsal"
    private const val K_AT = "at"
    private const val K_TOLD = "told"
    /** The daytime hour it comes at. */
    val AT: LocalTime = LocalTime.of(13, 0)
    /** Kept this far from a real alarm, so the two never meet. */
    private const val CLEAR_MILLIS = 2 * 60 * 60_000L
    /** Later than this is worth saying. */
    const val LATE_SECONDS = 120L

    /** The next rehearsal after [now]: 13:00 today or tomorrow, moved a day when an alarm is within two hours of it. */
    internal fun nextAt(now: LocalDateTime, alarm: Long?): LocalDateTime {
        var at = now.toLocalDate().atTime(AT).let { if (it.isAfter(now.plusMinutes(30))) it else it.plusDays(1) }
        fun near(t: LocalDateTime) = alarm != null && kotlin.math.abs(t.atZone(Today.zone()).toInstant().toEpochMilli() - alarm) < CLEAR_MILLIS
        if (near(at)) at = at.plusDays(1)
        return at
    }

    /** Planned with the alarms; [alarm] is the next real one, null with alarms or 알람 지킴이 off (none then). */
    fun schedule(context: Context, on: Boolean, alarm: Long?) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        // Only where the phone lets the app ring on time: without 정확한 알람 알람 점검 already says what to fix.
        val exact = android.os.Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!on || !exact) {
            am.cancel(pending(context, 0L))
            prefs.edit().remove(K_AT).apply()
            return
        }
        val at = nextAt(Today.dateTime(), alarm).atZone(Today.zone()).toInstant().toEpochMilli()
        // Set every time (a restart drops it); written to the log only when it is a new one.
        runCatching { am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(context, at)) }
        if (prefs.getLong(K_AT, 0L) == at) return
        prefs.edit().putLong(K_AT, at).apply()
        if (!BootAlarms.isLocked(context)) context.appContainer.telemetry.event(DiagEvents.ALARM_REHEARSAL_SET, EventBook.triggerDetail(at))
    }

    /** The rehearsal came: timed, and said when it was late. */
    fun onRehearsal(context: Context, intent: Intent) {
        val at = intent.getLongExtra(EXTRA_AT, 0L)
        val late = ((Today.millis() - at) / 1000).coerceAtLeast(0L)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(K_AT).apply()
        if (at <= 0L || BootAlarms.isLocked(context)) return
        val c = context.appContainer
        c.telemetry.event(DiagEvents.ALARM_REHEARSAL, EventBook.triggerDetail(at, "late_s=$late"))
        if (late >= LATE_SECONDS) tell(context, late / 60)
        else Notifications.cancel(context, NOTICE_ID)
    }

    /** "이 폰은 알람이 4분 늦게 와요": once a day, quiet, opening 알람 점검. */
    private fun tell(context: Context, minutes: Long) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val today = Today.date().toString()
        if (prefs.getString(K_TOLD, null) == today) return
        prefs.edit().putString(K_TOLD, today).apply()
        val n = NotificationCompat.Builder(context, Notifications.STATUS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setSilent(true)
            .setContentTitle(line(minutes))
            .setContentText("밤 알람 전에 알람 점검에서 고쳐 두세요")
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                "낮에 소리 없이 시험해 보니 알람이 ${minutes}분 늦게 왔어요. 배터리 절약이 앱을 늦추는 경우가 많아요. 눌러서 알람 점검을 열어요.\n${AlarmGuard.OFF_HINT}"))
            .setContentIntent(Notifications.open(context, NoticeLinks.Target.ALARM_CHECK, REQUEST + 1))
            .setAutoCancel(true)
            .build()
        Notifications.post(context, NOTICE_ID, n)
    }

    internal fun line(minutes: Long) = "이 폰은 알람이 ${minutes}분 늦게 와요"

    private fun pending(context: Context, at: Long): PendingIntent = PendingIntent.getBroadcast(
        context, REQUEST,
        Intent(context, AlarmActionReceiver::class.java).setAction(ACTION).putExtra(EXTRA_AT, at),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
