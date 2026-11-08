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

    /**
     * 1.39 [TASK-44] [shiftsOf]: the schedule a 근무마다·쉬는 날마다 memo goes by, so a repeating schedule rings on each of its days; a
     * repeating memo's 미루기 is read from [MemoSnoozeStore].
     */
    fun reschedule(
        context: Context, memos: List<Memo>, after: LocalDateTime = Today.dateTime(),
        shiftsOf: (Memo) -> com.bamnat.core.memo.MemoRepeat.ShiftOf? = { null },
    ) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val extra = MemoSnoozeStore.all(context)
        val next = MemoSchedules.next(memos, after, shiftsOf, extra)
        if (next == null) {
            am.cancel(pending(context, 0L))
            return
        }
        val at = next.atZone(Today.zone()).toInstant().toEpochMilli()
        val op = pending(context, at)
        val rings = MemoSchedules.dueAt(memos, next, shiftsOf, extra).any { it.reminder?.ring == true }
        when {
            rings -> AlarmScheduler.setAlarm(context, am, at, op)
            Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms() -> am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, op)
            else -> am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, op)
        }
    }

    /** 1.39 [TASK-44] [reschedule] from the saved memos, with the schedule each repeating one goes by. */
    suspend fun rescheduleNow(context: Context, after: LocalDateTime = Today.dateTime()) {
        val container = context.appContainer
        val memos = container.memoRepository.memos.first().filter { it.reminder != null }
        reschedule(context, memos, after, shiftsFor(context, memos))
    }

    /** The schedule each 근무마다·쉬는 날마다 memo of [memos] goes by (one reading per calendar); null for any other. */
    suspend fun shiftsFor(context: Context, memos: List<Memo>): (Memo) -> com.bamnat.core.memo.MemoRepeat.ShiftOf? = runCatching {
        val container = context.appContainer
        container.memoRepository.shiftLookup(memos, container.calendars.active.value).first()
    }.getOrDefault { _: Memo -> null }

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
     * 1.39 [TASK-04][TASK-20][TASK-39] [rest] is when to tell on the memo's calendar's next day off (its morning, the afternoon after a
     * 야간; null: none ahead), offered first in 다른 때….
     */
    fun post(context: Context, memo: Memo, at: LocalDateTime, rest: LocalDateTime? = null) {
        val body = memo.preview.takeIf { it.isNotBlank() && memo.title.isNotBlank() }
        val notification = NotificationCompat.Builder(context, Notifications.MEMO_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentTitle(label(memo))
            .setContentText(listOfNotNull("${at.format(time)} 일정", body).joinToString(" · "))
            .setContentIntent(Notifications.open(context, NoticeLinks.Target.MEMOS, REQUEST + 1))
            .setAutoCancel(true)
            // PC 7차 c92cec3e: two memo notices folded into one '밤낮 · 2' bundle, so 끝내기 took two taps to open. Each notice is
            // its own group (like the 근무표 notices), so Android does not bundle them; a notice clears itself the day after.
            .setGroup(GROUP + memo.id)
            .setGroupSummary(true)
            .setTimeoutAfter(keepFor(at))
            .setWhen(at.atZone(Today.zone()).toInstant().toEpochMilli())
            // 1.39 [TASK-39] 끝내기 · 10분 뒤 · 다른 때… (두 앱 실사용 2차): 끝내기 finishes the memo on that day for real (the 할 일 앱's
            // Complete left the task open in its list), and 다른 때… opens a small window with the day off's morning first.
            .addAction(0, "끝내기", MemoDoneReceiver.intent(context, memo.id, at.toLocalDate()))
            .addAction(0, com.bamnat.core.memo.MemoSnooze.label(com.bamnat.core.memo.MemoSnooze.Way.MIN10, rest),
                MemoSnoozeReceiver.intent(context, memo.id, com.bamnat.core.memo.MemoSnooze.Way.MIN10, rest, 0))
            .addAction(0, "다른 때…", MemoLaterActivity.intent(context, memo.id, rest))
            .build()
        clearPastDays(context)
        Notifications.post(context, noticeId(memo.id), notification)
    }

    /** Each memo notice's own group, so Android does not fold two of them into one '밤낮 · 2' bundle. */
    internal const val GROUP = "bamnat_memo_"

    /** A notice for [at] stays until the end of its day, and at least 6 hours (a late-evening one is not gone at midnight). */
    internal fun keepFor(at: LocalDateTime, now: Long = Today.millis()): Long {
        val dayEnd = at.toLocalDate().plusDays(1).atStartOfDay(Today.zone()).toInstant().toEpochMilli()
        return maxOf(dayEnd - now, KEEP_AT_LEAST)
    }

    private const val KEEP_AT_LEAST = 6 * 60 * 60 * 1000L

    /**
     * PC 7차 c92cec3e: yesterday's '보고서 1:04' notice was still there the next day. Memo notices posted before today
     * (and older than [KEEP_AT_LEAST]) are taken down when a new one shows.
     */
    internal fun clearPastDays(context: Context, now: Long = Today.millis()) {
        val nm = context.getSystemService(android.app.NotificationManager::class.java) ?: return
        val dayStart = Today.date().atStartOfDay(Today.zone()).toInstant().toEpochMilli()
        runCatching { nm.activeNotifications }.getOrNull().orEmpty()
            // Only memo notices: the memo channel, no tag (the date notices on that channel carry one), a memo id.
            .filter { it.tag == null && it.notification.channelId == Notifications.MEMO_CHANNEL_ID && it.id in NOTIFICATION_BASE..NOTIFICATION_BASE + 0x3ff }
            // A notice's 'when' is its schedule's minute (set in [post]); a schedule of a past day, 6 hours gone, is taken down.
            .filter { it.notification.`when` in 1 until dayStart && now - it.notification.`when` > KEEP_AT_LEAST }
            .forEach { nm.cancel(it.tag, it.id) }
    }

    internal fun handle(
        context: Context, memos: List<Memo>, atMillis: Long, now: Long = Today.millis(), restOf: (Memo) -> LocalDateTime? = { null },
        shiftsOf: (Memo) -> com.bamnat.core.memo.MemoRepeat.ShiftOf? = { null },
    ) {
        if (atMillis == 0L || now - atMillis > STALE_AFTER_MILLIS) return
        val at = AlarmScheduler.millisToLocal(atMillis)
        val due = MemoSchedules.dueAt(memos, at, shiftsOf, MemoSnoozeStore.all(context))
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
                // 1.39 [TASK-44] A 근무마다·쉬는 날마다 schedule is due on its days only, as the calendar says now.
                val shifts = MemoScheduleAlarm.shiftsFor(context, memos.filter { it.reminder != null })
                val extra = MemoSnoozeStore.all(context)
                // 1.39 [TASK-20] Each due memo's next day off, on the calendar it goes by, for its notice's 미루기.
                val due = if (atMillis == 0L) emptyList() else MemoSchedules.dueAt(memos, AlarmScheduler.millisToLocal(atMillis), shifts, extra)
                val rests = due.associate { it.id to MemoSnoozeReceiver.nextRest(container, it) }
                MemoScheduleAlarm.handle(context, memos, atMillis, restOf = { rests[it.id] }, shiftsOf = shifts)
                // A repeating memo's 미루기 that has now rung is let go.
                if (atMillis != 0L) MemoSnoozeStore.dropUpTo(context, AlarmScheduler.millisToLocal(atMillis))
                // 1.39 [TASK-39] The schedule's time has come: the 할 일 위젯 shows it late (red) from now, not at the next redraw (PC 6차).
                if (due.isNotEmpty()) runCatching { com.bamnat.app.widget.TaskWidget.redraw(context) }
                val after = if (atMillis != 0L) AlarmScheduler.millisToLocal(atMillis) else Today.dateTime()
                MemoScheduleAlarm.reschedule(context, memos, maxOf(after, Today.dateTime().minusSeconds(1)), shifts)
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
        val rest = restAt(intent)
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
        private const val EXTRA_REST = "restAt"
        private const val REQUEST_BASE = 0x40000

        /** 1.39 [TASK-39] The day off's time, as minutes (a notice posted before this kept the day only, under "rest", and is let be). */
        internal fun putRest(intent: Intent, rest: LocalDateTime?): Intent =
            intent.apply { rest?.let { putExtra(EXTRA_REST, it.toEpochSecond(java.time.ZoneOffset.UTC) / 60) } }

        internal fun restAt(intent: Intent): LocalDateTime? = intent.getLongExtra(EXTRA_REST, Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE }
            ?.let { LocalDateTime.ofEpochSecond(it * 60, 0, java.time.ZoneOffset.UTC) }

        fun intent(context: Context, memo: Long, way: com.bamnat.core.memo.MemoSnooze.Way, rest: LocalDateTime?, slot: Int): PendingIntent =
            PendingIntent.getBroadcast(
                context, REQUEST_BASE + (memo.toInt() and 0x3ff) * 4 + slot,
                Intent(context, MemoSnoozeReceiver::class.java)
                    .putExtra(EXTRA_MEMO, memo).putExtra(EXTRA_WAY, way.key)
                    .let { putRest(it, rest) },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        /** Memo [id]'s schedule moved [way] from now, its notice taken down; the new time, or null when it is gone. */
        suspend fun put(context: Context, id: Long, way: com.bamnat.core.memo.MemoSnooze.Way, rest: LocalDateTime?): LocalDateTime? {
            Notifications.cancel(context, MemoScheduleAlarm.noticeId(id))
            val repo = context.appContainer.memoRepository
            val memo = repo.get(id) ?: return null
            val r = memo.reminder ?: return null
            val at = com.bamnat.core.memo.MemoSnooze.at(way, Today.dateTime(), r.at, rest)
            // 1.39 [TASK-44] A repeating memo keeps its time of day for the days after: this 미루기 is once only, kept on the side.
            if (memo.date != null && com.bamnat.core.memo.MemoRepeat.kindOf(memo.repeat) != com.bamnat.core.memo.MemoRepeat.Kind.NONE) {
                MemoSnoozeStore.put(context, id, at)
                MemoScheduleAlarm.rescheduleNow(context)
            } else {
                repo.save(memo.copy(reminder = r.copy(at = at)))
            }
            return at
        }

        /**
         * When to tell on the next day off of the calendar [memo] goes by, or null. 1.39 [TASK-39] PC 6차: a day the schedule leaves
         * empty counts (평일 일근's 토요일), and the day after a 야간 is told in the afternoon ([com.bamnat.core.memo.ShiftMemoTime.offTime]).
         */
        suspend fun nextRest(container: com.bamnat.app.AppContainer, memo: Memo): LocalDateTime? = runCatching {
            val cal = com.bamnat.app.data.MemoRepository.calendarOf(memo, container.calendars.active.value)
            val resolver = container.calendars.repository(cal).live.first().resolver
            restAfter(Today.date(), resolver::resolve)
        }.getOrNull()

        internal fun restAfter(today: java.time.LocalDate, resolve: (java.time.LocalDate) -> com.bamnat.core.schedule.ResolvedDay): LocalDateTime? {
            val day = com.bamnat.core.memo.MemoSnooze.nextRest(today) { d -> com.bamnat.core.memo.ShiftMemoTime.isRest(resolve(d)) } ?: return null
            return com.bamnat.core.memo.ShiftMemoTime.offTime(day, com.bamnat.core.memo.ShiftMemoTime.of(resolve(day.minusDays(1))))
        }
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
            // A 미루기 still waiting on a repeating memo finished now does not ring.
            MemoSnoozeStore.remove(context, id)
            val saved = if (memo.isDoneOn(day)) memo else repo.save(memo.withDone(true, day, Today.millis()))
            runCatching { com.bamnat.app.widget.TaskWidget.redraw(context) }
            return saved
        }
    }
}

/**
 * 1.39 [TASK-44] A repeating memo's 미루기 (10분 뒤, 다른 때…): once only, so it is kept here (memo id → time) and its schedule keeps its
 * time of day for the days after. Let go once it has rung or the memo is finished; lost with the app's data, as a 미루기 may be.
 */
internal object MemoSnoozeStore {
    private const val PREFS = "memo_snoozes"
    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun all(context: Context): Map<Long, LocalDateTime> = runCatching {
        prefs(context).all.mapNotNull { (k, v) ->
            val id = k.toLongOrNull() ?: return@mapNotNull null
            val minutes = (v as? Long) ?: return@mapNotNull null
            id to LocalDateTime.ofEpochSecond(minutes * 60, 0, java.time.ZoneOffset.UTC)
        }.toMap()
    }.getOrDefault(emptyMap())

    fun put(context: Context, id: Long, at: LocalDateTime) {
        prefs(context).edit().putLong(id.toString(), at.toEpochSecond(java.time.ZoneOffset.UTC) / 60).apply()
    }

    fun remove(context: Context, id: Long) {
        prefs(context).edit().remove(id.toString()).apply()
    }

    fun dropUpTo(context: Context, at: LocalDateTime) {
        val gone = all(context).filterValues { !it.isAfter(at) }.keys
        if (gone.isNotEmpty()) prefs(context).edit().apply { gone.forEach { remove(it.toString()) } }.apply()
    }
}
