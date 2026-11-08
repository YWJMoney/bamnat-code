package com.bamnat.app.memo

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.bamnat.app.NoticeLinks
import com.bamnat.app.R
import com.bamnat.app.alarm.AlarmScheduler
import com.bamnat.app.alarm.AlarmService
import com.bamnat.app.alarm.Notifications
import com.bamnat.app.alarm.RingRequest
import com.bamnat.app.appContainer
import com.bamnat.core.memo.Memo
import com.bamnat.core.memo.MemoSchedules
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.bamnat.core.time.Today

/**
 * [SCHED-01] Memos that are schedules: one system alarm for the next of them. At that minute each
 * memo shows a notice, rings like an alarm, or both, as the user chose. A ringing schedule is set
 * as an alarm clock, so it is on time in Doze like the shift alarm; a notice-only one is exact
 * when the phone allows it.
 */
object MemoScheduleAlarm {
    private const val REQUEST = 3201
    private const val NOTIFICATION_BASE = 7400
    private const val EXTRA_AT = "at"
    /** A schedule delivered later than this (the phone was off) is not announced any more. */
    private const val STALE_AFTER_MILLIS = 2 * 60 * 60 * 1000L

    fun reschedule(context: Context, memos: List<Memo>, after: LocalDateTime = Today.dateTime()) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val next = MemoSchedules.next(memos, after)
        if (next == null) {
            am.cancel(pending(context, 0L))
            return
        }
        val at = next.atZone(Today.zone()).toInstant().toEpochMilli()
        val op = pending(context, at)
        val rings = MemoSchedules.dueAt(memos, next).any { it.reminder?.ring == true }
        when {
            rings -> AlarmScheduler.setAlarm(context, am, at, op)
            Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms() -> am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, op)
            else -> am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, op)
        }
    }

    private fun pending(context: Context, at: Long): PendingIntent = PendingIntent.getBroadcast(
        context, REQUEST,
        Intent(context, MemoScheduleReceiver::class.java).putExtra(EXTRA_AT, at),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** What the memo is called in a notice or on the ringing screen. */
    fun label(memo: Memo): String = memo.title.ifBlank { memo.preview }.ifBlank { "일정" }.take(40)

    private val time = DateTimeFormatter.ofPattern("a h:mm", Locale.KOREAN)

    /** The notice's id for [memo], so a 미루기 can take it down. */
    internal fun noticeId(memo: Long): Int = NOTIFICATION_BASE + (memo.toInt() and 0x3ff)

    /**
     * 1.39 [TASK-04][TASK-20][TASK-39] [rest] is the memo's calendar's next day off (null: none ahead), offered first in 다른 때….
     */
    fun post(context: Context, memo: Memo, at: LocalDateTime, rest: java.time.LocalDate? = null) {
        val body = memo.preview.takeIf { it.isNotBlank() && memo.title.isNotBlank() }
        val notification = NotificationCompat.Builder(context, Notifications.MEMO_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentTitle(label(memo))
            .setContentText(listOfNotNull("${at.format(time)} 일정", body).joinToString(" · "))
            .setContentIntent(Notifications.open(context, NoticeLinks.Target.MEMOS, REQUEST + 1))
            .setAutoCancel(true)
            // 1.39 [TASK-39] 끝내기 · 10분 뒤 · 다른 때… (두 앱 실사용 2차): 끝내기 finishes the memo on that day for real (the 할 일 앱's
            // Complete left the task open in its list), and 다른 때… opens a small window with the day off's morning first.
            .addAction(0, "끝내기", MemoDoneReceiver.intent(context, memo.id, at.toLocalDate()))
            .addAction(0, com.bamnat.core.memo.MemoSnooze.label(com.bamnat.core.memo.MemoSnooze.Way.MIN10, rest),
                MemoSnoozeReceiver.intent(context, memo.id, com.bamnat.core.memo.MemoSnooze.Way.MIN10, rest, 0))
            .addAction(0, "다른 때…", MemoLaterActivity.intent(context, memo.id, rest))
            .build()
        Notifications.post(context, noticeId(memo.id), notification)
    }

    internal fun handle(context: Context, memos: List<Memo>, atMillis: Long, now: Long = Today.millis(), restOf: (Memo) -> java.time.LocalDate? = { null }) {
        if (atMillis == 0L || now - atMillis > STALE_AFTER_MILLIS) return
        val at = AlarmScheduler.millisToLocal(atMillis)
        val due = MemoSchedules.dueAt(memos, at)
        due.filter { it.reminder?.notify == true }.forEach { post(context, it, at, restOf(it)) }
        val ringing = due.filter { it.reminder?.ring == true }
        if (ringing.isEmpty()) return
        // [ALRM-09] 오늘 남은 알람 모두 끄기: the schedule still shows, quietly, instead of ringing.
        if (com.bamnat.app.alarm.AlarmsOff.isOff(context, now)) {
            ringing.filter { it.reminder?.notify != true }.forEach { post(context, it, at, restOf(it)) }
            return
        }
        val ring = RingRequest(
            shiftName = "",
            shiftDate = at.toLocalDate(),
            shiftStart = at.toLocalTime(),
            offsetMinutes = 0,
            soundUri = null,
            triggerAtMillis = atMillis,
            custom = true,
            label = ringing.joinToString(" · ") { label(it) }.take(60),
        )
        try {
            AlarmService.start(context, ring)
        } catch (e: RuntimeException) {
            // Not allowed to start the ringing screen from here: the loud notice rings instead.
            Notifications.postFallbackAlarm(context, ring)
        }
    }
}

class MemoScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val container = context.appContainer
        val atMillis = intent.getLongExtra("at", 0L)
        val pending = goAsync()
        container.appScope.launch {
            try {
                // Read again now, so a memo changed or deleted since the alarm was set is not announced.
                val memos = container.memoRepository.memos.first()
                // 1.39 [TASK-20] Each due memo's next day off, on the calendar it goes by, for its notice's 미루기.
                val due = if (atMillis == 0L) emptyList() else MemoSchedules.dueAt(memos, AlarmScheduler.millisToLocal(atMillis))
                val rests = due.associate { it.id to MemoSnoozeReceiver.nextRest(container, it) }
                MemoScheduleAlarm.handle(context, memos, atMillis, restOf = { rests[it.id] })
                val after = if (atMillis != 0L) AlarmScheduler.millisToLocal(atMillis) else Today.dateTime()
                MemoScheduleAlarm.reschedule(context, memos, maxOf(after, Today.dateTime().minusSeconds(1)))
            } finally {
                pending.finish()
            }
        }
    }
}

/**
 * 1.39 [TASK-04][TASK-20] A 미루기 pressed on a memo's schedule notice: the schedule moves to 10분 뒤, 1시간 뒤 or the next day
 * off's morning (내일 when there is none), the notice goes, and the one system alarm is set again from the saved memos.
 */
class MemoSnoozeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val container = context.appContainer
        val id = intent.getLongExtra(EXTRA_MEMO, -1L)
        val way = com.bamnat.core.memo.MemoSnooze.Way.of(intent.getStringExtra(EXTRA_WAY)) ?: return
        val rest = intent.getLongExtra(EXTRA_REST, Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE }?.let(java.time.LocalDate::ofEpochDay)
        val pending = goAsync()
        container.appScope.launch {
            try {
                put(context, id, way, rest)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val EXTRA_MEMO = "memo"
        private const val EXTRA_WAY = "way"
        private const val EXTRA_REST = "rest"
        private const val REQUEST_BASE = 0x40000

        fun intent(context: Context, memo: Long, way: com.bamnat.core.memo.MemoSnooze.Way, rest: java.time.LocalDate?, slot: Int): PendingIntent =
            PendingIntent.getBroadcast(
                context, REQUEST_BASE + (memo.toInt() and 0x3ff) * 4 + slot,
                Intent(context, MemoSnoozeReceiver::class.java)
                    .putExtra(EXTRA_MEMO, memo).putExtra(EXTRA_WAY, way.key)
                    .apply { rest?.let { putExtra(EXTRA_REST, it.toEpochDay()) } },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        /** Memo [id]'s schedule moved [way] from now, its notice taken down; the new time, or null when it is gone. */
        suspend fun put(context: Context, id: Long, way: com.bamnat.core.memo.MemoSnooze.Way, rest: java.time.LocalDate?): LocalDateTime? {
            Notifications.cancel(context, MemoScheduleAlarm.noticeId(id))
            val repo = context.appContainer.memoRepository
            val memo = repo.get(id) ?: return null
            val r = memo.reminder ?: return null
            val at = com.bamnat.core.memo.MemoSnooze.at(way, Today.dateTime(), r.at, rest)
            repo.save(memo.copy(reminder = r.copy(at = at)))
            return at
        }

        /** The next day off on the calendar [memo] goes by (a day with a kind of day that is a rest day), or null. */
        suspend fun nextRest(container: com.bamnat.app.AppContainer, memo: Memo): java.time.LocalDate? = runCatching {
            val cal = com.bamnat.app.data.MemoRepository.calendarOf(memo, container.calendars.active.value)
            val resolver = container.calendars.repository(cal).live.first().resolver
            com.bamnat.core.memo.MemoSnooze.nextRest(Today.date()) { d -> resolver.resolve(d).let { it.shiftType != null && it.isRestDay } }
        }.getOrNull()
    }
}

/**
 * 1.39 [TASK-39] 끝내기 on a memo's schedule notice: the memo is finished on the notice's day (a repeating one that day only), in the
 * memo list and on the 할 일 위젯 at once. The 할 일 앱 seen in 실사용 2차 took its notice's Complete away but left the task open.
 */
class MemoDoneReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val container = context.appContainer
        val id = intent.getLongExtra(EXTRA_MEMO, -1L)
        val day = intent.getLongExtra(EXTRA_DAY, Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE }?.let(java.time.LocalDate::ofEpochDay)
        val pending = goAsync()
        container.appScope.launch {
            try {
                finish(context, id, day)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val EXTRA_MEMO = "memo"
        private const val EXTRA_DAY = "day"
        private const val REQUEST_BASE = 0x48000

        fun intent(context: Context, memo: Long, day: java.time.LocalDate): PendingIntent = PendingIntent.getBroadcast(
            context, REQUEST_BASE + (memo.toInt() and 0x3ff),
            Intent(context, MemoDoneReceiver::class.java).putExtra(EXTRA_MEMO, memo).putExtra(EXTRA_DAY, day.toEpochDay()),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        /** Memo [id] finished on [day] (never brought back: a second press does nothing), its notice gone; null when it is gone. */
        suspend fun finish(context: Context, id: Long, day: java.time.LocalDate?): Memo? {
            Notifications.cancel(context, MemoScheduleAlarm.noticeId(id))
            val repo = context.appContainer.memoRepository
            val memo = repo.get(id)?.takeUnless { it.inTrash } ?: return null
            val saved = if (memo.isDoneOn(day)) memo else repo.save(memo.withDone(true, day, Today.millis()))
            runCatching { com.bamnat.app.widget.TaskWidget.redraw(context) }
            return saved
        }
    }
}
