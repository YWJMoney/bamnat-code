package com.bamnat.app.memo

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.bamnat.app.NoticeLinks
import com.bamnat.app.R
import com.bamnat.app.alarm.Notifications
import com.bamnat.app.appContainer
import com.bamnat.app.data.MemoRepository
import com.bamnat.app.ui.memo.OldWay
import com.bamnat.core.memo.MemoBacklog
import com.bamnat.core.memo.ShiftMemoTime
import com.bamnat.core.time.Today
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 2.0.0 [RVIB-05] (검토 I07ac-02) 지난 할 일 한 장. The to-dos left from before (the 늦음 week's and the older ones) come, without blame,
 * as one quiet card on the next day off's morning (the afternoon after a 야간): "지난 할 일 3개 · 쉬는 날이에요. 천천히 해도 돼요",
 * with 모두 끝내기, 오늘로 and 지우기 (into the 휴지통, where it can be brought back) for all of them at once. Once a day at most, and
 * not at all with none left; 설정 › 알림과 잠 › 메모·할 일 turns it off.
 */
object MemoRestCard {
    const val NOTICE_ID = 7396
    const val ACTION_SHOW = "com.bamnat.app.memo.REST_SHOW"
    const val ACTION_DONE = "com.bamnat.app.memo.REST_DONE"
    const val ACTION_TODAY = "com.bamnat.app.memo.REST_TODAY"
    const val ACTION_TRASH = "com.bamnat.app.memo.REST_TRASH"
    private const val REQ_AT = 3216
    private const val REQ_OPEN = 3218
    private const val REQ_DONE = 3219
    private const val REQ_TODAY = 3220
    private const val REQ_TRASH = 3221
    private const val PREFS = "memo_rest_card"
    private const val K_SHOWN = "shown"
    private const val K_IDS = "ids"

    /**
     * The end of the work a time falls in on the calendar on screen, for [MemoScheduleAlarm.handle]: none when 근무 중 알림 모으기 is
     * off or the calendar cannot be read (then nothing waits).
     */
    suspend fun workEnd(context: Context): (LocalDateTime) -> LocalDateTime? {
        val c = context.appContainer
        if (!c.settingsRepository.current.memoHoldAtWork) return { null }
        val resolver = runCatching { c.calendars.repository(c.calendars.active.value).live.first().resolver }.getOrNull() ?: return { null }
        val shift = ShiftMemoTime.shiftOf(resolver)
        return { at -> MemoBacklog.workEnd(at, shift::hours) }
    }

    /** Sets the alarm for the next day off's morning on the calendar on screen, or takes it away (off, or no day off ahead). */
    suspend fun plan(context: Context, now: LocalDateTime = Today.dateTime()) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val op = PendingIntent.getBroadcast(context, REQ_AT, Intent(context, MemoRestCardReceiver::class.java).setAction(ACTION_SHOW),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val c = context.appContainer
        val at = if (!c.settingsRepository.current.memoRestCard) null else runCatching {
            val resolver = c.calendars.repository(c.calendars.active.value).live.first().resolver
            MemoBacklog.restMorning(now, { ShiftMemoTime.isRest(resolver.resolve(it)) }, { ShiftMemoTime.of(resolver.resolve(it)) })
        }.getOrNull()
        if (at == null) {
            am.cancel(op)
            return
        }
        // A few minutes late is fine for a morning card, and needs no exact alarm.
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.atZone(Today.zone()).toInstant().toEpochMilli(), op)
    }

    /** The card, once on [today], when something is left; quiet. */
    suspend fun show(context: Context, today: LocalDate = Today.date()) {
        val c = context.appContainer
        if (!c.settingsRepository.current.memoRestCard) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(K_SHOWN, null) == today.toString()) return
        val left = MemoBacklog.left(c.memoRepository.memos.first(), today)
        prefs.edit().putString(K_SHOWN, today.toString()).putString(K_IDS, left.joinToString(",") { it.id.toString() }).apply()
        if (left.isEmpty()) return
        val (title, text) = MemoBacklog.restLines(left)
        val n = NotificationCompat.Builder(context, Notifications.MEMO_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setSilent(true)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                "쉬는 날이에요. 천천히 해도 돼요\n" + left.joinToString("\n") { m ->
                    val due = m.reminder?.at?.toLocalDate() ?: m.date
                    m.title.ifBlank { m.preview }.lineSequence().firstOrNull().orEmpty().take(40) + (due?.let { " · ${it.monthValue}/${it.dayOfMonth}" } ?: "")
                }))
            .setNumber(left.size)
            .setContentIntent(Notifications.open(context, NoticeLinks.Target.MEMOS, REQ_OPEN))
            .setAutoCancel(true)
            .setTimeoutAfter(MemoScheduleAlarm.KEEP_AT_LEAST * 2)
            .addAction(0, OldWay.DONE.label, button(context, ACTION_DONE, REQ_DONE))
            .addAction(0, "오늘로", button(context, ACTION_TODAY, REQ_TODAY))
            .addAction(0, "지우기", button(context, ACTION_TRASH, REQ_TRASH))
            .build()
        Notifications.post(context, NOTICE_ID, n)
        c.telemetry.event("memo_rest_card", "shown n=${left.size}")
    }

    /** The memos the card named, still left now (one finished meanwhile is not touched). */
    internal suspend fun named(context: Context): List<Long> {
        val ids = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(K_IDS, null).orEmpty()
            .split(',').mapNotNull { it.toLongOrNull() }.toSet()
        val c = context.appContainer
        return MemoBacklog.left(c.memoRepository.memos.first(), Today.date()).map { it.id }.filter { it in ids }
    }

    private fun button(context: Context, action: String, req: Int): PendingIntent = PendingIntent.getBroadcast(
        context, req, Intent(context, MemoRestCardReceiver::class.java).setAction(action), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
}

/**
 * 2.0.0 [RVI-80][RVIB-05] The to-dos left, at once: finished, moved to today (a time still ahead today kept, one gone dropped), or into
 * the trash. Saved without touching '고친 시각'. The memo tab's 오래된 할 일 and the 지난 할 일 card share it.
 */
internal suspend fun settleLeft(repo: MemoRepository, ids: List<Long>, how: OldWay, now: LocalDateTime = Today.dateTime()) {
    for (id in ids) {
        val stored = repo.get(id) ?: continue
        when (how) {
            OldWay.DONE -> repo.save(stored.withDone(true, stored.date, Today.millis()), touch = false)
            OldWay.TODAY -> repo.save(stored.copy(
                date = if (stored.date != null) now.toLocalDate() else null,
                reminder = stored.reminder?.let { r -> now.toLocalDate().atTime(r.at.toLocalTime()).takeIf { it.isAfter(now) }?.let { r.copy(at = it) } },
            ), touch = false)
            OldWay.TRASH -> repo.delete(stored)
        }
    }
}

/** 2.0.0 [RVIB-05] The 지난 할 일 card's morning, and its three answers (from the lock screen too). */
class MemoRestCardReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val c = context.appContainer
        val pending = goAsync()
        c.appScope.launch {
            try {
                val how = when (intent.action) {
                    MemoRestCard.ACTION_SHOW -> {
                        MemoRestCard.show(context)
                        MemoRestCard.plan(context, Today.dateTime().plusMinutes(1))
                        return@launch
                    }
                    MemoRestCard.ACTION_DONE -> OldWay.DONE
                    MemoRestCard.ACTION_TODAY -> OldWay.TODAY
                    MemoRestCard.ACTION_TRASH -> OldWay.TRASH
                    else -> return@launch
                }
                val ids = MemoRestCard.named(context)
                Notifications.cancel(context, MemoRestCard.NOTICE_ID)
                settleLeft(c.memoRepository, ids, how)
                runCatching { com.bamnat.app.widget.TaskWidget.redraw(context) }
                c.telemetry.event("memo_rest_card", "${how.name.lowercase()} n=${ids.size}")
            } finally {
                pending.finish()
            }
        }
    }
}
