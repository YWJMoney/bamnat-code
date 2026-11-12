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
import com.bamnat.core.schedule.ResolvedDay
import com.bamnat.core.schedule.ScheduleResolver
import com.bamnat.core.sleep.TodayFlow
import com.bamnat.core.time.Today
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/**
 * 2.0.0 [RVIB-21] (결정 19, 설계 docs/plans/2.0.0/두-구간-근무-시간-연차-설계.md 4-2절 1·7번) 둘째 구간 알림: on a day of two parts
 * (가게·알바 '브레이크 타임', 일근·교대 '비는 시간', or an hour's leave in the middle), a quiet line [LEAD_MINUTES] before the next
 * part starts: "17:00 다시 시작". Not a ringing alarm: whoever sleeps in the gap turns on '둘째 구간도 알람' on the shift type
 * ([com.bamnat.core.model.ShiftType.gapAlarm]), and then the alarm rings instead of this. On by default, off in 설정 › 알림과 잠 and
 * its own channel; on a phone without the notification permission it is passed over quietly ([Notifications.post]).
 */
object GapNotice {
    const val ID = 7640
    const val LEAD_MINUTES = 10L
    private const val REQUEST = 3740
    private const val REQUEST_OPEN = 3741
    private const val ACTION_FIRE = "com.bamnat.app.GAP_RESUME"

    /** One coming start of a later part: when to say it, and the day it is on. */
    data class Due(val at: LocalDateTime, val partStart: LocalDateTime, val partEnd: LocalDateTime, val day: ResolvedDay)

    /** Whether [day]'s later parts are told by this notice (not by an alarm that rings for them). */
    fun tells(day: ResolvedDay): Boolean =
        day.isWorkDay && day.split && !(day.shiftType?.gapAlarm == true && day.gapFrom != null && day.entry?.customAlarms == null)

    /** Every later part's notice on [day], earliest first. */
    fun dues(day: ResolvedDay): List<Due> =
        if (!tells(day)) emptyList()
        else day.segments.drop(1).map { Due(it.start.minusMinutes(LEAD_MINUTES), it.start, it.endInclusive, day) }

    /** The next notice after [now] over yesterday (a night of two parts) and the coming week, or null. */
    fun next(resolver: ScheduleResolver, now: LocalDateTime): Due? =
        (-1L..7L).asSequence().flatMap { dues(resolver.resolve(now.toLocalDate().plusDays(it))) }
            .filter { it.at.isAfter(now) }.minByOrNull { it.at }

    /** The notice due at [now] (said up to the part's start), or null. */
    fun dueAt(resolver: ScheduleResolver, now: LocalDateTime): Due? =
        (-1L..0L).asSequence().flatMap { dues(resolver.resolve(now.toLocalDate().plusDays(it))) }
            .firstOrNull { !now.isBefore(it.at.minusMinutes(1)) && now.isBefore(it.partStart) }

    /** "17:00 다시 시작" over "브레이크 타임 끝나고 저녁 17:00–21:00". */
    fun words(due: Due, gap: String): Pair<String, String> {
        val name = due.day.shiftType?.name?.takeIf { it.isNotBlank() } ?: "근무"
        return "${hm(due.partStart)} 다시 시작" to "$gap 끝나고 $name ${hm(due.partStart)}–${hm(due.partEnd)}"
    }

    suspend fun reschedule(context: Context, now: LocalDateTime = Today.dateTime()) {
        val c = context.appContainer
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val due = if (c.settingsRepository.current.gapNotice) next(c.scheduleRepository.snapshot.first().resolver, now) else null
        if (due == null) {
            am.cancel(pending(context))
            return
        }
        // Quiet and not to the minute: a window, never an exact alarm.
        runCatching { am.setWindow(AlarmManager.RTC_WAKEUP, due.at.atZone(Today.zone()).toInstant().toEpochMilli(), 60_000L, pending(context)) }
    }

    private fun pending(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, REQUEST, Intent(context, GapNoticeReceiver::class.java).setAction(ACTION_FIRE),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    internal suspend fun post(context: Context, now: LocalDateTime = Today.dateTime()) {
        val c = context.appContainer
        val s = c.settingsRepository.current
        if (!s.gapNotice) return
        val due = dueAt(c.scheduleRepository.snapshot.first().resolver, now) ?: return
        val (title, text) = words(due, TodayFlow.WorkTalk.of(s.workStyle).gap)
        val n = NotificationCompat.Builder(context, Notifications.GAP_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setSilent(true)
            .setContentTitle(title)
            .setContentText(text)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(Notifications.open(context, NoticeLinks.Target.TODAY, REQUEST_OPEN))
            .setAutoCancel(true)
            // Gone a while after the part started: it is about the next few minutes only.
            .setTimeoutAfter(java.time.Duration.between(now, due.partStart).toMillis().coerceAtLeast(0) + 30 * 60_000L)
            .build()
        Notifications.post(context, ID, n)
    }

    fun cancel(context: Context) = NotificationManagerCompat.from(context).cancel(ID)

    internal const val FIRE = ACTION_FIRE

    private fun hm(t: LocalDateTime) = "%02d:%02d".format(t.hour, t.minute)
}

/** 2.0.0 [RVIB-21] Says [GapNotice] and sets the next one. */
class GapNoticeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        context.appContainer.appScope.launch {
            try {
                if (intent.action == GapNotice.FIRE) runCatching { GapNotice.post(context) }
            } finally {
                runCatching { GapNotice.reschedule(context, Today.dateTime().plusMinutes(1)) }
                pending.finish()
            }
        }
    }
}
