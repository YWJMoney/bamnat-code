package com.bamnat.app.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.bamnat.app.NoticeLinks
import com.bamnat.app.R
import com.bamnat.app.appContainer
import com.bamnat.app.ui.Fmt
import com.bamnat.app.ui.diag.AlarmCheck
import com.bamnat.app.ui.diag.alarmChecks
import com.bamnat.core.time.Today

/**
 * [ALRM-27] 1.38 알람 지킴이 (사용자 2026-11-05 "경쟁 앱을 이길 강점"): people find out an alarm could not ring only when it
 * did not. A few hours before each alarm the app looks at what would stop it (the alarm volume at 0, the alarm notice
 * turned off, 정확한 알람 taken away, 방해 금지 that silences alarms, the system losing the alarm) and, if one is wrong, says
 * so while there is still time: "05:30 알람이 제대로 안 울릴 수 있어요 · 알람 소리가 0이에요". Tapping opens 알람 점검. Once a day
 * per problem, never while the phone is locked after a restart, and 설정 › 알람 › 다음 알람 카드 turns it off.
 */
object AlarmGuard {
    const val ACTION_GUARD = "com.bamnat.app.alarm.GUARD"
    private const val EXTRA_AT = "at"
    const val NOTICE_ID = 7011
    private const val REQ_GUARD = 2311
    private const val PREFS = "alarm_guard"
    private const val K_LAST = "last"

    /** How long before the alarm it looks: early enough to fix, late enough that the phone is as it will be at night. */
    const val LEAD_MILLIS = 3 * 60 * 60_000L
    /** Closer than this to the alarm there is no point in a notice. */
    private const val MIN_AHEAD_MILLIS = 15 * 60_000L

    /** The checks that can keep an alarm from ringing or from being heard; advice such as 배터리 최적화 is left to 알람 점검. */
    val CRITICAL = setOf("알람 사용", "알람 알림 켜짐", "알람 소리 크기", "정확한 알람", "잠금 화면 알람", "잠금 화면에 알람 보이기", "방해 금지", "다음 알람 등록")

    private fun intent(context: Context, at: Long): PendingIntent = PendingIntent.getBroadcast(
        context, REQ_GUARD,
        Intent(context, AlarmActionReceiver::class.java).setAction(ACTION_GUARD).putExtra(EXTRA_AT, at),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** When to look for an alarm at [triggerAt] from [now]; null when it is too close. */
    internal fun lookAt(triggerAt: Long, now: Long): Long? {
        if (triggerAt - now < MIN_AHEAD_MILLIS) return null
        return maxOf(triggerAt - LEAD_MILLIS, now + 60_000L)
    }

    /** Registers the look for the alarm at [triggerAt]; null takes it away. Inexact on purpose: it works without 정확한 알람. */
    fun schedule(context: Context, triggerAt: Long?) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val at = triggerAt?.let { lookAt(it, Today.millis()) }
        if (triggerAt == null || at == null) {
            am.cancel(intent(context, 0L))
            return
        }
        runCatching { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent(context, triggerAt)) }
    }

    /** The problems that matter for the alarm, from the same list 알람 점검 shows. */
    internal fun problems(checks: List<AlarmCheck>): List<AlarmCheck> = checks.filter { it.ok == false && it.title in CRITICAL }

    /** The look came. */
    fun onGuard(context: Context, intent: Intent) {
        if (BootAlarms.isLocked(context)) return
        val at = intent.getLongExtra(EXTRA_AT, 0L)
        val now = Today.millis()
        if (at <= now) return
        val c = context.appContainer
        if (!c.settingsRepository.current.alarmGuard || AlarmsOff.isOff(context) || AlarmSoon.isSkipped(context, at)) return
        val bad = problems(runCatching { alarmChecks(c, context) }.getOrDefault(emptyList()))
        if (bad.isEmpty()) {
            Notifications.cancel(context, NOTICE_ID)
            return
        }
        // Once a day for the same problems.
        val key = "${Today.date()}|" + bad.joinToString(",") { it.title }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(K_LAST, null) == key) return
        prefs.edit().putString(K_LAST, key).apply()
        c.telemetry.event("alarm_guard_warned", bad.joinToString(",") { it.title })
        val time = Fmt.time(AlarmScheduler.millisToLocal(at).toLocalTime())
        val first = bad.first()
        val more = if (bad.size > 1) " 외 ${bad.size - 1}가지" else ""
        val n = NotificationCompat.Builder(context, Notifications.STATUS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentTitle(title(time))
            .setContentText("${first.title}$more · 눌러서 바로 고쳐요")
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                bad.joinToString("\n") { "· ${it.title}: ${it.why}" } + "\n$OFF_HINT"))
            .setContentIntent(Notifications.open(context, NoticeLinks.Target.ALARM_CHECK, REQ_GUARD + 1))
            .setAutoCancel(true)
            .setTimeoutAfter((at - now).coerceAtLeast(1_000L))
            .build()
        Notifications.post(context, NOTICE_ID, n)
    }

    internal fun title(time: String) = "$time 알람이 제대로 안 울릴 수 있어요"

    // [ALRM-29] 1.38 (사용자 2026-11-05 '달력 상호작용을 키우자'): the first alarm after a break (휴가, 연휴: two days without one)
    // is said the evening before, "내일부터 다시 근무예요 · 05:30 주간 근무 알람이 켜져 있어요", with 이번만 끄기 right there for
    // a break that got longer. Under the same 알람 지킴이 switch.
    const val ACTION_BACK = "com.bamnat.app.alarm.BACK_TO_WORK"
    const val BACK_NOTICE_ID = 7012
    private const val REQ_BACK = 2313
    private const val REQ_BACK_SKIP = 2314
    private const val EXTRA_TEXT = "text"

    private fun backIntent(context: Context, at: Long, text: String?): PendingIntent = PendingIntent.getBroadcast(
        context, REQ_BACK,
        Intent(context, AlarmActionReceiver::class.java).setAction(ACTION_BACK).putExtra(EXTRA_AT, at).putExtra(EXTRA_TEXT, text),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** When to say it for an alarm at [trigger]: ten hours before, moved to 21:00 the evening before when that is the night. */
    internal fun backAt(trigger: java.time.LocalDateTime): java.time.LocalDateTime {
        val at = trigger.minusHours(10)
        return if (at.hour >= 23 || at.hour < 7) (if (at.hour >= 23) at.toLocalDate() else at.toLocalDate().minusDays(1)).atTime(21, 0) else at
    }

    /** Registers the evening-before word for the alarm at [triggerAt] that ends a break; null takes it away. */
    fun scheduleBack(context: Context, triggerAt: Long?, text: String?) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        if (triggerAt == null) { am.cancel(backIntent(context, 0L, null)); return }
        val at = backAt(AlarmScheduler.millisToLocal(triggerAt)).atZone(Today.zone()).toInstant().toEpochMilli()
        if (at < Today.millis() + 60_000L) { am.cancel(backIntent(context, 0L, null)); return }
        runCatching { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, backIntent(context, triggerAt, text)) }
    }

    /** "내일부터 다시 근무예요" or "오늘 다시 근무예요", from where [now] is. */
    internal fun backTitle(alarm: java.time.LocalDate, now: java.time.LocalDate) =
        if (alarm == now) "오늘 다시 근무예요" else "내일부터 다시 근무예요"

    fun onBack(context: Context, intent: Intent) {
        if (BootAlarms.isLocked(context)) return
        val at = intent.getLongExtra(EXTRA_AT, 0L)
        if (at <= Today.millis()) return
        val c = context.appContainer
        if (!c.settingsRepository.current.alarmGuard || AlarmsOff.isOff(context) || AlarmSoon.isSkipped(context, at)) return
        val local = AlarmScheduler.millisToLocal(at)
        val text = intent.getStringExtra(EXTRA_TEXT)?.takeIf { it.isNotBlank() } ?: "${Fmt.time(local.toLocalTime())} 알람"
        c.telemetry.event("alarm_back_to_work")
        val n = NotificationCompat.Builder(context, Notifications.DAY_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setSilent(true)
            .setContentTitle(backTitle(local.toLocalDate(), Today.date()))
            .setContentText("$text${Fmt.iga(text)} 켜져 있어요")
            .setStyle(NotificationCompat.BigTextStyle().bigText("$text${Fmt.iga(text)} 켜져 있어요. 쉬는 날이 늘었다면 이번만 끌 수 있어요.\n$OFF_HINT"))
            .setContentIntent(Notifications.open(context, NoticeLinks.Target.TODAY, REQ_BACK + 10))
            .addAction(AlarmSoon.action(context, "이번만 끄기", AlarmSoon.ACTION_SKIP, REQ_BACK_SKIP, at))
            .setAutoCancel(true)
            .setTimeoutAfter((at - Today.millis()).coerceAtLeast(1_000L))
            .build()
        Notifications.post(context, BACK_NOTICE_ID, n)
    }

    const val OFF_HINT = "알리지 않으려면 설정 › 알람 › 다음 알람 › 알람 지킴이"
}
