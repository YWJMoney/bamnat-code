package com.bamnat.app.company

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import com.bamnat.app.AppContainer
import com.bamnat.app.NoticeLinks
import com.bamnat.app.R
import com.bamnat.app.alarm.Notifications
import com.bamnat.app.appContainer
import com.bamnat.core.plaza.StylePlaza
import com.bamnat.core.schedule.ScheduleResolver
import com.bamnat.core.time.Today
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/**
 * 2.0.0 [RVN-24] 검토 I10ac-03 근무에 맞춰 말을 거는 광장: [AFTER_MINUTES] after a shift on the main calendar ends, one quiet notice
 * "오늘 야간 어땠어요?" with a line to write right there; the line goes to my own kind of work's 광장 with my 근무 태그, as one written
 * there, and a tap opens that 광장 on '나와 같은 근무'. Off by default (광장 › 내 근무 태그 › 알림 '근무가 끝나면 한 줄 묻기');
 * once a day at most, not on a day off, not while the user sleeps; left alone [LEFT_MAX] times in a row, it waits [PAUSE_DAYS] days.
 * Answers to my questions already wait through work and sleep and come as one (PlazaNewsJob.tell, QuietHold VILLAGE).
 */
object PlazaAfterShift {
    const val ID = 7660
    private const val REQUEST = 3760
    private const val REQUEST_REPLY = 3761
    private const val REQUEST_OPEN = 3762
    private const val ACTION_FIRE = "com.bamnat.app.PLAZA_AFTER_SHIFT"
    private const val ACTION_REPLY = "com.bamnat.app.PLAZA_AFTER_SHIFT_REPLY"
    internal const val KEY_LINE = "line"
    /** The payload that opens 광장 on '나와 같은 근무'. */
    const val SAME = "same"

    const val AFTER_MINUTES = 20L
    const val LEFT_MAX = 3
    const val PAUSE_DAYS = 14L
    private const val STALE_AFTER_MILLIS = 3 * 60 * 60 * 1000L

    /** The kinds of work whose day ends at a time the calendar knows. */
    private val STYLES = setOf(WorkStyle.SHIFT, WorkStyle.DAY, WorkStyle.FIELD, WorkStyle.HOURLY)

    private const val PREFS = "plaza_tag"
    private const val K_ON = "push_after_shift"
    private const val K_LEFT = "after_shift_left"
    private const val K_PAUSE = "after_shift_pause"
    private const val K_LAST = "after_shift_last"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun on(context: Context): Boolean = runCatching { prefs(context).getBoolean(K_ON, false) }.getOrDefault(false)

    fun setOn(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(K_ON, on).apply {
            // Turned on again: asked as new.
            if (on) remove(K_LEFT).remove(K_PAUSE)
        }.apply()
    }

    /** My own kind of work's 광장 when it is one with a day that ends; null otherwise. */
    fun room(c: AppContainer): String? = c.settingsRepository.current.workStyle?.takeIf { it in STYLES }?.let(StylePlaza::styleRoom)

    /** Whether it may ask at all: the 광장 open to this phone's news, the switch on, and a kind of work with an end. */
    private fun asks(c: AppContainer): Boolean {
        val s = c.settingsRepository.current
        return on(c.appContext) && c.plazaTags.on && c.companyApi.enabled && s.plazaRulesAgreed > 0 && room(c) != null
    }

    /** The question for the shift that ended, in my kind of work's words. */
    internal fun question(style: WorkStyle?, shiftName: String?, night: Boolean): String = when (style) {
        WorkStyle.SHIFT -> if (night) "오늘 야간 어땠어요?" else "오늘 ${shiftName?.takeIf { it.isNotBlank() } ?: "근무"} 어땠어요?"
        WorkStyle.DAY -> "오늘 하루 어땠어요?"
        WorkStyle.FIELD -> "오늘 현장 어땠어요?"
        WorkStyle.HOURLY -> "오늘 알바 어땠어요?"
        else -> "오늘 어땠어요?"
    }

    /** The end of the next workday after [now] (within a week) that is still to be asked about, plus [AFTER_MINUTES]. */
    internal fun next(resolver: ScheduleResolver, now: LocalDateTime): LocalDateTime? =
        (-1L..7L).asSequence().map { resolver.resolve(now.toLocalDate().plusDays(it)) }
            .filter { it.isWorkDay && it.leave?.isFullDay != true }
            .mapNotNull { it.shiftTo?.plusMinutes(AFTER_MINUTES) }.firstOrNull { it.isAfter(now) }

    /**
     * Whether a notice may go on [today]: not twice a day, not while paused (until [pausedUntil]), and after [LEFT_MAX] left alone the
     * pause starts. Returns the new (left, pausedUntil) to keep, with whether to ask.
     */
    internal data class Turn(val ask: Boolean, val left: Int, val pausedUntil: Long)

    internal fun turn(today: Long, last: Long, left: Int, pausedUntil: Long): Turn = when {
        today == last -> Turn(false, left, pausedUntil)
        today < pausedUntil -> Turn(false, left, pausedUntil)
        left >= LEFT_MAX -> Turn(false, 0, today + PAUSE_DAYS)
        else -> Turn(true, left + 1, pausedUntil)
    }

    suspend fun reschedule(context: Context, now: LocalDateTime = Today.dateTime()) {
        val c = context.appContainer
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val at = if (!asks(c)) null else runCatching { next(c.scheduleRepository.snapshot.first().resolver, now) }.getOrNull()
        if (at == null) {
            am.cancel(fire(context))
            return
        }
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.atZone(Today.zone()).toInstant().toEpochMilli(), fire(context))
    }

    private fun fire(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, REQUEST, Intent(context, PlazaAfterShiftReceiver::class.java).setAction(ACTION_FIRE),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** The reply action: mutable so the system can put the written line in, and only ever to this app's own receiver. */
    private fun reply(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, REQUEST_REPLY, Intent(context, PlazaAfterShiftReceiver::class.java).setAction(ACTION_REPLY).setPackage(context.packageName),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
    )

    private fun openPlaza(context: Context, payload: String?): PendingIntent =
        Notifications.open(context, NoticeLinks.Target.PLAZA, REQUEST_OPEN, payload = payload)

    internal suspend fun post(context: Context) {
        val c = context.appContainer
        if (!asks(c)) return
        val room = room(c) ?: return
        val now = Today.dateTime()
        val resolver = c.scheduleRepository.snapshot.first().resolver
        // The shift that just ended (yesterday's night ends this morning).
        val ended = listOf(now.toLocalDate(), now.toLocalDate().minusDays(1)).map(resolver::resolve)
            .firstOrNull { d -> d.isWorkDay && d.shiftTo?.let { !it.isAfter(now) && it.isAfter(now.minusHours(2)) } == true } ?: return
        // Not into sleep: a notice the 잠 시간 would hold is not asked at all (it is about now).
        if (com.bamnat.app.sleep.QuietHold.asleepNow(context, now)) return
        val p = prefs(context)
        val today = now.toLocalDate().toEpochDay()
        val t = turn(today, p.getLong(K_LAST, Long.MIN_VALUE), p.getInt(K_LEFT, 0), p.getLong(K_PAUSE, Long.MIN_VALUE))
        p.edit().putInt(K_LEFT, t.left).putLong(K_PAUSE, t.pausedUntil).apply { if (t.ask) putLong(K_LAST, today) }.apply()
        if (!t.ask) return
        val night = ended.shiftFrom?.let { from -> ended.shiftTo?.toLocalDate()?.isAfter(from.toLocalDate()) == true || from.hour >= 19 } == true
        val style = c.settingsRepository.current.workStyle
        val title = question(style, ended.shiftType?.name, night)
        val same = runCatching { c.plazaTags.tagFor(room)?.form }.getOrNull() != null
        val input = RemoteInput.Builder(KEY_LINE).setLabel("한 줄 남기기").build()
        val write = NotificationCompat.Action.Builder(0, "한 줄 쓰기", reply(context)).addRemoteInput(input).setAllowGeneratedReplies(false).build()
        val text = "같은 근무 분들에게 한 줄 남겨 보세요. 내 근무 태그와 함께 광장에 올라가요"
        val n = NotificationCompat.Builder(context, Notifications.PLAZA_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setSilent(true)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$text\n광장 › 내 근무 태그 › 알림에서 끌 수 있어요."))
            .addAction(write)
            .setContentIntent(openPlaza(context, if (same) SAME else null))
            .setAutoCancel(true)
            .setTimeoutAfter(STALE_AFTER_MILLIS)
            .build()
        Notifications.post(context, ID, n)
        c.telemetry.event("plaza_after_shift", "asked")
    }

    /** The line written on the notice: checked as the 광장's box checks it, then sent; the notice says how it went. */
    internal suspend fun send(context: Context, line: String) {
        val c = context.appContainer
        prefs(context).edit().putInt(K_LEFT, 0).apply()
        val room = room(c) ?: return
        val text = line.trim().take(MAX_LINE)
        val said = when {
            text.isEmpty() -> null
            else -> CompanyText.postHit(text, room = true, roomMoney = false)?.let { "보내지 못했어요 · " + CompanyText.stopLine(it, CompanyText::problem) }
                ?: try {
                    c.companyApi.post(c.companyStore.device, room, StylePlaza.ROOM, text)
                    c.telemetry.event("plaza_after_shift", "sent")
                    "광장에 올렸어요. 같은 근무 분들의 답이 오면 알려 드려요"
                } catch (e: CompanyException) {
                    "지금은 보내지 못했어요 · " + com.bamnat.app.ui.company.talkProblem(e)
                }
        } ?: return Notifications.cancel(context, ID)
        val n = NotificationCompat.Builder(context, Notifications.PLAZA_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setSilent(true)
            .setContentTitle(said)
            .setContentIntent(openPlaza(context, null))
            .setAutoCancel(true)
            .setTimeoutAfter(60 * 60 * 1000L)
            .build()
        Notifications.post(context, ID, n)
    }

    /** As long as the 광장's own box takes for a line. */
    private const val MAX_LINE = 500

    internal const val FIRE = ACTION_FIRE
    internal const val REPLY = ACTION_REPLY
}

class PlazaAfterShiftReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val c = context.appContainer
        val pending = goAsync()
        c.appScope.launch {
            try {
                when (intent.action) {
                    PlazaAfterShift.FIRE -> PlazaAfterShift.post(context)
                    PlazaAfterShift.REPLY -> RemoteInput.getResultsFromIntent(intent)?.getCharSequence(PlazaAfterShift.KEY_LINE)
                        ?.let { PlazaAfterShift.send(context, it.toString()) }
                }
            } finally {
                runCatching { PlazaAfterShift.reschedule(context, Today.dateTime().plusMinutes(1)) }
                pending.finish()
            }
        }
    }
}
