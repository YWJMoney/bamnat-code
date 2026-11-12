package com.bamnat.app.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.bamnat.app.NoticeLinks
import com.bamnat.app.R
import com.bamnat.app.appContainer
import com.bamnat.app.data.AppSettings
import com.bamnat.core.sleep.TodayFlow
import com.bamnat.core.time.Today
import java.time.LocalDateTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 2.0.0 [RVI-93] (검토 I03b-04, 대기열 G5-M2) 근무 중 쪽잠 타이머. The 낮잠 button came once, before the first night; a night's 휴게
 * had nothing. Deep in a night of work, 오늘의 흐름 offers 쪽잠 20분: a wake that only vibrates (the person beside sleeps on), on
 * the phone and, with 워치 진동 알람, on the wrist. It rings through the same exact alarm and ringing screen as a shift's, in a
 * slot of its own that never moves the shift's alarms, and rings even when today's alarms were all turned off (the person just
 * asked for it). A notice waits with 일어났어요 to end it first.
 */
object ShiftNap {
    /** The ring's words, which keep it apart from a shift's alarm ([RingRequest.label]). */
    const val LABEL = "쪽잠 알람"
    const val ACTION_CANCEL = "com.bamnat.app.alarm.SHIFT_NAP_CANCEL"
    const val NOTICE_ID = 7015
    private const val REQ_RING = 2322
    private const val REQ_CANCEL = 2323
    private const val REQ_OPEN = 2324
    private const val PREFS = "shift_nap"
    private const val K_AT = "at"

    private val _until = MutableStateFlow<Long?>(null)
    /** When the 쪽잠 now on vibrates (epoch ms), or null. */
    val until: StateFlow<Long?> = _until.asStateFlow()

    /** Read once the app starts, so the card knows a 쪽잠 set before. */
    fun load(context: Context) {
        _until.value = prefs(context).getLong(K_AT, 0L).takeIf { it > Today.millis() }
    }

    fun untilLocal(context: Context): LocalDateTime? {
        if (_until.value == null) load(context)
        return _until.value?.let(AlarmScheduler::millisToLocal)
    }

    /** 쪽잠 [minutes]: vibrates then, on the minute. */
    fun start(context: Context, minutes: Int = TodayFlow.SHIFT_NAP_MINUTES, now: LocalDateTime = Today.dateTime()): Long {
        val at = now.plusMinutes(minutes.toLong()).withSecond(0).withNano(0)
        val atMillis = at.atZone(Today.zone()).toInstant().toEpochMilli()
        val ring = RingRequest("", at.toLocalDate(), at.toLocalTime(), 0, AppSettings.SILENT_SOUND, atMillis, label = LABEL)
        val am = context.getSystemService(AlarmManager::class.java)
        if (am != null) AlarmScheduler.setAlarm(context, am, atMillis, ringIntent(context, ring))
        prefs(context).edit().putLong(K_AT, atMillis).apply()
        _until.value = atMillis
        notice(context, at, minutes)
        context.appContainer.telemetry.event("shift_nap", "min=$minutes")
        return atMillis
    }

    /** 일어났어요 before the vibration: called off. */
    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(ringIntent(context, null))
        clear(context)
    }

    /** It rang, or was called off. */
    fun clear(context: Context) {
        prefs(context).edit().remove(K_AT).apply()
        _until.value = null
        Notifications.cancel(context, NOTICE_ID)
    }

    /** The ring is a 쪽잠's. */
    fun isNap(ring: RingRequest): Boolean = ring.label == LABEL

    internal fun describe(ring: RingRequest): String = if (ring.isSnooze) "쪽잠 · 다시 울림" else "쪽잠이 끝났어요 · 진동만 울려요"

    private fun notice(context: Context, at: LocalDateTime, minutes: Int) {
        val atMillis = at.atZone(Today.zone()).toInstant().toEpochMilli()
        val n = NotificationCompat.Builder(context, Notifications.STATUS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSilent(true)
            .setOngoing(true)
            .setContentTitle("쪽잠 ${minutes}분 · %02d:%02d에 진동".format(at.hour, at.minute))
            .setContentText("진동만 울려요 · 옆 사람은 깨지 않아요")
            .setWhen(atMillis)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setContentIntent(Notifications.open(context, NoticeLinks.Target.TODAY, REQ_OPEN))
            .addAction(NotificationCompat.Action.Builder(0, "일어났어요", PendingIntent.getBroadcast(
                context, REQ_CANCEL, Intent(context, AlarmActionReceiver::class.java).setAction(ACTION_CANCEL),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )).setAuthenticationRequired(false).build())
            .build()
        Notifications.post(context, NOTICE_ID, n)
    }

    internal fun ringIntent(context: Context, ring: RingRequest?): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).setAction(AlarmReceiver.ACTION_SHIFT_NAP)
        ring?.putInto(intent)
        return PendingIntent.getBroadcast(context, REQ_RING, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
